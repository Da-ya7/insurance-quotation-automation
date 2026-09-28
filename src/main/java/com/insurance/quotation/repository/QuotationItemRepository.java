package com.insurance.quotation.repository;

import com.insurance.quotation.entity.QuotationItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationItemRepository extends JpaRepository<QuotationItem, Long> {
}