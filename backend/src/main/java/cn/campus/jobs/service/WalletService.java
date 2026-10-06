package cn.campus.jobs.service;

import cn.campus.jobs.mapper.WalletMapper;

import cn.campus.jobs.mapper.UserRepository;
import cn.campus.jobs.controller.WalletController;

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
    private final WalletMapper mapper;
    private final UserRepository users;
    private final String mode;
    private final NotificationService notifications;
    public WalletService(UserRepository users,NotificationService notifications,@Value("${app.wallet-mode:DISABLED}") String mode,WalletMapper mapper){ this.mapper=mapper;this.users=users;this.notifications=notifications;this.mode=mode;}
    @Transactional(readOnly=true)
    public Map<String,Object> summary(String id,int page) {
        if(page<0 || page>10000) bad("页码无效");
        var wallet=mapper.balances(id);
        wallet.put("mode",mode);
        wallet.put("entries",mapper.entries(id,page*20));
        wallet.put("total",mapper.entryCount(id));
        wallet.put("withdrawals",mapper.withdrawals(id));
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
        var apps=mapper.paymentApplication(applicationId);
        if(apps.isEmpty() || !actor.equals(apps.get(0).get("publisher_id"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"接取记录不存在或无权发放");
        // Same locking order as joining, withdrawing and cancelling: job -> application -> wallets.
        var job=mapper.lockJob(apps.get(0).get("job_id"));
        var application=mapper.lockApplication(applicationId);
        String student=apps.get(0).get("student_id").toString();
        for(String id:new TreeSet<>(List.of(actor,student)))lock(id);
        var replay=replay(actor,input.requestKey(),"JOB_PAY",applicationId,cents); if(replay!=null)return replay;
        if(!"OPEN".equals(job.get("status")))conflict("活动已取消，不能发放报酬");
        if(!"ACTIVE".equals(application.get("status")))conflict("学生尚未录取或已退出，不能发放报酬");
        if(mapper.paymentCount(applicationId)>0) conflict("该接取记录已结算，不能重复发放");
        String operation=operation(actor,input.requestKey(),"JOB_PAY",applicationId,cents);
        String title=apps.get(0).get("title").toString();
        change(actor,operation,"JOB_PAY",-cents,0,"发放兼职费："+title);
        change(student,operation,"JOB_INCOME",cents,0,"兼职费收入："+title);
        mapper.insertPayment(operation,applicationId,actor,student,cents);
        notifications.send(student,"income:"+operation,"JOB_INCOME","兼职报酬已到账","「"+title+"」的模拟报酬 ¥"+BigDecimal.valueOf(cents,2).toPlainString()+" 已存入钱包，可前往查看余额和流水。","WALLET",null);
        return result(operation,false);
    }
    @Transactional
    public Map<String,Object> withdraw(String actor,WalletController.Amount input){
        simulate();role(actor,"STUDENT");long cents=cents(input.amount());lock(actor);
        var replay=replay(actor,input.requestKey(),"WITHDRAW",actor,cents); if(replay!=null)return replay;
        String operation=operation(actor,input.requestKey(),"WITHDRAW",actor,cents);
        change(actor,operation,"WITHDRAW_HOLD",-cents,cents,"模拟提现申请：资金冻结，未实际到账");
        mapper.insertWithdrawal(operation,actor,cents);
        return result(operation,false);
    }
    @Transactional
    public Map<String,Object> cancel(String actor,String withdrawalId,String requestKey){
        simulate();role(actor,"STUDENT");
        var rows=mapper.lockWithdrawal(withdrawalId,actor);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"提现申请不存在");
        long amount=((Number)rows.get(0).get("amount_cents")).longValue();lock(actor);
        var replay=replay(actor,requestKey,"WITHDRAW_CANCEL",withdrawalId,amount);if(replay!=null)return replay;
        if(!"PENDING".equals(rows.get(0).get("status"))) conflict("该提现申请已处理");
        String operation=operation(actor,requestKey,"WITHDRAW_CANCEL",withdrawalId,amount);
        change(actor,operation,"WITHDRAW_RELEASE",amount,-amount,"撤销模拟提现：冻结余额退回");
        mapper.cancelWithdrawal(withdrawalId);
        return result(operation,false);
    }
    private void lock(String id){mapper.lockWallet(id);}
    private void change(String id,String operation,String kind,long delta,long frozenDelta,String description){
        var wallet=mapper.balances(id);
        long balance=Math.addExact(((Number)wallet.get("balance_cents")).longValue(),delta);
        long frozen=Math.addExact(((Number)wallet.get("frozen_cents")).longValue(),frozenDelta);
        if(balance<0)bad("可用余额不足");if(frozen<0)conflict("冻结余额异常");
        if(balance>100_000_000_00L || frozen>100_000_000_00L)bad("模拟钱包余额超过上限");
        mapper.updateBalances(balance,frozen,id);
        mapper.insertEntry(UUID.randomUUID().toString(),id,operation,kind,delta,frozenDelta,balance,frozen,description);
    }
    private Map<String,Object> replay(String actor,String key,String kind,String target,long amount){
        var rows=mapper.operation(actor,key);
        if(rows.isEmpty())return null;var op=rows.get(0);
        if(!kind.equals(op.get("kind")) || !target.equals(op.get("target_id")) || amount!=((Number)op.get("amount_cents")).longValue())conflict("请求标识已用于其他交易，请刷新重试");
        return result(op.get("id").toString(),true);
    }
    private String operation(String actor,String key,String kind,String target,long amount){
        String id=UUID.randomUUID().toString();
        mapper.insertOperation(id,actor,key,kind,target,amount);return id;
    }
    private Map<String,Object> result(String id,boolean replay){return Map.of("operationId",id,"replayed",replay,"mode",mode,"message",replay?"该操作已处理，无重复扣款":"模拟操作已完成");}
    private void role(String id,String role){if(!role.equals(users.byId(id).get("role")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"当前身份不能执行此操作");}
    private void simulate(){if(!"SIMULATED".equals(mode))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"真实支付尚未接入，钱包操作未开放");}
    private long cents(BigDecimal amount){try{long value=amount.movePointRight(2).longValueExact();if(value<1 || value>10_000_000)bad("金额需在0.01至100000元之间");return value;}catch(ArithmeticException e){bad("金额最多保留两位小数");return 0;}}
    private void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
