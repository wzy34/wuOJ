package dev.wuoj.auth;

import dev.wuoj.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AccountUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByUsername(username)
                .map(u -> new AuthUser(u.id(), u.username(), u.passwordHash(), u.role(), u.enabled()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
