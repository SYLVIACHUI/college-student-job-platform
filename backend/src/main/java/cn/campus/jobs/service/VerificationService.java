package cn.campus.jobs.service;

import cn.campus.jobs.mapper.VerificationMapper;

import cn.campus.jobs.controller.ApiController;
import cn.campus.jobs.mapper.UserRepository;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VerificationService {
    private final VerificationMapper mapper;
    private final UserRepository users;
    private final Crypto crypto;
    private final NotificationService notifications;
    public VerificationService(UserRepository users, Crypto crypto,NotificationService notifications,VerificationMapper mapper) { this.mapper=mapper; this.users = users; this.crypto = crypto; this.notifications=notifications; }
    public List<Map<String,Object>> events(String userId) { return mapper.events(userId); }
    @Transactional
    public void submit(String id, ApiController.Verification input) {
        Map<String,Object> user = users.lockById(id);
        if (!java.util.Set.of("UNVERIFIED", "REJECTED").contains(user.get("verification_status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "当前状态不能重复提交认证");
        String reviewId = UUID.randomUUID().toString();
        if (user.get("role").equals("PUBLISHER")) {
            require(input.organization(), 150); require(input.surname(), 40); require(input.name(), 80);
            if (!validIdentity(input.identityNumber())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入有效的18位身份证号码");
            mapper.submitPublisher(input.organization().trim(),input.surname().trim(),crypto.encrypt(input.name().trim()),crypto.encrypt(input.identityNumber().toUpperCase()),id);
        } else {
            require(input.school(),150); require(input.name(),80); require(input.studentNumber(),50);
            mapper.submitStudent(input.school().trim(),crypto.encrypt(input.name().trim()),crypto.encrypt(input.studentNumber().trim()),id);
        }
        mapper.markPending(input.email(),"资料已提交，等待审核服务处理",reviewId,id,users.tableForId(id));
        mapper.insertEvent(reviewId,id,"PENDING","等待审核服务接入；不自动通过");
    }
    /** Adapter boundary for a future trusted AI/manual review worker. Never expose as a public approval API. */
    @Transactional
    public void applyDecision(String id, String reviewId, boolean approved, String note) {
        Map<String,Object> user = users.lockById(id);
        if (!"PENDING".equals(user.get("verification_status")) || !reviewId.equals(user.get("review_id"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "审核记录已变更，请刷新后重试");
        String status = approved ? "APPROVED" : "REJECTED";
        mapper.applyDecision(status,note,approved?1:0,id,users.tableForId(id));
        mapper.insertEvent(UUID.randomUUID().toString(),id,status,note);
        notifications.send(id,"review:"+reviewId,"VERIFICATION_"+status,approved?"实名认证审核通过":"实名认证审核未通过",
            approved?"你的实名认证已通过，可以开始"+("PUBLISHER".equals(user.get("role"))?"发布兼职。":"接取兼职。") : "你的实名认证未通过，请前往实名认证页面查看原因并重新提交。","VERIFY",null);
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
