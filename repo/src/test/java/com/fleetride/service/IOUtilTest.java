package com.fleetride.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class IOUtilTest {

    @Test
    void uncheckedPassthrough() {
        assertEquals(42, IOUtil.unchecked(() -> 42));
    }

    @Test
    void uncheckedWrapsIOException() {
        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> IOUtil.unchecked(() -> { throw new IOException("fail"); }));
        assertEquals("fail", ex.getCause().getMessage());
    }

    @Test
    void uncheckedRunPassthrough() {
        int[] counter = {0};
        IOUtil.uncheckedRun(() -> counter[0]++);
        assertEquals(1, counter[0]);
    }

    @Test
    void uncheckedRunWrapsIOException() {
        assertThrows(UncheckedIOException.class,
                () -> IOUtil.uncheckedRun(() -> { throw new IOException("boom"); }));
    }

    @Test
    void privateConstructor() throws Exception {
        Constructor<IOUtil> c = IOUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        c.newInstance();
    }
}
