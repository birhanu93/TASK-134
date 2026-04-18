package com.fleetride.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class InMemoryJobRunRepository implements JobRunRepository {
    private final Map<String, Record> store = new ConcurrentHashMap<>();

    @Override
    public void upsert(Record r) { store.put(r.id(), r); }

    @Override
    public Optional<Record> find(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Record> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(Record::startedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Record> findByStatus(Status status) {
        List<Record> out = new ArrayList<>();
        for (Record r : store.values()) {
            if (r.status() == status) out.add(r);
        }
        return out;
    }
}
