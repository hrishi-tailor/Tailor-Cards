package com.tailorcards.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2 stand-ins for PostgreSQL's pg_get_serial_sequence and setval, so migrations that reset
 * identity sequences can run on H2. Registered via spring.flyway.init-sqls in H2-backed tests.
 */
public final class H2PostgresSequenceFunctions {

    public static final String CREATE_PG_GET_SERIAL_SEQUENCE =
            "CREATE ALIAS IF NOT EXISTS PG_GET_SERIAL_SEQUENCE FOR 'com.tailorcards.api.H2PostgresSequenceFunctions.pgGetSerialSequence';";
    public static final String CREATE_SETVAL =
            "CREATE ALIAS IF NOT EXISTS SETVAL FOR 'com.tailorcards.api.H2PostgresSequenceFunctions.setval';";

    private H2PostgresSequenceFunctions() {
    }

    /** Returns "table.column", which {@link #setval} uses to locate the identity column. */
    public static String pgGetSerialSequence(String table, String column) {
        return table + "." + column;
    }

    /** Like PostgreSQL setval(seq, value): the next generated id becomes value + 1. */
    public static long setval(Connection connection, String sequence, long value) throws SQLException {
        String[] parts = sequence.split("\\.");
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + parts[0] + " ALTER COLUMN " + parts[1] + " RESTART WITH " + (value + 1));
        }
        return value;
    }
}
