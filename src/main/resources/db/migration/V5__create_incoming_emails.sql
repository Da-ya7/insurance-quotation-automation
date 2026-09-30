CREATE TABLE incoming_emails (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_request_id BIGINT NULL,
    message_id VARCHAR(255) NOT NULL UNIQUE,

    CONSTRAINT fk_incoming_email_request
        FOREIGN KEY (quotation_request_id)
        REFERENCES quotation_requests(id)
);