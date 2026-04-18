package com.fleetride.repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UpdateHistoryRepository {
    final class Record {
        private final long seq;
        private final String version;
        private final Path packagePath;
        private final LocalDateTime installedAt;

        public Record(long seq, String version, Path packagePath, LocalDateTime installedAt) {
            this.seq = seq;
            this.version = version;
            this.packagePath = packagePath;
            this.installedAt = installedAt;
        }

        public long seq() { return seq; }
        public String version() { return version; }
        public Path packagePath() { return packagePath; }
        public LocalDateTime installedAt() { return installedAt; }
    }

    long append(String version, Path packagePath, LocalDateTime installedAt);
    Optional<Record> peekLatest();
    List<Record> listAll();
    void deleteBySeq(long seq);

    String currentVersion();
    void setCurrentVersion(String version);
}
