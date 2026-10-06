package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.MissingField;
import com.insurance.quotation.entity.enums.MissingFieldStatus;

public interface MissingFieldRepository extends JpaRepository<MissingField, Long> {

    List<MissingField> findByQuotationRequestIdAndStatus(
            Long quotationRequestId,
            MissingFieldStatus status);
}