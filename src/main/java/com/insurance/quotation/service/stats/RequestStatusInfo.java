package com.insurance.quotation.service.stats;

import java.util.EnumSet;
import java.util.Set;

import com.insurance.quotation.entity.enums.RequestStatus;

/** One definition of what each request status means, used by EVERY page and count. */
public final class RequestStatusInfo {

    public enum Group {
        PROCESSING("Processing"),
        MISSING_DETAILS("Missing Details"),
        COMPLETED("Quotation Sent"),
        NEEDS_ATTENTION("Needs Attention");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private RequestStatusInfo() {
    }

    /** The reference shown for a request everywhere, e.g. REQ-2026-0012. */
    public static String reference(Long id, java.time.LocalDateTime createdAt) {
        return String.format("REQ-%d-%04d", createdAt == null ? 0 : createdAt.getYear(),
                id == null ? 0 : id);
    }

    public static Group group(RequestStatus s) {
        if (s == null) {
            return Group.PROCESSING;
        }
        return switch (s) {
            case MISSING_INFORMATION, WAITING_FOR_CLIENT -> Group.MISSING_DETAILS;
            case QUOTATION_SENT, COMPLETED -> Group.COMPLETED;
            case EMAIL_PROCESSING_FAILED, DATA_EXTRACTION_FAILED, QUOTATION_GENERATION_FAILED,
                 EMAIL_SEND_FAILED, MANUAL_REVIEW_REQUIRED -> Group.NEEDS_ATTENTION;
            default -> Group.PROCESSING;
        };
    }

    public static Set<RequestStatus> statuses(Group g) {
        Set<RequestStatus> set = EnumSet.noneOf(RequestStatus.class);
        for (RequestStatus s : RequestStatus.values()) {
            if (group(s) == g) {
                set.add(s);
            }
        }
        return set;
    }

    public static String label(RequestStatus s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case EMAIL_RECEIVED -> "Email received";
            case PROCESSING -> "Processing";
            case DATA_EXTRACTED -> "Data extracted";
            case VALIDATING -> "Validating details";
            case MISSING_INFORMATION -> "Missing details";
            case WAITING_FOR_CLIENT -> "Waiting for client";
            case CLIENT_REPLY_RECEIVED -> "Client replied";
            case RE_VALIDATING -> "Re-validating";
            case READY_FOR_QUOTATION -> "Ready for quotation";
            case QUOTATION_GENERATED -> "Quotation generated";
            case QUOTATION_SENT -> "Quotation sent";
            case COMPLETED -> "Completed";
            case EMAIL_PROCESSING_FAILED -> "Email processing failed";
            case DATA_EXTRACTION_FAILED -> "Data extraction failed";
            case QUOTATION_GENERATION_FAILED -> "Quotation failed";
            case EMAIL_SEND_FAILED -> "Email sending failed";
            case MANUAL_REVIEW_REQUIRED -> "Manual review required";
        };
    }

    public static String emailStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "RECEIVED" -> "Pending";
            case "PROCESSED" -> "Processed";
            case "FAILED" -> "Failed";
            case "IGNORED" -> "Ignored";
            default -> status;
        };
    }

    public static String emailKindLabel(String kind) {
        if (kind == null) {
            return "";
        }
        return switch (kind) {
            case "NEW_REQUEST" -> "New request";
            case "CLIENT_REPLY" -> "Client reply";
            case "UNRELATED" -> "Unrelated";
            default -> kind;
        };
    }

    public static String quotationStatusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "DRAFT" -> "Not sent";
            case "SENT" -> "Sent";
            case "SUPERSEDED" -> "Superseded";
            default -> status;
        };
    }
}
