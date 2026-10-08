package com.insurance.quotation.service.extraction;

import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.entity.enums.FieldSource;
import com.insurance.quotation.mailbox.EmailMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailExtractionService {

    public List<ExtractedFieldData> extractFields(EmailMessage email) {

        List<ExtractedFieldData> fields = new ArrayList<>();

        String body = email.getBody();

        fields.add(extract(body, "Name", "name", FieldDataType.TEXT));
        fields.add(extract(body, "Email", "email", FieldDataType.EMAIL));
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

    private ExtractedFieldData extract(
            String body,
            String label,
            String fieldName,
            FieldDataType dataType) {

        Pattern pattern = Pattern.compile(
                "^" + Pattern.quote(label) + "\\s*:\\s*(.*)$",
                Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {

            String value = matcher.group(1).trim();

            if (!value.isEmpty()) {
                return new ExtractedFieldData(
                        fieldName,
                        value,
                        dataType,
                        FieldSource.EMAIL_EXTRACTED,
                        0.95,
                        false
                );
            }
        }

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