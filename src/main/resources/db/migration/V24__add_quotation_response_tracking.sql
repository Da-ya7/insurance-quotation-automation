ALTER TABLE quotations
    ADD COLUMN response_status ENUM('PENDING', 'ACCEPTED', 'REJECTED')
        NOT NULL DEFAULT 'PENDING',

    ADD COLUMN response_at TIMESTAMP NULL,

    ADD COLUMN response_token VARCHAR(64) NULL,

    ADD COLUMN reminder_sent_at TIMESTAMP NULL,

    ADD COLUMN reminder_count INT NOT NULL DEFAULT 0,

    ADD COLUMN closed_at TIMESTAMP NULL,

    ADD COLUMN closure_reason VARCHAR(50) NULL,

    ADD CONSTRAINT uq_quotation_response_token UNIQUE (response_token),

    ADD INDEX idx_quotation_response_status (response_status),

    ADD INDEX idx_quotation_reminder (response_status, reminder_sent_at);