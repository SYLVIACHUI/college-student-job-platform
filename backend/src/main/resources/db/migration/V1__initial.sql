-- 新数据库从初始化开始即分开存储企业与学生。
CREATE TABLE account_identity (
  id VARCHAR(36) PRIMARY KEY,
  role VARCHAR(16) NOT NULL,
  UNIQUE(id, role)
) ENGINE=InnoDB;
CREATE TABLE publisher_user (
  id VARCHAR(36) PRIMARY KEY,
  role VARCHAR(16) NOT NULL DEFAULT 'PUBLISHER',
  account_hash VARCHAR(64) NOT NULL UNIQUE,
  phone_cipher VARCHAR(512) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  email VARCHAR(254),
  organization VARCHAR(150),
  surname VARCHAR(40),
  name_cipher VARCHAR(512),
  identity_cipher VARCHAR(512),
  verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
  review_note VARCHAR(500),
  review_id VARCHAR(36),
  can_publish INTEGER NOT NULL DEFAULT 0,
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(id, role) REFERENCES account_identity(id, role),
  CHECK(role='PUBLISHER')
) ENGINE=InnoDB;
CREATE TABLE student_user (
  id VARCHAR(36) PRIMARY KEY,
  role VARCHAR(16) NOT NULL DEFAULT 'STUDENT',
  account_hash VARCHAR(64) NOT NULL UNIQUE,
  phone_cipher VARCHAR(512) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  email VARCHAR(254),
  school VARCHAR(150),
  name_cipher VARCHAR(512),
  student_number_cipher VARCHAR(512),
  verification_status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
  review_note VARCHAR(500),
  review_id VARCHAR(36),
  can_accept INTEGER NOT NULL DEFAULT 0,
  last_login_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(id, role) REFERENCES account_identity(id, role),
  CHECK(role='STUDENT')
) ENGINE=InnoDB;
CREATE TABLE verification_event (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  status VARCHAR(16) NOT NULL,
  note VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(user_id) REFERENCES account_identity(id)
) ENGINE=InnoDB;
CREATE TABLE job (
  id VARCHAR(36) PRIMARY KEY,
  publisher_id VARCHAR(36) NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  location VARCHAR(150) NOT NULL,
  pay INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(publisher_id) REFERENCES publisher_user(id)
) ENGINE=InnoDB;
CREATE TABLE job_application (
  id VARCHAR(36) PRIMARY KEY,
  job_id VARCHAR(36) NOT NULL,
  student_id VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(job_id, student_id),
  FOREIGN KEY(job_id) REFERENCES job(id),
  FOREIGN KEY(student_id) REFERENCES student_user(id)
) ENGINE=InnoDB;
CREATE INDEX idx_job_time ON job(created_at);
