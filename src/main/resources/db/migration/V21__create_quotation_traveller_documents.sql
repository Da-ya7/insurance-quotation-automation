
-- Store uploaded identity document files for individual travellers.
-- Uploads are optional.

CREATE TABLE quotation_traveller_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    traveller_id BIGINT NOT NULL,

    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,

    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,

    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_traveller_document
        FOREIGN KEY (traveller_id)
        REFERENCES quotation_request_travellers(id),

    CONSTRAINT uq_traveller_stored_file
        UNIQUE (stored_file_name),

    INDEX idx_traveller_documents (traveller_id)
);
