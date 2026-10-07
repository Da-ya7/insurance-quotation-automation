package com.insurance.quotation.mailbox.provider;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;

import java.time.LocalDateTime;
import java.util.List;

public class DemoMailboxClient implements MailboxClient {

    @Override
    public List<EmailMessage> fetchUnreadEmails() {

        EmailMessage email = new EmailMessage(
                "DEMO-EMAIL-001",
                null,
                "john.smith@example.com",
                "Travel Insurance Quotation Request",

                """
                Name: John Smith
                Passport Number: A1234567
                Origin Country: India
                Destination Country: France
                Travel Start Date: 20-10-2026
                Travel End Date: 30-10-2026
                """,

                LocalDateTime.now()
        );

        return List.of(email);
    }

    @Override
    public void markAsProcessed(String messageId) {
        System.out.println("Demo email marked as processed: " + messageId);
    }
}