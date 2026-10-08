package com.insurance.quotation.mailbox;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.service.ingestion.EmailIngestionService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoEmailIngestionTest {

    @Test
    void shouldFetchIncomingEmails() {

        MailboxClient mailboxClient = new DemoMailboxClient();

        EmailIngestionService ingestionService =
                new EmailIngestionService(mailboxClient);

        List<EmailMessage> emails =
                ingestionService.fetchIncomingEmails();

        assertNotNull(emails);
        assertFalse(emails.isEmpty());

        EmailMessage email = emails.get(0);

        System.out.println("===== INGESTED EMAIL =====");
        System.out.println("Subject: " + email.getSubject());

        System.out.println("===== EMAIL BODY =====");
        System.out.println(email.getBody());

        assertEquals(
                "Travel Insurance Quotation Request",
                email.getSubject()
        );

        assertNotNull(email.getBody());

        System.out.println("===== INGESTION SUCCESS =====");
    }
}
