package com.tailorcards.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for verifying administrator credentials")
public class AuthController {

    @GetMapping("/verify")
    @Operation(summary = "Verify admin credentials", description = "Validates HTTP Basic Auth credentials and returns admin session status", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Map<String, Object>> verify(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "admin";
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", username,
                "role", "ADMIN"
        ));
    }
}
