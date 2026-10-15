
package com.insurance.quotation.service.quotation;

import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.mailbox.provider.GmailMailboxClient;
import com.insurance.quotation.repository.QuotationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import java.time.LocalDateTime;

@Service
public class QuotationEmailService {

    private final GmailMailboxClient gmailMailboxClient;
    private final QuotationRepository quotationRepository;
    private final boolean demoMode;
    private final String formUrl;

    public QuotationEmailService(
            GmailMailboxClient gmailMailboxClient,
            QuotationRepository quotationRepository,
            @org.springframework.beans.factory.annotation.Value("${mailbox.mode:demo}") String mailboxMode,
            @org.springframework.beans.factory.annotation.Value("${app.public-form-url:http://localhost:4200/client-quotation}") String formUrl) {
        this.gmailMailboxClient = gmailMailboxClient;
        this.quotationRepository = quotationRepository;
        this.demoMode = !"gmail".equalsIgnoreCase(mailboxMode);
        this.formUrl = formUrl;
    }

    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        if (demoMode) {
            System.out.println("[DEMO MAIL] Missing-information email to "
                    + recipient + " missing=" + missingFields
                    + " invalid=" + invalidFields);
            return;
        }

        gmailMailboxClient.sendMissingInformationEmail(
                recipient,
                missingFields,
                invalidFields
        );
    }

    /** Reply to an email that was not a quotation request: point to the web form. */
    public void sendFormLinkEmail(String recipient) {

        if (demoMode) {
            System.out.println("[DEMO MAIL] Form link " + formUrl
                    + " would be sent to " + recipient);
            return;
        }

        gmailMailboxClient.sendFormLinkEmail(recipient, formUrl);
    }

    @Transactional
    public void sendQuotation(Quotation quotation) {

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

        String recipient = quotation.getQuotationRequest()
                .getClient()
                .getEmail();

        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException(
                    "Cannot send quotation: client email is missing.");
        }

        if (demoMode) {
            System.out.println("[DEMO MAIL] Quotation "
                    + quotation.getQuotationNumber() + " would be sent to "
                    + recipient + " with " + quotation.getPdfPath());
        } else {
            // Gmail API sends the PDF and throws an exception if sending fails.
            gmailMailboxClient.sendQuotationEmail(
                    recipient,
                    quotation.getQuotationNumber(),
                    quotation.getPdfPath()
            );
        }

        // Update the database only after Gmail accepts the send request.
        quotation.setStatus("SENT");
        quotation.setSentAt(LocalDateTime.now());

        quotationRepository.save(quotation);

        System.out.println(
                "Quotation email sent successfully to "
                        + recipient
                        + " for "
                        + quotation.getQuotationNumber());
    }
}
