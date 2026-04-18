package com.fleetride.service;

import com.fleetride.domain.Role;
import com.fleetride.repository.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private InMemoryUserRepository repo;
    private EncryptionService enc;
    private AuthService svc;

    @BeforeEach
    void setup() {
        repo = new InMemoryUserRepository();
        enc = new EncryptionService("master-key");
        AtomicInteger n = new AtomicInteger();
        svc = new AuthService(repo, enc, () -> "u-" + n.incrementAndGet());
    }

    private void bootstrapAdmin() {
        svc.bootstrapAdministrator("admin", "adminpw");
        svc.login("admin", "adminpw");
    }

    @Test
    void bootstrapOnlyWhenNoUsers() {
        svc.bootstrapAdministrator("admin", "pw");
        assertThrows(AuthService.AuthException.class,
                () -> svc.bootstrapAdministrator("admin2", "pw"));
    }

    @Test
    void bootstrappedAdminCanLogin() {
        svc.bootstrapAdministrator("root", "pw");
        assertEquals(Role.ADMINISTRATOR, svc.login("root", "pw").role());
    }

    @Test
    void registerRequiresLoggedInAdministrator() {
        // not logged in
        assertThrows(AuthService.AuthException.class,
                () -> svc.register("alice", "pw", Role.DISPATCHER));
        bootstrapAdmin();
        svc.lock();
        assertThrows(AuthService.AuthException.class,
                () -> svc.register("alice", "pw", Role.DISPATCHER));
    }

    @Test
    void nonAdminCannotRegister() {
        bootstrapAdmin();
        svc.register("disp", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("disp", "pw");
        assertThrows(AuthService.AuthException.class,
                () -> svc.register("other", "pw", Role.DISPATCHER));
    }

    @Test
    void registerAndLogin() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        assertEquals("alice", svc.login("alice", "pw").username());
        assertTrue(svc.currentUser().isPresent());
    }

    @Test
    void registerDuplicateUserFails() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        assertThrows(AuthService.AuthException.class,
                () -> svc.register("alice", "pw2", Role.FINANCE_CLERK));
    }

    @Test
    void loginUnknownFails() {
        assertThrows(AuthService.AuthException.class, () -> svc.login("bob", "pw"));
    }

    @Test
    void loginBadPasswordFails() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        assertThrows(AuthService.AuthException.class, () -> svc.login("alice", "wrong"));
    }

    @Test
    void logout() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("alice", "pw");
        svc.logout();
        assertFalse(svc.currentUser().isPresent());
    }

    @Test
    void lockAndUnlock() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("alice", "pw");
        svc.lock();
        assertTrue(svc.isLocked());
        svc.unlock("pw");
        assertFalse(svc.isLocked());
    }

    @Test
    void lockBeforeLoginThrows() {
        assertThrows(AuthService.AuthException.class, () -> svc.lock());
    }

    @Test
    void unlockBeforeLoginThrows() {
        assertThrows(AuthService.AuthException.class, () -> svc.unlock("x"));
    }

    @Test
    void unlockBadPasswordThrows() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("alice", "pw");
        svc.lock();
        assertThrows(AuthService.AuthException.class, () -> svc.unlock("bad"));
    }

    @Test
    void requireRoleAllowed() {
        svc.bootstrapAdministrator("admin", "pw");
        svc.login("admin", "pw");
        svc.requireRole(Role.ADMINISTRATOR, Role.DISPATCHER);
    }

    @Test
    void requireRoleNotLoggedInThrows() {
        assertThrows(AuthService.AuthException.class, () -> svc.requireRole(Role.DISPATCHER));
    }

    @Test
    void requireRoleLockedThrows() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("alice", "pw");
        svc.lock();
        assertThrows(AuthService.AuthException.class, () -> svc.requireRole(Role.DISPATCHER));
    }

    @Test
    void requireRoleForbidden() {
        bootstrapAdmin();
        svc.register("alice", "pw", Role.DISPATCHER);
        svc.logout();
        svc.login("alice", "pw");
        assertThrows(AuthService.AuthException.class, () -> svc.requireRole(Role.ADMINISTRATOR));
    }
}
