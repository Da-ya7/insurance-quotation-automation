CREATE TABLE missing_fields (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_request_id BIGINT NOT NULL,
    required_field_id BIGINT NOT NULL,
    status ENUM('OPEN','RESOLVED') NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT fk_mf_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id),
    CONSTRAINT fk_mf_field FOREIGN KEY (required_field_id) REFERENCES required_fields(id),
    INDEX idx_mf_request_status (quotation_request_id, status)
);