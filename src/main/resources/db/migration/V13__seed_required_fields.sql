INSERT INTO required_fields
    (field_name, display_label, data_type, policy_type, is_active)
VALUES
    ('name', 'Name', 'TEXT', 'TRAVEL', TRUE),
    ('email', 'Email', 'EMAIL', 'TRAVEL', TRUE),
    ('phone', 'Phone', 'PHONE', 'TRAVEL', TRUE),
    ('passportNumber', 'Passport Number', 'TEXT', 'TRAVEL', TRUE),
    ('originCountry', 'Origin Country', 'TEXT', 'TRAVEL', TRUE),
    ('destinationCountry', 'Destination Country', 'TEXT', 'TRAVEL', TRUE),
    ('travelStartDate', 'Travel Start Date', 'DATE', 'TRAVEL', TRUE),
    ('travelEndDate', 'Travel End Date', 'DATE', 'TRAVEL', TRUE),
    ('travellers', 'Travellers', 'NUMBER', 'TRAVEL', TRUE),
    ('policyType', 'Policy Type', 'TEXT', 'TRAVEL', TRUE);