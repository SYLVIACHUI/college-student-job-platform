package cn.campus.jobs.service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** HTTPS bridge to an operator-owned SMS gateway; provider credentials stay at the gateway. */
@Component @Profile("!dev")
class WebhookSmsSender implements SmsSender {
    private final String url,token;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    WebhookSmsSender(@Value("${app.sms-url:}") String url,@Value("${app.sms-token:}") String token,ObjectMapper json) {
        this.url=url;this.token=token;this.json=json;
    }
    public void send(String phone,String code) {
        if (!url.startsWith("https://") || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"短信服务尚未配置");
        try {
            HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("phone",phone,"code",code,"expiresIn",300)))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()<200 || response.statusCode()>=300) throw new IllegalStateException("Gateway rejected SMS");
        } catch (Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"短信发送失败，请稍后重试");
        }
    }
}
