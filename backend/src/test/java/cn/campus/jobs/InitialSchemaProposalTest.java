package cn.campus.jobs;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class InitialSchemaProposalTest {
    @Test void isolatedFreshSchemaSeparatesUsers(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:proposed_fresh;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration","classpath:db/common-migration").load();flyway.migrate();flyway.validate();
        var db=new JdbcTemplate(ds);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='app_user'",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name IN ('publisher_user','student_user')",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_name='app_user_legacy_v3'",Integer.class));
    }
    @Test void archivedLegacyScriptsRetainOriginalChecksum()throws Exception{
        var ds=new DriverManagerDataSource("jdbc:h2:mem:proposed_legacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(ds).locations("classpath:db/legacy-migration").target("1").load().migrate();
        var db=new JdbcTemplate(ds);
        db.update("INSERT INTO app_user(id,role,account_hash,phone_cipher,password_hash) VALUES('legacy-student','STUDENT','original-hash','original-cipher','original-password')");
        var crc=new CRC32();try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/db/legacy-migration/V1__initial.sql"),StandardCharsets.UTF_8))){String line;while((line=reader.readLine())!=null)crc.update(line.getBytes(StandardCharsets.UTF_8));}
        assertEquals((int)crc.getValue(),new JdbcTemplate(ds).queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'",Integer.class));
        var config=Flyway.configure().dataSource(ds);try(var c=ds.getConnection()){DatabaseMigrationConfig.configure(config,c);}
        config.load().migrate();config.load().validate();
        assertEquals("original-cipher",db.queryForObject("SELECT phone_cipher FROM student_user WHERE id='legacy-student'",String.class));
        assertEquals((int)crc.getValue(),db.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='1'",Integer.class));
    }
    @Test @EnabledIfSystemProperty(named="auditMysql",matches="true")
    void auditCurrentMysqlHistoryReadOnly()throws Exception{
        Map<String,Object> yaml=new org.yaml.snakeyaml.Yaml().load(getClass().getResourceAsStream("/application.yml"));
        Map<String,Object> spring=(Map<String,Object>)yaml.get("spring"),config=(Map<String,Object>)spring.get("datasource");
        String url=resolve(config.get("url")),username=resolve(config.get("username")),password=resolve(config.get("password"));
        try(var c=java.sql.DriverManager.getConnection(url,username,password)){c.setReadOnly(true);
            var flywayConfig=Flyway.configure().dataSource(url,username,password);DatabaseMigrationConfig.configure(flywayConfig,c);flywayConfig.load().validate();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")){while(r.next())System.out.println("MYSQL MIGRATION "+r.getString(1)+" "+r.getString(2)+" checksum="+r.getString(3)+" success="+r.getBoolean(4));}
            try(var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT COUNT(*) FROM publisher_user),(SELECT COUNT(*) FROM student_user)")){assertTrue(r.next());System.out.println("MYSQL separated user counts: "+r.getInt(1)+", "+r.getInt(2));}
        }
    }
    private String resolve(Object value){String s=String.valueOf(value);if(!s.startsWith("${"))return s;int split=s.indexOf(':');String name=s.substring(2,split);return System.getenv().getOrDefault(name,s.substring(split+1,s.length()-1));}
}
