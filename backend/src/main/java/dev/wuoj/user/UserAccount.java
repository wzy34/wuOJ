package dev.wuoj.user;

import dev.wuoj.common.domain.UserRole;

public record UserAccount(long id, String username, String passwordHash, UserRole role, boolean enabled) {}
