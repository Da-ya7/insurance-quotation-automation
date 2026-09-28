package com.insurance.quotation.repository;

import com.insurance.quotation.entity.MissingField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissingFieldRepository extends JpaRepository<MissingField, Long> {

    List<MissingField> findByQuotationRequestIdAndStatus(
            Long quotationRequestId,
            String status
    );
}