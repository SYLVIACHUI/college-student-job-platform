package cn.campus.jobs;

import cn.campus.jobs.mapper.PublisherUserMapper;
import cn.campus.jobs.mapper.StudentUserMapper;
import cn.campus.jobs.mapper.AccountMapper;
import cn.campus.jobs.mapper.UserRepository;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

class UserSplitMigrationTest {
    private org.mybatis.spring.SqlSessionTemplate session(javax.sql.DataSource source) {
        try {
            var factory=new org.mybatis.spring.SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new org.springframework.core.io.support.PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
            var configuration=new org.apache.ibatis.session.Configuration();
            configuration.setCallSettersOnNulls(true);
            configuration.setReturnInstanceForEmptyRow(true);
            configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
            factory.setConfiguration(configuration);
            return new org.mybatis.spring.SqlSessionTemplate(factory.getObject());
        } catch(Exception e) {throw new IllegalStateException("Cannot create migration-test mappers",e);}
    }
    private UserRepository users(javax.sql.DataSource source) {
        var session=session(source);
        return new UserRepository(session.getMapper(AccountMapper.class),session.getMapper(PublisherUserMapper.class),session.getMapper(StudentUserMapper.class));
    }
    @Test void screeningUpgradeKeepsExistingJobsAndApplications() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:screening_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/common-migration").target("8").load().migrate();
        var db=new JdbcTemplate(ds);var users=users(ds);
        users.create("company","PUBLISHER","company-hash","cipher","hash");users.create("student","STUDENT","student-hash","cipher","hash");
        db.update("INSERT INTO job(id,publisher_id,title,description,location,pay) VALUES('existing','company','原有岗位','说明','校内',100)");
        db.update("INSERT INTO job_application(id,job_id,student_id) VALUES('enrolled','existing','student')");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/common-migration").load().migrate();
        assertEquals("DIRECT",db.queryForObject("SELECT recruitment_mode FROM job WHERE id='existing'",String.class));
        assertEquals("ACTIVE",db.queryForObject("SELECT status FROM job_application WHERE id='enrolled'",String.class));
        assertNull(db.queryForObject("SELECT resume_cipher FROM job_application WHERE id='enrolled'",String.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class));
        assertEquals(0,db.queryForObject("SELECT remaining_count FROM job WHERE id='existing'",Integer.class));
    }
    @Test void jobUpgradeKeepsLegacyParticipantsAndDailyPay() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:job_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/legacy-migration","classpath:db/common-migration").javaMigrations(new db.migration.V2__Ensure_transactional_tables(),new db.migration.V4__Split_publisher_and_student_users()).target("4").load().migrate();
        var db=new JdbcTemplate(ds);var users=users(ds);
        users.create("company","PUBLISHER","company-hash","cipher","hash");
        for(String id:List.of("one","two"))users.create(id,"STUDENT",id,"cipher","hash");
        db.update("INSERT INTO job(id,publisher_id,title,description,location,pay) VALUES('legacy','company','旧岗位','原工作内容','校内',150)");
        for(String id:List.of("one","two"))db.update("INSERT INTO job_application(id,job_id,student_id) VALUES(?,'legacy',?)",id,id);
        Flyway.configure().dataSource(ds).locations("classpath:db/legacy-migration","classpath:db/common-migration").javaMigrations(new db.migration.V2__Ensure_transactional_tables(),new db.migration.V4__Split_publisher_and_student_users()).load().migrate();
        var job=db.queryForMap("SELECT * FROM job WHERE id='legacy'");
        assertEquals(2,((Number)job.get("required_count")).intValue());assertEquals("DAY",job.get("pay_unit"));
        assertNull(job.get("starts_at"));assertNull(job.get("duration_minutes"));
        assertEquals(0,new java.math.BigDecimal("150.00").compareTo((java.math.BigDecimal)job.get("pay")));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class));
        assertEquals(0,db.queryForObject("SELECT remaining_count FROM job WHERE id='legacy'",Integer.class));
    }
    @Test void capacityUpgradeCountsOnlyActiveApplicationsAndKeepsUniqueConstraint() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:capacity_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/common-migration").target("9").load().migrate();
        var db=new JdbcTemplate(ds);var users=users(ds);
        users.create("company","PUBLISHER","company-hash","cipher","hash");
        db.update("INSERT INTO job(id,publisher_id,title,description,location,pay,required_count,recruitment_mode) VALUES('screening','company','筛选岗位','说明','校内',100,3,'SCREENING')");
        for(String status:List.of("ACTIVE","PENDING","WITHDRAWN","REJECTED")){
            users.create(status,"STUDENT",status,"cipher","hash");
            db.update("INSERT INTO job_application(id,job_id,student_id,status) VALUES(?,'screening',?,?)",status,status,status);
        }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/common-migration").load().migrate();
        assertEquals(2,db.queryForObject("SELECT remaining_count FROM job WHERE id='screening'",Integer.class));
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM job_application",Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->db.update("INSERT INTO job_application(id,job_id,student_id) VALUES('duplicate','screening','ACTIVE')"));
        assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE job SET remaining_count=-1 WHERE id='screening'"));
        assertThrows(DataIntegrityViolationException.class,()->db.update("UPDATE job SET remaining_count=4 WHERE id='screening'"));
    }
    @Test void upgradePreservesAccountsAndRelatedRecords() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:split_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/legacy-migration","classpath:db/common-migration").javaMigrations(new db.migration.V2__Ensure_transactional_tables(),new db.migration.V4__Split_publisher_and_student_users()).target("3").load().migrate();
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

        Flyway.configure().dataSource(ds).locations("classpath:db/legacy-migration","classpath:db/common-migration").javaMigrations(new db.migration.V2__Ensure_transactional_tables(),new db.migration.V4__Split_publisher_and_student_users()).load().migrate();
        assertEquals(before,db.queryForList("SELECT * FROM app_user_legacy_v3 ORDER BY id"));
        for(String table:List.of("publisher_user","student_user")) {
            var migrated=db.queryForMap("SELECT * FROM "+table);
            var original=before.stream().filter(row->row.get("id").equals(migrated.get("id"))).findFirst().orElseThrow();
            migrated.forEach((key,value)->assertEquals(original.get(key),value,table+"."+key));
        }
        var session=session(ds);var publishers=session.getMapper(PublisherUserMapper.class);var students=session.getMapper(StudentUserMapper.class);
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

