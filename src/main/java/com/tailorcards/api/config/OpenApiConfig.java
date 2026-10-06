package com.tailorcards.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI tailorCardsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tailor Cards E-Commerce API")
                        .version("1.0.0")
                        .description("REST API documentation for the Tailor Cards collectible trading cards platform. " +
                                "Covers product inventory, categories, guest cart sessions, buylist appraisals, and Stripe checkout.")
                        .contact(new Contact()
                                .name("Tailor Cards Engineering")
                                .url("https://tailorcards.com")
                                .email("engineering@tailorcards.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Current Host Server"),
                        new Server().url("https://tailor-cards-api.onrender.com").description("Production Cloud Server"),
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication for administrative endpoints (POST/PUT/DELETE on catalog, buylist review)"))
                );
    }
}
