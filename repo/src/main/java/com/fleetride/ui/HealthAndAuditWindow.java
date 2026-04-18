package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.AuditEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;

public final class HealthAndAuditWindow {
    private final AppContext ctx;

    Label healthStatus;
    Button healthRefresh;
    ListView<String> jobList;
    Button jobRefresh;
    ListView<String> auditList;
    Button auditRefresh;

    public HealthAndAuditWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        TabPane tabs = new TabPane();
        tabs.getTabs().add(healthTab());
        tabs.getTabs().add(jobRunTab());
        tabs.getTabs().add(auditTab());
        Stage stage = new Stage();
        stage.setTitle("Health & Audit (Administrator)");
        stage.setScene(new Scene(tabs, 820, 560));
        return stage;
    }

    private Tab healthTab() {
        healthStatus = new Label();
        healthStatus.setId("healthStatus");
        healthRefresh = new Button("Refresh");
        healthRefresh.setId("healthRefresh");
        Runnable update = () -> {
            try {
                ctx.securedAuditService.all();
                StringBuilder sb = new StringBuilder();
                sb.append("Overall: ").append(ctx.healthService.overall()).append('\n');
                ctx.healthService.snapshot().forEach((k, v) ->
                        sb.append(k).append(": ").append(v.status()).append(" — ").append(v.message()).append('\n'));
                healthStatus.setText(sb.toString());
            } catch (RuntimeException ex) {
                healthStatus.setText("forbidden: " + ex.getMessage());
            }
        };
        update.run();
        healthRefresh.setOnAction(e -> update.run());
        VBox box = new VBox(8, healthStatus, healthRefresh);
        box.setPadding(new Insets(12));
        Tab tab = new Tab("Health", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab jobRunTab() {
        jobList = new ListView<>();
        jobList.setId("jobList");
        jobRefresh = new Button("Refresh");
        jobRefresh.setId("jobRefresh");
        Runnable update = () -> {
            try {
                jobList.setItems(FXCollections.observableArrayList(
                        ctx.securedSchedulerService.recentRuns(50).stream()
                                .map(r -> r.startedAt() + " " + r.jobName() + " " + r.status()
                                        + (r.processed() == null ? "" : " processed=" + r.processed())
                                        + (r.message() == null ? "" : " — " + r.message()))
                                .toList()));
            } catch (RuntimeException ex) {
                jobList.setItems(FXCollections.observableArrayList("forbidden: " + ex.getMessage()));
            }
        };
        update.run();
        jobRefresh.setOnAction(e -> update.run());
        VBox box = new VBox(8, jobList, jobRefresh);
        box.setPadding(new Insets(12));
        Tab tab = new Tab("Scheduled jobs", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab auditTab() {
        auditList = new ListView<>();
        auditList.setId("auditList");
        auditRefresh = new Button("Refresh");
        auditRefresh.setId("auditRefresh");
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        Runnable update = () -> {
            try {
                auditList.setItems(FXCollections.observableArrayList(
                        ctx.securedAuditService.all().stream()
                                .map((AuditEvent e) -> fmt.format(e.at()) + " "
                                        + e.actor() + " " + e.action()
                                        + (e.target() == null ? "" : " " + e.target())
                                        + (e.details() == null ? "" : " — " + e.details()))
                                .toList()));
            } catch (RuntimeException ex) {
                auditList.setItems(FXCollections.observableArrayList("forbidden: " + ex.getMessage()));
            }
        };
        update.run();
        auditRefresh.setOnAction(e -> update.run());
        VBox box = new VBox(8, auditList, auditRefresh);
        box.setPadding(new Insets(12));
        Tab tab = new Tab("Audit log", box);
        tab.setClosable(false);
        return tab;
    }
}
