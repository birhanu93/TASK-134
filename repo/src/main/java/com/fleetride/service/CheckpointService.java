package com.fleetride.service;

import com.fleetride.repository.CheckpointRepository;
import com.fleetride.repository.CheckpointRepository.Record;
import com.fleetride.repository.CheckpointRepository.Status;

import java.util.List;
import java.util.Optional;

public final class CheckpointService {
    public static final class CheckpointException extends RuntimeException {
        public CheckpointException(String msg) { super(msg); }
    }

    private final CheckpointRepository repo;
    private final Clock clock;

    public CheckpointService(CheckpointRepository repo, Clock clock) {
        if (repo == null) throw new IllegalArgumentException("repo required");
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.repo = repo;
        this.clock = clock;
    }

    public void begin(String operationId, String detail) {
        Optional<Record> existing = repo.find(operationId);
        if (existing.isPresent() && existing.get().status() == Status.COMMITTED) {
            throw new CheckpointException("already completed: " + operationId);
        }
        repo.upsert(new Record(operationId, detail, Status.PENDING, clock.now()));
    }

    public void commit(String operationId) {
        Record cur = repo.find(operationId).orElseThrow(
                () -> new CheckpointException("no pending checkpoint: " + operationId));
        if (cur.status() != Status.PENDING) {
            throw new CheckpointException("checkpoint not pending: " + operationId);
        }
        repo.upsert(new Record(operationId, cur.detail(), Status.COMMITTED, cur.createdAt()));
    }

    public boolean isCompleted(String operationId) {
        return repo.find(operationId)
                .map(r -> r.status() == Status.COMMITTED)
                .orElse(false);
    }

    public Optional<String> pending(String operationId) {
        return repo.find(operationId)
                .filter(r -> r.status() == Status.PENDING)
                .map(Record::detail);
    }

    public List<Record> allPending() {
        return repo.findPending();
    }

    public void clear(String operationId) {
        repo.delete(operationId);
    }

    public <T> T runIdempotent(String operationId, String detail, java.util.function.Supplier<T> work) {
        if (isCompleted(operationId)) {
            throw new CheckpointException("operation already completed: " + operationId);
        }
        begin(operationId, detail);
        T result = work.get();
        commit(operationId);
        return result;
    }

    public void runIdempotentVoid(String operationId, String detail, Runnable work) {
        runIdempotent(operationId, detail, () -> { work.run(); return null; });
    }
}
