package dev.wuoj.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import dev.wuoj.WuojApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Base class for tests that need a real HTTP server: streaming responses and exact headers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, Fixtures.class})
public abstract class RealServerTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected Fixtures fixtures;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    @Qualifier("redis")
    private GenericContainer<?> redisContainer;

    @Autowired
    private RabbitMQContainer rabbit;

    @BeforeEach
    void resetState() {
        fixtures.clear();
        redis.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }

    /** A cookie-keeping HTTP client, like a browser. */
    public static final class Browser {

        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        public Browser() {}

        public HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
            Contract.record("GET", path);
            return http.send(request(port, path).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        public HttpResponse<String> postJson(int port, String path, String json)
                throws IOException, InterruptedException {
            Contract.record("POST", path);
            HttpRequest.Builder request = request(port, path).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            String token = cookie("XSRF-TOKEN");
            if (token != null) request.header("X-XSRF-TOKEN", token);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }

        public HttpResponse<InputStream> stream(int port, String path)
                throws IOException, InterruptedException {
            Contract.record("GET", path);
            return http.send(request(port, path).header("Accept", "text/event-stream").GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
        }

        public HttpResponse<String> signIn(int port, String username) throws IOException, InterruptedException {
            get(port, "/api/auth/me");
            return postJson(port, "/api/auth/login",
                    "{\"username\":\"" + username + "\",\"password\":\"" + username + "\"}");
        }

        public String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> c.getName().equals(name))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private static HttpRequest.Builder request(int port, String path) {
            return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(60));
        }
    }

    /**
     * Starts another instance of the application against the same PostgreSQL, Redis and RabbitMQ, e.g. to simulate a
     * restart. The caller closes it.
     */
    protected ConfigurableApplicationContext startAnotherInstance(Map<String, Object> extraProperties) {
        Map<String, Object> properties = new HashMap<>(connectionProperties());
        properties.put("server.port", 0);
        properties.putAll(extraProperties);
        // As arguments, not builder properties: those are defaults that application.yaml would override.
        String[] args = properties.entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(WuojApplication.class).profiles("test").run(args);
    }

    /** How to reach this test's PostgreSQL, Redis and RabbitMQ, as application properties. */
    protected Map<String, Object> connectionProperties() {
        return Map.of(
                "spring.datasource.url", postgres.getJdbcUrl(),
                "spring.datasource.username", postgres.getUsername(),
                "spring.datasource.password", postgres.getPassword(),
                "spring.data.redis.host", redisContainer.getHost(),
                "spring.data.redis.port", redisContainer.getMappedPort(6379),
                "spring.rabbitmq.host", rabbit.getHost(),
                "spring.rabbitmq.port", rabbit.getAmqpPort(),
                "spring.rabbitmq.username", rabbit.getAdminUsername(),
                "spring.rabbitmq.password", rabbit.getAdminPassword());
    }

    protected static int portOf(ConfigurableApplicationContext context) {
        return Integer.parseInt(context.getEnvironment().getProperty("local.server.port", "0"));
    }
}
