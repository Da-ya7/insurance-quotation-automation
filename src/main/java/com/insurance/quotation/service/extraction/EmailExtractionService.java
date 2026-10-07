package com.insurance.quotation.service.extraction;

import com.insurance.quotation.mailbox.EmailMessage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailExtractionService {

    public Map<String, String> extractFields(EmailMessage email) {

        Map<String, String> fields = new LinkedHashMap<>();

        String body = email.getBody();

        extract(body, "Name", "name", fields);
        extract(body, "Passport Number", "passportNumber", fields);
        extract(body, "Origin Country", "originCountry", fields);
        extract(body, "Destination Country", "destinationCountry", fields);
        extract(body, "Travel Start Date", "travelStartDate", fields);
        extract(body, "Travel End Date", "travelEndDate", fields);

        return fields;
    }

    private void extract(
            String body,
            String label,
            String fieldName,
            Map<String, String> fields) {

        Pattern pattern = Pattern.compile(
                "^" + Pattern.quote(label) + "\\s*:\\s*(.+)$",
                Pattern.MULTILINE
        );

        Matcher matcher = pattern.matcher(body);

        if (matcher.find()) {
            fields.put(fieldName, matcher.group(1).trim());
        }
    }
}