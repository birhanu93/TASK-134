package com.fleetride.repository;

import com.fleetride.domain.Subsidy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySubsidyRepository implements SubsidyRepository {
    private final Map<String, Subsidy> store = new ConcurrentHashMap<>();

    @Override
    public void save(Subsidy s) { store.put(s.customerId(), s); }

    @Override
    public Optional<Subsidy> findByCustomer(String customerId) { return Optional.ofNullable(store.get(customerId)); }

    @Override
    public List<Subsidy> findAll() { return new ArrayList<>(store.values()); }

    @Override
    public void delete(String customerId) { store.remove(customerId); }
}
