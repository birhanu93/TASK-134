package com.fleetride.repository.sqlite;

import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.repository.UserRepository;

import java.util.List;
import java.util.Optional;

public final class SqliteUserRepository implements UserRepository {
    private final Database db;

    public SqliteUserRepository(Database db) { this.db = db; }

    @Override
    public void save(User u) {
        db.update(
                "INSERT INTO users(id, username, encrypted_password, role) VALUES(?,?,?,?) " +
                        "ON CONFLICT(id) DO UPDATE SET username=excluded.username, " +
                        "encrypted_password=excluded.encrypted_password, role=excluded.role",
                ps -> {
                    ps.setString(1, u.id());
                    ps.setString(2, u.username());
                    ps.setString(3, u.encryptedPassword());
                    ps.setString(4, u.role().name());
                });
    }

    @Override
    public Optional<User> findById(String id) {
        List<User> found = db.query("SELECT * FROM users WHERE id = ?",
                ps -> ps.setString(1, id), this::map);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        List<User> found = db.query("SELECT * FROM users WHERE username = ?",
                ps -> ps.setString(1, username), this::map);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    @Override
    public List<User> findAll() {
        return db.query("SELECT * FROM users ORDER BY username", ps -> {}, this::map);
    }

    private User map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new User(rs.getString("id"), rs.getString("username"),
                rs.getString("encrypted_password"), Role.valueOf(rs.getString("role")));
    }
}
