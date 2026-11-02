package com.insurance.quotation.service.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailProcessingScheduler.class);

    private final AutomationService automationService;

    public EmailProcessingScheduler(AutomationService automationService) {
        this.automationService = automationService;
    }

    @Scheduled(fixedDelayString = "${mailbox.poll-ms:60000}", initialDelayString = "${mailbox.initial-delay-ms:15000}")
    public void processEmailsAutomatically() {
        try {
            automationService.processIncomingEmails();
        } catch (Exception e) {
            // a mailbox outage must not stop the scheduler
            log.error("Email check failed: {}", e.getMessage());
        }
    }
}
