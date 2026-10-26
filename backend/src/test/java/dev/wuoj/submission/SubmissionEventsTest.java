package dev.wuoj.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import dev.wuoj.common.domain.Difficulty;
import dev.wuoj.common.domain.Language;
import dev.wuoj.judge.JobPublisher;
import dev.wuoj.judge.JudgeMessaging;
import dev.wuoj.support.Fixtures;
import dev.wuoj.support.RealServerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** The event stream over real HTTP, judged by a slowed-down simulated judge. */
@TestPropertySource(properties = {"oj.judge.simulated-step=150ms", "oj.stream.heartbeat=1s"})
class SubmissionEventsTest extends RealServerTest {

    @Autowired
    SubmissionRepository submissions;

    @Autowired
    JobPublisher jobs;

    @Autowired
    RabbitTemplate rabbit;

    private Browser alice;
    private long userId;
    private long problem;

    /** One server-sent event, or a comment line (name "comment"). */
    record Event(String name, String data) {

        String status() {
            return JsonPath.read(data, "$.status");
        }

        Integer currentTest() {
            return JsonPath.read(data, "$.currentTest");
        }
    }

    /** Reads a stream in the background; END marks the server closing it. */
    static final class StreamReader {
        static final Event END = new Event("end", "");
        final HttpResponse<InputStream> response;
        final BlockingQueue<Event> events = new LinkedBlockingQueue<>();

        StreamReader(HttpResponse<InputStream> response) {
            this.response = response;
            Thread.ofVirtual().start(this::read);
        }

