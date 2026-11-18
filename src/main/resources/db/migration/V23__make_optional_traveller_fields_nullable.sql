ALTER TABLE quotation_request_travellers
    MODIFY COLUMN date_of_birth DATE NULL,
    MODIFY COLUMN age_at_submission INT NULL,
    MODIFY COLUMN passport_number VARCHAR(20) NULL,
    MODIFY COLUMN nationality VARCHAR(100) NULL,
    MODIFY COLUMN country_of_residence VARCHAR(100) NULL;