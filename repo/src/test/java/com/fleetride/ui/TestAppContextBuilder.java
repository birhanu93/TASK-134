package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.service.Clock;
import com.fleetride.service.SignatureVerifier;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds an {@link AppContext} wired against a per-test temp directory. The context
 * uses an in-memory SQLite-backed file, a trusted RSA key freshly generated for
 * this test, and a deterministic id generator so assertions on generated ids are
 * stable within a single test.
 */
final class TestAppContextBuilder {
    private TestAppContextBuilder() {}

    static AppContext build(Path dir) {
        AtomicInteger counter = new AtomicInteger();
        AppContext.Config cfg = new AppContext.Config(
                Database.file(dir.resolve("fleetride.db").toString()),
                Clock.system(),
                () -> "t-" + counter.incrementAndGet(),
                "test-master-key",
                dir.resolve("attachments"),
                dir.resolve("updates"),
                dir.resolve("fleetride.log"),
                dir.resolve("machine-id"),
                trustedVerifier());
        return new AppContext(cfg);
    }

    static SignatureVerifier trustedVerifier() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair kp = g.generateKeyPair();
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";
            return SignatureVerifier.fromPem(pem);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
