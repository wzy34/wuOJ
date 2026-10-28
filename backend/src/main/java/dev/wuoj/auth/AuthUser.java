package dev.wuoj.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.user.UserAccount;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user, stored in the session (in Redis). Keep it small and stable: changing its shape signs everyone
 * out, since stored sessions can no longer be read. The role is re-read from the account on every request (see
 * {@link AccountFilter}); the epoch ties the session to the account state it was created in.
 */
public final class AuthUser implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private final long id;
    private final String username;
    private final UserRole role;
    private final boolean enabled;
    private final int epoch;
    private final boolean mustChangePassword;
    private @Nullable String password;

    private AuthUser(UserAccount account, @Nullable String password) {
        this.id = account.id();
        this.username = account.username();
        this.password = password;
        this.role = account.role();
        this.enabled = account.enabled();
        this.epoch = account.sessionEpoch();
        this.mustChangePassword = account.mustChangePassword();
    }

    /** The principal for an account as it is stored now, without its password. */
    public static AuthUser of(UserAccount account) {
        return new AuthUser(account, null);
    }

    /** For checking a sign-in: the principal with the stored password hash. */
    static AuthUser withPassword(UserAccount account) {
        return new AuthUser(account, account.passwordHash());
    }

    public long id() {
        return id;
    }

    public UserRole role() {
        return role;
    }

    public int epoch() {
        return epoch;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
