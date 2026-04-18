package com.fleetride.service;

import java.time.LocalDateTime;

@FunctionalInterface
public interface Clock {
    LocalDateTime now();

    static Clock system() {
        return LocalDateTime::now;
    }
}
