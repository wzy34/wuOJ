package dev.wuoj.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for tests that run the whole application against real PostgreSQL, Redis and RabbitMQ. Every test starts
 * with an empty database and an empty Redis.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, Fixtures.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected Fixtures fixtures;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        fixtures.clear();
        redis.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }

    /** A new browser-like client with no cookies. */
    protected ApiSession session() {
        return new ApiSession(mvc);
    }

    /** A signed-in client; {@link Fixtures#user} accounts use the username as password. */
    protected ApiSession signedIn(String username) throws Exception {
        return session().signIn(username, username);
    }

    /** Performs the request and checks request and response against the contract. */
    protected ResultActions api(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(Contract.conforms());
    }

    /** Performs a request that is invalid on purpose; only the response is checked against the contract. */
    protected ResultActions apiInvalid(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(Contract.responseConforms());
    }
}
