package com.fleetride.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Produces a stable, machine-bound identifier. The identifier is generated once per
 * machine and persisted under the data directory; the resolver always reads it from the
 * host filesystem so callers cannot spoof it.
 */
public final class MachineIdProvider {
    private static final int ID_BYTES = 32;

    private final Path idFile;
    private final SecureRandom random;
    private String cached;

    public MachineIdProvider(Path idFile) {
        this(idFile, new SecureRandom());
    }

    public MachineIdProvider(Path idFile, SecureRandom random) {
        if (idFile == null) throw new IllegalArgumentException("idFile required");
        this.idFile = idFile;
        this.random = random;
    }

    public synchronized String machineId() {
        if (cached != null) return cached;
        IOUtil.uncheckedRun(() -> Files.createDirectories(idFile.toAbsolutePath().getParent()));
        if (Files.exists(idFile)) {
            byte[] bytes = IOUtil.unchecked(() -> Files.readAllBytes(idFile));
            String value = new String(bytes, StandardCharsets.UTF_8).trim();
            if (!value.isEmpty()) {
                cached = value;
                return cached;
            }
        }
        byte[] fresh = new byte[ID_BYTES];
        random.nextBytes(fresh);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(fresh);
        IOUtil.uncheckedRun(() -> Files.writeString(idFile, encoded, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        cached = encoded;
        return cached;
    }
}
