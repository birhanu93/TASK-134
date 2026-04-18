package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Order {
    private final String id;
    private final String customerId;
    private final Address pickup;
    private final Address dropoff;
    private final int riderCount;
    private final TimeWindow window;
    private final VehicleType vehicleType;
    private final ServicePriority priority;
    private final double miles;
    private final int durationMinutes;
    private final String couponCode;
    private final LocalDateTime createdAt;

    private String pickupFloorNotes;
    private String dropoffFloorNotes;

    private String tripId;

    private OrderState state;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime canceledAt;
    private LocalDateTime disputedAt;
    private Fare fare;
    private Money cancellationFee;
    private Money overdueFee;
    private String ownerUserId;

    public Order(String id, String customerId, Address pickup, Address dropoff, int riderCount,
                 TimeWindow window, VehicleType vehicleType, ServicePriority priority,
                 double miles, int durationMinutes, String couponCode, LocalDateTime createdAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");
        if (pickup == null) throw new IllegalArgumentException("pickup required");
        if (dropoff == null) throw new IllegalArgumentException("dropoff required");
        if (riderCount < 1 || riderCount > 6) throw new IllegalArgumentException("riderCount must be 1-6");
        if (window == null) throw new IllegalArgumentException("window required");
        if (vehicleType == null) throw new IllegalArgumentException("vehicleType required");
        if (priority == null) throw new IllegalArgumentException("priority required");
        if (miles < 0) throw new IllegalArgumentException("miles must be non-negative");
        if (durationMinutes < 0) throw new IllegalArgumentException("durationMinutes must be non-negative");
        if (createdAt == null) throw new IllegalArgumentException("createdAt required");
        this.id = id;
        this.customerId = customerId;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.riderCount = riderCount;
        this.window = window;
        this.vehicleType = vehicleType;
        this.priority = priority;
        this.miles = miles;
        this.durationMinutes = durationMinutes;
        this.couponCode = couponCode;
        this.createdAt = createdAt;
        this.state = OrderState.PENDING_MATCH;
        this.cancellationFee = Money.ZERO;
        this.overdueFee = Money.ZERO;
    }

    public String id() { return id; }
    public String customerId() { return customerId; }
    public Address pickup() { return pickup; }
    public Address dropoff() { return dropoff; }
    public int riderCount() { return riderCount; }
    public TimeWindow window() { return window; }
    public VehicleType vehicleType() { return vehicleType; }
    public ServicePriority priority() { return priority; }
    public double miles() { return miles; }
    public int durationMinutes() { return durationMinutes; }
    public String couponCode() { return couponCode; }
    public LocalDateTime createdAt() { return createdAt; }

    public String pickupFloorNotes() { return pickupFloorNotes; }
    public void setPickupFloorNotes(String s) { this.pickupFloorNotes = normalizeNotes(s); }
    public String dropoffFloorNotes() { return dropoffFloorNotes; }
    public void setDropoffFloorNotes(String s) { this.dropoffFloorNotes = normalizeNotes(s); }

    public String tripId() { return tripId; }
    public void setTripId(String id) { this.tripId = id; }

    private static String normalizeNotes(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public OrderState state() { return state; }
    public void setState(OrderState s) { this.state = s; }

    public LocalDateTime acceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime t) { this.acceptedAt = t; }
    public LocalDateTime startedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime t) { this.startedAt = t; }
    public LocalDateTime completedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime t) { this.completedAt = t; }
    public LocalDateTime canceledAt() { return canceledAt; }
    public void setCanceledAt(LocalDateTime t) { this.canceledAt = t; }
    public LocalDateTime disputedAt() { return disputedAt; }
    public void setDisputedAt(LocalDateTime t) { this.disputedAt = t; }

    public Fare fare() { return fare; }
    public void setFare(Fare f) { this.fare = f; }
    public Money cancellationFee() { return cancellationFee; }
    public void setCancellationFee(Money f) { this.cancellationFee = f; }
    public Money overdueFee() { return overdueFee; }
    public void setOverdueFee(Money f) { this.overdueFee = f; }
    public void addOverdueFee(Money f) { this.overdueFee = this.overdueFee.add(f); }
    public String ownerUserId() { return ownerUserId; }
    public void setOwnerUserId(String userId) { this.ownerUserId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
