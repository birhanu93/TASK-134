package com.fleetride.integration;

import com.fleetride.AppContext;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.service.Clock;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.SignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that importing a signed update package <i>actually changes what the running
 * app reads</i>, not just the metadata. The running {@link AppContext} is asked for
 * template/dictionary values through {@code ConfigService} both before and after the
 * admin applies / rolls back updates, and the values must match the payload that is
 * currently active on disk.
 */
class UpdatePayloadActivationIntegrationTest {

    private static final class SignedPackage {
        final Path pkg;
        final Path sig;
        SignedPackage(Path pkg, Path sig) { this.pkg = pkg; this.sig = sig; }
    }

    private PublicKey publicKey;
    private PrivateKey privateKey;

    @Test
    void applyingAnUpdateChangesWhatConfigServiceReads(@TempDir Path dir) throws Exception {
        generateKeys();
        try (AppContext ctx = buildContext(dir, publicKey)) {

        // Baseline: the DB has a welcome template; no update payload is active yet, so
        // runtime reads come from the DB.
        ctx.auth.bootstrapAdministrator("admin", "pw");
        ctx.auth.login("admin", "pw");
        ctx.securedConfigService.setTemplate("welcome", "DB-hello {name}");
        assertEquals("DB-hello Alice",
                ctx.securedConfigService.render("welcome", Map.of("name", "Alice")),
                "before any update, config reads the DB-authored template");
        assertEquals(0, ctx.configService.overlayTemplateCount(),
                "no payload activated yet → empty overlay");

        // Apply an update package that ships a welcome template of its own. After
        // applying, the same key must resolve to the payload's value, proving the
        // active payload directory is wired into runtime resource loading.
        SignedPackage v1 = signedPackage(dir, "v1.pkg", Map.of(
                "templates/welcome.txt", "Payload-v1 hello {name}".getBytes(),
                "dictionaries/vehicles.txt", "STANDARD,PRIORITY".getBytes()));
        ctx.securedUpdateService.apply(v1.pkg, "1.1.0", v1.sig);
        assertEquals("Payload-v1 hello Alice",
                ctx.securedConfigService.render("welcome", Map.of("name", "Alice")),
                "after apply, template reads from the active payload");
        assertEquals("STANDARD,PRIORITY",
                ctx.securedConfigService.dictionary("vehicles").orElseThrow(),
                "payload dictionary should be visible through the overlay");
        assertTrue(ctx.configService.overlayTemplateCount() >= 1);
        assertEquals("1.1.0", ctx.securedUpdateService.currentVersion());

        // Apply a newer package with a different welcome template and an additional key.
        SignedPackage v2 = signedPackage(dir, "v2.pkg", Map.of(
                "templates/welcome.txt", "Payload-v2 greetings {name}".getBytes(),
                "templates/farewell.txt", "bye {name}".getBytes(),
                "dictionaries/vehicles.txt", "STANDARD,PRIORITY,VAN".getBytes()));
        ctx.securedUpdateService.apply(v2.pkg, "1.2.0", v2.sig);
        assertEquals("Payload-v2 greetings Alice",
                ctx.securedConfigService.render("welcome", Map.of("name", "Alice")));
        assertEquals("bye Alice",
                ctx.securedConfigService.render("farewell", Map.of("name", "Alice")));
        assertEquals("STANDARD,PRIORITY,VAN",
                ctx.securedConfigService.dictionary("vehicles").orElseThrow());

        // Roll back: runtime must observe the v1 payload again, and the v2-only key
        // must disappear from the overlay.
        ctx.securedUpdateService.rollback();
        assertEquals("1.1.0", ctx.securedUpdateService.currentVersion());
        assertEquals("Payload-v1 hello Alice",
                ctx.securedConfigService.render("welcome", Map.of("name", "Alice")),
                "rollback must flip runtime reads back to the prior payload");
        // farewell came only from v2; it should fall through to the DB, which has no
        // such template, so render must fail with a not-found error.
        assertThrows(IllegalArgumentException.class,
                () -> ctx.securedConfigService.render("farewell", Map.of("name", "Alice")),
                "keys that existed only in the newer payload must disappear after rollback");
        assertEquals("STANDARD,PRIORITY",
                ctx.securedConfigService.dictionary("vehicles").orElseThrow());

        // DB-only keys remain readable because the overlay is a read-through, not a
        // replacement. Add a non-overlapping key to the DB and confirm the merged view.
        ctx.securedConfigService.setDictionary("db-only", "still-here");
        assertEquals("still-here",
                ctx.securedConfigService.dictionary("db-only").orElseThrow(),
                "DB-authored entries still visible when the overlay doesn't shadow them");
        }
    }

