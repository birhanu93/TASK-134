package com.fleetride.security;

import com.fleetride.domain.Role;
import com.fleetride.repository.InMemoryAuditRepository;
import com.fleetride.repository.InMemoryJobRunRepository;
import com.fleetride.repository.InMemoryUpdateHistoryRepository;
import com.fleetride.repository.InMemoryUserRepository;
import com.fleetride.service.AuditService;
import com.fleetride.service.AuthService;
import com.fleetride.service.Clock;
import com.fleetride.service.EncryptionService;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.ScheduledJobService;
import com.fleetride.service.SignatureVerifier;
import com.fleetride.service.UpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that privileged read/write paths go through Authorizer regardless of UI gating.
 * A malicious UI bypass that reaches the raw services would succeed; going through the
 * secured wrappers must fail-closed for non-admins.
 */
class SecuredAdminBoundariesTest {

    private AuthService auth;
    private SecuredAuditService securedAudit;
    private SecuredSchedulerService securedScheduler;
    private SecuredUpdateService securedUpdate;

    @BeforeEach
    void setup(@TempDir Path tmp) throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        EncryptionService enc = new EncryptionService("k");
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "x-" + n.incrementAndGet();
        Clock clock = () -> LocalDateTime.of(2026, 4, 18, 10, 0);

        auth = new AuthService(users, enc, ids);
        Authorizer authz = new Authorizer(auth);

        AuditService audit = new AuditService(new InMemoryAuditRepository(), ids, clock);
        ScheduledJobService scheduler = new ScheduledJobService(null, null, null, audit, clock,
                ids, new InMemoryJobRunRepository());

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        PublicKey pk = g.generateKeyPair().getPublic();
        SignatureVerifier verifier = SignatureVerifier.fromPem(
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getEncoder().encodeToString(pk.getEncoded())
                        + "\n-----END PUBLIC KEY-----");
        UpdateService update = new UpdateService(tmp.resolve("updates"), verifier,
                new InMemoryUpdateHistoryRepository(), clock);

        securedAudit = new SecuredAuditService(audit, authz);
        securedScheduler = new SecuredSchedulerService(scheduler, authz);
        securedUpdate = new SecuredUpdateService(update, authz);

        auth.bootstrapAdministrator("admin", "pw");
        auth.login("admin", "pw");
        auth.register("disp", "pw", Role.DISPATCHER);
        auth.register("fin", "pw", Role.FINANCE_CLERK);
        auth.logout();
    }

    @Test
    void dispatcherCannotReadAudit() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedAudit.all());
    }

    @Test
    void financeCannotReadAudit() {
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedAudit.all());
    }

    @Test
    void adminReadsAudit() {
        auth.login("admin", "pw");
        assertNotNull(securedAudit.all());
    }

    @Test
    void dispatcherCannotReadJobRuns() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedScheduler.recentRuns(10));
    }

    @Test
    void financeCannotReadJobRuns() {
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedScheduler.recentRuns(10));
    }

    @Test
    void adminReadsJobRuns() {
        auth.login("admin", "pw");
        assertNotNull(securedScheduler.recentRuns(10));
    }

    @Test
    void dispatcherCannotTriggerJobs() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedScheduler.hourlyTimeoutEnforcement());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedScheduler.nightlyOverdueSweep());
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedScheduler.nightlyQuotaReclamation());
    }

    @Test
    void dispatcherCannotReadUpdateHistory() {
        auth.login("disp", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedUpdate.currentVersion());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedUpdate.history());
    }

    @Test
    void adminCanReadUpdateReadsOnHappyPath() {
        auth.login("admin", "pw");
        assertNotNull(securedUpdate.currentVersion());
        assertNotNull(securedUpdate.history());
        assertNotNull(securedUpdate.activePayloadRoot());
    }

    @Test
    void dispatcherCannotApplyUpdate(@TempDir Path tmp) {
        auth.login("disp", "pw");
        Path pkg = tmp.resolve("fake.pkg");
        Path sig = tmp.resolve("fake.pkg.sig");
        assertThrows(Authorizer.ForbiddenException.class,
                () -> securedUpdate.apply(pkg, "9.9.9", sig));
    }

    @Test
    void financeCannotRollbackUpdate() {
        auth.login("fin", "pw");
        assertThrows(Authorizer.ForbiddenException.class, () -> securedUpdate.rollback());
    }

    @Test
    void lockedAdminCannotRead() {
        auth.login("admin", "pw");
        auth.lock();
        assertThrows(Authorizer.ForbiddenException.class, () -> securedAudit.all());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedScheduler.recentRuns(5));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedUpdate.currentVersion());
    }

    @Test
    void unauthenticatedCallsRejected() {
        assertThrows(Authorizer.ForbiddenException.class, () -> securedAudit.all());
        assertThrows(Authorizer.ForbiddenException.class, () -> securedScheduler.recentRuns(5));
        assertThrows(Authorizer.ForbiddenException.class, () -> securedUpdate.currentVersion());
    }
}
