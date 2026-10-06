package cn.campus.jobs.service;

import cn.campus.jobs.mapper.ReviewAdminMapper;


import java.util.UUID;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Explicit one-shot provisioning; no public registration and no default runtime password. */
@Component @ConditionalOnProperty(name="app.bootstrap-review-admin",havingValue="true")
public class ReviewAdminBootstrap implements CommandLineRunner {
    private final ReviewAdminMapper mapper;
    private final Environment env;private final ConfigurableApplicationContext context;
    public ReviewAdminBootstrap(Environment env,ConfigurableApplicationContext context,ReviewAdminMapper mapper){ this.mapper=mapper;this.env=env;this.context=context;}
    public void run(String... args){
        if(!"none".equalsIgnoreCase(env.getProperty("spring.main.web-application-type")))throw new IllegalStateException("Admin provisioning requires non-web startup");
        String username=env.getRequiredProperty("REVIEW_ADMIN_USERNAME"),password=env.getRequiredProperty("REVIEW_ADMIN_PASSWORD");
        if(!username.matches("[A-Za-z0-9_-]{3,60}")||!password.matches("(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{12,64}"))throw new IllegalArgumentException("Invalid admin credentials");
        var encoder=new BCryptPasswordEncoder(12);var rows=mapper.passwordHash(username);
        if(rows.isEmpty())mapper.insertAdmin(UUID.randomUUID().toString(),username,encoder.encode(password),"实名认证审核员");
        else if(!encoder.matches(password,rows.get(0).get("password_hash").toString()))throw new IllegalStateException("Existing admin unchanged: password mismatch");
        System.out.println("Review administrator provisioned; existing accounts never overwritten");SpringApplication.exit(context);
    }
}
