package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve IDs/ciphertext and redirect dependent foreign keys before archiving the old table. */
public class V4__Split_publisher_and_student_users extends BaseJavaMigration {
    private static final String COMMON="id,role,account_hash,phone_cipher,password_hash,email,nickname,birthday,bio,avatar_version,verification_status,review_note,review_id,last_login_at,created_at";
    @Override public void migrate(Context context)throws Exception {
        Connection c=context.getConnection();boolean mysql="MySQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName());String engine=mysql?" ENGINE=InnoDB":"";
        try(var tables=c.getMetaData().getTables(c.getCatalog(),mysql?null:c.getSchema(),"app_user",new String[]{"TABLE"})){
            if(!tables.next()){
                // New V1 already creates the role tables. Never recreate or copy them.
                count(c,"SELECT COUNT(*) FROM publisher_user");count(c,"SELECT COUNT(*) FROM student_user");return;
            }
        }
        execute(c,"CREATE TABLE account_identity (id VARCHAR(36) PRIMARY KEY,role VARCHAR(16) NOT NULL,UNIQUE(id,role))"+engine);
        execute(c,"INSERT INTO account_identity(id,role) SELECT id,role FROM app_user");
        String common="id VARCHAR(36) PRIMARY KEY,role VARCHAR(16) NOT NULL DEFAULT '%s',account_hash VARCHAR(64) NOT NULL UNIQUE,phone_cipher VARCHAR(512) NOT NULL,password_hash VARCHAR(100) NOT NULL,email VARCHAR(254),nickname VARCHAR(40),birthday DATE,bio VARCHAR(300),avatar_version VARCHAR(36),verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',review_note VARCHAR(500),review_id VARCHAR(36),last_login_at TIMESTAMP NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,";
        execute(c,"CREATE TABLE publisher_user ("+common.formatted("PUBLISHER")+"organization VARCHAR(150),surname VARCHAR(40),name_cipher VARCHAR(512),identity_cipher VARCHAR(512),can_publish INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(id,role) REFERENCES account_identity(id,role),CHECK(role='PUBLISHER'))"+engine);
        execute(c,"CREATE TABLE student_user ("+common.formatted("STUDENT")+"school VARCHAR(150),name_cipher VARCHAR(512),student_number_cipher VARCHAR(512),grade VARCHAR(10),major VARCHAR(100),can_accept INTEGER NOT NULL DEFAULT 0,FOREIGN KEY(id,role) REFERENCES account_identity(id,role),CHECK(role='STUDENT'))"+engine);
        String publisher=COMMON+",organization,surname,name_cipher,identity_cipher,can_publish",student=COMMON+",school,name_cipher,student_number_cipher,grade,major,can_accept";
        execute(c,"INSERT INTO publisher_user("+publisher+") SELECT "+publisher+" FROM app_user WHERE role='PUBLISHER'");
        execute(c,"INSERT INTO student_user("+student+") SELECT "+student+" FROM app_user WHERE role='STUDENT'");
        if(count(c,"SELECT COUNT(*) FROM app_user")!=count(c,"SELECT (SELECT COUNT(*) FROM publisher_user)+(SELECT COUNT(*) FROM student_user)"))throw new SQLException("Account counts mismatch; original table retained");
        Map<String,String[]> links=new LinkedHashMap<>();
        links.put("job",new String[]{"publisher_id","publisher_user"});links.put("job_application",new String[]{"student_id","student_user"});
        for(String table:List.of("verification_event","user_avatar","wallet","withdrawal","wallet_entry"))links.put(table,new String[]{"user_id","account_identity"});
        links.put("wallet_operation",new String[]{"actor_id","account_identity"});
        for(var entry:links.entrySet()){
            List<String> constraints=new ArrayList<>();
            try(var keys=c.getMetaData().getImportedKeys(c.getCatalog(),mysql?null:c.getSchema(),entry.getKey())){while(keys.next())if("app_user".equalsIgnoreCase(keys.getString("PKTABLE_NAME")))constraints.add(keys.getString("FK_NAME"));}
            for(String name:constraints){String quote=mysql?"`":"\"";execute(c,"ALTER TABLE "+entry.getKey()+(mysql?" DROP FOREIGN KEY ":" DROP CONSTRAINT ")+quote+name.replace(quote,quote+quote)+quote);}
            execute(c,"ALTER TABLE "+entry.getKey()+" ADD CONSTRAINT fk_split_"+entry.getKey()+" FOREIGN KEY("+entry.getValue()[0]+") REFERENCES "+entry.getValue()[1]+"(id)");
        }
        execute(c,"ALTER TABLE job_payment ADD CONSTRAINT fk_payment_publisher FOREIGN KEY(publisher_id) REFERENCES publisher_user(id)");
        execute(c,"ALTER TABLE job_payment ADD CONSTRAINT fk_payment_student FOREIGN KEY(student_id) REFERENCES student_user(id)");
        execute(c,"ALTER TABLE app_user RENAME TO app_user_legacy_v3");
    }
    private void execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
    private long count(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
}
