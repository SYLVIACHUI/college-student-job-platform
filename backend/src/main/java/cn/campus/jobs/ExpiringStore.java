package cn.campus.jobs;

import java.time.Duration;

/** Atomic operations are required for one-time codes and distributed rate limits. */
public interface ExpiringStore {
    void put(String key, String value, Duration ttl);
    String get(String key);
    void delete(String key);
    long increment(String key, Duration ttl);
    boolean consume(String key, String expected);
}
