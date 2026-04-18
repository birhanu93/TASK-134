package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Customer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class CustomerManagementWindow implements Saveable {
    private final AppContext ctx;
    private final ObservableList<Customer> rows = FXCollections.observableArrayList();
    TextField name;
    TextField phone;
    TextField token;
    Label feedback;
    ListView<Customer> list;
    Button deleteBtn;

    public CustomerManagementWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        list = new ListView<>(rows);
        list.setId("customerList");
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setText(null); return; }
                String masked = ctx.securedCustomerService.maskedPaymentToken(c);
                setText(c.id() + " | " + c.name() + " | " + c.phone()
                        + (masked == null ? "" : " | card:" + masked));
            }
        });
        refresh();

        name = new TextField();
        name.setId("name");
        name.setPromptText("name");
        phone = new TextField();
        phone.setId("phone");
        phone.setPromptText("phone");
        token = new TextField();
        token.setId("token");
        token.setPromptText("payment token (optional)");
        Button saveBtn = new Button("Add customer (Ctrl+S)");
        saveBtn.setId("saveBtn");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());
        deleteBtn = new Button("Delete selected");
        deleteBtn.setId("deleteBtn");
        deleteBtn.setOnAction(e -> {
            Customer sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                ctx.securedCustomerService.delete(sel.id());
                refresh();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        feedback = new Label();
        feedback.setId("feedback");

        HBox form = new HBox(8, name, phone, token, saveBtn, deleteBtn);
        VBox root = new VBox(8, new Label("Customers"), list, form, feedback);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("Customer Management");
        stage.setScene(new Scene(root, 900, 560));
        return stage;
    }

    private void refresh() {
        rows.setAll(ctx.securedCustomerService.list());
    }

    @Override
    public void save() {
        try {
            ctx.securedCustomerService.create(name.getText(), phone.getText(),
                    token.getText() == null || token.getText().isBlank() ? null : token.getText());
            name.clear(); phone.clear(); token.clear();
            feedback.setText("Customer added.");
            refresh();
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }
}
