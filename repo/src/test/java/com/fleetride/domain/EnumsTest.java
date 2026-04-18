package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumsTest {

    @Test
    void orderStateValues() {
        assertEquals(6, OrderState.values().length);
        assertEquals(OrderState.PENDING_MATCH, OrderState.valueOf("PENDING_MATCH"));
        assertEquals(OrderState.ACCEPTED, OrderState.valueOf("ACCEPTED"));
        assertEquals(OrderState.IN_PROGRESS, OrderState.valueOf("IN_PROGRESS"));
        assertEquals(OrderState.COMPLETED, OrderState.valueOf("COMPLETED"));
        assertEquals(OrderState.CANCELED, OrderState.valueOf("CANCELED"));
        assertEquals(OrderState.IN_DISPUTE, OrderState.valueOf("IN_DISPUTE"));
    }

    @Test
    void vehicleTypeValues() {
        assertEquals(4, VehicleType.values().length);
        assertEquals(VehicleType.STANDARD, VehicleType.valueOf("STANDARD"));
        assertEquals(VehicleType.XL, VehicleType.valueOf("XL"));
        assertEquals(VehicleType.ACCESSIBLE, VehicleType.valueOf("ACCESSIBLE"));
        assertEquals(VehicleType.PREMIUM, VehicleType.valueOf("PREMIUM"));
    }

    @Test
    void servicePriorityValues() {
        assertEquals(2, ServicePriority.values().length);
        assertEquals(ServicePriority.NORMAL, ServicePriority.valueOf("NORMAL"));
        assertEquals(ServicePriority.PRIORITY, ServicePriority.valueOf("PRIORITY"));
    }

    @Test
    void roleValues() {
        assertEquals(3, Role.values().length);
        assertTrue(Role.valueOf("DISPATCHER") == Role.DISPATCHER);
        assertTrue(Role.valueOf("FINANCE_CLERK") == Role.FINANCE_CLERK);
        assertTrue(Role.valueOf("ADMINISTRATOR") == Role.ADMINISTRATOR);
    }

    @Test
    void paymentEnumValues() {
        assertEquals(3, Payment.Tender.values().length);
        assertEquals(4, Payment.Kind.values().length);
        for (Payment.Tender t : Payment.Tender.values()) {
            assertEquals(t, Payment.Tender.valueOf(t.name()));
        }
        for (Payment.Kind k : Payment.Kind.values()) {
            assertEquals(k, Payment.Kind.valueOf(k.name()));
        }
    }

    @Test
    void disputeStatus() {
        assertEquals(3, Dispute.Status.values().length);
        for (Dispute.Status s : Dispute.Status.values()) {
            assertEquals(s, Dispute.Status.valueOf(s.name()));
        }
    }

    @Test
    void couponType() {
        assertEquals(2, Coupon.Type.values().length);
        for (Coupon.Type t : Coupon.Type.values()) {
            assertEquals(t, Coupon.Type.valueOf(t.name()));
        }
    }
}
