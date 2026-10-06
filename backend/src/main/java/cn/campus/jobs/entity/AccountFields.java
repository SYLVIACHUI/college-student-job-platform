package cn.campus.jobs.entity;

import java.time.*;
import java.util.*;

/** Shared value fields, not a shared user table. */
public record AccountFields(String id, String accountHash, String phoneCipher, String passwordHash,
        String email, String nickname, LocalDate birthday, String bio, String avatarVersion,
        String verificationStatus, String reviewNote, String reviewId,
        LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    public Map<String,Object> toMap() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("id",id);v.put("account_hash",accountHash);v.put("phone_cipher",phoneCipher);v.put("password_hash",passwordHash);
        v.put("email",email);v.put("nickname",nickname);v.put("birthday",birthday);v.put("bio",bio);v.put("avatar_version",avatarVersion);
        v.put("verification_status",verificationStatus);v.put("review_note",reviewNote);v.put("review_id",reviewId);v.put("last_login_at",lastLoginAt);v.put("created_at",createdAt);
        v.put("can_publish",0);v.put("can_accept",0);return v;
    }
}
