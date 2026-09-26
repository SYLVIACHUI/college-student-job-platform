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
            create("PUBLISHER", "13800009001", publisherPassword);
            create("STUDENT", "13800009002", studentPassword);
        });
        SpringApplication.exit(context);
    }
    private void validate(String password) {
        if (!password.matches("(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}")) {
            throw new IllegalArgumentException("Bootstrap password must meet registration password rules");
        }
    }
    private void create(String role, String phone, String password) {
        var encoder = new BCryptPasswordEncoder(12);
        var existing = users.byAccount(role, crypto.hash(phone));
        if (existing != null) {
            if (!encoder.matches(password, (String) existing.get("password_hash"))) {
                throw new IllegalStateException("Existing " + role + " account has another password; nothing overwritten");
            }
            if (!phone.equals(crypto.decrypt((String) existing.get("phone_cipher")))) {
                throw new IllegalStateException("Existing account encryption mismatch");
            }
            System.out.println("Bootstrap " + role + ": existing account verified, unchanged");
            return;
        }
        String id=UUID.randomUUID().toString();
        users.jdbc().update("INSERT INTO app_user(id,role,account_hash,phone_cipher,password_hash) VALUES(?,?,?,?,?)",
            id, role, crypto.hash(phone), crypto.encrypt(phone), encoder.encode(password));
        users.jdbc().update("INSERT INTO wallet(user_id) VALUES(?)",id);
        System.out.println("Bootstrap " + role + ": created, identity verification still required");
    }
}
