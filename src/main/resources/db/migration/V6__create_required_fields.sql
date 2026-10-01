CREATE TABLE required_fields (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    field_name VARCHAR(80) NOT NULL,
    display_label VARCHAR(120) NOT NULL,
    data_type ENUM('TEXT','DATE','NUMBER','EMAIL','PHONE') NOT NULL,
    policy_type VARCHAR(60) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_required_fields_name UNIQUE (field_name)
);