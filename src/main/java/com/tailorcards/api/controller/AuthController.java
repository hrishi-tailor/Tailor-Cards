package com.tailorcards.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for verifying administrator credentials and checking demo status")
public class AuthController {

    @Value("${DEMO_MODE:${app.demo-mode:false}}")
    private boolean demoMode;

    @GetMapping("/verify")
    @Operation(summary = "Verify admin credentials", description = "Validates HTTP Basic Auth credentials and returns admin session status and role", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Map<String, Object>> verify(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "admin";
        String role = (authentication != null)
                ? authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5))
                    .findFirst()
                    .orElse("ADMIN")
                : "ADMIN";

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", username,
                "role", role
        ));
    }

    @GetMapping("/demo-status")
    @Operation(summary = "Check platform demo mode status", description = "Returns whether recruiter/evaluator demo mode is enabled on the server")
    public ResponseEntity<Map<String, Object>> demoStatus() {
        return ResponseEntity.ok(Map.of(
                "demoMode", demoMode
        ));
    }
}
