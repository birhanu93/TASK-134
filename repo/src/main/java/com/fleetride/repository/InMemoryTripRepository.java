package com.fleetride.repository;

import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTripRepository implements TripRepository {
    private final Map<String, Trip> store = new ConcurrentHashMap<>();

    @Override
    public void save(Trip trip) { store.put(trip.id(), trip); }

    @Override
    public Optional<Trip> findById(String id) { return Optional.ofNullable(store.get(id)); }

    @Override
    public List<Trip> findAll() { return new ArrayList<>(store.values()); }

    @Override
    public List<Trip> findByStatus(TripStatus status) {
        List<Trip> out = new ArrayList<>();
        for (Trip t : store.values()) if (t.status() == status) out.add(t);
        return out;
    }

    @Override
    public List<Trip> findByOwner(String ownerUserId) {
        List<Trip> out = new ArrayList<>();
        for (Trip t : store.values()) {
            if (ownerUserId != null && ownerUserId.equals(t.ownerUserId())) out.add(t);
        }
        return out;
    }

    @Override
    public void delete(String id) { store.remove(id); }
}
