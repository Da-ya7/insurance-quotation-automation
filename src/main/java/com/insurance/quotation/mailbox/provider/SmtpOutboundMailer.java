package com.insurance.quotation.mailbox.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.insurance.quotation.mailbox.MailboxException;
import com.insurance.quotation.mailbox.OutboundMailer;

import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/** Sends mail through any SMTP server (used with the IMAP provider). */
public class SmtpOutboundMailer implements OutboundMailer {

    private final Session session;
    private final String username;
    private final String password;
    private final String from;

    public SmtpOutboundMailer(String host, int port, String username,
                              String password, String from, boolean startTls) {
        this.username = username;
        this.password = password;
        this.from = from != null && !from.isBlank() ? from : username;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(username != null && !username.isBlank()));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "30000");
        this.session = Session.getInstance(props);
    }

    @Override
    public void sendPlain(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    @Override
    public void sendWithAttachment(String to, String subject, String body, String pdfPath) {
        send(to, subject, body, pdfPath);
    }

    private void send(String to, String subject, String body, String pdfPath) {
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
            message.setSubject(subject, "UTF-8");

            MimeBodyPart text = new MimeBodyPart();
            text.setText(body, "UTF-8");

            MimeMultipart parts = new MimeMultipart("mixed");
            parts.addBodyPart(text);

            if (pdfPath != null) {
                Path file = Path.of(pdfPath);
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Quotation PDF not found: " + pdfPath);
                }
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setDataHandler(new DataHandler(new FileDataSource(file.toFile())));
                attachment.setFileName(file.getFileName().toString());
                parts.addBodyPart(attachment);
            }
            message.setContent(parts);

            Transport transport = session.getTransport("smtp");
            try {
                if (username != null && !username.isBlank()) {
                    transport.connect(username, password);
                } else {
                    transport.connect();
                }
                transport.sendMessage(message, message.getAllRecipients());
            } finally {
                transport.close();
            }
        } catch (Exception e) {
            throw new MailboxException("Failed to send email to " + to + ": " + e.getMessage(), e);
        }
    }
}
