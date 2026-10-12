package com.insurance.quotation.service.automation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailProcessingScheduler {

    private final AutomationService automationService;

    public EmailProcessingScheduler(
            AutomationService automationService) {
        this.automationService = automationService;
    }

    @Scheduled(fixedDelay = 60000)
    public void processEmailsAutomatically() {

        System.out.println(
                "Scheduler: checking for new incoming emails..."
        );

        automationService.processIncomingEmails();
    }
}