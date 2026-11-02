package com.insurance.quotation.service.extraction;

import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.mailbox.EmailMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailExtractionService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?)+",
            Pattern.CASE_INSENSITIVE
    );

    public List<ExtractedFieldData> extractFields(EmailMessage email) {
        List<ExtractedFieldData> fields = new ArrayList<>();

        String body = email == null || email.getBody() == null
                ? ""
                : normalizeBody(email.getBody());

        fields.add(extract(body, "Name", "name", FieldDataType.TEXT));
        fields.add(extractEmail(body));
        fields.add(extract(body, "Phone", "phone", FieldDataType.PHONE));
        fields.add(extract(body, "Passport Number", "passportNumber", FieldDataType.TEXT));
        fields.add(extract(body, "Origin Country", "originCountry", FieldDataType.TEXT));
        fields.add(extract(body, "Destination Country", "destinationCountry", FieldDataType.TEXT));
        fields.add(extract(body, "Travel Start Date", "travelStartDate", FieldDataType.DATE));
        fields.add(extract(body, "Travel End Date", "travelEndDate", FieldDataType.DATE));
        fields.add(extract(body, "Travellers", "travellers", FieldDataType.NUMBER));
        fields.add(extract(body, "Policy Type", "policyType", FieldDataType.TEXT));

        return fields;
    }

    /** [label shown in the email, identity document code, issuing country or null] */
    private static final String[][] IDENTITY_LABELS = {
            {"Aadhaar Number", "AADHAAR", "IN"}, {"Aadhaar", "AADHAAR", "IN"},
            {"Aadhar Number", "AADHAAR", "IN"}, {"Aadhar", "AADHAAR", "IN"},
            {"PAN Number", "PAN", "IN"}, {"PAN", "PAN", "IN"},
            {"Voter ID", "VOTER_ID", "IN"},
            {"Emirates ID", "EMIRATES_ID", "AE"},
            {"SSN", "SSN", "US"},
            {"NRIC", "NRIC", "SG"},
            {"Passport Number", "PASSPORT", null}, {"Passport No", "PASSPORT", null},
            {"Passport", "PASSPORT", null},
            {"Driving Licence Number", "DRIVERS_LICENSE", null},
            {"Driving License Number", "DRIVERS_LICENSE", null},
    };

    /**
     * Extracts exactly the fields configured in the database for the insurance
     * type (label = display label), plus the identity document (Aadhaar, PAN,
     * passport ...). Values are normalised: dates -> YYYY-MM-DD, phone -> digits.
     */
    public List<ExtractedFieldData> extractFields(
            EmailMessage email, List<RequiredField> requiredFields) {

        String body = email == null || email.getBody() == null
                ? "" : normalizeBody(email.getBody());

        Map<String, ExtractedFieldData> result = new LinkedHashMap<>();

        for (RequiredField rf : requiredFields) {
            String value = firstValue(body, rf.getDisplayLabel(), humanize(rf.getFieldName()));
            if (value == null || value.isBlank()) {
                result.put(rf.getFieldName(), missingField(rf.getFieldName(), rf.getDataType()));
                continue;
            }
            if (rf.getDataType() == FieldDataType.EMAIL) {
                Matcher m = EMAIL_PATTERN.matcher(value);
                value = m.find() ? m.group().trim() : value;
            }
            result.put(rf.getFieldName(), new ExtractedFieldData(
                    rf.getFieldName(),
                    ValueNormalizer.normalize(value, rf.getDataType()),
                    rf.getDataType(), FieldSource.EMAIL_EXTRACTED, 0.95, false));
        }

        // ---- identity document ----
        String type = firstValue(body, "Identity Type", "ID Type", "Document Type");
        String number = firstValue(body, "Identity Number", "ID Number", "Document Number");
        String country = firstValue(body, "Identity Country", "ID Country", "Country of Issue");

        if (number == null) {
            for (String[] entry : IDENTITY_LABELS) {
                String v = extractLabelValue(body, entry[0]);
                if (v != null && !v.isBlank()) {
                    number = v;
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

    private void putIdentity(Map<String, ExtractedFieldData> result, String name, String value) {
        if (value != null && !value.isBlank()) {
            result.put(name, new ExtractedFieldData(
                    name, value.trim(), FieldDataType.TEXT,
                    FieldSource.EMAIL_EXTRACTED, 0.9, false));
        } else if (!result.containsKey(name)) {
            result.put(name, missingField(name, FieldDataType.TEXT));
        }
    }

    private String firstValue(String body, String... labels) {
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            String v = extractLabelValue(body, label);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** vehicleRegistrationNumber -> Vehicle Registration Number */
    private static String humanize(String fieldName) {
        String spaced = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
        return spaced.isEmpty() ? spaced
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

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

    private ExtractedFieldData extract(
            String body,
            String label,
            String fieldName,
            FieldDataType dataType) {

        String value = extractLabelValue(body, label);

        if (value != null && !value.isBlank()) {
            return new ExtractedFieldData(
                    fieldName,
                    value,
                    dataType,
                    FieldSource.EMAIL_EXTRACTED,
                    0.95,
                    false
            );
        }

        return missingField(fieldName, dataType);
    }

    private String extractLabelValue(String body, String label) {
        Pattern pattern = Pattern.compile(
                "(?im)^[\\t >*#-]*"
                        + Pattern.quote(label)
                        + "[\\t ]*[:：\\-–—][\\t ]*(.*)$"
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1).trim();

        // Support Markdown links such as [person@example.com](mailto:person@example.com)
        Matcher mailto = Pattern.compile(
                "(?i)mailto:([^\\s)]+)"
        ).matcher(value);

        if (mailto.find()) {
            return mailto.group(1).trim();
        }

        // Remove basic Markdown link wrappers, if present.
        value = value.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");

        return value.trim();
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
