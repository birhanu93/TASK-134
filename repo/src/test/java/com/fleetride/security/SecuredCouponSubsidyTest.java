package com.fleetride.security;

import com.fleetride.domain.Money;
import com.fleetride.domain.Role;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryCouponRepository;
import com.fleetride.repository.InMemorySubsidyRepository;
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.Clock;
import com.fleetride.service.CouponService;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.SubsidyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SecuredCouponSubsidyTest {

    private AuthService auth;
    private SecuredCouponService coupon;
    private SecuredSubsidyService subsidy;

    @BeforeEach
    void setup() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        EncryptionService enc = new EncryptionService("k");
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "u-" + n.incrementAndGet();
        Clock clock = () -> LocalDateTime.of(2026, 3, 27, 10, 0);
        auth = new AuthService(users, enc, ids);
        Authorizer authz = new Authorizer(auth);
        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("disp", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();
        AuditService audit = new AuditService(new InMemoryAuditRepository(), ids, clock);
        coupon = new SecuredCouponService(
                new CouponService(new InMemoryCouponRepository(), audit), authz);
        subsidy = new SecuredSubsidyService(
                new SubsidyService(new InMemorySubsidyRepository(), audit), authz);
    }

    @Test
    void adminManagesCoupons() {
        auth.login("admin", "pw");
        coupon.createPercent("P10", new BigDecimal("0.10"), Money.of("25"));
        coupon.createFixed("F5", Money.of("5.00"), Money.of("25"));
        assertEquals(2, coupon.list().size());
        assertTrue(coupon.find("P10").isPresent());
        coupon.delete("P10");
        assertTrue(coupon.find("P10").isEmpty());
    }

    @Test
    void dispatcherCanReadButNotWriteCoupons() {
        auth.login("admin", "pw");
        coupon.createPercent("P10", new BigDecimal("0.10"), Money.of("25"));
        auth.logout();
        auth.login("disp", "pw");
        assertEquals(1, coupon.list().size());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> coupon.createPercent("P", new BigDecimal("0.1"), Money.of("25")));
        assertThrows(Authorizer.ForbiddenException.class,
                () -> coupon.createFixed("F", Money.of("5"), Money.of("25")));
        assertThrows(Authorizer.ForbiddenException.class, () -> coupon.delete("P10"));
    }

    @Test
    void adminManagesSubsidies() {
        auth.login("admin", "pw");
        subsidy.assign("c1", Money.of("50"));
        assertEquals(1, subsidy.list().size());
        assertTrue(subsidy.find("c1").isPresent());
        subsidy.revoke("c1");
        assertTrue(subsidy.find("c1").isEmpty());
    }

    @Test
    void financeCanReadSubsidies() {
        auth.login("admin", "pw");
        subsidy.assign("c1", Money.of("50"));
        auth.logout();
        auth.login("fin", "pw");
        assertEquals(1, subsidy.list().size());
        assertTrue(subsidy.find("c1").isPresent());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> subsidy.assign("c2", Money.of("30")));
        assertThrows(Authorizer.ForbiddenException.class, () -> subsidy.revoke("c1"));
    }

    @Test
    void dispatcherCannotReadSubsidies() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> subsidy.list());
        assertThrows(Authorizer.ForbiddenException.class, () -> subsidy.find("c1"));
    }
}
