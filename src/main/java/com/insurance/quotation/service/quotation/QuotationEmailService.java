
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

    public QuotationEmailService(
            GmailMailboxClient gmailMailboxClient,
            QuotationRepository quotationRepository) {
        this.gmailMailboxClient = gmailMailboxClient;
        this.quotationRepository = quotationRepository;
    }

    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        gmailMailboxClient.sendMissingInformationEmail(
                recipient,
                missingFields,
                invalidFields
        );
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

        // Gmail API sends the PDF and throws an exception if sending fails.
        gmailMailboxClient.sendQuotationEmail(
                recipient,
                quotation.getQuotationNumber(),
                quotation.getPdfPath()
        );

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
