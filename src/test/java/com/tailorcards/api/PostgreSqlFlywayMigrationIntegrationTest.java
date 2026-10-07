package com.tailorcards.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("isDockerAvailable")
class PostgreSqlFlywayMigrationIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres;

    static {
        if (isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine");
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url",
                    () -> AbstractFlywayBaselineIntegrationTest.withPrepareThresholdZero(postgres.getJdbcUrl()));
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            // The shared test config sets H2Dialect; validate against PostgreSQL types
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.flyway.baseline-version", () -> "1");
            registry.add("spring.sql.init.mode", () -> "never");
        }
    }

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Boots application with real PostgreSQL container, applies Flyway V1+V2, and passes ddl-auto=validate")
    void bootsWithRealPostgresAndValidatesFlyway() {
        assertThat(jdbcTemplate).isNotNull();

        // 1. Verify V1 schema tables exist in real PostgreSQL
        Integer categoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        assertThat(categoryCount).isNotNull();
        assertThat(categoryCount).isGreaterThanOrEqualTo(2);

        // Migrations seed no products (only DemoDataSeeder does, and only with DEMO_MODE=true),
        // so on an empty database the table exists and is empty
        Integer productCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        assertThat(productCount).isNotNull();
        assertThat(productCount).isZero();

        // 2. Verify V2 seed data exists
        Integer buyRuleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buy_rules", Integer.class);
        assertThat(buyRuleCount).isNotNull();
        assertThat(buyRuleCount).isEqualTo(4);

        Integer tradeParamCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_parameters", Integer.class);
        assertThat(tradeParamCount).isNotNull();
        assertThat(tradeParamCount).isGreaterThanOrEqualTo(16);

        Integer cardLiquidityCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM card_liquidity", Integer.class);
        assertThat(cardLiquidityCount).isNotNull();
        assertThat(cardLiquidityCount).isGreaterThanOrEqualTo(3);

        // 3. Verify flyway schema history table exists and contains records
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migrationCount).isNotNull();
        assertThat(migrationCount).isGreaterThanOrEqualTo(2);
    }
}
