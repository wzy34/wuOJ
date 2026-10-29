package dev.wuoj.user;

import java.time.Instant;
import java.time.OffsetDateTime;

import dev.wuoj.common.config.ConditionalOnRole;
import dev.wuoj.common.config.Role;
import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.InvalidFieldsException;
import dev.wuoj.common.web.Page;
import dev.wuoj.common.web.PageRequest;
import dev.wuoj.submission.SubmissionDtos.ProblemRef;
import dev.wuoj.submission.SubmissionDtos.UserRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Account administration; the security configuration admits admins only. Request bodies are never logged. */
@RestController
@RequestMapping("/api/admin")
@ConditionalOnRole(Role.API)
public class AdminUserController {

    public record AdminUser(long id, String username, UserRole role, boolean enabled, boolean mustChangePassword,
            Instant createdAt, @Nullable Instant lastLoginAt) {

        static AdminUser of(UserAccount account) {
            return new AdminUser(account.id(), account.username(), account.role(), account.enabled(),
                    account.mustChangePassword(), account.createdAt().toInstant(), instant(account.lastLoginAt()));
        }

        private static @Nullable Instant instant(@Nullable OffsetDateTime time) {
            return time == null ? null : time.toInstant();
        }
    }

    public record UpdateUserRequest(@Nullable UserRole role, @Nullable Boolean enabled) {}

    public record PasswordReset(String temporaryPassword) {}

    /** Exactly one of {@code target} and {@code problem} is present. */
    public record AuditEntry(long id, Instant createdAt, UserRef actor, @Nullable UserRef target,
            @Nullable ProblemRef problem, AuditAction action, @Nullable UserRole fromRole, @Nullable UserRole toRole,
            @Nullable Integer fromTestCount, @Nullable Integer toTestCount) {

        static AuditEntry of(AuditRepository.Entry entry) {
            AuditRepository.Account target = entry.target();
            AuditRepository.Problem problem = entry.problem();
            return new AuditEntry(entry.id(), entry.createdAt(), new UserRef(entry.actorId(), entry.actorUsername()),
                    target == null ? null : new UserRef(target.id(), target.username()),
                    problem == null ? null
                            : new ProblemRef(problem.id(), problem.displayId(), problem.slug(), problem.title()),
                    entry.action(), entry.fromRole(), entry.toRole(), entry.fromTestCount(), entry.toTestCount());
        }
    }

    private final UserRepository users;
    private final AdminUserService service;
    private final AuditRepository audit;

    public AdminUserController(UserRepository users, AdminUserService service, AuditRepository audit) {
        this.users = users;
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/users")
    Page<AdminUser> list(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size,
            @RequestParam(required = false) @Size(max = 64) @Nullable String q,
            @RequestParam(required = false) @Nullable UserRole role,
            @RequestParam(required = false) @Nullable Boolean enabled) {
        String query = q == null || q.isBlank() ? null : q.strip();
        return users.list(query, role, enabled, new PageRequest(page, size)).map(AdminUser::of);
    }

    @PatchMapping("/users/{id}")
    AdminUser update(Viewer admin, @PathVariable long id, @RequestBody UpdateUserRequest body) {
        if (body.role() == null && body.enabled() == null) {
            throw new InvalidFieldsException("role", "role or enabled must be given");
        }
        return AdminUser.of(service.update(admin, id, body.role(), body.enabled()));
    }

    @PostMapping("/users/{id}/password-reset")
    PasswordReset resetPassword(Viewer admin, @PathVariable long id) {
        return new PasswordReset(service.resetPassword(admin, id));
    }

    @GetMapping("/audit")
    Page<AuditEntry> audit(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(PageRequest.MAX_SIZE) int size) {
        return audit.list(new PageRequest(page, size)).map(AuditEntry::of);
    }
}
