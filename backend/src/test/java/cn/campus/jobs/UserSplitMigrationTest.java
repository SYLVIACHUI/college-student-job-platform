package cn.campus.jobs;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import cn.campus.jobs.user.*;
import static org.junit.jupiter.api.Assertions.*;

class UserSplitMigrationTest {
    @Test void jobUpgradeKeepsLegacyParticipantsAndDailyPay() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:job_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).target("4").load().migrate();
        var db=new JdbcTemplate(ds);var users=new UserRepository(db,new PublisherUserRepository(db),new StudentUserRepository(db));
        users.create("company","PUBLISHER","company-hash","cipher","hash");
        for(String id:List.of("one","two"))users.create(id,"STUDENT",id,"cipher","hash");
        db.update("INSERT INTO job(id,publisher_id,title,description,location,pay) VALUES('legacy','company','旧岗位','原工作内容','校内',150)");
        for(String id:List.of("one","two"))db.update("INSERT INTO job_application(id,job_id,student_id) VALUES(?,'legacy',?)",id,id);
        Flyway.configure().dataSource(ds).load().migrate();
        var job=db.queryForMap("SELECT * FROM job WHERE id='legacy'");
        assertEquals(2,((Number)job.get("required_count")).intValue());assertEquals("DAY",job.get("pay_unit"));
        assertNull(job.get("starts_at"));assertNull(job.get("duration_minutes"));
        assertEquals(0,new java.math.BigDecimal("150.00").compareTo((java.math.BigDecimal)job.get("pay")));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class));
    }
    @Test void upgradePreservesAccountsAndRelatedRecords() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:split_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).target("3").load().migrate();
        var db=new JdbcTemplate(ds);
        for(String role:List.of("PUBLISHER","STUDENT")) {
            db.update("INSERT INTO app_user(id,role,account_hash,phone_cipher,password_hash,name_cipher,nickname,birthday,verification_status,can_publish,can_accept,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                role,role,"same-phone-hash","unchanged-phone-cipher","unchanged-password-hash","unchanged-name-cipher",role,"2002-05-06","APPROVED",role.equals("PUBLISHER")?1:0,role.equals("STUDENT")?1:0,"2026-01-02 03:04:05");
            db.update("INSERT INTO wallet(user_id,balance_cents,frozen_cents) VALUES(?,12345,500)",role);
            db.update("INSERT INTO user_avatar(user_id,image_data) VALUES(?,?)",role,new byte[]{1,2,3});
            db.update("INSERT INTO verification_event(id,user_id,status,note) VALUES(?,?,'APPROVED','保留审核记录')",role,role);
        }
        db.update("UPDATE app_user SET organization='企业',surname='张',identity_cipher='identity-cipher' WHERE role='PUBLISHER'");
        db.update("UPDATE app_user SET school='学校',student_number_cipher='student-cipher',grade='大三',major='计算机' WHERE role='STUDENT'");
        db.update("INSERT INTO job(id,publisher_id,title,description,location,pay) VALUES('job','PUBLISHER','兼职','说明','学校',100)");
        db.update("INSERT INTO job_application(id,job_id,student_id) VALUES('application','job','STUDENT')");
        db.update("INSERT INTO job_payment(id,application_id,publisher_id,student_id,amount_cents) VALUES('payment','application','PUBLISHER','STUDENT',10000)");
        db.update("INSERT INTO wallet_operation(id,actor_id,request_key,kind,target_id,amount_cents) VALUES('operation','PUBLISHER','key','PAYMENT','STUDENT',10000)");
        db.update("INSERT INTO wallet_entry(id,user_id,operation_id,kind,delta_cents,frozen_delta_cents,balance_after_cents,frozen_after_cents,description) VALUES('entry','STUDENT','operation','PAYMENT',10000,0,12345,500,'兼职费')");
        db.update("INSERT INTO withdrawal(id,user_id,amount_cents) VALUES('withdrawal','STUDENT',500)");
        var before=db.queryForList("SELECT * FROM app_user ORDER BY id");

        Flyway.configure().dataSource(ds).load().migrate();
        assertEquals(before,db.queryForList("SELECT * FROM app_user_legacy_v3 ORDER BY id"));
        for(String table:List.of("publisher_user","student_user")) {
            var migrated=db.queryForMap("SELECT * FROM "+table);
            var original=before.stream().filter(row->row.get("id").equals(migrated.get("id"))).findFirst().orElseThrow();
            migrated.forEach((key,value)->assertEquals(original.get(key),value,table+"."+key));
        }
        var publishers=new PublisherUserRepository(db);var students=new StudentUserRepository(db);
        assertNotNull(publishers.findByAccount("same-phone-hash"));assertNotNull(students.findByAccount("same-phone-hash"));
        assertNull(publishers.findById("STUDENT",false));assertNull(students.findById("PUBLISHER",false));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM account_identity",Integer.class));
        assertEquals(12345L,db.queryForObject("SELECT balance_cents FROM wallet WHERE user_id='STUDENT'",Long.class));
        assertEquals(500L,db.queryForObject("SELECT frozen_cents FROM wallet WHERE user_id='STUDENT'",Long.class));
        assertArrayEquals(new byte[]{1,2,3},db.queryForObject("SELECT image_data FROM user_avatar WHERE user_id='STUDENT'",byte[].class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM job j JOIN publisher_user p ON p.id=j.publisher_id JOIN job_application a ON a.job_id=j.id JOIN student_user s ON s.id=a.student_id JOIN job_payment pay ON pay.application_id=a.id",Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE job SET publisher_id='STUDENT' WHERE id='job'"));
        assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE job_application SET student_id='PUBLISHER' WHERE id='application'"));
        // An active record has no remaining dependency on the archived table.
        db.execute("DROP TABLE app_user_legacy_v3");
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM wallet_entry",Integer.class));
    }
}

