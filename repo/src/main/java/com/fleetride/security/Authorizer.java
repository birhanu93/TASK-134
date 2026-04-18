package com.fleetride.security;

import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.service.AuthService;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class Authorizer {
    public static final class ForbiddenException extends RuntimeException {
        public ForbiddenException(String msg) { super(msg); }
    }

    private final AuthService auth;
    private final Map<Permission, Set<Role>> matrix;

    public Authorizer(AuthService auth) {
        this.auth = auth;
        this.matrix = defaultMatrix();
    }

    public Authorizer(AuthService auth, Map<Permission, Set<Role>> matrix) {
        this.auth = auth;
        this.matrix = new EnumMap<>(matrix);
    }

    public User require(Permission permission) {
        User user = auth.currentUser()
                .orElseThrow(() -> new ForbiddenException("not authenticated"));
        if (auth.isLocked()) throw new ForbiddenException("session locked");
        Set<Role> allowed = matrix.getOrDefault(permission, EnumSet.noneOf(Role.class));
        if (!allowed.contains(user.role())) {
            throw new ForbiddenException("role " + user.role() + " not allowed for " + permission);
        }
        return user;
    }

    public void requireOwnership(Permission permission, String resourceOwnerUserId) {
        User user = require(permission);
        if (user.role() == Role.ADMINISTRATOR) return;
        if (resourceOwnerUserId == null) return;
        if (!resourceOwnerUserId.equals(user.id())) {
            throw new ForbiddenException("resource owned by another user");
        }
    }

    public boolean canSeeAll(Role role) {
        return role == Role.ADMINISTRATOR || role == Role.FINANCE_CLERK;
    }

    private static Map<Permission, Set<Role>> defaultMatrix() {
        EnumMap<Permission, Set<Role>> m = new EnumMap<>(Permission.class);
        Set<Role> all = EnumSet.allOf(Role.class);
        Set<Role> adminOnly = EnumSet.of(Role.ADMINISTRATOR);
        Set<Role> dispAdmin = EnumSet.of(Role.DISPATCHER, Role.ADMINISTRATOR);
        Set<Role> finAdmin = EnumSet.of(Role.FINANCE_CLERK, Role.ADMINISTRATOR);

        m.put(Permission.CUSTOMER_CREATE, dispAdmin);
        m.put(Permission.CUSTOMER_READ, all);
        m.put(Permission.CUSTOMER_DELETE, dispAdmin);
        m.put(Permission.ORDER_CREATE, dispAdmin);
        m.put(Permission.ORDER_READ, all);
        m.put(Permission.ORDER_ACCEPT, dispAdmin);
        m.put(Permission.ORDER_START, dispAdmin);
        m.put(Permission.ORDER_COMPLETE, dispAdmin);
        m.put(Permission.ORDER_CANCEL, dispAdmin);
        m.put(Permission.ORDER_DISPUTE, EnumSet.of(Role.DISPATCHER, Role.FINANCE_CLERK, Role.ADMINISTRATOR));
        m.put(Permission.PAYMENT_RECORD, finAdmin);
        m.put(Permission.PAYMENT_REFUND, finAdmin);
        m.put(Permission.PAYMENT_READ, finAdmin);
        m.put(Permission.RECONCILIATION_EXPORT, finAdmin);
        m.put(Permission.ATTACHMENT_UPLOAD, dispAdmin);
        m.put(Permission.ATTACHMENT_READ, all);
        m.put(Permission.ATTACHMENT_DELETE, adminOnly);
        m.put(Permission.CONFIG_READ, all);
        m.put(Permission.CONFIG_WRITE, adminOnly);
        m.put(Permission.AUDIT_READ, adminOnly);
        m.put(Permission.USER_MANAGE, adminOnly);
        m.put(Permission.UPDATE_APPLY, adminOnly);
        m.put(Permission.UPDATE_ROLLBACK, adminOnly);
        m.put(Permission.SCHEDULER_RUN, adminOnly);
        m.put(Permission.SHARE_LINK_CREATE, dispAdmin);
        m.put(Permission.SHARE_LINK_RESOLVE, all);
        m.put(Permission.INVOICE_ISSUE, finAdmin);
        m.put(Permission.INVOICE_MANAGE, finAdmin);
        m.put(Permission.INVOICE_READ, finAdmin);
        m.put(Permission.COUPON_MANAGE, adminOnly);
        m.put(Permission.COUPON_READ, all);
        m.put(Permission.SUBSIDY_MANAGE, adminOnly);
        m.put(Permission.SUBSIDY_READ, finAdmin);
        m.put(Permission.TRIP_CREATE, dispAdmin);
        m.put(Permission.TRIP_READ, all);
        m.put(Permission.TRIP_MANAGE, dispAdmin);
        return m;
    }
}
