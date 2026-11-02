package com.insurance.quotation.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insurance.quotation.entity.Quotation;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findAllByOrderByCreatedAtDesc();

    /** Latest version of the quotation for a request. */
    Optional<Quotation> findFirstByQuotationRequestIdOrderByVersionNoDesc(Long quotationRequestId);

    /** Every version, newest first (quotation history). */
    List<Quotation> findByQuotationRequestIdOrderByVersionNoDesc(Long quotationRequestId);

    long countByStatus(String status);

    /** rows of [date, count] of NEW quotations (version 1) for the dashboard graph */
    @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM quotations "
            + "WHERE version_no = 1 AND created_at >= :since GROUP BY DATE(created_at)", nativeQuery = true)
    List<Object[]> countCreatedPerDay(@Param("since") LocalDateTime since);
}
