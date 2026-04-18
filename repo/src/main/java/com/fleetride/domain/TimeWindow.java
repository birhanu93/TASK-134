package com.fleetride.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class TimeWindow {
    private final LocalDateTime start;
    private final LocalDateTime end;

    public TimeWindow(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
        this.start = start;
        this.end = end;
    }

    public LocalDateTime start() { return start; }
    public LocalDateTime end() { return end; }

    public Duration duration() {
        return Duration.between(start, end);
    }

    public boolean contains(LocalDateTime t) {
        return !t.isBefore(start) && !t.isAfter(end);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeWindow w)) return false;
        return Objects.equals(start, w.start) && Objects.equals(end, w.end);
    }

    @Override
    public int hashCode() { return Objects.hash(start, end); }
}
