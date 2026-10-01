CREATE TABLE request_field_values (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_request_id BIGINT NOT NULL,
    required_field_id BIGINT NOT NULL,
    field_value TEXT NOT NULL,
    source ENUM('EMAIL_EXTRACTED','MANUAL_ENTRY') NOT NULL,
    extracted_from_email_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_rfv_request_field UNIQUE (quotation_request_id, required_field_id),
    CONSTRAINT fk_rfv_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id),
    CONSTRAINT fk_rfv_field FOREIGN KEY (required_field_id) REFERENCES required_fields(id),
    CONSTRAINT fk_rfv_email FOREIGN KEY (extracted_from_email_id) REFERENCES incoming_emails(id)
);