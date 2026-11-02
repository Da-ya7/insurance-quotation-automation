package com.insurance.quotation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.insurance.quotation.entity.ClientVersion;

public interface ClientVersionRepository extends JpaRepository<ClientVersion, Long> {

    List<ClientVersion> findByClientIdOrderByVersionNoDesc(Long clientId);
}
