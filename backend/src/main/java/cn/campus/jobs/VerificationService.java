package cn.campus.jobs;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationService {
    private final UserRepository users;
    private final Crypto crypto;
    public VerificationService(UserRepository users, Crypto crypto) { this.users = users; this.crypto = crypto; }
    @Transactional
    public void submit(String id, ApiController.Verification input) {
        Map<String,Object> user = users.jdbc().queryForMap("SELECT * FROM app_user WHERE id=? FOR UPDATE", id);
        if (!java.util.Set.of("UNVERIFIED", "REJECTED").contains(user.get("verification_status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不能重复提交认证");
        String reviewId = UUID.randomUUID().toString();
        if (user.get("role").equals("PUBLISHER")) {
            require(input.organization(), 150); require(input.surname(), 40); require(input.name(), 80);
            if (!validIdentity(input.identityNumber())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的18位身份证号码");
            users.jdbc().update("UPDATE app_user SET organization=?,surname=?,name_cipher=?,identity_cipher=? WHERE id=?", input.organization().trim(), input.surname().trim(), crypto.encrypt(input.name().trim()), crypto.encrypt(input.identityNumber().toUpperCase()), id);
        } else {
            require(input.school(),150); require(input.name(),80); require(input.studentNumber(),50);
            users.jdbc().update("UPDATE app_user SET school=?,name_cipher=?,student_number_cipher=? WHERE id=?", input.school().trim(), crypto.encrypt(input.name().trim()), crypto.encrypt(input.studentNumber().trim()), id);
        }
        users.jdbc().update("UPDATE app_user SET email=?,verification_status='PENDING',review_note=?,review_id=?,can_publish=0,can_accept=0 WHERE id=?", input.email(), "资料已提交，等待审核服务处理", reviewId, id);
        users.jdbc().update("INSERT INTO verification_event(id,user_id,status,note) VALUES(?,?,?,?)", reviewId,id,"PENDING","等待审核服务接入；不自动通过");
    }
    /** Adapter boundary for a future trusted AI/manual review worker. Never expose as a public approval API. */
    @Transactional
    public void applyDecision(String id, String reviewId, boolean approved, String note) {
        Map<String,Object> user = users.jdbc().queryForMap("SELECT * FROM app_user WHERE id=? FOR UPDATE", id);
        if (!"PENDING".equals(user.get("verification_status")) || !reviewId.equals(user.get("review_id"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "审核记录已变更，请刷新后重试");
        String status = approved ? "APPROVED" : "REJECTED";
        users.jdbc().update("UPDATE app_user SET verification_status=?,review_note=?,can_publish=?,can_accept=? WHERE id=?", status,note, approved && user.get("role").equals("PUBLISHER") ? 1 : 0, approved && user.get("role").equals("STUDENT") ? 1 : 0,id);
        users.jdbc().update("INSERT INTO verification_event(id,user_id,status,note) VALUES(?,?,?,?)",UUID.randomUUID().toString(),id,status,note);
    }
    private void require(String value,int max) {
        if (value==null || value.isBlank() || value.length()>max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请完整填写实名认证资料");
    }
    static boolean validIdentity(String value) {
        if (value == null || !value.matches("[1-9][0-9]{16}[0-9Xx]")) return false;
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(value.substring(6,14), java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(java.time.format.ResolverStyle.STRICT));
            if (date.isAfter(java.time.LocalDate.now()) || date.getYear()<1900) return false;
        } catch (Exception e) { return false; }
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};
        int sum=0; for (int i=0;i<17;i++) sum+=(value.charAt(i)-'0')*weights[i];
        return "10X98765432".charAt(sum%11)==Character.toUpperCase(value.charAt(17));
    }
}
