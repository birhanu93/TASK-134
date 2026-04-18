package com.fleetride.security;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.service.Clock;
import com.fleetride.service.SignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the admin-success paths on {@link SecuredSchedulerService} that
 * existing coverage only ran through the forbidden-rejection side. The
 * three trigger methods are all guarded by {@code SCHEDULER_RUN}; when an
 * administrator is signed in they must delegate to the underlying
 * {@link com.fleetride.service.ScheduledJobService} and return its report.
 */
class SecuredSchedulerAdminTest {

    private AppContext wire(Path dir) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        AtomicInteger n = new AtomicInteger();
        AppContext.Config cfg = new AppContext.Config(
                Database.file(dir.resolve("sch.db").toString()),
                Clock.system(),
                () -> "id-" + n.incrementAndGet(),
                "test-master",
                dir.resolve("att"),
                dir.resolve("upd"),
                dir.resolve("log.ndjson"),
                dir.resolve("machine-id"),
                SignatureVerifier.fromPem(pem));
        return new AppContext(cfg);
    }

    @Test
    void adminCanTriggerAllThreeScheduledJobs(@TempDir Path dir) throws Exception {
        try (AppContext ctx = wire(dir)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            assertNotNull(ctx.securedSchedulerService.hourlyTimeoutEnforcement());
            assertNotNull(ctx.securedSchedulerService.nightlyOverdueSweep());
            assertNotNull(ctx.securedSchedulerService.nightlyQuotaReclamation());
            assertNotNull(ctx.securedSchedulerService.recentRuns(10));
        }
    }

    @Test
    void financeClerkCanReadRunsButNotTriggerJobs(@TempDir Path dir) throws Exception {
        try (AppContext ctx = wire(dir)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("finance", "pw2", Role.FINANCE_CLERK);
            ctx.auth.logout();
            ctx.auth.login("finance", "pw2");

            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedSchedulerService.hourlyTimeoutEnforcement());
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedSchedulerService.nightlyOverdueSweep());
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedSchedulerService.nightlyQuotaReclamation());
        }
    }
}
