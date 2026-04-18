package com.fleetride.repository;

import com.fleetride.domain.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> findById(String id);
    List<Payment> findByOrder(String orderId);
    List<Payment> findAll();
}
