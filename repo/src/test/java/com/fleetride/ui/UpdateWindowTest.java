package com.fleetride.ui;

import com.fleetride.AppContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdateWindowTest extends FxTestBase {

    @Test
    void adminSeesCurrentVersionAndEmptyHistoryOnFreshInstall(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                UpdateWindow w = new UpdateWindow(ctx);
                w.build();
                assertTrue(w.versionLabel.getText().startsWith("Current version:"));
                assertEquals(0, w.history.getItems().size());
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void rollbackWithoutPriorRaisesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                UpdateWindow w = new UpdateWindow(ctx);
                w.build();
                w.rollbackBtn.fire();
            });
            assertNotNull(ErrorAlerts.lastError(), "rollback with no prior package should fail");
        } finally {
            ctx.close();
        }
    }

    @Test
    void applyWithBadSignatureReportsError(@TempDir Path dir) throws Exception {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Path pkg = dir.resolve("bogus.pkg");
            Path sig = dir.resolve("bogus.sig");
            java.nio.file.Files.write(pkg, new byte[]{1, 2, 3});
            java.nio.file.Files.write(sig, new byte[]{9, 9, 9});

            ErrorAlerts.reset();
            runFx(() -> {
                UpdateWindow w = new UpdateWindow(ctx);
                w.build();
                w.declaredVersion.setText("1.0.1");
                w.applyPackage(pkg, "1.0.1", sig);
            });
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void nonAdminSeesForbiddenOnBuild(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("ops", "pw2", com.fleetride.domain.Role.DISPATCHER);
            ctx.auth.logout();
            ctx.auth.login("ops", "pw2");

            runFx(() -> {
                UpdateWindow w = new UpdateWindow(ctx);
                w.build();
                assertTrue(w.versionLabel.getText().startsWith("forbidden:"));
            });
        } finally {
            ctx.close();
        }
    }
}
