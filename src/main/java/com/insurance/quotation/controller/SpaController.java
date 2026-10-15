package com.insurance.quotation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Used only when the built Angular app is copied into src/main/resources/static
 * (single-port deployment). It sends every Angular page URL to index.html so
 * refreshing the browser on /dashboard or /client-quotation still works.
 * Harmless during development.
 */
@Controller
public class SpaController {

    @GetMapping({
            "/",
            "/login",
            "/client-quotation",
            "/dashboard",
            "/incoming-emails",
            "/quotation-requests",
            "/clients",
            "/quotations",
            "/missing-information",
            "/reports",
            "/settings"
    })
    public String forwardToAngular() {
        return "forward:/index.html";
    }
}
