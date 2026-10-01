CREATE TABLE incoming_emails (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_request_id BIGINT NULL,
    message_id VARCHAR(255) NOT NULL,
    in_reply_to VARCHAR(255) NULL,
    sender_email VARCHAR(150) NOT NULL,
    subject VARCHAR(255) NULL,
    body TEXT NULL,
    processing_status ENUM('RECEIVED','PROCESSED','FAILED') NOT NULL DEFAULT 'RECEIVED',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_incoming_message_id UNIQUE (message_id),
    CONSTRAINT fk_incoming_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id),
    INDEX idx_incoming_sender (sender_email)
);