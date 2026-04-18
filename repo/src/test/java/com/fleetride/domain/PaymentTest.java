package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void validation() {
        LocalDateTime t = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(null, "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", null, Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", " ", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", "o", null, Payment.Kind.DEPOSIT, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", "o", Payment.Tender.CASH, null, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, null, t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment("id", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), null, null));
    }

    @Test
    void accessors() {
        LocalDateTime t = LocalDateTime.now();
        Payment p = new Payment("id", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT,
                Money.of("10.00"), t, "notes");
        assertEquals("id", p.id());
        assertEquals("o", p.orderId());
        assertEquals(Payment.Tender.CASH, p.tender());
        assertEquals(Payment.Kind.DEPOSIT, p.kind());
        assertEquals(Money.of("10.00"), p.amount());
        assertEquals(t, p.recordedAt());
        assertEquals("notes", p.notes());
    }

    @Test
    void equalsAndHash() {
        LocalDateTime t = LocalDateTime.now();
        Payment a = new Payment("id", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null);
        Payment b = new Payment("id", "o", Payment.Tender.CARD_ON_FILE, Payment.Kind.FINAL, Money.of("2"), t, null);
        Payment c = new Payment("id2", "o", Payment.Tender.CASH, Payment.Kind.DEPOSIT, Money.of("1"), t, null);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
