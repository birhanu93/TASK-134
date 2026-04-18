package com.fleetride.service;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Money;
import com.fleetride.domain.Subsidy;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCouponRepository;
import com.fleetride.repository.InMemorySubsidyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CouponAndSubsidyServiceTest {

    private AuditService audit() {
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "a-" + n.incrementAndGet();
        return new AuditService(new InMemoryAuditRepository(), ids,
                () -> LocalDateTime.of(2026, 3, 27, 10, 0));
    }

    @Test
    void couponRejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new CouponService(null, audit()));
        assertThrows(IllegalArgumentException.class,
                () -> new CouponService(new InMemoryCouponRepository(), null));
    }

    @Test
    void couponCreateFindListDelete() {
        CouponService svc = new CouponService(new InMemoryCouponRepository(), audit());
        Coupon p = svc.createPercent("PCT10", new BigDecimal("0.10"), Money.of("25.00"));
        Coupon f = svc.createFixed("FIXED5", Money.of("5.00"), Money.of("25.00"));
        assertEquals(p, svc.find("PCT10").orElseThrow());
        assertEquals(f, svc.require("FIXED5"));
        assertEquals(2, svc.list().size());
        assertTrue(svc.find("nope").isEmpty());
        assertTrue(svc.find(null).isEmpty());
        assertTrue(svc.find(" ").isEmpty());
        assertThrows(CouponService.CouponException.class, () -> svc.require("nope"));
        svc.delete("PCT10");
        assertTrue(svc.find("PCT10").isEmpty());
    }

    @Test
    void subsidyRejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new SubsidyService(null, audit()));
        assertThrows(IllegalArgumentException.class,
                () -> new SubsidyService(new InMemorySubsidyRepository(), null));
    }

    @Test
    void subsidyAssignRequireListRevoke() {
        SubsidyService svc = new SubsidyService(new InMemorySubsidyRepository(), audit());
        Subsidy s = svc.assign("c1", Money.of("50.00"));
        assertEquals(s, svc.find("c1").orElseThrow());
        assertEquals(s, svc.require("c1"));
        assertEquals(1, svc.list().size());
        assertTrue(svc.find("nope").isEmpty());
        assertTrue(svc.find(null).isEmpty());
        assertTrue(svc.find(" ").isEmpty());
        assertThrows(SubsidyService.SubsidyException.class, () -> svc.require("nope"));
        svc.revoke("c1");
        assertTrue(svc.find("c1").isEmpty());
    }
}
