package dev.wuoj.user;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** What a username may look like, for imported and registered accounts alike. */
public final class Usernames {

    public static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /** Names that registration refuses: they could be mistaken for staff or for parts of the site. */
    private static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "system", "support",
            "moderator", "staff", "wuoj", "me", "api", "null", "undefined", "anonymous", "guest", "visitor");

    private Usernames() {}

    public static boolean isValid(String username) {
        return PATTERN.matcher(username).matches();
    }

    public static boolean isReserved(String username) {
        return RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }
}
