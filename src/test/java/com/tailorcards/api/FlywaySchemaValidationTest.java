package com.tailorcards.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywaySchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway migrations apply cleanly and Hibernate validate passes")
    void flywayMigrationsApplyAndHibernateValidates() {
        Integer categoryCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
        assertThat(categoryCount).isNotNull();
        assertThat(categoryCount).isGreaterThanOrEqualTo(2);

        Integer buyRuleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buy_rules", Integer.class);
        assertThat(buyRuleCount).isNotNull();
        assertThat(buyRuleCount).isEqualTo(4);

        Integer tradeParamCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_parameters", Integer.class);
        assertThat(tradeParamCount).isNotNull();
        assertThat(tradeParamCount).isGreaterThanOrEqualTo(16);
    }
}
