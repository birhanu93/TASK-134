package com.fleetride.ui;

import com.fleetride.service.AuthService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

public final class TrayPresence {
    private final Stage stage;
    private final Supplier<String> statusSupplier;
    private final AuthService auth;
    private TrayIcon trayIcon;

    public TrayPresence(Stage stage, Supplier<String> statusSupplier, AuthService auth) {
        this.stage = stage;
        this.statusSupplier = statusSupplier;
        this.auth = auth;
    }

    public void install() {
        if (!SystemTray.isSupported()) return;
        Platform.setImplicitExit(false);
        Image image = Toolkit.getDefaultToolkit().createImage(new byte[0]);
        if (image == null) image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        trayIcon = new TrayIcon(image, "FleetRide Console");
        trayIcon.setImageAutoSize(true);

        PopupMenu popup = new PopupMenu();
        MenuItem open = new MenuItem("Open Console");
        open.addActionListener(e -> Platform.runLater(this::showStage));
        MenuItem status = new MenuItem("Status");
        status.addActionListener(e -> trayIcon.displayMessage("FleetRide",
                statusSupplier.get(), TrayIcon.MessageType.INFO));
        MenuItem lock = new MenuItem("Lock Session");
        lock.addActionListener(e -> Platform.runLater(this::lockSession));
        MenuItem unlock = new MenuItem("Unlock Session…");
        unlock.addActionListener(e -> Platform.runLater(this::unlockSession));
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> {
            SystemTray.getSystemTray().remove(trayIcon);
            Platform.exit();
            System.exit(0);
        });
        popup.add(open);
        popup.add(status);
        popup.addSeparator();
        popup.add(lock);
        popup.add(unlock);
        popup.addSeparator();
        popup.add(exit);
        trayIcon.setPopupMenu(popup);
        trayIcon.addActionListener(e -> Platform.runLater(this::showStage));
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException ignored) {
        }
    }

    private void showStage() {
        if (stage.isIconified()) stage.setIconified(false);
        stage.show();
        stage.toFront();
    }

    /**
     * Hides the main window so the app lives only in the system tray. If the tray isn't
     * available the stage simply iconifies so the caller can still reach it from the OS
     * taskbar.
     */
    public void minimizeToTray() {
        if (!SystemTray.isSupported() || trayIcon == null) {
            stage.setIconified(true);
            return;
        }
        stage.hide();
        trayIcon.displayMessage("FleetRide",
                "Minimized to tray. Double-click the tray icon to restore.",
                TrayIcon.MessageType.INFO);
    }

    public void updateTooltip(String text) {
        if (trayIcon != null) trayIcon.setToolTip(text);
    }

    public TrayIcon trayIcon() { return trayIcon; }

    private void lockSession() {
        try {
            auth.lock();
            if (trayIcon != null) {
                trayIcon.displayMessage("FleetRide", "Session locked.", TrayIcon.MessageType.INFO);
            }
        } catch (RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
        }
    }

    private void unlockSession() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setHeaderText("Enter password to unlock.");
        dlg.showAndWait().ifPresent(pwd -> {
            try {
                auth.unlock(pwd);
                if (trayIcon != null) {
                    trayIcon.displayMessage("FleetRide", "Session unlocked.", TrayIcon.MessageType.INFO);
                }
            } catch (RuntimeException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).showAndWait();
            }
        });
    }
}
