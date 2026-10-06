package cn.campus.jobs.entity;
import java.util.Map;

public sealed interface UserAccount permits PublisherUser, StudentUser {
    AccountFields account();
    String role();
    Map<String,Object> toMap();
}
