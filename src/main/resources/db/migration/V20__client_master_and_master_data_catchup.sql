-- Catch-up migration for the client master, versioning, master data, OTP and form links.
-- Safe to run on ANY database state: every change is skipped when it already exists.
-- (Replaces the earlier V15/V16 so it cannot clash with other people's V15..V19.)

-- Tables of the same name created earlier with a different design (by another branch) are
-- renamed to <name>_legacy so the correct table can be created. Nothing is deleted.

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME IN ('id','client_id','version_no','name','email','phone','date_of_birth','gender','nationality','country_of_residence','identity_country','identity_type','identity_number_last4','change_source','changed_fields','changed_by','otp_verified','created_at'))<18, 'RENAME TABLE client_versions TO client_versions_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='countries')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='countries' AND COLUMN_NAME IN ('code','name','active'))<3, 'RENAME TABLE countries TO countries_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='insurance_types')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='insurance_types' AND COLUMN_NAME IN ('code','label','keywords','base_premium','rate_percent','rate_basis_field','active','sort_order'))<8, 'RENAME TABLE insurance_types TO insurance_types_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_document_types')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='identity_document_types' AND COLUMN_NAME IN ('id','country_code','code','label','validation_regex','example','active'))<7, 'RENAME TABLE identity_document_types TO identity_document_types_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='email_subject_keywords')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='email_subject_keywords' AND COLUMN_NAME IN ('id','keyword','active'))<3, 'RENAME TABLE email_subject_keywords TO email_subject_keywords_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='form_links')>0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='form_links' AND COLUMN_NAME IN ('id','token','client_id','quotation_request_id','insurance_type','expires_at','used_at','created_at'))<8, 'RENAME TABLE form_links TO form_links_legacy', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;


SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND INDEX_NAME='uq_clients_email')>0, 'ALTER TABLE clients DROP INDEX uq_clients_email', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='client_code')=0, 'ALTER TABLE clients ADD COLUMN client_code VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='date_of_birth')=0, 'ALTER TABLE clients ADD COLUMN date_of_birth DATE NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='gender')=0, 'ALTER TABLE clients ADD COLUMN gender VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='nationality')=0, 'ALTER TABLE clients ADD COLUMN nationality VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='country_of_residence')=0, 'ALTER TABLE clients ADD COLUMN country_of_residence VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='identity_country')=0, 'ALTER TABLE clients ADD COLUMN identity_country VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='identity_type')=0, 'ALTER TABLE clients ADD COLUMN identity_type VARCHAR(40) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='identity_number_enc')=0, 'ALTER TABLE clients ADD COLUMN identity_number_enc VARCHAR(400) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='identity_number_hash')=0, 'ALTER TABLE clients ADD COLUMN identity_number_hash CHAR(64) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='identity_number_last4')=0, 'ALTER TABLE clients ADD COLUMN identity_number_last4 VARCHAR(8) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='phone_verified_at')=0, 'ALTER TABLE clients ADD COLUMN phone_verified_at TIMESTAMP NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND COLUMN_NAME='version_no')=0, 'ALTER TABLE clients ADD COLUMN version_no INT NOT NULL DEFAULT 1', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

UPDATE clients SET client_code = CONCAT('CL-', LPAD(id, 6, '0')) WHERE client_code IS NULL;

