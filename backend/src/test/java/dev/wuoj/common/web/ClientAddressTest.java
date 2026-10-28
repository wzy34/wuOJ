package dev.wuoj.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressTest {

    @Test
    void ipv4IsUsedWhole() {
        assertThat(ClientAddress.normalize("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6IsGroupedBySlash64() {
        assertThat(ClientAddress.normalize("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2::/64");
        assertThat(ClientAddress.normalize("2001:db8:1:2::1")).isEqualTo("2001:db8:1:2::/64");
        assertThat(ClientAddress.normalize("2001:0db8:0001:0003::1")).isEqualTo("2001:db8:1:3::/64");
        assertThat(ClientAddress.normalize("::1")).isEqualTo("0:0:0:0::/64");
    }

    @Test
    void ipv4MappedIpv6IsTheIpv4Address() {
        assertThat(ClientAddress.normalize("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void unparseableAddressIsKeptAsIs() {
        assertThat(ClientAddress.normalize("unknown")).isEqualTo("unknown");
    }

    @Test
    void readsTheRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.1");
        assertThat(ClientAddress.of(request)).isEqualTo("198.51.100.1");
    }
}
