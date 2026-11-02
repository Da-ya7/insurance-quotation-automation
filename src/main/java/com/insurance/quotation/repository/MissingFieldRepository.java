package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.MissingField;

public interface MissingFieldRepository extends JpaRepository<MissingField, Long> {

    List<MissingField> findByQuotationRequestIdAndStatus(Long quotationRequestId, String status);

    List<MissingField> findByQuotationRequestIdOrderByCreatedAtDesc(Long quotationRequestId);

    List<MissingField> findByQuotationRequestIdAndRequiredFieldIdAndStatus(
            Long quotationRequestId, Long requiredFieldId, String status);

    List<MissingField> findByStatusOrderByCreatedAtDesc(String status);

    List<MissingField> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
