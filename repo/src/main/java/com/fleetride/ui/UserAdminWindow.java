package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Role;
import com.fleetride.domain.User;
import com.fleetride.security.Permission;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public final class UserAdminWindow implements Saveable {
    private final AppContext ctx;
    private final ObservableList<User> rows = FXCollections.observableArrayList();
    TextField username;
    PasswordField password;
    ChoiceBox<Role> roleBox;
    Label feedback;
    ListView<User> list;

    public UserAdminWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        ctx.authz.require(Permission.USER_MANAGE);
        list = new ListView<>(rows);
        list.setId("userList");
        refresh();

        username = new TextField();
        username.setId("newUsername");
        username.setPromptText("username");
        password = new PasswordField();
        password.setId("newPassword");
        password.setPromptText("password");
        roleBox = new ChoiceBox<>(FXCollections.observableArrayList(Role.values()));
        roleBox.setId("newRole");
        roleBox.setValue(Role.DISPATCHER);
        Button saveBtn = new Button("Add user (Ctrl+S)");
        saveBtn.setId("saveBtn");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());
        feedback = new Label();
        feedback.setId("feedback");

        HBox form = new HBox(8, new Label("User"), username, password, roleBox, saveBtn);
        VBox root = new VBox(8, new Label("Users & Roles"), list, form, feedback);
        root.setPadding(new Insets(12));
        Stage stage = new Stage();
        stage.setTitle("User Administration");
        stage.setScene(new Scene(root, 700, 500));
        return stage;
    }

    private void refresh() {
        ctx.authz.require(Permission.USER_MANAGE);
        rows.setAll(ctx.userRepo.findAll());
    }

    @Override
    public void save() {
        try {
            ctx.authz.require(Permission.USER_MANAGE);
            ctx.auth.register(username.getText(), password.getText(), roleBox.getValue());
            username.clear(); password.clear();
            feedback.setText("User registered.");
            refresh();
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }
}
