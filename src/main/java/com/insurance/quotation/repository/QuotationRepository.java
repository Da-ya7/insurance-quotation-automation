package com.insurance.quotation.repository;

import com.insurance.quotation.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    java.util.List<Quotation> findAllByOrderByCreatedAtDesc();

    java.util.Optional<Quotation> findByQuotationRequestId(Long quotationRequestId);
}