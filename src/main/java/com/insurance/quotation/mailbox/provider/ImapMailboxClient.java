package com.insurance.quotation.mailbox.provider;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.MailboxException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ImapMailboxClient implements MailboxClient {

    @Override
    public List<EmailMessage> fetchUnreadEmails() {

        try {

            // TODO:
            // Connect to IMAP server
            // Read unread emails
            // Convert them into EmailMessage objects

            List<EmailMessage> emails = new ArrayList<>();

            return emails;

        } catch (Exception e) {

            throw new MailboxException(
                    "Failed to fetch emails from IMAP mailbox",
                    e
            );
        }
    }

    @Override
    public void markAsProcessed(String messageId) {

        try {

            // TODO:
            // Connect to IMAP mailbox
            // Find email using messageId
            // Mark email as SEEN/processed

        } catch (Exception e) {

            throw new MailboxException(
                    "Failed to mark email as processed: " + messageId,
                    e
            );
        }
    }
}