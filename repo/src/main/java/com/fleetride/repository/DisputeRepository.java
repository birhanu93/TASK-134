package com.fleetride.repository;

import com.fleetride.domain.Dispute;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository {
    void save(Dispute dispute);
    Optional<Dispute> findById(String id);
    List<Dispute> findByOrder(String orderId);
    List<Dispute> findAll();
}
