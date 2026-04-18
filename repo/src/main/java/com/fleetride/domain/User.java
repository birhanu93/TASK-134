package com.fleetride.domain;

import java.util.Objects;

public final class User {
    private final String id;
    private final String username;
    private final String encryptedPassword;
    private final Role role;

    public User(String id, String username, String encryptedPassword, Role role) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username required");
        if (encryptedPassword == null) throw new IllegalArgumentException("encryptedPassword required");
        if (role == null) throw new IllegalArgumentException("role required");
        this.id = id;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.role = role;
    }

    public String id() { return id; }
    public String username() { return username; }
    public String encryptedPassword() { return encryptedPassword; }
    public Role role() { return role; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return Objects.equals(id, u.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
