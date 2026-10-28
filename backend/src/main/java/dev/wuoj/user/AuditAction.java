package dev.wuoj.user;

/** What an admin did to an account (the contract's {@code AuditAction}). */
public enum AuditAction {
    ROLE_CHANGED,
    DISABLED,
    ENABLED,
    PASSWORD_RESET
}
