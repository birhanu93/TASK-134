package com.fleetride.repository;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJobRunAndCheckpointTest {

    @Test
    void jobRunFindsByStatusFiltersMismatches() {
        InMemoryJobRunRepository repo = new InMemoryJobRunRepository();
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);
        repo.upsert(new JobRunRepository.Record("j1", "n", t, null, null,
                JobRunRepository.Status.RUNNING, null));
        repo.upsert(new JobRunRepository.Record("j2", "n", t.plusMinutes(1), t.plusMinutes(2), 0,
                JobRunRepository.Status.SUCCESS, null));
        assertEquals(1, repo.findByStatus(JobRunRepository.Status.RUNNING).size());
        assertEquals(1, repo.findByStatus(JobRunRepository.Status.SUCCESS).size());
        assertEquals(0, repo.findByStatus(JobRunRepository.Status.FAILED).size());
        assertEquals("j2", repo.findRecent(5).get(0).id());
        assertTrue(repo.find("nope").isEmpty());
    }

    @Test
    void checkpointFindPendingFiltersCommitted() {
        InMemoryCheckpointRepository repo = new InMemoryCheckpointRepository();
        LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);
        repo.upsert(new CheckpointRepository.Record("a", "d",
                CheckpointRepository.Status.PENDING, t));
        repo.upsert(new CheckpointRepository.Record("b", "d",
                CheckpointRepository.Status.COMMITTED, t));
        assertEquals(1, repo.findPending().size());
        assertEquals("a", repo.findPending().get(0).operationId());
    }
}
