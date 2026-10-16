package com.tailorcards.api.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's IP for security limits. The left of X-Forwarded-For is whatever the client sent, so it can't be
 * trusted; each proxy appends the address it saw on the right. With {@code trustedHops} proxies in front of the
 * app (Render's load balancer: 1), the entry that many places from the right is the real client.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request, int trustedHops) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trustedHops > 0 && forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            int index = Math.max(0, hops.length - trustedHops);
            String ip = hops[index].trim();
            if (!ip.isEmpty()) {
                return ip.length() > 64 ? ip.substring(0, 64) : ip;
            }
        }
        return request.getRemoteAddr();
    }
}
