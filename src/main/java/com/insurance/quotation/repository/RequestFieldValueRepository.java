package com.insurance.quotation.repository;

import com.insurance.quotation.entity.RequestFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestFieldValueRepository extends JpaRepository<RequestFieldValue, Long> {

    List<RequestFieldValue> findByQuotationRequestId(Long quotationRequestId);
}