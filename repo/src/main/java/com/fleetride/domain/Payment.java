package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Payment {
    public enum Tender { CASH, CARD_ON_FILE, CHECK }
    public enum Kind { DEPOSIT, FINAL, REFUND, CANCEL_FEE }

    private final String id;
    private final String orderId;
    private final Tender tender;
    private final Kind kind;
    private final Money amount;
    private final LocalDateTime recordedAt;
    private final String notes;

    public Payment(String id, String orderId, Tender tender, Kind kind, Money amount,
                   LocalDateTime recordedAt, String notes) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
        if (tender == null) throw new IllegalArgumentException("tender required");
        if (kind == null) throw new IllegalArgumentException("kind required");
        if (amount == null) throw new IllegalArgumentException("amount required");
        if (recordedAt == null) throw new IllegalArgumentException("recordedAt required");
        this.id = id;
        this.orderId = orderId;
        this.tender = tender;
        this.kind = kind;
        this.amount = amount;
        this.recordedAt = recordedAt;
        this.notes = notes;
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public Tender tender() { return tender; }
    public Kind kind() { return kind; }
    public Money amount() { return amount; }
    public LocalDateTime recordedAt() { return recordedAt; }
    public String notes() { return notes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
