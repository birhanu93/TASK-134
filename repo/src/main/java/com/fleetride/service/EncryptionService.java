package com.fleetride.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class EncryptionService {
    @FunctionalInterface
    interface CryptoOp<T> {
        T run() throws GeneralSecurityException;
    }

    private static final String ALGO = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERS = 210_000;
    private static final int KEY_BITS = 256;
    private static final byte[] KEY_DERIVATION_SALT = "fleetride-key-derivation-salt".getBytes(StandardCharsets.UTF_8);

    private final SecretKey key;
    private final SecureRandom random;

    public EncryptionService(String masterKey) {
        this(masterKey, new SecureRandom());
    }

    public EncryptionService(String masterKey, SecureRandom random) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("masterKey required");
        }
        this.random = random;
        byte[] raw = unchecked(() -> {
            PBEKeySpec spec = new PBEKeySpec(masterKey.toCharArray(), KEY_DERIVATION_SALT, PBKDF2_ITERS, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        });
        this.key = new SecretKeySpec(raw, ALGO);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        return unchecked(() -> {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        });
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        byte[] all = Base64.getDecoder().decode(encoded);
        if (all.length <= GCM_IV_BYTES) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        return unchecked(() -> {
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(all, 0, iv, 0, GCM_IV_BYTES);
            byte[] ct = new byte[all.length - GCM_IV_BYTES];
            System.arraycopy(all, GCM_IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        });
    }

    /**
     * Encrypt a password under the master key using AES-256-GCM. A fresh 96-bit IV is
     * generated per call, so the same password produces different ciphertexts. The admin
     * who holds the master key can re-derive the plaintext, which is required for
     * {@link #verifyPassword(String, String)} to work.
     */
    public String encryptPassword(String password) {
        if (password == null) throw new IllegalArgumentException("password required");
        return encrypt(password);
    }

    public boolean verifyPassword(String password, String ciphertext) {
        if (password == null || ciphertext == null) return false;
        String decrypted;
        try {
            decrypted = decrypt(ciphertext);
        } catch (RuntimeException e) {
            return false;
        }
        if (decrypted == null) return false;
        byte[] given = password.getBytes(StandardCharsets.UTF_8);
        byte[] stored = decrypted.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, stored);
    }

    static <T> T unchecked(CryptoOp<T> op) {
        try {
            return op.run();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("crypto failed", e);
        }
    }
}
