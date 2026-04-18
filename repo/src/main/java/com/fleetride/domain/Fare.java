package com.fleetride.domain;

public final class Fare {
    private final Money baseFare;
    private final Money distanceCharge;
    private final Money timeCharge;
    private final Money floorSurcharge;
    private final Money priorityAdjustment;
    private final Money couponDiscount;
    private final Money subsidyApplied;
    private final Money subtotal;
    private final Money total;
    private final Money deposit;

    public Fare(Money baseFare, Money distanceCharge, Money timeCharge, Money floorSurcharge,
                Money priorityAdjustment, Money couponDiscount, Money subsidyApplied,
                Money subtotal, Money total, Money deposit) {
        this.baseFare = baseFare;
        this.distanceCharge = distanceCharge;
        this.timeCharge = timeCharge;
        this.floorSurcharge = floorSurcharge;
        this.priorityAdjustment = priorityAdjustment;
        this.couponDiscount = couponDiscount;
        this.subsidyApplied = subsidyApplied;
        this.subtotal = subtotal;
        this.total = total;
        this.deposit = deposit;
    }

    public Money baseFare() { return baseFare; }
    public Money distanceCharge() { return distanceCharge; }
    public Money timeCharge() { return timeCharge; }
    public Money floorSurcharge() { return floorSurcharge; }
    public Money priorityAdjustment() { return priorityAdjustment; }
    public Money couponDiscount() { return couponDiscount; }
    public Money subsidyApplied() { return subsidyApplied; }
    public Money subtotal() { return subtotal; }
    public Money total() { return total; }
    public Money deposit() { return deposit; }
}
