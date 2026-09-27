package cn.campus.jobs.user;
import java.sql.*;
import java.util.Map;

/** Student entity, persisted exclusively in student_user. */
public record StudentUser(AccountFields account,String school,String nameCipher,String studentNumberCipher,String grade,String major,int canAccept) implements UserAccount {
    public String role(){return "STUDENT";}
    public static StudentUser read(ResultSet r)throws SQLException{return new StudentUser(AccountFields.read(r),r.getString("school"),r.getString("name_cipher"),r.getString("student_number_cipher"),r.getString("grade"),r.getString("major"),r.getInt("can_accept"));}
    public Map<String,Object> toMap(){var v=account.toMap();v.put("role",role());v.put("school",school);v.put("name_cipher",nameCipher);v.put("student_number_cipher",studentNumberCipher);v.put("grade",grade);v.put("major",major);v.put("can_accept",canAccept);return v;}
}
