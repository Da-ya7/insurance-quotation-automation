-- Store individual traveller details for each quotation request.

CREATE TABLE quotation_request_travellers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,

    quotation_request_id BIGINT NOT NULL,
    traveller_number INT NOT NULL,

    name VARCHAR(150) NOT NULL,
    date_of_birth DATE NOT NULL,
    age_at_submission INT NOT NULL,

    passport_number VARCHAR(20) NOT NULL,
    gender VARCHAR(30) NULL,
    nationality VARCHAR(100) NOT NULL,
    country_of_residence VARCHAR(100) NOT NULL,

    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NOT NULL,

    pre_existing_medical_conditions TEXT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL
        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_traveller_quotation_request
        FOREIGN KEY (quotation_request_id)
        REFERENCES quotation_requests(id),

    CONSTRAINT uq_request_traveller_number
        UNIQUE (quotation_request_id, traveller_number),

    INDEX idx_travellers_request (quotation_request_id)
);

-- Store the shared purpose of the trip.
ALTER TABLE quotation_requests
    ADD COLUMN purpose_of_travel VARCHAR(60) NULL
    AFTER policy_type;