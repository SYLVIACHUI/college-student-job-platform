package cn.campus.jobs;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Crypto {
    private final byte[] key;
    private final byte[] lookupKey;
    private final SecureRandom random = new SecureRandom();
    public Crypto(@Value("${app.encryption-key}") String key, @Value("${app.lookup-key}") String lookupKey) {
        this.key = Base64.getDecoder().decode(key);
        if (this.key.length != 32) throw new IllegalArgumentException("APP_ENCRYPTION_KEY must be base64 of 32 bytes");
        this.lookupKey = lookupKey.getBytes(StandardCharsets.UTF_8);
        if (this.lookupKey.length < 32) throw new IllegalArgumentException("APP_LOOKUP_KEY must have at least 32 bytes");
    }
    public String encrypt(String value) {
        if (value == null) return null;
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) { throw new IllegalStateException("Encryption failed", e); }
    }
    public String decrypt(String value) {
        try {
            String[] parts = value.split(":");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Decryption failed", e); }
    }
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(lookupKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