    @Test
    void startupActivationPicksUpPreviouslyAppliedPayload(@TempDir Path dir) throws Exception {
        generateKeys();
        Path updateRoot = dir.resolve("upd");
        String packagedVersion = "5.5.5";

        // First process: bootstrap, apply update, shut down cleanly.
        try (AppContext ctx = buildContext(dir, publicKey)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            SignedPackage v = signedPackage(dir, "first.pkg", Map.of(
                    "templates/welcome.txt", "First-boot-hello".getBytes()));
            ctx.securedUpdateService.apply(v.pkg, packagedVersion, v.sig);
        }

        // Simulate a kill that left only active.pkg + DB metadata behind — the
        // versions/<version>/ directory is gone. The next process must re-extract on
        // startup and wire the payload into ConfigService; otherwise the app silently
        // runs on nothing.
        Path versionDir = updateRoot.resolve("versions/" + packagedVersion);
        deleteRecursively(versionDir);
        assertFalse(Files.exists(versionDir));

        // Second process: DB already has the admin user; AppContext.activateCurrentUpdate
        // is the startup hook FleetRideApp invokes, and it must restore the overlay.
        try (AppContext ctx = buildContext(dir, publicKey)) {
            ctx.auth.login("admin", "pw");
            ctx.activateCurrentUpdate();
            assertEquals("First-boot-hello",
                    ctx.securedConfigService.template("welcome").orElseThrow(),
                    "startup activation must re-extract the payload and refresh the overlay");
            assertTrue(Files.exists(versionDir),
                    "version directory should be back on disk after activation");
        }
    }

    @Test
    void applyFailureLeavesExistingOverlayUntouched(@TempDir Path dir) throws Exception {
        generateKeys();
        try (AppContext ctx = buildContext(dir, publicKey)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            SignedPackage good = signedPackage(dir, "good.pkg", Map.of(
                    "templates/welcome.txt", "good-payload".getBytes()));
            ctx.securedUpdateService.apply(good.pkg, "1.0.1", good.sig);
            assertEquals("good-payload",
                    ctx.securedConfigService.template("welcome").orElseThrow());

            // Craft a package signed with a foreign key so signature verification
            // fails. The apply must throw and leave the live overlay unchanged.
            KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
            kg.initialize(2048);
            KeyPair foreign = kg.generateKeyPair();
            Path badPkg = dir.resolve("bad.pkg");
            byte[] badBytes = zipBytes(Map.of(
                    "templates/welcome.txt", "evil-payload".getBytes()));
            Files.write(badPkg, badBytes);
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(foreign.getPrivate());
            s.update(badBytes);
            Path badSig = dir.resolve("bad.pkg.sig");
            Files.writeString(badSig, Base64.getEncoder().encodeToString(s.sign()));

            assertThrows(RuntimeException.class,
                    () -> ctx.securedUpdateService.apply(badPkg, "1.0.2", badSig));
            assertEquals("good-payload",
                    ctx.securedConfigService.template("welcome").orElseThrow(),
                    "rejected apply must not disturb the active overlay");
        }
    }

    // ---------- helpers ----------

    private AppContext buildContext(Path dir, PublicKey pk) {
        AtomicInteger n = new AtomicInteger();
        AppContext.Config cfg = new AppContext.Config(
                Database.file(dir.resolve("db.sqlite").toString()),
                Clock.system(),
                (IdGenerator) () -> "id-" + n.incrementAndGet(),
                "master-key",
                dir.resolve("att"),
                dir.resolve("upd"),
                dir.resolve("log.ndjson"),
                dir.resolve("machine-id"),
                SignatureVerifier.fromPem(
                        "-----BEGIN PUBLIC KEY-----\n"
                                + Base64.getEncoder().encodeToString(pk.getEncoded())
                                + "\n-----END PUBLIC KEY-----"));
        return new AppContext(cfg);
    }

    private void generateKeys() throws Exception {
        KeyPairGenerator kg = KeyPairGenerator.getInstance("RSA");
        kg.initialize(2048);
        KeyPair kp = kg.generateKeyPair();
        publicKey = kp.getPublic();
        privateKey = kp.getPrivate();
    }

    private SignedPackage signedPackage(Path dir, String name, Map<String, byte[]> entries) throws Exception {
        byte[] bytes = zipBytes(entries);
        Path pkg = dir.resolve(name);
        Files.write(pkg, bytes);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(privateKey);
        s.update(bytes);
        Path sig = dir.resolve(name + ".sig");
        Files.writeString(sig, Base64.getEncoder().encodeToString(s.sign()));
        return new SignedPackage(pkg, sig);
    }

    private static byte[] zipBytes(Map<String, byte[]> entries) throws Exception {
        // Use a deterministic-order copy so listing the zip entries is stable.
        Map<String, byte[]> ordered = new LinkedHashMap<>(entries);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : ordered.entrySet()) {
                zout.putNextEntry(new ZipEntry(e.getKey()));
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            java.util.List<Path> ordered = new java.util.ArrayList<>();
            walk.forEach(ordered::add);
            ordered.sort(java.util.Comparator.reverseOrder());
            for (Path p : ordered) Files.deleteIfExists(p);
        }
    }
}
