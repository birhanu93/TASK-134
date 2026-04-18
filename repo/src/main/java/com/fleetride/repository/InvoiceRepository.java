package com.fleetride.repository;

import com.fleetride.domain.Invoice;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository {
    void save(Invoice invoice);
    Optional<Invoice> findById(String id);
    List<Invoice> findByOrder(String orderId);
    List<Invoice> findByStatus(Invoice.Status status);
    List<Invoice> findAll();
}
