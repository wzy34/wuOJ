package dev.wuoj.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ViewerTest {

    private final Viewer visitor = Viewer.visitor();
    private final Viewer alice = new Viewer(1, UserRole.USER);
    private final Viewer admin = new Viewer(2, UserRole.ADMIN);

    @Test
    void visitorIsNotSignedInAndHasNoId() {
        assertThat(visitor.signedIn()).isFalse();
        assertThat(visitor.isAdmin()).isFalse();
        assertThatThrownBy(visitor::userId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyAdminsSeeHiddenProblems() {
        assertThat(visitor.seesHiddenProblems()).isFalse();
        assertThat(alice.seesHiddenProblems()).isFalse();
        assertThat(admin.seesHiddenProblems()).isTrue();
    }

    @Test
    void sourceIsForTheOwnerAndAdmins() {
        assertThat(alice.readsSourceOf(1)).isTrue();
        assertThat(alice.readsSourceOf(3)).isFalse();
        assertThat(admin.readsSourceOf(3)).isTrue();
        assertThat(visitor.readsSourceOf(0)).isFalse();
    }
}
