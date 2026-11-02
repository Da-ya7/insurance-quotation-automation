package com.insurance.quotation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.insurance.quotation.entity.IncomingEmail;

public interface IncomingEmailRepository extends JpaRepository<IncomingEmail, Long> {

    boolean existsByMessageId(String messageId);

    Optional<IncomingEmail> findByMessageId(String messageId);

    List<IncomingEmail> findAllByOrderByReceivedAtDesc();

    List<IncomingEmail> findByQuotationRequestIdOrderByReceivedAtDesc(Long requestId);

    long countByProcessingStatus(String processingStatus);

    long countByEmailKind(String emailKind);

    /** Has this sender already been sent a form link recently? (avoids spamming) */
    boolean existsBySenderEmailAndEmailKindAndQuotationRequestIsNullAndCreatedAtAfter(
            String senderEmail, String emailKind, LocalDateTime after);

    /** Emails whose request already has a quotation. */
    @Query("select count(e) from IncomingEmail e where e.quotationRequest is not null and exists "
            + "(select 1 from Quotation q where q.quotationRequest = e.quotationRequest)")
    long countEmailsWithQuotation();
}
