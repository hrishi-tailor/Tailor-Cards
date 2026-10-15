package com.tailorcards.api.security.login;

import com.tailorcards.api.security.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.IOException;

/** The usual 401 for HTTP Basic, counting a failed sign-in when credentials were sent and rejected. */
public class CountingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final LoginAttemptService attempts;
    private final int trustedHops;
    private final BasicAuthenticationEntryPoint delegate = new BasicAuthenticationEntryPoint();

    public CountingAuthenticationEntryPoint(LoginAttemptService attempts, int trustedHops) {
        this.attempts = attempts;
        this.trustedHops = trustedHops;
        delegate.setRealmName("Tailor Cards");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        String username = LoginThrottleFilter.basicUsername(request);
        if (username != null) {
            attempts.failure(username, ClientAddress.of(request, trustedHops));
        }
        delegate.commence(request, response, e);
    }
}
