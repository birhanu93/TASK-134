package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Order;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class OrderTimelineWindow {
    private final AppContext ctx;
    private final ObservableList<Order> rows = FXCollections.observableArrayList();
    private String initialQuery;

    ListView<Order> view;
    TextField searchField;
    Label matchCount;
    Label status;
    ContextMenu contextMenu;
    MenuItem acceptItem, startItem, completeItem, cancelItem, disputeItem;
    MenuItem copyIdItem, copyPickupItem, copyDropoffItem;

    public OrderTimelineWindow(AppContext ctx) { this.ctx = ctx; }

    public OrderTimelineWindow withInitialQuery(String query) {
        this.initialQuery = query;
        return this;
    }

    public Stage build() {
        view = new ListView<>(rows);
        view.setId("orderList");
        view.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Order item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                StringBuilder line = new StringBuilder();
                line.append(item.id()).append(" | ").append(item.state()).append(" | ")
                        .append(item.pickup().format());
                if (item.pickupFloorNotes() != null) {
                    line.append(" [pickup: ").append(item.pickupFloorNotes()).append(']');
                }
                line.append(" -> ").append(item.dropoff().format());
                if (item.dropoffFloorNotes() != null) {
                    line.append(" [drop: ").append(item.dropoffFloorNotes()).append(']');
                }
                setText(line.toString());
            }
        });
        contextMenu = buildContextMenu(view);
        view.setContextMenu(contextMenu);

        searchField = new TextField();
        searchField.setId("searchField");
        searchField.setPromptText("Search by id, customer id, address, floor notes, state…");
        searchField.setPrefColumnCount(40);
        matchCount = new Label();
        matchCount.setId("matchCount");
        if (initialQuery != null) searchField.setText(initialQuery);
        searchField.textProperty().addListener((obs, old, text) -> applyFilter(text));

        status = new Label();
        status.setId("status");
        refresh();

        HBox searchRow = new HBox(8, new Label("Search"), searchField, matchCount);
        VBox root = new VBox(8, new Label("Orders"), searchRow, view, status);
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        stage.setTitle("Order Timeline");
        stage.setScene(new Scene(root, 960, 640));
        stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                searchField::requestFocus);
        return stage;
    }

    void refresh() {
        applyFilter(searchField == null ? initialQuery : searchField.getText());
        StatusIndicators ind = new StatusIndicators(ctx.securedOrderService, ctx.securedPaymentService);
        if (status != null) status.setText(ind.snapshot().format());
    }

    void applyFilter(String query) {
        var all = ctx.securedOrderService.list();
        rows.setAll(OrderSearchFilter.apply(all, query));
        if (matchCount != null) {
            matchCount.setText(rows.size() + " match" + (rows.size() == 1 ? "" : "es"));
        }
    }

    private ContextMenu buildContextMenu(ListView<Order> view) {
        ContextMenu menu = new ContextMenu();
        acceptItem = menuItem("Accept",
                o -> ctx.securedOrderService.accept(o.id()), view);
        startItem = menuItem("Start Trip",
                o -> ctx.securedOrderService.start(o.id()), view);
        completeItem = menuItem("Complete",
                o -> ctx.securedOrderService.complete(o.id()), view);
        cancelItem = menuItem("Cancel",
                o -> ctx.securedOrderService.cancel(o.id()), view);
        disputeItem = menuItem("Open Dispute",
                o -> ctx.securedOrderService.openDispute(o.id(), "UI-initiated"), view);
        copyIdItem = new MenuItem("Copy Order ID");
        copyIdItem.setOnAction(e -> {
            Order sel = view.getSelectionModel().getSelectedItem();
            if (sel != null) ClipboardHelper.copy(sel.id());
        });
        copyPickupItem = new MenuItem("Copy Pickup Address");
        copyPickupItem.setOnAction(e -> {
            Order sel = view.getSelectionModel().getSelectedItem();
            if (sel != null) ClipboardHelper.copy(sel.pickup().format());
        });
        copyDropoffItem = new MenuItem("Copy Dropoff Address");
        copyDropoffItem.setOnAction(e -> {
            Order sel = view.getSelectionModel().getSelectedItem();
            if (sel != null) ClipboardHelper.copy(sel.dropoff().format());
        });
        menu.getItems().addAll(acceptItem, startItem, completeItem, cancelItem, disputeItem,
                copyIdItem, copyPickupItem, copyDropoffItem);
        return menu;
    }

    private MenuItem menuItem(String label, java.util.function.Consumer<Order> action, ListView<Order> view) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            Order sel = view.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                action.accept(sel);
                rows.setAll(ctx.securedOrderService.list());
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        return item;
    }
}
