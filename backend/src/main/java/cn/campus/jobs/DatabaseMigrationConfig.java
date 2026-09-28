package cn.campus.jobs;

import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import db.migration.V2__Ensure_transactional_tables;
import db.migration.V4__Split_publisher_and_student_users;

/** Legacy scripts are byte-for-byte copies, so existing migration history is never repaired or rewritten. */
@Configuration
public class DatabaseMigrationConfig {
    // Verified against the original V1 resource and the deployed database's history.
    private static final int LEGACY_V1_CHECKSUM=-2131711559;
    @Bean FlywayConfigurationCustomizer schemaHistoryLocations(DataSource source){
        return config->{try(var connection=source.getConnection()){configure(config,connection);}catch(Exception e){throw new IllegalStateException("Cannot determine database migration lineage",e);}};
    }
    public static void configure(FluentConfiguration config,Connection connection)throws Exception {
        boolean legacy=false;
        boolean mysql="MySQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        try(var tables=connection.getMetaData().getTables(connection.getCatalog(),mysql?null:connection.getSchema(),"flyway_schema_history",new String[]{"TABLE"})){
            if(tables.next())try(var s=connection.createStatement();var r=s.executeQuery("SELECT checksum FROM flyway_schema_history WHERE version='1' AND success=TRUE")){
                if(r.next())legacy=r.getInt(1)==LEGACY_V1_CHECKSUM;
            }
        }
        config.locations(legacy?"classpath:db/legacy-migration":"classpath:db/migration","classpath:db/common-migration");
        if(legacy)config.javaMigrations(new V2__Ensure_transactional_tables(),new V4__Split_publisher_and_student_users());
    }
}
