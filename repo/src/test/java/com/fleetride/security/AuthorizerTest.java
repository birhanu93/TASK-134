package com.fleetride.security;

import com.fleetride.domain.Role;
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AuthService;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizerTest {

    private AuthService auth;
    private Authorizer authz;

    @BeforeEach
    void setup() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        EncryptionService enc = new EncryptionService("k");
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "u-" + n.incrementAndGet();
        auth = new AuthService(users, enc, ids);
        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("disp", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();
        authz = new Authorizer(auth);
    }

    @Test
    void requiresAuthentication() {
        assertThrows(Authorizer.ForbiddenException.class,
                () -> authz.require(Permission.ORDER_CREATE));
    }

    @Test
    void requiresUnlockedSession() {
        auth.login("admin", "pw");
        auth.lock();
        assertThrows(Authorizer.ForbiddenException.class,
                () -> authz.require(Permission.ORDER_CREATE));
    }

    @Test
    void dispatcherCanCreateOrders() {
        auth.login("disp", "pw");
        assertEquals("disp", authz.require(Permission.ORDER_CREATE).username());
    }

    @Test
    void dispatcherCannotManageUsers() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> authz.require(Permission.USER_MANAGE));
    }

    @Test
    void financeCannotCreateOrders() {
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> authz.require(Permission.ORDER_CREATE));
    }

    @Test
    void adminAllowedEverywhere() {
        auth.login("admin", "pw");
        for (Permission p : Permission.values()) {
            authz.require(p);
        }
    }

    @Test
    void requireOwnershipAdminAlwaysPasses() {
        auth.login("admin", "pw");
        authz.requireOwnership(Permission.ORDER_READ, "someone-else");
    }

    @Test
    void requireOwnershipNullOwnerPasses() {
        auth.login("disp", "pw");
        authz.requireOwnership(Permission.ORDER_READ, null);
    }

    @Test
    void requireOwnershipOtherUserFails() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> authz.requireOwnership(Permission.ORDER_READ, "someone-else"));
    }

    @Test
    void requireOwnershipSameUserPasses() {
        String id = auth.login("disp", "pw").id();
        authz.requireOwnership(Permission.ORDER_READ, id);
    }

    @Test
    void canSeeAllForAdminAndFinance() {
        assertTrue(authz.canSeeAll(Role.ADMINISTRATOR));
        assertTrue(authz.canSeeAll(Role.FINANCE_CLERK));
        assertFalse(authz.canSeeAll(Role.DISPATCHER));
    }

    @Test
    void customMatrix() {
        EnumMap<Permission, java.util.Set<Role>> matrix = new EnumMap<>(Permission.class);
        matrix.put(Permission.ORDER_CREATE, EnumSet.of(Role.FINANCE_CLERK));
        Authorizer custom = new Authorizer(auth, matrix);
        auth.login("fin", "pw");
        custom.require(Permission.ORDER_CREATE);
        auth.logout();
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> custom.require(Permission.ORDER_CREATE));
    }

    @Test
    void unmappedPermissionForbidden() {
        EnumMap<Permission, java.util.Set<Role>> matrix = new EnumMap<>(Permission.class);
        Authorizer empty = new Authorizer(auth, matrix);
        auth.login("admin", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> empty.require(Permission.ORDER_CREATE));
    }

    @Test
    void permissionValues() {
        for (Permission p : Permission.values()) {
            assertEquals(p, Permission.valueOf(p.name()));
        }
    }
}
