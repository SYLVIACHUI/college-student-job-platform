CREATE TABLE company_profile (
  publisher_id VARCHAR(36) PRIMARY KEY,
  introduction VARCHAR(2000) NOT NULL DEFAULT '',
  FOREIGN KEY(publisher_id) REFERENCES publisher_user(id)
) ENGINE=InnoDB;

CREATE TABLE company_photo (
  id VARCHAR(36) PRIMARY KEY,
  publisher_id VARCHAR(36) NOT NULL,
  image_data MEDIUMBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY(publisher_id) REFERENCES publisher_user(id)
) ENGINE=InnoDB;
CREATE INDEX idx_company_photo_owner ON company_photo(publisher_id,created_at);
