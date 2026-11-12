
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

    /** Reply to an email that did not contain enough details to start a quotation. */
    public static Mail formLink(boolean returningClient, String formUrl) {
        StringBuilder b = new StringBuilder();

        b.append("Dear Client,\n\n");
        b.append("Thank you for contacting us.\n\n");
        b.append("We could not find all the details we need to prepare your insurance ")
         .append("quotation from your email. Please fill in the details using this form:\n")
         .append(formUrl).append("\n\n");

        if (returningClient) {
            b.append("Because you have applied before, we have already filled in the details ")
             .append("we hold for you. You only need to add the remaining information. ")
             .append("If you want to change saved personal details, we will send a one-time ")
             .append("code to the phone number registered with us to confirm it is you.\n\n");
        }

        b.append("The link is personal to you and valid for 7 days. Your quotation will be ")
         .append("emailed as soon as the form is submitted.");

        b.append(SIGNATURE);

        return new Mail(
                "Thank you for contacting us - please complete your quotation request",
                b.toString()
        );
    }

    /** Reply asking for the specific missing / invalid details. */
    public static Mail missingInformation(
            List<String> missing, List<String> invalid, String formUrl) {

        StringBuilder b = new StringBuilder();

        b.append("Dear Client,\n\nThank you for your insurance quotation request. ")
         .append("We need a few more details before we can prepare your quotation.\n\n");

        if (missing != null && !missing.isEmpty()) {
            b.append("Missing information:\n");
            missing.forEach(f -> b.append("- ").append(f).append('\n'));
            b.append('\n');
        }

        if (invalid != null && !invalid.isEmpty()) {
            b.append("Please correct the following information:\n");
            invalid.forEach(f -> b.append("- ").append(f).append('\n'));
            b.append('\n');
        }

        if (formUrl != null) {
            b.append("The quickest way is to complete these details in your personal form:\n")
             .append(formUrl).append("\n\n");
        }

        b.append("You can also reply to this email using the format Field Name: Value.");
        b.append(SIGNATURE);

        return new Mail(
                "Additional Information Required - Insurance Quotation",
                b.toString()
        );
    }

    /** Reply to a customer asking general insurance policy-related questions. */
    public static Mail policyQuestion() {

        String body = "Dear Client,\n\n"
                + "Thank you for contacting our Insurance Quotation Team.\n\n"
                + "We are happy to help with your insurance policy-related questions.\n"
                + "Please reply to this email with your specific question, such as "
                + "policy coverage, benefits, exclusions, eligibility, or claims.\n\n"
                + "Our team will review your enquiry and provide the relevant information.\n"
                + SIGNATURE;

        return new Mail(
                "Your Insurance Policy Enquiry - Thank You",
                body
        );
    }

    /** Quotation email with the generated PDF attachment. */
    public static Mail quotation(String quotationNumber, int version) {

        String body = "Dear Client,\n\n"
                + "Thank you for your interest in our insurance services.\n"
                + "Please find your quotation attached to this email.\n\n"
                + "Quotation Number: " + quotationNumber
                + (version > 1 ? " (updated version " + version + ")" : "")
                + "\n\n"
                + "Please review the attached quotation. If you have any questions, reply to this email."
                + SIGNATURE;

        return new Mail(
                "Your Insurance Quotation - " + quotationNumber,
                body
        );
    }
}
