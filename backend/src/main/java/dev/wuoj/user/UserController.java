package dev.wuoj.user;

import java.time.Instant;
import java.util.List;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.NotFoundException;
import dev.wuoj.submission.SubmissionDtos.ProblemRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public profiles. */
@RestController
@RequestMapping("/api/users")
@ConditionalOnRole(Role.API)
public class UserController {

    public record UserProfile(long id, String username, UserRole role, Instant joinedAt, int solvedCount,
            int attemptedCount, long submissionCount, long acceptedCount, List<ProblemRef> solved) {}

    private final UserRepository users;
    private final ProfileRepository profiles;

    public UserController(UserRepository users, ProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    /** Disabled accounts are hidden from everyone but admins. */
    @GetMapping("/{username}")
    UserProfile profile(Viewer viewer, @PathVariable String username) {
        UserAccount account = users.findByUsername(username)
                .filter(a -> a.enabled() || viewer.isAdmin())
                .orElseThrow(() -> new NotFoundException("User not found."));
        ProfileRepository.Counts counts = profiles.counts(account.id());
        return new UserProfile(account.id(), account.username(), account.role(), account.createdAt().toInstant(),
                counts.solved(), counts.attempted(), counts.submissions(), counts.accepted(),
                profiles.solved(account.id()));
    }
}
