package dev.wuoj.common.config;

/** Which parts of the application a process runs; see {@code oj.roles}. */
public enum Role {
    /** HTTP API, security, live status streams. */
    API,
    /** Judge queue consumer, recovery sweeper, sandbox. */
    WORKER
}
