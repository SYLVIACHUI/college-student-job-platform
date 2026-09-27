package cn.campus.jobs;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** job.publisher_id -> publisher_user; recipients are linked through job_application.student_id. */
public record Job(String id, String publisherId, String title, String category,
    int requiredCount, String description, String requirements, String location,
    BigDecimal pay, String payUnit, LocalDateTime startsAt, Integer durationMinutes) {}
