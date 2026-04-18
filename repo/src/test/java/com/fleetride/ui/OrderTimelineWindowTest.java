package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.OrderState;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderTimelineWindowTest extends FxTestBase {

    private Order seedOrder(AppContext ctx, Customer c, String pickupLine) {
        Address p = new Address(pickupLine, "City", null, null, 1);
        Address d = new Address("2 Elm St", "City", null, null, 1);
        TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(30),
                LocalDateTime.now().plusMinutes(90));
        return ctx.securedOrderService.create(c.id(), p, d, 2, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 10, null);
    }

    @Test
    void filterNarrowsToMatchingRows(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0100", null);
            seedOrder(ctx, c, "1 Baker Street");
            seedOrder(ctx, c, "7 Downing Street");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                assertEquals(2, w.view.getItems().size());
                w.searchField.setText("baker");
                assertEquals(1, w.view.getItems().size());
                assertTrue(w.view.getItems().get(0).pickup().format().toLowerCase().contains("baker"));
                assertTrue(w.matchCount.getText().contains("1 match"));
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void initialQueryPrepopulatesSearchField(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0101", null);
            Order found = seedOrder(ctx, c, "Airport Terminal 1");
            seedOrder(ctx, c, "10 Side Ave");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx)
                        .withInitialQuery("airport");
                w.build();
                assertEquals("airport", w.searchField.getText());
                assertEquals(1, w.view.getItems().size());
                assertEquals(found.id(), w.view.getItems().get(0).id());
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void acceptContextItemTransitionsOrder(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0102", null);
            Order o = seedOrder(ctx, c, "1 Main St");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                w.acceptItem.fire();
            });
            var fresh = ctx.securedOrderService.find(o.id()).orElseThrow();
            assertEquals(OrderState.ACCEPTED, fresh.state());
        } finally {
            ctx.close();
        }
    }

    @Test
    void fullAcceptStartCompleteWorkflow(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0103", null);
            Order o = seedOrder(ctx, c, "1 Flow St");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                w.acceptItem.fire();
                w.view.getSelectionModel().select(w.view.getItems().get(0));
                w.startItem.fire();
                w.view.getSelectionModel().select(w.view.getItems().get(0));
                w.completeItem.fire();
            });
            assertEquals(OrderState.COMPLETED,
                    ctx.securedOrderService.find(o.id()).orElseThrow().state());
        } finally {
            ctx.close();
        }
    }

    @Test
    void cancelContextItemCancelsOrder(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0104", null);
            Order o = seedOrder(ctx, c, "1 Cancel Rd");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                w.cancelItem.fire();
            });
            assertEquals(OrderState.CANCELED,
                    ctx.securedOrderService.find(o.id()).orElseThrow().state());
        } finally {
            ctx.close();
        }
    }

    @Test
    void disputeContextItemRequiresCompletedOrder(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0105", null);
            Order o = seedOrder(ctx, c, "1 Dispute Way");
            ctx.securedOrderService.accept(o.id());
            ctx.securedOrderService.start(o.id());
            ctx.securedOrderService.complete(o.id());

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(
                        w.view.getItems().stream().filter(r -> r.id().equals(o.id())).findFirst().orElseThrow());
                w.disputeItem.fire();
            });
            assertEquals(OrderState.IN_DISPUTE,
                    ctx.securedOrderService.find(o.id()).orElseThrow().state());
        } finally {
            ctx.close();
        }
    }

    @Test
    void contextMenuItemWithNoSelectionIsNoop(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0106", null);
            seedOrder(ctx, c, "1 None Rd");

            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().clearSelection();
                w.acceptItem.fire();
                w.copyIdItem.fire();
                w.copyPickupItem.fire();
                w.copyDropoffItem.fire();
            });
            // no exceptions, no state change
            assertEquals(1, ctx.securedOrderService.list().size());
            assertEquals(OrderState.PENDING_MATCH,
                    ctx.securedOrderService.list().get(0).state());
        } finally {
            ctx.close();
        }
    }

    @Test
    void invalidTransitionRaisesAlertAndLeavesStateUntouched(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0107", null);
            Order o = seedOrder(ctx, c, "1 Bad St");

            ErrorAlerts.reset();
            runFx(() -> {
                OrderTimelineWindow w = new OrderTimelineWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                // complete from PENDING_MATCH is illegal
                w.completeItem.fire();
            });
            assertNotNull(ErrorAlerts.lastError());
            assertEquals(OrderState.PENDING_MATCH,
                    ctx.securedOrderService.find(o.id()).orElseThrow().state());
        } finally {
            ctx.close();
        }
    }
}
