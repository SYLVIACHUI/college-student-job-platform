package cn.campus.jobs.service;

/** Implement this contract to integrate a specific SMS provider. */
public interface SmsSender {
    void send(String phone, String code);
}
