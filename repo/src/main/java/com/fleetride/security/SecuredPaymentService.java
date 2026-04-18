package com.fleetride.security;

import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.service.PaymentService;

import java.util.List;

public final class SecuredPaymentService {
    private final PaymentService delegate;
    private final Authorizer authz;

    public SecuredPaymentService(PaymentService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public Payment recordDeposit(String orderId, Payment.Tender tender, Money amount) {
        authz.require(Permission.PAYMENT_RECORD);
        return delegate.recordDeposit(orderId, tender, amount);
    }

    public Payment recordFinal(String orderId, Payment.Tender tender, Money amount) {
        authz.require(Permission.PAYMENT_RECORD);
        return delegate.recordFinal(orderId, tender, amount);
    }

    public Payment recordCancelFee(String orderId, Payment.Tender tender, Money amount) {
        authz.require(Permission.PAYMENT_RECORD);
        return delegate.recordCancelFee(orderId, tender, amount);
    }

    public Payment refund(String orderId, Payment.Tender tender, Money amount, String reason) {
        authz.require(Permission.PAYMENT_REFUND);
        return delegate.refund(orderId, tender, amount, reason);
    }

    public Money totalPaid(String orderId) {
        authz.require(Permission.PAYMENT_READ);
        return delegate.totalPaid(orderId);
    }

    public Money balanceFor(Order order) {
        authz.require(Permission.PAYMENT_READ);
        return delegate.balanceFor(order);
    }

    public boolean isOverdue(Order order) {
        authz.require(Permission.PAYMENT_READ);
        return delegate.isOverdue(order);
    }

    public List<Payment> listForOrder(String orderId) {
        authz.require(Permission.PAYMENT_READ);
        return delegate.listForOrder(orderId);
    }

    public List<Payment> listAll() {
        authz.require(Permission.PAYMENT_READ);
        return delegate.listAll();
    }
}
