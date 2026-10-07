package com.tailorcards.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public API docs
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Public Cart & Checkout flows
                .requestMatchers("/api/checkout/**").permitAll()
                .requestMatchers("/api/cart/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // Public Buylist customer actions
                .requestMatchers(HttpMethod.POST, "/api/buylist/submit").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/buylist/upload").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/buylist/track/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/buylist/*/messages").permitAll()
                // Public Trade Assistant customer actions
                .requestMatchers("/api/trade-assistant/**").permitAll()
                // Public Demo Mode status indicator
                .requestMatchers("/api/auth/demo-status").permitAll()
                // Auth verify: accessible by both ADMIN and DEMO
                .requestMatchers("/api/auth/verify").hasAnyRole("ADMIN", "DEMO")
                // Sensitive trade parameters, margins, buy rules, liquidity tags, and price overrides: ADMIN ONLY
                .requestMatchers("/api/admin/trade-assistant/parameters/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/trade-assistant/buy-rules/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/trade-assistant/liquidity/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/price-overrides/**").hasRole("ADMIN")
                // Listing generator (paid AI calls): ADMIN ONLY, never DEMO
                .requestMatchers("/api/admin/listing-generator/**").hasRole("ADMIN")
                // Read-only dashboard access: both ADMIN and DEMO
                .requestMatchers(HttpMethod.GET, "/api/buylist/admin/**").hasAnyRole("ADMIN", "DEMO")
                .requestMatchers(HttpMethod.GET, "/api/admin/trade-assistant/requests/**").hasAnyRole("ADMIN", "DEMO")
                // All other /api/admin/** endpoints (GET, POST, PUT, DELETE): ADMIN ONLY
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // State mutations on buylist endpoints: ADMIN ONLY
                .requestMatchers(HttpMethod.POST, "/api/buylist/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/buylist/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/buylist/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/buylist/admin/**").hasRole("ADMIN")
                // General public GET endpoints (catalog, categories, price history)
                .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                // Fallback for all other endpoints
                .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .anyRequest().hasRole("ADMIN")
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${ADMIN_USERNAME:${spring.security.user.name:admin}}") String adminUsername,
            @Value("${ADMIN_PASSWORD:${spring.security.user.password:}}") String adminPassword,
            @Value("${DEMO_MODE:${app.demo-mode:false}}") boolean demoMode,
            @Value("${DEMO_USERNAME:${app.demo-user.username:demo}}") String demoUsername,
            @Value("${DEMO_PASSWORD:${app.demo-user.password:}}") String demoPassword,
            PasswordEncoder passwordEncoder
    ) {
        List<UserDetails> users = new ArrayList<>();

        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "CRITICAL SECURITY CONFIGURATION ERROR: ADMIN_PASSWORD environment variable is not set. " +
                    "Application refuses to boot in production with an empty or default password."
            );
        }

        users.add(User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build());

        if (demoMode) {
            if (demoPassword == null || demoPassword.isBlank()) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY CONFIGURATION ERROR: DEMO_MODE is true but DEMO_PASSWORD environment variable is not set. " +
                        "Application refuses to boot without explicit demo credentials."
                );
            }

            users.add(User.builder()
                    .username(demoUsername)
                    .password(passwordEncoder.encode(demoPassword))
                    .roles("DEMO")
                    .build());
        }

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
