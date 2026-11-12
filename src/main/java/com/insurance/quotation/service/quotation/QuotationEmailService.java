
package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.mailbox.MailTemplates;
import com.insurance.quotation.mailbox.OutboundMailer;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.service.master.FormLinkService;

/** Every email the system sends to customers goes through here. */
@Service
public class QuotationEmailService {

    private static final Logger log = LoggerFactory.getLogger(QuotationEmailService.class);

    private final OutboundMailer mailer;
    private final QuotationRepository quotationRepository;
    private final FormLinkService formLinks;

    public QuotationEmailService(
            OutboundMailer mailer,
            QuotationRepository quotationRepository,
            FormLinkService formLinks) {
        this.mailer = mailer;
        this.quotationRepository = quotationRepository;
        this.formLinks = formLinks;
    }

    /** Missing-details email with a personal form link. */
    public String sendMissingInformationEmail(
            Client client, Long requestId, String insuranceType,
            List<String> missingFields, List<String> invalidFields) {

        FormLinkService.FormLink link =
                formLinks.create(client.getId(), requestId, insuranceType);

        String url = formLinks.url(link);

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(missingFields, invalidFields, url);

        mailer.sendPlain(client.getEmail(), mail.subject(), mail.body());

        log.info("Missing-information email sent to {} (request {})",
                client.getEmail(), requestId);

        return url;
    }

    /** Missing-information email without a personal form link. */
    public void sendMissingInformationEmail(
            String recipient, List<String> missingFields, List<String> invalidFields) {

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(missingFields, invalidFields, null);

        mailer.sendPlain(recipient, mail.subject(), mail.body());
    }

    /** Sends a form link to a customer who has not provided enough details. */
    public String sendFormLinkEmail(
            String recipient, Client existingClientOrNull,
            String insuranceType, Long requestId) {

        FormLinkService.FormLink link = formLinks.create(
                existingClientOrNull == null ? null : existingClientOrNull.getId(),
                requestId,
                insuranceType
        );

        String url = formLinks.url(link);

        MailTemplates.Mail mail =
                MailTemplates.formLink(existingClientOrNull != null, url);

        mailer.sendPlain(recipient, mail.subject(), mail.body());

        log.info("Form-link email sent to {}", recipient);

        return url;
    }

    /** Sends an acknowledgement to a customer asking policy-related questions. */
    public void sendPolicyQuestionEmail(String recipient) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "Cannot send policy enquiry email: recipient is missing."
            );
        }

        MailTemplates.Mail mail = MailTemplates.policyQuestion();

        mailer.sendPlain(recipient, mail.subject(), mail.body());

        log.info("Policy-enquiry acknowledgement sent to {}", recipient);
    }

    /** Sends the quotation PDF. Does nothing if this version was already sent. */
    @Transactional
    public void sendQuotation(Quotation quotation) {

        if ("SENT".equals(quotation.getStatus())) {
            log.info("Quotation {} was already sent - skipping",
                    quotation.getQuotationNumber());
            return;
        }

        if (quotation.getPdfPath() == null || quotation.getPdfPath().isBlank()) {
            throw new IllegalStateException(
                    "Cannot send quotation: PDF path is missing."
            );
        }

        if (quotation.getQuotationRequest() == null
                || quotation.getQuotationRequest().getClient() == null) {
            throw new IllegalStateException(
                    "Cannot send quotation: client details are missing."
            );
        }

        String recipient =
                quotation.getQuotationRequest().getClient().getEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException(
                    "Cannot send quotation: client email is missing."
            );
        }

        MailTemplates.Mail mail = MailTemplates.quotation(
                quotation.getQuotationNumber(),
                quotation.getVersionNo()
        );

        mailer.sendWithAttachment(
                recipient,
                mail.subject(),
                mail.body(),
                quotation.getPdfPath()
        );

        quotation.setStatus("SENT");
        quotation.setSentAt(LocalDateTime.now());

        quotationRepository.save(quotation);

        log.info("Quotation {} sent to {}",
                quotation.getQuotationNumber(), recipient);
    }
}
