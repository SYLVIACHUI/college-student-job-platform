package cn.campus.jobs.user;
import java.sql.*;
import java.util.Map;

/** Publisher entity, persisted exclusively in publisher_user. */
public record PublisherUser(AccountFields account,String organization,String surname,String nameCipher,String identityCipher,int canPublish) implements UserAccount {
    public String role(){return "PUBLISHER";}
    public static PublisherUser read(ResultSet r)throws SQLException{return new PublisherUser(AccountFields.read(r),r.getString("organization"),r.getString("surname"),r.getString("name_cipher"),r.getString("identity_cipher"),r.getInt("can_publish"));}
    public Map<String,Object> toMap(){var v=account.toMap();v.put("role",role());v.put("organization",organization);v.put("surname",surname);v.put("name_cipher",nameCipher);v.put("identity_cipher",identityCipher);v.put("can_publish",canPublish);return v;}
}
