package dev.wuoj.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user, stored in the session (in Redis). Keep it small and stable: changing its shape signs everyone
 * out, since stored sessions can no longer be read.
 */
public final class AuthUser implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final UserRole role;
    private final boolean enabled;
    private @Nullable String password;

    public AuthUser(long id, String username, @Nullable String password, UserRole role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    public long id() {
        return id;
    }

    public UserRole role() {
        return role;
    }

    public Viewer viewer() {
        return new Viewer(id, role);
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
