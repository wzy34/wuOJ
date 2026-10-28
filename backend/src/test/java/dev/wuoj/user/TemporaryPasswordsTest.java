package dev.wuoj.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    @Test
    void lengthAndAlphabet() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String password = TemporaryPasswords.generate();
            assertThat(password).hasSize(16).doesNotContainAnyWhitespaces();
            assertThat(password.chars()).allMatch(c -> TemporaryPasswords.ALPHABET.indexOf(c) >= 0);
            assertThat(password).doesNotContain("0", "O", "1", "l", "I");
            seen.add(password);
        }
        assertThat(seen).hasSize(200);
    }
}
