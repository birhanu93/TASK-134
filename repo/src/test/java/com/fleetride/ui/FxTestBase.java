package com.fleetride.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JUnit base for JavaFX-backed tests. Uses the Monocle headless glass platform so
 * {@code Button.fire()}, scene graph construction, and observable list updates all
 * run inside a real FX application thread without requiring a display server.
 *
 * <p>The headless system properties (glass.platform=Monocle, monocle.platform=Headless,
 * prism.order=sw, fleetride.ui.silentAlerts=true) are set by Maven Surefire so
 * {@link Platform#startup} succeeds in CI/Docker. See {@code pom.xml}.
 */
abstract class FxTestBase {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @BeforeAll
    static void bootPlatform() {
        if (STARTED.compareAndSet(false, true)) {
            try {
                Platform.startup(() -> {});
            } catch (IllegalStateException alreadyRunning) {
                // Idempotent: another test class may have started the platform first.
            }
            Platform.setImplicitExit(false);
            // Replace the error/info alert dialog with a no-op so headless
            // tests never block on Alert.showAndWait().
            ErrorAlerts.setPresenter((type, message) -> { /* swallowed in tests */ });
        }
    }

    /** Runs the supplier on the FX application thread and returns its result. */
    protected static <T> T runFx(java.util.function.Supplier<T> supplier) {
        Object[] out = new Object[1];
        Throwable[] err = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out[0] = supplier.get();
            } catch (Throwable t) {
                err[0] = t;
            } finally {
                latch.countDown();
            }
        });
        await(latch);
        throwIfAny(err[0]);
        @SuppressWarnings("unchecked")
        T t = (T) out[0];
        return t;
    }

    /** Runs the runnable on the FX application thread and waits for completion. */
    protected static void runFx(Runnable r) {
        runFx(() -> { r.run(); return null; });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task timed out after 20s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void throwIfAny(Throwable t) {
        if (t == null) return;
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error er) throw er;
        throw new RuntimeException(t);
    }
}
