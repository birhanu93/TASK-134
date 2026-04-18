package com.fleetride.service;

import com.fleetride.repository.InMemoryUpdateHistoryRepository;
import com.fleetride.repository.UpdateHistoryRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {

    private static KeyPair keyPair;
    private final Clock clock = () -> LocalDateTime.of(2026, 3, 27, 10, 0);

    @BeforeAll
    static void setupKeys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        keyPair = g.generateKeyPair();
    }

    private Path writeSigned(Path dir, String name, byte[] data, PrivateKey key) throws Exception {
        // Wrap the raw bytes in a zip so UpdateService can actually extract the payload.
        byte[] zipBytes = zipOf(Map.of("payload.txt", data));
        Path pkg = dir.resolve(name);
        Files.write(pkg, zipBytes);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(zipBytes);
        byte[] sig = s.sign();
        Path sigFile = dir.resolve(name + ".sig");
        Files.writeString(sigFile, Base64.getEncoder().encodeToString(sig));
        return pkg;
    }

    private static byte[] zipOf(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zout.putNextEntry(new ZipEntry(e.getKey()));
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private String exportPem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private UpdateService newService(Path root, UpdateHistoryRepository history) {
        return new UpdateService(root, SignatureVerifier.fromPem(exportPem(keyPair.getPublic())),
                history, clock);
    }

    @Test
    void applyAndRollback(@TempDir Path dir) throws Exception {
        byte[] v1 = "v1-package".getBytes();
        Path v1File = writeSigned(dir, "v1.pkg", v1, keyPair.getPrivate());
        Path v1Sig = dir.resolve("v1.pkg.sig");

        InMemoryUpdateHistoryRepository history = new InMemoryUpdateHistoryRepository();
        UpdateService svc = newService(dir.resolve("root"), history);

        assertEquals("1.0.0", svc.currentVersion());
        svc.apply(v1File, "1.1.0", v1Sig);
        assertEquals("1.1.0", svc.currentVersion());
        assertTrue(Files.exists(dir.resolve("root/active.pkg")));

        svc.apply(v1File, "1.2.0", v1Sig);
        assertEquals("1.2.0", svc.currentVersion());
        assertEquals(1, svc.history().size());

        svc.rollback();
        assertEquals("1.1.0", svc.currentVersion());
    }

    @Test
    void historyPersistsAcrossServiceInstances(@TempDir Path dir) throws Exception {
        byte[] v1 = "v1".getBytes();
        Path v1File = writeSigned(dir, "v1.pkg", v1, keyPair.getPrivate());
        Path v1Sig = dir.resolve("v1.pkg.sig");
        InMemoryUpdateHistoryRepository history = new InMemoryUpdateHistoryRepository();
        UpdateService first = newService(dir.resolve("root"), history);
        first.apply(v1File, "1.1.0", v1Sig);
        first.apply(v1File, "1.2.0", v1Sig);

        UpdateService second = newService(dir.resolve("root"), history);
        assertEquals("1.2.0", second.currentVersion());
        assertEquals(1, second.history().size());
        second.rollback();
        assertEquals("1.1.0", second.currentVersion());
    }

    @Test
    void rejectsNullInstallRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateService(null, SignatureVerifier.fromPem(exportPem(keyPair.getPublic())),
                        new InMemoryUpdateHistoryRepository(), clock));
    }

    @Test
    void rejectsNullVerifier(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateService(dir, null, new InMemoryUpdateHistoryRepository(), clock));
    }

    @Test
    void rejectsNullHistory(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateService(dir, SignatureVerifier.fromPem(exportPem(keyPair.getPublic())),
                        null, clock));
    }

    @Test
    void rejectsNullClock(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateService(dir, SignatureVerifier.fromPem(exportPem(keyPair.getPublic())),
                        new InMemoryUpdateHistoryRepository(), null));
    }

    @Test
    void applyRejectsBlankVersion(@TempDir Path dir) throws Exception {
        Path v1File = writeSigned(dir, "v.pkg", "x".getBytes(), keyPair.getPrivate());
        Path sig = dir.resolve("v.pkg.sig");
        UpdateService svc = newService(dir, new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(v1File, null, sig));
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(v1File, " ", sig));
    }

    @Test
    void applyMissingPackage(@TempDir Path dir) {
        UpdateService svc = newService(dir, new InMemoryUpdateHistoryRepository());
        Path sig = dir.resolve("sig");
        assertThrows(UpdateService.UpdateException.class,
                () -> svc.apply(dir.resolve("missing.pkg"), "2", sig));
    }

    @Test
    void applyMissingSignatureFile(@TempDir Path dir) throws Exception {
        Path v1File = writeSigned(dir, "v.pkg", "x".getBytes(), keyPair.getPrivate());
        UpdateService svc = newService(dir, new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(v1File, "2", null));
        assertThrows(UpdateService.UpdateException.class,
                () -> svc.apply(v1File, "2", dir.resolve("nope.sig")));
    }

    @Test
    void applyTamperedPackageFails(@TempDir Path dir) throws Exception {
        byte[] v1 = "good".getBytes();
        Path v1File = writeSigned(dir, "v.pkg", v1, keyPair.getPrivate());
        Path sig = dir.resolve("v.pkg.sig");
        Files.write(v1File, "evil".getBytes());
        UpdateService svc = newService(dir, new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(v1File, "2", sig));
    }

    @Test
    void applyForeignKeyFails(@TempDir Path dir) throws Exception {
        byte[] v1 = "x".getBytes();
        Path v1File = writeSigned(dir, "v.pkg", v1, keyPair.getPrivate());
        Path sig = dir.resolve("v.pkg.sig");

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair other = g.generateKeyPair();
        UpdateService svc = new UpdateService(dir,
                SignatureVerifier.fromPem(exportPem(other.getPublic())),
                new InMemoryUpdateHistoryRepository(), clock);
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(v1File, "2", sig));
    }

    @Test
    void rollbackWithoutHistory(@TempDir Path dir) {
        UpdateService svc = newService(dir, new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, svc::rollback);
    }

    @Test
    void rollbackWithMissingPriorPackage(@TempDir Path dir) {
        InMemoryUpdateHistoryRepository history = new InMemoryUpdateHistoryRepository();
        history.append("0.9", dir.resolve("missing-prior.pkg"), clock.now());
        UpdateService svc = newService(dir, history);
        assertThrows(UpdateService.UpdateException.class, svc::rollback);
    }

    @Test
    void applyFailsWhenInstallRootBlocked(@TempDir Path dir) throws Exception {
        byte[] v1 = "v".getBytes();
        Path v1File = writeSigned(dir, "v.pkg", v1, keyPair.getPrivate());
        Path sig = dir.resolve("v.pkg.sig");
        Path blocked = dir.resolve("blocker");
        Files.writeString(blocked, "not-a-dir");
        UpdateService svc = newService(blocked, new InMemoryUpdateHistoryRepository());
        assertThrows(UncheckedIOException.class, () -> svc.apply(v1File, "2", sig));
    }

    @Test
    void signatureVerifierRejectsBadPem() {
        assertThrows(IllegalArgumentException.class, () -> SignatureVerifier.fromPem(null));
        assertThrows(IllegalArgumentException.class, () -> SignatureVerifier.fromPem("  "));
        assertThrows(SignatureVerifier.SignatureException.class,
                () -> SignatureVerifier.fromPem("-----BEGIN PUBLIC KEY-----\nbm90YWtleQ==\n-----END PUBLIC KEY-----"));
    }

    @Test
    void signatureVerifierRejectsNullPublicKey() {
        assertThrows(IllegalArgumentException.class, () -> new SignatureVerifier(null));
    }

    @Test
    void verifyRejectsNulls() {
        SignatureVerifier v = SignatureVerifier.fromPem(exportPem(keyPair.getPublic()));
        assertFalse(v.verify(null, new byte[]{1}));
        assertFalse(v.verify(new byte[]{1}, null));
    }

    @Test
    void verifyHandlesInvalidSignature() {
        SignatureVerifier v = SignatureVerifier.fromPem(exportPem(keyPair.getPublic()));
        assertFalse(v.verify("data".getBytes(), new byte[]{1, 2, 3}));
    }

    @Test
    void verifyFileWithRawBinarySignature(@TempDir Path dir) throws Exception {
        byte[] data = "binary".getBytes();
        Path pkg = dir.resolve("pkg.bin");
        Files.write(pkg, data);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(keyPair.getPrivate());
        s.update(data);
        byte[] rawSig = s.sign();
        byte[] withNonPrintable = new byte[rawSig.length + 1];
        withNonPrintable[0] = (byte) 0x00;
        System.arraycopy(rawSig, 0, withNonPrintable, 1, rawSig.length);
        Path sig = dir.resolve("pkg.bin.sig");
        Files.write(sig, withNonPrintable);
        SignatureVerifier v = SignatureVerifier.fromPem(exportPem(keyPair.getPublic()));
        assertFalse(v.verifyFile(pkg, sig));
    }

    @Test
    void verifyFileTextWithInvalidBase64(@TempDir Path dir) throws Exception {
        byte[] data = "txt".getBytes();
        Path pkg = dir.resolve("t.pkg");
        Files.write(pkg, data);
        Path sig = dir.resolve("t.sig");
        Files.writeString(sig, "!!!", StandardCharsets.UTF_8);
        SignatureVerifier v = SignatureVerifier.fromPem(exportPem(keyPair.getPublic()));
        assertFalse(v.verifyFile(pkg, sig));
    }

    @Test
    void fromPemFileReads(@TempDir Path dir) throws Exception {
        Path pem = dir.resolve("key.pem");
        Files.writeString(pem, exportPem(keyPair.getPublic()));
        SignatureVerifier v = SignatureVerifier.fromPemFile(pem);
        assertNotNull(v);
    }

    @Test
    void applyExtractsPayloadIntoVersionDirectory(@TempDir Path dir) throws Exception {
        byte[] zip = zipOf(Map.of(
                "templates/welcome.txt", "Hello {name}".getBytes(),
                "dicts/colors.txt", "blue".getBytes()));
        Path pkg = dir.resolve("v.pkg");
        Files.write(pkg, zip);
        Path sig = signOf(dir, "v.pkg.sig", zip);

        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        svc.apply(pkg, "2.0.0", sig);

        Path payload = dir.resolve("root/versions/2.0.0");
        assertTrue(Files.exists(payload.resolve("templates/welcome.txt")),
                "payload files must be extracted into versions/<version>/");
        assertEquals("Hello {name}",
                Files.readString(payload.resolve("templates/welcome.txt")));
        assertEquals("blue", Files.readString(payload.resolve("dicts/colors.txt")));
        assertTrue(svc.activePayloadRoot().isPresent());
        assertEquals(payload, svc.activePayloadRoot().orElseThrow());
    }

    @Test
    void applyRejectsNonZipPackage(@TempDir Path dir) throws Exception {
        Path pkg = dir.resolve("v.pkg");
        Files.write(pkg, "not a zip at all".getBytes());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(keyPair.getPrivate());
        s.update("not a zip at all".getBytes());
        Path sig = dir.resolve("v.pkg.sig");
        Files.writeString(sig, Base64.getEncoder().encodeToString(s.sign()));

        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(pkg, "2.0.0", sig));
    }

    @Test
    void applyRejectsUnsafeVersionString(@TempDir Path dir) throws Exception {
        byte[] zip = zipOf(Map.of("f.txt", "x".getBytes()));
        Path pkg = dir.resolve("v.pkg");
        Files.write(pkg, zip);
        Path sig = signOf(dir, "v.pkg.sig", zip);
        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(pkg, "../etc", sig));
        assertThrows(UpdateService.UpdateException.class, () -> svc.apply(pkg, "bad name", sig));
    }

    @Test
    void applyRejectsZipSlipEntry(@TempDir Path dir) throws Exception {
        byte[] zip = zipOf(Map.of("../escape.txt", "evil".getBytes()));
        Path pkg = dir.resolve("v.pkg");
        Files.write(pkg, zip);
        Path sig = signOf(dir, "v.pkg.sig", zip);
        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        UpdateService.UpdateException ex = assertThrows(UpdateService.UpdateException.class,
                () -> svc.apply(pkg, "2.0.0", sig));
        assertTrue(ex.getMessage().contains("escapes"), "should reject zip-slip: " + ex.getMessage());
        // staging dir must have been cleaned up
        Path versionsDir = dir.resolve("root/versions");
        if (Files.exists(versionsDir)) {
            try (java.util.stream.Stream<Path> s = Files.list(versionsDir)) {
                s.forEach(p -> assertFalse(p.getFileName().toString().startsWith("2.0.0.staging"),
                        "staging leftover: " + p));
            }
        }
    }

    @Test
    void activateReExtractsPayloadWhenVersionDirMissing(@TempDir Path dir) throws Exception {
        byte[] zip = zipOf(Map.of("f.txt", "hello".getBytes()));
        Path pkg = dir.resolve("v.pkg");
        Files.write(pkg, zip);
        Path sig = signOf(dir, "v.pkg.sig", zip);

        Path root = dir.resolve("root");
        InMemoryUpdateHistoryRepository history = new InMemoryUpdateHistoryRepository();
        UpdateService svc = newService(root, history);
        svc.apply(pkg, "3.0.0", sig);

        // Simulate an interrupted install where only the pkg and metadata survived.
        Path versionDir = root.resolve("versions/3.0.0");
        deleteRecursively(versionDir);
        assertFalse(Files.exists(versionDir));

        java.util.Optional<Path> activated = svc.activate();
        assertTrue(activated.isPresent());
        assertEquals(versionDir, activated.orElseThrow());
        assertEquals("hello", Files.readString(versionDir.resolve("f.txt")));
    }

    @Test
    void activateWithNoActivePkgIsNoop(@TempDir Path dir) {
        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        assertTrue(svc.activate().isEmpty());
    }

    @Test
    void rollbackReExtractsPriorPayload(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("root");
        InMemoryUpdateHistoryRepository history = new InMemoryUpdateHistoryRepository();
        UpdateService svc = newService(root, history);

        byte[] v1zip = zipOf(Map.of("app.txt", "v1".getBytes()));
        Path v1pkg = dir.resolve("v1.pkg");
        Files.write(v1pkg, v1zip);
        Path v1sig = signOf(dir, "v1.pkg.sig", v1zip);
        svc.apply(v1pkg, "1.1.0", v1sig);

        byte[] v2zip = zipOf(Map.of("app.txt", "v2".getBytes()));
        Path v2pkg = dir.resolve("v2.pkg");
        Files.write(v2pkg, v2zip);
        Path v2sig = signOf(dir, "v2.pkg.sig", v2zip);
        svc.apply(v2pkg, "1.2.0", v2sig);

        assertEquals("v2", Files.readString(root.resolve("versions/1.2.0/app.txt")));

        // Remove the prior payload directory to prove rollback actually re-extracts.
        deleteRecursively(root.resolve("versions/1.1.0"));
        svc.rollback();

        assertEquals("1.1.0", svc.currentVersion());
        assertEquals("v1", Files.readString(root.resolve("versions/1.1.0/app.txt")),
                "rollback must re-activate the prior payload on disk, not just metadata");
    }

    @Test
    void activePayloadRootReturnsEmptyBeforeAnyApply(@TempDir Path dir) {
        UpdateService svc = newService(dir.resolve("root"), new InMemoryUpdateHistoryRepository());
        assertTrue(svc.activePayloadRoot().isEmpty());
    }

    private Path signOf(Path dir, String sigName, byte[] data) throws Exception {
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(keyPair.getPrivate());
        s.update(data);
        Path sigFile = dir.resolve(sigName);
        Files.writeString(sigFile, Base64.getEncoder().encodeToString(s.sign()));
        return sigFile;
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

    @Test
    void updateHistoryRecordAccessors() {
        UpdateHistoryRepository.Record r = new UpdateHistoryRepository.Record(
                42L, "1.0", Path.of("x.pkg"), clock.now());
        assertEquals(42L, r.seq());
        assertEquals("1.0", r.version());
        assertEquals(Path.of("x.pkg"), r.packagePath());
        assertEquals(clock.now(), r.installedAt());
    }
}
