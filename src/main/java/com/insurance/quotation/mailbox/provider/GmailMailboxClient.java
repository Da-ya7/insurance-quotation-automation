package com.insurance.quotation.mailbox.provider;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.MailboxException;

import java.util.ArrayList;
import java.util.List;

public class GmailMailboxClient implements MailboxClient {

    @Override
    public List<EmailMessage> fetchUnreadEmails() {

        try {

            // TODO:
            // Connect to Gmail API
            // Read unread emails
            // Convert Gmail messages into EmailMessage objects

            List<EmailMessage> emails = new ArrayList<>();

            return emails;

        } catch (Exception e) {

            throw new MailboxException(
                    "Failed to fetch emails from Gmail",
                    e
            );
        }
    }

    @Override
    public void markAsProcessed(String messageId) {

        try {

            // TODO:
            // Connect to Gmail API
            // Find email using messageId
            // Mark email as read/processed

        } catch (Exception e) {

            throw new MailboxException(
                    "Failed to mark Gmail email as processed: " + messageId,
                    e
            );
        }
    }
}