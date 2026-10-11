package com.tailorcards.api.buylistchat.identity;

import jakarta.servlet.http.HttpServletRequest;

/** Client IP for limits: first X-Forwarded-For hop (set by the hosting proxy), else the socket address. */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first.length() > 64 ? first.substring(0, 64) : first;
            }
        }
        return request.getRemoteAddr();
    }
}
