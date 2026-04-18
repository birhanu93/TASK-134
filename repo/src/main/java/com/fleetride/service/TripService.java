package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.OrderRepository;
import com.fleetride.repository.TripRepository;

import java.util.List;
import java.util.Optional;

/**
 * Coordinates trips: a trip is a carpool container that groups one or more rider
 * orders dispatched together. Orders still carry their own state machine; the Trip
 * tracks the operational phase (planning/dispatched/closed/canceled) and the shared
 * scheduled window, vehicle type, and capacity.
 */
public final class TripService {
    public static final class TripException extends RuntimeException {
        public TripException(String msg) { super(msg); }
    }

    private final TripRepository trips;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditService audit;

    public TripService(TripRepository trips, OrderRepository orders, OrderService orderService,
                       IdGenerator ids, Clock clock, AuditService audit) {
        this.trips = trips;
        this.orders = orders;
        this.orderService = orderService;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public Trip create(VehicleType vehicleType, int capacity, TimeWindow window,
                       String driverPlaceholder) {
        Trip t = new Trip(ids.next(), vehicleType, capacity, window, clock.now());
        t.setDriverPlaceholder(driverPlaceholder);
        trips.save(t);
        audit.record("system", "TRIP_CREATE", t.id(),
                vehicleType + " cap=" + capacity);
        return t;
    }

    /**
     * Create a rider order that joins the given trip. Delegates to OrderService so the
     * usual order validation (customer, quota, state) and side effects (ride-count
     * increment, audit) happen exactly once; the resulting Order is then bound to the
     * Trip.
     */
    public Order addRiderOrder(String tripId, String customerId, Address pickup, Address dropoff,
                               int riderCount, ServicePriority priority,
                               double miles, int durationMinutes, String couponCode,
                               String pickupFloorNotes, String dropoffFloorNotes) {
        Trip trip = requireOpen(tripId);
        checkRiderCapacity(trip, riderCount);
        Order order = orderService.create(customerId, pickup, dropoff, riderCount,
                trip.scheduledWindow(), trip.vehicleType(), priority,
                miles, durationMinutes, couponCode, pickupFloorNotes, dropoffFloorNotes);
        order.setTripId(trip.id());
        orders.save(order);
        audit.record("system", "TRIP_ADD_ORDER", trip.id(), order.id());
        return order;
    }

    /**
     * Attach an existing order to a planning trip. The order's vehicle type and window
     * must match the trip — otherwise the carpool grouping would mean nothing.
     */
    public void attachExistingOrder(String tripId, String orderId) {
        Trip trip = requireOpen(tripId);
        Order o = orders.findById(orderId).orElseThrow(
                () -> new TripException("unknown order " + orderId));
        if (o.tripId() != null) {
            throw new TripException("order already on trip " + o.tripId());
        }
        if (o.vehicleType() != trip.vehicleType()) {
            throw new TripException("order vehicle " + o.vehicleType()
                    + " doesn't match trip " + trip.vehicleType());
        }
        if (!o.window().equals(trip.scheduledWindow())) {
            throw new TripException("order window doesn't match trip window");
        }
        checkRiderCapacity(trip, o.riderCount());
        o.setTripId(trip.id());
        orders.save(o);
        audit.record("system", "TRIP_ATTACH_ORDER", trip.id(), orderId);
    }

    public void dispatch(String tripId) {
        Trip trip = requireOpen(tripId);
        List<Order> riders = orders.findByTrip(tripId);
        if (riders.isEmpty()) {
            throw new TripException("cannot dispatch an empty trip");
        }
        trip.setStatus(TripStatus.DISPATCHED);
        trip.setDispatchedAt(clock.now());
        trips.save(trip);
        audit.record("system", "TRIP_DISPATCH", tripId, "riders=" + riders.size());
    }

    public void close(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(
                () -> new TripException("unknown trip " + tripId));
        List<Order> riders = orders.findByTrip(tripId);
        for (Order o : riders) {
            if (!isTerminal(o.state())) {
                throw new TripException("rider order " + o.id()
                        + " not terminal (state=" + o.state() + ")");
            }
        }
        trip.setStatus(TripStatus.CLOSED);
        trip.setClosedAt(clock.now());
        trips.save(trip);
        audit.record("system", "TRIP_CLOSE", tripId, "riders=" + riders.size());
    }

    public void cancel(String tripId, String reason) {
        Trip trip = trips.findById(tripId).orElseThrow(
                () -> new TripException("unknown trip " + tripId));
        if (trip.status() == TripStatus.CLOSED) {
            throw new TripException("cannot cancel a closed trip");
        }
        trip.setStatus(TripStatus.CANCELED);
        trip.setCanceledAt(clock.now());
        trips.save(trip);
        // Cancel non-terminal rider orders too so the trip cancel is coherent.
        for (Order o : orders.findByTrip(tripId)) {
            if (!isTerminal(o.state())) {
                try {
                    orderService.cancel(o.id());
                } catch (RuntimeException ignored) {
                    // Best-effort cascade — the trip itself is already canceled.
                }
            }
        }
        audit.record("system", "TRIP_CANCEL", tripId, reason);
    }

    public Optional<Trip> find(String id) { return trips.findById(id); }
    public List<Trip> list() { return trips.findAll(); }
    public List<Trip> listByStatus(TripStatus s) { return trips.findByStatus(s); }
    public List<Order> riderOrders(String tripId) { return orders.findByTrip(tripId); }

    public Money totalTripFare(String tripId) {
        Money sum = Money.ZERO;
        for (Order o : orders.findByTrip(tripId)) {
            if (o.fare() != null) sum = sum.add(o.fare().total());
        }
        return sum;
    }

    public int riderSeatsUsed(String tripId) {
        int used = 0;
        for (Order o : orders.findByTrip(tripId)) {
            if (o.state() != OrderState.CANCELED) used += o.riderCount();
        }
        return used;
    }

    private Trip requireOpen(String tripId) {
        Trip trip = trips.findById(tripId).orElseThrow(
                () -> new TripException("unknown trip " + tripId));
        if (trip.status() != TripStatus.PLANNING) {
            throw new TripException("trip " + tripId + " is " + trip.status()
                    + " — only PLANNING trips accept new riders");
        }
        return trip;
    }

    private void checkRiderCapacity(Trip trip, int riderCount) {
        int used = riderSeatsUsed(trip.id());
        if (used + riderCount > trip.capacity()) {
            throw new TripException("trip " + trip.id() + " capacity " + trip.capacity()
                    + " would be exceeded (used=" + used + ", adding=" + riderCount + ")");
        }
    }

    private static boolean isTerminal(OrderState s) {
        return s == OrderState.COMPLETED || s == OrderState.CANCELED || s == OrderState.IN_DISPUTE;
    }
}
