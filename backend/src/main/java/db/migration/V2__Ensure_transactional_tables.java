package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Older local MySQL installations may default to MyISAM, which cannot protect wallet transactions. */
public class V2__Ensure_transactional_tables extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) return;
        for (String table : new String[]{"app_user", "job", "job_application", "verification_event"}) {
            try (var query = connection.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) {
                query.setString(1, table);
                try (var result = query.executeQuery()) {
                    if (result.next() && !"InnoDB".equalsIgnoreCase(result.getString(1))) {
                        try (var statement = connection.createStatement()) { statement.execute("ALTER TABLE " + table + " ENGINE=InnoDB"); }
                    }
                }
            }
        }
    }
}
