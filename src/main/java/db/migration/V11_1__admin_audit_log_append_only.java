package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * Makes admin_audit_log append-only in the database: a trigger rejects UPDATE, DELETE and TRUNCATE. PostgreSQL
 * only (checked by product name, so H2's PostgreSQL compatibility mode in tests doesn't run plpgsql).
 */
public class V11_1__admin_audit_log_append_only extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!"PostgreSQL".equalsIgnoreCase(context.getConnection().getMetaData().getDatabaseProductName())) {
            return;
        }
        try (Statement sql = context.getConnection().createStatement()) {
            sql.execute("""
                    CREATE OR REPLACE FUNCTION admin_audit_log_append_only() RETURNS trigger AS $$
                    BEGIN
                        RAISE EXCEPTION 'admin_audit_log is append-only: % is not allowed', TG_OP;
                    END;
                    $$ LANGUAGE plpgsql""");
            sql.execute("DROP TRIGGER IF EXISTS admin_audit_log_no_update_delete ON admin_audit_log");
            sql.execute("""
                    CREATE TRIGGER admin_audit_log_no_update_delete
                        BEFORE UPDATE OR DELETE ON admin_audit_log
                        FOR EACH ROW EXECUTE FUNCTION admin_audit_log_append_only()""");
            sql.execute("DROP TRIGGER IF EXISTS admin_audit_log_no_truncate ON admin_audit_log");
            sql.execute("""
                    CREATE TRIGGER admin_audit_log_no_truncate
                        BEFORE TRUNCATE ON admin_audit_log
                        FOR EACH STATEMENT EXECUTE FUNCTION admin_audit_log_append_only()""");
        }
    }
}
