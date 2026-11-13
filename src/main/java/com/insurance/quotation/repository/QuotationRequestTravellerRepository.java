// package com.insurance.quotation.repository;

// import java.util.List;

// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.transaction.annotation.Transactional;

// import com.insurance.quotation.entity.QuotationRequestTraveller;

// public interface QuotationRequestTravellerRepository
//         extends JpaRepository<QuotationRequestTraveller, Long> {

//     List<QuotationRequestTraveller> findByQuotationRequestIdOrderByTravellerNumber(
//             Long quotationRequestId);

//     long countByQuotationRequestId(Long quotationRequestId);

//     /** Used when a returning client re-submits the form for the same request. */
//     @Transactional
//     void deleteByQuotationRequestId(Long quotationRequestId);
// }




package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.insurance.quotation.entity.QuotationRequestTraveller;

public interface QuotationRequestTravellerRepository
        extends JpaRepository<QuotationRequestTraveller, Long> {

    List<QuotationRequestTraveller> findByQuotationRequestIdOrderByTravellerNumber(
            Long quotationRequestId);

    long countByQuotationRequestId(Long quotationRequestId);

    /**
     * Retrieves primary-traveller records, newest first.
     * Used to find previous personal details after OTP verification.
     */
    List<QuotationRequestTraveller>
            findByTravellerNumberOrderByCreatedAtDesc(Integer travellerNumber);

    /** Used when a returning client re-submits the form for the same request. */
    @Transactional
    void deleteByQuotationRequestId(Long quotationRequestId);
}