ALTER TABLE clients MODIFY client_code VARCHAR(30) NOT NULL;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND INDEX_NAME='uq_clients_client_code')=0, 'ALTER TABLE clients ADD CONSTRAINT uq_clients_client_code UNIQUE (client_code)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND INDEX_NAME='uq_clients_identity')=0, 'ALTER TABLE clients ADD CONSTRAINT uq_clients_identity UNIQUE (identity_number_hash)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='clients' AND INDEX_NAME='idx_clients_email')=0, 'ALTER TABLE clients ADD INDEX idx_clients_email (email)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS client_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    name VARCHAR(150) NULL,
    email VARCHAR(150) NOT NULL,
    phone VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='name')=0, 'ALTER TABLE client_versions ADD COLUMN name VARCHAR(150) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='email')=0, 'ALTER TABLE client_versions ADD COLUMN email VARCHAR(150) NOT NULL DEFAULT ''''', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='phone')=0, 'ALTER TABLE client_versions ADD COLUMN phone VARCHAR(20) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='date_of_birth')=0, 'ALTER TABLE client_versions ADD COLUMN date_of_birth DATE NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='gender')=0, 'ALTER TABLE client_versions ADD COLUMN gender VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='nationality')=0, 'ALTER TABLE client_versions ADD COLUMN nationality VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='country_of_residence')=0, 'ALTER TABLE client_versions ADD COLUMN country_of_residence VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='identity_country')=0, 'ALTER TABLE client_versions ADD COLUMN identity_country VARCHAR(100) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='identity_type')=0, 'ALTER TABLE client_versions ADD COLUMN identity_type VARCHAR(40) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='identity_number_last4')=0, 'ALTER TABLE client_versions ADD COLUMN identity_number_last4 VARCHAR(8) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='change_source')=0, 'ALTER TABLE client_versions ADD COLUMN change_source VARCHAR(30) NOT NULL DEFAULT ''MIGRATION''', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='changed_fields')=0, 'ALTER TABLE client_versions ADD COLUMN changed_fields VARCHAR(500) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='changed_by')=0, 'ALTER TABLE client_versions ADD COLUMN changed_by VARCHAR(150) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='otp_verified')=0, 'ALTER TABLE client_versions ADD COLUMN otp_verified BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND COLUMN_NAME='created_at')=0, 'ALTER TABLE client_versions ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND INDEX_NAME='uq_client_version')=0, 'ALTER TABLE client_versions ADD CONSTRAINT uq_client_version UNIQUE (client_id, version_no)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='client_versions' AND CONSTRAINT_NAME='fk_cvers_client' AND CONSTRAINT_TYPE='FOREIGN KEY')=0, 'ALTER TABLE client_versions ADD CONSTRAINT fk_cvers_client FOREIGN KEY (client_id) REFERENCES clients(id)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

INSERT INTO client_versions (client_id, version_no, name, email, phone, change_source, changed_fields, changed_by)
SELECT c.id, 1, c.name, c.email, c.phone, 'MIGRATION', 'initial', 'system' FROM clients c
WHERE NOT EXISTS (SELECT 1 FROM client_versions v WHERE v.client_id = c.id);

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND CONSTRAINT_NAME='fk_quotation_request' AND CONSTRAINT_TYPE='FOREIGN KEY')>0, 'ALTER TABLE quotations DROP FOREIGN KEY fk_quotation_request', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND INDEX_NAME='uq_quotation_request')>0, 'ALTER TABLE quotations DROP INDEX uq_quotation_request', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND COLUMN_NAME='version_no')=0, 'ALTER TABLE quotations ADD COLUMN version_no INT NOT NULL DEFAULT 1', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND COLUMN_NAME='client_version_no')=0, 'ALTER TABLE quotations ADD COLUMN client_version_no INT NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

ALTER TABLE quotations MODIFY status ENUM('DRAFT','SENT','SUPERSEDED') NOT NULL DEFAULT 'DRAFT';

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND INDEX_NAME='uq_quotation_request_version')=0, 'ALTER TABLE quotations ADD CONSTRAINT uq_quotation_request_version UNIQUE (quotation_request_id, version_no)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='quotations' AND CONSTRAINT_NAME='fk_quotation_request' AND CONSTRAINT_TYPE='FOREIGN KEY')=0, 'ALTER TABLE quotations ADD CONSTRAINT fk_quotation_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS countries (
    code VARCHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE IF NOT EXISTS insurance_types (
    code VARCHAR(30) PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    keywords VARCHAR(500) NULL,
    base_premium DECIMAL(12,2) NOT NULL DEFAULT 0,
    rate_percent DECIMAL(8,4) NOT NULL DEFAULT 0,
    rate_basis_field VARCHAR(80) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS identity_document_types (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    country_code VARCHAR(3) NOT NULL,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(100) NOT NULL,
    validation_regex VARCHAR(255) NOT NULL,
    example VARCHAR(60) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_identity_type UNIQUE (country_code, code)
);
CREATE TABLE IF NOT EXISTS email_subject_keywords (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    keyword VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_subject_keyword UNIQUE (keyword)
);

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='required_fields' AND COLUMN_NAME='is_mandatory')=0, 'ALTER TABLE required_fields ADD COLUMN is_mandatory BOOLEAN NOT NULL DEFAULT TRUE', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='required_fields' AND COLUMN_NAME='validation_regex')=0, 'ALTER TABLE required_fields ADD COLUMN validation_regex VARCHAR(255) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='required_fields' AND COLUMN_NAME='sort_order')=0, 'ALTER TABLE required_fields ADD COLUMN sort_order INT NOT NULL DEFAULT 0', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='required_fields' AND COLUMN_NAME='help_text')=0, 'ALTER TABLE required_fields ADD COLUMN help_text VARCHAR(255) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

