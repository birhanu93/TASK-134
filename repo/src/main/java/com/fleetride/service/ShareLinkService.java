package com.fleetride.service;

import com.fleetride.domain.ShareLink;
import com.fleetride.repository.ShareLinkRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

public final class ShareLinkService {
    public static final class ShareLinkException extends RuntimeException {
        public ShareLinkException(String msg) { super(msg); }
    }

    public static final int DEFAULT_TTL_HOURS = 24;

    private final ShareLinkRepository repo;
    private final Clock clock;
    private final MachineIdProvider machineIdProvider;
    private final SecureRandom random;

    public ShareLinkService(ShareLinkRepository repo, Clock clock, MachineIdProvider machineIdProvider) {
        this(repo, clock, machineIdProvider, new SecureRandom());
    }

    public ShareLinkService(ShareLinkRepository repo, Clock clock,
                            MachineIdProvider machineIdProvider, SecureRandom random) {
        if (machineIdProvider == null) {
            throw new IllegalArgumentException("machineIdProvider required");
        }
        this.repo = repo;
        this.clock = clock;
        this.machineIdProvider = machineIdProvider;
        this.random = random;
    }

    public ShareLink create(String resourceId, int ttlHours) {
        if (ttlHours <= 0) throw new IllegalArgumentException("ttlHours must be positive");
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        java.time.LocalDateTime now = clock.now();
        ShareLink link = new ShareLink(token, resourceId, machineIdProvider.machineId(),
                now, now.plusHours(ttlHours));
        repo.save(link);
        return link;
    }

    public ShareLink create(String resourceId) {
        return create(resourceId, DEFAULT_TTL_HOURS);
    }

    /**
     * Resolve a share-link token. The current host's machine identifier is supplied by
     * the configured {@link MachineIdProvider} and is never accepted from the caller,
     * making same-machine enforcement non-spoofable.
     */
    public String resolve(String token) {
        Optional<ShareLink> found = repo.findByToken(token);
        if (found.isEmpty()) throw new ShareLinkException("unknown token");
        ShareLink link = found.get();
        if (!link.machineId().equals(machineIdProvider.machineId())) {
            throw new ShareLinkException("machine mismatch");
        }
        if (link.isExpired(clock.now())) {
            throw new ShareLinkException("expired");
        }
        return link.resourceId();
    }

    public void revoke(String token) { repo.delete(token); }
}
