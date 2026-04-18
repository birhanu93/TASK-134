package com.fleetride.repository;

import com.fleetride.domain.Dispute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDisputeRepository implements DisputeRepository {
    private final Map<String, Dispute> store = new ConcurrentHashMap<>();

    @Override
    public void save(Dispute d) { store.put(d.id(), d); }

    @Override
    public Optional<Dispute> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Dispute> findByOrder(String orderId) {
        List<Dispute> out = new ArrayList<>();
        for (Dispute d : store.values()) {
            if (d.orderId().equals(orderId)) out.add(d);
        }
        return out;
    }

    @Override
    public List<Dispute> findAll() { return new ArrayList<>(store.values()); }
}
