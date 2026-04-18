package com.fleetride.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckpointRepository {

    enum Status { PENDING, COMMITTED }

    final class Record {
        private final String operationId;
        private final String detail;
        private final Status status;
        private final LocalDateTime createdAt;

        public Record(String operationId, String detail, Status status, LocalDateTime createdAt) {
            this.operationId = operationId;
            this.detail = detail;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String operationId() { return operationId; }
        public String detail() { return detail; }
        public Status status() { return status; }
        public LocalDateTime createdAt() { return createdAt; }
    }

    void upsert(Record record);
    Optional<Record> find(String operationId);
    List<Record> findPending();
    void delete(String operationId);
}
