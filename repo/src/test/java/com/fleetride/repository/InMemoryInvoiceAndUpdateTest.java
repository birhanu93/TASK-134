package com.fleetride.repository;

import com.fleetride.domain.Invoice;
import com.fleetride.domain.Money;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInvoiceAndUpdateTest {

    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @Test
    void invoiceRepoCoversAllBranches() {
        InMemoryInvoiceRepository repo = new InMemoryInvoiceRepository();
        Invoice a = new Invoice("a", "o1", "c1", Money.of("1"), t, null);
        Invoice b = new Invoice("b", "o1", "c2", Money.of("1"), t, null);
        Invoice c = new Invoice("c", "o2", "c1", Money.of("1"), t, null);
        b.markPaid(t);
        c.cancel(t);
        repo.save(a);
        repo.save(b);
        repo.save(c);
        assertEquals(a, repo.findById("a").orElseThrow());
        assertTrue(repo.findById("nope").isEmpty());
        assertEquals(2, repo.findByOrder("o1").size());
        assertEquals(1, repo.findByStatus(Invoice.Status.PAID).size());
        assertEquals(1, repo.findByStatus(Invoice.Status.CANCELED).size());
        assertEquals(1, repo.findByStatus(Invoice.Status.ISSUED).size());
        assertEquals(3, repo.findAll().size());
    }

    @Test
    void updateHistoryInMemoryBranches() {
        InMemoryUpdateHistoryRepository repo = new InMemoryUpdateHistoryRepository();
        assertEquals("1.0.0", repo.currentVersion());
        assertTrue(repo.peekLatest().isEmpty());
        long s1 = repo.append("v1", Path.of("/p1"), t);
        long s2 = repo.append("v2", Path.of("/p2"), t.plusMinutes(1));
        assertEquals(2, repo.listAll().size());
        assertEquals("v2", repo.peekLatest().orElseThrow().version());
        repo.setCurrentVersion("v2");
        assertEquals("v2", repo.currentVersion());
        repo.deleteBySeq(s2);
        assertEquals("v1", repo.peekLatest().orElseThrow().version());
        repo.deleteBySeq(s1);
        assertTrue(repo.peekLatest().isEmpty());
    }

    @Test
    void configInMemorySanity() {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        repo.setSetting("a", "b");
        repo.setDictionary("c", "d");
        repo.setTemplate("e", "f");
        assertEquals("b", repo.getSetting("a").orElseThrow());
        assertTrue(repo.getSetting("nope").isEmpty());
        assertEquals("d", repo.getDictionary("c").orElseThrow());
        assertTrue(repo.getDictionary("nope").isEmpty());
        assertEquals("f", repo.getTemplate("e").orElseThrow());
        assertTrue(repo.getTemplate("nope").isEmpty());
        assertEquals(1, repo.allSettings().size());
        assertEquals(1, repo.allDictionaries().size());
        assertEquals(1, repo.allTemplates().size());
    }
}
