package com.insurance.quotation.repository;

import com.insurance.quotation.entity.IncomingEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomingEmailRepository extends JpaRepository<IncomingEmail, Long> {

    boolean existsByMessageId(String messageId);

    java.util.Optional<com.insurance.quotation.entity.IncomingEmail> findByMessageId(String messageId);

    java.util.List<com.insurance.quotation.entity.IncomingEmail> findAllByOrderByReceivedAtDesc();

    boolean existsBySenderEmailAndQuotationRequestIsNull(String senderEmail);
}
