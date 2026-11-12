
package com.insurance.quotation.service.email;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.insurance.quotation.service.master.InsuranceTypeDef;

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

    public record Classification(Kind kind, boolean subjectMatched, String reason) { }

    // Backward-compatible method
    public Classification classify(
            String subject,
            boolean senderHasPendingRequest,
            boolean bodyHasRequestDetails,
            List<String> subjectKeywords) {

        return classify(subject, "", senderHasPendingRequest,
                bodyHasRequestDetails, subjectKeywords);
    }

    // Checks both subject and body
    public Classification classify(
            String subject,
            String body,
            boolean senderHasPendingRequest,
            boolean bodyHasRequestDetails,
            List<String> subjectKeywords) {

        boolean subjectMatched = subjectContainsKeyword(subject, subjectKeywords);
        boolean bodyMatched = subjectContainsKeyword(body, subjectKeywords);
        boolean keywordMatched = subjectMatched || bodyMatched;

        boolean looksLikeReply = subject != null
                && subject.trim().toLowerCase(Locale.ROOT)
                .matches("^(re|fw|fwd)\\s*:.*");

        if (senderHasPendingRequest
                && (keywordMatched || looksLikeReply || bodyHasRequestDetails)) {
            return new Classification(
                    Kind.CLIENT_REPLY,
                    subjectMatched,
                    "Sender has a request waiting for information");
        }

        if (keywordMatched) {
            return new Classification(
                    Kind.NEW_REQUEST,
                    subjectMatched,
                    subjectMatched
                            ? "Subject contains a quotation keyword"
                            : "Email body contains a quotation keyword");
        }

        return new Classification(
                Kind.UNRELATED,
                false,
                "Neither subject nor body contains a quotation keyword");
    }

    public static boolean subjectContainsKeyword(
            String text, List<String> keywords) {

        if (text == null || text.isBlank() || keywords == null) {
            return false;
        }

        String normalizedText = text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }

            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);

            // Matches keyword regardless of uppercase/lowercase.
            // Also matches travel inside travelling.
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(normalizedKeyword));

            if (pattern.matcher(normalizedText).find()) {
                return true;
            }
        }

        return false;
    }

    public String detectInsuranceType(
            String subject, String body, List<InsuranceTypeDef> types) {

        String text = ((subject == null ? "" : subject) + " "
                + (body == null ? "" : body)).toLowerCase(Locale.ROOT);

        String best = null;
        int bestScore = 0;

        for (InsuranceTypeDef t : types) {
            if (!t.active()) {
                continue;
            }

            int score = 0;

            if (containsWord(text, t.code().toLowerCase(Locale.ROOT))) {
                score += 3;
            }

            if (t.keywords() != null) {
                for (String k : t.keywords().split(",")) {
                    String kw = k.trim().toLowerCase(Locale.ROOT);

                    if (!kw.isEmpty() && containsWord(text, kw)) {
                        score++;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                best = t.code();
            }
        }

        return best;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word))
                .matcher(text).find();
    }
}
