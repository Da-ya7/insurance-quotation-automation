package com.insurance.quotation.repository;

import com.insurance.quotation.entity.QuotationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRequestRepository extends JpaRepository<QuotationRequest, Long> {

    List<QuotationRequest> findByStatus(String status);
}