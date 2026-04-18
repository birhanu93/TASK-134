package com.fleetride.ui;

import com.fleetride.AppContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HealthAndAuditWindowTest extends FxTestBase {

    @Test
    void adminSeesHealthAuditAndJobRuns(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            // Record at least one audit event so the audit tab has a line to render.
            ctx.audit.record("admin", "TEST_EVENT", "target-1", "smoke");

            runFx(() -> {
                HealthAndAuditWindow w = new HealthAndAuditWindow(ctx);
                w.build();
                assertTrue(w.healthStatus.getText().contains("Overall:"));
                w.healthRefresh.fire();
                assertTrue(w.healthStatus.getText().contains("Overall:"));

                w.jobRefresh.fire();
                w.auditRefresh.fire();
                assertFalse(w.auditList.getItems().isEmpty(),
                        "recorded audit events should appear in the audit tab");
                assertTrue(w.auditList.getItems().get(0).contains("TEST_EVENT"));
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void nonAdminSeesForbiddenHealthBanner(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("ops", "pw2", com.fleetride.domain.Role.DISPATCHER);
            ctx.auth.logout();
            ctx.auth.login("ops", "pw2");

            runFx(() -> {
                HealthAndAuditWindow w = new HealthAndAuditWindow(ctx);
                w.build();
                assertTrue(w.healthStatus.getText().startsWith("forbidden:"));
                assertFalse(w.jobList.getItems().isEmpty());
                assertTrue(w.jobList.getItems().get(0).startsWith("forbidden:"));
                assertTrue(w.auditList.getItems().get(0).startsWith("forbidden:"));
            });
        } finally {
            ctx.close();
        }
    }
}
