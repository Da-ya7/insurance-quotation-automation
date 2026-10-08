package com.insurance.quotation.service.quotation;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;
import com.insurance.quotation.repository.QuotationItemRepository;
import com.insurance.quotation.repository.QuotationRepository;

@Service
public class QuotationGenerationService {

    private final QuotationCalculationService calculationService;
    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final QuotationPdfService pdfService;

    public QuotationGenerationService(
            QuotationCalculationService calculationService,
            QuotationRepository quotationRepository,
            QuotationItemRepository quotationItemRepository,
            QuotationPdfService pdfService) {

        this.calculationService = calculationService;
        this.quotationRepository = quotationRepository;
        this.quotationItemRepository = quotationItemRepository;
        this.pdfService = pdfService;
    }

    @Transactional
    public Quotation generateQuotation(QuotationRequest quotationRequest) {

        if (quotationRequest.getStatus() != RequestStatus.READY_FOR_QUOTATION) {
        throw new IllegalStateException(
              "Quotation can be generated only when request status is READY_FOR_QUOTATION");
        }

        QuotationCalculationService.CalculationResult calculation =
                calculationService.calculate();

        Quotation quotation = new Quotation();

        quotation.setQuotationRequest(quotationRequest);
        quotation.setQuotationNumber(
                "IQ-" + LocalDateTime.now().getYear()
                        + "-"
                        + String.format("%06d", quotationRequest.getId()));

        quotation.setPremiumAmount(calculation.premium());
        quotation.setTaxAmount(calculation.tax());
        quotation.setTotalAmount(calculation.total());
        quotation.setCurrency("INR");
        quotation.setStatus("DRAFT");
        quotation.setCreatedAt(LocalDateTime.now());

        Quotation savedQuotation =
                quotationRepository.save(quotation);

        for (QuotationCalculationService.LineItem item :
                calculation.items()) {

            QuotationItem quotationItem =
                    new QuotationItem();

            quotationItem.setQuotation(savedQuotation);
            quotationItem.setDescription(item.description());
            quotationItem.setAmount(item.amount());

            quotationItemRepository.save(quotationItem);
        }

        pdfService.generatePdf(savedQuotation);

        return savedQuotation;
    }
}