package cn.campus.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("dev")
class PlatformIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Crypto crypto;
    @Autowired ExpiringStore store;

    MvcResult postJson(String path,Object body,Cookie cookie,int status) throws Exception {
        var request=post("/api"+path).header("X-Requested-With","campus-web").contentType("application/json").content(json.writeValueAsBytes(body));
        if(cookie!=null) request.cookie(cookie);
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    String code(String role,String phone) throws Exception { return body(postJson("/auth/code",Map.of("role",role,"phone",phone),null,200)).get("debugCode").asText(); }
    Cookie registerLogin(String role,String phone) throws Exception {
        String otp=code(role,phone);
        postJson("/auth/register",Map.of("role",role,"phone",phone,"code",otp,"password","Password123"),null,200);
        return postJson("/auth/login",Map.of("role",role,"phone",phone,"password","Password123"),null,200).getResponse().getCookie("CAMPUS_SESSION");
    }
    JsonNode me(Cookie cookie) throws Exception { return body(mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isOk()).andReturn()); }
    void review(Cookie cookie,boolean approved) throws Exception { postJson("/dev/review",Map.of("reviewId",me(cookie).get("review_id").asText(),"approved",approved),cookie,200); }

    @Test void endToEndRoleVerificationJobsAndLogout() throws Exception {
        Cookie publisher=registerLogin("PUBLISHER","13900000001");
        Cookie student=registerLogin("STUDENT","13900000001");
        assertTrue(publisher.isHttpOnly());
        Map<String,Object> job=Map.of("title","图书馆助理","description","周末整理图书","location","大学城","pay",150);
        postJson("/jobs",job,publisher,403);
        postJson("/jobs",job,student,403);
        postJson("/verification",Map.of("organization","青禾科技","surname","张","name","三","identityNumber","11010519491231002X","email","test@example.com"),publisher,200);
        assertEquals("PENDING",me(publisher).get("verification_status").asText());
        postJson("/jobs",job,publisher,403);
        review(publisher,true);
        String id=body(postJson("/jobs",job,publisher,200)).get("id").asText();
        mvc.perform(get("/api/jobs").cookie(student)).andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("图书馆助理"));
        postJson("/jobs/"+id+"/apply",Map.of(),student,403);
        postJson("/verification",Map.of("school","测试大学","name","李四","studentNumber","20260001"),student,200);
        review(student,false);
        assertEquals(0,me(student).get("can_accept").asInt());
        postJson("/verification",Map.of("school","测试大学","name","李四","studentNumber","20260002"),student,200);
        review(student,true);
        postJson("/jobs/"+id+"/apply",Map.of(),student,200);
        postJson("/jobs/"+id+"/apply",Map.of(),student,409);
        postJson("/jobs",job,student,403);
        postJson("/jobs/"+id+"/apply",Map.of(),publisher,403);
        postJson("/auth/logout",Map.of(),student,200);
        mvc.perform(get("/api/me").cookie(student)).andExpect(status().isUnauthorized());
    }
    @Test void encryptedStorageAndNoSensitiveResponses() throws Exception {
        Cookie c=registerLogin("STUDENT","13900000002");
        postJson("/verification",Map.of("school","大学","name","敏感姓名","studentNumber","SECRET123"),c,200);
        var row=jdbc.queryForMap("SELECT * FROM app_user WHERE id=?",me(c).get("id").asText());
        assertNotEquals("13900000002",row.get("phone_cipher"));
        assertNotEquals("Password123",row.get("password_hash"));
        assertNotEquals("敏感姓名",row.get("name_cipher"));
        assertEquals("敏感姓名",crypto.decrypt((String)row.get("name_cipher")));
        assertEquals("SECRET123",crypto.decrypt((String)row.get("student_number_cipher")));
        String response=me(c).toString();
        assertFalse(response.contains("SECRET123")); assertFalse(response.contains("敏感姓名"));
        assertFalse(response.contains("13900000002")); assertFalse(response.contains("password_hash"));
        assertNotEquals(crypto.encrypt("same"),crypto.encrypt("same"));
    }
    @Test void otpIsOneTimeRoleBoundAndRateLimited() throws Exception {
        String phone="13900000003",otp=code("PUBLISHER",phone);
        postJson("/auth/code",Map.of("role","PUBLISHER","phone",phone),null,429);
        postJson("/auth/register",Map.of("role","STUDENT","phone",phone,"code",otp,"password","Password123"),null,400);
        var input=Map.of("role","PUBLISHER","phone",phone,"code",otp,"password","Password123");
        postJson("/auth/register",input,null,200);
        postJson("/auth/register",input,null,400);
        store.put("otp:PUBLISHER:"+crypto.hash(phone),crypto.hash("345678"),Duration.ofMinutes(5));
        postJson("/auth/register",Map.of("role","PUBLISHER","phone",phone,"code","345678","password","Password123"),null,409);
    }
    @Test void validationCsrfAnonymousAndWrongRole() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        postJson("/auth/register",Map.of("role","ADMIN","phone","bad","code","123456","password","short"),null,400);
        registerLogin("PUBLISHER","13900000004");
        postJson("/auth/login",Map.of("role","STUDENT","phone","13900000004","password","Password123"),null,401);
        postJson("/auth/login",Map.of("role","PUBLISHER","phone","13900000004","password","Incorrect123"),null,401);
    }
    @Test void rejectInvalidIdentityAndStaleReview() throws Exception {
        Cookie c=registerLogin("PUBLISHER","13900000005");
        postJson("/verification",Map.of("organization","公司","surname","张","name","三","identityNumber","110105194912310021"),c,400);
        var form=Map.of("organization","公司","surname","张","name","三","identityNumber","11010519491231002X");
        postJson("/verification",form,c,200);
        postJson("/verification",form,c,409);
        postJson("/dev/review",Map.of("reviewId","stale","approved",true),c,409);
        review(c,true);
        postJson("/verification",form,c,409);
        assertEquals(1,me(c).get("can_publish").asInt());
    }
    @Test void expirationAndAtomicConsumption() throws Exception {
        store.put("short-lived","value",Duration.ofMillis(1));
        Thread.sleep(10);
        assertNull(store.get("short-lived"));
        store.put("consume-test","value",Duration.ofMinutes(1));
        assertFalse(store.consume("consume-test","incorrect"));
        assertTrue(store.consume("consume-test","value"));
        assertFalse(store.consume("consume-test","value"));
        assertEquals(1,store.increment("counter",Duration.ofMinutes(1)));
        assertEquals(2,store.increment("counter",Duration.ofMinutes(1)));
    }
}
