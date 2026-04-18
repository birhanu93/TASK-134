package com.fleetride.repository;

import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> store = new ConcurrentHashMap<>();

    @Override
    public void save(Order order) {
        store.put(order.id(), order);
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Order> findByCustomer(String customerId) {
        List<Order> out = new ArrayList<>();
        for (Order o : store.values()) {
            if (o.customerId().equals(customerId)) out.add(o);
        }
        return out;
    }

    @Override
    public List<Order> findByState(OrderState state) {
        List<Order> out = new ArrayList<>();
        for (Order o : store.values()) {
            if (o.state() == state) out.add(o);
        }
        return out;
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
