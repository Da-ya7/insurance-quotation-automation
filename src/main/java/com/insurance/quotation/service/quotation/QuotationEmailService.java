
package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.mailbox.MailTemplates;
import com.insurance.quotation.mailbox.OutboundMailer;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.service.master.FormLinkService;

@Service
public class QuotationEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationEmailService.class);

    private final OutboundMailer mailer;
    private final QuotationRepository quotationRepository;
    private final FormLinkService formLinks;
    private final QuotationResponseService responseService;
    private final String publicBaseUrl;

    public QuotationEmailService(
            OutboundMailer mailer,
            QuotationRepository quotationRepository,
            FormLinkService formLinks,
            QuotationResponseService responseService,
            @Value("${app.public-base-url:http://localhost:4200}")
            String publicBaseUrl) {

        this.mailer = mailer;
        this.quotationRepository = quotationRepository;
        this.formLinks = formLinks;
        this.responseService = responseService;

        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /** Sends missing-information email with quotation request reference. */
    public String sendMissingInformationEmail(
            Client client,
            Long requestId,
            String insuranceType,
            List<String> missingFields,
            List<String> invalidFields) {

        if (client == null || client.getEmail() == null
                || client.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "Client email is required.");
        }

        String url = formLinks.publicUrl();

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(
                        missingFields,
                        invalidFields,
                        url,
                        requestId
                );

        mailer.sendPlain(
                client.getEmail(),
                mail.subject(),
                mail.body()
        );

        log.info(
                "Missing-information email sent to {} (request {})",
                client.getEmail(),
                requestId
        );

        return url;
    }

    /** Missing-information email for older web-form flows. */
    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        String url = formLinks.publicUrl();

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(
                        missingFields,
                        invalidFields,
                        url
                );

        mailer.sendPlain(
                recipient,
                mail.subject(),
                mail.body()
        );
    }

    /** Sends the general quotation form link with a unique request reference. */
    public String sendFormLinkEmail(
            String recipient,
            Client existingClientOrNull,
            String insuranceType,
            Long requestId) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required.");
        }

        String url = formLinks.publicUrl();

        MailTemplates.Mail mail =
                MailTemplates.formLink(
                        existingClientOrNull != null,
                        url,
                        requestId
                );

        mailer.sendPlain(
                recipient,
                mail.subject(),
                mail.body()
        );

        log.info(
                "Form-link email sent to {} (request {})",
                recipient,
                requestId
        );

        return url;
    }

    /**
     * Sends the quotation PDF together with Accept and Reject buttons.
     * Does nothing if this quotation version was already sent.
     */
    @Transactional
    public void sendQuotation(Quotation quotation) {

        if (quotation == null) {
            throw new IllegalArgumentException(
                    "Quotation is required.");
        }

        if ("SENT".equals(quotation.getStatus())) {
            log.info(
                    "Quotation {} was already sent - skipping",
                    quotation.getQuotationNumber());

            return;
        }

        if (quotation.getPdfPath() == null
                || quotation.getPdfPath().isBlank()) {

            throw new IllegalStateException(
                    "Cannot send quotation: PDF path is missing.");
        }

        if (quotation.getQuotationRequest() == null
                || quotation.getQuotationRequest().getClient() == null) {

            throw new IllegalStateException(
                    "Cannot send quotation: client details are missing.");
        }

        String recipient =
                quotation.getQuotationRequest()
                        .getClient()
                        .getEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException(
                    "Cannot send quotation: client email is missing.");
        }

        // Generate or reuse the secure response token.
        String token =
                responseService.ensureResponseToken(quotation);

        String acceptUrl =
                publicBaseUrl
                        + "/quotation-response/"
                        + token
                        + "?decision=ACCEPT";

        String rejectUrl =
                publicBaseUrl
                        + "/quotation-response/"
                        + token
                        + "?decision=REJECT";

        MailTemplates.Mail mail =
                MailTemplates.quotation(
                        quotation.getQuotationNumber(),
                        quotation.getVersionNo());

        String htmlBody = buildQuotationEmailHtml(
                quotation,
                acceptUrl,
                rejectUrl
        );

        // Send the quotation and capture Gmail conversation identifiers.
        OutboundMailer.SendResult sendResult =
                mailer.sendHtmlWithAttachmentAndThreadDetails(
                        recipient,
                        mail.subject(),
                        htmlBody,
                        quotation.getPdfPath());

        // Save the original Gmail thread and message IDs.
        if (sendResult != null) {
            quotation.setGmailThreadId(
                    sendResult.threadId());

            quotation.setGmailMessageId(
                    sendResult.messageId());
        }

        // Only after the provider accepted the message.
        quotation.setStatus("SENT");
        quotation.setSentAt(LocalDateTime.now());

        quotationRepository.save(quotation);

        log.info(
                "Quotation {} sent to {}. Gmail thread ID: {}",
                quotation.getQuotationNumber(),
                recipient,
                quotation.getGmailThreadId());
    }

    private String buildQuotationEmailHtml(
            Quotation quotation,
            String acceptUrl,
            String rejectUrl) {

        String quotationNumber =
                quotation.getQuotationNumber();

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f5f7;
                    font-family:Arial,sans-serif;color:#333333;">

                  <div style="max-width:600px;margin:30px auto;
                      background:#ffffff;padding:35px;
                      border-radius:8px;">

                    <h2 style="color:#1f2937;">
                      Your Insurance Quotation
                    </h2>

                    <p>Dear Customer,</p>

                    <p>
                      Please find your insurance quotation attached
                      to this email.
                    </p>

                    <p>
                      <strong>Quotation Number:</strong>
                      %s
                    </p>

                    <p>
                      Please review the attached quotation and select
                      your decision below.
                    </p>

                    <div style="text-align:center;margin:35px 0;">

                      <a href="%s"
                         style="display:inline-block;
                         background:#198754;
                         color:#ffffff;
                         text-decoration:none;
                         padding:14px 28px;
                         border-radius:6px;
                         font-weight:bold;
                         margin:8px;">
                        ACCEPT QUOTATION
                      </a>

                      <a href="%s"
                         style="display:inline-block;
                         background:#dc3545;
                         color:#ffffff;
                         text-decoration:none;
                         padding:14px 28px;
                         border-radius:6px;
                         font-weight:bold;
                         margin:8px;">
                        REJECT QUOTATION
                      </a>

                    </div>

                    <p style="font-size:13px;color:#666666;">
                      Clicking a button will open a confirmation page.
                      Your decision will only be recorded after you
                      confirm it on that page.
                    </p>

                    <p>
                      Regards,<br>
                      Insurance Quotation Team
                    </p>

                  </div>

                </body>
                </html>
                """.formatted(
                quotationNumber,
                acceptUrl,
                rejectUrl);
    }
}
