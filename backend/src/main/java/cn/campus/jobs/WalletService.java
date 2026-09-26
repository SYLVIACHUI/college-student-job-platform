package cn.campus.jobs;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** A simulated closed ledger. No bank, payment provider, or real currency is involved. */
@Service
public class WalletService {
    private final UserRepository users;
    private final String mode;
    public WalletService(UserRepository users,@Value("${app.wallet-mode:DISABLED}") String mode){this.users=users;this.mode=mode;}
    @Transactional(readOnly=true)
    public Map<String,Object> summary(String id,int page) {
        if(page<0 || page>10000) bad("页码无效");
        var wallet=users.jdbc().queryForMap("SELECT balance_cents,frozen_cents FROM wallet WHERE user_id=?",id);
        wallet.put("mode",mode);
        wallet.put("entries",users.jdbc().queryForList("SELECT id,kind,delta_cents,frozen_delta_cents,balance_after_cents,frozen_after_cents,description,created_at FROM wallet_entry WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",id,page*20));
        wallet.put("total",users.jdbc().queryForObject("SELECT COUNT(*) FROM wallet_entry WHERE user_id=?",Long.class,id));
        wallet.put("withdrawals",users.jdbc().queryForList("SELECT id,amount_cents,status,created_at FROM withdrawal WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 20",id));
        wallet.put("page",page);return wallet;
    }
    @Transactional
    public Map<String,Object> topUp(String actor,WalletController.Amount input){
        simulate(); role(actor,"PUBLISHER");long cents=cents(input.amount()); lock(actor);
        var replay=replay(actor,input.requestKey(),"TOP_UP",actor,cents); if(replay!=null)return replay;
        String operation=operation(actor,input.requestKey(),"TOP_UP",actor,cents);
        change(actor,operation,"TOP_UP",cents,0,"企业模拟充值");return result(operation,false);
    }
    @Transactional
    public Map<String,Object> pay(String actor,String applicationId,WalletController.Amount input){
        simulate();role(actor,"PUBLISHER");long cents=cents(input.amount());
        var apps=users.jdbc().queryForList("SELECT a.student_id,j.publisher_id,j.title FROM job_application a JOIN job j ON j.id=a.job_id WHERE a.id=?",applicationId);
        if(apps.isEmpty() || !actor.equals(apps.get(0).get("publisher_id"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"接取记录不存在或无权发放");
        users.jdbc().queryForMap("SELECT id FROM job_application WHERE id=? FOR UPDATE",applicationId);
        String student=apps.get(0).get("student_id").toString();
        for(String id:new TreeSet<>(List.of(actor,student)))lock(id);
        var replay=replay(actor,input.requestKey(),"JOB_PAY",applicationId,cents); if(replay!=null)return replay;
        if(users.jdbc().queryForObject("SELECT COUNT(*) FROM job_payment WHERE application_id=?",Integer.class,applicationId)>0) conflict("该接取记录已结算，不能重复发放");
        String operation=operation(actor,input.requestKey(),"JOB_PAY",applicationId,cents);
        String title=apps.get(0).get("title").toString();
        change(actor,operation,"JOB_PAY",-cents,0,"发放兼职费："+title);
        change(student,operation,"JOB_INCOME",cents,0,"兼职费收入："+title);
        users.jdbc().update("INSERT INTO job_payment(id,application_id,publisher_id,student_id,amount_cents) VALUES(?,?,?,?,?)",operation,applicationId,actor,student,cents);
        return result(operation,false);
    }
    @Transactional
    public Map<String,Object> withdraw(String actor,WalletController.Amount input){
        simulate();role(actor,"STUDENT");long cents=cents(input.amount());lock(actor);
        var replay=replay(actor,input.requestKey(),"WITHDRAW",actor,cents); if(replay!=null)return replay;
        String operation=operation(actor,input.requestKey(),"WITHDRAW",actor,cents);
        change(actor,operation,"WITHDRAW_HOLD",-cents,cents,"模拟提现申请：资金冻结，未实际到账");
        users.jdbc().update("INSERT INTO withdrawal(id,user_id,amount_cents) VALUES(?,?,?)",operation,actor,cents);
        return result(operation,false);
    }
    @Transactional
    public Map<String,Object> cancel(String actor,String withdrawalId,String requestKey){
        simulate();role(actor,"STUDENT");
        var rows=users.jdbc().queryForList("SELECT * FROM withdrawal WHERE id=? AND user_id=? FOR UPDATE",withdrawalId,actor);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提现申请不存在");
        long amount=((Number)rows.get(0).get("amount_cents")).longValue();lock(actor);
        var replay=replay(actor,requestKey,"WITHDRAW_CANCEL",withdrawalId,amount);if(replay!=null)return replay;
        if(!"PENDING".equals(rows.get(0).get("status"))) conflict("该提现申请已处理");
        String operation=operation(actor,requestKey,"WITHDRAW_CANCEL",withdrawalId,amount);
        change(actor,operation,"WITHDRAW_RELEASE",amount,-amount,"撤销模拟提现：冻结余额退回");
        users.jdbc().update("UPDATE withdrawal SET status='CANCELLED' WHERE id=?",withdrawalId);
        return result(operation,false);
    }
    private void lock(String id){users.jdbc().queryForMap("SELECT user_id FROM wallet WHERE user_id=? FOR UPDATE",id);}
    private void change(String id,String operation,String kind,long delta,long frozenDelta,String description){
        var wallet=users.jdbc().queryForMap("SELECT balance_cents,frozen_cents FROM wallet WHERE user_id=?",id);
        long balance=Math.addExact(((Number)wallet.get("balance_cents")).longValue(),delta);
        long frozen=Math.addExact(((Number)wallet.get("frozen_cents")).longValue(),frozenDelta);
        if(balance<0)bad("可用余额不足");if(frozen<0)conflict("冻结余额异常");
        if(balance>100_000_000_00L || frozen>100_000_000_00L)bad("模拟钱包余额超过上限");
        users.jdbc().update("UPDATE wallet SET balance_cents=?,frozen_cents=? WHERE user_id=?",balance,frozen,id);
        users.jdbc().update("INSERT INTO wallet_entry(id,user_id,operation_id,kind,delta_cents,frozen_delta_cents,balance_after_cents,frozen_after_cents,description) VALUES(?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),id,operation,kind,delta,frozenDelta,balance,frozen,description);
    }
    private Map<String,Object> replay(String actor,String key,String kind,String target,long amount){
        var rows=users.jdbc().queryForList("SELECT * FROM wallet_operation WHERE actor_id=? AND request_key=?",actor,key);
        if(rows.isEmpty())return null;var op=rows.get(0);
        if(!kind.equals(op.get("kind")) || !target.equals(op.get("target_id")) || amount!=((Number)op.get("amount_cents")).longValue())conflict("请求标识已用于其他交易，请刷新重试");
        return result(op.get("id").toString(),true);
    }
    private String operation(String actor,String key,String kind,String target,long amount){
        String id=UUID.randomUUID().toString();
        users.jdbc().update("INSERT INTO wallet_operation(id,actor_id,request_key,kind,target_id,amount_cents) VALUES(?,?,?,?,?,?)",id,actor,key,kind,target,amount);return id;
    }
    private Map<String,Object> result(String id,boolean replay){return Map.of("operationId",id,"replayed",replay,"mode",mode,"message",replay?"该操作已处理，无重复扣款":"模拟操作已完成");}
    private void role(String id,String role){if(!role.equals(users.byId(id).get("role")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份不能执行此操作");}
    private void simulate(){if(!"SIMULATED".equals(mode))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"真实支付尚未接入，钱包操作未开放");}
    private long cents(BigDecimal amount){try{long value=amount.movePointRight(2).longValueExact();if(value<1 || value>10_000_000)bad("金额需在0.01至100000元之间");return value;}catch(ArithmeticException e){bad("金额最多保留两位小数");return 0;}}
    private void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
