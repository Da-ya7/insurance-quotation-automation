// package com.insurance.quotation.repository;

// import java.time.LocalDateTime;
// import java.util.Collection;
// import java.util.List;
// import java.util.Optional;

// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.repository.query.Param;

// import com.insurance.quotation.entity.QuotationRequest;
// import com.insurance.quotation.entity.enums.RequestStatus;

// public interface QuotationRequestRepository
//         extends JpaRepository<QuotationRequest, Long> {

//     List<QuotationRequest> findByStatus(RequestStatus status);

//     List<QuotationRequest> findByStatusIn(Collection<RequestStatus> statuses);

//     List<QuotationRequest> findAllByOrderByCreatedAtDesc();

//     long countByClientId(Long clientId);

//     long countByStatusIn(Collection<RequestStatus> statuses);

//     Optional<QuotationRequest> findFirstByClientIdAndStatusInOrderByIdDesc(
//             Long clientId, Collection<RequestStatus> statuses);

//     /** rows of [date, count] for the dashboard graph */
//     @Query(value = "SELECT DATE(created_at) AS d, COUNT(*) AS c FROM quotation_requests "
//             + "WHERE created_at >= :since GROUP BY DATE(created_at)", nativeQuery = true)
//     List<Object[]> countCreatedPerDay(@Param("since") LocalDateTime since);
// }




package com.insurance.quotation.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insurance.quotation.entity.QuotationRequest;
import com.insurance.quotation.entity.enums.RequestStatus;

public interface QuotationRequestRepository
        extends JpaRepository<QuotationRequest, Long> {

    List<QuotationRequest> findByStatus(RequestStatus status);

    List<QuotationRequest> findByStatusIn(Collection<RequestStatus> statuses);

    List<QuotationRequest> findAllByOrderByCreatedAtDesc();

    // Retrieve all quotation requests belonging to a particular client.
    // Newest requests will appear first.
    List<QuotationRequest> findByClient_IdOrderByCreatedAtDesc(Long clientId);

    long countByClientId(Long clientId);

    long countByStatusIn(Collection<RequestStatus> statuses);

    Optional<QuotationRequest> findFirstByClientIdAndStatusInOrderByIdDesc(
            Long clientId, Collection<RequestStatus> statuses);

    /** Rows of [date, count] for the dashboard graph. */
    @Query(
        value = "SELECT DATE(created_at) AS d, COUNT(*) AS c "
              + "FROM quotation_requests "
              + "WHERE created_at >= :since "
              + "GROUP BY DATE(created_at)",
        nativeQuery = true
    )
    List<Object[]> countCreatedPerDay(@Param("since") LocalDateTime since);
}
