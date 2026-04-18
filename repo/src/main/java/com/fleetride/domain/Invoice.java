package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Invoice {
    public enum Status { ISSUED, PAID, CANCELED }

    private final String id;
    private final String orderId;
    private final String customerId;
    private final Money amount;
    private final LocalDateTime issuedAt;
    private String notes;
    private Status status;
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;

    public Invoice(String id, String orderId, String customerId, Money amount,
                   LocalDateTime issuedAt, String notes) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");
        if (amount == null || amount.isNegative()) throw new IllegalArgumentException("amount must be non-negative");
        if (issuedAt == null) throw new IllegalArgumentException("issuedAt required");
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.issuedAt = issuedAt;
        this.notes = notes;
        this.status = Status.ISSUED;
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String customerId() { return customerId; }
    public Money amount() { return amount; }
    public LocalDateTime issuedAt() { return issuedAt; }
    public String notes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Status status() { return status; }
    public LocalDateTime paidAt() { return paidAt; }
    public LocalDateTime canceledAt() { return canceledAt; }

    public void markPaid(LocalDateTime at) {
        if (status != Status.ISSUED) throw new IllegalStateException("cannot mark paid from " + status);
        this.status = Status.PAID;
        this.paidAt = at;
    }

    public void cancel(LocalDateTime at) {
        if (status != Status.ISSUED) throw new IllegalStateException("cannot cancel from " + status);
        this.status = Status.CANCELED;
        this.canceledAt = at;
    }

    public void restorePaid(LocalDateTime at) {
        this.status = Status.PAID;
        this.paidAt = at;
    }

    public void restoreCanceled(LocalDateTime at) {
        this.status = Status.CANCELED;
        this.canceledAt = at;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invoice i)) return false;
        return Objects.equals(id, i.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
