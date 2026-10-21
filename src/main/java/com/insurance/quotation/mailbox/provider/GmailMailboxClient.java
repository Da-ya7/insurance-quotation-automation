
package com.insurance.quotation.mailbox.provider;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.ModifyMessageRequest;
import com.insurance.quotation.mailbox.EmailMessage;
import com.insurance.quotation.mailbox.MailboxClient;
import com.insurance.quotation.mailbox.MailboxException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GmailMailboxClient implements MailboxClient {

    private static final GsonFactory JSON_FACTORY =
            GsonFactory.getDefaultInstance();

    private static final String APPLICATION_NAME =
            "Insurance Quotation Automation";


    private static final String GMAIL_QUERY =
        "in:inbox is:unread -in:spam -in:trash";

    private static final int MAX_EMAILS_PER_CHECK = 20;

    private static final Path CREDENTIALS_PATH =
            Path.of("credentials", "credentials.json");

    private static final Path TOKENS_DIRECTORY =
            Path.of("tokens");

    private Gmail gmailService;

    @Override
    public List<EmailMessage> fetchUnreadEmails() {
        try {
            Gmail gmail = getGmailService();

            ListMessagesResponse response = gmail.users()
                    .messages()
                    .list("me")
                    .setQ(GMAIL_QUERY)
                    .setMaxResults((long) MAX_EMAILS_PER_CHECK)
                    .execute();

            List<Message> messages = response.getMessages();
            List<EmailMessage> emails = new ArrayList<>();

            if (messages == null || messages.isEmpty()) {
                System.out.println(
                        "Gmail: no matching unread emails found.");
                return emails;
            }

            for (Message reference : messages) {
                Message message = gmail.users()
                        .messages()
                        .get("me", reference.getId())
                        .setFormat("full")
                        .execute();

                MessagePart payload = message.getPayload();

                if (payload == null) {
                    continue;
                }

                String sender = getHeader(payload, "From");
                String subject = getHeader(payload, "Subject");
                String inReplyTo = getHeader(payload, "In-Reply-To");

                String body = extractPlainText(payload);

                if (body.isBlank()) {
                    body = extractHtmlText(payload);
                }

                LocalDateTime receivedAt =
                        message.getInternalDate() == null
                                ? LocalDateTime.now()
                                : Instant.ofEpochMilli(
                                        message.getInternalDate())
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime();

                emails.add(new EmailMessage(
                        message.getId(),
                        inReplyTo,
                        extractEmailAddress(sender),
                        subject,
                        body,
                        receivedAt
                ));
            }

            System.out.println(
                    "Gmail: fetched " + emails.size()
                            + " matching unread email(s).");

            return emails;

        } catch (Exception e) {
            throw new MailboxException(
                    "Failed to fetch emails from Gmail.", e);
        }
    }

    @Override
    public void markAsProcessed(String messageId) {
        try {
            if (messageId == null || messageId.isBlank()) {
                throw new IllegalArgumentException(
                        "Gmail message ID cannot be empty.");
            }

            getGmailService().users()
                    .messages()
                    .modify(
                            "me",
                            messageId,
                            new ModifyMessageRequest()
                                    .setRemoveLabelIds(
                                            Collections.singletonList("UNREAD"))
                    )
                    .execute();

            System.out.println(
                    "Gmail: marked message as read: " + messageId);

        } catch (Exception e) {
            throw new MailboxException(
                    "Failed to mark Gmail email as processed: "
                            + messageId, e);
        }
    }

    /**
     * Sends a quotation PDF to the client.
     * Returns only after Gmail accepts the send request.
     */
    public void sendQuotationEmail(
            String recipient,
            String quotationNumber,
            String pdfPath) {

        try {
            if (recipient == null
                    || !recipient.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException(
                        "Invalid recipient email address.");
            }

            if (quotationNumber == null
                    || quotationNumber.isBlank()
                    || quotationNumber.contains("\r")
                    || quotationNumber.contains("\n")) {
                throw new IllegalArgumentException(
                        "Invalid quotation number.");
            }

            Path file = Path.of(pdfPath);

            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException(
                        "Quotation PDF not found: " + pdfPath);
            }

            byte[] pdfBytes = Files.readAllBytes(file);

            String boundary =
                    "QuotationBoundary" + System.currentTimeMillis();

            String subject =
                    "Your Insurance Quotation - " + quotationNumber;

            String encodedSubject = "=?UTF-8?B?"
                    + Base64.getEncoder().encodeToString(
                            subject.getBytes(StandardCharsets.UTF_8))
                    + "?=";

            String fileName = file.getFileName()
                    .toString()
                    .replace("\"", "_")
                    .replace("\r", "_")
                    .replace("\n", "_");

            String attachment = Base64.getMimeEncoder(
                    76,
                    "\r\n".getBytes(StandardCharsets.US_ASCII)
            ).encodeToString(pdfBytes);

            String body =
                    "Dear Client,\r\n\r\n"
                    + "Thank you for your interest in our insurance services.\r\n"
                    + "Please find your quotation attached to this email.\r\n\r\n"
                    + "Quotation Number: " + quotationNumber + "\r\n\r\n"
                    + "Please review the attached quotation. "
                    + "If you have any questions, reply to this email.\r\n\r\n"
                    + "Regards,\r\n"
                    + "Insurance Quotation Team";

            String mime =
                    "To: " + recipient + "\r\n"
                    + "Subject: " + encodedSubject + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: multipart/mixed; boundary=\""
                    + boundary + "\"\r\n\r\n"

                    + "--" + boundary + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                    + body + "\r\n\r\n"

                    + "--" + boundary + "\r\n"
                    + "Content-Type: application/pdf; name=\""
                    + fileName + "\"\r\n"
                    + "Content-Disposition: attachment; filename=\""
                    + fileName + "\"\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n"
                    + attachment + "\r\n"
                    + "--" + boundary + "--";

            String raw = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            mime.getBytes(StandardCharsets.UTF_8));

            Message outgoing = new Message();
            outgoing.setRaw(raw);

            Message sent = getGmailService()
                    .users()
                    .messages()
                    .send("me", outgoing)
                    .execute();

            System.out.println(
                    "Gmail accepted quotation email. Message ID: "
                            + sent.getId());

        } catch (Exception e) {
            throw new MailboxException(
                    "Failed to send quotation "
                            + quotationNumber + " to " + recipient, e);
        }
    }

    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        try {
            if (recipient == null
                    || !recipient.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException(
                        "Invalid recipient email address.");
            }

            String subject =
                    "Additional Information Required - Insurance Quotation";

            StringBuilder body = new StringBuilder();

            body.append("Dear Client,\r\n\r\n");
            body.append("Thank you for your insurance quotation request.\r\n");
            body.append("We need a few more details before we can prepare ");
            body.append("your quotation.\r\n\r\n");

            if (missingFields != null && !missingFields.isEmpty()) {
                body.append("Missing information:\r\n");

                for (String field : missingFields) {
                    body.append("- ")
                            .append(field)
                            .append("\r\n");
                }

                body.append("\r\n");
            }

            if (invalidFields != null && !invalidFields.isEmpty()) {
                body.append("Please correct the following information:\r\n");

                for (String field : invalidFields) {
                    body.append("- ")
                            .append(field)
                            .append("\r\n");
                }

                body.append("\r\n");
            }

            body.append("Please reply to this email with the requested ");
            body.append("information. Use the format Field Name: Value ");
            body.append("where possible.\r\n\r\n");
            body.append("Regards,\r\n");
            body.append("Insurance Quotation Team");

            String encodedSubject = "=?UTF-8?B?"
                    + Base64.getEncoder().encodeToString(
                            subject.getBytes(StandardCharsets.UTF_8))
                    + "?=";

            String mime =
                    "To: " + recipient + "\r\n"
                    + "Subject: " + encodedSubject + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                    + body;

            String raw = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            mime.getBytes(StandardCharsets.UTF_8));

            Message outgoing = new Message();
            outgoing.setRaw(raw);

            Message sent = getGmailService()
                    .users()
                    .messages()
                    .send("me", outgoing)
                    .execute();

            System.out.println(
                    "Missing-information email accepted by Gmail. Message ID: "
                            + sent.getId());

        } catch (Exception e) {
            throw new MailboxException(
                    "Failed to send missing-information email to "
                            + recipient, e);
        }
    }

    public void sendFormLinkEmail(String recipient, String formUrl) {

        try {
            if (recipient == null
                    || !recipient.matches(
                    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                throw new IllegalArgumentException(
                        "Invalid recipient email address.");
            }

            String subject = "How to request your insurance quotation";

            String body = "Dear Client,\r\n\r\n"
                    + "Thank you for contacting us. To prepare your travel "
                    + "insurance quotation we need a few details about you "
                    + "and your trip.\r\n\r\n"
                    + "Please fill in our quotation form:\r\n"
                    + formUrl + "\r\n\r\n"
                    + "Your quotation will be emailed to you as soon as the "
                    + "form is submitted.\r\n\r\n"
                    + "Regards,\r\n"
                    + "Insurance Quotation Team";

            String encodedSubject = "=?UTF-8?B?"
                    + Base64.getEncoder().encodeToString(
                            subject.getBytes(StandardCharsets.UTF_8))
                    + "?=";

            String mime =
                    "To: " + recipient + "\r\n"
                    + "Subject: " + encodedSubject + "\r\n"
                    + "MIME-Version: 1.0\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: 8bit\r\n\r\n"
                    + body;

            String raw = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            mime.getBytes(StandardCharsets.UTF_8));

            Message outgoing = new Message();
            outgoing.setRaw(raw);

            Message sent = getGmailService()
                    .users()
                    .messages()
                    .send("me", outgoing)
                    .execute();

            System.out.println(
                    "Form-link email accepted by Gmail. Message ID: "
                            + sent.getId());

        } catch (Exception e) {
            throw new MailboxException(
                    "Failed to send form-link email to " + recipient, e);
        }
    }

    private synchronized Gmail getGmailService() throws Exception {
        if (gmailService != null) {
            return gmailService;
        }

        if (!Files.exists(CREDENTIALS_PATH)) {
            throw new IllegalStateException(
                    "OAuth credentials not found at "
                            + CREDENTIALS_PATH.toAbsolutePath());
        }

        Files.createDirectories(TOKENS_DIRECTORY);

        NetHttpTransport transport =
                GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets;

        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(CREDENTIALS_PATH),
                StandardCharsets.UTF_8)) {

            clientSecrets = GoogleClientSecrets.load(
                    JSON_FACTORY, reader);
        }

        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        transport,
                        JSON_FACTORY,
                        clientSecrets,
                        Arrays.asList(
                                GmailScopes.GMAIL_MODIFY,
                                GmailScopes.GMAIL_SEND
                        )
                )
                .setDataStoreFactory(
                        new FileDataStoreFactory(
                                TOKENS_DIRECTORY.toFile()))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver =
                new LocalServerReceiver.Builder()
                        .setPort(8888)
                        .build();

        Credential credential =
                new AuthorizationCodeInstalledApp(flow, receiver)
                        .authorize("user");

        gmailService = new Gmail.Builder(
                transport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        System.out.println(
                "Gmail OAuth authentication successful.");

        return gmailService;
    }

    private String getHeader(
            MessagePart payload, String headerName) {

        if (payload.getHeaders() == null) {
            return "";
        }

        return payload.getHeaders()
                .stream()
                .filter(h -> headerName.equalsIgnoreCase(h.getName()))
                .map(MessagePartHeader::getValue)
                .findFirst()
                .orElse("");
    }

    private String extractEmailAddress(String fromHeader) {
        if (fromHeader == null || fromHeader.isBlank()) {
            return "";
        }

        Matcher matcher = Pattern.compile("<([^<>]+)>")
                .matcher(fromHeader);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return fromHeader.trim();
    }

    private String extractPlainText(MessagePart part) {
        if (part == null) {
            return "";
        }

        if ("text/plain".equalsIgnoreCase(part.getMimeType())) {
            String text = decodePart(part);

            if (!text.isBlank()) {
                return text;
            }
        }

        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String text = extractPlainText(child);

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String extractHtmlText(MessagePart part) {
        if (part == null) {
            return "";
        }

        if ("text/html".equalsIgnoreCase(part.getMimeType())) {
            return decodePart(part)
                    .replaceAll("(?i)<br\\s*/?>", "\n")
                    .replaceAll("(?i)</p\\s*>", "\n")
                    .replaceAll("<[^>]*>", " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        }

        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String text = extractHtmlText(child);

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String decodePart(MessagePart part) {
        if (part.getBody() == null
                || part.getBody().getData() == null) {
            return "";
        }

        byte[] decoded = Base64.getUrlDecoder()
                .decode(part.getBody().getData());

        return new String(decoded, StandardCharsets.UTF_8);
    }
}
