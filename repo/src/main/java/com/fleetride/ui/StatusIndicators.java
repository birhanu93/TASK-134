package com.fleetride.ui;

import com.fleetride.domain.OrderState;
import com.fleetride.security.Authorizer;
import com.fleetride.security.SecuredOrderService;
import com.fleetride.security.SecuredPaymentService;

public final class StatusIndicators {
    public static final class Snapshot {
        public final int jobsPending;
        public final Integer overdueBalances;

        public Snapshot(int jobsPending, Integer overdueBalances) {
            this.jobsPending = jobsPending;
            this.overdueBalances = overdueBalances;
        }

        public String format() {
            String overduePart = overdueBalances == null
                    ? "Overdue balances: —"
                    : "Overdue balances: " + overdueBalances;
            return String.format("Pending jobs: %d | %s", jobsPending, overduePart);
        }
    }

    private final SecuredOrderService orders;
    private final SecuredPaymentService payments;

    public StatusIndicators(SecuredOrderService orders, SecuredPaymentService payments) {
        this.orders = orders;
        this.payments = payments;
    }

    public Snapshot snapshot() {
        int pending = orders.listByState(OrderState.PENDING_MATCH).size();
        Integer overdue = countOverdueOrNull();
        return new Snapshot(pending, overdue);
    }

    private Integer countOverdueOrNull() {
        try {
            int overdue = 0;
            for (var o : orders.listByState(OrderState.COMPLETED)) {
                if (payments.isOverdue(o)) overdue++;
            }
            return overdue;
        } catch (Authorizer.ForbiddenException notAllowed) {
            return null;
        }
    }
}
