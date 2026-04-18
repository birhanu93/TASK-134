package com.fleetride.domain;

import java.util.Objects;

public final class Customer {
    public static final int DEFAULT_MONTHLY_RIDE_QUOTA = 60;

    private final String id;
    private final String name;
    private final String phone;
    private final String encryptedPaymentToken;
    private Money subsidyUsedThisMonth;
    private int monthlyRideQuota;
    private int monthlyRidesUsed;
    private String ownerUserId;

    public Customer(String id, String name, String phone, String encryptedPaymentToken) {
        this(id, name, phone, encryptedPaymentToken, Money.ZERO, null);
    }

    public Customer(String id, String name, String phone, String encryptedPaymentToken,
                    Money subsidyUsedThisMonth) {
        this(id, name, phone, encryptedPaymentToken, subsidyUsedThisMonth, null);
    }

    public Customer(String id, String name, String phone, String encryptedPaymentToken,
                    Money subsidyUsedThisMonth, String ownerUserId) {
        this(id, name, phone, encryptedPaymentToken, subsidyUsedThisMonth, ownerUserId,
                DEFAULT_MONTHLY_RIDE_QUOTA, 0);
    }

    public Customer(String id, String name, String phone, String encryptedPaymentToken,
                    Money subsidyUsedThisMonth, String ownerUserId,
                    int monthlyRideQuota, int monthlyRidesUsed) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("phone required");
        if (monthlyRideQuota < 0) throw new IllegalArgumentException("monthlyRideQuota must be non-negative");
        if (monthlyRidesUsed < 0) throw new IllegalArgumentException("monthlyRidesUsed must be non-negative");
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.encryptedPaymentToken = encryptedPaymentToken;
        this.subsidyUsedThisMonth = subsidyUsedThisMonth == null ? Money.ZERO : subsidyUsedThisMonth;
        this.ownerUserId = ownerUserId;
        this.monthlyRideQuota = monthlyRideQuota;
        this.monthlyRidesUsed = monthlyRidesUsed;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String phone() { return phone; }
    public String encryptedPaymentToken() { return encryptedPaymentToken; }
    public Money subsidyUsedThisMonth() { return subsidyUsedThisMonth; }
    public String ownerUserId() { return ownerUserId; }
    public void setOwnerUserId(String userId) { this.ownerUserId = userId; }

    public int monthlyRideQuota() { return monthlyRideQuota; }
    public void setMonthlyRideQuota(int v) {
        if (v < 0) throw new IllegalArgumentException("monthlyRideQuota must be non-negative");
        this.monthlyRideQuota = v;
    }
    public int monthlyRidesUsed() { return monthlyRidesUsed; }

    public boolean isOverQuota() {
        return monthlyRideQuota > 0 && monthlyRidesUsed >= monthlyRideQuota;
    }

    public int rideQuotaRemaining() {
        return Math.max(0, monthlyRideQuota - monthlyRidesUsed);
    }

    public void recordRide() {
        this.monthlyRidesUsed++;
    }

    public void recordSubsidyUsage(Money amount) {
        this.subsidyUsedThisMonth = this.subsidyUsedThisMonth.add(amount);
    }

    public void resetSubsidyUsage() {
        this.subsidyUsedThisMonth = Money.ZERO;
    }

    public void resetMonthlyQuota() {
        this.monthlyRidesUsed = 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
