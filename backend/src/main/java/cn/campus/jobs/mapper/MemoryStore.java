package cn.campus.jobs.mapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("dev & !docker")
public class MemoryStore implements ExpiringStore {
    private record Entry(String value, long expires) {}
    private final Map<String, Entry> entries = new HashMap<>();
    public synchronized void put(String key, String value, Duration ttl) {
        entries.entrySet().removeIf(e -> e.getValue().expires <= System.currentTimeMillis());
        entries.put(key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }
    public synchronized String get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (entry.expires <= System.currentTimeMillis()) { entries.remove(key); return null; }
        return entry.value;
    }
    public synchronized void delete(String key) { entries.remove(key); }
    public synchronized long increment(String key, Duration ttl) {
        String old = get(key);
        long next = old == null ? 1 : Long.parseLong(old) + 1;
        if (old == null) put(key, "1", ttl);
        else entries.put(key, new Entry(Long.toString(next), entries.get(key).expires));
        return next;
    }
    public synchronized boolean consume(String key, String expected) {
        if (!expected.equals(get(key))) return false;
        delete(key); return true;
    }
}
