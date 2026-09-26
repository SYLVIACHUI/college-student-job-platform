package cn.campus.jobs;

import java.util.*;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:profiles;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("dev")
class ProfileWalletIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired Crypto crypto; @Autowired ExpiringStore store;
    String publisher,student,other; Cookie pubCookie,stuCookie,otherCookie;
    @BeforeEach void fixtures() {
        publisher=create("PUBLISHER");student=create("STUDENT");other=create("PUBLISHER");
        pubCookie=session(publisher);stuCookie=session(student);otherCookie=session(other);
    }
    String create(String role){String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO app_user(id,role,account_hash,phone_cipher,password_hash,verification_status,can_publish,can_accept) VALUES(?,?,?,?,?,'APPROVED',?,?)",id,role,crypto.hash(id),crypto.encrypt("13911112222"),"unused-fixture-password",role.equals("PUBLISHER")?1:0,role.equals("STUDENT")?1:0);jdbc.update("INSERT INTO wallet(user_id) VALUES(?)",id);return id;}
    Cookie session(String id){String token=UUID.randomUUID().toString();store.put("session:"+crypto.hash(token),id,Duration.ofHours(1));return new Cookie("CAMPUS_SESSION",token);}
    JsonNode postJson(String path,Object input,Cookie cookie,int expected)throws Exception{return body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api"+path).cookie(cookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(input))).andExpect(status().is(expected)).andReturn());}
    JsonNode getJson(String path,Cookie cookie,int expected)throws Exception{return body(mvc.perform(get("/api"+path).cookie(cookie)).andExpect(status().is(expected)).andReturn());}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsByteArray());}
    Map<String,String> amount(String value){return Map.of("amount",value,"requestKey",UUID.randomUUID().toString());}
    long balance(String id){return jdbc.queryForObject("SELECT balance_cents FROM wallet WHERE user_id=?",Long.class,id);}
    String application()throws Exception {
        String job=postJson("/jobs",Map.of("title","测试兼职","description","岗位说明","location","大学城","pay",150),pubCookie,200).get("id").asText();
        postJson("/jobs/"+job+"/apply",Map.of(),stuCookie,200);
        return jdbc.queryForObject("SELECT id FROM job_application WHERE job_id=? AND student_id=?",String.class,job,student);
    }
    @Test void profileEditingAndHomepagePrivacy()throws Exception {
        Map<String,Object> form=Map.of("nickname","小禾","birthday","2003-05-20","grade","大三","major","计算机","bio","热爱摄影");
        mvc.perform(put("/api/me/profile").cookie(stuCookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(form)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nickname").value("小禾")).andExpect(jsonPath("$.birthday").value("2003-05-20"));
        JsonNode home=getJson("/users/"+student,pubCookie,200);
        assertEquals("小禾",home.get("display_name").asText());assertEquals("大三",home.get("grade").asText());
        for(String field:List.of("birthday","phone","account","email","balance_cents","identity_cipher","password_hash","name_cipher"))assertFalse(home.has(field),field);
        mvc.perform(put("/api/me/profile").cookie(pubCookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(form))).andExpect(status().isBadRequest());
        for(var invalid:List.of(Map.of("nickname","  "),Map.of("nickname","名字","grade","高中"),Map.of("nickname","名字","birthday","2999-01-01")))
            mvc.perform(put("/api/me/profile").cookie(stuCookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(invalid))).andExpect(status().isBadRequest());
        getJson("/users/does-not-exist",pubCookie,404);
        mvc.perform(get("/api/users/"+student)).andExpect(status().isUnauthorized());
    }
    @Test void avatarUploadIsReencodedAndRestricted()throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(30,20,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var file=new MockMultipartFile("file","portrait.png","image/png",bytes.toByteArray());
        mvc.perform(multipart("/api/me/avatar").file(file).cookie(stuCookie).header("X-Requested-With","campus-web")).andExpect(status().isOk()).andExpect(jsonPath("$.avatar_url").isNotEmpty());
        byte[] returned=mvc.perform(get("/api/users/"+student+"/avatar").cookie(pubCookie)).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        assertEquals(256,ImageIO.read(new java.io.ByteArrayInputStream(returned)).getWidth());
        mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file","bad.png","image/png","<script>bad</script>".getBytes())).cookie(stuCookie).header("X-Requested-With","campus-web")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/me/avatar").file(new MockMultipartFile("file","huge.png","image/png",new byte[2*1024*1024+1])).cookie(stuCookie).header("X-Requested-With","campus-web")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/"+student+"/avatar")).andExpect(status().isUnauthorized());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_avatar WHERE user_id=?",Integer.class,student));
    }
    @Test void historiesAndApplicantsAreOwnerScoped()throws Exception {
        String app=application();String job=jdbc.queryForObject("SELECT job_id FROM job_application WHERE id=?",String.class,app);
        JsonNode history=getJson("/me/history",stuCookie,200);assertEquals(1,history.get("total").asInt());assertEquals(app,history.at("/items/0/application_id").asText());
        JsonNode people=getJson("/jobs/"+job+"/applicants",pubCookie,200);assertEquals(student,people.at("/items/0/id").asText());assertFalse(people.at("/items/0").has("phone_cipher"));
        getJson("/jobs/"+job+"/applicants",otherCookie,404);getJson("/jobs/"+job+"/applicants",stuCookie,404);
        assertEquals(0,getJson("/me/history",otherCookie,200).get("total").asInt());getJson("/me/history?page=-1",stuCookie,400);
    }
    @Test void completeLedgerFlowAndIdempotency()throws Exception {
        String app=application();var topup=amount("300.50");postJson("/wallet/top-up",topup,pubCookie,200);postJson("/wallet/top-up",topup,pubCookie,200);assertEquals(30050,balance(publisher));
        var pay=amount("150.25");postJson("/applications/"+app+"/payment",pay,pubCookie,200);assertTrue(postJson("/applications/"+app+"/payment",pay,pubCookie,200).get("replayed").asBoolean());
        assertEquals(15025,balance(publisher));assertEquals(15025,balance(student));
        postJson("/applications/"+app+"/payment",amount("150.25"),pubCookie,409);
        var withdraw=amount("100.10");String withdrawal=postJson("/wallet/withdrawals",withdraw,stuCookie,200).get("operationId").asText();postJson("/wallet/withdrawals",withdraw,stuCookie,200);
        assertEquals(5015,balance(student));assertEquals(10010,getJson("/wallet",stuCookie,200).get("frozen_cents").asLong());
        var cancel=Map.of("requestKey",UUID.randomUUID().toString());postJson("/wallet/withdrawals/"+withdrawal+"/cancel",cancel,stuCookie,200);postJson("/wallet/withdrawals/"+withdrawal+"/cancel",cancel,stuCookie,200);
        assertEquals(15025,balance(student));assertEquals(0,getJson("/wallet",stuCookie,200).get("frozen_cents").asLong());
        assertEquals(3,getJson("/wallet",stuCookie,200).get("total").asInt());assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM withdrawal WHERE id=?",String.class,withdrawal));
        assertEquals(15025,getJson("/me/history",stuCookie,200).at("/items/0/paid_cents").asLong());
    }
    @Test void insufficientFundsRollBackAndPermissionsAreEnforced()throws Exception {
        String app=application();postJson("/applications/"+app+"/payment",amount("1.00"),pubCookie,400);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM job_payment WHERE application_id=?",Integer.class,app));assertEquals(0,balance(student));
        postJson("/wallet/top-up",amount("10"),stuCookie,403);postJson("/wallet/withdrawals",amount("10"),pubCookie,403);
        postJson("/applications/"+app+"/payment",amount("1.00"),otherCookie,404);
        postJson("/wallet/withdrawals",amount("1.00"),stuCookie,400);
        for(String value:List.of("0","-1","1.001","100001"))postJson("/wallet/top-up",amount(value),pubCookie,400);
        var top=amount("10");postJson("/wallet/top-up",top,pubCookie,200);postJson("/wallet/top-up",Map.of("requestKey",top.get("requestKey"),"amount","20"),pubCookie,409);
        assertEquals(1000,balance(publisher));
    }
    @Test void concurrentPayoutCannotDoubleCredit()throws Exception {
        String app=application();postJson("/wallet/top-up",amount("200"),pubCookie,200);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2); try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> task=()->{start.await();return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/applications/"+app+"/payment").cookie(pubCookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(amount("150")))).andReturn().getResponse().getStatus();};
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        } finally { executor.shutdownNow(); }
        assertEquals(5000,balance(publisher));assertEquals(15000,balance(student));
    }
    @Test void concurrentWithdrawalsCannotOverdraw()throws Exception {
        String app=application();postJson("/wallet/top-up",amount("150"),pubCookie,200);postJson("/applications/"+app+"/payment",amount("150"),pubCookie,200);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2); try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> task=()->{start.await();return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/wallet/withdrawals").cookie(stuCookie).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(amount("100")))).andReturn().getResponse().getStatus();};
            var first=executor.submit(task);var second=executor.submit(task);start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),second.get(10,java.util.concurrent.TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,400),statuses);
        } finally { executor.shutdownNow(); }
        assertEquals(5000,balance(student));
        String withdrawal=jdbc.queryForObject("SELECT id FROM withdrawal WHERE user_id=?",String.class,student);
        String stranger=create("STUDENT");postJson("/wallet/withdrawals/"+withdrawal+"/cancel",Map.of("requestKey",UUID.randomUUID().toString()),session(stranger),404);
    }
}
