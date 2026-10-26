package dev.wuoj.submission;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.SubmissionStatus;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.judge.EventPublisher.StatusMessage;
import dev.wuoj.submission.SubmissionDtos.SubmissionStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open status streams of this API instance. Events arrive from RabbitMQ in any order and possibly twice; each
 * stream only forwards events newer than the last one it sent. Idle streams are periodically checked against the
 * database, which both keeps proxies from cutting them and repairs lost events.
 */
@Component
@ConditionalOnRole(Role.API)
public class StreamRegistry {

    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(10);
    private static final Logger log = LoggerFactory.getLogger(StreamRegistry.class);
    private static final Viewer SYSTEM = new Viewer(0, UserRole.ADMIN);

    private final Map<Long, Set<Stream>> streams = new ConcurrentHashMap<>();
    private final SubmissionRepository submissions;
    private final Duration heartbeat;

    public StreamRegistry(SubmissionRepository submissions,
            @Value("${oj.stream.heartbeat:15s}") Duration heartbeat) {
        this.submissions = submissions;
        this.heartbeat = heartbeat;
    }

    /** One client's stream of one submission. */
    private final class Stream {
        final long submissionId;
        final SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        long lastSeq;
        Instant lastSent = Instant.now();
        boolean closed;

        Stream(long submissionId) {
            this.submissionId = submissionId;
        }

        /** Sends the event if it is newer than anything sent so far; closes the stream after the final one. */
        synchronized void offer(long seq, SubmissionStatusEvent event) {
            if (closed || seq <= lastSeq) return;
            try {
                emitter.send(SseEmitter.event().name("status").data(event, MediaType.APPLICATION_JSON));
                lastSeq = seq;
                lastSent = Instant.now();
                if (event.status() == SubmissionStatus.FINISHED) {
                    close();
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                close();
            }
        }

        synchronized void keepAlive() {
            if (closed) return;
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                close();
            }
        }

        void close() {
            closed = true;
            unregister(this);
        }
    }

    /**
     * Opens a stream: registered first, then sent the current state, so that no change can fall between the two.
     * The caller has already checked that the viewer may see the submission.
     */
    public SseEmitter open(long submissionId) {
        Stream stream = new Stream(submissionId);
        stream.emitter.onCompletion(stream::close);
        stream.emitter.onTimeout(stream::close);
        stream.emitter.onError(e -> stream.close());
        streams.computeIfAbsent(submissionId, id -> ConcurrentHashMap.newKeySet()).add(stream);
        snapshot(submissionId).ifPresent(row -> stream.offer(row.eventSeq(), SubmissionStatusEvent.of(row)));
        return stream.emitter;
    }

    /** An event from RabbitMQ. */
    public void dispatch(StatusMessage message) {
        Set<Stream> open = streams.get(message.event().submissionId());
        if (open == null) return;
        for (Stream stream : open) stream.offer(message.seq(), message.event());
    }

    /** Keeps idle streams alive and catches up any whose events were lost. */
    @Scheduled(fixedDelayString = "${oj.stream.heartbeat:15s}", initialDelayString = "${oj.stream.heartbeat:15s}")
    public void heartbeat() {
        Instant idleSince = Instant.now().minus(heartbeat).plusMillis(100);
        streams.forEach((submissionId, open) -> {
            Optional<SubmissionRow> row = Optional.empty();
            for (Stream stream : open) {
                if (stream.lastSent.isAfter(idleSince)) continue;
                if (row.isEmpty()) row = snapshot(submissionId);
                long seqBefore = stream.lastSeq;
                row.ifPresent(r -> stream.offer(r.eventSeq(), SubmissionStatusEvent.of(r)));
                if (stream.lastSeq == seqBefore) stream.keepAlive();
            }
        });
    }

    int openStreams() {
        return streams.values().stream().mapToInt(Set::size).sum();
    }

    private Optional<SubmissionRow> snapshot(long submissionId) {
        try {
            return submissions.find(SYSTEM, submissionId);
        } catch (RuntimeException e) {
            log.warn("Could not load submission {} for its status stream", submissionId, e);
            return Optional.empty();
        }
    }

    private void unregister(Stream stream) {
        streams.computeIfPresent(stream.submissionId, (id, open) -> {
            open.remove(stream);
            return open.isEmpty() ? null : open;
        });
    }
}
