package com.insurance.quotation.service.email;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.insurance.quotation.service.master.InsuranceTypeDef;

/**
 * First-level email filter. The SUBJECT must contain a quotation-related
 * keyword (kept in the database) unless the sender is answering a request that
 * is waiting for information.
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

    public record Classification(Kind kind, boolean subjectMatched, String reason) { }

    public Classification classify(
            String subject,
            boolean senderHasPendingRequest,
            boolean bodyHasRequestDetails,
            List<String> subjectKeywords) {

        boolean subjectMatched = subjectContainsKeyword(subject, subjectKeywords);
        boolean looksLikeReply = subject != null
                && subject.trim().toLowerCase(Locale.ROOT).matches("^(re|fw|fwd)\\s*:.*");

        if (senderHasPendingRequest
                && (subjectMatched || looksLikeReply || bodyHasRequestDetails)) {
            return new Classification(Kind.CLIENT_REPLY, subjectMatched,
                    "Sender has a request waiting for information");
        }
        if (subjectMatched) {
            return new Classification(Kind.NEW_REQUEST, true,
                    "Subject contains a quotation keyword");
        }
        return new Classification(Kind.UNRELATED, false,
                "Subject has no quotation-related keyword");
    }

    public static boolean subjectContainsKeyword(String subject, List<String> keywords) {
        if (subject == null || subject.isBlank() || keywords == null) {
            return false;
        }
        for (String k : keywords) {
            if (k == null || k.isBlank()) {
                continue;
            }
            // word START match: "travel" also matches "travelling"
            Pattern p = Pattern.compile("(?i)\\b" + Pattern.quote(k.trim()));
            if (p.matcher(subject).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Picks the insurance type whose code/keywords occur most in the text.
     * Returns null when nothing matches (the client then chooses on the form).
     */
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
        return Pattern.compile("\\b" + Pattern.quote(word)).matcher(text).find();
    }
}
