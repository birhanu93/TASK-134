package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Invoice;
import com.fleetride.domain.Order;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class InvoiceWindow {
    private final AppContext ctx;
    private final ObservableList<Invoice> rows = FXCollections.observableArrayList();

    ListView<Invoice> list;
    ChoiceBox<Order> orderBox;
    TextField notes;
    Button issueBtn, paidBtn, cancelBtn;

    public InvoiceWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        list = new ListView<>(rows);
        list.setId("invoiceList");
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Invoice i, boolean empty) {
                super.updateItem(i, empty);
                if (empty || i == null) { setText(null); return; }
                setText(i.id() + " | order " + i.orderId() + " | "
                        + i.amount() + " | " + i.status());
            }
        });
        refresh();

        orderBox = new ChoiceBox<>();
        orderBox.setId("orderBox");
        orderBox.getItems().setAll(ctx.securedOrderService.list());
        notes = new TextField();
        notes.setId("notes");
        notes.setPromptText("notes");
        issueBtn = new Button("Issue invoice");
        issueBtn.setId("issue");
        issueBtn.setOnAction(e -> {
            Order o = orderBox.getValue();
            if (o == null) return;
            try {
                ctx.securedInvoiceService.issueFor(o.id(), notes.getText());
                refresh();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });

        paidBtn = new Button("Mark paid");
        paidBtn.setId("paid");
        paidBtn.setOnAction(e -> actOnSelected(inv -> ctx.securedInvoiceService.markPaid(inv.id())));
        cancelBtn = new Button("Cancel invoice");
        cancelBtn.setId("cancel");
        cancelBtn.setOnAction(e -> actOnSelected(inv -> ctx.securedInvoiceService.cancel(inv.id())));

        HBox issueRow = new HBox(8, new Label("Order"), orderBox, notes, issueBtn);
        HBox actions = new HBox(8, paidBtn, cancelBtn);
        VBox root = new VBox(8, new Label("Invoices"), list, issueRow, actions);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("Invoices (Finance)");
        stage.setScene(new Scene(root, 800, 560));
        return stage;
    }

    void refresh() {
        rows.setAll(ctx.securedInvoiceService.listAll());
    }

    private void actOnSelected(java.util.function.Consumer<Invoice> action) {
        Invoice sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            action.accept(sel);
            refresh();
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }
}
