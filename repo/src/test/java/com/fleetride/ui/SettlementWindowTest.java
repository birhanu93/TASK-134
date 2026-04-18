package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SettlementWindowTest extends FxTestBase {

    private Order seedOrder(AppContext ctx) {
        Customer c = ctx.securedCustomerService.create("Payer", "555-0300", null);
        Address p = new Address("1 Cash St", "City", null, null, 1);
        Address d = new Address("2 Cash St", "City", null, null, 1);
        TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(35));
        return ctx.securedOrderService.create(c.id(), p, d, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 4.0, 12, null);
    }

    @Test
    void depositAndFinalRecordPayments(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);

            runFx(() -> {
                SettlementWindow w = new SettlementWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                w.tender.setValue(Payment.Tender.CASH);
                w.amountField.setText("5.00");
                w.depositBtn.fire();

                w.view.getSelectionModel().select(o);
                w.amountField.setText("15.00");
                w.finalBtn.fire();

                assertTrue(w.balance.getText().startsWith("Balance:"));
            });

            assertEquals(2, ctx.securedPaymentService.listForOrder(o.id()).size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void depositWithoutSelectionSurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                SettlementWindow w = new SettlementWindow(ctx);
                w.build();
                w.depositBtn.fire();
            });
            assertEquals("Pick an order.", ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void refundRequiresPriorPayment(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);

            ErrorAlerts.reset();
            runFx(() -> {
                SettlementWindow w = new SettlementWindow(ctx);
                w.build();
                w.view.getSelectionModel().select(o);
                w.amountField.setText("1.00");
                w.refundBtn.fire();
            });
            // Refund without prior payment fails at service layer.
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void selectingAnOrderUpdatesBalanceLabel(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);

            runFx(() -> {
                SettlementWindow w = new SettlementWindow(ctx);
                w.build();
                assertEquals("Balance: —", w.balance.getText());
                w.view.getSelectionModel().select(o);
                assertTrue(w.balance.getText().startsWith("Balance:"));
                assertNotEquals("Balance: —", w.balance.getText());
                w.view.getSelectionModel().clearSelection();
                assertEquals("Balance: —", w.balance.getText());
            });
        } finally {
            ctx.close();
        }
    }
}
