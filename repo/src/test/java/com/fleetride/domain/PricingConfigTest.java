package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingConfigTest {

    @Test
    void defaultValuesMatchSpec() {
        PricingConfig c = new PricingConfig();
        assertEquals(Money.of("3.50"), c.baseFare());
        assertEquals(Money.of("1.80"), c.perMile());
        assertEquals(Money.of("0.35"), c.perMinute());
        assertEquals(new BigDecimal("1.25"), c.priorityMultiplier());
        assertEquals(Money.of("1.00"), c.perFloorSurcharge());
        assertEquals(3, c.freeFloorThreshold());
        assertEquals(new BigDecimal("0.20"), c.depositPercent());
        assertEquals(Money.of("50.00"), c.monthlySubsidyCap());
        assertEquals(new BigDecimal("0.20"), c.maxCouponPercent());
        assertEquals(Money.of("25.00"), c.couponMinimumOrder());
        assertEquals(Money.of("5.00"), c.lateCancelFee());
        assertEquals(15, c.autoCancelMinutes());
        assertEquals(10, c.lateCancelWindowMinutes());
        assertEquals(7, c.disputeWindowDays());
    }

    @Test
    void settersUpdate() {
        PricingConfig c = new PricingConfig();
        c.setBaseFare(Money.of("5.00"));
        c.setPerMile(Money.of("2.00"));
        c.setPerMinute(Money.of("0.40"));
        c.setPriorityMultiplier(new BigDecimal("1.5"));
        c.setPerFloorSurcharge(Money.of("2.00"));
        c.setFreeFloorThreshold(5);
        c.setDepositPercent(new BigDecimal("0.25"));
        c.setMonthlySubsidyCap(Money.of("100.00"));
        // Max coupon percent is capped at 20% by spec — use a valid value that stays within.
        c.setMaxCouponPercent(new BigDecimal("0.15"));
        c.setCouponMinimumOrder(Money.of("30.00"));
        c.setLateCancelFee(Money.of("10.00"));
        c.setAutoCancelMinutes(20);
        c.setLateCancelWindowMinutes(15);
        c.setDisputeWindowDays(10);

        assertEquals(Money.of("5.00"), c.baseFare());
        assertEquals(Money.of("2.00"), c.perMile());
        assertEquals(Money.of("0.40"), c.perMinute());
        assertEquals(new BigDecimal("1.5"), c.priorityMultiplier());
        assertEquals(Money.of("2.00"), c.perFloorSurcharge());
        assertEquals(5, c.freeFloorThreshold());
        assertEquals(new BigDecimal("0.25"), c.depositPercent());
        assertEquals(Money.of("100.00"), c.monthlySubsidyCap());
        assertEquals(new BigDecimal("0.15"), c.maxCouponPercent());
        assertEquals(Money.of("30.00"), c.couponMinimumOrder());
        assertEquals(Money.of("10.00"), c.lateCancelFee());
        assertEquals(20, c.autoCancelMinutes());
        assertEquals(15, c.lateCancelWindowMinutes());
        assertEquals(10, c.disputeWindowDays());
    }

    @Test
    void rejectsNegativeMoneyFields() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setBaseFare(Money.of("-1.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setPerMile(Money.of("-0.01")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setPerMinute(Money.of("-1.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setPerFloorSurcharge(Money.of("-1.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setLateCancelFee(Money.of("-5.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setCouponMinimumOrder(Money.of("-25.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setMonthlySubsidyCap(Money.of("-50.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setOverdueFeePerSweep(Money.of("-5.00")));
    }

    @Test
    void rejectsNullMoneyFields() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setBaseFare(null));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setPerMile(null));
    }

    @Test
    void rejectsOutOfRangeDepositPercent() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setDepositPercent(new BigDecimal("-0.01")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setDepositPercent(new BigDecimal("1.01")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setDepositPercent(null));
    }

    @Test
    void rejectsCouponPercentAboveTwentyPercent() {
        PricingConfig c = new PricingConfig();
        // Spec hard-caps coupon percent at 20%.
        PricingConfig.InvalidPolicyException ex = assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setMaxCouponPercent(new BigDecimal("0.25")));
        assertTrue(ex.getMessage().contains("maxCouponPercent"));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setMaxCouponPercent(new BigDecimal("-0.01")));
        // boundary: exactly 0.20 is allowed
        c.setMaxCouponPercent(new BigDecimal("0.20"));
        assertEquals(new BigDecimal("0.20"), c.maxCouponPercent());
    }

    @Test
    void rejectsInvalidPriorityMultiplier() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setPriorityMultiplier(new BigDecimal("0.5")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setPriorityMultiplier(new BigDecimal("6")));
    }

    @Test
    void rejectsInvalidFreeFloorThreshold() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setFreeFloorThreshold(-1));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setFreeFloorThreshold(201));
    }

    @Test
    void rejectsNonPositiveTimeWindows() {
        PricingConfig c = new PricingConfig();
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setAutoCancelMinutes(0));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setAutoCancelMinutes(-5));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setAutoCancelMinutes(61));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setLateCancelWindowMinutes(0));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setLateCancelWindowMinutes(-1));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setDisputeWindowDays(0));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setDisputeWindowDays(91));
    }

    @Test
    void validBoundaryValuesAccepted() {
        PricingConfig c = new PricingConfig();
        c.setBaseFare(Money.ZERO);
        c.setPerMile(Money.ZERO);
        c.setMaxCouponPercent(BigDecimal.ZERO);
        c.setDepositPercent(BigDecimal.ZERO);
        c.setDepositPercent(BigDecimal.ONE);
        c.setFreeFloorThreshold(0);
        c.setFreeFloorThreshold(200);
        c.setAutoCancelMinutes(1);
        c.setAutoCancelMinutes(60);
        c.setDisputeWindowDays(1);
        c.setDisputeWindowDays(90);
    }
}
