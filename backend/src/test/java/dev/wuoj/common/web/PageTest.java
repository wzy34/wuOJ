package dev.wuoj.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageTest {

    @Test
    void totalPagesRoundsUp() {
        Page<String> page = Page.of(List.of("a"), new PageRequest(3, 20), 45);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void emptyListHasNoPages() {
        assertThat(Page.of(List.of(), new PageRequest(1, 20), 0).totalPages()).isZero();
    }

    @Test
    void offsetIsZeroBased() {
        assertThat(new PageRequest(3, 20).offset()).isEqualTo(40);
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequest(0, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequest(1, 101));
    }
}
