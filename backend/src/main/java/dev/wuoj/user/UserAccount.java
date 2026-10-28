package dev.wuoj.user;

import java.time.OffsetDateTime;

import dev.wuoj.common.domain.UserRole;
import org.jspecify.annotations.Nullable;

/**
 * @param sessionEpoch sessions created under another epoch are no longer valid
 * @param mustChangePassword set by an admin password reset until the account chooses a new password
 */
public record UserAccount(long id, String username, String passwordHash, UserRole role, boolean enabled,
        int sessionEpoch, boolean mustChangePassword, OffsetDateTime createdAt, @Nullable OffsetDateTime lastLoginAt) {}
