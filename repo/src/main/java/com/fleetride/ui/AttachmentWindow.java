package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Attachment;
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
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AttachmentWindow {
    private final AppContext ctx;
    private final ObservableList<Attachment> rows = FXCollections.observableArrayList();

    ChoiceBox<Order> orderBox;
    ListView<Attachment> list;
    Spinner<Integer> ttl;
    Button uploadBtn;
    Button shareBtn;
    Button deleteBtn;
    Label feedback;

    public AttachmentWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        orderBox = new ChoiceBox<>();
        orderBox.setId("orderBox");
        orderBox.setItems(FXCollections.observableArrayList(ctx.securedOrderService.list()));
        list = new ListView<>(rows);
        list.setId("attachmentList");
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(Attachment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); return; }
                setText(a.filename() + " | " + a.mimeType() + " | " + a.sizeBytes() + " B");
            }
        });
        orderBox.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) rows.setAll(ctx.securedAttachmentService.listForOrder(sel.id()));
            else rows.clear();
        });

        uploadBtn = new Button("Upload PDF/JPG/PNG…");
        uploadBtn.setId("upload");
        uploadBtn.setOnAction(e -> {
            Order sel = orderBox.getValue();
            if (sel == null) return;
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Allowed", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
            java.io.File file = chooser.showOpenDialog(null);
            if (file == null) return;
            uploadFileForOrder(sel.id(), file.toPath());
        });

        ttl = new Spinner<>(1, 168, 24);
        ttl.setId("ttl");
        shareBtn = new Button("Issue share link");
        shareBtn.setId("share");
        shareBtn.setOnAction(e -> {
            Attachment sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            issueShareToken(sel.id(), ttl.getValue());
        });
        deleteBtn = new Button("Delete");
        deleteBtn.setId("delete");
        deleteBtn.setOnAction(e -> {
            Attachment sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                ctx.securedAttachmentService.delete(sel.id());
                Order cur = orderBox.getValue();
                if (cur != null) rows.setAll(ctx.securedAttachmentService.listForOrder(cur.id()));
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        feedback = new Label();
        feedback.setId("feedback");

        HBox actions = new HBox(8, uploadBtn, new Label("TTL(h)"), ttl, shareBtn, deleteBtn);
        VBox root = new VBox(8, new Label("Order"), orderBox, list, actions, feedback);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("Attachments");
        stage.setScene(new Scene(root, 780, 520));
        return stage;
    }

    void uploadFileForOrder(String orderId, Path file) {
        try (InputStream in = new FileInputStream(file.toFile())) {
            String mime = MimeGuesser.guess(file.getFileName().toString());
            long size = Files.size(file);
            ctx.securedAttachmentService.upload(orderId, file.getFileName().toString(), mime, in, size);
            rows.setAll(ctx.securedAttachmentService.listForOrder(orderId));
            feedback.setText("Uploaded " + file.getFileName());
        } catch (Exception ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }

    void issueShareToken(String attachmentId, int ttlHours) {
        try {
            String tok = ctx.securedAttachmentService.issueShareToken(attachmentId, ttlHours);
            ClipboardHelper.copy(tok);
            ErrorAlerts.info("Token copied to clipboard (expires in " + ttlHours + "h).");
            feedback.setText("Issued token (ttl=" + ttlHours + "h).");
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }
}
