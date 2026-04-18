package com.fleetride.service;

import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.repository.OrderRepository;
import com.fleetride.repository.PaymentRepository;

import java.util.List;

public final class PaymentService {
    public static final class PaymentException extends RuntimeException {
        public PaymentException(String msg) { super(msg); }
    }

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditService audit;

    public PaymentService(PaymentRepository payments, OrderRepository orders,
                          IdGenerator ids, Clock clock, AuditService audit) {
        this.payments = payments;
        this.orders = orders;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public Payment recordDeposit(String orderId, Payment.Tender tender, Money amount) {
        requireOrder(orderId);
        requirePositive(amount, "deposit");
        return record(orderId, tender, Payment.Kind.DEPOSIT, amount, null);
    }

    public Payment recordFinal(String orderId, Payment.Tender tender, Money amount) {
        requireOrder(orderId);
        requirePositive(amount, "final payment");
        return record(orderId, tender, Payment.Kind.FINAL, amount, null);
    }

    public Payment recordCancelFee(String orderId, Payment.Tender tender, Money amount) {
        requireOrder(orderId);
        requirePositive(amount, "cancel fee");
        return record(orderId, tender, Payment.Kind.CANCEL_FEE, amount, null);
    }

    public Payment refund(String orderId, Payment.Tender tender, Money amount, String reason) {
        requireOrder(orderId);
        requirePositive(amount, "refund");
        Money paid = totalPaid(orderId);
        if (amount.greaterThan(paid)) {
            throw new PaymentException("refund exceeds net paid");
        }
        return record(orderId, tender, Payment.Kind.REFUND, amount.multiply(-1.0), reason);
    }

    private static void requirePositive(Money amount, String kind) {
        if (amount == null) {
            throw new PaymentException(kind + " amount required");
        }
        if (amount.isZero() || amount.isNegative()) {
            throw new PaymentException(kind + " amount must be positive (got " + amount + ")");
        }
    }

    public Money totalPaid(String orderId) {
        Money sum = Money.ZERO;
        for (Payment p : payments.findByOrder(orderId)) {
            sum = sum.add(p.amount());
        }
        return sum;
    }

    public Money balanceFor(Order order) {
        Money expected = order.fare() == null ? Money.ZERO : order.fare().total();
        expected = expected.add(order.cancellationFee()).add(order.overdueFee());
        return expected.subtract(totalPaid(order.id()));
    }

    public boolean isOverdue(Order order) {
        Money bal = balanceFor(order);
        return bal.greaterThan(Money.ZERO);
    }

    public List<Payment> listForOrder(String orderId) { return payments.findByOrder(orderId); }
    public List<Payment> listAll() { return payments.findAll(); }

    private Payment record(String orderId, Payment.Tender tender, Payment.Kind kind,
                           Money amount, String notes) {
        Payment p = new Payment(ids.next(), orderId, tender, kind, amount, clock.now(), notes);
        payments.save(p);
        audit.record("system", "PAYMENT_" + kind, orderId, tender + ":" + amount);
        return p;
    }

    private Order requireOrder(String orderId) {
        return orders.findById(orderId).orElseThrow(() -> new PaymentException("unknown order"));
    }
}
