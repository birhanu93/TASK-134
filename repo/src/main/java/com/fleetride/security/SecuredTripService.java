package com.fleetride.security;

import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.User;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.repository.OrderRepository;
import com.fleetride.repository.TripRepository;
import com.fleetride.service.OrderService;
import com.fleetride.service.TripService;

import java.util.List;
import java.util.Optional;

public final class SecuredTripService {
    private final TripService delegate;
    private final Authorizer authz;
    private final TripRepository trips;
    private final CustomerRepository customers;
    private final OrderRepository orders;

    public SecuredTripService(TripService delegate, Authorizer authz, TripRepository trips,
                              CustomerRepository customers, OrderRepository orders) {
        this.delegate = delegate;
        this.authz = authz;
        this.trips = trips;
        this.customers = customers;
        this.orders = orders;
    }

    /**
     * @deprecated pass the customer and order repositories so object-level ownership
     *             on rider orders (and on attached standalone orders) is enforced.
     *             Retained for existing tests only.
     */
    @Deprecated
    public SecuredTripService(TripService delegate, Authorizer authz, TripRepository trips) {
        this(delegate, authz, trips, null, null);
    }

    public Trip create(VehicleType vehicleType, int capacity, TimeWindow window,
                       String driverPlaceholder) {
        User u = authz.require(Permission.TRIP_CREATE);
        Trip t = delegate.create(vehicleType, capacity, window, driverPlaceholder);
        t.setOwnerUserId(u.id());
        trips.save(t);
        return t;
    }

    public Order addRiderOrder(String tripId, String customerId, Address pickup, Address dropoff,
                               int riderCount, ServicePriority priority,
                               double miles, int durationMinutes, String couponCode,
                               String pickupFloorNotes, String dropoffFloorNotes) {
        User u = authz.require(Permission.TRIP_MANAGE);
        Trip t = requireVisibleTrip(u, tripId);
        requireVisibleCustomer(u, customerId);
        return delegate.addRiderOrder(t.id(), customerId, pickup, dropoff, riderCount,
                priority, miles, durationMinutes, couponCode,
                pickupFloorNotes, dropoffFloorNotes);
    }

    public void attachExistingOrder(String tripId, String orderId) {
        User u = authz.require(Permission.TRIP_MANAGE);
        Trip t = requireVisibleTrip(u, tripId);
        requireVisibleOrder(u, orderId);
        delegate.attachExistingOrder(t.id(), orderId);
    }

    public void dispatch(String tripId) {
        requireOwned(tripId, Permission.TRIP_MANAGE);
        delegate.dispatch(tripId);
    }

    public void close(String tripId) {
        requireOwned(tripId, Permission.TRIP_MANAGE);
        delegate.close(tripId);
    }

    public void cancel(String tripId, String reason) {
        requireOwned(tripId, Permission.TRIP_MANAGE);
        delegate.cancel(tripId, reason);
    }

    public Optional<Trip> find(String id) {
        User u = authz.require(Permission.TRIP_READ);
        Optional<Trip> t = delegate.find(id);
        if (t.isEmpty()) return t;
        if (!visible(u, t.get().ownerUserId())) return Optional.empty();
        return t;
    }

    public List<Trip> list() {
        User u = authz.require(Permission.TRIP_READ);
        List<Trip> all = delegate.list();
        if (authz.canSeeAll(u.role())) return all;
        return all.stream().filter(t -> isOwner(u, t.ownerUserId())).toList();
    }

    public List<Trip> listByStatus(TripStatus status) {
        User u = authz.require(Permission.TRIP_READ);
        List<Trip> all = delegate.listByStatus(status);
        if (authz.canSeeAll(u.role())) return all;
        return all.stream().filter(t -> isOwner(u, t.ownerUserId())).toList();
    }

    public List<Order> riderOrders(String tripId) {
        Trip t = requireOwned(tripId, Permission.TRIP_READ);
        return delegate.riderOrders(t.id());
    }

    public Money totalTripFare(String tripId) {
        Trip t = requireOwned(tripId, Permission.TRIP_READ);
        return delegate.totalTripFare(t.id());
    }

    public int riderSeatsUsed(String tripId) {
        Trip t = requireOwned(tripId, Permission.TRIP_READ);
        return delegate.riderSeatsUsed(t.id());
    }

    private Trip requireOwned(String tripId, Permission p) {
        User u = authz.require(p);
        return requireVisibleTrip(u, tripId);
    }

    private Trip requireVisibleTrip(User u, String tripId) {
        Trip t = delegate.find(tripId).orElseThrow(
                () -> new TripService.TripException("unknown trip " + tripId));
        if (u.role() == Role.ADMINISTRATOR) return t;
        if (!visible(u, t.ownerUserId())) {
            throw new Authorizer.ForbiddenException("trip owned by another user");
        }
        return t;
    }

    private void requireVisibleCustomer(User u, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new TripService.TripException("customerId required");
        }
        if (customers == null) return;
        Customer c = customers.findById(customerId).orElseThrow(
                () -> new TripService.TripException("unknown customer " + customerId));
        if (!visible(u, c.ownerUserId())) {
            throw new Authorizer.ForbiddenException(
                    "customer " + customerId + " not visible to " + u.username());
        }
    }

    private void requireVisibleOrder(User u, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new TripService.TripException("orderId required");
        }
        if (orders == null) return;
        Order o = orders.findById(orderId).orElseThrow(
                () -> new TripService.TripException("unknown order " + orderId));
        if (!visible(u, o.ownerUserId())) {
            throw new Authorizer.ForbiddenException(
                    "order " + orderId + " not visible to " + u.username());
        }
    }

    private boolean visible(User u, String ownerUserId) {
        if (authz.canSeeAll(u.role())) return true;
        return isOwner(u, ownerUserId);
    }

    private boolean isOwner(User u, String ownerUserId) {
        return ownerUserId != null && ownerUserId.equals(u.id());
    }
}
