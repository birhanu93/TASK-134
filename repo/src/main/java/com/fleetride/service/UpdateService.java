package com.fleetride.service;

import com.fleetride.repository.UpdateHistoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Offline update orchestrator. A signed update package is a zip that carries the
 * application payload (templates, dictionaries, bundled resources). `apply` verifies
 * the signature, records the new version, swaps an <b>extracted</b> version directory
 * under `<installRoot>/versions/<version>/` into place, and archives the prior active
 * package for rollback. `activate` is invoked on startup to make sure the active
 * version's files are unpacked on disk — so the app really runs on the last imported
 * payload, not just metadata.
 */
public final class UpdateService {
    public static final class UpdateException extends RuntimeException {
        public UpdateException(String msg) { super(msg); }
        public UpdateException(String msg, Throwable cause) { super(msg, cause); }
    }

    private static final String ACTIVE_VERSION_FILE = "active-version";
    private static final String VERSIONS_DIR = "versions";

    private final Path installRoot;
    private final SignatureVerifier verifier;
    private final UpdateHistoryRepository history;
    private final Clock clock;

    public UpdateService(Path installRoot, SignatureVerifier verifier,
                         UpdateHistoryRepository history, Clock clock) {
        if (installRoot == null) throw new IllegalArgumentException("installRoot required");
        if (verifier == null) throw new IllegalArgumentException("verifier required");
        if (history == null) throw new IllegalArgumentException("history required");
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.installRoot = installRoot;
        this.verifier = verifier;
        this.history = history;
        this.clock = clock;
    }

    public String currentVersion() { return history.currentVersion(); }

    public void apply(Path updatePackage, String declaredVersion, Path signatureFile) {
        if (declaredVersion == null || declaredVersion.isBlank()) {
            throw new UpdateException("declaredVersion required");
        }
        if (!isSafeVersion(declaredVersion)) {
            throw new UpdateException("declaredVersion must be [A-Za-z0-9._-]+ (got '"
                    + declaredVersion + "')");
        }
        if (!Files.exists(updatePackage)) throw new UpdateException("package not found");
        if (signatureFile == null || !Files.exists(signatureFile)) {
            throw new UpdateException("signature file not found");
        }
        if (!verifier.verifyFile(updatePackage, signatureFile)) {
            throw new UpdateException("signature verification failed");
        }
        IOUtil.uncheckedRun(() -> Files.createDirectories(installRoot));
        IOUtil.uncheckedRun(() -> Files.createDirectories(versionsDir()));

        // Stage new version directory next to the destination, then rename atomically once
        // extraction succeeded. If anything fails mid-extract we delete the staged dir so
        // the prior active version stays intact.
        Path staging = versionsDir().resolve(declaredVersion + ".staging-" + System.nanoTime());
        IOUtil.uncheckedRun(() -> Files.createDirectories(staging));
        try {
            extractZip(updatePackage, staging);
        } catch (RuntimeException e) {
            IOUtil.uncheckedRun(() -> deleteTree(staging));
            throw e;
        }

        Path target = versionsDir().resolve(declaredVersion);
        if (Files.exists(target)) {
            IOUtil.uncheckedRun(() -> deleteTree(target));
        }
        IOUtil.uncheckedRun(() -> Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE));