UPDATE required_fields SET policy_type = NULL WHERE field_name IN ('name', 'email', 'phone', 'policyType');
UPDATE required_fields SET sort_order = id * 10 WHERE sort_order = 0;

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'dateOfBirth', 'Date of Birth', 'DATE', 'HEALTH,LIFE', TRUE, TRUE, NULL, 200, 'Format YYYY-MM-DD' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'dateOfBirth');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'identityType', 'Identity Type', 'TEXT', 'HEALTH,MOTOR,HOME,LIFE', TRUE, TRUE, NULL, 210, 'For example AADHAAR or PASSPORT' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'identityType');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'identityNumber', 'Identity Number', 'TEXT', 'HEALTH,MOTOR,HOME,LIFE', TRUE, TRUE, NULL, 220, 'Government issued ID number' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'identityNumber');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'sumInsured', 'Sum Insured', 'NUMBER', 'HEALTH,LIFE', TRUE, TRUE, NULL, 230, 'Cover amount in INR' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'sumInsured');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'preExistingConditions', 'Pre-existing Conditions', 'TEXT', 'HEALTH', TRUE, FALSE, NULL, 240, 'Write None if there are none' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'preExistingConditions');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'vehicleRegistrationNumber', 'Vehicle Registration Number', 'TEXT', 'MOTOR', TRUE, TRUE, '^[A-Za-z]{2}[ -]?[0-9]{1,2}[ -]?[A-Za-z]{0,3}[ -]?[0-9]{4}$', 300, 'Example MH12AB1234' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'vehicleRegistrationNumber');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'vehicleMake', 'Vehicle Make', 'TEXT', 'MOTOR', TRUE, TRUE, NULL, 310, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'vehicleMake');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'vehicleModel', 'Vehicle Model', 'TEXT', 'MOTOR', TRUE, TRUE, NULL, 320, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'vehicleModel');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'vehicleYear', 'Vehicle Year', 'NUMBER', 'MOTOR', TRUE, TRUE, '^(19|20)[0-9]{2}$', 330, 'Year of manufacture' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'vehicleYear');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'vehicleValue', 'Vehicle Value', 'NUMBER', 'MOTOR', TRUE, TRUE, NULL, 340, 'Current value in INR' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'vehicleValue');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'propertyAddress', 'Property Address', 'TEXT', 'HOME', TRUE, TRUE, NULL, 400, NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'propertyAddress');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'propertyType', 'Property Type', 'TEXT', 'HOME', TRUE, TRUE, NULL, 410, 'Apartment, house, ...' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'propertyType');

INSERT INTO required_fields (field_name, display_label, data_type, policy_type, is_active, is_mandatory, validation_regex, sort_order, help_text)
SELECT 'propertyValue', 'Property Value', 'NUMBER', 'HOME', TRUE, TRUE, NULL, 420, 'Value in INR' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM required_fields WHERE field_name = 'propertyValue');

INSERT IGNORE INTO insurance_types (code, label, keywords, base_premium, rate_percent, rate_basis_field, sort_order) VALUES
    ('TRAVEL', 'Travel Insurance', 'travel,trip,holiday,passport,visa,abroad', 0, 0, NULL, 1),
    ('HEALTH', 'Health Insurance', 'health,medical,mediclaim,hospital', 2000, 0.5, 'sumInsured', 2),
    ('MOTOR',  'Motor Insurance',  'motor,car,vehicle,bike,two wheeler', 3000, 2.0, 'vehicleValue', 3),
    ('HOME',   'Home Insurance',   'home,house,property,fire', 1500, 0.1, 'propertyValue', 4),
    ('LIFE',   'Life Insurance',   'life,term plan,term insurance', 1000, 0.3, 'sumInsured', 5);

