package com.insurance.quotation.service.quotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import com.insurance.quotation.entity.Client;
import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import com.insurance.quotation.repository.QuotationItemRepository;
import com.insurance.quotation.repository.QuotationRepository;

@Service
public class QuotationPdfService {

    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;

    public QuotationPdfService(
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository) {

        this.quotationRepository = quotationRepository;
        this.quotationItemRepository = quotationItemRepository;
    }

    public String generatePdf(Quotation quotation) {

        String folder = "generated/quotations";
        Path folderPath = Paths.get(folder);

        try {
            Files.createDirectories(folderPath);

            String fileName = quotation.getQuotationNumber() + ".pdf";
            Path pdfPath = folderPath.resolve(fileName);

            Client client =
                    quotation.getQuotationRequest().getClient();

            List<QuotationItem> items =
                    quotationItemRepository.findByQuotation(quotation);

            try (PDDocument document = new PDDocument()) {

                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream content =
                             new PDPageContentStream(document, page)) {

                    float y = 750;

                    content.beginText();
                    content.setFont(
                            new PDType1Font(
                                    Standard14Fonts.FontName.HELVETICA_BOLD),
                            18);
                    content.newLineAtOffset(50, y);
                    content.showText("INSURANCE QUOTATION");
                    content.endText();

                    y -= 40;

                    y = writeLine(
                            content,
                            "Quotation No: "
                                    + quotation.getQuotationNumber(),
                            y);

                    y = writeLine(
                            content,
                            "Date: "
                                    + quotation.getCreatedAt()
                                            .format(DateTimeFormatter
                                                    .ofPattern("dd-MM-yyyy")),
                            y);

                    y = writeLine(
                            content,
                            "Client: " + safe(client.getName()),
                            y);

                    y = writeLine(
                            content,
                            "Email: " + safe(client.getEmail()),
                            y);

                    y = writeLine(
                            content,
                            "Policy Type: "
                                    + safe(quotation.getQuotationRequest()
                                            .getPolicyType()),
                            y);

                    y -= 20;

                    content.beginText();
                    content.setFont(
                            new PDType1Font(
                                    Standard14Fonts.FontName.HELVETICA_BOLD),
                            12);
                    content.newLineAtOffset(50, y);
                    content.showText("Quotation Details");
                    content.endText();

                    y -= 25;

                    for (QuotationItem item : items) {
                        y = writeLine(
                                content,
                                item.getDescription()
                                        + ": INR "
                                        + item.getAmount().toPlainString(),
                                y);
                    }

                    y -= 15;

                    y = writeLine(
                            content,
                            "Premium: INR "
                                    + quotation.getPremiumAmount()
                                            .toPlainString(),
                            y);

                    y = writeLine(
                            content,
                            "Tax: INR "
                                    + quotation.getTaxAmount()
                                            .toPlainString(),
                            y);

                    y = writeLine(
                            content,
                            "TOTAL: INR "
                                    + quotation.getTotalAmount()
                                            .toPlainString(),
                            y);

                    y -= 20;

                    writeLine(
                            content,
                            "Currency: " + quotation.getCurrency(),
                            y);
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

    private float writeLine(
            PDPageContentStream content,
            String text,
            float y) throws IOException {

        content.beginText();
        content.setFont(
                new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA),
                11);
        content.newLineAtOffset(50, y);
        content.showText(text);
        content.endText();

        return y - 20;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}