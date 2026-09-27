package cn.campus.jobs;

import java.util.*;
import java.time.Duration;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:admin_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("dev")
class ReviewAdminTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired UserRepository users;@Autowired Crypto crypto;@Autowired ExpiringStore store;@Autowired VerificationService verification;
    String adminId,username;Cookie admin;
    @BeforeEach void setup()throws Exception{
        adminId=UUID.randomUUID().toString();username="review_"+adminId.substring(0,8);
        users.jdbc().update("INSERT INTO review_admin(id,username,password_hash,display_name) VALUES(?,?,?,?)",adminId,username,new BCryptPasswordEncoder(12).encode("ReviewTest2026!"),"测试审核员");
        var response=mvc.perform(post("/api/admin/login").header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(Map.of("username",username,"password","ReviewTest2026!")))).andExpect(status().isOk()).andReturn();
        admin=response.getResponse().getCookie("CAMPUS_REVIEW_SESSION");assertNotNull(admin);assertTrue(admin.isHttpOnly());assertEquals("/api/admin",admin.getPath());
    }
    String applicant(String role){String id=UUID.randomUUID().toString();users.create(id,role,crypto.hash(id),crypto.encrypt("13900000000"),"unused");submit(id,role);return id;}
    void submit(String id,String role){verification.submit(id,role.equals("PUBLISHER")?new ApiController.Verification("测试企业","张","三","11010519491231002X",null,null,null):new ApiController.Verification(null,null,"测试学生",null,"测试大学","S001",null));}
    JsonNode getJson(String url,int expected)throws Exception{return json.readTree(mvc.perform(get("/api/admin"+url).cookie(admin)).andExpect(status().is(expected)).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray());}
    void decide(String id,String reviewId,boolean approved,String note,int expected)throws Exception{mvc.perform(post("/api/admin/reviews/"+id+"/decision").cookie(admin).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(Map.of("reviewId",reviewId,"approved",approved,"note",note)))).andExpect(status().is(expected));}
    @Test void approveAndRejectAuditWithVersionControl()throws Exception{
        String pub=applicant("PUBLISHER"),stu=applicant("STUDENT");
        var detail=getJson("/reviews/"+pub,200);assertEquals("11010519491231002X",detail.get("identityNumber").asText());assertFalse(detail.has("password_hash"));
        for(var row:getJson("/reviews?role=PUBLISHER",200).get("items")){assertEquals("PUBLISHER",row.get("role").asText());assertFalse(row.has("identityNumber"));}
        String review=detail.get("review_id").asText();decide(pub,review,true,"资料核验通过",200);
        assertEquals(1,users.byId(pub).get("can_publish"));decide(pub,review,true,"重复处理",409);
        String old=users.byId(stu).get("review_id").toString();decide(stu,old,false,"请核对学号",200);assertEquals(0,users.byId(stu).get("can_accept"));
        submit(stu,"STUDENT");decide(stu,old,true,"过期版本",409);
        decide(stu,users.byId(stu).get("review_id").toString(),true,"资料核验通过",200);assertEquals(1,users.byId(stu).get("can_accept"));
        assertEquals(3,users.jdbc().queryForObject("SELECT COUNT(*) FROM review_action WHERE admin_id=?",Integer.class,adminId));
        assertTrue(getJson("/history",200).get("total").asInt()>=3);getJson("/reviews/"+pub,409);
    }
    @Test void businessSessionCannotReadOrReviewAndLogoutRevokesSession()throws Exception{
        String id=applicant("STUDENT"),token=UUID.randomUUID().toString();store.put("session:"+crypto.hash(token),id,Duration.ofHours(1));
        Cookie business=new Cookie("CAMPUS_SESSION",token);
        for(String path:List.of("/me","/reviews","/reviews/"+id,"/history"))mvc.perform(get("/api/admin"+path).cookie(business)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/reviews/"+id+"/decision").cookie(business).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(Map.of("reviewId",users.byId(id).get("review_id"),"approved",true,"note","无权限")))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(admin)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/logout").cookie(admin).header("X-Requested-With","campus-web")).andExpect(status().isOk());getJson("/me",401);
    }
    @Test void validatesOpinionAndDisabledAccountAndCsrf()throws Exception{
        String id=applicant("STUDENT"),review=users.byId(id).get("review_id").toString();decide(id,review,false," ",400);
        mvc.perform(post("/api/admin/reviews/"+id+"/decision").cookie(admin).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        getJson("/reviews?role=ADMIN",400);
        users.jdbc().update("UPDATE review_admin SET enabled=0 WHERE id=?",adminId);getJson("/me",401);
    }
}
