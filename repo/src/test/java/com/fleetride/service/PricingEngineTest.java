package com.fleetride.service;

import com.fleetride.domain.Address;
import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Fare;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.PricingConfig;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.Subsidy;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {

    private final LocalDateTime windowStart = LocalDateTime.of(2026, 3, 27, 14, 0);
    private final TimeWindow window = new TimeWindow(windowStart, windowStart.plusHours(1));
    private final LocalDateTime createdAt = windowStart.minusHours(2);

    private Order order(Integer pickupFloor, Integer dropFloor, ServicePriority priority, double miles, int minutes) {
        Address p = new Address("1 Main", "NY", "NY", "10001", pickupFloor);
        Address d = new Address("2 Main", "NY", "NY", "10001", dropFloor);
        return new Order("oid", "cid", p, d, 1, window, VehicleType.STANDARD, priority, miles, minutes,
                null, createdAt);
    }

    private Customer customer(Money used) {
        return new Customer("cid", "n", "p", null, used);
    }

    @Test
    void rejectsNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> new PricingEngine(null));
    }

    @Test
    void basicFareNoExtras() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3.0, 10);
        Fare f = e.quote(o, customer(Money.ZERO), null, null);
        // 3.50 + (1.80*3)=5.40 + (0.35*10)=3.50 = 12.40
        assertEquals(Money.of("3.50"), f.baseFare());
        assertEquals(Money.of("5.40"), f.distanceCharge());
        assertEquals(Money.of("3.50"), f.timeCharge());
        assertEquals(Money.ZERO, f.floorSurcharge());
        assertEquals(Money.ZERO, f.priorityAdjustment());
        assertEquals(Money.ZERO, f.couponDiscount());
        assertEquals(Money.ZERO, f.subsidyApplied());
        assertEquals(Money.of("12.40"), f.subtotal());
        assertEquals(Money.of("12.40"), f.total());
        assertEquals(Money.of("2.48"), f.deposit());
    }

    @Test
    void floorSurchargeAppliesAbove3() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        // pickup floor 5 -> (5-3)=2 * 1.00 = 2.00, drop floor 2 -> 0
        Order o = order(5, 2, ServicePriority.NORMAL, 0, 0);
        Fare f = e.quote(o, customer(Money.ZERO), null, null);
        assertEquals(Money.of("2.00"), f.floorSurcharge());
    }

    @Test
    void priorityMultiplier() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.PRIORITY, 3.0, 10);
        Fare f = e.quote(o, customer(Money.ZERO), null, null);
        // base: 12.40, *1.25 = 15.50, priorityAdj = 3.10
        assertEquals(Money.of("15.50"), f.subtotal());
        assertEquals(Money.of("3.10"), f.priorityAdjustment());
    }

    @Test
    void couponPercentApplied() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        // base=3.50 + 36.00 + 10.50 = 50.00
        Coupon c = Coupon.percent("X", new BigDecimal("0.10"), Money.of("25.00"));
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        assertEquals(Money.of("50.00"), f.subtotal());
        assertEquals(Money.of("5.00"), f.couponDiscount());
        assertEquals(Money.of("45.00"), f.total());
    }

    @Test
    void couponPercentCappedAtMax() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        Coupon c = Coupon.percent("X", new BigDecimal("0.50"), Money.of("25.00"));
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        // capped to 20%: 50 * 0.20 = 10
        assertEquals(Money.of("10.00"), f.couponDiscount());
    }

    @Test
    void couponSkippedBelowCouponThreshold() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3.0, 10); // subtotal 12.40 < 25
        Coupon c = Coupon.percent("X", new BigDecimal("0.10"), Money.of("1.00"));
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        assertEquals(Money.ZERO, f.couponDiscount());
    }

    @Test
    void couponSkippedBelowCouponMinimum() {
        PricingConfig cfg = new PricingConfig();
        cfg.setCouponMinimumOrder(Money.of("5.00"));
        PricingEngine e = new PricingEngine(cfg);
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30); // subtotal 50, passes cfg min
        Coupon c = Coupon.percent("X", new BigDecimal("0.10"), Money.of("200.00")); // coupon's own min is high
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        assertEquals(Money.ZERO, f.couponDiscount());
    }

    @Test
    void couponFixedAmount() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        Coupon c = Coupon.fixed("X", Money.of("5.00"), Money.of("25.00"));
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        assertEquals(Money.of("5.00"), f.couponDiscount());
    }

    @Test
    void couponFixedCappedToSubtotal() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        // subtotal 50; give fixed 100 -> caps at 50
        Coupon c = Coupon.fixed("X", Money.of("100.00"), Money.of("25.00"));
        Fare f = e.quote(o, customer(Money.ZERO), c, null);
        assertEquals(Money.of("50.00"), f.couponDiscount());
        assertEquals(Money.ZERO, f.total());
    }

    @Test
    void subsidyAppliedWithinCap() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        Subsidy s = new Subsidy("cid", Money.of("50.00"));
        Fare f = e.quote(o, customer(Money.of("20.00")), null, s);
        // remaining=30. amount=50. remaining > amount? 30 > 50 false -> return remaining
        assertEquals(Money.of("30.00"), f.subsidyApplied());
        assertEquals(Money.of("20.00"), f.total());
    }

    @Test
    void subsidyAmountFullyCovered() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3.0, 10); // 12.40
        Subsidy s = new Subsidy("cid", Money.of("50.00"));
        Fare f = e.quote(o, customer(Money.ZERO), null, s);
        // remaining=50. amount=12.40. remaining > amount? true -> return amount
        assertEquals(Money.of("12.40"), f.subsidyApplied());
        assertEquals(Money.ZERO, f.total());
    }

    @Test
    void subsidyZeroWhenCapExceeded() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 20, 30);
        Subsidy s = new Subsidy("cid", Money.of("50.00"));
        Fare f = e.quote(o, customer(Money.of("50.00")), null, s);
        assertEquals(Money.ZERO, f.subsidyApplied());
    }

    @Test
    void subsidyNullOrCustomerNull() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3, 10);
        Fare f = e.quote(o, customer(Money.ZERO), null, null);
        assertEquals(Money.ZERO, f.subsidyApplied());
    }

    @Test
    void cancellationFeeWithinWindow() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3, 10);
        assertEquals(Money.of("5.00"), e.computeCancellationFee(o, windowStart.minusMinutes(5)));
        assertEquals(Money.of("5.00"), e.computeCancellationFee(o, windowStart.plusMinutes(5)));
    }

    @Test
    void cancellationFeeOutsideWindow() {
        PricingEngine e = new PricingEngine(new PricingConfig());
        Order o = order(null, null, ServicePriority.NORMAL, 3, 10);
        assertEquals(Money.ZERO, e.computeCancellationFee(o, windowStart.minusMinutes(30)));
        assertEquals(Money.ZERO, e.computeCancellationFee(o, windowStart.plusMinutes(30)));
    }

    @Test
    void configAccessor() {
        PricingConfig c = new PricingConfig();
        PricingEngine e = new PricingEngine(c);
        assertSame(c, e.config());
    }

    @Test
    void roundPercentUtility() {
        assertEquals(new BigDecimal("0.1234"),
                PricingEngine.roundPercent(new BigDecimal("0.12344")));
        assertEquals(new BigDecimal("0.1235"),
                PricingEngine.roundPercent(new BigDecimal("0.12345")));
    }
}
