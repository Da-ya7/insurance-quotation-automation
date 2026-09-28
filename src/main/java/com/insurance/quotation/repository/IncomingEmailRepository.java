package com.insurance.quotation.repository;

import com.insurance.quotation.entity.IncomingEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomingEmailRepository extends JpaRepository<IncomingEmail, Long> {

    boolean existsByMessageId(String messageId);
}