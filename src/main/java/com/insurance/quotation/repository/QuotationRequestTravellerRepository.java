package com.insurance.quotation.repository;

import com.insurance.quotation.entity.QuotationRequestTraveller;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRequestTravellerRepository
        extends JpaRepository<QuotationRequestTraveller, Long> {

    List<QuotationRequestTraveller>
        findByQuotationRequest_IdOrderByTravellerNumberAsc(
            Long quotationRequestId
        );
}