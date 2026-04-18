package com.fleetride.service;

import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;

import java.time.LocalDateTime;

public final class OrderStateMachine {

    public static final class InvalidTransitionException extends RuntimeException {
        public InvalidTransitionException(String msg) { super(msg); }
    }

    public void accept(Order order, LocalDateTime at) {
        require(order, OrderState.PENDING_MATCH, "accept");
        order.setState(OrderState.ACCEPTED);
        order.setAcceptedAt(at);
    }

    public void start(Order order, LocalDateTime at) {
        require(order, OrderState.ACCEPTED, "start");
        order.setState(OrderState.IN_PROGRESS);
        order.setStartedAt(at);
    }

    public void complete(Order order, LocalDateTime at) {
        require(order, OrderState.IN_PROGRESS, "complete");
        order.setState(OrderState.COMPLETED);
        order.setCompletedAt(at);
    }

    public void cancel(Order order, LocalDateTime at) {
        OrderState s = order.state();
        if (s == OrderState.COMPLETED || s == OrderState.CANCELED || s == OrderState.IN_DISPUTE) {
            throw new InvalidTransitionException("cannot cancel from " + s);
        }
        order.setState(OrderState.CANCELED);
        order.setCanceledAt(at);
    }

    public void openDispute(Order order, LocalDateTime at, int disputeWindowDays) {
        if (order.state() != OrderState.COMPLETED) {
            throw new InvalidTransitionException("dispute only allowed from COMPLETED");
        }
        LocalDateTime deadline = order.completedAt().plusDays(disputeWindowDays);
        if (at.isAfter(deadline)) {
            throw new InvalidTransitionException("dispute window expired");
        }
        order.setState(OrderState.IN_DISPUTE);
        order.setDisputedAt(at);
    }

    public boolean shouldAutoCancel(Order order, LocalDateTime now, int autoCancelMinutes) {
        if (order.state() != OrderState.PENDING_MATCH) return false;
        return java.time.Duration.between(order.createdAt(), now).toMinutes() >= autoCancelMinutes;
    }

    private void require(Order order, OrderState expected, String action) {
        if (order.state() != expected) {
            throw new InvalidTransitionException("cannot " + action + " from " + order.state());
        }
    }
}
