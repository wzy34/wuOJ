package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.wuoj.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "oj.registration.enabled=false")
class ClosedRegistrationTest extends IntegrationTest {

    @Test
    void registrationClosed() throws Exception {
        session().start().perform(RegistrationApiTest.register("carol", "correct horse battery"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Registration is closed."));
        session().perform(get("/api/auth/options")).andExpect(jsonPath("$.registrationEnabled").value(false));
        assertThat(fixtures.jdbc().sql("SELECT count(*) FROM users").query(Long.class).single()).isZero();
    }
}
