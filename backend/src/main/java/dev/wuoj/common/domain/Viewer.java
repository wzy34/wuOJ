package dev.wuoj.common.domain;

/** The signed-in user a query is answered for: decides per-user status and which hidden content is visible. */
public record Viewer(long userId, UserRole role) {

    /** Admins also see hidden problems and submissions to them. */
    public boolean seesHiddenProblems() {
        return role == UserRole.ADMIN;
    }
}
