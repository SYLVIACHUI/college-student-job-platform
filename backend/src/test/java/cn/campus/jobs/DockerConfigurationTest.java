package cn.campus.jobs;

import cn.campus.jobs.mapper.ExpiringStore;
import cn.campus.jobs.mapper.MemoryStore;
import cn.campus.jobs.mapper.RedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DockerConfigurationTest {
    @Configuration(proxyBeanMethods=false)
    @Import({MemoryStore.class,RedisStore.class})
    static class Stores {
        @Bean StringRedisTemplate redisTemplate(){return mock(StringRedisTemplate.class);}
    }
    private ApplicationContextRunner context(String profiles) {
        return new ApplicationContextRunner()
            .withPropertyValues("spring.profiles.active="+profiles,
                "DB_NAME=campusjobs","DB_USERNAME=docker_test_user",
                "DB_PASSWORD=docker-test-database-password","REDIS_PASSWORD=docker-test-redis-password",
                "APP_ENCRYPTION_KEY=ZGV2LW9ubHkta2V5LTMyaHl0ZXMtbG9uZy0xMjM0NTY=",
                "APP_LOOKUP_KEY=docker-test-independent-lookup-key-32-characters")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Stores.class);
    }
    @Test void dockerDemoUsesMysqlAndRedisInsteadOfLocalDevStores() {
        context("dev,docker").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(ExpiringStore.class).hasSingleBean(RedisStore.class).doesNotHaveBean(MemoryStore.class);
            assertThat(c.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:mysql://mysql:3306/campusjobs?");
            assertThat(c.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("docker_test_user");
            assertThat(c.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("docker-test-database-password");
            assertThat(c.getEnvironment().getProperty("spring.data.redis.host")).isEqualTo("redis");
            assertThat(c.getEnvironment().getProperty("spring.data.redis.password")).isEqualTo("docker-test-redis-password");
            assertThat(c.getEnvironment().getProperty("app.secure-cookie")).isEqualTo("false");
        });
    }
    @Test void dockerProductionUsesRedisAndCanEnableSecureCookies() {
        context("prod,docker").withPropertyValues("APP_SECURE_COOKIE=true").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(RedisStore.class).doesNotHaveBean(MemoryStore.class);
            assertThat(c.getEnvironment().getProperty("app.secure-cookie")).isEqualTo("true");
        });
    }
    @Test void baseConfigurationUsesProvidedLocalConnectionSettings() {
        context("prod").withPropertyValues("DB_HOST=database.test","DB_PORT=3307","DB_NAME=custom_jobs",
            "REDIS_HOST=cache.test","REDIS_PORT=6380","REDIS_DATABASE=2").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(RedisStore.class).doesNotHaveBean(MemoryStore.class);
            assertThat(c.getEnvironment().getProperty("spring.datasource.url")).startsWith("jdbc:mysql://database.test:3307/custom_jobs?");
            assertThat(c.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("docker_test_user");
            assertThat(c.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("docker-test-database-password");
            assertThat(c.getEnvironment().getProperty("spring.data.redis.host")).isEqualTo("cache.test");
            assertThat(c.getEnvironment().getProperty("spring.data.redis.port")).isEqualTo("6380");
            assertThat(c.getEnvironment().getProperty("spring.data.redis.database")).isEqualTo("2");
        });
    }
}