INSERT IGNORE INTO countries (code, name) VALUES
    ('IN','India'),('US','United States'),('GB','United Kingdom'),('AE','United Arab Emirates'),
    ('SG','Singapore'),('AU','Australia'),('CA','Canada'),('DE','Germany'),('FR','France'),
    ('IT','Italy'),('ES','Spain'),('NL','Netherlands'),('CH','Switzerland'),('JP','Japan'),
    ('CN','China'),('TH','Thailand'),('MY','Malaysia'),('ID','Indonesia'),('LK','Sri Lanka'),
    ('NP','Nepal'),('BD','Bangladesh'),('SA','Saudi Arabia'),('QA','Qatar'),('NZ','New Zealand'),
    ('ZA','South Africa');

INSERT IGNORE INTO identity_document_types (country_code, code, label, validation_regex, example) VALUES
    ('IN','AADHAAR','Aadhaar Number','^[2-9][0-9]{11}$','1234 5678 9012'),
    ('IN','PAN','PAN Card','^[A-Z]{5}[0-9]{4}[A-Z]$','ABCDE1234F'),
    ('IN','PASSPORT','Passport','^[A-Z][0-9]{7}$','A1234567'),
    ('IN','VOTER_ID','Voter ID','^[A-Z]{3}[0-9]{7}$','ABC1234567'),
    ('US','PASSPORT','Passport','^[A-Z0-9]{6,9}$','123456789'),
    ('US','SSN','Social Security Number','^[0-9]{9}$','123-45-6789'),
    ('US','DRIVERS_LICENSE','Driver License','^[A-Z0-9]{5,20}$','D1234567'),
    ('GB','PASSPORT','Passport','^[0-9]{9}$','123456789'),
    ('GB','NI_NUMBER','National Insurance Number','^[A-Z]{2}[0-9]{6}[A-D]$','QQ123456C'),
    ('AE','EMIRATES_ID','Emirates ID','^784[0-9]{12}$','784-1990-1234567-1'),
    ('AE','PASSPORT','Passport','^[A-Z0-9]{6,9}$','N1234567'),
    ('SG','NRIC','NRIC / FIN','^[STFGM][0-9]{7}[A-Z]$','S1234567D'),
    ('SG','PASSPORT','Passport','^[A-Z0-9]{6,9}$','E1234567K'),
    ('*','PASSPORT','Passport','^[A-Z0-9]{6,9}$','A1234567'),
    ('*','NATIONAL_ID','National ID','^[A-Z0-9]{5,20}$','1234567890');

INSERT IGNORE INTO email_subject_keywords (keyword) VALUES
    ('quotation'),('quote'),('quotes'),('policy'),('insurance'),('travel'),
    ('cover'),('coverage'),('premium'),('renewal'),('mediclaim');

ALTER TABLE incoming_emails
    MODIFY processing_status ENUM('RECEIVED','PROCESSED','FAILED','IGNORED') NOT NULL DEFAULT 'RECEIVED';

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='incoming_emails' AND COLUMN_NAME='email_kind')=0, 'ALTER TABLE incoming_emails ADD COLUMN email_kind VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='incoming_emails' AND COLUMN_NAME='retry_count')=0, 'ALTER TABLE incoming_emails ADD COLUMN retry_count INT NOT NULL DEFAULT 0', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

SET @s = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='incoming_emails' AND COLUMN_NAME='last_error')=0, 'ALTER TABLE incoming_emails ADD COLUMN last_error VARCHAR(500) NULL', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS form_links (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token CHAR(64) NOT NULL,
    client_id BIGINT NULL,
    quotation_request_id BIGINT NULL,
    insurance_type VARCHAR(30) NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_form_link_token UNIQUE (token),
    CONSTRAINT fk_fl_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT fk_fl_request FOREIGN KEY (quotation_request_id) REFERENCES quotation_requests(id),
    INDEX idx_form_link_client (client_id)
);

SET @s = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='missing_fields' AND INDEX_NAME='idx_mf_status')=0, 'ALTER TABLE missing_fields ADD INDEX idx_mf_status (status)', 'DO 0');
PREPARE st FROM @s;
EXECUTE st;
DEALLOCATE PREPARE st;
