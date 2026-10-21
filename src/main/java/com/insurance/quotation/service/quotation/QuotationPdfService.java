
package com.insurance.quotation.service.quotation;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import com.insurance.quotation.entity.RequestFieldValue;
import com.insurance.quotation.repository.QuotationItemRepository;
import com.insurance.quotation.repository.QuotationRepository;
import com.insurance.quotation.repository.RequestFieldValueRepository;

@Service
public class QuotationPdfService {

    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;

    private static final float MARGIN = 48;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH =
            PAGE_WIDTH - (2 * MARGIN);

    private static final PDColor NAVY = color(24, 65, 105);
    private static final PDColor BLUE = color(35, 119, 190);
    private static final PDColor LIGHT_BLUE = color(237, 245, 252);
    private static final PDColor BORDER = color(210, 222, 233);
    private static final PDColor GREY = color(75, 91, 108);
    private static final PDColor WHITE = color(255, 255, 255);

    private static final PDFont REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final PDFont BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    public QuotationPdfService(
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository,
            RequestFieldValueRepository requestFieldValueRepository) {

        this.quotationRepository = quotationRepository;
        this.quotationItemRepository = quotationItemRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
    }

    public String generatePdf(Quotation quotation) {

        Path folder = Paths.get("generated", "quotations");

        try {
            Files.createDirectories(folder);

            String quotationNumber =
                    safe(quotation.getQuotationNumber());

            Path pdfPath = folder.resolve(quotationNumber + ".pdf");

            Client client =
                    quotation.getQuotationRequest().getClient();

            Long requestId = quotation.getQuotationRequest().getId();

            // Load the saved field values for this quotation request.
            List<RequestFieldValue> savedFields =
                    requestFieldValueRepository
                            .findByQuotationRequestId(requestId);

            Map<String, String> travelFields = new HashMap<>();

            if (savedFields != null) {
                for (RequestFieldValue field : savedFields) {

                    if (field == null
                            || field.getRequiredField() == null
                            || field.getRequiredField().getFieldName() == null) {
                        continue;
                    }

                    String key = normalizeFieldName(
                            field.getRequiredField().getFieldName());

                    String fieldValue = field.getFieldValue();

                    if (!key.isBlank()
                            && fieldValue != null
                            && !fieldValue.isBlank()) {
                        travelFields.put(key, fieldValue.trim());
                    }
                }
            }

            // Travel details: support alternative field names.
            String departureCountry = getField(
                    travelFields,
                    "originCountry",
                    "departureCountry",
                    "origin_country",
                    "departure_country");

            String destinationCountry = getField(
                    travelFields,
                    "destinationCountry",
                    "destination_country",
                    "destination");

            String travelStartDate = formatDate(getField(
                    travelFields,
                    "travelStartDate",
                    "travel_start_date",
                    "startDate"));

            String travelEndDate = formatDate(getField(
                    travelFields,
                    "travelEndDate",
                    "travel_end_date",
                    "endDate"));

            String travellers = getField(
                    travelFields,
                    "travellers",
                    "numberOfTravellers",
                    "number_of_travellers");

            String policyType =
                    safe(quotation.getQuotationRequest().getPolicyType());

            String currency = safe(quotation.getCurrency());

            String issueDate = quotation.getCreatedAt() == null
                    ? LocalDate.now().format(DISPLAY_DATE)
                    : quotation.getCreatedAt()
                            .toLocalDate()
                            .format(DISPLAY_DATE);

            List<QuotationItem> items =
                    quotationItemRepository.findByQuotation(quotation);

            try (PDDocument document = new PDDocument()) {

                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream cs =
                             new PDPageContentStream(document, page)) {

                    // Page background and top accent.
                    rect(cs, 0, 0, PAGE_WIDTH, PAGE_HEIGHT, WHITE);
                    rect(cs, 0, PAGE_HEIGHT - 5,
                            PAGE_WIDTH, 5, BLUE);

                    // Company heading.
                    text(cs, "WENXT INSURANCE",
                            MARGIN, 790, BOLD, 21, NAVY);

                    rightText(cs, "INSURANCE QUOTATION",
                            PAGE_WIDTH - MARGIN, 790,
                            BOLD, 12, NAVY);

                    rightText(cs, "Automated Quotation System",
                            PAGE_WIDTH - MARGIN, 773,
                            REGULAR, 9, BLUE);

                    line(cs, MARGIN, 758,
                            PAGE_WIDTH - MARGIN, 758, BLUE, 1);

                    // Quotation summary.
                    text(cs, "QUOTATION SUMMARY",
                            MARGIN, 738, BOLD, 11, NAVY);

                    float tableTop = 720;
                    float rowHeight = 30;

                    float label1Width = 100;
                    float value1Width = 150;
                    float label2Width = 100;

                    float value2Width = CONTENT_WIDTH
                            - label1Width
                            - value1Width
                            - label2Width;

                    // All five rows use the same four-column layout.
                    String[][] rows = {
                        {
                            "Quotation No.", quotationNumber,
                            "Date", issueDate
                        },
                        {
                            "Client Name", safe(client.getName()),
                            "Policy Type", policyType
                        },
                        {
                            "Email", safe(client.getEmail()),
                            "Travellers", travellers
                        },
                        {
                            "Departure Country", departureCountry,
                            "Destination Country", destinationCountry
                        },
                        {
                            "Travel Start Date", travelStartDate,
                            "Travel End Date", travelEndDate
                        }
                    };

                    for (int i = 0; i < rows.length; i++) {

                        float y = tableTop - ((i + 1) * rowHeight);

                        rect(cs, MARGIN, y,
                                CONTENT_WIDTH, rowHeight, LIGHT_BLUE);

                        outline(cs, MARGIN, y,
                                CONTENT_WIDTH, rowHeight, BORDER);

                        float x1 = MARGIN + label1Width;
                        float x2 = x1 + value1Width;
                        float x3 = x2 + label2Width;

                        line(cs, x1, y, x1, y + rowHeight,
                                BORDER, 0.5f);

                        line(cs, x2, y, x2, y + rowHeight,
                                BORDER, 0.5f);

                        line(cs, x3, y, x3, y + rowHeight,
                                BORDER, 0.5f);

                        text(cs, rows[i][0],
                                MARGIN + 7, y + 11,
                                BOLD, 7.5f, GREY);

                        text(cs, rows[i][1],
                                x1 + 7, y + 11,
                                REGULAR, 8, NAVY);

                        text(cs, rows[i][2],
                                x2 + 7, y + 11,
                                BOLD, 7.5f, GREY);

                        text(cs, rows[i][3],
                                x3 + 7, y + 11,
                                REGULAR, 8, NAVY);
                    }

                    // Premium details.
                    float premiumTitleY =
                            tableTop - (rows.length * rowHeight) - 28;

                    text(cs, "PREMIUM DETAILS",
                            MARGIN, premiumTitleY,
                            BOLD, 11, NAVY);

                    float premiumTableTop = premiumTitleY - 18;
                    float headerHeight = 26;

                    float col1 = 190;
                    float col2 = 160;
                    float col3 = CONTENT_WIDTH - col1 - col2;

                    rect(cs, MARGIN,
                            premiumTableTop - headerHeight,
                            CONTENT_WIDTH, headerHeight, NAVY);

                    text(cs, "Coverage / Plan",
                            MARGIN + 8, premiumTableTop - 17,
                            BOLD, 8.5f, WHITE);

                    text(cs, "Coverage Amount",
                            MARGIN + col1 + 8,
                            premiumTableTop - 17,
                            BOLD, 8.5f, WHITE);

                    text(cs, "Premium",
                            MARGIN + col1 + col2 + 8,
                            premiumTableTop - 17,
                            BOLD, 8.5f, WHITE);

                    float rowY =
                            premiumTableTop - headerHeight - 36;

                    if (items != null && !items.isEmpty()) {

                        for (QuotationItem item : items) {

                            rect(cs, MARGIN, rowY,
                                    CONTENT_WIDTH, 36, WHITE);

                            outline(cs, MARGIN, rowY,
                                    CONTENT_WIDTH, 36, BORDER);

                            line(cs, MARGIN + col1,
                                    rowY, MARGIN + col1,
                                    rowY + 36, BORDER, 0.5f);

                            line(cs, MARGIN + col1 + col2,
                                    rowY, MARGIN + col1 + col2,
                                    rowY + 36, BORDER, 0.5f);

                            text(cs, safe(item.getDescription()),
                                    MARGIN + 8, rowY + 14,
                                    REGULAR, 8, NAVY);

                            text(cs, "As per selected policy",
                                    MARGIN + col1 + 8, rowY + 14,
                                    REGULAR, 8, NAVY);

                            text(cs, currency + " "
                                            + money(item.getAmount()),
                                    MARGIN + col1 + col2 + 8,
                                    rowY + 14,
                                    REGULAR, 8, NAVY);

                            rowY -= 36;
                        }

                    } else {

                        rect(cs, MARGIN, rowY,
                                CONTENT_WIDTH, 36, WHITE);

                        outline(cs, MARGIN, rowY,
                                CONTENT_WIDTH, 36, BORDER);

                        text(cs, "Insurance Premium",
                                MARGIN + 8, rowY + 14,
                                REGULAR, 8, NAVY);

                        text(cs, "As per selected policy",
                                MARGIN + col1 + 8, rowY + 14,
                                REGULAR, 8, NAVY);

                        text(cs, currency + " "
                                        + money(quotation.getPremiumAmount()),
                                MARGIN + col1 + col2 + 8,
                                rowY + 14,
                                REGULAR, 8, NAVY);

                        rowY -= 36;
                    }

                    // Premium total panel.
                    float totalY = rowY - 18;

                    rect(cs, MARGIN, totalY,
                            CONTENT_WIDTH, 52, LIGHT_BLUE);

                    outline(cs, MARGIN, totalY,
                            CONTENT_WIDTH, 52, BORDER);

                    text(cs, "Base Premium",
                            MARGIN + 12, totalY + 37,
                            REGULAR, 8.5f, GREY);

                    rightText(cs,
                            currency + " "
                                    + money(quotation.getPremiumAmount()),
                            PAGE_WIDTH - MARGIN - 12,
                            totalY + 37,
                            REGULAR, 8.5f, NAVY);

                    text(cs, "Applicable Taxes",
                            MARGIN + 12, totalY + 23,
                            REGULAR, 8.5f, GREY);

                    rightText(cs,
                            currency + " "
                                    + money(quotation.getTaxAmount()),
                            PAGE_WIDTH - MARGIN - 12,
                            totalY + 23,
                            REGULAR, 8.5f, NAVY);

                    // Highlight the total payable amount.
                    rect(cs, MARGIN, totalY,
                            CONTENT_WIDTH, 20, BLUE);

                    text(cs, "Total Estimated Premium",
                            MARGIN + 12, totalY + 6,
                            BOLD, 9, WHITE);

                    rightText(cs,
                            currency + " "
                                    + money(quotation.getTotalAmount()),
                            PAGE_WIDTH - MARGIN - 12,
                            totalY + 6,
                            BOLD, 10, WHITE);

                    // Important notes.
                    float notesY = totalY - 28;

                    text(cs, "IMPORTANT NOTES",
                            MARGIN, notesY, BOLD, 10, NAVY);

                    String[] notes = {
                        "This quotation is an estimate and is subject to insurer underwriting and final policy terms.",
                        "Coverage, exclusions, waiting periods and benefits are governed by the final policy wording.",
                        "Please verify all customer and travel details before confirming the policy."
                    };

                    float noteY = notesY - 17;

                    for (String note : notes) {
                        text(cs, "- " + note,
                                MARGIN, noteY, REGULAR, 7.5f, GREY);
                        noteY -= 16;
                    }

                    // Footer.
                    line(cs, MARGIN, 85,
                            PAGE_WIDTH - MARGIN, 85, BLUE, 0.8f);

                    text(cs, "SecureLife Insurance",
                            MARGIN, 68, BOLD, 8, NAVY);

                    text(cs, "Quotation generated electronically",
                            MARGIN, 55, REGULAR, 7, GREY);

                    rightText(cs, "Thank you for choosing us.",
                            PAGE_WIDTH - MARGIN, 68,
                            REGULAR, 8, NAVY);

                    rightText(cs, "Your protection is our priority.",
                            PAGE_WIDTH - MARGIN, 55,
                            BOLD, 8, NAVY);
                }

                document.save(pdfPath.toFile());
            }

            quotation.setPdfPath(pdfPath.toString());
            quotationRepository.save(quotation);

            return pdfPath.toString();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to generate quotation PDF", e);
        }
    }

    // Retrieves a value using one or more possible field names.
    private String getField(
            Map<String, String> fields, String... fieldNames) {

        for (String fieldName : fieldNames) {

            String value = fields.get(normalizeFieldName(fieldName));

            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "-";
    }

    // Makes field names consistent regardless of case or underscores.
    private String normalizeFieldName(String name) {
        return name == null
                ? ""
                : name.replaceAll("[^a-zA-Z0-9]", "")
                      .toLowerCase(Locale.ROOT);
    }

    // Formats ISO dates such as 2026-10-27 as 27 Oct 2026.
    private String formatDate(String value) {

        if (value == null || value.isBlank() || value.equals("-")) {
            return "-";
        }

        String cleaned = value.trim();

        DateTimeFormatter[] acceptedFormats = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        };

        for (DateTimeFormatter formatter : acceptedFormats) {
            try {
                return LocalDate.parse(cleaned, formatter)
                        .format(DISPLAY_DATE);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted date format.
            }
        }

        return cleaned;
    }

    private static PDColor color(int r, int g, int b) {
        return new PDColor(
                new float[] {
                    r / 255f, g / 255f, b / 255f
                },
                PDDeviceRGB.INSTANCE);
    }

    private void rect(
            PDPageContentStream cs,
            float x, float y, float w, float h,
            PDColor color) throws IOException {

        cs.setNonStrokingColor(color);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private void outline(
            PDPageContentStream cs,
            float x, float y, float w, float h,
            PDColor color) throws IOException {

        cs.setStrokingColor(color);
        cs.setLineWidth(0.5f);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private void line(
            PDPageContentStream cs,
            float x1, float y1, float x2, float y2,
            PDColor color, float width) throws IOException {

        cs.setStrokingColor(color);
        cs.setLineWidth(width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void text(
            PDPageContentStream cs,
            String value,
            float x, float y,
            PDFont font, float size,
            PDColor color) throws IOException {

        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(clean(value));
        cs.endText();
    }

    private void rightText(
            PDPageContentStream cs,
            String value,
            float rightX, float y,
            PDFont font, float size,
            PDColor color) throws IOException {

        String cleaned = clean(value);

        float width =
                font.getStringWidth(cleaned) / 1000f * size;

        text(cs, cleaned, rightX - width, y, font, size, color);
    }

    private String money(BigDecimal amount) {
        return amount == null
                ? "0.00"
                : amount.setScale(2, RoundingMode.HALF_UP)
                         .toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "-"
                : value.trim();
    }

    private String clean(String value) {

        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (char c : value.toCharArray()) {

            if (c == '\n' || c == '\r' || c == '\t') {
                result.append(' ');
            } else if (c >= 32 && c <= 255) {
                result.append(c);
            } else {
                result.append('?');
            }
        }

        return result.toString();
    }
}
