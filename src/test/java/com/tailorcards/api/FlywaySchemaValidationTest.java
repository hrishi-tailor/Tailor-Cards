package com.tailorcards.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        // Own uniquely named in-memory database: the shared "testdb" may already hold tables created by
        // other Spring tests (ddl-auto=create-drop), depending on test order. Same mode/parameters as testdb.
        "spring.datasource.url=jdbc:h2:mem:flyway_validation_${random.value};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.init-sqls[0]=" + H2PostgresSequenceFunctions.CREATE_PG_GET_SERIAL_SEQUENCE,
        "spring.flyway.init-sqls[1]=" + H2PostgresSequenceFunctions.CREATE_SETVAL
})
class FlywaySchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway migrations apply cleanly and Hibernate validate passes")
    void flywayMigrationsApplyAndHibernateValidates() {
        // Started from an empty schema: V1, V2 and V3 were executed, not baselined
        List<String> types = jdbcTemplate.queryForList(
                "SELECT \"type\" FROM \"flyway_schema_history\" ORDER BY \"installed_rank\"", String.class);
        assertThat(types).doesNotContain("BASELINE");
        List<String> versioned = jdbcTemplate.queryForList(
                "SELECT \"version\" || ':' || \"type\" || ':' || \"success\" FROM \"flyway_schema_history\" "
                        + "WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\"",
                String.class);
        assertThat(versioned).containsExactly("1:SQL:TRUE", "2:SQL:TRUE", "3:SQL:TRUE", "4:SQL:TRUE", "5:SQL:TRUE", "6:SQL:TRUE", "7:SQL:TRUE");

        Integer categoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        assertThat(categoryCount).isNotNull();
        assertThat(categoryCount).isGreaterThanOrEqualTo(2);

        Integer buyRuleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buy_rules", Integer.class);
        assertThat(buyRuleCount).isNotNull();
        assertThat(buyRuleCount).isEqualTo(6); // V2 seeds 4, V6 adds PSA 9 and CGC 10

        Integer tradeParamCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_parameters", Integer.class);
        assertThat(tradeParamCount).isNotNull();
        assertThat(tradeParamCount).isGreaterThanOrEqualTo(16);

        // V2 seeds categories with explicit ids; the identity sequence must continue after them
        jdbcTemplate.update("INSERT INTO categories (name) VALUES ('Sequence Check')");
        Long newId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name = 'Sequence Check'", Long.class);
        assertThat(newId).isGreaterThan(2L);
        jdbcTemplate.update("DELETE FROM categories WHERE name = 'Sequence Check'");
    }
}
