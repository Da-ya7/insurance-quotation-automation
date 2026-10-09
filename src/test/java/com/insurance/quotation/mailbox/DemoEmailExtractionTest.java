package com.insurance.quotation.mailbox;

import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.service.extraction.EmailExtractionService;
import com.insurance.quotation.service.extraction.ExtractedFieldData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DemoEmailExtractionTest {

    @Test
    void shouldExtractQuotationDetailsFromDemoEmail() {

        DemoMailboxClient mailboxClient = new DemoMailboxClient();

        EmailExtractionService extractionService =
                new EmailExtractionService();

        List<EmailMessage> emails =
                mailboxClient.fetchUnreadEmails();

        assertFalse(emails.isEmpty());

        EmailMessage email = emails.get(0);

        List<ExtractedFieldData> fields =
                extractionService.extractFields(email);

        System.out.println("===== EMAIL =====");
        System.out.println("Subject: " + email.getSubject());
        System.out.println("From: " + email.getSenderEmail());

        System.out.println("\n===== EMAIL BODY =====");
        System.out.println(email.getBody());

        System.out.println("\n===== EXTRACTED FIELDS =====");

        fields.forEach(field ->
                System.out.println(
                        field.getFieldName() + " = " +
                        field.getFieldValue()
                )
        );

        assertEquals("John Smith", getValue(fields, "name"));
        assertEquals("john.smith@example.com", getValue(fields, "email"));
        assertEquals("9876543210", getValue(fields, "phone"));
        assertEquals("A1234567", getValue(fields, "passportNumber"));
        assertEquals("India", getValue(fields, "originCountry"));
        assertEquals("France", getValue(fields, "destinationCountry"));
        assertEquals("2026-10-20", getValue(fields, "travelStartDate"));
        assertEquals("2026-10-30", getValue(fields, "travelEndDate"));
        assertEquals("2", getValue(fields, "travellers"));
        assertEquals("TRAVEL", getValue(fields, "policyType"));
    }

    private String getValue(
            List<ExtractedFieldData> fields,
            String fieldName) {

        return fields.stream()
                .filter(field ->
                        fieldName.equalsIgnoreCase(field.getFieldName()))
                .map(ExtractedFieldData::getFieldValue)
                .findFirst()
                .orElse(null);
    }
}