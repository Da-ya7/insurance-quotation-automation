
package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
import com.insurance.quotation.service.master.FormLinkService.FormLink;

@Service
public class QuotationEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationEmailService.class);

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

        String normalizedType = normalizeInsuranceType(insuranceType);
        String url;

        if (normalizedType != null) {

            FormLink link = formLinks.create(
                    client.getId(),
                    requestId,
                    normalizedType);

            url = formLinks.url(link);

        } else {
            url = formLinks.publicUrl();
        }

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(
                        missingFields,
                        invalidFields,
                        url,
                        requestId);

        mailer.sendPlain(
                client.getEmail(),
                mail.subject(),
                mail.body());

        log.info(
                "Missing-information email sent to {} "
                        + "(request {}, insurance type {})",
                client.getEmail(),
                requestId,
                normalizedType);

        return url;
    }

    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        String url = formLinks.publicUrl();

        MailTemplates.Mail mail =
                MailTemplates.missingInformation(
                        missingFields,
                        invalidFields,
                        url);

        mailer.sendPlain(
                recipient,
                mail.subject(),
                mail.body());
    }

    /**
     * Existing customer:
     * Secure token URL -> OTP verification -> correct form.
     *
     * New customer:
     * Direct insurance-type URL -> phone verification -> form.
     *
     * Unknown insurance type:
     * General insurance selection URL.
     */
    public String sendFormLinkEmail(
            String recipient,
            Client existingClientOrNull,
            String insuranceType,
            Long requestId) {

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient email is required.");
        }

        String normalizedType = normalizeInsuranceType(insuranceType);
        String url;

        if (normalizedType != null && existingClientOrNull != null) {

            FormLink link = formLinks.create(
                    existingClientOrNull.getId(),
                    requestId,
                    normalizedType);

            url = formLinks.url(link);

        } else if (normalizedType != null) {

            url = formLinks.urlForInsuranceType(normalizedType);

        } else {
            url = formLinks.publicUrl();
        }

        MailTemplates.Mail mail =
                MailTemplates.formLink(
                        existingClientOrNull != null,
                        url,
                        requestId);

        mailer.sendPlain(
                recipient,
                mail.subject(),
                mail.body());

        log.info(
                "Form-link email sent to {} (request {}, insurance type {})",
                recipient,
                requestId,
                normalizedType);

        return url;
    }

    private String normalizeInsuranceType(String insuranceType) {

        if (insuranceType == null || insuranceType.isBlank()) {
            return null;
        }

        String normalized =
                insuranceType.trim().toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z0-9_-]+")) {
            return null;
        }

        return normalized;
    }

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

        MailTemplates.Mail mail =
                MailTemplates.quotation(
                        quotation.getQuotationNumber(),
                        quotation.getVersionNo());

        mailer.sendWithAttachment(
                recipient,
                mail.subject(),
                mail.body(),
                quotation.getPdfPath());

        quotation.setStatus("SENT");
        quotation.setSentAt(LocalDateTime.now());

        quotationRepository.save(quotation);

        log.info(
                "Quotation {} sent to {}",
                quotation.getQuotationNumber(),
                recipient);
    }
}
