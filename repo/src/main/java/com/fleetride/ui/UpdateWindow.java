package com.fleetride.ui;

import com.fleetride.AppContext;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;

public final class UpdateWindow {
    private final AppContext ctx;

    Label versionLabel;
    ListView<String> history;
    TextField declaredVersion;
    Button applyBtn;
    Button rollbackBtn;

    public UpdateWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        versionLabel = new Label();
        versionLabel.setId("versionLabel");
        history = new ListView<>();
        history.setId("history");
        Runnable refresh = this::refreshView;
        refresh.run();

        declaredVersion = new TextField();
        declaredVersion.setId("declaredVersion");
        declaredVersion.setPromptText("new version (e.g. 1.2.0)");
        applyBtn = new Button("Choose signed package and apply…");
        applyBtn.setId("applyBtn");
        applyBtn.setOnAction(e -> {
            try {
                FileChooser pkgChooser = new FileChooser();
                pkgChooser.setTitle("Signed update package");
                java.io.File pkg = pkgChooser.showOpenDialog(null);
                if (pkg == null) return;
                FileChooser sigChooser = new FileChooser();
                sigChooser.setTitle("Detached signature file");
                java.io.File sig = sigChooser.showOpenDialog(null);
                if (sig == null) return;
                applyPackage(pkg.toPath(), declaredVersion.getText(), sig.toPath());
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        rollbackBtn = new Button("Rollback");
        rollbackBtn.setId("rollbackBtn");
        rollbackBtn.setOnAction(e -> {
            try {
                ctx.securedUpdateService.rollback();
                refreshView();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });

        HBox row = new HBox(8, declaredVersion, applyBtn, rollbackBtn);
        VBox root = new VBox(8, versionLabel, history, row);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("Update (Administrator)");
        stage.setScene(new Scene(root, 820, 520));
        return stage;
    }

    void applyPackage(Path pkgPath, String version, Path sigPath) {
        try {
            ctx.securedUpdateService.apply(pkgPath, version, sigPath);
            refreshView();
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }

    void refreshView() {
        try {
            versionLabel.setText("Current version: " + ctx.securedUpdateService.currentVersion());
            history.getItems().setAll(
                    ctx.securedUpdateService.history().stream()
                            .map(r -> r.installedAt() + " v" + r.version() + " " + r.packagePath())
                            .toList());
        } catch (RuntimeException ex) {
            versionLabel.setText("forbidden: " + ex.getMessage());
            history.getItems().clear();
        }
    }
}
