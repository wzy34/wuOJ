package dev.wuoj.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The rules for a password a person chooses (registration and password changes). Passwords set by the importer are
 * not checked, so operators can provision development accounts such as {@code student}.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private final Set<String> common;

    public PasswordPolicy() {
        this.common = load("common-passwords.txt");
    }

    /** Why the password is not acceptable for this account, or empty if it is. */
    public Optional<String> problem(String username, String password) {
        if (password.length() < MIN_LENGTH) return Optional.of("must be at least " + MIN_LENGTH + " characters");
        if (password.length() > MAX_LENGTH) return Optional.of("must be at most " + MAX_LENGTH + " characters");
        if (password.equalsIgnoreCase(username)) return Optional.of("must not be the same as the username");
        if (common.contains(password.toLowerCase(Locale.ROOT))) {
            return Optional.of("is too commonly used; choose a less predictable password");
        }
        return Optional.empty();
    }

    private static Set<String> load(String resource) {
        Set<String> entries = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isEmpty() && !line.startsWith("#")) entries.add(line.toLowerCase(Locale.ROOT));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
        return Set.copyOf(entries);
    }
}
