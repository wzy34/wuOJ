package dev.wuoj.common.domain;

import org.jspecify.annotations.Nullable;

/**
 * Who a request is answered for: a visitor (no session), or a signed-in user with the role their account has now.
 * Decides per-user status and which hidden content is visible.
 */
public record Viewer(long userId, @Nullable UserRole role) {

    private static final Viewer VISITOR = new Viewer(0, null);

    public static Viewer visitor() {
        return VISITOR;
    }

    public boolean signedIn() {
        return role != null;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /** Visitors have no user id; asking for one is a bug. */
    @Override
    public long userId() {
        if (!signedIn()) throw new IllegalStateException("A visitor has no user id");
        return userId;
    }

    /** Admins also see hidden problems and submissions to them. */
    public boolean seesHiddenProblems() {
        return isAdmin();
    }

    /** Source code and compiler output: the owner's own, and everyone's for admins. */
    public boolean readsSourceOf(long ownerId) {
        return signedIn() && (userId == ownerId || isAdmin());
    }
}
