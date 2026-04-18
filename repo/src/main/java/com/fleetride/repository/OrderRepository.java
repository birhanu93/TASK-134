package com.fleetride.repository;

import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(String id);
    List<Order> findByCustomer(String customerId);
    List<Order> findByState(OrderState state);
    List<Order> findAll();
    void delete(String id);

    default List<Order> findByTrip(String tripId) {
        if (tripId == null) return List.of();
        List<Order> out = new java.util.ArrayList<>();
        for (Order o : findAll()) {
            if (tripId.equals(o.tripId())) out.add(o);
        }
        return out;
    }
}
