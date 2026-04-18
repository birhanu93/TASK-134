package com.fleetride.service;

import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.repository.UserRepository;

import java.util.Optional;

public final class AuthService {
    public static final class AuthException extends RuntimeException {
        public AuthException(String msg) { super(msg); }
    }

    private final UserRepository users;
    private final EncryptionService encryption;
    private final IdGenerator ids;
    private User currentUser;
    private boolean locked = false;

    public AuthService(UserRepository users, EncryptionService encryption, IdGenerator ids) {
        this.users = users;
        this.encryption = encryption;
        this.ids = ids;
    }

    /**
     * Create the initial administrator. Only succeeds when the user table is empty;
     * callers must have out-of-band authority to invoke this (e.g. a CLI tool, an
     * operator-provisioned env secret at startup). Never called implicitly from the UI.
     */
    public User bootstrapAdministrator(String username, String password) {
        if (!users.findAll().isEmpty()) {
            throw new AuthException("bootstrap not allowed: users already exist");
        }
        return registerInternal(username, password, Role.ADMINISTRATOR);
    }

    /**
     * Register a new user. Requires an already-logged-in administrator session.
     */
    public User register(String username, String password, Role role) {
        if (currentUser == null || locked) {
            throw new AuthException("not authenticated");
        }
        if (currentUser.role() != Role.ADMINISTRATOR) {
            throw new AuthException("only administrators may register users");
        }
        return registerInternal(username, password, role);
    }

    private User registerInternal(String username, String password, Role role) {
        if (users.findByUsername(username).isPresent()) {
            throw new AuthException("username exists");
        }
        User u = new User(ids.next(), username, encryption.encryptPassword(password), role);
        users.save(u);
        return u;
    }

    public User login(String username, String password) {
        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty()) throw new AuthException("unknown user");
        User u = found.get();
        if (!encryption.verifyPassword(password, u.encryptedPassword())) {
            throw new AuthException("bad password");
        }
        this.currentUser = u;
        this.locked = false;
        return u;
    }

    public void logout() {
        this.currentUser = null;
        this.locked = false;
    }

    public void lock() {
        if (currentUser == null) throw new AuthException("not logged in");
        this.locked = true;
    }

    public void unlock(String password) {
        if (currentUser == null) throw new AuthException("not logged in");
        if (!encryption.verifyPassword(password, currentUser.encryptedPassword())) {
            throw new AuthException("bad password");
        }
        this.locked = false;
    }

    public boolean isLocked() { return locked; }
    public Optional<User> currentUser() { return Optional.ofNullable(currentUser); }

    public void requireRole(Role... allowed) {
        if (currentUser == null) throw new AuthException("not logged in");
        if (locked) throw new AuthException("session locked");
        for (Role r : allowed) {
            if (currentUser.role() == r) return;
        }
        throw new AuthException("forbidden");
    }
}
