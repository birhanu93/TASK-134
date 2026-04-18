package com.fleetride.repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryUpdateHistoryRepository implements UpdateHistoryRepository {
    private final List<Record> rows = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong();
    private String currentVersion = "1.0.0";

    @Override
    public synchronized long append(String version, Path packagePath, LocalDateTime installedAt) {
        long s = seq.incrementAndGet();
        rows.add(new Record(s, version, packagePath, installedAt));
        return s;
    }

    @Override
    public synchronized Optional<Record> peekLatest() {
        return rows.stream().max(Comparator.comparingLong(Record::seq));
    }

    @Override
    public synchronized List<Record> listAll() { return new ArrayList<>(rows); }

    @Override
    public synchronized void deleteBySeq(long seq) {
        rows.removeIf(r -> r.seq() == seq);
    }

    @Override
    public synchronized String currentVersion() { return currentVersion; }

    @Override
    public synchronized void setCurrentVersion(String version) { this.currentVersion = version; }
}
