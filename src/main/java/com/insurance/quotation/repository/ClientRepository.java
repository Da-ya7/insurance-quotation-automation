// package com.insurance.quotation.repository;

// import java.util.List;
// import java.util.Optional;

// import org.springframework.data.jpa.repository.JpaRepository;

// import com.insurance.quotation.entity.Client;

// public interface ClientRepository extends JpaRepository<Client, Long> {

//     /** Several different people may share one email address. */
//     List<Client> findAllByEmailIgnoreCase(String email);

//     Optional<Client> findByClientCode(String clientCode);

//     Optional<Client> findByIdentityNumberHash(String identityNumberHash);
// }





package com.insurance.quotation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.insurance.quotation.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /**
     * Several different people may share one email address.
     */
    List<Client> findAllByEmailIgnoreCase(String email);

    Optional<Client> findByClientCode(String clientCode);

    /**
     * Find a client using the protected identity hash.
     */
    Optional<Client> findByIdentityNumberHash(String identityNumberHash);

    /**
     * Retrieve clients that have a phone number.
     */
    List<Client> findAllByPhoneIsNotNull();

    /**
     * Find clients using a normalized phone number.
     *
     * Supports:
     * 6345634563
     * 916345634563
     * +91 6345634563
     * +91-6345634563
     *
     * The input phone should be normalized to 10 digits.
     */
    @Query(value = """
            SELECT *
            FROM clients
            WHERE REGEXP_REPLACE(COALESCE(phone, ''), '[^0-9]', '') = :phone
               OR REGEXP_REPLACE(COALESCE(phone, ''), '[^0-9]', '') = CONCAT('91', :phone)
            """, nativeQuery = true)
    List<Client> findAllByNormalizedPhone(
            @Param("phone") String phone);
}
