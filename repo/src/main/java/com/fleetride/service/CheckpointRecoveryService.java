package com.fleetride.service;

import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.repository.CheckpointRepository;
import com.fleetride.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reconciles pending checkpoints at startup. For each {@code PENDING} entry,
 * examines the current state of the target domain object and either:
 * <ul>
 *   <li>commits the checkpoint, if the domain already reflects the target state
 *       (the operation succeeded but the process crashed before committing the
 *       checkpoint);</li>
 *   <li>or clears the checkpoint, leaving the next normal call free to retry.</li>
 * </ul>
 */
public final class CheckpointRecoveryService {
    public static final class RecoveryReport {
        private final int inspected;
        private final int committed;
        private final int cleared;
        private final List<String> notes;

        public RecoveryReport(int inspected, int committed, int cleared, List<String> notes) {
            this.inspected = inspected;
            this.committed = committed;
            this.cleared = cleared;
            this.notes = notes;
        }

        public int inspected() { return inspected; }
        public int committed() { return committed; }
        public int cleared() { return cleared; }
        public List<String> notes() { return notes; }
    }

    private static final Map<String, OrderState> ORDER_TARGET_STATE = Map.of(
            "ACCEPT", OrderState.ACCEPTED,
            "START", OrderState.IN_PROGRESS,
            "COMPLETE", OrderState.COMPLETED,
            "CANCEL", OrderState.CANCELED,
            "AUTO_CANCEL", OrderState.CANCELED);

    private final CheckpointRepository checkpointRepo;
    private final CheckpointService checkpoints;
    private final OrderRepository orders;
    private final AuditService audit;

    public CheckpointRecoveryService(CheckpointRepository checkpointRepo,
                                     CheckpointService checkpoints,
                                     OrderRepository orders,
                                     AuditService audit) {
        this.checkpointRepo = checkpointRepo;
        this.checkpoints = checkpoints;
        this.orders = orders;
        this.audit = audit;
    }

    public RecoveryReport recover() {
        List<String> notes = new ArrayList<>();
        int committed = 0;
        int cleared = 0;
        List<CheckpointRepository.Record> pending = checkpointRepo.findPending();
        for (CheckpointRepository.Record rec : pending) {
            String opId = rec.operationId();
            String[] parts = opId.split(":", 3);
            if (parts.length == 3 && "order".equals(parts[0])) {
                String orderId = parts[1];
                String transition = parts[2];
                OrderState target = ORDER_TARGET_STATE.get(transition);
                if (target != null) {
                    Order o = orders.findById(orderId).orElse(null);
                    if (o != null && o.state() == target) {
                        checkpoints.commit(opId);
                        notes.add(opId + " -> committed (state matches " + target + ")");
                        audit.record("system", "CHECKPOINT_RECOVER_COMMIT", opId, null);
                        committed++;
                        continue;
                    }
                }
            }
            if (opId.startsWith("order:") && opId.contains(":DISPUTE:")) {
                // dispute opening is idempotent: if order already IN_DISPUTE, commit.
                String orderId = opId.split(":")[1];
                Order o = orders.findById(orderId).orElse(null);
                if (o != null && o.state() == OrderState.IN_DISPUTE) {
                    checkpoints.commit(opId);
                    notes.add(opId + " -> committed (dispute opened)");
                    audit.record("system", "CHECKPOINT_RECOVER_COMMIT", opId, null);
                    committed++;
                    continue;
                }
            }
            checkpoints.clear(opId);
            notes.add(opId + " -> cleared (stale/unfinished)");
            audit.record("system", "CHECKPOINT_RECOVER_CLEAR", opId, null);
            cleared++;
        }
        return new RecoveryReport(pending.size(), committed, cleared, notes);
    }
}
