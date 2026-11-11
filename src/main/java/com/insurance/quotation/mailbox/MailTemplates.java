
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
     */
    public static Mail formLink(boolean returningClient, String formUrl) {

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

        b.append("Once you submit the form, our team will process ")
         .append("your request and contact you regarding your quotation.");

        b.append(SIGNATURE);

        return new Mail(
                "Insurance Quotation - Complete Your Details",
                b.toString()
        );
    }

    /**
     * Email asking for specific missing or invalid details.
     */
    public static Mail missingInformation(
            List<String> missing,
            List<String> invalid,
            String formUrl) {

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

        b.append(SIGNATURE);

        return new Mail(
                "Additional Information Required - Insurance Quotation",
                b.toString()
        );
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
