package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StatusIndicatorsTest {

    @Test
    void snapshotCountsPendingMatchOrdersForAdmin(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("R", "555-0", null);
            Address p = new Address("1 A", "City", null, null, 1);
            Address d = new Address("2 A", "City", null, null, 1);
            TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                    LocalDateTime.now().plusMinutes(20));
            ctx.securedOrderService.create(c.id(), p, d, 1, w, VehicleType.STANDARD,
                    ServicePriority.NORMAL, 3.0, 10, null);
            ctx.securedOrderService.create(c.id(), p, d, 1, w, VehicleType.STANDARD,
                    ServicePriority.NORMAL, 3.0, 10, null);

            StatusIndicators ind = new StatusIndicators(
                    ctx.securedOrderService, ctx.securedPaymentService);
            StatusIndicators.Snapshot snap = ind.snapshot();
            assertEquals(2, snap.jobsPending);
            assertEquals(0, snap.overdueBalances);
            assertTrue(snap.format().contains("Pending jobs: 2"));
            assertTrue(snap.format().contains("Overdue balances: 0"));
        } finally {
            ctx.close();
        }
    }

    @Test
    void dispatcherOverdueIsDashWhenPaymentReadIsForbidden(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.auth.register("disp", "pw2", Role.DISPATCHER);
            ctx.auth.logout();
            ctx.auth.login("disp", "pw2");

            // Dispatcher has CUSTOMER_CREATE and ORDER_* but not PAYMENT_READ, so
            // owning a COMPLETED order forces StatusIndicators' overdue probe to
            // throw ForbiddenException → the Integer slot becomes null → "—".
            Customer c = ctx.securedCustomerService.create("Own Rider", "555-0700", null);
            Address p = new Address("1 A", "City", null, null, 1);
            Address d = new Address("2 A", "City", null, null, 1);
            TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                    LocalDateTime.now().plusMinutes(20));
            Order o = ctx.securedOrderService.create(c.id(), p, d, 1, w,
                    VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 10, null);
            ctx.securedOrderService.accept(o.id());
            ctx.securedOrderService.start(o.id());
            ctx.securedOrderService.complete(o.id());

            StatusIndicators ind = new StatusIndicators(
                    ctx.securedOrderService, ctx.securedPaymentService);
            StatusIndicators.Snapshot snap = ind.snapshot();
            assertEquals(0, snap.jobsPending);
            assertNull(snap.overdueBalances,
                    "dispatcher lacks finance read, so overdue count is unknown (not zero)");
            assertTrue(snap.format().contains("Overdue balances: —"));
        } finally {
            ctx.close();
        }
    }

    @Test
    void overdueIsCountedForFinance(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0702", null);
            Address p = new Address("1 A", "City", null, null, 1);
            Address d = new Address("2 A", "City", null, null, 1);
            TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                    LocalDateTime.now().plusMinutes(20));
            Order o = ctx.securedOrderService.create(c.id(), p, d, 1, w,
                    VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 10, null);
            ctx.securedOrderService.quoteResolving(o);   // sets fare > 0
            ctx.securedOrderService.accept(o.id());
            ctx.securedOrderService.start(o.id());
            ctx.securedOrderService.complete(o.id());

            StatusIndicators ind = new StatusIndicators(
                    ctx.securedOrderService, ctx.securedPaymentService);
            StatusIndicators.Snapshot snap = ind.snapshot();
            assertEquals(0, snap.jobsPending);
            assertEquals(1, snap.overdueBalances,
                    "completed order with an unpaid balance should register as overdue");
        } finally {
            ctx.close();
        }
    }
}
