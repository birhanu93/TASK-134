package com.fleetride.ui;

import java.util.Optional;

public final class BootstrapEnv {
    public static final class Credentials {
        private final String username;
        private final String password;

        public Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String username() { return username; }
        public String password() { return password; }
    }

    private BootstrapEnv() {}

    public static Optional<Credentials> parse(String envValue) {
        if (envValue == null) return Optional.empty();
        int colon = envValue.indexOf(':');
        // colon <= 0 catches both "no colon at all" (-1) and ":pw" (0), so the
        // username substring is always non-empty past this point.
        if (colon <= 0) return Optional.empty();
        return Optional.of(new Credentials(
                envValue.substring(0, colon),
                envValue.substring(colon + 1)));
    }
}
