package cn.campus.jobs.user;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PublisherUserRepository {
    private final JdbcTemplate jdbc;
    public PublisherUserRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public PublisherUser findById(String id,boolean lock){var rows=jdbc.query("SELECT * FROM publisher_user WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->PublisherUser.read(r),id);return rows.isEmpty()?null:rows.get(0);}
    public PublisherUser findByAccount(String hash){var rows=jdbc.query("SELECT * FROM publisher_user WHERE account_hash=?",(r,n)->PublisherUser.read(r),hash);return rows.isEmpty()?null:rows.get(0);}
    public void insert(String id,String hash,String phoneCipher,String passwordHash){jdbc.update("INSERT INTO publisher_user(id,account_hash,phone_cipher,password_hash) VALUES(?,?,?,?)",id,hash,phoneCipher,passwordHash);}
}
