package com.insurance.quotation.service.quotation;

import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.mailbox.provider.GmailMailboxClient;
import com.insurance.quotation.repository.QuotationRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class QuotationEmailService {

    private final GmailMailboxClient gmailMailboxClient;
    private final QuotationRepository quotationRepository;

    private final boolean demoMode;
    private final String formUrl;

    public QuotationEmailService(
            GmailMailboxClient gmailMailboxClient,
            QuotationRepository quotationRepository,
            @Value("${mailbox.mode:demo}") String mailboxMode,
            @Value("${app.public-form-url:http://localhost:4200/client-quotation}")
            String formUrl) {

        this.gmailMailboxClient = gmailMailboxClient;
        this.quotationRepository = quotationRepository;

        this.demoMode = !"gmail".equalsIgnoreCase(mailboxMode);
        this.formUrl = formUrl;
    }

    // ---------------------------------------------------------
    // SEND MISSING INFORMATION EMAIL
    // ---------------------------------------------------------

    public void sendMissingInformationEmail(
            String recipient,
            List<String> missingFields,
            List<String> invalidFields) {

        validateRecipient(recipient);

        List<String> actualMissingFields = cleanFieldNames(missingFields);
        List<String> actualInvalidFields = cleanFieldNames(invalidFields);

        if (actualMissingFields.isEmpty()
                && actualInvalidFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot send a missing-information email because no missing or invalid fields were provided.");
        }

        if (demoMode) {
            System.out.println(
                    "[DEMO MAIL] Missing-information email to "
                            + recipient
                            + ", missing=" + actualMissingFields
                            + ", invalid=" + actualInvalidFields);
            return;
        }

        gmailMailboxClient.sendMissingInformationEmail(
                recipient,
                actualMissingFields,
                actualInvalidFields);

        System.out.println(
                "Missing-information email accepted by Gmail for "
                        + recipient);
    }

    // ---------------------------------------------------------
    // SEND QUOTATION FORM LINK EMAIL
    // ---------------------------------------------------------

    public void sendFormLinkEmail(String recipient) {

        validateRecipient(recipient);
        validateFormUrl();

        if (demoMode) {
            System.out.println(
                    "[DEMO MAIL] Quotation form link email to "
                            + recipient
                            + "\nMessage:\n"
                            + buildFormLinkMessage());
            return;
        }

        gmailMailboxClient.sendFormLinkEmail(
                recipient,
                formUrl);

        System.out.println(
                "Quotation form link email accepted by Gmail for "
                        + recipient);
    }

    // ---------------------------------------------------------
    // SEND QUOTATION EMAIL WITH PDF ATTACHMENT
    // ---------------------------------------------------------

    @Transactional
    public void sendQuotation(Quotation quotation) {

        if (quotation == null) {
            throw new IllegalArgumentException(
                    "Cannot send quotation: quotation is missing.");
        }

        if (quotation.getQuotationNumber() == null
                || quotation.getQuotationNumber().isBlank()) {
            throw new IllegalStateException(
                    "Cannot send quotation: quotation number is missing.");
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

        String recipient = quotation.getQuotationRequest()
                .getClient()
                .getEmail();

        validateRecipient(recipient);

        Path pdfPath = Path.of(quotation.getPdfPath());

        if (!Files.isRegularFile(pdfPath)
                || !Files.isReadable(pdfPath)) {
            throw new IllegalStateException(
                    "Cannot send quotation: PDF file does not exist or cannot be read: "
                            + quotation.getPdfPath());
        }

        if (demoMode) {
            System.out.println(
                    "[DEMO MAIL] Quotation "
                            + quotation.getQuotationNumber()
                            + " would be sent to "
                            + recipient
                            + " with PDF "
                            + quotation.getPdfPath());
            return;
        }

        // The Gmail send method must complete successfully before
        // the quotation is marked as sent.
        gmailMailboxClient.sendQuotationEmail(
                recipient,
                quotation.getQuotationNumber(),
                quotation.getPdfPath());

        quotation.setStatus("SENT");
        quotation.setSentAt(LocalDateTime.now());

        quotationRepository.save(quotation);

        System.out.println(
                "Quotation email accepted by Gmail for "
                        + recipient
                        + ", quotation "
                        + quotation.getQuotationNumber());
    }

    // ---------------------------------------------------------
    // CLEAN FIELD NAMES
    // ---------------------------------------------------------

    private List<String> cleanFieldNames(List<String> fields) {

        if (fields == null || fields.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> uniqueFields = new LinkedHashSet<>();

        for (String field : fields) {
            if (field != null && !field.isBlank()) {
                uniqueFields.add(field.trim());
            }
        }

        return new ArrayList<>(uniqueFields);
    }

    // ---------------------------------------------------------
    // VALIDATE FORM URL
    // ---------------------------------------------------------

    private void validateFormUrl() {

        if (formUrl == null
                || !formUrl.matches("^https?://[^\\s]+$")) {
            throw new IllegalStateException(
                    "Configure a valid quotation form URL.");
        }
    }

    // ---------------------------------------------------------
    // FORM LINK EMAIL MESSAGE
    // ---------------------------------------------------------

    private String buildFormLinkMessage() {

        return "Dear Client,\n\n"
                + "Thank you for contacting us. To prepare your travel insurance "
                + "quotation, we need a few details about you and your trip.\n\n"
                + "Please fill in our quotation form:\n"
                + formUrl
                + "\n\n"
                + "Your quotation will be emailed to you as soon as the form is submitted.\n\n"
                + "Regards,\n"
                + "Insurance Quotation Team";
    }

    // ---------------------------------------------------------
    // VALIDATE RECIPIENT EMAIL ADDRESS
    // ---------------------------------------------------------

    private void validateRecipient(String recipient) {

        if (recipient == null
                || recipient.isBlank()
                || !recipient.matches(
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException(
                    "A valid recipient email address is required.");
        }
    }
}
