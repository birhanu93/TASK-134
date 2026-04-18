package com.fleetride.service;

import com.fleetride.domain.ShareLink;
import com.fleetride.repository.InMemoryShareLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ShareLinkServiceTest {

    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    private MachineIdProvider provider(Path dir, String seed) {
        Path f = dir.resolve("machine-id");
        // use a deterministic random stream for test stability
        SecureRandom r = new SecureRandom(seed.getBytes());
        return new MachineIdProvider(f, r);
    }

    @Test
    void rejectsNullProvider() {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        assertThrows(IllegalArgumentException.class,
                () -> new ShareLinkService(repo, () -> t, null));
    }

    @Test
    void createAndResolve(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        MachineIdProvider mid = provider(dir, "m1");
        ShareLinkService svc = new ShareLinkService(repo, () -> t, mid);
        ShareLink l = svc.create("order-42");
        assertEquals(mid.machineId(), l.machineId());
        assertEquals(t.plusHours(24), l.expiresAt());
        assertEquals("order-42", svc.resolve(l.token()));
    }

    @Test
    void customTtl(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService svc = new ShareLinkService(repo, () -> t, provider(dir, "x"));
        ShareLink l = svc.create("r", 2);
        assertEquals(t.plusHours(2), l.expiresAt());
    }

    @Test
    void rejectsNonPositiveTtl(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService svc = new ShareLinkService(repo, () -> t, provider(dir, "x"));
        assertThrows(IllegalArgumentException.class, () -> svc.create("r", 0));
        assertThrows(IllegalArgumentException.class, () -> svc.create("r", -1));
    }

    @Test
    void resolveUnknownToken(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService svc = new ShareLinkService(repo, () -> t, provider(dir, "x"));
        assertThrows(ShareLinkService.ShareLinkException.class, () -> svc.resolve("x"));
    }

    @Test
    void resolveFromDifferentMachineFails(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService issuer = new ShareLinkService(repo, () -> t, provider(dir, "issuer"));
        ShareLink l = issuer.create("secret");
        // simulate the DB being read on a different host with a different machine id
        Path otherDir = dir.resolve("other");
        ShareLinkService attacker = new ShareLinkService(repo, () -> t, provider(otherDir, "attacker"));
        assertThrows(ShareLinkService.ShareLinkException.class, () -> attacker.resolve(l.token()));
    }

    @Test
    void resolveExpiredThrows(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        AtomicReference<LocalDateTime> now = new AtomicReference<>(t);
        ShareLinkService svc = new ShareLinkService(repo, now::get, provider(dir, "x"));
        ShareLink l = svc.create("r", 1);
        now.set(t.plusHours(2));
        assertThrows(ShareLinkService.ShareLinkException.class, () -> svc.resolve(l.token()));
    }

    @Test
    void revoke(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService svc = new ShareLinkService(repo, () -> t, provider(dir, "x"));
        ShareLink l = svc.create("r");
        svc.revoke(l.token());
        assertThrows(ShareLinkService.ShareLinkException.class, () -> svc.resolve(l.token()));
    }

    @Test
    void customRandomConstructor(@TempDir Path dir) {
        InMemoryShareLinkRepository repo = new InMemoryShareLinkRepository();
        ShareLinkService svc = new ShareLinkService(repo, () -> t, provider(dir, "x"), new SecureRandom());
        assertNotNull(svc.create("r").token());
    }
}
