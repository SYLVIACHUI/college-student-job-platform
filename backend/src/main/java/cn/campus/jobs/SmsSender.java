package cn.campus.jobs;

/** Implement this contract to integrate a specific SMS provider. */
public interface SmsSender {
    void send(String phone, String code);
}
