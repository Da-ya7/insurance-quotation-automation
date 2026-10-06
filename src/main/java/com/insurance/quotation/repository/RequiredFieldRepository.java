package com.insurance.quotation.repository;

import com.insurance.quotation.entity.RequiredField;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequiredFieldRepository extends JpaRepository<RequiredField, Long> {
}