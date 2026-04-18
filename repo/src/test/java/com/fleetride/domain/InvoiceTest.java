package com.fleetride.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceTest {

    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @Test
    void validation() {
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice(null, "o", "c", Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice(" ", "o", "c", Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", null, "c", Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "", "c", Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "o", null, Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "o", " ", Money.of("1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "o", "c", null, t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "o", "c", Money.of("-1"), t, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Invoice("id", "o", "c", Money.of("1"), null, null));
    }

    @Test
    void accessors() {
        Invoice i = new Invoice("id", "o", "c", Money.of("50.00"), t, "notes");
        assertEquals("id", i.id());
        assertEquals("o", i.orderId());
        assertEquals("c", i.customerId());
        assertEquals(Money.of("50.00"), i.amount());
        assertEquals(t, i.issuedAt());
        assertEquals("notes", i.notes());
        assertEquals(Invoice.Status.ISSUED, i.status());
        assertNull(i.paidAt());
        assertNull(i.canceledAt());
        i.setNotes("updated");
        assertEquals("updated", i.notes());
    }

    @Test
    void markPaidTransitions() {
        Invoice i = new Invoice("id", "o", "c", Money.of("1"), t, null);
        i.markPaid(t.plusMinutes(5));
        assertEquals(Invoice.Status.PAID, i.status());
        assertEquals(t.plusMinutes(5), i.paidAt());
        assertThrows(IllegalStateException.class, () -> i.markPaid(t.plusHours(1)));
    }

    @Test
    void cancelTransitions() {
        Invoice i = new Invoice("id", "o", "c", Money.of("1"), t, null);
        i.cancel(t.plusMinutes(5));
        assertEquals(Invoice.Status.CANCELED, i.status());
        assertEquals(t.plusMinutes(5), i.canceledAt());
        assertThrows(IllegalStateException.class, () -> i.cancel(t.plusHours(1)));
    }

    @Test
    void cannotMarkPaidAfterCancel() {
        Invoice i = new Invoice("id", "o", "c", Money.of("1"), t, null);
        i.cancel(t);
        assertThrows(IllegalStateException.class, () -> i.markPaid(t));
    }

    @Test
    void restoreFromPersistence() {
        Invoice i = new Invoice("id", "o", "c", Money.of("1"), t, null);
        i.restorePaid(t.plusMinutes(1));
        assertEquals(Invoice.Status.PAID, i.status());
        assertEquals(t.plusMinutes(1), i.paidAt());
        Invoice j = new Invoice("id2", "o", "c", Money.of("1"), t, null);
        j.restoreCanceled(t.plusMinutes(2));
        assertEquals(Invoice.Status.CANCELED, j.status());
        assertEquals(t.plusMinutes(2), j.canceledAt());
    }

    @Test
    void equalsAndHash() {
        Invoice a = new Invoice("id", "o", "c", Money.of("1"), t, null);
        Invoice b = new Invoice("id", "o2", "c2", Money.of("2"), t, null);
        Invoice c = new Invoice("id2", "o", "c", Money.of("1"), t, null);
        assertEquals(a, b);
        assertEquals(a, a);
        assertNotEquals(a, c);
        assertNotEquals(a, "x");
        assertNotEquals(a, null);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void statusValues() {
        assertEquals(3, Invoice.Status.values().length);
        for (Invoice.Status s : Invoice.Status.values()) {
            assertEquals(s, Invoice.Status.valueOf(s.name()));
        }
    }
}
