package com.fleetride.domain;

public final class Subsidy {
    private final String customerId;
    private final Money monthlyCap;

    public Subsidy(String customerId, Money monthlyCap) {
        if (customerId == null || customerId.isBlank()) throw new IllegalArgumentException("customerId required");
        if (monthlyCap == null || monthlyCap.isNegative()) throw new IllegalArgumentException("monthlyCap must be non-negative");
        this.customerId = customerId;
        this.monthlyCap = monthlyCap;
    }

    public String customerId() { return customerId; }
    public Money monthlyCap() { return monthlyCap; }
}
