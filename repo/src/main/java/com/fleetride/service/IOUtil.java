package com.fleetride.service;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class IOUtil {
    @FunctionalInterface
    public interface IOOp<T> {
        T run() throws IOException;
    }

    @FunctionalInterface
    public interface IOAction {
        void run() throws IOException;
    }

    private IOUtil() {}

    public static <T> T unchecked(IOOp<T> op) {
        try {
            return op.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void uncheckedRun(IOAction a) {
        try {
            a.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
