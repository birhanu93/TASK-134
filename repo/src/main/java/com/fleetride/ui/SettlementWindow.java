package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Money;
import com.fleetride.domain.Order;
import com.fleetride.domain.Payment;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Path;

public final class SettlementWindow {
    private final AppContext ctx;
    private final ObservableList<Order> orders = FXCollections.observableArrayList();

    ListView<Order> view;
    Label balance;
    ChoiceBox<Payment.Tender> tender;
    TextField amountField;
    Button depositBtn, finalBtn, refundBtn, exportBtn;

    public SettlementWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        view = new ListView<>(orders);
        view.setId("orderList");
        refresh();

        balance = new Label("Balance: —");
        balance.setId("balance");
        view.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null) { balance.setText("Balance: —"); return; }
            balance.setText("Balance: " + ctx.securedPaymentService.balanceFor(sel));
        });

        tender = new ChoiceBox<>(FXCollections.observableArrayList(Payment.Tender.values()));
        tender.setId("tender");
        tender.setValue(Payment.Tender.CASH);
        amountField = new TextField("0.00");
        amountField.setId("amount");

        depositBtn = new Button("Record Deposit");
        depositBtn.setId("deposit");
        depositBtn.setOnAction(e -> act(Payment.Kind.DEPOSIT));
        finalBtn = new Button("Record Final");
        finalBtn.setId("final");
        finalBtn.setOnAction(e -> act(Payment.Kind.FINAL));
        refundBtn = new Button("Refund");
        refundBtn.setId("refund");
        refundBtn.setOnAction(e -> act(Payment.Kind.REFUND));

        exportBtn = new Button("Export CSV (Ctrl+Shift+E)");
        exportBtn.setId("export");
        exportBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName("recon.csv");
            java.io.File f = chooser.showSaveDialog(null);
            if (f == null) return;
            try {
                Path out = ctx.securedReconciliationService.exportCsv(f.toPath());
                ErrorAlerts.info("Exported " + out);
            } catch (Exception ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });

        HBox buttons = new HBox(8, depositBtn, finalBtn, refundBtn, exportBtn);
        HBox paymentRow = new HBox(8, new Label("Tender"), tender, new Label("Amount"), amountField);
        VBox root = new VBox(8, new Label("Settlement"), view, balance, paymentRow, buttons);
        root.setPadding(new Insets(12));

        Stage stage = new Stage();
        stage.setTitle("Settlement & Reconciliation");
        stage.setScene(new Scene(root, 800, 600));
        return stage;
    }

    public void refresh() {
        orders.setAll(ctx.securedOrderService.list());
    }

    void act(Payment.Kind kind) {
        Order sel = view.getSelectionModel().getSelectedItem();
        if (sel == null) { ErrorAlerts.error("Pick an order."); return; }
        try {
            Money amt = Money.of(amountField.getText());
            switch (kind) {
                case DEPOSIT -> ctx.securedPaymentService.recordDeposit(sel.id(), tender.getValue(), amt);
                case FINAL -> ctx.securedPaymentService.recordFinal(sel.id(), tender.getValue(), amt);
                case REFUND -> ctx.securedPaymentService.refund(sel.id(), tender.getValue(), amt, "UI");
                case CANCEL_FEE -> ctx.securedPaymentService.recordCancelFee(sel.id(), tender.getValue(), amt);
            }
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }
}
