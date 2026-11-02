package com.insurance.quotation.service.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.insurance.quotation.service.email.EmailClassificationService.Kind;
import com.insurance.quotation.service.master.InsuranceTypeDef;

class EmailClassificationServiceTest {

    private final EmailClassificationService service = new EmailClassificationService();
    private final List<String> keywords =
            List.of("quotation", "quote", "policy", "insurance", "travel");

    @Test
    void subjectWithQuotationWordIsNewRequest() {
        assertEquals(Kind.NEW_REQUEST,
                service.classify("Travel quotation request", false, false, keywords).kind());
        assertEquals(Kind.NEW_REQUEST,
                service.classify("Need a QUOTE for my car", false, false, keywords).kind());
    }

    @Test
    void wordStartMatchAcceptsTravelling() {
        assertTrue(EmailClassificationService.subjectContainsKeyword(
                "Travelling to Paris", keywords));
    }

    @Test
    void subjectWithoutKeywordIsUnrelated() {
        assertEquals(Kind.UNRELATED,
                service.classify("Lunch on Friday?", false, false, keywords).kind());
        assertEquals(Kind.UNRELATED,
                service.classify(null, false, true, keywords).kind());
    }

    @Test
    void replyFromSenderWithPendingRequestIsClientReply() {
        assertEquals(Kind.CLIENT_REPLY,
                service.classify("Re: Additional information required", true, false, keywords).kind());
        assertEquals(Kind.CLIENT_REPLY,
                service.classify("Here are my details", true, true, keywords).kind());
    }

    @Test
    void unrelatedMailEvenFromPendingSenderStaysUnrelated() {
        assertEquals(Kind.UNRELATED,
                service.classify("Happy birthday", true, false, keywords).kind());
    }

    @Test
    void emptyKeywordListMatchesNothing() {
        assertFalse(EmailClassificationService.subjectContainsKeyword("quotation", List.of()));
    }

    @Test
    void insuranceTypeIsDetectedFromKeywords() {
        List<InsuranceTypeDef> types = List.of(
                new InsuranceTypeDef("TRAVEL", "Travel Insurance", "travel,trip,passport",
                        BigDecimal.ZERO, BigDecimal.ZERO, null, true, 1),
                new InsuranceTypeDef("MOTOR", "Motor Insurance", "motor,car,vehicle",
                        BigDecimal.ZERO, BigDecimal.ZERO, null, true, 2),
                new InsuranceTypeDef("HEALTH", "Health Insurance", "health,medical",
                        BigDecimal.ZERO, BigDecimal.ZERO, null, false, 3));

        assertEquals("MOTOR", service.detectInsuranceType(
                "Insurance quote", "I want cover for my new car", types));
        assertEquals("TRAVEL", service.detectInsuranceType(
                "Policy Type: TRAVEL", "", types));
        // inactive types are never chosen
        assertNull(service.detectInsuranceType("health cover", "medical", types));
        assertNull(service.detectInsuranceType("hello", "nothing here", types));
    }
}
