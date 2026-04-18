package com.fleetride.repository.sqlite;

import com.fleetride.repository.ConfigRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SqliteConfigRepository implements ConfigRepository {
    private final Database db;

    public SqliteConfigRepository(Database db) { this.db = db; }

    @Override
    public Optional<String> getSetting(String key) { return getFrom("settings", key); }

    @Override
    public void setSetting(String key, String value) { upsertInto("settings", key, value); }

    @Override
    public Map<String, String> allSettings() { return allFrom("settings"); }

    @Override
    public Optional<String> getDictionary(String key) { return getFrom("dictionaries", key); }

    @Override
    public void setDictionary(String key, String value) { upsertInto("dictionaries", key, value); }

    @Override
    public Map<String, String> allDictionaries() { return allFrom("dictionaries"); }

    @Override
    public Optional<String> getTemplate(String key) { return getFrom("templates", key); }

    @Override
    public void setTemplate(String key, String value) { upsertInto("templates", key, value); }

    @Override
    public Map<String, String> allTemplates() { return allFrom("templates"); }

    private Optional<String> getFrom(String table, String key) {
        List<String> rows = db.query("SELECT value FROM " + table + " WHERE key = ?",
                ps -> ps.setString(1, key), rs -> rs.getString("value"));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private void upsertInto(String table, String key, String value) {
        db.update("INSERT INTO " + table + "(key, value) VALUES(?,?) " +
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                ps -> {
                    ps.setString(1, key);
                    ps.setString(2, value);
                });
    }

    private Map<String, String> allFrom(String table) {
        Map<String, String> out = new HashMap<>();
        for (String[] row : db.query("SELECT key, value FROM " + table, ps -> {},
                rs -> new String[]{rs.getString("key"), rs.getString("value")})) {
            out.put(row[0], row[1]);
        }
        return out;
    }
}
