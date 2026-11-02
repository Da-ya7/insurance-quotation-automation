package com.insurance.quotation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {

    /** Several different people may share one email address. */
    List<Client> findAllByEmailIgnoreCase(String email);

    Optional<Client> findByClientCode(String clientCode);

    Optional<Client> findByIdentityNumberHash(String identityNumberHash);
}
