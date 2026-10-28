package dev.wuoj.user;

import java.security.SecureRandom;

/** Passwords an admin hands to a user who forgot theirs; the user must replace it at the next sign-in. */
final class TemporaryPasswords {

    /** Letters and digits without the ones that are easily confused: 0, O, 1, l and I. */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    static final int LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {}

    static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return password.toString();
    }
}
