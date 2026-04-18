package com.fleetride.repository;

import com.fleetride.domain.Invoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryInvoiceRepository implements InvoiceRepository {
    private final Map<String, Invoice> store = new ConcurrentHashMap<>();

    @Override
    public void save(Invoice invoice) { store.put(invoice.id(), invoice); }

    @Override
    public Optional<Invoice> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Invoice> findByOrder(String orderId) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice i : store.values()) {
            if (i.orderId().equals(orderId)) out.add(i);
        }
        return out;
    }

    @Override
    public List<Invoice> findByStatus(Invoice.Status status) {
        List<Invoice> out = new ArrayList<>();
        for (Invoice i : store.values()) {
            if (i.status() == status) out.add(i);
        }
        return out;
    }

    @Override
    public List<Invoice> findAll() { return new ArrayList<>(store.values()); }
}
