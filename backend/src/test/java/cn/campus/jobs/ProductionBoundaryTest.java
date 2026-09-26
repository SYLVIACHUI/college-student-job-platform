package cn.campus.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:productiontest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa","spring.datasource.password=",
    "app.encryption-key=ZGV2LW9ubHkta2V5LTMyaHl0ZXMtbG9uZy0xMjM0NTY=",
    "app.lookup-key=production-test-only-lookup-key-32-characters"
})
@AutoConfigureMockMvc
class ProductionBoundaryTest {
    @TestConfiguration static class TestStore {
        @Bean @Primary ExpiringStore testStore() { return new MemoryStore(); }
    }
    @Autowired MockMvc mvc;
    @Test void devFeaturesAreNotExposedInProduction() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk()).andExpect(jsonPath("$.development").value(false));
        mvc.perform(post("/api/dev/review").header("X-Requested-With","campus-web").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/code").header("X-Requested-With","campus-web").contentType("application/json")
            .content("{\"phone\":\"13900000009\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.debugCode").doesNotExist());
    }
}
