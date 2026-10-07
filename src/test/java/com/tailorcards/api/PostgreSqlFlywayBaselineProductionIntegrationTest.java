package com.tailorcards.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Baselined-production simulation using a schema-only dump of the real production database,
 * loaded with psql inside the container. Recommended dump command:
 * {@code pg_dump --schema-only --no-owner --no-privileges --schema=public "$PROD_URL" > src/test/resources/prod-schema.sql}
 *
 * <p>A missing dump fails the test (even without Docker) rather than skipping it.
 */
class PostgreSqlFlywayBaselineProductionIntegrationTest extends AbstractFlywayBaselineIntegrationTest {

    static final Path PROD_SCHEMA = Path.of("src/test/resources/prod-schema.sql");
    private static final String CONTAINER_PATH = "/tmp/prod-schema.sql";

    static PostgreSQLContainer<?> postgres;

    static {
        if (Files.isRegularFile(PROD_SCHEMA) && PROD_SCHEMA.toFile().length() > 0 && isDockerAvailable()) {
            postgres = newPostgresContainer();
            postgres.start();
            loadProductionSchema();
        }
    }

    private static void loadProductionSchema() {
        postgres.copyFileToContainer(MountableFile.forHostPath(PROD_SCHEMA), CONTAINER_PATH);
        ExecResult result;
        try {
            result = postgres.execInContainer("psql", "-v", "ON_ERROR_STOP=1", "-q",
                    "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(), "-f", CONTAINER_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run psql on " + PROD_SCHEMA, e);
        }
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql failed loading " + PROD_SCHEMA + " (exit " + result.getExitCode()
                    + "):\n" + result.getStderr());
        }

        List<String> statements = new ArrayList<>();
        // A schema-only dump carries flyway_schema_history (left by the failed deploy) without its
        // baseline row; drop it so Flyway baselines afresh exactly as production does.
        statements.add("drop table if exists public.flyway_schema_history");
        statements.addAll(LEGACY_DATA);
        executeStatements(postgres, statements);
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registerBaselinedPostgres(registry, postgres);
    }

    @Test
    @DisplayName("Production schema dump: V1 baselined, V2 creates trade tables and seeds, validate passes")
    void migratesBaselinedProductionSchemaDump() {
        assertThat(PROD_SCHEMA)
                .as("Missing or empty %s. Add a schema-only dump of production, e.g. "
                        + "pg_dump --schema-only --no-owner --no-privileges --schema=public \"$PROD_URL\" > %s",
                        PROD_SCHEMA, PROD_SCHEMA)
                .isRegularFile()
                .isNotEmptyFile();
        assumeThat(postgres).as("Docker is not available; cannot start PostgreSQL").isNotNull();

        assertBaselinedSchemaMigrated();
    }
}
