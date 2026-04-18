package com.fleetride.log;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedactorTest {

    @Test
    void maskSensitiveKeys() {
        assertEquals("***", Redactor.redactValue("password", "secret"));
        assertEquals("***", Redactor.redactValue("apiKey", "1234"));
        assertEquals("***", Redactor.redactValue("payment_token", "pt-xyz"));
        assertEquals("plain", Redactor.redactValue("username", "plain"));
    }

    @Test
    void redactValueNull() {
        assertNull(Redactor.redactValue("any", null));
    }

    @Test
    void scrubsCardsPhonesEmails() {
        assertEquals("card=****-****-****-####",
                Redactor.scrubPatterns("card=4111111111111111"));
        assertEquals("call ***-***-####", Redactor.scrubPatterns("call 555-123-4567"));
        assertEquals("email ***@***", Redactor.scrubPatterns("email a@b.co"));
    }

    @Test
    void scrubPatternsNull() {
        assertNull(Redactor.scrubPatterns(null));
    }

    @Test
    void redactFieldsMap() {
        Map<String, String> in = Map.of("password", "x", "note", "card 4111 1111 1111 1111");
        Map<String, String> out = Redactor.redactFields(in);
        assertEquals("***", out.get("password"));
        assertTrue(out.get("note").contains("****"));
    }

    @Test
    void isSensitiveKeyNull() {
        assertFalse(Redactor.isSensitiveKey(null));
    }

    @Test
    void privateConstructor() throws Exception {
        Constructor<Redactor> c = Redactor.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }
}
