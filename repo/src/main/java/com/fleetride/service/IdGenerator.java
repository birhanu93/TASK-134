package com.fleetride.service;

import java.util.UUID;

@FunctionalInterface
public interface IdGenerator {
    String next();

    static IdGenerator uuid() {
        return () -> UUID.randomUUID().toString();
    }
}
