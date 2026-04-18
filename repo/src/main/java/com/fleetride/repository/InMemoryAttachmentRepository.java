package com.fleetride.repository;

import com.fleetride.domain.Attachment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAttachmentRepository implements AttachmentRepository {
    private final Map<String, Attachment> store = new ConcurrentHashMap<>();

    @Override
    public void save(Attachment a) { store.put(a.id(), a); }

    @Override
    public Optional<Attachment> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Attachment> findByOrder(String orderId) {
        List<Attachment> out = new ArrayList<>();
        for (Attachment a : store.values()) {
            if (a.orderId().equals(orderId)) out.add(a);
        }
        return out;
    }

    @Override
    public void delete(String id) { store.remove(id); }
}
