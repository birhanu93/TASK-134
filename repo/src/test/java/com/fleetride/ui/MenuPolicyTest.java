package com.fleetride.ui;

import com.fleetride.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MenuPolicyTest {

    @Test
    void dispatcherSeesDispatchAndSessionOnly() {
        List<String> menus = MenuPolicy.menuTitlesFor(Role.DISPATCHER);
        assertEquals(List.of("Dispatch", "Session"), menus);
        assertFalse(MenuPolicy.showFinanceMenu(Role.DISPATCHER));
        assertFalse(MenuPolicy.showAdministrationMenu(Role.DISPATCHER));
        assertTrue(MenuPolicy.canDispatchTrips(Role.DISPATCHER));
        assertFalse(MenuPolicy.canExportReconciliation(Role.DISPATCHER));
    }

    @Test
    void financeClerkSeesFinanceButNoAdmin() {
        List<String> menus = MenuPolicy.menuTitlesFor(Role.FINANCE_CLERK);
        assertEquals(List.of("Dispatch", "Finance", "Session"), menus);
        assertTrue(MenuPolicy.showFinanceMenu(Role.FINANCE_CLERK));
        assertFalse(MenuPolicy.showAdministrationMenu(Role.FINANCE_CLERK));
        assertFalse(MenuPolicy.canDispatchTrips(Role.FINANCE_CLERK));
        assertTrue(MenuPolicy.canExportReconciliation(Role.FINANCE_CLERK));
    }

    @Test
    void administratorSeesEverything() {
        List<String> menus = MenuPolicy.menuTitlesFor(Role.ADMINISTRATOR);
        assertEquals(List.of("Dispatch", "Finance", "Administration", "Session"), menus);
        assertTrue(MenuPolicy.showFinanceMenu(Role.ADMINISTRATOR));
        assertTrue(MenuPolicy.showAdministrationMenu(Role.ADMINISTRATOR));
        assertTrue(MenuPolicy.canDispatchTrips(Role.ADMINISTRATOR));
        assertTrue(MenuPolicy.canExportReconciliation(Role.ADMINISTRATOR));
    }

    @Test
    void dispatchItemsIncludeWriteItemsOnlyForMutatingRoles() {
        List<String> dispatcherItems = MenuPolicy.dispatchItemsFor(Role.DISPATCHER);
        assertTrue(dispatcherItems.contains("New Trip"));
        assertTrue(dispatcherItems.contains("Trips & Carpools"));
        assertTrue(dispatcherItems.contains("Customers"));
        assertTrue(dispatcherItems.contains("Attachments"));
        assertTrue(dispatcherItems.contains("Order Timeline"));
        assertTrue(dispatcherItems.contains("Search Orders…"));

        List<String> adminItems = MenuPolicy.dispatchItemsFor(Role.ADMINISTRATOR);
        assertEquals(dispatcherItems, adminItems);

        List<String> financeItems = MenuPolicy.dispatchItemsFor(Role.FINANCE_CLERK);
        assertFalse(financeItems.contains("New Trip"));
        assertFalse(financeItems.contains("Trips & Carpools"));
        assertFalse(financeItems.contains("Customers"));
        assertFalse(financeItems.contains("Attachments"));
        assertTrue(financeItems.contains("Order Timeline"));
        assertTrue(financeItems.contains("Search Orders…"));
    }

    @Test
    void financeItemsOnlyForFinanceAndAdmin() {
        assertEquals(List.of(), MenuPolicy.financeItemsFor(Role.DISPATCHER));
        assertEquals(List.of("Settlement", "Invoices", "Export reconciliation CSV"),
                MenuPolicy.financeItemsFor(Role.FINANCE_CLERK));
        assertEquals(List.of("Settlement", "Invoices", "Export reconciliation CSV"),
                MenuPolicy.financeItemsFor(Role.ADMINISTRATOR));
    }

    @Test
    void administrationItemsOnlyForAdmin() {
        assertEquals(List.of(), MenuPolicy.administrationItemsFor(Role.DISPATCHER));
        assertEquals(List.of(), MenuPolicy.administrationItemsFor(Role.FINANCE_CLERK));
        assertEquals(List.of("Users & roles", "Configuration center",
                        "Coupons & subsidies", "Updates", "Health & audit"),
                MenuPolicy.administrationItemsFor(Role.ADMINISTRATOR));
    }

    @Test
    void signInHeaderTogglesOnFirstRun() {
        assertEquals(List.of("No users exist — create the initial administrator.",
                        "Bootstrap administrator"),
                MenuPolicy.signInHeaderFor(true));
        assertEquals(List.of("Sign in to FleetRide", "Sign in"),
                MenuPolicy.signInHeaderFor(false));
    }

    @Test
    void menuTitlesListIsImmutable() {
        List<String> menus = MenuPolicy.menuTitlesFor(Role.ADMINISTRATOR);
        assertThrows(UnsupportedOperationException.class, () -> menus.add("Extra"));
    }
}
