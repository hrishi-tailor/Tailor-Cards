package com.tailorcards.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "admin";
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", username,
                "role", "ADMIN"
        ));
    }
}
