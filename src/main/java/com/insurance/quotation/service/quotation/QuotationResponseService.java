
package com.insurance.quotation.service.quotation;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.mailbox.OutboundMailer;

@Service
public class QuotationResponseService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationResponseService.class);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static final String ORIGINAL_EMAIL_SUBJECT_PREFIX =
            "Your Insurance Quotation - ";

    private final QuotationRepository quotationRepository;
    private final OutboundMailer outboundMailer;

    public QuotationResponseService(
            QuotationRepository quotationRepository,
            OutboundMailer outboundMailer) {

        this.quotationRepository = quotationRepository;
        this.outboundMailer = outboundMailer;
    }

    // ==========================================
    // RESPONSE RESULT
    // ==========================================

    public enum ResponseResult {
        ACCEPTED,
        REJECTED,
        ALREADY_RESPONDED,
        CLOSED,
        INVALID_TOKEN,
        INVALID_RESPONSE
    }

    // ==========================================
    // GENERATE SECURE RESPONSE TOKEN
    // ==========================================

    /**
     * Generates a secure token for a quotation.
     *
     * The token is included in the Accept and Reject links.
     *
     * If a token already exists, the existing token is reused.
     */
    @Transactional
    public String ensureResponseToken(Quotation quotation) {

        if (quotation == null) {
            throw new IllegalArgumentException(
                    "Quotation cannot be null");
        }

        if (quotation.getResponseToken() != null
                && !quotation.getResponseToken().isBlank()) {

            return quotation.getResponseToken();
        }

        byte[] randomBytes = new byte[32];

        SECURE_RANDOM.nextBytes(randomBytes);

        String token = HexFormat.of().formatHex(randomBytes);

        quotation.setResponseToken(token);

        quotationRepository.save(quotation);

        log.info(
                "Response token generated for quotation {}",
                quotation.getQuotationNumber()
        );

        return token;
    }

    // ==========================================
    // PROCESS CLIENT RESPONSE
    // ==========================================

    @Transactional
    public ResponseResult respond(
            String token,
            String decision) {

        // --------------------------------------
        // VALIDATE TOKEN
        // --------------------------------------

        if (token == null || token.isBlank()) {

            return ResponseResult.INVALID_TOKEN;
        }

        // --------------------------------------
        // VALIDATE DECISION
        // --------------------------------------

        if (decision == null || decision.isBlank()) {

            return ResponseResult.INVALID_RESPONSE;
        }

        String normalizedDecision =
                decision.trim().toUpperCase();

        if (!normalizedDecision.equals("ACCEPT")
                && !normalizedDecision.equals("REJECT")) {

            return ResponseResult.INVALID_RESPONSE;
        }

        // --------------------------------------
        // FETCH QUOTATION WITH DATABASE LOCK
        // --------------------------------------

        Quotation quotation =
                quotationRepository
                        .findByResponseTokenForUpdate(token)
                        .orElse(null);

        if (quotation == null) {

            log.warn("Invalid quotation response token received");

            return ResponseResult.INVALID_TOKEN;
        }

        // --------------------------------------
        // CHECK EXISTING RESPONSE
        // --------------------------------------

        if (!"PENDING".equalsIgnoreCase(
                quotation.getResponseStatus())) {

            log.info(
                    "Quotation {} already has final response status {}",
                    quotation.getQuotationNumber(),
                    quotation.getResponseStatus()
            );

            return ResponseResult.ALREADY_RESPONDED;
        }

        // --------------------------------------
        // CHECK IF QUOTATION IS CLOSED
        // --------------------------------------

        if (quotation.getClosedAt() != null) {

            log.info(
                    "Quotation {} is already closed",
                    quotation.getQuotationNumber()
            );

            return ResponseResult.CLOSED;
        }

        LocalDateTime now = LocalDateTime.now();

        quotation.setResponseAt(now);

        // ======================================
        // ACCEPT
        // ======================================

        if (normalizedDecision.equals("ACCEPT")) {

            quotation.setResponseStatus("ACCEPTED");

            quotationRepository.save(quotation);

            log.info(
                    "Quotation {} permanently accepted by client",
                    quotation.getQuotationNumber()
            );

            sendAcceptanceConfirmation(quotation);

            return ResponseResult.ACCEPTED;
        }

        // ======================================
        // REJECT
        // ======================================

        quotation.setResponseStatus("REJECTED");

        quotation.setClosedAt(now);

        quotation.setClosureReason("REJECTED");

        quotationRepository.save(quotation);

        log.info(
                "Quotation {} permanently rejected and closed",
                quotation.getQuotationNumber()
        );

        sendRejectionConfirmation(quotation);

        return ResponseResult.REJECTED;
    }

    // ==========================================
    // ACCEPTANCE CONFIRMATION EMAIL
    // ==========================================

    private void sendAcceptanceConfirmation(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send acceptance confirmation for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return;
        }

        String subject =
                ORIGINAL_EMAIL_SUBJECT_PREFIX
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "Thank you for accepting our quotation.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n"
                        + "Amount: "
                        + quotation.getCurrency()
                        + " "
                        + quotation.getTotalAmount()
                        + "\n\n"
                        + "Your acceptance has been recorded successfully. "
                        + "Our team will process your request manually "
                        + "and contact you regarding the next steps.\n\n"
                        + "Thank you for choosing us.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        sendThreadedConfirmation(
                quotation,
                email,
                subject,
                body,
                "acceptance"
        );
    }

    // ==========================================
    // REJECTION CONFIRMATION EMAIL
    // ==========================================

    private void sendRejectionConfirmation(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send rejection confirmation for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return;
        }

        String subject =
                ORIGINAL_EMAIL_SUBJECT_PREFIX
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "We have received your decision to reject "
                        + "the quotation.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n\n"
                        + "Your quotation has been closed successfully.\n\n"
                        + "If you would like a new quotation in the future, "
                        + "please feel free to contact us.\n\n"
                        + "Thank you for your time.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        sendThreadedConfirmation(
                quotation,
                email,
                subject,
                body,
                "rejection"
        );
    }

    // ==========================================
    // SEND CONFIRMATION IN ORIGINAL EMAIL THREAD
    // ==========================================

    private void sendThreadedConfirmation(
            Quotation quotation,
            String email,
            String subject,
            String body,
            String responseType) {

        String threadId = quotation.getGmailThreadId();

        String originalMessageId =
                quotation.getGmailMessageId();

        // --------------------------------------
        // THREAD INFORMATION MUST BE AVAILABLE
        // --------------------------------------

        if (threadId == null || threadId.isBlank()
                || originalMessageId == null
                || originalMessageId.isBlank()) {

            log.error(
                    "Cannot send {} confirmation for quotation {} "
                            + "inside original email thread. "
                            + "Gmail thread ID or original message ID is missing. "
                            + "The client's decision has already been recorded.",
                    responseType,
                    quotation.getQuotationNumber()
            );

            return;
        }

        // --------------------------------------
        // SEND REPLY IN ORIGINAL THREAD
        // --------------------------------------

        try {

            outboundMailer.sendReplyInThread(
                    email,
                    subject,
                    body,
                    threadId,
                    originalMessageId
            );

            log.info(
                    "{} confirmation sent in original Gmail thread "
                            + "for quotation {}",
                    responseType,
                    quotation.getQuotationNumber()
            );

        } catch (RuntimeException e) {

            log.error(
                    "Failed to send {} confirmation in original Gmail thread "
                            + "for quotation {}. "
                            + "The client's decision remains recorded.",
                    responseType,
                    quotation.getQuotationNumber(),
                    e
            );
        }
    }

    // ==========================================
    // GET CLIENT EMAIL
    // ==========================================

    private String getClientEmail(
            Quotation quotation) {

        if (quotation == null
                || quotation.getQuotationRequest() == null) {

            return null;
        }

        Client client =
                quotation.getQuotationRequest().getClient();

        if (client == null) {
            return null;
        }

        return client.getEmail();
    }
}
