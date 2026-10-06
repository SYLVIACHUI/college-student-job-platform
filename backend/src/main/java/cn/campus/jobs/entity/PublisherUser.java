package cn.campus.jobs.entity;
import java.util.Map;

/** 发布者实体 */
public record PublisherUser(AccountFields account,String organization,String surname,String nameCipher,String identityCipher,int canPublish) implements UserAccount {
    public String role(){return "PUBLISHER";}
    public Map<String,Object> toMap(){var v=account.toMap();v.put("role",role());v.put("organization",organization);v.put("surname",surname);v.put("name_cipher",nameCipher);v.put("identity_cipher",identityCipher);v.put("can_publish",canPublish);return v;}
}
