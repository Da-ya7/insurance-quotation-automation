package com.insurance.quotation.mailbox.provider;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;

import java.time.LocalDateTime;
import java.util.List;

public class DemoMailboxClient implements MailboxClient {

    @Override
    public List<EmailMessage> fetchUnreadEmails() {

        EmailMessage email = new EmailMessage(
                "DEMO-EMAIL-003",
                null,
                "john.smith@example.com",
                "Travel Insurance Quotation Request",

                """
                Name: John Smith
                Email: john.smith@example.com
                Phone: 9876543210
                Passport Number: A1234567
                Origin Country: India
                Destination Country: France
                Travel Start Date: 2026-10-20
                Travel End Date: 2026-10-30
                Travellers: 2
                Policy Type: TRAVEL
                """,

                LocalDateTime.now()
        );

        return List.of(email);
    }

    @Override
    public void markAsProcessed(String messageId) {
        System.out.println(
                "Demo email marked as processed: " + messageId
        );
    }
}