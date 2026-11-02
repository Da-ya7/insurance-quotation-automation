package com.insurance.quotation.mailbox.provider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.MailboxException;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.HeaderTerm;

/** Reads unread mail from any IMAP mailbox (Outlook, Zoho, cPanel, company server...). */
public class ImapMailboxClient implements MailboxClient {

    private static final int MAX_EMAILS_PER_CHECK = 20;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean ssl;

    public ImapMailboxClient(String host, int port, String username,
                             String password, boolean ssl) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.ssl = ssl;
    }

    @Override
    public List<EmailMessage> fetchUnreadEmails() {
        try (MailSession ms = open()) {
            Message[] unread = ms.folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            List<EmailMessage> emails = new ArrayList<>();

            int from = Math.max(0, unread.length - MAX_EMAILS_PER_CHECK);
            for (int i = from; i < unread.length; i++) {
                Message m = unread[i];
                String[] ids = m.getHeader("Message-ID");
                if (ids == null || ids.length == 0) {
                    continue;
                }
                String[] replyTo = m.getHeader("In-Reply-To");
                Address[] senders = m.getFrom();
                String sender = senders != null && senders.length > 0
                        && senders[0] instanceof InternetAddress ia
                        ? ia.getAddress() : "";

                Date date = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
                LocalDateTime received = date == null ? LocalDateTime.now()
                        : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());

                emails.add(new EmailMessage(
                        ids[0].trim(),
                        replyTo == null || replyTo.length == 0 ? null : replyTo[0].trim(),
                        sender == null ? "" : sender.toLowerCase(),
                        m.getSubject(),
                        textOf(m),
                        received));
            }
            return emails;
        } catch (Exception e) {
            throw new MailboxException("Failed to fetch emails from IMAP mailbox: " + e.getMessage(), e);
        }
    }

    @Override
    public void markAsProcessed(String messageId) {
        try (MailSession ms = open()) {
            Message[] found = ms.folder.search(new HeaderTerm("Message-ID", messageId));
            for (Message m : found) {
                m.setFlag(Flags.Flag.SEEN, true);
            }
        } catch (Exception e) {
            throw new MailboxException("Failed to mark email as processed: " + messageId, e);
        }
    }

    // ---------------------------------------------------------------

    private MailSession open() throws Exception {
        Properties props = new Properties();
        String protocol = ssl ? "imaps" : "imap";
        props.put("mail.store.protocol", protocol);
        props.put("mail." + protocol + ".host", host);
        props.put("mail." + protocol + ".port", String.valueOf(port));
        props.put("mail." + protocol + ".peek", "true");       // reading does not mark as read
        props.put("mail." + protocol + ".connectiontimeout", "15000");
        props.put("mail." + protocol + ".timeout", "30000");

        Store store = Session.getInstance(props).getStore(protocol);
        store.connect(host, port, username, password);
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);
        return new MailSession(store, folder);
    }

    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return String.valueOf(part.getContent());
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part p = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(p.getDisposition())) {
                    continue;
                }
                if (p.isMimeType("text/plain")) {
                    return String.valueOf(p.getContent());
                }
                if (p.isMimeType("text/html") && html == null) {
                    html = String.valueOf(p.getContent());
                }
                if (p.isMimeType("multipart/*")) {
                    String nested = textOf(p);
                    if (nested != null && !nested.isBlank()) {
                        return nested;
                    }
                }
            }
            return html == null ? "" : html;   // the extractor strips HTML tags
        }
        if (part.isMimeType("text/html")) {
            return String.valueOf(part.getContent());
        }
        return "";
    }

    private record MailSession(Store store, Folder folder) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
                store.close();
            } catch (Exception ignored) {
                // closing problems are not worth failing the run for
            }
        }
    }
}
