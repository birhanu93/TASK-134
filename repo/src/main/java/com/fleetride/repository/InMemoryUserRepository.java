package com.fleetride.repository;

import com.fleetride.domain.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> byId = new ConcurrentHashMap<>();

    @Override
    public void save(User u) { byId.put(u.id(), u); }

    @Override
    public Optional<User> findById(String id) { return Optional.ofNullable(byId.get(id)); }

    @Override
    public Optional<User> findByUsername(String username) {
        for (User u : byId.values()) {
            if (u.username().equals(username)) return Optional.of(u);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll() { return new ArrayList<>(byId.values()); }
}
