package com.fleetride.repository.sqlite;

import com.fleetride.domain.Invoice;
import com.fleetride.domain.Money;
import com.fleetride.repository.UpdateHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteInvoiceAndConfigAndUpdateTest {

    private Database db;
    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    @BeforeEach
    void setup() { db = Database.inMemory(); }

    @AfterEach
    void teardown() { db.close(); }

    @Test
    void invoiceRoundTripAllStates() {
        SqliteInvoiceRepository repo = new SqliteInvoiceRepository(db);
        Invoice issued = new Invoice("i1", "o1", "c1", Money.of("25.00"), t, "notes");
        repo.save(issued);
        Invoice loaded = repo.findById("i1").orElseThrow();
        assertEquals(Invoice.Status.ISSUED, loaded.status());
        assertEquals("notes", loaded.notes());

        Invoice paid = new Invoice("i2", "o1", "c1", Money.of("10"), t, null);
        paid.markPaid(t.plusHours(1));
        repo.save(paid);
        assertEquals(Invoice.Status.PAID, repo.findById("i2").orElseThrow().status());
        assertEquals(t.plusHours(1), repo.findById("i2").orElseThrow().paidAt());

        Invoice canceled = new Invoice("i3", "o1", "c1", Money.of("5"), t, null);
        canceled.cancel(t.plusHours(2));
        repo.save(canceled);
        assertEquals(Invoice.Status.CANCELED, repo.findById("i3").orElseThrow().status());

        assertEquals(3, repo.findByOrder("o1").size());
        assertEquals(1, repo.findByStatus(Invoice.Status.ISSUED).size());
        assertEquals(3, repo.findAll().size());
        assertTrue(repo.findById("no").isEmpty());

        // upsert: mark issued -> paid
        issued.markPaid(t.plusMinutes(5));
        repo.save(issued);
        assertEquals(Invoice.Status.PAID, repo.findById("i1").orElseThrow().status());
    }

    @Test
    void configRepositoryRoundTrip() {
        SqliteConfigRepository repo = new SqliteConfigRepository(db);
        repo.setSetting("k", "v");
        assertEquals("v", repo.getSetting("k").orElseThrow());
        repo.setSetting("k", "v2");
        assertEquals("v2", repo.getSetting("k").orElseThrow());
        assertTrue(repo.getSetting("missing").isEmpty());
        assertEquals(1, repo.allSettings().size());

        repo.setDictionary("dk", "dv");
        assertEquals("dv", repo.getDictionary("dk").orElseThrow());
        assertTrue(repo.getDictionary("missing").isEmpty());
        assertEquals(1, repo.allDictionaries().size());

        repo.setTemplate("tk", "tv");
        assertEquals("tv", repo.getTemplate("tk").orElseThrow());
        assertTrue(repo.getTemplate("missing").isEmpty());
        assertEquals(1, repo.allTemplates().size());
    }

    @Test
    void updateHistoryRoundTrip() {
        SqliteUpdateHistoryRepository repo = new SqliteUpdateHistoryRepository(db);
        assertEquals("1.0.0", repo.currentVersion());

        long s1 = repo.append("1.1", Path.of("/tmp/p1"), t);
        long s2 = repo.append("1.2", Path.of("/tmp/p2"), t.plusMinutes(1));
        assertTrue(s2 > s1);

        UpdateHistoryRepository.Record latest = repo.peekLatest().orElseThrow();
        assertEquals("1.2", latest.version());
        assertEquals(Path.of("/tmp/p2"), latest.packagePath());

        List<UpdateHistoryRepository.Record> all = repo.listAll();
        assertEquals(2, all.size());
        assertEquals(s1, all.get(0).seq());
        assertEquals(s2, all.get(1).seq());

        repo.setCurrentVersion("1.2");
        assertEquals("1.2", repo.currentVersion());

        repo.deleteBySeq(s2);
        assertEquals("1.1", repo.peekLatest().orElseThrow().version());
        repo.deleteBySeq(s1);
        assertTrue(repo.peekLatest().isEmpty());
    }

    @Test
    void updateHistoryDoubleInitKeepsDefault() {
        // constructing twice should not wipe the stored version
        SqliteUpdateHistoryRepository first = new SqliteUpdateHistoryRepository(db);
        first.setCurrentVersion("1.9");
        SqliteUpdateHistoryRepository second = new SqliteUpdateHistoryRepository(db);
        assertEquals("1.9", second.currentVersion());
    }
}
