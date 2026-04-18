package com.fleetride.ui;

import com.fleetride.service.SignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class UpdateTrustCheckTest {

    @Test
    void missingKeyFailsStartup(@TempDir Path dir) {
        Path absent = dir.resolve("update-public.pem");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> UpdateTrustCheck.require(absent));
        assertTrue(ex.getMessage().contains("Update public key not found"),
                "message should explain the missing trusted key: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("refuses to start"),
                "message should make the fail-closed stance explicit: " + ex.getMessage());
    }

    @Test
    void nullPathFailsStartup() {
        assertThrows(IllegalStateException.class, () -> UpdateTrustCheck.require(null));
    }

    @Test
    void presentKeyLoadsVerifier(@TempDir Path dir) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        Path pemFile = dir.resolve("update-public.pem");
        Files.writeString(pemFile, pem);

        SignatureVerifier v = UpdateTrustCheck.require(pemFile);
        assertNotNull(v);
    }

    @Test
    void malformedKeyFailsStartup(@TempDir Path dir) throws Exception {
        Path pemFile = dir.resolve("update-public.pem");
        Files.writeString(pemFile, "not-a-pem");
        assertThrows(RuntimeException.class, () -> UpdateTrustCheck.require(pemFile));
    }
}
