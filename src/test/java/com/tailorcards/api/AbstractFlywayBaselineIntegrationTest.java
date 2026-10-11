package com.tailorcards.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared setup and assertions for tests that simulate the production database: a schema that
 * predates Flyway, baselined at version 1 so V1 is skipped and V2 alone must bring it up to date.
 */
@SpringBootTest
abstract class AbstractFlywayBaselineIntegrationTest {

    // Existing production data that must survive the migration
    static final List<String> LEGACY_DATA = List.of(
            "insert into categories (name, description) values ('Singles', 'Individual collectible trading cards')",
            "insert into products (name, price, stock, category_id, status, version) "
                    + "values ('Legacy Charizard', 199.99, 1, (select id from categories where name = 'Singles'), 'AVAILABLE', 0)"
    );

    @Autowired
    JdbcTemplate jdbcTemplate;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    static PostgreSQLContainer<?> newPostgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    static void executeStatements(PostgreSQLContainer<?> postgres, List<String> statements) {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to prepare legacy production database", e);
        }
    }

    /** Appends prepareThreshold=0 with the right separator (Testcontainers URLs already carry "?loggerLevel=OFF"). */
    static String withPrepareThresholdZero(String jdbcUrl) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
    }

    static void registerBaselinedPostgres(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", () -> withPrepareThresholdZero(postgres.getJdbcUrl()));
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.flyway.baseline-on-migrate", () -> "true");
            registry.add("spring.flyway.baseline-version", () -> "1");
            registry.add("spring.sql.init.mode", () -> "never");
        }
    }

    /** Startup reaching the test means Flyway migrated and ddl-auto=validate succeeded. */
    void assertBaselinedSchemaMigrated() {
        // V1 was baselined, not executed; V2 ran successfully
        List<String> history = jdbcTemplate.queryForList(
                "SELECT version || ':' || type || ':' || success FROM flyway_schema_history ORDER BY installed_rank",
                String.class);
        assertThat(history).containsExactly("1:BASELINE:true", "2:SQL:true", "3:SQL:true", "4:SQL:true", "5:SQL:true", "6:SQL:true", "7:SQL:true");

        for (String table : List.of("buy_rules", "trade_parameters", "card_liquidity",
                "price_snapshots", "manual_price_overrides", "trade_assistant_requests")) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("table %s exists", table).isEqualTo(1);
        }

        Integer newProductColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = 'products' AND column_name IN ('cost_basis', 'pokemontcg_id')",
                Integer.class);
        assertThat(newProductColumns).isEqualTo(2);

        for (String index : List.of("idx_price_override_card_condition", "idx_price_snapshot_card_id",
                "idx_price_snapshot_fetched_at", "idx_trade_req_reference", "idx_trade_req_status",
                "idx_trade_req_created_at")) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                    Integer.class, index);
            assertThat(exists).as("index %s exists", index).isEqualTo(1);
        }

        // Seed rows
        assertThat(count("buy_rules")).isEqualTo(6); // 4 from V2 + PSA 9 / CGC 10 from V6
        assertThat(count("trade_parameters")).isEqualTo(17); // 16 from V2 + buylist trade rate from V5
        assertThat(jdbcTemplate.queryForObject(
                "SELECT param_value FROM trade_parameters WHERE param_key = 'BUYLIST_TRADE_CREDIT_RATE'", BigDecimal.class))
                .isEqualByComparingTo("0.80");
        assertThat(count("card_liquidity")).isEqualTo(5);
        assertThat(count("manual_price_overrides")).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE name IN ('Singles', 'Sealed')", Integer.class)).isEqualTo(2);

        // Existing data preserved, new columns empty
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE name = 'Legacy Charizard' AND cost_basis IS NULL AND pokemontcg_id IS NULL",
                Integer.class)).isEqualTo(1);

        assertBuylistChatSchema();

        // Identity sequence continues after the seeded/existing category ids
        Long maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM categories", Long.class);
        Long newId = jdbcTemplate.queryForObject(
                "INSERT INTO categories (name) VALUES ('Sequence Check') RETURNING id", Long.class);
        assertThat(newId).isGreaterThan(maxId);
    }

    /** V3: chatbot tables exist and the one-per-day rule is a real unique constraint. */
    void assertBuylistChatSchema() {
        for (String table : List.of("buylist_otp_codes", "buylist_chat_sessions", "buylist_drafts",
                "buylist_draft_lines", "buylist_chat_messages", "buylist_submission_lines", "buylist_llm_spend")) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertThat(exists).as("table %s exists", table).isEqualTo(1);
        }
        String insert = "INSERT INTO buylist_submissions (tracking_token, customer_email, card_name, status, created_at, local_date) "
                + "VALUES (?, 'dupe@example.com', 'Chat buylist', 'PENDING', now(), DATE '2026-10-08')";
        jdbcTemplate.update(insert, "v3-check-1");
        assertThatThrownBy(() -> jdbcTemplate.update(insert, "v3-check-2"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // Manual / reset rows have local_date NULL and are not limited
        String manual = "INSERT INTO buylist_submissions (tracking_token, customer_email, card_name, status, created_at) "
                + "VALUES (?, 'dupe@example.com', 'Manual', 'PENDING', now())";
        jdbcTemplate.update(manual, "v3-check-3");
        jdbcTemplate.update(manual, "v3-check-4");
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }
}
