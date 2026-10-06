package cn.campus.jobs.service;

import cn.campus.jobs.mapper.NotificationMapper;


import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final NotificationMapper mapper;
    public NotificationService(NotificationMapper mapper){ this.mapper=mapper;}
    // Notifications must commit or roll back together with their business operation.
    @Transactional(propagation=Propagation.MANDATORY)
    public void send(String recipient,String eventKey,String kind,String title,String content,String targetType,String targetId){
        mapper.insert(UUID.randomUUID().toString(),recipient,eventKey,kind,title,content,targetType,targetId);
    }
    public long unread(String id){return mapper.unreadCount(id);}
    @Transactional(readOnly=true)
    public Map<String,Object> list(String id,int page,boolean unreadOnly){
        if(page<0||page>10000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码无效");
        var items=mapper.list(id,page*20,unreadOnly);
        long total=mapper.count(id,unreadOnly);
        return Map.of("items",items,"total",total,"page",page,"unread",unread(id));
    }
    public void read(String userId,String id){
        int found=mapper.ownedCount(id,userId);
        if(found==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"消息不存在");
        mapper.markRead(id,userId);
    }
    public void readAll(String id){mapper.markAllRead(id);}
}
