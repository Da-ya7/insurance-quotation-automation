CREATE TABLE email_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    quotation_request_id BIGINT NULL,
    quotation_id BIGINT NULL,
    incoming_email_id BIGINT NULL,
    direction ENUM('INBOUND','OUTBOUND') NOT NULL,
    email_type ENUM('INCOMING_REQUEST','CLIENT_REPLY','MISSING_FIELD','QUOTATION','REMINDER','MANUAL_REVIEW') NOT NULL,
    subject VARCHAR(255) NULL,
    status ENUM('RECEIVED','SENT','FAILED') NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_el_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT fk_el_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id),
    CONSTRAINT fk_el_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id),
    CONSTRAINT fk_el_incoming FOREIGN KEY (incoming_email_id) REFERENCES incoming_emails(id),
    INDEX idx_el_dedup (quotation_request_id, email_type, status)
);