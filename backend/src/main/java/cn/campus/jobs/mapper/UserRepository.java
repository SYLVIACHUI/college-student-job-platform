package cn.campus.jobs.mapper;

import cn.campus.jobs.entity.UserAccount;

import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class UserRepository {
    private final AccountMapper accounts;
    private final PublisherUserMapper publishers;
    private final StudentUserMapper students;
    public UserRepository(AccountMapper accounts,PublisherUserMapper publishers,StudentUserMapper students) { this.accounts=accounts;this.publishers=publishers;this.students=students; }
    public Map<String,Object> byId(String id) {
        UserAccount account=findAccountById(id);
        if(account==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录");
        return account.toMap();
    }
    public Map<String,Object> byAccount(String role, String hash) {
        UserAccount account=switch(role){case "PUBLISHER"->publishers.findByAccount(hash);case "STUDENT"->students.findByAccount(hash);default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"身份无效");};
        return account==null?null:account.toMap();
    }
    public UserAccount findAccountById(String id){String role=roleForId(id);return role==null?null:"PUBLISHER".equals(role)?publishers.findById(id,false):students.findById(id,false);}
    public Map<String,Object> lockById(String id){String role=roleForId(id);UserAccount account=role==null?null:"PUBLISHER".equals(role)?publishers.findById(id,true):students.findById(id,true);if(account==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录");return account.toMap();}
    @Transactional
    public void create(String id,String role,String hash,String phoneCipher,String passwordHash){
        if(!"PUBLISHER".equals(role)&&!"STUDENT".equals(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"身份无效");
        accounts.insertIdentity(id,role);
        if("PUBLISHER".equals(role))publishers.insert(id,hash,phoneCipher,passwordHash);else students.insert(id,hash,phoneCipher,passwordHash);
    }
    public String tableForId(String id){String role=roleForId(id);if(role==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录");return "PUBLISHER".equals(role)?"publisher_user":"student_user";}
    private String roleForId(String id){return accounts.role(id);}
}
