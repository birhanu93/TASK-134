package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Invoice;
import com.fleetride.domain.Order;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceWindowTest extends FxTestBase {

    private Order completedOrder(AppContext ctx) {
        Customer c = ctx.securedCustomerService.create("Billable", "555-0400", null);
        Address p = new Address("1 Bill St", "City", null, null, 1);
        Address d = new Address("2 Bill St", "City", null, null, 1);
        TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(35));
        Order o = ctx.securedOrderService.create(c.id(), p, d, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 10, null);
        // Quote to set a non-zero fare so the invoice has a balance > 0 to collect.
        ctx.securedOrderService.quoteResolving(o);
        ctx.securedOrderService.accept(o.id());
        ctx.securedOrderService.start(o.id());
        ctx.securedOrderService.complete(o.id());
        return o;
    }

    @Test
    void issueMarkPaidAndCancel(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order a = completedOrder(ctx);
            Order b = completedOrder(ctx);

            runFx(() -> {
                InvoiceWindow w = new InvoiceWindow(ctx);
                w.build();
                assertEquals(0, w.list.getItems().size());

                w.orderBox.setValue(a);
                w.notes.setText("a-invoice");
                w.issueBtn.fire();

                w.orderBox.setValue(b);
                w.notes.setText("b-invoice");
                w.issueBtn.fire();
                assertEquals(2, w.list.getItems().size());

                Invoice first = w.list.getItems().get(0);
                w.list.getSelectionModel().select(first);
                w.paidBtn.fire();

                Invoice second = w.list.getItems().stream()
                        .filter(i -> !i.id().equals(first.id())).findFirst().orElseThrow();
                w.list.getSelectionModel().select(second);
                w.cancelBtn.fire();
            });

            var statuses = ctx.securedInvoiceService.listAll().stream()
                    .map(i -> i.status().name()).sorted().toList();
            assertEquals(java.util.List.of("CANCELED", "PAID"), statuses);
        } finally {
            ctx.close();
        }
    }

    @Test
    void issueWithNoOrderSelectionIsNoop(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            completedOrder(ctx);

            runFx(() -> {
                InvoiceWindow w = new InvoiceWindow(ctx);
                w.build();
                w.issueBtn.fire();
            });
            assertEquals(0, ctx.securedInvoiceService.listAll().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void markPaidWithoutSelectionIsNoop(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = completedOrder(ctx);

            runFx(() -> {
                InvoiceWindow w = new InvoiceWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                w.issueBtn.fire();
                w.list.getSelectionModel().clearSelection();
                w.paidBtn.fire();
                w.cancelBtn.fire();
            });
            assertEquals("ISSUED",
                    ctx.securedInvoiceService.listAll().get(0).status().name());
        } finally {
            ctx.close();
        }
    }

    @Test
    void doublePaidSurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = completedOrder(ctx);

            ErrorAlerts.reset();
            runFx(() -> {
                InvoiceWindow w = new InvoiceWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                w.issueBtn.fire();
                w.list.getSelectionModel().select(w.list.getItems().get(0));
                w.paidBtn.fire();
                // Re-mark the already-paid invoice
                w.list.getSelectionModel().select(w.list.getItems().get(0));
                w.paidBtn.fire();
            });
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }
}
