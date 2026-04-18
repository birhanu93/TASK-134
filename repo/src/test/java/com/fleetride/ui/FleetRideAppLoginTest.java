package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots {@link FleetRideApp} through its real login/openMainShell pipeline against a
 * headless Monocle platform. Exercises the interactive bootstrap path, the ordinary
 * sign-in path, and the role-gated menu bar.
 */
class FleetRideAppLoginTest extends FxTestBase {

    @Test
    void interactiveBootstrapCreatesAdminAndOpensMainShell(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            FleetRideApp app = new FleetRideApp();
            app.attach(ctx);

            runFx(() -> {
                Stage stage = new Stage();
                app.login(stage);
                // First-run UI: header + button label reflect bootstrap mode.
                Label header = (Label) stage.getScene().lookup("#loginHeader");
                Button submit = (Button) stage.getScene().lookup("#loginSubmit");
                assertEquals("No users exist — create the initial administrator.",
                        header.getText());
                assertEquals("Bootstrap administrator", submit.getText());

                TextField user = (TextField) stage.getScene().lookup("#loginUsername");
                PasswordField pwd = (PasswordField) stage.getScene().lookup("#loginPassword");
                user.setText("admin");
                pwd.setText("pw");
                submit.fire();

                // After bootstrap + login, scene is replaced by the main shell.
                assertEquals("FleetRide Console", stage.getTitle());
                Label status = (Label) stage.getScene().lookup("#statusLabel");
                assertNotNull(status);
                assertTrue(status.getText().contains("admin"));
                assertTrue(status.getText().contains("ADMINISTRATOR"));
            });

            assertTrue(ctx.auth.currentUser().isPresent());
            assertEquals(Role.ADMINISTRATOR, ctx.auth.currentUser().get().role());
        } finally {
            ctx.close();
        }
    }

    @Test
    void signInPathUsedAfterBootstrapIsShownAsSignIn(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.logout();

            FleetRideApp app = new FleetRideApp();
            app.attach(ctx);
            runFx(() -> {
                Stage stage = new Stage();
                app.login(stage);
                Label header = (Label) stage.getScene().lookup("#loginHeader");
                Button submit = (Button) stage.getScene().lookup("#loginSubmit");
                assertEquals("Sign in to FleetRide", header.getText());
                assertEquals("Sign in", submit.getText());

                TextField user = (TextField) stage.getScene().lookup("#loginUsername");
                PasswordField pwd = (PasswordField) stage.getScene().lookup("#loginPassword");
                user.setText("admin");
                pwd.setText("pw");
                submit.fire();

                assertEquals("FleetRide Console", stage.getTitle());
            });
            assertTrue(ctx.auth.currentUser().isPresent());
        } finally {
            ctx.close();
        }
    }

    @Test
    void badPasswordSurfacesInlineError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.logout();

            FleetRideApp app = new FleetRideApp();
            app.attach(ctx);
            runFx(() -> {
                Stage stage = new Stage();
                app.login(stage);
                TextField user = (TextField) stage.getScene().lookup("#loginUsername");
                PasswordField pwd = (PasswordField) stage.getScene().lookup("#loginPassword");
                Button submit = (Button) stage.getScene().lookup("#loginSubmit");
                Label err = (Label) stage.getScene().lookup("#loginError");
                user.setText("admin");
                pwd.setText("WRONG");
                submit.fire();

                // Scene must not have been swapped for the shell.
                assertEquals("FleetRide – Sign in", stage.getTitle());
                assertNotNull(err);
                assertFalse(err.getText().isBlank(),
                        "error label should describe the auth failure");
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void dispatcherMenuHidesFinanceAndAdministration(@TempDir Path dir) {
        menuBarTitles(dir, Role.DISPATCHER, titles -> {
            assertTrue(titles.contains("Dispatch"));
            assertTrue(titles.contains("Session"));
            assertFalse(titles.contains("Finance"));
            assertFalse(titles.contains("Administration"));
        });
    }

    @Test
    void financeClerkMenuHidesAdministrationKeepsFinance(@TempDir Path dir) {
        menuBarTitles(dir, Role.FINANCE_CLERK, titles -> {
            assertTrue(titles.contains("Dispatch"));
            assertTrue(titles.contains("Finance"));
            assertFalse(titles.contains("Administration"));
            assertTrue(titles.contains("Session"));
        });
    }

    @Test
    void administratorMenuShowsEveryTopLevel(@TempDir Path dir) {
        menuBarTitles(dir, Role.ADMINISTRATOR, titles -> {
            assertEquals(List.of("Dispatch", "Finance", "Administration", "Session"), titles);
        });
    }

    @Test
    void dispatcherDispatchMenuOmitsMutationItems(@TempDir Path dir) {
        dispatchMenuItems(dir, Role.FINANCE_CLERK, items -> {
            // Finance cannot dispatch: only read-only items remain.
            assertFalse(items.contains("New Trip"));
            assertFalse(items.contains("Trips & Carpools"));
            assertFalse(items.contains("Customers"));
            assertFalse(items.contains("Attachments"));
            assertTrue(items.contains("Order Timeline"));
            assertTrue(items.contains("Search Orders…"));
        });
    }

    @Test
    void dispatcherDispatchMenuIncludesMutationItems(@TempDir Path dir) {
        dispatchMenuItems(dir, Role.DISPATCHER, items -> {
            assertTrue(items.contains("New Trip"));
            assertTrue(items.contains("Trips & Carpools"));
            assertTrue(items.contains("Customers"));
            assertTrue(items.contains("Attachments"));
            assertTrue(items.contains("Order Timeline"));
            assertTrue(items.contains("Search Orders…"));
        });
    }

    private void menuBarTitles(Path dir, Role role, java.util.function.Consumer<List<String>> check) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            if (role != Role.ADMINISTRATOR) {
                ctx.auth.login("admin", "pw");
                ctx.auth.register("user", "pw2", role);
                ctx.auth.logout();
                ctx.auth.login("user", "pw2");
            } else {
                ctx.auth.login("admin", "pw");
            }

            FleetRideApp app = new FleetRideApp();
            app.attach(ctx);
            runFx(() -> {
                MenuBar bar = app.buildMenuBar(new Stage(), role);
                List<String> titles = bar.getMenus().stream().map(Menu::getText).toList();
                check.accept(titles);
            });
        } finally {
            ctx.close();
        }
    }

    private void dispatchMenuItems(Path dir, Role role, java.util.function.Consumer<List<String>> check) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            if (role != Role.ADMINISTRATOR) {
                ctx.auth.login("admin", "pw");
                ctx.auth.register("user", "pw2", role);
                ctx.auth.logout();
                ctx.auth.login("user", "pw2");
            } else {
                ctx.auth.login("admin", "pw");
            }

            FleetRideApp app = new FleetRideApp();
            app.attach(ctx);
            runFx(() -> {
                MenuBar bar = app.buildMenuBar(new Stage(), role);
                Menu dispatch = bar.getMenus().stream()
                        .filter(m -> "Dispatch".equals(m.getText()))
                        .findFirst().orElseThrow();
                List<String> items = dispatch.getItems().stream()
                        .map(javafx.scene.control.MenuItem::getText)
                        .toList();
                check.accept(items);
            });
        } finally {
            ctx.close();
        }
    }
}
