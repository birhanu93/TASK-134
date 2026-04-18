package com.fleetride.repository;

import com.fleetride.domain.Payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<String, Payment> store = new ConcurrentHashMap<>();

    @Override
    public void save(Payment p) { store.put(p.id(), p); }

    @Override
    public Optional<Payment> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Payment> findByOrder(String orderId) {
        List<Payment> out = new ArrayList<>();
        for (Payment p : store.values()) {
            if (p.orderId().equals(orderId)) out.add(p);
        }
        return out;
    }

    @Override
    public List<Payment> findAll() { return new ArrayList<>(store.values()); }
}
