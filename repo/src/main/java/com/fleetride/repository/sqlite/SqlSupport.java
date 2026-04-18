package com.fleetride.repository.sqlite;

import com.fleetride.domain.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class SqlSupport {
    static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private SqlSupport() {}

    static long moneyToCents(Money m) {
        return m.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static Money centsToMoney(long cents) {
        return new Money(BigDecimal.valueOf(cents).movePointLeft(2));
    }

    static String dt(LocalDateTime t) {
        return t == null ? null : FMT.format(t);
    }

    static LocalDateTime parseDt(String s) {
        return s == null ? null : LocalDateTime.parse(s, FMT);
    }

    static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Long getNullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
