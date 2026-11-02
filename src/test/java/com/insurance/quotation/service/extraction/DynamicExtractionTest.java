package com.insurance.quotation.service.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.insurance.quotation.entity.RequiredField;
import com.insurance.quotation.entity.enums.FieldDataType;
import com.insurance.quotation.mailbox.EmailMessage;

/** Fields come from the database configuration, not from hard-coded labels. */
class DynamicExtractionTest {

    private RequiredField field(String name, String label, FieldDataType type) {
        RequiredField f = new RequiredField();
        f.setFieldName(name);
        f.setDisplayLabel(label);
        f.setDataType(type);
        return f;
    }

    private String value(List<ExtractedFieldData> list, String name) {
        return list.stream().filter(f -> name.equals(f.getFieldName()))
                .findFirst().map(ExtractedFieldData::getFieldValue).orElse(null);
    }

    @Test
    void extractsConfiguredFieldsNormalisesAndFindsAadhaar() {
        EmailMessage email = new EmailMessage("m1", null, "asha@example.com",
                "Health insurance quotation",
                """
                Name: Asha Rao
                Phone: +91 98765-43210
                Date of Birth: 12/11/1990
                Sum Insured: Rs. 5,00,000
                Aadhaar: 1234 5678 9012
                """, LocalDateTime.now());

        List<RequiredField> configured = List.of(
                field("name", "Name", FieldDataType.TEXT),
                field("phone", "Phone", FieldDataType.PHONE),
                field("dateOfBirth", "Date of Birth", FieldDataType.DATE),
                field("sumInsured", "Sum Insured", FieldDataType.NUMBER),
                field("preExistingConditions", "Pre-existing Conditions", FieldDataType.TEXT));

        List<ExtractedFieldData> out = new EmailExtractionService().extractFields(email, configured);

        assertEquals("Asha Rao", value(out, "name"));
        assertEquals("919876543210", value(out, "phone"));
        assertEquals("1990-11-12", value(out, "dateOfBirth"));
        assertEquals("500000", value(out, "sumInsured"));
        assertEquals("AADHAAR", value(out, "identityType"));
        assertEquals("1234 5678 9012", value(out, "identityNumber"));
        assertEquals("IN", value(out, "identityCountry"));

        ExtractedFieldData missing = out.stream()
                .filter(f -> "preExistingConditions".equals(f.getFieldName())).findFirst().orElseThrow();
        assertTrue(missing.isMissing());
    }

    @Test
    void emailWithoutDetailsYieldsOnlyMissingFields() {
        EmailMessage email = new EmailMessage("m2", null, "x@example.com",
                "Insurance quote", "Hi, please send me a quote.", LocalDateTime.now());
        List<ExtractedFieldData> out = new EmailExtractionService().extractFields(email,
                List.of(field("name", "Name", FieldDataType.TEXT)));
        assertTrue(out.stream().allMatch(ExtractedFieldData::isMissing));
    }
}
