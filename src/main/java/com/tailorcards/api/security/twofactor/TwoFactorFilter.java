package com.tailorcards.api.security.twofactor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs after the admin password check: a request authenticated as ADMIN must also carry a live second-factor
 * session (X-Admin-Session), or it gets 401 {"error":"two_factor_required"}. The setup and sign-in endpoints are the
 * only exceptions. Customer endpoints (no admin credentials) are not affected.
 */
public class TwoFactorFilter extends OncePerRequestFilter {

    public static final String SESSION_HEADER = "X-Admin-Session";
    private static final List<String> EXEMPT = List.of("/api/auth/2fa/", "/api/auth/verify", "/api/auth/demo-status", "/error");

    private final TwoFactorService twoFactor;

    public TwoFactorFilter(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!twoFactor.isEnabled() || !admin || EXEMPT.stream().anyMatch(path::startsWith)
                || twoFactor.validSession(auth.getName(), request.getHeader(SESSION_HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        boolean enrolled = twoFactor.status(auth.getName()).enrolled();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"two_factor_required\",\"enrolled\":" + enrolled
                + ",\"message\":\"Enter the code from your authenticator app to continue.\"}");
    }
}
