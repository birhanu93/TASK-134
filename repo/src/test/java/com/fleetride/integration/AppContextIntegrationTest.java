package com.fleetride.integration;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.service.Clock;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.SignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AppContextIntegrationTest {

    private SignatureVerifier verifier() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        return SignatureVerifier.fromPem(pem);
    }

    @Test
    void wiresUpAndHealthChecks(@TempDir Path dir) throws Exception {
        AtomicInteger n = new AtomicInteger();
        AppContext.Config cfg = new AppContext.Config(
                Database.file(dir.resolve("db.sqlite").toString()),
                Clock.system(),
                () -> "id-" + n.incrementAndGet(),
                "master",
                dir.resolve("att"),
                dir.resolve("upd"),
                dir.resolve("log.ndjson"),
                dir.resolve("machine-id"),
                verifier());
        try (AppContext ctx = new AppContext(cfg)) {
            assertNotNull(ctx.healthService);
            assertNotNull(ctx.database());
            assertNotNull(ctx.encryption());
            assertNotNull(ctx.clock());
            assertNotNull(ctx.ids());

            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            assertEquals(0, ctx.recoverPendingCheckpoints().inspected());
            assertNotNull(ctx.securedConfigService.pricing());
            assertNotNull(ctx.securedOrderService.list());
            assertNotNull(ctx.securedPaymentService.listAll());
        }
    }
}
