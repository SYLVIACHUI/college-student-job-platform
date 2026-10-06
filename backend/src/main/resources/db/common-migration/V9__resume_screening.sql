ALTER TABLE job ADD COLUMN recruitment_mode VARCHAR(16) NOT NULL DEFAULT 'DIRECT';
ALTER TABLE job ADD CONSTRAINT chk_job_recruitment_mode CHECK (recruitment_mode IN ('DIRECT','SCREENING'));
ALTER TABLE job_application ADD COLUMN resume_cipher TEXT;
ALTER TABLE job_application ADD COLUMN reviewed_at TIMESTAMP NULL;
CREATE INDEX idx_application_enrollment ON job_application(job_id,status);
