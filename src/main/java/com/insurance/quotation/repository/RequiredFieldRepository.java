package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.RequiredField;

public interface RequiredFieldRepository extends JpaRepository<RequiredField, Long> {

    List<RequiredField> findByIsActiveTrueOrderBySortOrderAscIdAsc();

    List<RequiredField> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByFieldNameIgnoreCase(String fieldName);
}
