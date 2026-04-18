package com.fleetride.repository;

import com.fleetride.domain.Customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCustomerRepository implements CustomerRepository {
    private final Map<String, Customer> store = new ConcurrentHashMap<>();

    @Override
    public void save(Customer c) { store.put(c.id(), c); }

    @Override
    public Optional<Customer> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Customer> findAll() { return new ArrayList<>(store.values()); }

    @Override
    public void delete(String id) { store.remove(id); }
}
