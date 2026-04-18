package com.fleetride.repository.sqlite;

import com.fleetride.domain.Coupon;
import com.fleetride.domain.Money;
import com.fleetride.domain.Subsidy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SqliteCouponAndSubsidyRepositoryTest {

    private Database db;

    @BeforeEach
    void setup() { db = Database.inMemory(); }

    @AfterEach
    void teardown() { db.close(); }

    @Test
    void couponPercentRoundTrip() {
        SqliteCouponRepository repo = new SqliteCouponRepository(db);
        Coupon c = Coupon.percent("PCT10", new BigDecimal("0.1000"), Money.of("25.00"));
        repo.save(c);
        Coupon loaded = repo.findByCode("PCT10").orElseThrow();
        assertEquals(Coupon.Type.PERCENT, loaded.type());
        assertEquals(0, new BigDecimal("0.1").compareTo(loaded.percent()));
        assertEquals(Money.of("25.00"), loaded.minimumOrder());
    }

    @Test
    void couponFixedRoundTrip() {
        SqliteCouponRepository repo = new SqliteCouponRepository(db);
        repo.save(Coupon.fixed("FIX5", Money.of("5.00"), Money.of("30.00")));
        Coupon loaded = repo.findByCode("FIX5").orElseThrow();
        assertEquals(Coupon.Type.FIXED, loaded.type());
        assertEquals(Money.of("5.00"), loaded.fixedAmount());
        assertEquals(Money.of("30.00"), loaded.minimumOrder());
    }

    @Test
    void couponUpsertAndList() {
        SqliteCouponRepository repo = new SqliteCouponRepository(db);
        repo.save(Coupon.percent("X", new BigDecimal("0.10"), Money.of("25")));
        repo.save(Coupon.percent("X", new BigDecimal("0.20"), Money.of("25")));
        assertEquals(0, new BigDecimal("0.2")
                .compareTo(repo.findByCode("X").orElseThrow().percent()));
        assertTrue(repo.findByCode("nope").isEmpty());
        assertEquals(1, repo.findAll().size());
        repo.delete("X");
        assertTrue(repo.findByCode("X").isEmpty());
    }

    @Test
    void subsidyRoundTrip() {
        SqliteSubsidyRepository repo = new SqliteSubsidyRepository(db);
        repo.save(new Subsidy("c1", Money.of("50.00")));
        Subsidy s = repo.findByCustomer("c1").orElseThrow();
        assertEquals(Money.of("50.00"), s.monthlyCap());
        repo.save(new Subsidy("c1", Money.of("75.00")));
        assertEquals(Money.of("75.00"), repo.findByCustomer("c1").orElseThrow().monthlyCap());
        assertTrue(repo.findByCustomer("nope").isEmpty());
        assertEquals(1, repo.findAll().size());
        repo.delete("c1");
        assertTrue(repo.findByCustomer("c1").isEmpty());
    }
}
