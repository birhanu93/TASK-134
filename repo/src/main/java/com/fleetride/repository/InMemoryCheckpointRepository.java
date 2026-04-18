package com.fleetride.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCheckpointRepository implements CheckpointRepository {
    private final Map<String, Record> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(Record r) { store.put(r.operationId(), r); }

    @Override
    public Optional<Record> find(String operationId) {
        return Optional.ofNullable(store.get(operationId));
    }

    @Override
    public List<Record> findPending() {
        List<Record> out = new ArrayList<>();
        for (Record r : store.values()) {
            if (r.status() == Status.PENDING) out.add(r);
        }
        return out;
    }

    @Override
    public void delete(String operationId) { store.remove(operationId); }
}