        Path active = installRoot.resolve("active.pkg");
        if (Files.exists(active)) {
            String priorVersion = history.currentVersion();
            Path prior = installRoot.resolve("prior-" + priorVersion + "-"
                    + System.nanoTime() + ".pkg");
            IOUtil.uncheckedRun(() -> Files.move(active, prior, StandardCopyOption.REPLACE_EXISTING));
            history.append(priorVersion, prior, clock.now());
        }
        IOUtil.uncheckedRun(() -> Files.copy(updatePackage, active, StandardCopyOption.REPLACE_EXISTING));
        history.setCurrentVersion(declaredVersion);
        writeActivePointer(declaredVersion);
    }

    public void rollback() {
        UpdateHistoryRepository.Record prev = history.peekLatest()
                .orElseThrow(() -> new UpdateException("no prior version"));
        if (!Files.exists(prev.packagePath())) {
            throw new UpdateException("prior package missing: " + prev.packagePath());
        }
        // Re-extract the prior version's payload so the active directory matches what's
        // actually live on disk — not just the metadata string.
        Path priorDir = versionsDir().resolve(prev.version());
        if (Files.exists(priorDir)) {
            IOUtil.uncheckedRun(() -> deleteTree(priorDir));
        }
        IOUtil.uncheckedRun(() -> Files.createDirectories(versionsDir()));
        Path staging = versionsDir().resolve(prev.version() + ".staging-" + System.nanoTime());
        IOUtil.uncheckedRun(() -> Files.createDirectories(staging));
        try {
            extractZip(prev.packagePath(), staging);
        } catch (RuntimeException e) {
            IOUtil.uncheckedRun(() -> deleteTree(staging));
            throw e;
        }
        IOUtil.uncheckedRun(() -> Files.move(staging, priorDir, StandardCopyOption.ATOMIC_MOVE));

        Path active = installRoot.resolve("active.pkg");
        IOUtil.uncheckedRun(() -> Files.copy(prev.packagePath(), active, StandardCopyOption.REPLACE_EXISTING));
        history.setCurrentVersion(prev.version());
        history.deleteBySeq(prev.seq());
        writeActivePointer(prev.version());
    }

    /**
     * Make sure the recorded active version's payload directory exists on disk. Called
     * at startup so a process that was killed mid-apply (or a fresh install that didn't
     * re-extract) still boots into a consistent state instead of silently running the
     * prior payload while history claims the new version is active.
     */
    public Optional<Path> activate() {
        String version = history.currentVersion();
        if (version == null || version.isBlank()) return Optional.empty();
        Path dir = versionsDir().resolve(version);
        Path activePkg = installRoot.resolve("active.pkg");
        if (!Files.exists(dir)) {
            if (!Files.exists(activePkg)) {
                // Nothing to activate — baseline install before any apply.
                writeActivePointer(version);
                return Optional.empty();
            }
            IOUtil.uncheckedRun(() -> Files.createDirectories(versionsDir()));
            Path staging = versionsDir().resolve(version + ".staging-" + System.nanoTime());
            IOUtil.uncheckedRun(() -> Files.createDirectories(staging));
            try {
                extractZip(activePkg, staging);
            } catch (RuntimeException e) {
                IOUtil.uncheckedRun(() -> deleteTree(staging));
                throw new UpdateException("activation failed: could not extract " + activePkg, e);
            }
            IOUtil.uncheckedRun(() -> Files.move(staging, dir, StandardCopyOption.ATOMIC_MOVE));
        }
        writeActivePointer(version);
        return Optional.of(dir);
    }

    /** Currently-live payload directory, if any version is active and extracted. */
    public Optional<Path> activePayloadRoot() {
        String version = readActivePointer();
        if (version == null) version = history.currentVersion();
        if (version == null || version.isBlank()) return Optional.empty();
        Path dir = versionsDir().resolve(version);
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    public List<UpdateHistoryRepository.Record> history() { return history.listAll(); }

    private Path versionsDir() { return installRoot.resolve(VERSIONS_DIR); }

    private void writeActivePointer(String version) {
        IOUtil.uncheckedRun(() -> Files.createDirectories(installRoot));
        IOUtil.uncheckedRun(() -> Files.writeString(installRoot.resolve(ACTIVE_VERSION_FILE), version));
    }

    private String readActivePointer() {
        Path p = installRoot.resolve(ACTIVE_VERSION_FILE);
        if (!Files.exists(p)) return null;
        return IOUtil.unchecked(() -> Files.readString(p)).trim();
    }

    private static boolean isSafeVersion(String v) {
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }

    private static void extractZip(Path zipFile, Path targetRoot) {
        // ZipInputStream#getNextEntry returns null on some malformed inputs instead
        // of throwing, so a signed-but-bogus package could previously land as an
        // empty successful update. Opening with ZipFile validates the central
        // directory and throws ZipException for anything that isn't a real ZIP.
        try (java.util.zip.ZipFile probe = new java.util.zip.ZipFile(zipFile.toFile())) {
            probe.entries();
        } catch (IOException e) {
            throw new UpdateException("package is not a valid ZIP archive: "
                    + e.getMessage(), e);
        }
        Path normalizedTarget = targetRoot.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = normalizedTarget.resolve(entry.getName()).normalize();
                if (!out.startsWith(normalizedTarget)) {
                    throw new UpdateException("zip entry escapes target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    copyEntry(zin, out);
                }
                zin.closeEntry();
            }
        } catch (IOException e) {
            throw new UpdateException("failed to extract package: " + e.getMessage(), e);
        }
    }

    private static void copyEntry(InputStream in, Path out) throws IOException {
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> ordered = new ArrayList<>();
            walk.forEach(ordered::add);
            ordered.sort(Comparator.reverseOrder());
            for (Path p : ordered) {
                Files.deleteIfExists(p);
            }
        }
    }
}
