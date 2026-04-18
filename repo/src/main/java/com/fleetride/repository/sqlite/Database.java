package com.fleetride.repository.sqlite;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class Database implements Closeable {
    public static final class DbException extends RuntimeException {
        public DbException(String msg) { super(msg); }
        public DbException(String msg, Throwable cause) { super(msg, cause); }
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private final String url;
    private Connection connection;

    public Database(String url) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
        this.url = url;
        this.connection = open();
        execute("PRAGMA foreign_keys = ON");
        execute("PRAGMA journal_mode = WAL");
        execute("PRAGMA busy_timeout = 5000");
        new Migrations().applyAll(this);
    }

    public static Database inMemory() {
        return new Database("jdbc:sqlite::memory:");
    }

    public static Database file(String path) {
        return new Database("jdbc:sqlite:" + path);
    }

    private Connection open() {
        try {
            return DriverManager.getConnection(url);
        } catch (SQLException e) {
            throw new DbException("connect failed: " + url, e);
        }
    }

    public synchronized int update(String sql, StatementBinder binder) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DbException("update failed: " + sql, e);
        }
    }

    public synchronized <T> List<T> query(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DbException("query failed: " + sql, e);
        }
    }

    public synchronized <T> T inTransaction(Function<Database, T> work) {
        runUnchecked(() -> connection.setAutoCommit(false));
        try {
            T result = work.apply(this);
            runUnchecked(connection::commit);
            return result;
        } catch (RuntimeException e) {
            runUnchecked(connection::rollback);
            throw e;
        } finally {
            runUnchecked(() -> connection.setAutoCommit(true));
        }
    }

    public synchronized void execute(String sql) {
        try (java.sql.Statement s = connection.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new DbException("execute failed: " + sql, e);
        }
    }

    public void ping() {
        query("SELECT 1", ps -> {}, rs -> rs.getInt(1));
    }

    @Override
    public synchronized void close() {
        if (connection == null) return;
        Connection c = connection;
        connection = null;
        runUnchecked(c::close);
    }

    @FunctionalInterface
    interface SqlAction {
        void run() throws SQLException;
    }

    static void runUnchecked(SqlAction a) {
        try {
            a.run();
        } catch (SQLException e) {
            throw new DbException("sql failed", e);
        }
    }
}
