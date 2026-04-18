package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CustomerManagementWindowTest extends FxTestBase {

    @Test
    void saveAddsCustomer(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                CustomerManagementWindow w = new CustomerManagementWindow(ctx);
                w.build();
                w.name.setText("Grace H.");
                w.phone.setText("555-9999");
                w.token.setText("tok-1234567890");
                w.save();
                assertEquals("Customer added.", w.feedback.getText());
                assertEquals("", w.name.getText());
            });

            assertEquals(1, ctx.securedCustomerService.list().size());
            Customer c = ctx.securedCustomerService.list().get(0);
            assertEquals("Grace H.", c.name());
        } finally {
            ctx.close();
        }
    }

    @Test
    void blankTokenStoredAsNull(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                CustomerManagementWindow w = new CustomerManagementWindow(ctx);
                w.build();
                w.name.setText("No Token");
                w.phone.setText("555-0000");
                w.token.setText("   ");
                w.save();
            });

            Customer c = ctx.securedCustomerService.list().get(0);
            assertNull(ctx.securedCustomerService.maskedPaymentToken(c));
        } finally {
            ctx.close();
        }
    }

    @Test
    void deleteRemovesSelectedCustomer(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer victim = ctx.securedCustomerService.create("Delete Me", "555-0200", null);

            runFx(() -> {
                CustomerManagementWindow w = new CustomerManagementWindow(ctx);
                w.build();
                w.list.getSelectionModel().select(victim);
                w.deleteBtn.fire();
                assertTrue(w.list.getItems().isEmpty());
            });

            assertEquals(0, ctx.securedCustomerService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void invalidPhoneSurfacesAsError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                CustomerManagementWindow w = new CustomerManagementWindow(ctx);
                w.build();
                w.name.setText("");
                w.phone.setText("");
                w.save();
            });
            assertNotNull(ErrorAlerts.lastError());
            assertEquals(0, ctx.securedCustomerService.list().size());
        } finally {
            ctx.close();
        }
    }
}
