CREATE TABLE quotation_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    quotation_id BIGINT NOT NULL,
    description VARCHAR(200) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_qi_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id)
);