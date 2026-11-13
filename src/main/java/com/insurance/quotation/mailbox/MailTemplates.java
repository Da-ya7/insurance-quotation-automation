
package com.insurance.quotation.mailbox;

import java.util.List;

/** All customer-facing email texts in one place. */
public final class MailTemplates {

    public record Mail(String subject, String body) {
    }

    private MailTemplates() {
    }

    private static final String SIGNATURE =
            "\n\nRegards,\nInsurance Quotation Team";

    /**
     * Email asking the customer to complete the PUBLIC quotation form.
     * Existing method retained for compatibility.
     */
    public static Mail formLink(
            boolean returningClient,
            String formUrl) {

        return formLink(returningClient, formUrl, null);
    }

    /**
     * Email asking the customer to complete the PUBLIC quotation form
     * with a unique quotation request reference.
     */
    public static Mail formLink(
            boolean returningClient,
            String formUrl,
            Long requestId) {

        StringBuilder b = new StringBuilder();

        b.append("Dear Client,\n\n");
        b.append("Thank you for contacting us.\n\n");

        b.append("To prepare your insurance quotation, please complete ")
         .append("our public quotation form using the link below:\n\n");

        b.append(formUrl).append("\n\n");

        if (returningClient) {
            b.append("If you have applied before, please review your ")
             .append("details and provide any missing information.\n\n");
        } else {
            b.append("Please enter your personal details and insurance ")
             .append("requirements accurately.\n\n");
        }

        // Add a unique request reference when available.
        if (requestId != null) {
            String reference = "IQAS-REQ-" + requestId;

            b.append("Your quotation request reference is: ")
             .append(reference)
             .append("\n\n");

            b.append("Please keep this reference when replying ")
             .append("to our emails.\n\n");
        }

        b.append("Once you submit the form, our team will process ")
         .append("your request and contact you regarding your quotation.");

        b.append(SIGNATURE);

        String subject =
                "Insurance Quotation - Complete Your Details";

        if (requestId != null) {
            subject += " [IQAS-REQ-" + requestId + "]";
        }

        return new Mail(subject, b.toString());
    }

    /**
     * Email asking for specific missing or invalid details.
     * Existing method retained for compatibility.
     */
    public static Mail missingInformation(
            List<String> missing,
            List<String> invalid,
            String formUrl) {

        return missingInformation(
                missing,
                invalid,
                formUrl,
                null
        );
    }

    /**
     * Missing-information email with a unique quotation request reference.
     */
    public static Mail missingInformation(
            List<String> missing,
            List<String> invalid,
            String formUrl,
            Long requestId) {

        StringBuilder b = new StringBuilder();

        b.append("Dear Client,\n\n");

        b.append("Thank you for your insurance quotation request. ")
         .append("We need a few more details before we can prepare ")
         .append("your quotation.\n\n");

        if (missing != null && !missing.isEmpty()) {
            b.append("Missing information:\n");

            missing.forEach(field ->
                    b.append("- ").append(field).append('\n'));

            b.append('\n');
        }

        if (invalid != null && !invalid.isEmpty()) {
            b.append("Please correct the following information:\n");

            invalid.forEach(field ->
                    b.append("- ").append(field).append('\n'));

            b.append('\n');
        }

        if (formUrl != null && !formUrl.isBlank()) {

            b.append("Please complete or correct your details ")
             .append("using our public quotation form:\n\n");

            b.append(formUrl).append("\n\n");
        }

        b.append("You can also reply to this email using the format ")
         .append("Field Name: Value.");

        String subject =
                "Additional Information Required - Insurance Quotation";

        // Add the request reference to the subject and email body.
        if (requestId != null) {

            String reference = "IQAS-REQ-" + requestId;

            subject += " [" + reference + "]";

            b.append("\n\nYour quotation request reference is: ")
             .append(reference)
             .append("\n");

            b.append("Please keep this reference when replying.");
        }

        b.append(SIGNATURE);

        return new Mail(subject, b.toString());
    }

    /**
     * Email containing the generated quotation.
     */
    public static Mail quotation(
            String quotationNumber,
            int version) {

        String body =
                "Dear Client,\n\n"
                + "Thank you for your interest in our insurance services.\n"
                + "Please find your quotation attached to this email.\n\n"
                + "Quotation Number: " + quotationNumber
                + (version > 1
                    ? " (updated version " + version + ")"
                    : "")
                + "\n\n"
                + "Please review the attached quotation. "
                + "If you have any questions, reply to this email."
                + SIGNATURE;

        return new Mail(
                "Your Insurance Quotation - " + quotationNumber,
                body
        );
    }
}
