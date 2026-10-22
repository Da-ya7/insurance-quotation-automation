
// package com.insurance.quotation.service.extraction;

// import com.insurance.quotation.entity.enums.FieldDataType;
// import com.insurance.quotation.entity.enums.FieldSource;
// import com.insurance.quotation.mailbox.EmailMessage;
// import org.springframework.stereotype.Service;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.regex.Matcher;
// import java.util.regex.Pattern;

// @Service
// public class EmailExtractionService {

//     public List<ExtractedFieldData> extractFields(EmailMessage email) {

//         List<ExtractedFieldData> fields = new ArrayList<>();

//         String body = email.getBody() == null ? "" : email.getBody();

//         fields.add(extract(body, "Name", "name", FieldDataType.TEXT));
//         fields.add(extract(body, "Email", "email", FieldDataType.EMAIL));
//         fields.add(extract(body, "Phone", "phone", FieldDataType.PHONE));
//         fields.add(extract(body, "Passport Number", "passportNumber", FieldDataType.TEXT));
//         fields.add(extract(body, "Origin Country", "originCountry", FieldDataType.TEXT));
//         fields.add(extract(body, "Destination Country", "destinationCountry", FieldDataType.TEXT));
//         fields.add(extract(body, "Travel Start Date", "travelStartDate", FieldDataType.DATE));
//         fields.add(extract(body, "Travel End Date", "travelEndDate", FieldDataType.DATE));
//         fields.add(extract(body, "Travellers", "travellers", FieldDataType.NUMBER));
//         fields.add(extract(body, "Policy Type", "policyType", FieldDataType.TEXT));

//         return fields;
//     }

//     private ExtractedFieldData extract(
//             String body,
//             String label,
//             String fieldName,
//             FieldDataType dataType) {

//         Pattern pattern = Pattern.compile(
//                 "^\\s*" + Pattern.quote(label)
//                         + "\\s*[:\\-–—]\\s*(.*?)\\s*$",
//                 Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
//         );

//         Matcher matcher = pattern.matcher(body);

//         if (matcher.find()) {

//             String value = matcher.group(1).trim();

//             if (!value.isEmpty()) {
//                 return new ExtractedFieldData(
//                         fieldName,
//                         value,
//                         dataType,
//                         FieldSource.EMAIL_EXTRACTED,
//                         0.95,
//                         false
//                 );
//             }
//         }

//         return new ExtractedFieldData(
//                 fieldName,
//                 null,
//                 dataType,
//                 FieldSource.EMAIL_EXTRACTED,
//                 0.0,
//                 true
//         );
//     }
// }



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
 
 