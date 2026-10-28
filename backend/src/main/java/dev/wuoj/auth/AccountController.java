package dev.wuoj.auth;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ClientAddress;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.user.UserAccount;
import dev.wuoj.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own account. */
@RestController
@RequestMapping("/api/account")
@ConditionalOnRole(Role.API)
public class AccountController {

    /** The new password's rules are checked by {@link PasswordPolicy}, as field errors. */
    public record ChangePasswordRequest(@NotEmpty @Size(max = 128) String currentPassword,
            @NotEmpty String newPassword) {}

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy policy;
    private final LoginThrottle throttle;
    private final SessionStarter sessions;

    public AccountController(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy policy,
            LoginThrottle throttle, SessionStarter sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.policy = policy;
        this.throttle = throttle;
        this.sessions = sessions;
    }

    /**
     * A wrong current password counts as a failed sign-in, so a stolen session can't be used to guess it. On success
     * the account's other sessions end and this one is moved to the new epoch.
     */
    @PostMapping("/password")
    ResponseEntity<Void> changePassword(Viewer viewer, @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = users.findById(viewer.userId()).orElseThrow();
        String address = ClientAddress.of(request);
        throttle.check(account.username(), address);
        if (!passwordEncoder.matches(body.currentPassword(), account.passwordHash())) {
            throttle.recordFailure(account.username(), address);
            throw new InvalidFieldsException("currentPassword", "is not correct");
        }
        policy.problem(account.username(), body.newPassword()).ifPresent(problem -> {
            throw new InvalidFieldsException("newPassword", problem);
        });

        UserAccount changed = users.changePassword(account.id(), passwordEncoder.encode(body.newPassword()));
        sessions.replace(AuthUser.of(changed), request, response);
        return ResponseEntity.noContent().build();
    }
}
