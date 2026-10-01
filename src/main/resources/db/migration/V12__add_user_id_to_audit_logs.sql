ALTER TABLE audit_logs
    ADD COLUMN user_id BIGINT NULL AFTER performed_by,
    ADD CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id),
    ADD INDEX idx_audit_user (user_id);