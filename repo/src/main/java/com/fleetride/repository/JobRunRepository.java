package com.fleetride.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRunRepository {

    enum Status { RUNNING, SUCCESS, FAILED }

    final class Record {
        private final String id;
        private final String jobName;
        private final LocalDateTime startedAt;
        private final LocalDateTime finishedAt;
        private final Integer processed;
        private final Status status;
        private final String message;

        public Record(String id, String jobName, LocalDateTime startedAt, LocalDateTime finishedAt,
                      Integer processed, Status status, String message) {
            this.id = id;
            this.jobName = jobName;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.processed = processed;
            this.status = status;
            this.message = message;
        }

        public String id() { return id; }
        public String jobName() { return jobName; }
        public LocalDateTime startedAt() { return startedAt; }
        public LocalDateTime finishedAt() { return finishedAt; }
        public Integer processed() { return processed; }
        public Status status() { return status; }
        public String message() { return message; }
    }

    void upsert(Record r);
    Optional<Record> find(String id);
    List<Record> findRecent(int limit);
    List<Record> findByStatus(Status status);
}
