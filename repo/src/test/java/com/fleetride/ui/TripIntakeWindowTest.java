package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Customer;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.VehicleType;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TripIntakeWindowTest extends FxTestBase {

    @Test
    void submitCreatesOrderAndPopulatesFeedback(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer rider = ctx.securedCustomerService.create("Rider One", "555-0100", null);

            runFx(() -> {
                TripIntakeWindow w = new TripIntakeWindow(ctx);
                Stage stage = w.build();
                w.customerBox.setValue(rider);
                w.pickupLine.setText("10 Market St");
                w.pickupCity.setText("Springfield");
                w.dropLine.setText("99 Park Ave");
                w.dropCity.setText("Springfield");
                w.riders.getValueFactory().setValue(2);
                w.vehicle.setValue(VehicleType.STANDARD);
                w.priority.setValue(ServicePriority.PRIORITY);
                w.miles.setText("4.2");
                w.durationMin.setText("18");
                w.pickupFloorNotes.setText("loading dock");
                w.dropFloorNotes.setText("ring bell");
                w.save();
                assertTrue(w.feedback.getText().startsWith("Created "),
                        "expected feedback, got: " + w.feedback.getText());
                assertTrue(stage.getTitle().contains("Trip Intake"));
            });

            assertEquals(1, ctx.securedOrderService.list().size());
            var o = ctx.securedOrderService.list().get(0);
            assertEquals(rider.id(), o.customerId());
            assertEquals("loading dock", o.pickupFloorNotes());
            assertEquals("ring bell", o.dropoffFloorNotes());
        } finally {
            ctx.close();
        }
    }

    @Test
    void submittingWithoutCustomerReportsError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                TripIntakeWindow w = new TripIntakeWindow(ctx);
                w.build();
                w.pickupLine.setText("X");
                w.pickupCity.setText("Y");
                w.dropLine.setText("X");
                w.dropCity.setText("Y");
                w.save();
            });
            assertNotNull(ErrorAlerts.lastError());
            assertTrue(ErrorAlerts.lastError().contains("customer"));
            assertEquals(0, ctx.securedOrderService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void defaultsApplyWhenVehicleAndPriorityNotSelected(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer rider = ctx.securedCustomerService.create("Rider Two", "555-0101", null);

            runFx(() -> {
                TripIntakeWindow w = new TripIntakeWindow(ctx);
                w.build();
                w.customerBox.setValue(rider);
                w.pickupLine.setText("1 A St");
                w.pickupCity.setText("C");
                w.dropLine.setText("2 B St");
                w.dropCity.setText("C");
                // vehicle and priority intentionally left null → defaults apply.
                w.save();
            });

            var o = ctx.securedOrderService.list().get(0);
            assertEquals(VehicleType.STANDARD, o.vehicleType());
            assertEquals(ServicePriority.NORMAL, o.priority());
        } finally {
            ctx.close();
        }
    }
}
