package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UserAdminWindowTest extends FxTestBase {

    @Test
    void adminAddsDispatcher(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                UserAdminWindow w = new UserAdminWindow(ctx);
                w.build();
                w.username.setText("alice");
                w.password.setText("pw1");
                w.roleBox.setValue(Role.DISPATCHER);
                w.save();
                assertEquals("User registered.", w.feedback.getText());
                assertEquals("", w.username.getText());
                assertEquals("", w.password.getText());
                // list refreshed on save
                assertTrue(w.list.getItems().stream().anyMatch(u -> "alice".equals(u.username())));
            });

            assertTrue(ctx.userRepo.findByUsername("alice").isPresent());
            assertEquals(Role.DISPATCHER, ctx.userRepo.findByUsername("alice").get().role());
        } finally {
            ctx.close();
        }
    }

    @Test
    void duplicateUsernameSurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("alice", "pw1", Role.DISPATCHER);

            ErrorAlerts.reset();
            runFx(() -> {
                UserAdminWindow w = new UserAdminWindow(ctx);
                w.build();
                w.username.setText("alice");
                w.password.setText("pw2");
                w.roleBox.setValue(Role.FINANCE_CLERK);
                w.save();
            });
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void nonAdminCannotOpenUserAdmin(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("ops", "pw2", Role.FINANCE_CLERK);
            ctx.auth.logout();
            ctx.auth.login("ops", "pw2");

            runFx(() -> {
                UserAdminWindow w = new UserAdminWindow(ctx);
                // Authorizer.ForbiddenException on build: finance clerks cannot
                // reach user registration even if the window is instantiated.
                assertThrows(RuntimeException.class, w::build);
            });
        } finally {
            ctx.close();
        }
    }
}
