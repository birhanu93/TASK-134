package com.fleetride.security;

import com.fleetride.domain.Customer;
import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.repository.CustomerRepository;
import com.fleetride.service.CustomerService;

import java.util.List;
import java.util.Optional;

public final class SecuredCustomerService {
    private final CustomerService delegate;
    private final Authorizer authz;
    private final CustomerRepository customers;

    public SecuredCustomerService(CustomerService delegate, Authorizer authz, CustomerRepository customers) {
        this.delegate = delegate;
        this.authz = authz;
        this.customers = customers;
    }

    public Customer create(String name, String phone, String rawPaymentToken) {
        User u = authz.require(Permission.CUSTOMER_CREATE);
        Customer c = delegate.create(name, phone, rawPaymentToken);
        c.setOwnerUserId(u.id());
        customers.save(c);
        return c;
    }

    public Optional<Customer> find(String id) {
        User u = authz.require(Permission.CUSTOMER_READ);
        Optional<Customer> c = delegate.find(id);
        if (c.isEmpty()) return c;
        if (!visible(u, c.get().ownerUserId())) return Optional.empty();
        return c;
    }

    public List<Customer> list() {
        User u = authz.require(Permission.CUSTOMER_READ);
        List<Customer> all = delegate.list();
        if (authz.canSeeAll(u.role())) return all;
        return all.stream().filter(c -> isOwner(u, c.ownerUserId())).toList();
    }

    public String maskedPaymentToken(Customer c) {
        if (c == null) throw new IllegalArgumentException("customer required");
        User u = authz.require(Permission.CUSTOMER_READ);
        // Always resolve the current owner from the repository — the caller may have
        // passed in a detached snapshot whose ownerUserId field is stale or forged.
        Customer stored = customers.findById(c.id()).orElseThrow(
                () -> new IllegalArgumentException("unknown customer"));
        if (!visible(u, stored.ownerUserId())) {
            throw new Authorizer.ForbiddenException("customer owned by another user");
        }
        return delegate.maskedPaymentToken(stored);
    }

    public void delete(String id) {
        User u = authz.require(Permission.CUSTOMER_DELETE);
        Customer c = delegate.find(id).orElseThrow(
                () -> new IllegalArgumentException("unknown customer"));
        if (u.role() == Role.ADMINISTRATOR) {
            delegate.delete(id);
            return;
        }
        if (!isOwner(u, c.ownerUserId())) {
            throw new Authorizer.ForbiddenException("customer owned by another user");
        }
        delegate.delete(id);
    }

    private boolean visible(User u, String ownerUserId) {
        if (authz.canSeeAll(u.role())) return true;
        return isOwner(u, ownerUserId);
    }

    private boolean isOwner(User u, String ownerUserId) {
        return ownerUserId != null && ownerUserId.equals(u.id());
    }
}
