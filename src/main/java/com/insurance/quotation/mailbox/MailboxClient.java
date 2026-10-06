package com.insurance.quotation.mailbox;

import java.util.List;

public interface MailboxClient {

    List<EmailMessage> fetchUnreadEmails();

    void markAsProcessed(String messageId);
}