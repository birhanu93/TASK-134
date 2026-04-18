package com.fleetride.ui;

import com.fleetride.domain.Role;

import java.util.ArrayList;
import java.util.List;

public final class MenuPolicy {
    private MenuPolicy() {}

    public static boolean showFinanceMenu(Role role) {
        return role == Role.FINANCE_CLERK || role == Role.ADMINISTRATOR;
    }

    public static boolean showAdministrationMenu(Role role) {
        return role == Role.ADMINISTRATOR;
    }

    public static boolean canDispatchTrips(Role role) {
        return role == Role.DISPATCHER || role == Role.ADMINISTRATOR;
    }

    public static boolean canExportReconciliation(Role role) {
        return showFinanceMenu(role);
    }

    public static List<String> menuTitlesFor(Role role) {
        List<String> out = new ArrayList<>();
        out.add("Dispatch");
        if (showFinanceMenu(role)) out.add("Finance");
        if (showAdministrationMenu(role)) out.add("Administration");
        out.add("Session");
        return List.copyOf(out);
    }

    public static List<String> dispatchItemsFor(Role role) {
        List<String> out = new ArrayList<>();
        if (canDispatchTrips(role)) {
            out.add("New Trip");
            out.add("Trips & Carpools");
            out.add("Customers");
            out.add("Attachments");
        }
        out.add("Order Timeline");
        out.add("Search Orders…");
        return List.copyOf(out);
    }

    public static List<String> financeItemsFor(Role role) {
        if (!showFinanceMenu(role)) return List.of();
        return List.of("Settlement", "Invoices", "Export reconciliation CSV");
    }

    public static List<String> administrationItemsFor(Role role) {
        if (!showAdministrationMenu(role)) return List.of();
        return List.of("Users & roles", "Configuration center",
                "Coupons & subsidies", "Updates", "Health & audit");
    }

    public static List<String> signInHeaderFor(boolean firstRun) {
        return firstRun
                ? List.of("No users exist — create the initial administrator.",
                          "Bootstrap administrator")
                : List.of("Sign in to FleetRide", "Sign in");
    }
}
