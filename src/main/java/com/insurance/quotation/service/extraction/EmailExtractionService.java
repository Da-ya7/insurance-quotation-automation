
package com.insurance.quotation.service.extraction;

import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.mailbox.EmailMessage;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailExtractionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?)+",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[][] IDENTITY_LABELS = {
            {"Aadhaar Number", "AADHAAR", "IN"},
            {"Aadhaar", "AADHAAR", "IN"},
            {"Aadhar Number", "AADHAAR", "IN"},
            {"Aadhar", "AADHAAR", "IN"},
            {"PAN Number", "PAN", "IN"},
            {"PAN", "PAN", "IN"},
            {"Voter ID", "VOTER_ID", "IN"},
            {"Emirates ID", "EMIRATES_ID", "AE"},
            {"SSN", "SSN", "US"},
            {"NRIC", "NRIC", "SG"},
            {"Passport Number", "PASSPORT", null},
            {"Passport No", "PASSPORT", null},
            {"Passport", "PASSPORT", null},
            {"Driving Licence Number", "DRIVERS_LICENSE", null},
            {"Driving License Number", "DRIVERS_LICENSE", null}
    };

    /**
     * Fixed-field extraction.
     * Supports the existing extractFields(email) caller.
     */
    public List<ExtractedFieldData> extractFields(EmailMessage email) {

        List<ExtractedFieldData> fields = new ArrayList<>();

        String body = email == null || email.getBody() == null
                ? ""
                : normalizeBody(email.getBody());

        fields.add(extract(body, "Name", "name", FieldDataType.TEXT));

        fields.add(extractEmail(body));

        fields.add(extract(body, "Phone", "phone", FieldDataType.PHONE));

        fields.add(extractAny(
                body,
                "passportNumber",
                FieldDataType.TEXT,
                "Passport Number",
                "Passport No",
                "Passport"
        ));

        fields.add(extract(body, "Origin Country",
                "originCountry", FieldDataType.TEXT));

        fields.add(extract(body, "Destination Country",
                "destinationCountry", FieldDataType.TEXT));

        fields.add(extract(body, "Travel Start Date",
                "travelStartDate", FieldDataType.DATE));

        fields.add(extract(body, "Travel End Date",
                "travelEndDate", FieldDataType.DATE));

        fields.add(extract(body, "Travellers",
                "travellers", FieldDataType.NUMBER));

        fields.add(extractAny(
                body,
                "policyType",
                FieldDataType.TEXT,
                "Policy Type",
                "Insurance Type"
        ));

        fields.add(extractAny(
                body,
                "dateOfBirth",
                FieldDataType.DATE,
                "Date of Birth",
                "DOB"
        ));

        fields.add(extract(
                body,
                "Age",
                "age",
                FieldDataType.NUMBER
        ));

        fields.add(extract(
                body,
                "Gender",
                "gender",
                FieldDataType.TEXT
        ));

        fields.add(extract(
                body,
                "Nationality",
                "nationality",
                FieldDataType.TEXT
        ));

        fields.add(extract(
                body,
                "Country of Residence",
                "countryOfResidence",
                FieldDataType.TEXT
        ));

        fields.add(extractAny(
                body,
                "preExistingMedicalConditions",
                FieldDataType.TEXT,
                "Pre-existing Medical Conditions",
                "Pre Existing Medical Conditions",
                "Medical Conditions"
        ));

        fields.add(extractAny(
                body,
                "purposeOfTravel",
                FieldDataType.TEXT,
                "Purpose of Travel",
                "Travel Purpose"
        ));

        return fields;
    }

    /**
     * Dynamic extraction using database-configured fields.
     */
    public List<ExtractedFieldData> extractFields(
            EmailMessage email,
            List<RequiredField> requiredFields) {

        String body = email == null || email.getBody() == null
                ? ""
                : normalizeBody(email.getBody());

        Map<String, ExtractedFieldData> result =
                new LinkedHashMap<>();

        if (requiredFields != null) {
            for (RequiredField rf : requiredFields) {

                if (rf == null || rf.getFieldName() == null
                        || rf.getFieldName().isBlank()) {
                    continue;
                }

                List<String> labels = labelsForField(
                        rf.getFieldName(),
                        rf.getDisplayLabel()
                );

                String value = firstValue(
                        body,
                        labels.toArray(new String[0])
                );

                if (value == null || value.isBlank()) {
                    result.put(
                            rf.getFieldName(),
                            missingField(
                                    rf.getFieldName(),
                                    rf.getDataType()
                            )
                    );
                    continue;
                }

                if (rf.getDataType() == FieldDataType.EMAIL) {
                    Matcher matcher = EMAIL_PATTERN.matcher(value);
                    value = matcher.find()
                            ? matcher.group().trim()
                            : value;
                }

                result.put(
                        rf.getFieldName(),
                        new ExtractedFieldData(
                                rf.getFieldName(),
                                ValueNormalizer.normalize(
                                        value,
                                        rf.getDataType()
                                ),
                                rf.getDataType(),
                                FieldSource.EMAIL_EXTRACTED,
                                0.95,
                                false
                        )
                );
            }
        }

        /*
         * Also extract known customer and travel fields.
         * The automation workflow uses this database-configured overload.
         * These values are returned as extracted data. The existing workflow
         * remains responsible for deciding which values are persisted.
         */
        addFixedFieldIfMissing(
                result, body, "name", FieldDataType.TEXT,
                "Name", "Full Name", "Customer Name"
        );

        addFixedFieldIfMissing(
                result, body, "email", FieldDataType.EMAIL,
                "Email", "Email Address", "E-mail"
        );

        addFixedFieldIfMissing(
                result, body, "phone", FieldDataType.PHONE,
                "Phone", "Phone Number", "Mobile", "Mobile Number",
                "Contact Number"
        );

        addFixedFieldIfMissing(
                result, body, "dateOfBirth", FieldDataType.DATE,
                "Date of Birth", "DOB", "Birth Date"
        );

        addFixedFieldIfMissing(
                result, body, "age", FieldDataType.NUMBER,
                "Age"
        );

        addFixedFieldIfMissing(
                result, body, "gender", FieldDataType.TEXT,
                "Gender", "Sex"
        );

        addFixedFieldIfMissing(
                result, body, "nationality", FieldDataType.TEXT,
                "Nationality", "National Origin"
        );

        addFixedFieldIfMissing(
                result, body, "countryOfResidence", FieldDataType.TEXT,
                "Country of Residence", "Country of Residency",
                "Residence Country", "Residential Country"
        );

        addFixedFieldIfMissing(
                result, body, "passportNumber", FieldDataType.TEXT,
                "Passport Number", "Passport No", "Passport",
                "Passport No."
        );

        addFixedFieldIfMissing(
                result, body, "originCountry", FieldDataType.TEXT,
                "Origin Country", "Country of Origin",
                "Departure Country", "From Country"
        );

        addFixedFieldIfMissing(
                result, body, "destinationCountry", FieldDataType.TEXT,
                "Destination Country", "Country of Destination",
                "Travel Destination", "To Country"
        );

        addFixedFieldIfMissing(
                result, body, "travelStartDate", FieldDataType.DATE,
                "Travel Start Date", "Start Date",
                "Departure Date", "Trip Start Date"
        );

        addFixedFieldIfMissing(
                result, body, "travelEndDate", FieldDataType.DATE,
                "Travel End Date", "End Date",
                "Return Date", "Trip End Date"
        );

        addFixedFieldIfMissing(
                result, body, "travellers", FieldDataType.NUMBER,
                "Travellers", "Travelers", "Number of Travellers",
                "Number of Travelers", "No. of Travellers",
                "No. of Travelers"
        );

        addFixedFieldIfMissing(
                result, body, "policyType", FieldDataType.TEXT,
                "Policy Type", "Insurance Type"
        );

        addFixedFieldIfMissing(
                result, body, "preExistingMedicalConditions",
                FieldDataType.TEXT,
                "Pre-existing Medical Conditions",
                "Pre Existing Medical Conditions",
                "Pre-existing Conditions",
                "Medical Conditions"
        );

        addFixedFieldIfMissing(
                result, body, "purposeOfTravel", FieldDataType.TEXT,
                "Purpose of Travel", "Travel Purpose",
                "Purpose"
        );

        // Identity document extraction.
        String type = firstValue(
                body,
                "Identity Document Type",
                "Identity Type",
                "ID Type",
                "Document Type"
        );

        String number = firstValue(
                body,
                "Identity Document Number",
                "Identity Number",
                "ID Number",
                "Document Number"
        );

        String country = firstValue(
                body,
                "Identity Document Issuing Country",
                "Issuing Country",
                "Identity Country",
                "ID Country",
                "Country of Issue",
                "Country of Issuance"
        );

        if (number == null) {
            for (String[] entry : IDENTITY_LABELS) {

                String value = extractLabelValue(body, entry[0]);

                if (value != null && !value.isBlank()) {
                    number = value;
                    type = entry[1];

                    if (country == null) {
                        country = entry[2];
                    }

                    break;
                }
            }
        }

        putIdentity(result, "identityType", type);
        putIdentity(result, "identityNumber", number);
        putIdentity(result, "identityCountry", country);

        return new ArrayList<>(result.values());
    }

    /**
     * Return common email labels for a configured field.
     */
    private List<String> labelsForField(
            String fieldName,
            String displayLabel) {

        List<String> labels = new ArrayList<>();

        addLabel(labels, displayLabel);
        addLabel(labels, humanize(fieldName));

        String key = fieldName == null
                ? ""
                : fieldName.replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase();

        switch (key) {

            case "name":
            case "fullname":
            case "clientname":
            case "customername":
                addLabels(labels, "Name", "Full Name", "Customer Name");
                break;

            case "email":
            case "emailaddress":
                addLabels(labels, "Email", "Email Address", "E-mail");
                break;

            case "phone":
            case "phonenumber":
            case "mobile":
            case "mobilenumber":
            case "contactnumber":
                addLabels(labels, "Phone", "Phone Number", "Mobile",
                        "Mobile Number", "Contact Number");
                break;

            case "dateofbirth":
            case "dob":
            case "birthdate":
                addLabels(labels, "Date of Birth", "DOB", "Birth Date");
                break;

            case "gender":
            case "sex":
                addLabels(labels, "Gender", "Sex");
                break;

            case "nationality":
            case "nationalorigin":
                addLabels(labels, "Nationality", "National Origin");
                break;

            case "countryofresidence":
            case "countryofresidency":
            case "residencecountry":
            case "residentialcountry":
                addLabels(labels, "Country of Residence",
                        "Country of Residency", "Residence Country",
                        "Residential Country");
                break;

            case "passportnumber":
            case "passportno":
            case "passport":
                addLabels(labels, "Passport Number", "Passport No",
                        "Passport", "Passport No.");
                break;

            case "origincountry":
            case "countryoforigin":
                addLabels(labels, "Origin Country", "Country of Origin",
                        "Departure Country", "From Country");
                break;

            case "destinationcountry":
            case "countryofdestination":
                addLabels(labels, "Destination Country",
                        "Country of Destination", "Travel Destination",
                        "To Country");
                break;

            case "travelstartdate":
            case "startdate":
            case "departuredate":
                addLabels(labels, "Travel Start Date", "Start Date",
                        "Departure Date", "Trip Start Date");
                break;

            case "travelenddate":
            case "enddate":
            case "returndate":
                addLabels(labels, "Travel End Date", "End Date",
                        "Return Date", "Trip End Date");
                break;

            case "travellers":
            case "travelers":
            case "numberoftravellers":
            case "numberoftravelers":
                addLabels(labels, "Travellers", "Travelers",
                        "Number of Travellers", "Number of Travelers",
                        "No. of Travellers", "No. of Travelers");
                break;

            case "policytype":
            case "insurancetype":
                addLabels(labels, "Policy Type", "Insurance Type");
                break;

            case "preexistingmedicalconditions":
            case "medicalconditions":
                addLabels(labels, "Pre-existing Medical Conditions",
                        "Pre Existing Medical Conditions",
                        "Pre-existing Conditions", "Medical Conditions");
                break;

            case "purposeoftravel":
            case "travelpurpose":
                addLabels(labels, "Purpose of Travel", "Travel Purpose",
                        "Purpose");
                break;

            default:
                break;
        }

        return labels;
    }

    private void addFixedFieldIfMissing(
            Map<String, ExtractedFieldData> result,
            String body,
            String fieldName,
            FieldDataType dataType,
            String... labels) {

        if (result.containsKey(fieldName)) {
            return;
        }

        String value = firstValue(body, labels);

        if (value == null || value.isBlank()) {
            return;
        }

        if (dataType == FieldDataType.EMAIL) {
            Matcher matcher = EMAIL_PATTERN.matcher(value);

            if (matcher.find()) {
                value = matcher.group().trim();
            }
        }

        result.put(
                fieldName,
                new ExtractedFieldData(
                        fieldName,
                        ValueNormalizer.normalize(value, dataType),
                        dataType,
                        FieldSource.EMAIL_EXTRACTED,
                        0.95,
                        false
                )
        );
    }

    private void addLabel(List<String> labels, String label) {

        if (label != null && !label.isBlank()
                && !labels.contains(label)) {
            labels.add(label);
        }
    }

    private void addLabels(List<String> labels, String... values) {

        for (String value : values) {
            addLabel(labels, value);
        }
    }

    /**
     * Add identity document values.
     */
    private void putIdentity(
            Map<String, ExtractedFieldData> result,
            String name,
            String value) {

        if (value != null && !value.isBlank()) {

            result.put(
                    name,
                    new ExtractedFieldData(
                            name,
                            value.trim(),
                            FieldDataType.TEXT,
                            FieldSource.EMAIL_EXTRACTED,
                            0.9,
                            false
                    )
            );

        } else if (!result.containsKey(name)) {

            result.put(
                    name,
                    missingField(name, FieldDataType.TEXT)
            );
        }
    }

    /**
     * Extract the first available value from multiple possible labels.
     */
    private String firstValue(
            String body,
            String... labels) {

        for (String label : labels) {

            if (label == null || label.isBlank()) {
                continue;
            }

            String value = extractLabelValue(body, label);

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    /**
     * Convert camelCase field names into readable labels.
     */
    private static String humanize(String fieldName) {

        if (fieldName == null) {
            return "";
        }

        String spaced = fieldName.replaceAll(
                "([a-z])([A-Z])",
                "$1 $2"
        );

        return spaced.isEmpty()
                ? spaced
                : Character.toUpperCase(spaced.charAt(0))
                + spaced.substring(1);
    }

    /**
     * Extract email address.
     */
    private ExtractedFieldData extractEmail(String body) {

        String value = extractLabelValue(body, "Email");

        if (value != null) {

            Matcher matcher = EMAIL_PATTERN.matcher(value);

            if (matcher.find()) {

                return new ExtractedFieldData(
                        "email",
                        matcher.group().trim(),
                        FieldDataType.EMAIL,
                        FieldSource.EMAIL_EXTRACTED,
                        0.95,
                        false
                );
            }
        }

        return missingField("email", FieldDataType.EMAIL);
    }

    /**
     * Extract a single field.
     */
    private ExtractedFieldData extract(
            String body,
            String label,
            String fieldName,
            FieldDataType dataType) {

        String value = extractLabelValue(body, label);

        if (value != null && !value.isBlank()) {

            return new ExtractedFieldData(
                    fieldName,
                    ValueNormalizer.normalize(value, dataType),
                    dataType,
                    FieldSource.EMAIL_EXTRACTED,
                    0.95,
                    false
            );
        }

        return missingField(fieldName, dataType);
    }

    /**
     * Extract a field using alternative labels.
     */
    private ExtractedFieldData extractAny(
            String body,
            String fieldName,
            FieldDataType dataType,
            String... labels) {

        String value = firstValue(body, labels);

        if (value != null && !value.isBlank()) {

            return new ExtractedFieldData(
                    fieldName,
                    ValueNormalizer.normalize(value, dataType),
                    dataType,
                    FieldSource.EMAIL_EXTRACTED,
                    0.95,
                    false
            );
        }

        return missingField(fieldName, dataType);
    }

    /**
     * Extract values from email label formats such as:
     * Phone: 7896546785
     * Phone - 7896546785
     * - Phone: 7896546785
     */
    private String extractLabelValue(
            String body,
            String label) {

        if (body == null || body.isBlank()
                || label == null || label.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile(
                "(?im)^[\\t ]*(?:[-*•>]?[\\t ]*)?"
                        + Pattern.quote(label)
                        + "[\\t ]*(?::|：|[-–—])[\\t ]*(.*?)\\s*$"
        );

        Matcher matcher = pattern.matcher(body);

        while (matcher.find()) {

            String value = matcher.group(1).trim();

            if (value.isBlank()) {
                continue;
            }

            Matcher mailto = Pattern.compile(
                    "(?i)mailto:([^\\s)]+)"
            ).matcher(value);

            if (mailto.find()) {
                return mailto.group(1).trim();
            }

            value = value.replaceAll(
                    "\\[([^\\]]*)\\]\\([^)]*\\)",
                    "$1"
            );

            value = value.replaceAll(
                    "^\\*\\*(.*?)\\*\\*$",
                    "$1"
            );

            return value.trim();
        }

        return null;
    }

    /**
     * Normalize plain-text and HTML email bodies.
     */
    private String normalizeBody(String body) {

        if (body == null) {
            return "";
        }

        return body
                .replaceAll(
                        "(?i)<br\\s*/?>",
                        "\n"
                )
                .replaceAll(
                        "(?i)</p\\s*>",
                        "\n"
                )
                .replaceAll(
                        "(?i)</div\\s*>",
                        "\n"
                )
                .replaceAll(
                        "(?i)</li\\s*>",
                        "\n"
                )
                .replaceAll(
                        "<[^>]+>",
                        " "
                )
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    /**
     * Create a missing field.
     */
    private ExtractedFieldData missingField(
            String fieldName,
            FieldDataType dataType) {

        return new ExtractedFieldData(
                fieldName,
                null,
                dataType,
                FieldSource.EMAIL_EXTRACTED,
                0.0,
                true
        );
    }
}
