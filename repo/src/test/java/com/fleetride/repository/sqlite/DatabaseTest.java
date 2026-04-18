package com.fleetride.repository.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @Test
    void inMemoryBootstraps() {
        try (Database db = Database.inMemory()) {
            db.ping();
        }
    }

    @Test
    void fileBootstraps(@TempDir Path dir) {
        try (Database db = Database.file(dir.resolve("t.db").toString())) {
            db.ping();
        }
    }

    @Test
    void rejectsBlankUrl() {
        assertThrows(IllegalArgumentException.class, () -> new Database(null));
        assertThrows(IllegalArgumentException.class, () -> new Database(" "));
    }

    @Test
    void invalidUrlFails() {
        assertThrows(Database.DbException.class, () -> new Database("jdbc:notarealdb:/"));
    }

    @Test
    void updateQueryAndTransaction() {
        try (Database db = Database.inMemory()) {
            db.update("CREATE TABLE t (id TEXT PRIMARY KEY, v TEXT)", ps -> {});
            db.inTransaction(d -> {
                d.update("INSERT INTO t(id, v) VALUES(?, ?)", ps -> {
                    ps.setString(1, "1"); ps.setString(2, "a");
                });
                return null;
            });
            List<String> vals = db.query("SELECT v FROM t WHERE id = ?",
                    ps -> ps.setString(1, "1"), rs -> rs.getString("v"));
            assertEquals(1, vals.size());
            assertEquals("a", vals.get(0));
        }
    }

    @Test
    void transactionRollsBackOnException() {
        try (Database db = Database.inMemory()) {
            db.update("CREATE TABLE t (id TEXT PRIMARY KEY)", ps -> {});
            assertThrows(RuntimeException.class, () -> db.inTransaction(d -> {
                d.update("INSERT INTO t(id) VALUES(?)", ps -> ps.setString(1, "1"));
                throw new RuntimeException("rollback me");
            }));
            assertEquals(0, db.query("SELECT id FROM t", ps -> {}, rs -> rs.getString("id")).size());
        }
    }

    @Test
    void updateWithBadSqlThrows() {
        try (Database db = Database.inMemory()) {
            assertThrows(Database.DbException.class,
                    () -> db.update("INSERT INTO nope VALUES(?)", ps -> ps.setString(1, "x")));
        }
    }

    @Test
    void queryWithBadSqlThrows() {
        try (Database db = Database.inMemory()) {
            assertThrows(Database.DbException.class,
                    () -> db.query("SELECT * FROM nope", ps -> {}, rs -> ""));
        }
    }

    @Test
    void executeWithBadSqlThrows() {
        try (Database db = Database.inMemory()) {
            assertThrows(Database.DbException.class, () -> db.execute("this is not sql"));
        }
    }

    @Test
    void closeIsIdempotent() {
        Database db = Database.inMemory();
        db.close();
        db.close();
    }

    @Test
    void exceptionHierarchy() {
        Database.DbException e1 = new Database.DbException("m");
        Database.DbException e2 = new Database.DbException("m", new RuntimeException());
        assertEquals("m", e1.getMessage());
        assertNotNull(e2.getCause());
    }

    @Test
    void migrationsApplyAll() {
        try (Database db = Database.inMemory()) {
            new Migrations().applyAll(db);
            db.query("SELECT * FROM users", ps -> {}, rs -> "");
            db.query("SELECT * FROM orders", ps -> {}, rs -> "");
        }
    }

    @Test
    void runUncheckedWrapsSqlException() {
        assertThrows(Database.DbException.class,
                () -> Database.runUnchecked(() -> { throw new java.sql.SQLException("boom"); }));
    }

    @Test
    void runUncheckedHappyPath() {
        int[] count = {0};
        Database.runUnchecked(() -> count[0]++);
        assertEquals(1, count[0]);
    }
}
