package dev.wuoj.user;

import dev.wuoj.common.domain.UserRole;
import dev.wuoj.common.domain.Viewer;
import dev.wuoj.common.web.ConflictException;
import dev.wuoj.common.web.NotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin changes to accounts. Each change ends the account's sessions and is audited in the same transaction. Admins
 * cannot change their own account here, which also guarantees that an enabled admin always remains: only an enabled
 * admin can act on admin accounts.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final AuditRepository audit;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository users, AuditRepository audit, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    /** Changes nothing, ends no sessions and records nothing when the values are the current ones. */
    @Transactional
    public UserAccount update(Viewer admin, long id, @Nullable UserRole role, @Nullable Boolean enabled) {
        UserAccount target = lockOther(admin, id, "You cannot change your own role or disable your own account.");
        UserRole newRole = role == null ? target.role() : role;
        boolean newEnabled = enabled == null ? target.enabled() : enabled;
        if (newRole == target.role() && newEnabled == target.enabled()) return target;

        UserAccount updated = users.updateAccess(id, newRole, newEnabled);
        if (newRole != target.role()) audit.recordRoleChange(admin.userId(), id, target.role(), newRole);
        if (newEnabled != target.enabled()) {
            audit.record(admin.userId(), id, newEnabled ? AuditAction.ENABLED : AuditAction.DISABLED);
        }
        return updated;
    }

    /** Returns the temporary password; it is not kept anywhere but as a hash. */
    @Transactional
    public String resetPassword(Viewer admin, long id) {
        lockOther(admin, id, "You cannot reset your own password. Change it in your settings instead.");
        String password = TemporaryPasswords.generate();
        users.resetPassword(id, passwordEncoder.encode(password));
        audit.record(admin.userId(), id, AuditAction.PASSWORD_RESET);
        return password;
    }

    private UserAccount lockOther(Viewer admin, long id, String ownAccount) {
        UserAccount target = users.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("User not found."));
        if (target.id() == admin.userId()) throw new ConflictException(ownAccount);
        return target;
    }
}
