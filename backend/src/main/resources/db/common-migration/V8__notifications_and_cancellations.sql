ALTER TABLE job ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'OPEN';
ALTER TABLE job ADD COLUMN cancel_reason VARCHAR(300);
ALTER TABLE job ADD COLUMN cancelled_at TIMESTAMP NULL;
ALTER TABLE job_application ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE job_application ADD COLUMN withdrawn_at TIMESTAMP NULL;
CREATE TABLE notification (
  id VARCHAR(36) PRIMARY KEY,
  recipient_id VARCHAR(36) NOT NULL,
  event_key VARCHAR(120) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  title VARCHAR(100) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  target_type VARCHAR(16) NOT NULL,
  target_id VARCHAR(36),
  read_at TIMESTAMP NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE(recipient_id,event_key),
  FOREIGN KEY(recipient_id) REFERENCES account_identity(id)
) ENGINE=InnoDB;
CREATE INDEX idx_notification_inbox ON notification(recipient_id,created_at,id);
CREATE INDEX idx_notification_unread ON notification(recipient_id,read_at);
