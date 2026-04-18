package com.fleetride.repository;

import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;

import java.util.List;
import java.util.Optional;

public interface TripRepository {
    void save(Trip trip);
    Optional<Trip> findById(String id);
    List<Trip> findAll();
    List<Trip> findByStatus(TripStatus status);
    List<Trip> findByOwner(String ownerUserId);
    void delete(String id);
}
