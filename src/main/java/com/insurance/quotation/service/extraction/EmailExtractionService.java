package com.insurance.quotation.service.extraction;

import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.mailbox.EmailMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailExtractionService {

    private static final double EXPLICIT_FIELD_CONFIDENCE = 0.95;
    private static final double GENERIC_FIELD_CONFIDENCE = 0.90;

    /*
     * Labels already handled by the predefined fields.
     * This prevents the generic extractor from adding duplicates.
     */
    private static final Set<String> KNOWN_LABELS = new HashSet<>(
            Arrays.asList(
                    "name", "client name", "customer name",
                    "email", "email address", "client email",
                    "phone", "phone number", "mobile", "mobile number",
                    "passport number", "passport no", "passport",
                    "origin country", "country of origin",
                    "departure country", "from country", "origin",
                    "destination country", "destination",
                    "travel destination", "country of destination",
                    "travel start date", "start date",
                    "departure date", "travel from",
                    "travel end date", "end date",
                    "return date", "travel to",
                    "travellers", "travelers",
                    "number of travellers", "number of travelers",
                    "traveller count", "traveler count",
                    "policy type", "insurance type",
                    "date of birth", "dob", "birth date",
                    "age", "gender", "sex",
                    "nationality",
                    "country of residence", "residence country",
                    "pre-existing medical conditions",
                    "pre existing medical conditions",
                    "existing medical conditions",
                    "medical conditions",
                    "purpose of travel", "travel purpose"
            ).stream()
             .map(EmailExtractionService::normalizeName)
             .toList()
    );

    public List<ExtractedFieldData> extractFields(EmailMessage email) {

        List<ExtractedFieldData> fields = new ArrayList<>();

        if (email == null) {
            return fields;
        }

        String body = email.getBody() == null
                ? ""
                : email.getBody();

        // 1. Name
        fields.add(extract(
                body, "name", FieldDataType.TEXT,
                "Name", "Client Name", "Customer Name"
        ));

        // 2. Email
        fields.add(extract(
                body, "email", FieldDataType.EMAIL,
                "Email", "Email Address", "Client Email"
        ));

        // 3. Phone
        fields.add(extract(
                body, "phone", FieldDataType.PHONE,
                "Phone", "Phone Number", "Mobile", "Mobile Number"
        ));

        // 4. Passport Number
        fields.add(extract(
                body, "passportNumber", FieldDataType.TEXT,
                "Passport Number", "Passport No", "Passport"
        ));

        // 5. Origin Country
        fields.add(extract(
                body, "originCountry", FieldDataType.TEXT,
                "Origin Country", "Country of Origin",
                "Departure Country", "From Country", "Origin"
        ));

        // 6. Destination Country
        fields.add(extract(
                body, "destinationCountry", FieldDataType.TEXT,
                "Destination Country", "Destination",
                "Travel Destination", "Country of Destination"
        ));

        // 7. Travel Start Date
        fields.add(extract(
                body, "travelStartDate", FieldDataType.DATE,
                "Travel Start Date", "Start Date",
                "Departure Date", "Travel From"
        ));

        // 8. Travel End Date
        fields.add(extract(
                body, "travelEndDate", FieldDataType.DATE,
                "Travel End Date", "End Date",
                "Return Date", "Travel To"
        ));

        // 9. Number of Travellers
        fields.add(extract(
                body, "travellers", FieldDataType.NUMBER,
                "Travellers", "Travelers",
                "Number of Travellers", "Number of Travelers",
                "Traveller Count", "Traveler Count"
        ));

        // 10. Policy Type
        ExtractedFieldData policyType = extract(
                body, "policyType", FieldDataType.TEXT,
                "Policy Type", "Insurance Type"
        );

        // Default to TRAVEL when the subject clearly identifies
        // a travel insurance or travel quotation request.
        String subject = email.getSubject() == null
                ? ""
                : email.getSubject().toLowerCase(Locale.ROOT);

        if (policyType.getFieldValue() == null
                && (subject.contains("travel insurance")
                || subject.contains("travel quotation")
                || subject.contains("travel quote"))) {

            policyType = new ExtractedFieldData(
                    "policyType",
                    "TRAVEL",
                    FieldDataType.TEXT,
                    FieldSource.EMAIL_EXTRACTED,
                    0.90,
                    false
            );
        }

        fields.add(policyType);

        // 11. Date of Birth
        fields.add(extract(
                body, "dateOfBirth", FieldDataType.DATE,
                "Date of Birth", "DOB", "Birth Date"
        ));

        // 12. Age
        fields.add(extract(
                body, "age", FieldDataType.NUMBER,
                "Age"
        ));

        // 13. Gender
        fields.add(extract(
                body, "gender", FieldDataType.TEXT,
                "Gender", "Sex"
        ));

        // 14. Nationality
        fields.add(extract(
                body, "nationality", FieldDataType.TEXT,
                "Nationality"
        ));

        // 15. Country of Residence
        fields.add(extract(
                body, "countryOfResidence", FieldDataType.TEXT,
                "Country of Residence", "Residence Country"
        ));

        // 16. Pre-existing Medical Conditions
        fields.add(extract(
                body, "preExistingMedicalConditions", FieldDataType.TEXT,
                "Pre-existing Medical Conditions",
                "Pre Existing Medical Conditions",
                "Existing Medical Conditions",
                "Medical Conditions"
        ));

        // 17. Purpose of Travel
        fields.add(extract(
                body, "purposeOfTravel", FieldDataType.TEXT,
                "Purpose of Travel", "Travel Purpose"
        ));

        /*
         * Extract any other Label: Value fields.
         * This supports additional fields configured in the database.
         */
        Pattern genericPattern = Pattern.compile(
                "^\\s*([^:\\r\\n]{2,80}?)\\s*[:\\-–—]\\s*(.*?)\\s*$",
                Pattern.MULTILINE
        );

        Matcher genericMatcher = genericPattern.matcher(body);

        while (genericMatcher.find()) {

            String label = genericMatcher.group(1).trim();
            String value = cleanValue(genericMatcher.group(2));

            if (label.isEmpty() || value.isEmpty()) {
                continue;
            }

            // Do not add a second copy of a predefined field.
            if (KNOWN_LABELS.contains(normalizeName(label))) {
                continue;
            }

            // Do not add a duplicate of an already extracted field.
            boolean alreadyExtracted = fields.stream()
                    .anyMatch(existing ->
                            normalizeName(existing.getFieldName())
                                    .equals(normalizeName(label)));

            if (alreadyExtracted) {
                continue;
            }

            fields.add(new ExtractedFieldData(
                    label,
                    value,
                    FieldDataType.TEXT,
                    FieldSource.EMAIL_EXTRACTED,
                    GENERIC_FIELD_CONFIDENCE,
                    false
            ));
        }

        return fields;
    }

    private ExtractedFieldData extract(
            String body,
            String fieldName,
            FieldDataType dataType,
            String... labels) {

        for (String label : labels) {

            Pattern pattern = Pattern.compile(
                    "^\\s*" + Pattern.quote(label)
                            + "\\s*[:\\-–—]\\s*(.*?)\\s*$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
            );

            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {

                String value = cleanValue(matcher.group(1));

                if (value.isEmpty()) {
                    continue;
                }

                // Convert Markdown email links into plain email addresses.
                if ("email".equals(fieldName)) {
                    Matcher emailMatcher = Pattern.compile(
                            "[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}",
                            Pattern.CASE_INSENSITIVE
                    ).matcher(value);

                    if (emailMatcher.find()) {
                        value = emailMatcher.group();
                    }
                }

                return new ExtractedFieldData(
                        fieldName,
                        value,
                        dataType,
                        FieldSource.EMAIL_EXTRACTED,
                        EXPLICIT_FIELD_CONFIDENCE,
                        false
                );
            }
        }

        // No usable value was found for this field.
        return new ExtractedFieldData(
                fieldName,
                null,
                dataType,
                FieldSource.EMAIL_EXTRACTED,
                0.0,
                true
        );
    }

    private String cleanValue(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replaceFirst("\\\\\\s*$", "")
                .trim();
    }

    private static String normalizeName(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
