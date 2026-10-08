package com.insurance.quotation.repository;

import com.insurance.quotation.entity.Quotation;
import com.insurance.quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {

    List<QuotationItem> findByQuotation(Quotation quotation);
}