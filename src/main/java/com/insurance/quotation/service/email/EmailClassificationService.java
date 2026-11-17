
package com.insurance.quotation.service.email;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.insurance.quotation.service.master.InsuranceTypeDef;

/**
 * First-level email filter.
 * Configured quotation keywords are checked in both subject and body.
 */
@Service
public class EmailClassificationService {

    public enum Kind {
        NEW_REQUEST("New request"),
        CLIENT_REPLY("Client reply"),
        UNRELATED("Unrelated");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Classification(
            Kind kind,
            boolean subjectMatched,
            String reason) {
    }

    /**
     * Classifies an email using configured keywords in both subject and body.
     */
    public Classification classify(
            String subject,
            String body,
            boolean senderHasPendingRequest,
            boolean bodyHasRequestDetails,
            List<String> keywords) {

        boolean subjectMatched =
                containsKeyword(subject, keywords);

        boolean bodyMatched =
                containsKeyword(body, keywords);

        boolean looksLikeReply = subject != null
                && subject.trim().toLowerCase(Locale.ROOT)
                        .matches("^(re|fw|fwd)\\s*:.*");

        if (senderHasPendingRequest
                && (subjectMatched
                    || bodyMatched
                    || looksLikeReply
                    || bodyHasRequestDetails)) {

            return new Classification(
                    Kind.CLIENT_REPLY,
                    subjectMatched,
                    "Sender has a request waiting for information"
            );
        }

        if (subjectMatched || bodyMatched) {

            String reason = subjectMatched && bodyMatched
                    ? "Quotation keyword found in subject and body"
                    : subjectMatched
                        ? "Quotation keyword found in subject"
                        : "Quotation keyword found in body";

            return new Classification(
                    Kind.NEW_REQUEST,
                    subjectMatched,
                    reason
            );
        }

        return new Classification(
                Kind.UNRELATED,
                false,
                "No quotation-related keyword found in subject or body"
        );
    }

    /**
     * Backward-compatible overload for existing callers and tests.
     */
    public Classification classify(
            String subject,
            boolean senderHasPendingRequest,
            boolean bodyHasRequestDetails,
            List<String> keywords) {

        return classify(
                subject,
                null,
                senderHasPendingRequest,
                bodyHasRequestDetails,
                keywords
        );
    }

    public static boolean subjectContainsKeyword(
            String subject,
            List<String> keywords) {

        return containsKeyword(subject, keywords);
    }

    /**
     * Checks whether any configured keyword occurs in the supplied text.
     * Case-insensitive. Word-start matching means "travel" also matches
     * "travelling".
     */
    public static boolean containsKeyword(
            String text,
            List<String> keywords) {

        if (text == null || text.isBlank()
                || keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {

            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            Pattern pattern = Pattern.compile(
                    "(?i)\\b" + Pattern.quote(keyword.trim())
            );

            if (pattern.matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detects the insurance type using its code and configured type keywords
     * in both the subject and body.
     */
    public String detectInsuranceType(
            String subject,
            String body,
            List<InsuranceTypeDef> types) {

        String text = ((subject == null ? "" : subject) + " "
                + (body == null ? "" : body))
                .toLowerCase(Locale.ROOT);

        String best = null;
        int bestScore = 0;

        for (InsuranceTypeDef type : types) {

            if (!type.active()) {
                continue;
            }

            int score = 0;

            if (containsWord(
                    text,
                    type.code().toLowerCase(Locale.ROOT))) {
                score += 3;
            }

            if (type.keywords() != null) {

                for (String keyword : type.keywords().split(",")) {

                    String normalized = keyword.trim()
                            .toLowerCase(Locale.ROOT);

                    if (!normalized.isEmpty()
                            && containsWord(text, normalized)) {
                        score++;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = type.code();
            }
        }

        return best;
    }

    private static boolean containsWord(String text, String word) {

        return Pattern.compile(
                "\\b" + Pattern.quote(word)
        ).matcher(text).find();
    }
}
