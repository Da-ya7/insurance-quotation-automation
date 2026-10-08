package com.insurance.quotation.service.ingestion;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;

import java.util.List;

public class EmailIngestionService {

    private final MailboxClient mailboxClient;

    public EmailIngestionService(MailboxClient mailboxClient) {
        this.mailboxClient = mailboxClient;
    }

    public List<EmailMessage> fetchIncomingEmails() {
        return mailboxClient.fetchUnreadEmails();
    }

    public void markEmailAsProcessed(String messageId) {
        mailboxClient.markAsProcessed(messageId);
    }
}