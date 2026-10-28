package dev.wuoj.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsAnUncommonPasswordOfValidLength() {
        assertThat(policy.problem("carol", "correct horse battery")).isEmpty();
        assertThat(policy.problem("carol", "x".repeat(7) + "Q")).isEmpty();
    }

    @Test
    void lengthBounds() {
        assertThat(policy.problem("carol", "abc123")).hasValue("must be at least 8 characters");
        assertThat(policy.problem("carol", "z9".repeat(64))).isEmpty();
        assertThat(policy.problem("carol", "z9".repeat(64) + "z")).hasValue("must be at most 128 characters");
    }

    @Test
    void notTheUsername() {
        assertThat(policy.problem("Carolina1", "carolina1")).hasValue("must not be the same as the username");
    }

    @Test
    void commonPasswordsIgnoringCase() {
        assertThat(policy.problem("carol", "password123")).get().asString().contains("too commonly used");
        assertThat(policy.problem("carol", "PassWord123")).get().asString().contains("too commonly used");
        assertThat(policy.problem("carol", "qwertyuiop")).get().asString().contains("too commonly used");
    }
}
