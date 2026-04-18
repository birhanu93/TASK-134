package com.fleetride.security;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Role;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.Trip;
import com.fleetride.domain.TripStatus;
import com.fleetride.domain.VehicleType;
import com.fleetride.repository.sqlite.Database;
import com.fleetride.service.Clock;
import com.fleetride.service.SignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the read and ownership-filter paths on {@link SecuredTripService}
 * that the dispatcher-focused tests don't reach:
 * {@code find}, {@code listByStatus}, {@code totalTripFare},
 * {@code riderSeatsUsed}, {@code attachExistingOrder}, plus the
 * dispatcher-scope filtering of {@code list()}/{@code find()}.
 */
class SecuredTripAdminReadTest {

    private AppContext wire(Path dir) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
        AtomicInteger n = new AtomicInteger();
        AppContext.Config cfg = new AppContext.Config(
                Database.file(dir.resolve("t.db").toString()),
                Clock.system(),
                () -> "id-" + n.incrementAndGet(),
                "test-master",
                dir.resolve("att"),
                dir.resolve("upd"),
                dir.resolve("log.ndjson"),
                dir.resolve("machine-id"),
                SignatureVerifier.fromPem(pem));
        return new AppContext(cfg);
    }

    private Trip createTripWithRider(AppContext ctx, Customer c) {
        Trip t = ctx.securedTripService.create(VehicleType.STANDARD, 4,
                new TimeWindow(LocalDateTime.now().plusMinutes(10),
                        LocalDateTime.now().plusMinutes(40)),
                "driver");
        ctx.securedTripService.addRiderOrder(t.id(), c.id(),
                new Address("1 A", "City", null, null, null),
                new Address("2 A", "City", null, null, null),
                2, ServicePriority.NORMAL, 3.0, 10, null, null, null);
        return t;
    }

    @Test
    void adminReadsListByStatusFindTotalSeats(@TempDir Path dir) throws Exception {
        try (AppContext ctx = wire(dir)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Rider", "555-0999", null);
            Trip t = createTripWithRider(ctx, c);

            assertTrue(ctx.securedTripService.find(t.id()).isPresent());
            assertEquals(List.of(t),
                    ctx.securedTripService.listByStatus(TripStatus.PLANNING));
            assertNotNull(ctx.securedTripService.totalTripFare(t.id()));
            assertEquals(2, ctx.securedTripService.riderSeatsUsed(t.id()));
            assertEquals(List.of(), ctx.securedTripService.listByStatus(TripStatus.CLOSED));
            assertTrue(ctx.securedTripService.find("nope").isEmpty());
        }
    }

    @Test
    void dispatcherSeesOnlyOwnedTripsViaListAndFind(@TempDir Path dir) throws Exception {
        try (AppContext ctx = wire(dir)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer adminCust = ctx.securedCustomerService.create("Admin Rider", "555-0100", null);
            Trip adminTrip = createTripWithRider(ctx, adminCust);

            ctx.auth.register("disp", "pw2", Role.DISPATCHER);
            ctx.auth.logout();
            ctx.auth.login("disp", "pw2");
            Customer dispCust = ctx.securedCustomerService.create("Disp Rider", "555-0101", null);
            Trip dispTrip = createTripWithRider(ctx, dispCust);

            // Dispatcher sees only their own trip in list() and listByStatus().
            assertEquals(List.of(dispTrip), ctx.securedTripService.list());
            assertEquals(List.of(dispTrip),
                    ctx.securedTripService.listByStatus(TripStatus.PLANNING));
            // find() returns empty for trips owned by another user.
            assertTrue(ctx.securedTripService.find(adminTrip.id()).isEmpty());
            assertTrue(ctx.securedTripService.find(dispTrip.id()).isPresent());

            // attachExistingOrder from a different user's order is refused.
            assertThrows(RuntimeException.class,
                    () -> ctx.securedTripService.attachExistingOrder(dispTrip.id(), "bogus"));
        }
    }

    @Test
    void dispatcherCannotActOnAnotherUsersTrip(@TempDir Path dir) throws Exception {
        try (AppContext ctx = wire(dir)) {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer adminCust = ctx.securedCustomerService.create("AC", "555-0200", null);
            Trip adminTrip = createTripWithRider(ctx, adminCust);

            ctx.auth.register("disp", "pw2", Role.DISPATCHER);
            ctx.auth.logout();
            ctx.auth.login("disp", "pw2");

            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.dispatch(adminTrip.id()));
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.close(adminTrip.id()));
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.cancel(adminTrip.id(), "reason"));
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.riderOrders(adminTrip.id()));
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.totalTripFare(adminTrip.id()));
            assertThrows(Authorizer.ForbiddenException.class,
                    () -> ctx.securedTripService.riderSeatsUsed(adminTrip.id()));
        }
    }
}
