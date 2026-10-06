package cn.campus.jobs.mapper;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component @Profile("!dev | docker")
public class RedisStore implements ExpiringStore {
    private final StringRedisTemplate redis;
    public RedisStore(StringRedisTemplate redis) { this.redis = redis; }
    public void put(String key, String value, Duration ttl) { redis.opsForValue().set(key, value, ttl); }
    public String get(String key) { return redis.opsForValue().get(key); }
    public void delete(String key) { redis.delete(key); }
    public long increment(String key, Duration ttl) {
        Long value = redis.execute(new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return n", Long.class), List.of(key), Long.toString(ttl.toMillis()));
        return value == null ? Long.MAX_VALUE : value;
    }
    public boolean consume(String key, String expected) {
        Long value = redis.execute(new DefaultRedisScript<>("if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]); else return 0; end", Long.class), List.of(key), expected);
        return Long.valueOf(1).equals(value);
    }
}
