
package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.mailbox.OutboundMailer;
import com.insurance.quotation.repository.QuotationRepository;

@Service
@ConditionalOnProperty(
        name = "quotation.response.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class QuotationReminderService {

    private static final Logger log =
            LoggerFactory.getLogger(QuotationReminderService.class);

    private final QuotationRepository quotationRepository;
    private final OutboundMailer outboundMailer;

    private final int reminderAfterDays;
    private final int closeAfterReminderDays;

    public QuotationReminderService(
            QuotationRepository quotationRepository,
            OutboundMailer outboundMailer,
            @Value("${quotation.response.reminder-after-days:3}")
            int reminderAfterDays,
            @Value("${quotation.response.close-after-reminder-days:2}")
            int closeAfterReminderDays) {

        this.quotationRepository = quotationRepository;
        this.outboundMailer = outboundMailer;
        this.reminderAfterDays = reminderAfterDays;
        this.closeAfterReminderDays = closeAfterReminderDays;
    }

    @Scheduled(
            fixedDelayString =
                    "${quotation.response.scheduler-delay-ms:3600000}"
    )
    public void processPendingQuotations() {

        log.info("Starting quotation response reminder processing");

        List<Quotation> pendingQuotations =
                quotationRepository
                        .findByResponseStatusAndClosedAtIsNullAndSentAtIsNotNull(
                                "PENDING");

        LocalDateTime now = LocalDateTime.now();

        int remindersSent = 0;
        int quotationsClosed = 0;

        for (Quotation quotation : pendingQuotations) {

            try {

                if (!"PENDING".equalsIgnoreCase(
                        quotation.getResponseStatus())) {
                    continue;
                }

                if (quotation.getClosedAt() != null) {
                    continue;
                }

                Integer reminderCount =
                        quotation.getReminderCount() != null
                                ? quotation.getReminderCount()
                                : 0;

                if (reminderCount == 0) {

                    LocalDateTime reminderDueAt =
                            quotation.getSentAt()
                                    .plusDays(reminderAfterDays);

                    if (!now.isBefore(reminderDueAt)) {

                        boolean sent =
                                sendReminderEmail(quotation);

                        if (sent) {

                            quotation.setReminderSentAt(now);
                            quotation.setReminderCount(1);

                            quotationRepository.save(quotation);

                            remindersSent++;

                            log.info(
                                    "Reminder sent for quotation {}",
                                    quotation.getQuotationNumber()
                            );
                        }
                    }

                    continue;
                }

                if (quotation.getReminderSentAt() != null) {

                    LocalDateTime closureDueAt =
                            quotation.getReminderSentAt()
                                    .plusDays(closeAfterReminderDays);

                    if (!now.isBefore(closureDueAt)) {

                        quotation.setClosedAt(now);
                        quotation.setClosureReason("NO_RESPONSE");

                        quotationRepository.save(quotation);

                        quotationClosedEmail(quotation);

                        quotationsClosed++;

                        log.info(
                                "Quotation {} closed due to no response",
                                quotation.getQuotationNumber()
                        );
                    }
                }

            } catch (RuntimeException e) {

                log.error(
                        "Failed to process response reminder for quotation {}",
                        quotation.getQuotationNumber(),
                        e
                );
            }
        }

        log.info(
                "Quotation response processing completed. "
                        + "Reminders sent: {}, quotations closed: {}",
                remindersSent,
                quotationsClosed
        );
    }

    private boolean sendReminderEmail(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send reminder for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return false;
        }

        String subject =
                "Reminder: Quotation Awaiting Your Response - "
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "We would like to remind you that the quotation "
                        + "below is awaiting your response.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n"
                        + "Amount: "
                        + quotation.getCurrency()
                        + " "
                        + quotation.getTotalAmount()
                        + "\n\n"
                        + "Please refer to the original quotation email "
                        + "and use the Accept or Reject option to submit "
                        + "your decision.\n\n"
                        + "If you have already responded, please disregard "
                        + "this reminder.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        try {

            outboundMailer.sendPlain(
                    email,
                    subject,
                    body
            );

            return true;

        } catch (RuntimeException e) {

            log.error(
                    "Failed to send reminder for quotation {}",
                    quotation.getQuotationNumber(),
                    e
            );

            return false;
        }
    }

    private void quotationClosedEmail(
            Quotation quotation) {

        String email = getClientEmail(quotation);

        if (email == null || email.isBlank()) {

            log.warn(
                    "Cannot send closure email for quotation {}: "
                            + "client email is missing",
                    quotation.getQuotationNumber()
            );

            return;
        }

        String subject =
                "Quotation Closure Notification - "
                        + quotation.getQuotationNumber();

        String body =
                "Dear Customer,\n\n"
                        + "We have not received a response to the "
                        + "quotation sent to you, despite our reminder.\n\n"
                        + "Quotation Number: "
                        + quotation.getQuotationNumber()
                        + "\n\n"
                        + "The quotation has now been closed due to "
                        + "no response.\n\n"
                        + "If you are still interested, please contact "
                        + "our team to request a new quotation.\n\n"
                        + "Regards,\n"
                        + "Insurance Quotation Team";

        try {

            outboundMailer.sendPlain(
                    email,
                    subject,
                    body
            );

        } catch (RuntimeException e) {

            log.error(
                    "Failed to send closure notification for quotation {}",
                    quotation.getQuotationNumber(),
                    e
            );
        }
    }

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
