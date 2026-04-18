package com.fleetride.ui;

import com.fleetride.domain.Order;

import java.util.List;
import java.util.Locale;

public final class OrderSearchFilter {
    private OrderSearchFilter() {}

    public static List<Order> apply(List<Order> input, String query) {
        if (query == null || query.isBlank()) return List.copyOf(input);
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return input.stream().filter(o -> matches(o, needle)).toList();
    }

    public static boolean matches(Order o, String needleLower) {
        return contains(o.id(), needleLower)
                || contains(o.customerId(), needleLower)
                || contains(o.state().name(), needleLower)
                || contains(o.pickup().format(), needleLower)
                || contains(o.dropoff().format(), needleLower)
                || contains(o.pickupFloorNotes(), needleLower)
                || contains(o.dropoffFloorNotes(), needleLower)
                || contains(o.couponCode(), needleLower);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }
}
