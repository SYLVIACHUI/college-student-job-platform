ALTER TABLE publisher_user ADD COLUMN nickname VARCHAR(40);
ALTER TABLE publisher_user ADD COLUMN birthday DATE;
ALTER TABLE publisher_user ADD COLUMN bio VARCHAR(300);
ALTER TABLE publisher_user ADD COLUMN avatar_version VARCHAR(36);
ALTER TABLE student_user ADD COLUMN nickname VARCHAR(40);
ALTER TABLE student_user ADD COLUMN birthday DATE;
ALTER TABLE student_user ADD COLUMN grade VARCHAR(10);
ALTER TABLE student_user ADD COLUMN major VARCHAR(100);
ALTER TABLE student_user ADD COLUMN bio VARCHAR(300);
ALTER TABLE student_user ADD COLUMN avatar_version VARCHAR(36);
CREATE TABLE user_avatar (
  user_id VARCHAR(36) PRIMARY KEY,
  image_data MEDIUMBLOB NOT NULL,
  FOREIGN KEY(user_id) REFERENCES account_identity(id)
) ENGINE=InnoDB;
CREATE TABLE wallet (
  user_id VARCHAR(36) PRIMARY KEY,
  balance_cents BIGINT NOT NULL DEFAULT 0,
  frozen_cents BIGINT NOT NULL DEFAULT 0,
  FOREIGN KEY(user_id) REFERENCES account_identity(id),
  CHECK (balance_cents >= 0),
  CHECK (frozen_cents >= 0)
) ENGINE=InnoDB;
INSERT INTO wallet(user_id) SELECT id FROM account_identity;
CREATE TABLE wallet_operation (
  id VARCHAR(36) PRIMARY KEY,
  actor_id VARCHAR(36) NOT NULL,
  request_key VARCHAR(36) NOT NULL,
  kind VARCHAR(30) NOT NULL,
  target_id VARCHAR(36) NOT NULL,
  amount_cents BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(actor_id, request_key),
  FOREIGN KEY(actor_id) REFERENCES account_identity(id)
) ENGINE=InnoDB;
CREATE TABLE job_payment (
  id VARCHAR(36) PRIMARY KEY,
  application_id VARCHAR(36) NOT NULL UNIQUE,
  publisher_id VARCHAR(36) NOT NULL,
  student_id VARCHAR(36) NOT NULL,
  amount_cents BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(application_id) REFERENCES job_application(id),
  FOREIGN KEY(publisher_id) REFERENCES publisher_user(id),
  FOREIGN KEY(student_id) REFERENCES student_user(id)
) ENGINE=InnoDB;
CREATE TABLE withdrawal (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  amount_cents BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES account_identity(id)
) ENGINE=InnoDB;
CREATE TABLE wallet_entry (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  operation_id VARCHAR(36) NOT NULL,
  kind VARCHAR(30) NOT NULL,
  delta_cents BIGINT NOT NULL,
  frozen_delta_cents BIGINT NOT NULL,
  balance_after_cents BIGINT NOT NULL,
  frozen_after_cents BIGINT NOT NULL,
  description VARCHAR(200) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES account_identity(id),
  FOREIGN KEY(operation_id) REFERENCES wallet_operation(id)
) ENGINE=InnoDB;
CREATE INDEX idx_wallet_entry_user ON wallet_entry(user_id, created_at);
CREATE INDEX idx_application_student ON job_application(student_id, created_at);
