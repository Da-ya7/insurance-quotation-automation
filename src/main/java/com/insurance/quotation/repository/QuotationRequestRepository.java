package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;

public interface QuotationRequestRepository
        extends JpaRepository<QuotationRequest, Long> {

    List<QuotationRequest> findByStatus(RequestStatus status);
}