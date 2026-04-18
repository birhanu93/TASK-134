package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Customer;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TripManagementWindowTest extends FxTestBase {

    @Test
    void createsTripAndAddsRider(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Carpooler", "555-0700", null);

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.vehicleBox.setValue(VehicleType.STANDARD);
                w.capacity.getValueFactory().setValue(4);
                w.timeFrom.setText("14:00");
                w.timeTo.setText("15:00");
                w.driver.setText("Driver A");
                w.createBtn.fire();
                assertTrue(w.feedback.getText().startsWith("Created trip"));
                assertEquals(1, w.tripList.getItems().size());

                w.tripList.getSelectionModel().select(0);
                w.customerBox.setValue(c);
                w.pickupLine.setText("1 Share St");
                w.pickupCity.setText("City");
                w.dropLine.setText("2 Share St");
                w.dropCity.setText("City");
                w.riderCount.getValueFactory().setValue(2);
                w.priorityBox.setValue(ServicePriority.NORMAL);
                w.miles.setText("2.5");
                w.duration.setText("12");
                w.addRiderBtn.fire();
                assertTrue(w.feedback.getText().contains("Added rider"));
                assertEquals(1, w.ridersList.getItems().size());
            });

            var trips = ctx.securedTripService.list();
            assertEquals(1, trips.size());
            assertEquals(TripStatus.PLANNING, trips.get(0).status());
            assertEquals(1, ctx.securedTripService.riderOrders(trips.get(0).id()).size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void dispatchCloseAndCancelActionsAreGuardedBySelection(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.dispatchBtn.fire();
                assertEquals("Select a trip first.", w.feedback.getText());
                w.closeBtn.fire();
                assertEquals("Select a trip first.", w.feedback.getText());
                w.cancelBtn.fire();
                assertEquals("Select a trip first.", w.feedback.getText());
                w.addRiderBtn.fire();
                assertEquals("Select a trip first.", w.feedback.getText());
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void dispatchMovesTripToDispatchedStatus(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0701", null);

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.timeFrom.setText("10:00");
                w.timeTo.setText("11:00");
                w.createBtn.fire();
                w.tripList.getSelectionModel().select(0);
                w.customerBox.setValue(c);
                w.pickupLine.setText("1 A");
                w.pickupCity.setText("B");
                w.dropLine.setText("2 A");
                w.dropCity.setText("B");
                w.miles.setText("2.5");
                w.duration.setText("10");
                w.addRiderBtn.fire();
                w.dispatchBtn.fire();
                assertTrue(w.feedback.getText().startsWith("Dispatched"));
            });

            assertEquals(TripStatus.DISPATCHED,
                    ctx.securedTripService.list().get(0).status());
        } finally {
            ctx.close();
        }
    }

    @Test
    void addRiderWithoutCustomerReportsFeedback(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.timeFrom.setText("10:00");
                w.timeTo.setText("11:00");
                w.createBtn.fire();
                w.tripList.getSelectionModel().select(0);
                w.addRiderBtn.fire();
                assertEquals("Pick a customer.", w.feedback.getText());
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void saveDelegatesToCreateTrip(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.timeFrom.setText("10:00");
                w.timeTo.setText("11:00");
                w.save();
                assertTrue(w.feedback.getText().startsWith("Created trip"));
            });

            assertEquals(1, ctx.securedTripService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void cancelActionMarksTripCanceled(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.timeFrom.setText("10:00");
                w.timeTo.setText("11:00");
                w.createBtn.fire();
                w.tripList.getSelectionModel().select(0);
                w.cancelBtn.fire();
            });

            assertEquals(TripStatus.CANCELED,
                    ctx.securedTripService.list().get(0).status());
        } finally {
            ctx.close();
        }
    }

    @Test
    void dispatchEmptyTripSurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                TripManagementWindow w = new TripManagementWindow(ctx);
                w.build();
                w.timeFrom.setText("10:00");
                w.timeTo.setText("11:00");
                w.createBtn.fire();
                w.tripList.getSelectionModel().select(0);
                w.dispatchBtn.fire();
            });
            assertNotNull(ErrorAlerts.lastError());
            assertTrue(ErrorAlerts.lastError().contains("empty"));
        } finally {
            ctx.close();
        }
    }
}
