package cn.campus.jobs;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component @Profile("dev")
class DevSmsSender implements SmsSender {
    public void send(String phone, String code) { /* Code is returned only by the explicitly enabled dev profile. */ }
}
