package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A Trip groups one or more rider orders that are dispatched together as a carpool.
 * Orders still carry their own customer, pickup/drop-off, state, and fare; the Trip is
 * the operational container that coordinates them — capacity, driver, vehicle, and the
 * scheduled pickup window that rider orders must fall within.
 */
public final class Trip {
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 6;

    private final String id;
    private final VehicleType vehicleType;
    private final int capacity;
    private final TimeWindow scheduledWindow;
    private final LocalDateTime createdAt;

    private String driverPlaceholder;
    private TripStatus status;
    private String ownerUserId;
    private LocalDateTime dispatchedAt;
    private LocalDateTime closedAt;
    private LocalDateTime canceledAt;

    public Trip(String id, VehicleType vehicleType, int capacity,
                TimeWindow scheduledWindow, LocalDateTime createdAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (vehicleType == null) throw new IllegalArgumentException("vehicleType required");
        if (capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "capacity must be in [" + MIN_CAPACITY + ", " + MAX_CAPACITY + "]");
        }
        if (scheduledWindow == null) throw new IllegalArgumentException("scheduledWindow required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        this.id = id;
        this.vehicleType = vehicleType;
        this.capacity = capacity;
        this.scheduledWindow = scheduledWindow;
        this.createdAt = createdAt;
        this.status = TripStatus.PLANNING;
    }

    public String id() { return id; }
    public VehicleType vehicleType() { return vehicleType; }
    public int capacity() { return capacity; }
    public TimeWindow scheduledWindow() { return scheduledWindow; }
    public LocalDateTime createdAt() { return createdAt; }

    public String driverPlaceholder() { return driverPlaceholder; }
    public void setDriverPlaceholder(String s) { this.driverPlaceholder = s; }

    public TripStatus status() { return status; }
    public void setStatus(TripStatus s) { this.status = s; }

    public String ownerUserId() { return ownerUserId; }
    public void setOwnerUserId(String s) { this.ownerUserId = s; }

    public LocalDateTime dispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime t) { this.dispatchedAt = t; }

    public LocalDateTime closedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime t) { this.closedAt = t; }

    public LocalDateTime canceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime t) { this.canceledAt = t; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trip t)) return false;
        return Objects.equals(id, t.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
