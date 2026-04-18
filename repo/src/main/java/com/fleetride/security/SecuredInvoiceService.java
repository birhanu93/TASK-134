package com.fleetride.security;

import com.fleetride.domain.Invoice;
import com.fleetride.service.InvoiceService;

import java.util.List;
import java.util.Optional;

public final class SecuredInvoiceService {
    private final InvoiceService delegate;
    private final Authorizer authz;

    public SecuredInvoiceService(InvoiceService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public Invoice issueFor(String orderId, String notes) {
        authz.require(Permission.INVOICE_ISSUE);
        return delegate.issueFor(orderId, notes);
    }

    public void markPaid(String invoiceId) {
        authz.require(Permission.INVOICE_MANAGE);
        delegate.markPaid(invoiceId);
    }

    public void cancel(String invoiceId) {
        authz.require(Permission.INVOICE_MANAGE);
        delegate.cancel(invoiceId);
    }

    public Optional<Invoice> find(String id) {
        authz.require(Permission.INVOICE_READ);
        return delegate.find(id);
    }

    public List<Invoice> listForOrder(String orderId) {
        authz.require(Permission.INVOICE_READ);
        return delegate.listForOrder(orderId);
    }

    public List<Invoice> listByStatus(Invoice.Status s) {
        authz.require(Permission.INVOICE_READ);
        return delegate.listByStatus(s);
    }

    public List<Invoice> listAll() {
        authz.require(Permission.INVOICE_READ);
        return delegate.listAll();
    }
}
