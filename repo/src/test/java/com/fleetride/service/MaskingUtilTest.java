package com.fleetride.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class MaskingUtilTest {

    @Test
    void maskLast4Null() {
        assertNull(MaskingUtil.maskLast4(null));
    }

    @Test
    void maskLast4Short() {
        assertEquals("***", MaskingUtil.maskLast4("abc"));
        assertEquals("****", MaskingUtil.maskLast4("abcd"));
    }

    @Test
    void maskLast4Long() {
        assertEquals("************3456", MaskingUtil.maskLast4("1234123412343456"));
    }

    @Test
    void maskPhoneNull() {
        assertNull(MaskingUtil.maskPhone(null));
    }

    @Test
    void maskPhoneShort() {
        assertEquals("***", MaskingUtil.maskPhone("123"));
    }

    @Test
    void maskPhoneLong() {
        assertEquals("***-***-5678", MaskingUtil.maskPhone("555-123-5678"));
    }

    @Test
    void privateConstructor() throws NoSuchMethodException {
        Constructor<MaskingUtil> c = MaskingUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        try {
            c.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            fail(e);
        }
    }
}
