package com.fleetride.repository;

import com.fleetride.domain.Subsidy;

import java.util.List;
import java.util.Optional;

public interface SubsidyRepository {
    void save(Subsidy subsidy);
    Optional<Subsidy> findByCustomer(String customerId);
    List<Subsidy> findAll();
    void delete(String customerId);
}
