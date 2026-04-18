package com.fleetride.repository.sqlite;

import com.fleetride.domain.Money;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SqlSupportTest {

    @Test
    void moneyToCentsAndBack() {
        assertEquals(350L, SqlSupport.moneyToCents(Money.of("3.50")));
        assertEquals(Money.of("3.50"), SqlSupport.centsToMoney(350L));
    }

    @Test
    void dateFormatAndParse() {
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0, 0);
        String s = SqlSupport.dt(t);
        assertEquals(t, SqlSupport.parseDt(s));
        assertNull(SqlSupport.dt(null));
        assertNull(SqlSupport.parseDt(null));
    }

    @Test
    void privateConstructor() throws Exception {
        Constructor<SqlSupport> c = SqlSupport.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }

    @Test
    void nullableIntAndLongWhenNull() throws Exception {
        try (Database db = Database.inMemory()) {
            db.update("CREATE TABLE t(a INTEGER, b INTEGER)", ps -> {});
            db.update("INSERT INTO t(a, b) VALUES(NULL, NULL)", ps -> {});
            db.query("SELECT a, b FROM t", ps -> {}, rs -> {
                assertNull(SqlSupport.getNullableInt(rs, "a"));
                assertNull(SqlSupport.getNullableLong(rs, "b"));
                return null;
            });
        }
    }

    @Test
    void nullableIntAndLongWhenPresent() throws Exception {
        try (Database db = Database.inMemory()) {
            db.update("CREATE TABLE t(a INTEGER, b INTEGER)", ps -> {});
            db.update("INSERT INTO t(a, b) VALUES(7, 9999999999)", ps -> {});
            db.query("SELECT a, b FROM t", ps -> {}, rs -> {
                assertEquals(7, SqlSupport.getNullableInt(rs, "a"));
                assertEquals(9999999999L, SqlSupport.getNullableLong(rs, "b"));
                return null;
            });
        }
    }
}
