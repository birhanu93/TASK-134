package com.fleetride.security;

import com.fleetride.domain.Address;
import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Dispute;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.Subsidy;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.User;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.repository.OrderRepository;
import com.fleetride.service.OrderService;

import java.util.List;
import java.util.Optional;

public final class SecuredOrderService {
    private final OrderService delegate;
    private final Authorizer authz;
    private final OrderRepository orders;
    private final CustomerRepository customers;

    public SecuredOrderService(OrderService delegate, Authorizer authz, OrderRepository orders,
                               CustomerRepository customers) {
        this.delegate = delegate;
        this.authz = authz;
        this.orders = orders;
        this.customers = customers;
    }

    /**
     * @deprecated pass the {@link CustomerRepository} so customer-ownership can be enforced
     *             at order-creation time. Retained for existing tests only.
     */
    @Deprecated
    public SecuredOrderService(OrderService delegate, Authorizer authz, OrderRepository orders) {
        this(delegate, authz, orders, null);
    }

    public Order create(String customerId, Address pickup, Address dropoff, int riderCount,
                        TimeWindow window, VehicleType vehicleType, ServicePriority priority,
                        double miles, int durationMinutes, String couponCode) {
        return create(customerId, pickup, dropoff, riderCount, window, vehicleType, priority,
                miles, durationMinutes, couponCode, null, null);
    }

    public Order create(String customerId, Address pickup, Address dropoff, int riderCount,
                        TimeWindow window, VehicleType vehicleType, ServicePriority priority,
                        double miles, int durationMinutes, String couponCode,
                        String pickupFloorNotes, String dropoffFloorNotes) {
        User u = authz.require(Permission.ORDER_CREATE);
        requireVisibleCustomer(u, customerId);
        Order o = delegate.create(customerId, pickup, dropoff, riderCount, window,
                vehicleType, priority, miles, durationMinutes, couponCode,
                pickupFloorNotes, dropoffFloorNotes);
        o.setOwnerUserId(u.id());
        orders.save(o);
        return o;
    }

    /**
     * Verify the caller can actually see (and therefore use) the given customer before
     * creating an order on their behalf. This closes the gap where a dispatcher could
     * supply another dispatcher's customer id and create an order against it.
     */
    private void requireVisibleCustomer(User u, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new OrderService.OrderException("customerId required");
        }
        if (customers == null) return; // legacy constructor; best-effort only.
        Customer c = customers.findById(customerId).orElseThrow(
                () -> new OrderService.OrderException("unknown customer"));
        if (!visible(u, c.ownerUserId())) {
            throw new Authorizer.ForbiddenException(
                    "customer " + customerId + " not visible to " + u.username());
        }
    }

    public Fare quote(Order order, Coupon coupon, Subsidy subsidy) {
        User u = authz.require(Permission.ORDER_READ);
        if (!visible(u, order.ownerUserId())) {
            throw new Authorizer.ForbiddenException("order owned by another user");
        }
        return delegate.quote(order, coupon, subsidy);
    }

    public Fare quoteResolving(Order order) {
        User u = authz.require(Permission.ORDER_READ);
        if (!visible(u, order.ownerUserId())) {
            throw new Authorizer.ForbiddenException("order owned by another user");
        }
        return delegate.quoteResolving(order);
    }

    public void accept(String orderId) {
        Order o = requireOwned(orderId, Permission.ORDER_ACCEPT);
        delegate.accept(o.id());
    }

    public void start(String orderId) {
        Order o = requireOwned(orderId, Permission.ORDER_START);
        delegate.start(o.id());
    }

    public void complete(String orderId) {
        Order o = requireOwned(orderId, Permission.ORDER_COMPLETE);
        delegate.complete(o.id());
    }

    public void cancel(String orderId) {
        Order o = requireOwned(orderId, Permission.ORDER_CANCEL);
        delegate.cancel(o.id());
    }

    public Dispute openDispute(String orderId, String reason) {
        Order o = requireOwned(orderId, Permission.ORDER_DISPUTE);
        return delegate.openDispute(o.id(), reason);
    }

    public Optional<Order> find(String orderId) {
        User u = authz.require(Permission.ORDER_READ);
        Optional<Order> o = delegate.find(orderId);
        if (o.isEmpty()) return o;
        if (!visible(u, o.get().ownerUserId())) return Optional.empty();
        return o;
    }

    public List<Order> list() {
        User u = authz.require(Permission.ORDER_READ);
        List<Order> all = delegate.list();
        if (authz.canSeeAll(u.role())) return all;
        return all.stream().filter(o -> isOwner(u, o.ownerUserId())).toList();
    }

    public List<Order> listByState(OrderState state) {
        User u = authz.require(Permission.ORDER_READ);
        List<Order> all = delegate.listByState(state);
        if (authz.canSeeAll(u.role())) return all;
        return all.stream().filter(o -> isOwner(u, o.ownerUserId())).toList();
    }

    public int autoCancelStale() {
        authz.require(Permission.SCHEDULER_RUN);
        return delegate.autoCancelStale();
    }

    private Order requireOwned(String orderId, Permission p) {
        User u = authz.require(p);
        Order o = delegate.find(orderId).orElseThrow(
                () -> new OrderService.OrderException("unknown order"));
        if (u.role() == Role.ADMINISTRATOR) return o;
        if (!isOwner(u, o.ownerUserId())) {
            throw new Authorizer.ForbiddenException("order owned by another user");
        }
        return o;
    }

    private boolean visible(User u, String ownerUserId) {
        if (authz.canSeeAll(u.role())) return true;
        return isOwner(u, ownerUserId);
    }

    private boolean isOwner(User u, String ownerUserId) {
        return ownerUserId != null && ownerUserId.equals(u.id());
    }
}