        private void read() {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String name = "message";
                StringBuilder data = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith(":")) {
                        events.add(new Event("comment", line.substring(1).strip()));
                    } else if (line.startsWith("event:")) {
                        name = line.substring(6).strip();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5));
                    } else if (line.isEmpty() && !data.isEmpty()) {
                        events.add(new Event(name, data.toString()));
                        name = "message";
                        data.setLength(0);
                    }
                }
            } catch (Exception e) {
                // Treated like the end of the stream.
            }
            events.add(END);
        }

        Event next() throws InterruptedException {
            Event event = events.poll(20, TimeUnit.SECONDS);
            assertThat(event).as("an event within 20 s").isNotNull();
            return event;
        }

        Event nextStatus() throws InterruptedException {
            Event event;
            do {
                event = next();
            } while (event.name().equals("comment"));
            return event;
        }

        /** All status events until the server closes the stream. */
        List<Event> statusesUntilEnd() throws InterruptedException {
            List<Event> all = new ArrayList<>();
            for (Event event = nextStatus(); event != END; event = nextStatus()) all.add(event);
            return all;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        userId = fixtures.user("alice");
        problem = fixtures.problem("1001", "a-plus-b", "A + B", Difficulty.EASY, true);
        alice = new Browser();
        assertThat(alice.signIn(port, "alice").statusCode()).isEqualTo(200);
    }

    /** Created without publishing, so nothing judges it until the test says so. */
    private long unpublished(String source) {
        return submissions.insert(userId, problem, Language.CPP17, source, Fixtures.VERSION);
    }

    private StreamReader open(long id) throws Exception {
        HttpResponse<InputStream> response = alice.stream(port, "/api/submissions/" + id + "/events");
        assertThat(response.statusCode()).isEqualTo(200);
        return new StreamReader(response);
    }

    @Test
    void freshSubmissionStreamsEveryStep() throws Exception {
        long id = unpublished("int main() {}");
        StreamReader stream = open(id);
        assertThat(stream.response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream"));
        assertThat(stream.response.headers().firstValue("Cache-Control")).contains("no-cache");
        assertThat(stream.response.headers().firstValue("X-Accel-Buffering")).contains("no");

        Event first = stream.nextStatus();
        assertThat(first.name()).isEqualTo("status");
        assertThat(first.status()).isEqualTo("PENDING");
        jobs.publish(id);

        List<Event> rest = stream.statusesUntilEnd();
        assertThat(rest).extracting(Event::status)
                .containsExactly("COMPILING", "RUNNING", "RUNNING", "RUNNING", "RUNNING", "RUNNING", "FINISHED");
        assertThat(rest.subList(1, 6)).extracting(Event::currentTest).containsExactly(1, 2, 3, 4, 5);
        assertThat(rest.subList(1, 6)).allSatisfy(e -> assertThat((Integer) JsonPath.read(e.data(), "$.totalTests"))
                .isEqualTo(5));
        Event finished = rest.getLast();
        assertThat((String) JsonPath.read(finished.data(), "$.verdict")).isEqualTo("AC");
        assertThat((Integer) JsonPath.read(finished.data(), "$.timeMs")).isPositive();
        assertThat((Integer) JsonPath.read(finished.data(), "$.memoryKb")).isPositive();
        assertThat((Integer) JsonPath.read(finished.data(), "$.submissionId")).isEqualTo((int) id);
    }

    @Test
    void alreadyFinishedSendsOneEventAndCloses() throws Exception {
        long id = unpublished("int main() {}");
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'WA', time_ms = 4, memory_kb = 3000,"
                + " event_seq = event_seq + 1 WHERE id = :id").param("id", id).update();

        List<Event> events = open(id).statusesUntilEnd();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.status()).isEqualTo("FINISHED");
            assertThat((String) JsonPath.read(e.data(), "$.verdict")).isEqualTo("WA");
        });
    }

    @Test
    void resultsAreReadyWhenTheFinalEventArrives() throws Exception {
        long id = unpublished("int main() {} // @mock-verdict: WA");
        StreamReader stream = open(id);
        jobs.publish(id);
        Event last = stream.statusesUntilEnd().getLast();
        assertThat(last.status()).isEqualTo("FINISHED");

        String detail = alice.get(port, "/api/submissions/" + id).body();
        List<String> verdicts = JsonPath.read(detail, "$.testResults[*].verdict");
        assertThat(verdicts).containsExactly("AC", "AC", "WA");
    }

    @Test
    void unknownSubmissionIsAProblem() throws Exception {
        HttpResponse<InputStream> response = alice.stream(port, "/api/submissions/999999/events");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        try (InputStream body = response.body()) {
            assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).contains("Submission not found.");
        }
    }

    @Test
    void staleAndDuplicateEventsAreDropped() throws Exception {
        long id = unpublished("int main() {}");
        StreamReader stream = open(id);
        assertThat(stream.nextStatus().status()).isEqualTo("PENDING"); // seq 1

        sendEvent(id, 3, "{\"submissionId\":" + id + ",\"status\":\"RUNNING\",\"currentTest\":2,\"totalTests\":5}");
        sendEvent(id, 2, "{\"submissionId\":" + id + ",\"status\":\"RUNNING\",\"currentTest\":1,\"totalTests\":5}");
        sendEvent(id, 3, "{\"submissionId\":" + id + ",\"status\":\"RUNNING\",\"currentTest\":2,\"totalTests\":5}");
        sendEvent(id, 4, "{\"submissionId\":" + id + ",\"status\":\"FINISHED\",\"totalTests\":5,\"verdict\":\"AC\","
                + "\"timeMs\":5,\"memoryKb\":3000}");

        List<Event> events = stream.statusesUntilEnd();
        assertThat(events).extracting(Event::status).containsExactly("RUNNING", "FINISHED");
        assertThat(events.getFirst().currentTest()).isEqualTo(2);
    }

    @Test
    void idleStreamsGetKeepAliveComments() throws Exception {
        long id = unpublished("int main() {}");
        StreamReader stream = open(id);
        assertThat(stream.nextStatus().status()).isEqualTo("PENDING");
        assertThat(stream.next()).isEqualTo(new Event("comment", "keep-alive"));
    }

    @Test
    void lostFinalEventIsRepaired() throws Exception {
        long id = unpublished("int main() {}");
        StreamReader stream = open(id);
        assertThat(stream.nextStatus().status()).isEqualTo("PENDING");

        // Finished without any event being published, as if the event had been lost.
        fixtures.jdbc().sql("UPDATE submissions SET status = 'FINISHED', verdict = 'AC', time_ms = 1, memory_kb = 1,"
                + " event_seq = event_seq + 1 WHERE id = :id").param("id", id).update();

        long start = System.nanoTime();
        List<Event> events = stream.statusesUntilEnd();
        assertThat(events).extracting(Event::status).containsExactly("FINISHED");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(20));
    }

    private void sendEvent(long id, long seq, String event) {
        rabbit.send(JudgeMessaging.EVENTS_EXCHANGE, "submission." + id, MessageBuilder
                .withBody(("{\"seq\":" + seq + ",\"event\":" + event + "}").getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .build());
    }
}
