
package com.insurance.quotation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.insurance.quotation.entity.Quotation;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    // Retrieve all quotations, newest first
    List<Quotation> findAllByOrderByCreatedAtDesc();

    // Latest version of the quotation for a request
    Optional<Quotation> findFirstByQuotationRequestIdOrderByVersionNoDesc(
            Long quotationRequestId);

    // Every version, newest first (quotation history)
    List<Quotation> findByQuotationRequestIdOrderByVersionNoDesc(
            Long quotationRequestId);

    // Count quotations by existing quotation status
    long countByStatus(String status);

    // Dashboard graph: count new quotations created per day
    @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM quotations "
            + "WHERE version_no = 1 AND created_at >= :since GROUP BY DATE(created_at)",
            nativeQuery = true)
    List<Object[]> countCreatedPerDay(@Param("since") LocalDateTime since);

    // Find quotations by their secure response token
    Optional<Quotation> findByResponseToken(String responseToken);

    // Find quotations awaiting a client response that haven't been closed
    List<Quotation> findByResponseStatusAndClosedAtIsNull(
            String responseStatus);

    // Existing finder retained for compatibility with other application logic
    List<Quotation> findByResponseStatusAndClosedAtIsNullAndSentAtIsNotNull(
            String responseStatus);

    // Scheduler-only query:
    // Select only successfully sent quotations with a valid PDF path,
    // awaiting a client response and not already closed.
    @Query("""
            SELECT q
            FROM Quotation q
            WHERE UPPER(q.status) = 'SENT'
              AND UPPER(q.responseStatus) = UPPER(:responseStatus)
              AND q.closedAt IS NULL
              AND q.sentAt IS NOT NULL
              AND q.pdfPath IS NOT NULL
              AND TRIM(q.pdfPath) <> ''
            """)
    List<Quotation> findEligibleForResponseReminder(
            @Param("responseStatus") String responseStatus);

    // Lock a quotation while processing a client's response.
    // Prevents simultaneous Accept/Reject requests from changing the decision.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM Quotation q WHERE q.responseToken = :responseToken")
    Optional<Quotation> findByResponseTokenForUpdate(
            @Param("responseToken") String responseToken);
}
