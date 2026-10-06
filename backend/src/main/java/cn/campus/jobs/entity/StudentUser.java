package cn.campus.jobs.entity;
import java.util.Map;

/** 学生实体 */
public record StudentUser(AccountFields account,String school,String nameCipher,String studentNumberCipher,String grade,String major,int canAccept) implements UserAccount {
    public String role(){return "STUDENT";}
    public Map<String,Object> toMap(){var v=account.toMap();v.put("role",role());v.put("school",school);v.put("name_cipher",nameCipher);v.put("student_number_cipher",studentNumberCipher);v.put("grade",grade);v.put("major",major);v.put("can_accept",canAccept);return v;}
}
