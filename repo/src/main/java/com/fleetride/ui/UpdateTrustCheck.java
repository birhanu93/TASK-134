package com.fleetride.ui;

import com.fleetride.service.SignatureVerifier;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdateTrustCheck {
    private UpdateTrustCheck() {}

    public static SignatureVerifier require(Path pubKey) {
        if (pubKey == null || !Files.exists(pubKey)) {
            throw new IllegalStateException(
                    "Update public key not found at " + pubKey + ". "
                            + "Provision the trusted PEM via fleetride.updatePublicKey "
                            + "or place update-public.pem in the data directory. "
                            + "The app refuses to start rather than trust an unsigned-or-self-signed fallback.");
        }
        return SignatureVerifier.fromPemFile(pubKey);
    }
}
