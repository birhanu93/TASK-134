package com.fleetride.domain;

/**
 * Lifecycle of a Trip (a group of rider orders dispatched together).
 *
 * <ul>
 *   <li>PLANNING — still accepting rider orders</li>
 *   <li>DISPATCHED — trip is on the road (at least one order started)</li>
 *   <li>CLOSED — all constituent orders reached a terminal state</li>
 *   <li>CANCELED — trip was canceled before or during execution</li>
 * </ul>
 */
public enum TripStatus {
    PLANNING,
    DISPATCHED,
    CLOSED,
    CANCELED
}
