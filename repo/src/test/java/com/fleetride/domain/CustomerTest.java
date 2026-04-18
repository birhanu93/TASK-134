package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {

    @Test
    void validationBranches() {
        assertThrows(IllegalArgumentException.class, () -> new Customer(null, "n", "p", "t"));
        assertThrows(IllegalArgumentException.class, () -> new Customer(" ", "n", "p", "t"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("id", null, "p", "t"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("id", "", "p", "t"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("id", "n", null, "t"));
        assertThrows(IllegalArgumentException.class, () -> new Customer("id", "n", " ", "t"));
    }

    @Test
    void twoArgConstructorUsesZero() {
        Customer c = new Customer("id", "n", "p", "t");
        assertEquals(Money.ZERO, c.subsidyUsedThisMonth());
    }

    @Test
    void nullSubsidyBecomesZero() {
        Customer c = new Customer("id", "n", "p", "t", null);
        assertEquals(Money.ZERO, c.subsidyUsedThisMonth());
    }

    @Test
    void accessorsAndSubsidyRecords() {
        Customer c = new Customer("id", "n", "p", "tok", Money.of("5.00"));
        assertEquals("id", c.id());
        assertEquals("n", c.name());
        assertEquals("p", c.phone());
        assertEquals("tok", c.encryptedPaymentToken());
        assertEquals(Money.of("5.00"), c.subsidyUsedThisMonth());
        c.recordSubsidyUsage(Money.of("3.00"));
        assertEquals(Money.of("8.00"), c.subsidyUsedThisMonth());
        c.resetSubsidyUsage();
        assertEquals(Money.ZERO, c.subsidyUsedThisMonth());
    }

    @Test
    void rideQuotaDefaultsAndTracking() {
        Customer c = new Customer("id", "n", "p", "t");
        assertEquals(Customer.DEFAULT_MONTHLY_RIDE_QUOTA, c.monthlyRideQuota());
        assertEquals(0, c.monthlyRidesUsed());
        assertFalse(c.isOverQuota());
        assertEquals(Customer.DEFAULT_MONTHLY_RIDE_QUOTA, c.rideQuotaRemaining());

        c.recordRide();
        c.recordRide();
        assertEquals(2, c.monthlyRidesUsed());
        assertEquals(Customer.DEFAULT_MONTHLY_RIDE_QUOTA - 2, c.rideQuotaRemaining());
    }

    @Test
    void overQuotaWhenAtOrAboveCap() {
        Customer c = new Customer("id", "n", "p", "t", Money.ZERO, null, 3, 3);
        assertTrue(c.isOverQuota());
        assertEquals(0, c.rideQuotaRemaining());
    }

    @Test
    void zeroQuotaMeansUnlimited() {
        // Admin may zero the quota to disable enforcement.
        Customer c = new Customer("id", "n", "p", "t", Money.ZERO, null, 0, 999);
        assertFalse(c.isOverQuota());
    }

    @Test
    void resetMonthlyQuotaZeroesUsage() {
        Customer c = new Customer("id", "n", "p", "t", Money.ZERO, null, 10, 7);
        assertEquals(7, c.monthlyRidesUsed());
        c.resetMonthlyQuota();
        assertEquals(0, c.monthlyRidesUsed());
        assertEquals(10, c.monthlyRideQuota());
    }

    @Test
    void setMonthlyRideQuotaValidation() {
        Customer c = new Customer("id", "n", "p", "t");
        c.setMonthlyRideQuota(25);
        assertEquals(25, c.monthlyRideQuota());
        assertThrows(IllegalArgumentException.class, () -> c.setMonthlyRideQuota(-1));
    }

    @Test
    void constructorRejectsNegativeQuotaFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new Customer("id", "n", "p", "t", Money.ZERO, null, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Customer("id", "n", "p", "t", Money.ZERO, null, 10, -1));
    }

    @Test
    void equalsAndHash() {
        Customer a = new Customer("id", "n", "p", "t");
        Customer b = new Customer("id", "other", "other", "t2");
        Customer c = new Customer("id2", "n", "p", "t");
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
