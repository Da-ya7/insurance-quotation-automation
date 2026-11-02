package com.insurance.quotation.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.OutboundMailer;
import com.insurance.quotation.mailbox.provider.DemoMailboxClient;
import com.insurance.quotation.mailbox.provider.DemoOutboundMailer;
import com.insurance.quotation.mailbox.provider.GmailMailboxClient;
import com.insurance.quotation.mailbox.provider.ImapMailboxClient;
import com.insurance.quotation.mailbox.provider.SmtpOutboundMailer;
import com.insurance.quotation.service.ingestion.EmailIngestionService;

/**
 * Chooses the email provider from the system configuration.
 *
 *   mailbox.mode=auto   (default)
 *       gmail  if credentials/credentials.json exists
 *       imap   else if mail.imap.host is set
 *       demo   otherwise (nothing real is read or sent)
 *   mailbox.mode=gmail | imap | demo   forces one provider.
 */
@Configuration
public class AutomationConfig {

    private static final Logger log = LoggerFactory.getLogger(AutomationConfig.class);

    /** Resolved provider name: gmail, imap or demo. */
    @Bean
    public MailProvider mailProvider(
            @Value("${mailbox.mode:auto}") String mode,
            @Value("${mail.imap.host:}") String imapHost) {

        String chosen = mode == null ? "auto" : mode.trim().toLowerCase();

        if ("auto".equals(chosen)) {
            if (Files.isRegularFile(Path.of("credentials", "credentials.json"))) {
                chosen = "gmail";
            } else if (imapHost != null && !imapHost.isBlank()) {
                chosen = "imap";
            } else {
                chosen = "demo";
            }
            log.info("mailbox.mode=auto -> using the '{}' provider", chosen);
        } else {
            log.info("mailbox.mode={} (forced)", chosen);
        }
        return new MailProvider(chosen);
    }

    public record MailProvider(String name) {
        public boolean isDemo() {
            return "demo".equals(name);
        }
    }

    // One shared Gmail client, created only when Gmail is really used.
    private GmailMailboxClient gmailClient;

    private synchronized GmailMailboxClient gmail() {
        if (gmailClient == null) {
            gmailClient = new GmailMailboxClient();
        }
        return gmailClient;
    }

    // The Gmail client can both read and send. Each bean exposes only ONE role
    // so Spring never sees two candidates for the same type.
    private record MailboxOnly(MailboxClient delegate) implements MailboxClient {
        @Override
        public java.util.List<com.insurance.quotation.mailbox.EmailMessage> fetchUnreadEmails() {
            return delegate.fetchUnreadEmails();
        }

        @Override
        public void markAsProcessed(String messageId) {
            delegate.markAsProcessed(messageId);
        }
    }

    private record OutboundOnly(OutboundMailer delegate) implements OutboundMailer {
        @Override
        public void sendPlain(String to, String subject, String body) {
            delegate.sendPlain(to, subject, body);
        }

        @Override
        public void sendWithAttachment(String to, String subject, String body, String pdfPath) {
            delegate.sendWithAttachment(to, subject, body, pdfPath);
        }
    }

    @Bean
    public MailboxClient mailboxClient(
            MailProvider provider,
            @Value("${mail.imap.host:}") String host,
            @Value("${mail.imap.port:993}") int port,
            @Value("${mail.imap.username:}") String username,
            @Value("${mail.imap.password:}") String password,
            @Value("${mail.imap.ssl:true}") boolean ssl) {

        return switch (provider.name()) {
            case "gmail" -> new MailboxOnly(gmail());
            case "imap" -> new ImapMailboxClient(host, port, username, password, ssl);
            default -> new DemoMailboxClient();
        };
    }

    @Bean
    public OutboundMailer outboundMailer(
            MailProvider provider,
            @Value("${mail.smtp.host:}") String host,
            @Value("${mail.smtp.port:587}") int port,
            @Value("${mail.smtp.username:${mail.imap.username:}}") String username,
            @Value("${mail.smtp.password:${mail.imap.password:}}") String password,
            @Value("${mail.from:}") String from,
            @Value("${mail.smtp.starttls:true}") boolean startTls) {

        return switch (provider.name()) {
            case "gmail" -> new OutboundOnly(gmail());
            case "imap" -> new SmtpOutboundMailer(host, port, username, password, from, startTls);
            default -> new DemoOutboundMailer();
        };
    }

    @Bean
    public EmailIngestionService emailIngestionService(MailboxClient mailboxClient) {
        return new EmailIngestionService(mailboxClient);
    }
}