package com.insurance.quotation.controller;

import com.insurance.quotation.service.automation.AutomationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final AutomationService automationService;

    public AutomationController(AutomationService automationService) {
        this.automationService = automationService;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processAutomation() {

        automationService.processIncomingEmails();

        return ResponseEntity.ok(
                Map.of(
                        "status", "success",
                        "message", "Incoming email automation processed"
                )
        );
    }
}