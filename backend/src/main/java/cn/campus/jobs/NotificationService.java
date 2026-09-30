package cn.campus.jobs;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final UserRepository users;
    public NotificationService(UserRepository users){this.users=users;}
    // Notifications must commit or roll back together with their business operation.
    @Transactional(propagation=Propagation.MANDATORY)
    public void send(String recipient,String eventKey,String kind,String title,String content,String targetType,String targetId){
        users.jdbc().update("INSERT INTO notification(id,recipient_id,event_key,kind,title,content,target_type,target_id) VALUES(?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),recipient,eventKey,kind,title,content,targetType,targetId);
    }
    public long unread(String id){return users.jdbc().queryForObject("SELECT COUNT(*) FROM notification WHERE recipient_id=? AND read_at IS NULL",Long.class,id);}
    @Transactional(readOnly=true)
    public Map<String,Object> list(String id,int page,boolean unreadOnly){
        if(page<0||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        String where=" WHERE recipient_id=?"+(unreadOnly?" AND read_at IS NULL":"");
        var items=users.jdbc().queryForList("SELECT id,kind,title,content,target_type,target_id,read_at,created_at FROM notification"+where+" ORDER BY created_at DESC,id DESC LIMIT 20 OFFSET ?",id,page*20);
        long total=users.jdbc().queryForObject("SELECT COUNT(*) FROM notification"+where,Long.class,id);
        return Map.of("items",items,"total",total,"page",page,"unread",unread(id));
    }
    public void read(String userId,String id){
        int found=users.jdbc().queryForObject("SELECT COUNT(*) FROM notification WHERE id=? AND recipient_id=?",Integer.class,id,userId);
        if(found==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"消息不存在");
        users.jdbc().update("UPDATE notification SET read_at=CURRENT_TIMESTAMP WHERE id=? AND recipient_id=? AND read_at IS NULL",id,userId);
    }
    public void readAll(String id){users.jdbc().update("UPDATE notification SET read_at=CURRENT_TIMESTAMP WHERE recipient_id=? AND read_at IS NULL",id);}
}
