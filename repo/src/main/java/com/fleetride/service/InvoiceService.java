package com.fleetride.service;

import com.fleetride.domain.Invoice;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.repository.InvoiceRepository;
import com.fleetride.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

public final class InvoiceService {
    public static final class InvoiceException extends RuntimeException {
        public InvoiceException(String msg) { super(msg); }
    }

    private final InvoiceRepository invoices;
    private final OrderRepository orders;
    private final PaymentService payments;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditService audit;

    public InvoiceService(InvoiceRepository invoices, OrderRepository orders,
                          PaymentService payments, IdGenerator ids, Clock clock, AuditService audit) {
        this.invoices = invoices;
        this.orders = orders;
        this.payments = payments;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
    }

    public Invoice issueFor(String orderId, String notes) {
        Order o = orders.findById(orderId)
                .orElseThrow(() -> new InvoiceException("unknown order"));
        Money balance = payments.balanceFor(o);
        if (!balance.greaterThan(Money.ZERO)) {
            throw new InvoiceException("nothing to invoice (balance is zero)");
        }
        Invoice i = new Invoice(ids.next(), o.id(), o.customerId(), balance, clock.now(), notes);
        invoices.save(i);
        audit.record("system", "INVOICE_ISSUE", i.id(), balance.toString());
        return i;
    }

    public void markPaid(String invoiceId) {
        Invoice i = require(invoiceId);
        i.markPaid(clock.now());
        invoices.save(i);
        audit.record("system", "INVOICE_PAID", i.id(), null);
    }

    public void cancel(String invoiceId) {
        Invoice i = require(invoiceId);
        i.cancel(clock.now());
        invoices.save(i);
        audit.record("system", "INVOICE_CANCELED", i.id(), null);
    }

    public Optional<Invoice> find(String id) { return invoices.findById(id); }
    public List<Invoice> listForOrder(String orderId) { return invoices.findByOrder(orderId); }
    public List<Invoice> listByStatus(Invoice.Status s) { return invoices.findByStatus(s); }
    public List<Invoice> listAll() { return invoices.findAll(); }

    private Invoice require(String id) {
        return invoices.findById(id).orElseThrow(() -> new InvoiceException("unknown invoice"));
    }
}
