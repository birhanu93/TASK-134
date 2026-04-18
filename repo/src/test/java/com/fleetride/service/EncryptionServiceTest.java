package com.fleetride.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    @Test
    void rejectsNullOrBlankKey() {
        assertThrows(IllegalArgumentException.class, () -> new EncryptionService(null));
        assertThrows(IllegalArgumentException.class, () -> new EncryptionService(" "));
    }

    @Test
    void encryptDecryptRoundTrip() {
        EncryptionService e = new EncryptionService("test-key");
        String cipher = e.encrypt("hello world");
        assertNotEquals("hello world", cipher);
        assertEquals("hello world", e.decrypt(cipher));
    }

    @Test
    void encryptNullIsNull() {
        EncryptionService e = new EncryptionService("key");
        assertNull(e.encrypt(null));
        assertNull(e.decrypt(null));
    }

    @Test
    void decryptTooShortFails() {
        EncryptionService e = new EncryptionService("key");
        String tiny = Base64.getEncoder().encodeToString(new byte[4]);
        assertThrows(IllegalArgumentException.class, () -> e.decrypt(tiny));
    }

    @Test
    void decryptTamperedFails() {
        EncryptionService e = new EncryptionService("key");
        String cipher = e.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> e.decrypt(tampered));
    }

    @Test
    void encryptPasswordAndVerify() {
        EncryptionService e = new EncryptionService("k");
        String c = e.encryptPassword("pw");
        assertNotEquals("pw", c);
        assertTrue(e.verifyPassword("pw", c));
        assertFalse(e.verifyPassword("wrong", c));
    }

    @Test
    void encryptPasswordNullRejected() {
        EncryptionService e = new EncryptionService("k");
        assertThrows(IllegalArgumentException.class, () -> e.encryptPassword(null));
    }

    @Test
    void verifyPasswordNullsReturnsFalse() {
        EncryptionService e = new EncryptionService("k");
        assertFalse(e.verifyPassword(null, "x"));
        assertFalse(e.verifyPassword("p", null));
    }

    @Test
    void customRandomConstructor() {
        EncryptionService e = new EncryptionService("k", new SecureRandom());
        assertNotNull(e.encrypt("x"));
    }

    @Test
    void uncheckedWrapsCryptoException() {
        assertThrows(IllegalStateException.class,
                () -> EncryptionService.unchecked(() -> {
                    throw new java.security.NoSuchAlgorithmException("boom");
                }));
    }

    @Test
    void passwordCipherTextsDifferPerCall() {
        EncryptionService e = new EncryptionService("k");
        String a = e.encryptPassword("same");
        String b = e.encryptPassword("same");
        assertNotEquals(a, b, "AES-GCM IV must vary per call");
        assertTrue(e.verifyPassword("same", a));
        assertTrue(e.verifyPassword("same", b));
    }

    @Test
    void verifyRejectsMalformedCiphertext() {
        EncryptionService e = new EncryptionService("k");
        assertFalse(e.verifyPassword("pw", "!!!not-base64!!!"));
        assertFalse(e.verifyPassword("pw", Base64.getEncoder().encodeToString(new byte[4])));
    }

    @Test
    void verifyRejectsCiphertextFromDifferentKey() {
        EncryptionService a = new EncryptionService("key-A");
        EncryptionService b = new EncryptionService("key-B");
        String c = a.encryptPassword("pw");
        assertFalse(b.verifyPassword("pw", c),
                "ciphertext encrypted with a different master key must not verify");
    }

    @Test
    void verifyRejectsTamperedCiphertext() {
        EncryptionService e = new EncryptionService("k");
        String c = e.encryptPassword("pw");
        byte[] raw = Base64.getDecoder().decode(c);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertFalse(e.verifyPassword("pw", tampered));
    }
}
