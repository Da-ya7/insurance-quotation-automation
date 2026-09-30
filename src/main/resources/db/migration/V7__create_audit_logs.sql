CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    entity_type VARCHAR(40) NOT NULL,

    entity_id BIGINT NOT NULL,

    action VARCHAR(60) NOT NULL,

    performed_by VARCHAR(60) NOT NULL,

    details TEXT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_audit_entity (entity_id)
);