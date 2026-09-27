package cn.campus.jobs.user;

import java.sql.*;
import java.time.*;
import java.util.*;

/** Shared value fields, not a shared user table. */
public record AccountFields(String id, String accountHash, String phoneCipher, String passwordHash,
        String email, String nickname, LocalDate birthday, String bio, String avatarVersion,
        String verificationStatus, String reviewNote, String reviewId,
        LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    public static AccountFields read(ResultSet row) throws SQLException {
        var birthday=row.getDate("birthday"); var login=row.getTimestamp("last_login_at");
        return new AccountFields(row.getString("id"),row.getString("account_hash"),row.getString("phone_cipher"),row.getString("password_hash"),row.getString("email"),row.getString("nickname"),birthday==null?null:birthday.toLocalDate(),row.getString("bio"),row.getString("avatar_version"),row.getString("verification_status"),row.getString("review_note"),row.getString("review_id"),login==null?null:login.toLocalDateTime(),row.getTimestamp("created_at").toLocalDateTime());
    }
    public Map<String,Object> toMap() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("id",id);v.put("account_hash",accountHash);v.put("phone_cipher",phoneCipher);v.put("password_hash",passwordHash);
        v.put("email",email);v.put("nickname",nickname);v.put("birthday",birthday);v.put("bio",bio);v.put("avatar_version",avatarVersion);
        v.put("verification_status",verificationStatus);v.put("review_note",reviewNote);v.put("review_id",reviewId);v.put("last_login_at",lastLoginAt);v.put("created_at",createdAt);
        v.put("can_publish",0);v.put("can_accept",0);return v;
    }
}
