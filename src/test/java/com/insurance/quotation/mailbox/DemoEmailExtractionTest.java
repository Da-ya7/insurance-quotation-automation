package com.insurance.quotation.mailbox;

import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.service.extraction.EmailExtractionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

        Map<String, String> fields =
                extractionService.extractFields(email);

        System.out.println("===== EMAIL =====");
        System.out.println("Subject: " + email.getSubject());
        System.out.println("From: " + email.getSenderEmail());

        System.out.println("\n===== EMAIL BODY =====");
        System.out.println(email.getBody());

        System.out.println("\n===== EXTRACTED FIELDS =====");

        fields.forEach((key, value) ->
                System.out.println(key + " = " + value)
        );

        assertEquals("John Smith", fields.get("name"));
        assertEquals("A1234567", fields.get("passportNumber"));
        assertEquals("India", fields.get("originCountry"));
        assertEquals("France", fields.get("destinationCountry"));
        assertEquals("20-10-2026", fields.get("travelStartDate"));
        assertEquals("30-10-2026", fields.get("travelEndDate"));
    }
}


// package com.insurance.quotation.mailbox;

// import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
// import com.insurance.quotation.service.extraction.EmailExtractionService;
// import org.junit.jupiter.api.Test;

// import java.util.List;
// import java.util.Map;

// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertFalse;

// class DemoEmailExtractionTest {

//     @Test
//     void shouldExtractQuotationDetailsFromDemoEmail() {

//         // Create demo mailbox
//         DemoMailboxClient mailboxClient = new DemoMailboxClient();

//         // Create email extraction service
//         EmailExtractionService extractionService =
//                 new EmailExtractionService();

//         System.out.println("STEP 1: Test started");

//         // Fetch unread emails
//         List<EmailMessage> emails =
//                 mailboxClient.fetchUnreadEmails();

//         System.out.println("STEP 2: Emails fetched = " + emails.size());

//         // Make sure at least one email was received
//         assertFalse(emails.isEmpty());

//         // Get the first email
//         EmailMessage email = emails.get(0);

//         System.out.println("STEP 3: Email received");

//         System.out.println("Subject: " + email.getSubject());
//         System.out.println("From: " + email.getSenderEmail());

//         System.out.println("Body:");
//         System.out.println(email.getBody());

//         System.out.println("STEP 4: Starting extraction");

//         // Extract quotation fields from email
//         Map<String, String> fields =
//                 extractionService.extractFields(email);

//         System.out.println("STEP 5: Extraction completed");

//         System.out.println("\n===== EXTRACTED FIELDS =====");

//         fields.forEach((key, value) ->
//                 System.out.println(key + " = " + value)
//         );

//         // Verify extracted values
//         assertEquals("John Smith", fields.get("name"));
//         assertEquals("A1234567", fields.get("passportNumber"));
//         assertEquals("India", fields.get("originCountry"));
//         assertEquals("France", fields.get("destinationCountry"));
//         assertEquals("20-10-2026", fields.get("travelStartDate"));
//         assertEquals("30-10-2026", fields.get("travelEndDate"));
//     }
// }