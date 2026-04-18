package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import com.fleetride.service.Clock;
import com.fleetride.service.IdGenerator;
import com.fleetride.service.SignatureVerifier;
import com.fleetride.repository.sqlite.Database;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;

public class FleetRideApp extends Application {

    AppContext ctx;
    Label statusLabel;
    TrayPresence tray;
    Stage activeChildStage;
    Saveable activeSaveable;

    @Override
    public void start(Stage stage) {
        Path dataDir = Path.of(System.getProperty("fleetride.dataDir",
                System.getProperty("user.home") + "/.fleetride"));
        com.fleetride.service.IOUtil.uncheckedRun(() -> Files.createDirectories(dataDir));
        String masterKey = System.getProperty("fleetride.masterKey",
                System.getenv("FLEETRIDE_MASTER_KEY"));
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "fleetride.masterKey (system property) or FLEETRIDE_MASTER_KEY (env) " +
                    "must be set to a non-empty value by the administrator.");
        }

        Database db = Database.file(dataDir.resolve("fleetride.db").toString());

        Path pubKey = Path.of(System.getProperty("fleetride.updatePublicKey",
                dataDir.resolve("update-public.pem").toString()));
        SignatureVerifier verifier = UpdateTrustCheck.require(pubKey);

        AppContext.Config cfg = new AppContext.Config(db, Clock.system(), IdGenerator.uuid(),
                masterKey,
                dataDir.resolve("attachments"),
                dataDir.resolve("updates"),
                dataDir.resolve("fleetride.log"),
                dataDir.resolve("machine-id"),
                verifier);
        this.ctx = new AppContext(cfg);
        ctx.recoverPendingCheckpoints();
        ctx.activateCurrentUpdate();
        ctx.scheduler.startScheduler(5, 24 * 60, 24 * 60);

        login(stage);
    }

    /** Test seam: start with an already-built {@link AppContext}. */
    void attach(AppContext context) {
        this.ctx = context;
    }

    void login(Stage stage) {
        boolean noUsers = ctx.userRepo.findAll().isEmpty();
        if (noUsers) {
            var creds = BootstrapEnv.parse(System.getenv("FLEETRIDE_BOOTSTRAP_ADMIN"));
            if (creds.isPresent()) {
                ctx.auth.bootstrapAdministrator(creds.get().username(), creds.get().password());
                noUsers = false;
            }
        }
        final boolean firstRun = noUsers;
        TextField user = new TextField();
        user.setPromptText("username");
        user.setId("loginUsername");
        PasswordField pwd = new PasswordField();
        pwd.setPromptText("password");
        pwd.setId("loginPassword");
        String buttonLabel = firstRun ? "Bootstrap administrator" : "Sign in";
        Button ok = new Button(buttonLabel);
        ok.setId("loginSubmit");
        Label err = new Label();
        err.setId("loginError");
        String header = firstRun
                ? "No users exist — create the initial administrator."
                : "Sign in to FleetRide";
        Label headerLabel = new Label(header);
        headerLabel.setId("loginHeader");
        VBox box = new VBox(8, headerLabel, user, pwd, ok, err);
        box.setPadding(new Insets(16));

        ok.setOnAction(e -> {
            try {
                if (firstRun) {
                    ctx.auth.bootstrapAdministrator(user.getText(), pwd.getText());
                }
                ctx.auth.login(user.getText(), pwd.getText());
                openMainShell(stage);
            } catch (RuntimeException ex) {
                err.setText(ex.getMessage());
            }
        });
        stage.setTitle("FleetRide – Sign in");
        stage.setScene(new Scene(box, 360, 220));
        stage.show();
    }

    void openMainShell(Stage stage) {
        Role role = ctx.auth.currentUser().orElseThrow().role();
        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar(stage, role));
        statusLabel = new Label("Welcome, "
                + ctx.auth.currentUser().orElseThrow().username() + " (" + role + ")");
        statusLabel.setId("statusLabel");
        root.setCenter(statusLabel);
        Scene scene = new Scene(root, 1920, 1080);
        bindShortcuts(scene, role);
        stage.setScene(scene);
        stage.setTitle("FleetRide Console");
        stage.setOnCloseRequest(e -> {
            ctx.scheduler.stopScheduler();
            ctx.database().close();
        });

        tray = new TrayPresence(stage,
                () -> new StatusIndicators(ctx.securedOrderService, ctx.securedPaymentService)
                        .snapshot().format(),
                ctx.auth);
        tray.install();
    }

    MenuBar buildMenuBar(Stage stage, Role role) {
        MenuBar bar = new MenuBar();

        Menu dispatch = new Menu("Dispatch");
        if (MenuPolicy.canDispatchTrips(role)) {
            dispatch.getItems().add(item("New Trip",
                    new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                    () -> {
                        TripIntakeWindow w = new TripIntakeWindow(ctx);
                        showChild(w.build(), w);
                    }));
            dispatch.getItems().add(item("Trips & Carpools",
                    null, () -> {
                        TripManagementWindow w = new TripManagementWindow(ctx);
                        showChild(w.build(), w);
                    }));
            dispatch.getItems().add(item("Customers",
                    null, () -> {
                        CustomerManagementWindow w = new CustomerManagementWindow(ctx);
                        showChild(w.build(), w);
                    }));
            dispatch.getItems().add(item("Attachments",
                    null, () -> showChild(new AttachmentWindow(ctx).build(), null)));
        }
        dispatch.getItems().add(item("Order Timeline",
                null,
                () -> showChild(new OrderTimelineWindow(ctx).build(), null)));
        dispatch.getItems().add(item("Search Orders…",
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                this::openSearchDialog));
        bar.getMenus().add(dispatch);

        Menu finance = new Menu("Finance");
        if (MenuPolicy.showFinanceMenu(role)) {
            finance.getItems().add(item("Settlement",
                    null, () -> showChild(new SettlementWindow(ctx).build(), null)));
            finance.getItems().add(item("Invoices",
                    null, () -> showChild(new InvoiceWindow(ctx).build(), null)));
            finance.getItems().add(item("Export reconciliation CSV",
                    new KeyCodeCombination(KeyCode.E,
                            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    this::exportReconciliationCsv));
            bar.getMenus().add(finance);
        }

        if (MenuPolicy.showAdministrationMenu(role)) {
            Menu admin = new Menu("Administration");
            admin.getItems().add(item("Users & roles",
                    null, () -> {
                        UserAdminWindow w = new UserAdminWindow(ctx);
                        showChild(w.build(), w);
                    }));
            admin.getItems().add(item("Configuration center",
                    null, () -> {
                        ConfigCenterWindow w = new ConfigCenterWindow(ctx);
                        Stage s = w.build();
                        showChild(s, w);
                    }));
            admin.getItems().add(item("Coupons & subsidies",
                    null, () -> showChild(new CouponSubsidyWindow(ctx).build(), null)));
            admin.getItems().add(item("Updates",
                    null, () -> showChild(new UpdateWindow(ctx).build(), null)));
            admin.getItems().add(item("Health & audit",
                    null, () -> showChild(new HealthAndAuditWindow(ctx).build(), null)));
            bar.getMenus().add(admin);
        }

        Menu session = new Menu("Session");
        MenuItem lock = new MenuItem("Lock");
        lock.setOnAction(e -> {
            ctx.auth.lock();
            TextInputDialog dlg = new TextInputDialog();
            dlg.setHeaderText("Session locked. Enter password to unlock.");
            dlg.showAndWait().ifPresent(p -> {
                try {
                    ctx.auth.unlock(p);
                } catch (RuntimeException ex) {
                    ErrorAlerts.error(ex.getMessage());
                }
            });
        });
        MenuItem minimize = new MenuItem("Minimize to Tray");
        minimize.setOnAction(e -> minimizeToTray(stage));
        session.getItems().addAll(lock, minimize);
        bar.getMenus().add(session);

        return bar;
    }

    private MenuItem item(String label, KeyCombination accel, Runnable action) {
        MenuItem mi = new MenuItem(label);
        if (accel != null) mi.setAccelerator(accel);
        mi.setOnAction(e -> action.run());
        return mi;
    }

    void bindShortcuts(Scene scene, Role role) {
        if (MenuPolicy.canDispatchTrips(role)) {
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                    () -> {
                        TripIntakeWindow w = new TripIntakeWindow(ctx);
                        showChild(w.build(), w);
                    });
        }
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                this::openSearchDialog);
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                this::triggerSave);
        if (MenuPolicy.canExportReconciliation(role)) {
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.E,
                            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                    this::exportReconciliationCsv);
        }
    }

    void showChild(Stage child, Saveable saveable) {
        activeChildStage = child;
        activeSaveable = saveable;
        child.focusedProperty().addListener((obs, was, is) -> {
            if (Boolean.TRUE.equals(is)) {
                activeChildStage = child;
                activeSaveable = saveable;
            }
        });
        child.show();
    }

    private void minimizeToTray(Stage stage) {
        if (tray != null) {
            tray.minimizeToTray();
        } else {
            stage.setIconified(true);
        }
    }

    void exportReconciliationCsv() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export reconciliation CSV");
        chooser.setInitialFileName("reconciliation-"
                + java.time.LocalDate.now().toString().replace("-", "") + ".csv");
        chooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File file = chooser.showSaveDialog(activeChildStage);
        if (file == null) return;
        try {
            java.nio.file.Path out = ctx.securedReconciliationService.exportCsv(file.toPath());
            if (statusLabel != null) statusLabel.setText("Exported reconciliation CSV: " + out);
            ErrorAlerts.info("Exported reconciliation CSV to " + out);
        } catch (RuntimeException | java.io.IOException ex) {
            ErrorAlerts.error("Reconciliation export failed: " + ex.getMessage());
        }
    }

    private void openSearchDialog() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Search orders");
        dlg.setHeaderText("Enter a query — matches id, customer, state, address, or floor notes.");
        dlg.showAndWait().ifPresent(query ->
                showChild(new OrderTimelineWindow(ctx).withInitialQuery(query).build(), null));
    }

    void triggerSave() {
        if (activeSaveable != null && activeChildStage != null && activeChildStage.isShowing()) {
            activeSaveable.save();
        } else if (statusLabel != null) {
            statusLabel.setText("Nothing to save (focus a Save-capable window first).");
        }
    }

    @Override
    public void stop() {
        if (ctx != null) ctx.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
