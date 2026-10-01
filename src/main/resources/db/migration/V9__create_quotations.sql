CREATE TABLE quotations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_request_id BIGINT NOT NULL,
    quotation_number VARCHAR(30) NOT NULL,
    premium_amount DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(6) NOT NULL DEFAULT 'INR',
    status ENUM('DRAFT','SENT') NOT NULL DEFAULT 'DRAFT',
    pdf_path VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    CONSTRAINT uq_quotation_request UNIQUE (quotation_request_id),
    CONSTRAINT uq_quotation_number UNIQUE (quotation_number),
    CONSTRAINT fk_quotation_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id)
);