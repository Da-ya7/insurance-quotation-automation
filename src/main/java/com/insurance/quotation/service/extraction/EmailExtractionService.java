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

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?)+",
            Pattern.CASE_INSENSITIVE
    );

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
                    "age", "gender", "sex", "nationality",
                    "country of residence", "residence country",
                    "pre-existing medical conditions",
                    "pre existing medical conditions",
                    "existing medical conditions", "medical conditions",
                    "purpose of travel", "travel purpose"
            ).stream().map(EmailExtractionService::normalizeName).toList()
    );

    public List<ExtractedFieldData> extractFields(EmailMessage email) {
        List<ExtractedFieldData> fields = new ArrayList<>();

        if (email == null) {
            return fields;
        }

        String body = normalizeBody(
                email.getBody() == null ? "" : email.getBody()
        );

        fields.add(extract(body, "name", FieldDataType.TEXT,
                "Name", "Client Name", "Customer Name"));

        fields.add(extractEmail(body));

        fields.add(extract(body, "phone", FieldDataType.PHONE,
                "Phone", "Phone Number", "Mobile", "Mobile Number"));

        fields.add(extract(body, "passportNumber", FieldDataType.TEXT,
                "Passport Number", "Passport No", "Passport"));

        fields.add(extract(body, "originCountry", FieldDataType.TEXT,
                "Origin Country", "Country of Origin",
                "Departure Country", "From Country", "Origin"));

        fields.add(extract(body, "destinationCountry", FieldDataType.TEXT,
                "Destination Country", "Destination",
                "Travel Destination", "Country of Destination"));

        fields.add(extract(body, "travelStartDate", FieldDataType.DATE,
                "Travel Start Date", "Start Date",
                "Departure Date", "Travel From"));

        fields.add(extract(body, "travelEndDate", FieldDataType.DATE,
                "Travel End Date", "End Date", "Return Date", "Travel To"));

        fields.add(extract(body, "travellers", FieldDataType.NUMBER,
                "Travellers", "Travelers", "Number of Travellers",
                "Number of Travelers", "Traveller Count", "Traveler Count"));

        ExtractedFieldData policyType = extract(
                body, "policyType", FieldDataType.TEXT,
                "Policy Type", "Insurance Type"
        );

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
                    GENERIC_FIELD_CONFIDENCE,
                    false
            );
        }

        fields.add(policyType);

        fields.add(extract(body, "dateOfBirth", FieldDataType.DATE,
                "Date of Birth", "DOB", "Birth Date"));

        fields.add(extract(body, "age", FieldDataType.NUMBER, "Age"));

        fields.add(extract(body, "gender", FieldDataType.TEXT,
                "Gender", "Sex"));

        fields.add(extract(body, "nationality", FieldDataType.TEXT,
                "Nationality"));

        fields.add(extract(body, "countryOfResidence", FieldDataType.TEXT,
                "Country of Residence", "Residence Country"));

        fields.add(extract(body, "preExistingMedicalConditions",
                FieldDataType.TEXT,
                "Pre-existing Medical Conditions",
                "Pre Existing Medical Conditions",
                "Existing Medical Conditions", "Medical Conditions"));

        fields.add(extract(body, "purposeOfTravel", FieldDataType.TEXT,
                "Purpose of Travel", "Travel Purpose"));

        extractGenericFields(body, fields);

        return fields;
    }

    private ExtractedFieldData extractEmail(String body) {
        ExtractedFieldData field = extract(
                body, "email", FieldDataType.EMAIL,
                "Email", "Email Address", "Client Email"
        );

        if (field.getFieldValue() == null) {
            return field;
        }

        Matcher matcher = EMAIL_PATTERN.matcher(field.getFieldValue());

        if (matcher.find()) {
            return new ExtractedFieldData(
                    "email",
                    matcher.group().trim(),
                    FieldDataType.EMAIL,
                    FieldSource.EMAIL_EXTRACTED,
                    EXPLICIT_FIELD_CONFIDENCE,
                    false
            );
        }

        return missingField("email", FieldDataType.EMAIL);
    }

    private ExtractedFieldData extract(
            String body,
            String fieldName,
            FieldDataType dataType,
            String... labels) {

        for (String label : labels) {
            Pattern pattern = Pattern.compile(
                    "(?im)^[\\t >*#-]*" + Pattern.quote(label)
                            + "[\\t ]*[:：=\\-–—][\\t ]*(.*?)\\s*$"
            );

            Matcher matcher = pattern.matcher(body);

            if (matcher.find()) {
                String value = cleanValue(matcher.group(1));

                if (value.isEmpty()) {
                    continue;
                }

                if (dataType == FieldDataType.EMAIL) {
                    Matcher emailMatcher = EMAIL_PATTERN.matcher(value);
                    if (emailMatcher.find()) {
                        value = emailMatcher.group().trim();
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

        return missingField(fieldName, dataType);
    }

    private void extractGenericFields(
            String body,
            List<ExtractedFieldData> fields) {

        Pattern genericPattern = Pattern.compile(
                "(?m)^[\\t >*#-]*([^:\\r\\n]{2,80}?)[\\t ]*[:：][\\t ]*(.*?)\\s*$"
        );

        Matcher matcher = genericPattern.matcher(body);

        while (matcher.find()) {
            String label = matcher.group(1).trim();
            String value = cleanValue(matcher.group(2));

            if (label.isEmpty() || value.isEmpty()) {
                continue;
            }

            String normalizedLabel = normalizeName(label);

            if (KNOWN_LABELS.contains(normalizedLabel)) {
                continue;
            }

            boolean alreadyExtracted = fields.stream().anyMatch(existing ->
                    normalizeName(existing.getFieldName()).equals(normalizedLabel));

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
    }

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

    private String cleanValue(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value.trim();

        Matcher mailto = Pattern.compile(
                "(?i)mailto:([^\\s)]+)"
        ).matcher(cleaned);

        if (mailto.find()) {
            return mailto.group(1).trim();
        }

        cleaned = cleaned.replaceAll(
                "\\[([^\\]]*)\\]\\([^)]*\\)", "$1"
        );

        return cleaned.trim();
    }

    private String normalizeBody(String body) {
        return body
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</div\\s*>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static String normalizeName(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
    }
}
