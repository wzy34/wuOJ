package dev.wuoj.common.web;

import java.net.Inet6Address;
import java.net.InetAddress;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The client address that per-address limits count by. IPv4 addresses are used whole; IPv6 addresses are grouped by
 * their /64 prefix, the smallest block a single customer usually gets. Forwarded headers are not read here: behind a
 * reverse proxy, the servlet container must be configured to trust it, so that the remote address is the client's.
 */
public final class ClientAddress {

    private ClientAddress() {}

    public static String of(HttpServletRequest request) {
        return normalize(request.getRemoteAddr());
    }

    static String normalize(String address) {
        InetAddress parsed;
        try {
            parsed = InetAddress.ofLiteral(address);
        } catch (IllegalArgumentException e) {
            return address;
        }
        if (!(parsed instanceof Inet6Address)) return parsed.getHostAddress();
        byte[] bytes = parsed.getAddress();
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < 8; i += 2) {
            prefix.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
        }
        return prefix.append(":/64").toString();
    }
}
