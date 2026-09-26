package cn.campus.jobs;

import java.util.Map;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    public Map<String,Object> byId(String id) {
        List<Map<String,Object>> users = jdbc.queryForList("SELECT * FROM app_user WHERE id=?", id);
        if (users.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请重新登录");
        return users.get(0);
    }
    public Map<String,Object> byAccount(String role, String hash) {
        List<Map<String,Object>> users = jdbc.queryForList("SELECT * FROM app_user WHERE role=? AND account_hash=?", role, hash);
        return users.isEmpty() ? null : users.get(0);
    }
}
