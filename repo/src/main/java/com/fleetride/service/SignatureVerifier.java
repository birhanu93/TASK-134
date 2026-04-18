package com.fleetride.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class SignatureVerifier {
    public static final class SignatureException extends RuntimeException {
        public SignatureException(String msg) { super(msg); }
    }

    private final PublicKey publicKey;

    public SignatureVerifier(PublicKey publicKey) {
        if (publicKey == null) throw new IllegalArgumentException("publicKey required");
        this.publicKey = publicKey;
    }

    public static SignatureVerifier fromPem(String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalArgumentException("pem required");
        String cleaned = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(cleaned);
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(der));
            return new SignatureVerifier(key);
        } catch (java.security.GeneralSecurityException e) {
            throw new SignatureException("invalid public key: " + e.getMessage());
        }
    }

    public static SignatureVerifier fromPemFile(Path pemFile) {
        byte[] bytes = IOUtil.unchecked(() -> Files.readAllBytes(pemFile));
        return fromPem(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean verify(byte[] data, byte[] signature) {
        if (data == null || signature == null) return false;
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initVerify(publicKey);
            s.update(data);
            return s.verify(signature);
        } catch (java.security.GeneralSecurityException e) {
            return false;
        }
    }

    public boolean verifyFile(Path data, Path signature) {
        byte[] d = IOUtil.unchecked(() -> Files.readAllBytes(data));
        byte[] s = IOUtil.unchecked(() -> Files.readAllBytes(signature));
        byte[] decoded = decodeSignature(s);
        return verify(d, decoded);
    }

    private byte[] decodeSignature(byte[] raw) {
        try {
            String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8).trim();
            String stripped = text.replaceAll("-----[^-]*-----", "").replaceAll("\\s+", "");
            return Base64.getDecoder().decode(stripped);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
