package cn.campus.jobs;

import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, one-shot local provisioning. Disabled by default; never resets existing users. */
@Component
@ConditionalOnProperty(name = "app.bootstrap-test-accounts", havingValue = "true")
public class TestAccountBootstrap implements CommandLineRunner {
    private final UserRepository users;
    private final Crypto crypto;
    private final Environment env;
    private final TransactionTemplate transaction;
    private final ConfigurableApplicationContext context;
    public TestAccountBootstrap(UserRepository users, Crypto crypto, Environment env,
            TransactionTemplate transaction, ConfigurableApplicationContext context) {
        this.users = users; this.crypto = crypto; this.env = env;
        this.transaction = transaction; this.context = context;
    }
    @Override public void run(String... args) {
        if (!"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type"))) {
            throw new IllegalStateException("Provisioning requires spring.main.web-application-type=none");
        }
        String publisherPassword = env.getRequiredProperty("BOOTSTRAP_PUBLISHER_PASSWORD");
        String studentPassword = env.getRequiredProperty("BOOTSTRAP_STUDENT_PASSWORD");
        validate(publisherPassword); validate(studentPassword);
        transaction.executeWithoutResult(status -> {
            create("PUBLISHER", env.getProperty("BOOTSTRAP_PUBLISHER_PHONE","13800009001"), publisherPassword);
            create("STUDENT", env.getProperty("BOOTSTRAP_STUDENT_PHONE","13800009002"), studentPassword);
        });
        SpringApplication.exit(context);
    }
    private void validate(String password) {
        if (!password.matches("(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}")) {
            throw new IllegalArgumentException("Bootstrap password must meet registration password rules");
        }
    }
    private void create(String role, String phone, String password) {
        if(!phone.matches("1[3-9][0-9]{9}"))throw new IllegalArgumentException("Invalid test phone");
        boolean verified=env.getProperty("app.bootstrap-verified-test-accounts",Boolean.class,false);
        var encoder = new BCryptPasswordEncoder(12);
        var existing = users.byAccount(role, crypto.hash(phone));
        if (existing != null) {
            if (!encoder.matches(password, (String) existing.get("password_hash"))) {
                throw new IllegalStateException("Existing " + role + " account has another password; nothing overwritten");
            }
            if (!phone.equals(crypto.decrypt((String) existing.get("phone_cipher")))) {
                throw new IllegalStateException("Existing account encryption mismatch");
            }
            if(verified && !"APPROVED".equals(existing.get("verification_status")))throw new IllegalStateException("Existing account is not approved; refusing to overwrite identity");
            System.out.println("Bootstrap " + role + ": existing account verified, unchanged");
            return;
        }
        String id=UUID.randomUUID().toString();
        users.create(id, role, crypto.hash(phone), crypto.encrypt(phone), encoder.encode(password));
        users.jdbc().update("INSERT INTO wallet(user_id) VALUES(?)",id);
        if(verified){
            String reviewId=UUID.randomUUID().toString();
            String note="用户授权创建的已认证测试账号，仅用于功能联调，未经过真实身份核验";
            if("PUBLISHER".equals(role))users.jdbc().update("UPDATE publisher_user SET organization=?,surname=?,name_cipher=?,identity_cipher=?,nickname=?,verification_status='APPROVED',can_publish=1,review_id=?,review_note=? WHERE id=?","贝鱼测试企业（演示）","测试",crypto.encrypt("发布人"),crypto.encrypt("TEST-IDENTITY-NOT-REAL"),"测试企业",reviewId,note,id);
            else users.jdbc().update("UPDATE student_user SET school=?,name_cipher=?,student_number_cipher=?,nickname=?,grade=?,major=?,verification_status='APPROVED',can_accept=1,review_id=?,review_note=? WHERE id=?","演示大学（测试）",crypto.encrypt("测试同学"),crypto.encrypt("TEST20260001"),"测试同学","大三","计算机科学与技术",reviewId,note,id);
            users.jdbc().update("INSERT INTO verification_event(id,user_id,status,note) VALUES(?,?,'APPROVED',?)",reviewId,id,note);
            var saved=users.byId(id);
            if(!encoder.matches(password,(String)saved.get("password_hash"))||!phone.equals(crypto.decrypt((String)saved.get("phone_cipher")))||!"APPROVED".equals(saved.get("verification_status")))throw new IllegalStateException("Test account verification failed");
        }
        System.out.println("Bootstrap " + role + (verified?": created APPROVED test account; password and encryption checked":": created, identity verification still required"));
    }
}
