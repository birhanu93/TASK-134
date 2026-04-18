package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Coupon;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Money;
import com.fleetride.domain.Subsidy;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;

public final class CouponSubsidyWindow {
    private final AppContext ctx;

    ListView<Coupon> couponList;
    TextField couponCode;
    ChoiceBox<String> couponType;
    TextField couponValue;
    TextField couponMinOrder;
    Button couponSaveBtn;
    Button couponDeleteBtn;
    Label couponFeedback;

    ListView<Subsidy> subsidyList;
    ChoiceBox<Customer> subsidyCustomer;
    TextField subsidyCap;
    Button subsidyAssignBtn;
    Button subsidyRevokeBtn;
    Label subsidyFeedback;

    public CouponSubsidyWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        TabPane tabs = new TabPane();
        tabs.getTabs().add(couponTab());
        tabs.getTabs().add(subsidyTab());
        Stage stage = new Stage();
        stage.setTitle("Coupons & Subsidies (Administrator)");
        stage.setScene(new Scene(tabs, 760, 520));
        return stage;
    }

    private Tab couponTab() {
        couponList = new ListView<>();
        couponList.setId("couponList");
        Runnable refresh = () -> couponList.setItems(FXCollections.observableArrayList(
                ctx.securedCouponService.list()));
        refresh.run();

        couponCode = new TextField();
        couponCode.setId("couponCode");
        couponCode.setPromptText("code");
        couponType = new ChoiceBox<>(FXCollections.observableArrayList("PERCENT", "FIXED"));
        couponType.setId("couponType");
        couponType.setValue("PERCENT");
        couponValue = new TextField();
        couponValue.setId("couponValue");
        couponValue.setPromptText("percent (0.1) or amount");
        couponMinOrder = new TextField("25.00");
        couponMinOrder.setId("couponMinOrder");
        couponSaveBtn = new Button("Save coupon");
        couponSaveBtn.setId("couponSave");
        couponDeleteBtn = new Button("Delete");
        couponDeleteBtn.setId("couponDelete");
        couponFeedback = new Label();
        couponFeedback.setId("couponFeedback");

        couponSaveBtn.setOnAction(e -> {
            try {
                Money min = Money.of(couponMinOrder.getText());
                if ("PERCENT".equals(couponType.getValue())) {
                    ctx.securedCouponService.createPercent(couponCode.getText(),
                            new BigDecimal(couponValue.getText()), min);
                } else {
                    ctx.securedCouponService.createFixed(couponCode.getText(),
                            Money.of(couponValue.getText()), min);
                }
                couponFeedback.setText("Saved.");
                refresh.run();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        couponDeleteBtn.setOnAction(e -> {
            Coupon sel = couponList.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                ctx.securedCouponService.delete(sel.code());
                refresh.run();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });

        HBox form = new HBox(8, couponCode, couponType, couponValue,
                new Label("Min"), couponMinOrder, couponSaveBtn, couponDeleteBtn);
        VBox box = new VBox(8, couponList, form, couponFeedback);
        box.setPadding(new Insets(12));
        Tab tab = new Tab("Coupons", box);
        tab.setClosable(false);
        return tab;
    }

    private Tab subsidyTab() {
        subsidyList = new ListView<>();
        subsidyList.setId("subsidyList");
        Runnable refresh = () -> subsidyList.setItems(FXCollections.observableArrayList(
                ctx.securedSubsidyService.list()));
        refresh.run();

        subsidyCustomer = new ChoiceBox<>();
        subsidyCustomer.setId("subsidyCustomer");
        subsidyCustomer.setItems(FXCollections.observableArrayList(ctx.securedCustomerService.list()));
        subsidyCap = new TextField("50.00");
        subsidyCap.setId("subsidyCap");
        subsidyAssignBtn = new Button("Assign subsidy");
        subsidyAssignBtn.setId("subsidyAssign");
        subsidyRevokeBtn = new Button("Revoke selected");
        subsidyRevokeBtn.setId("subsidyRevoke");
        subsidyFeedback = new Label();
        subsidyFeedback.setId("subsidyFeedback");
        subsidyAssignBtn.setOnAction(e -> {
            Customer c = subsidyCustomer.getValue();
            if (c == null) return;
            try {
                ctx.securedSubsidyService.assign(c.id(), Money.of(subsidyCap.getText()));
                subsidyFeedback.setText("Subsidy assigned.");
                refresh.run();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        subsidyRevokeBtn.setOnAction(e -> {
            Subsidy s = subsidyList.getSelectionModel().getSelectedItem();
            if (s == null) return;
            try {
                ctx.securedSubsidyService.revoke(s.customerId());
                refresh.run();
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        HBox form = new HBox(8, subsidyCustomer, new Label("Cap"), subsidyCap,
                subsidyAssignBtn, subsidyRevokeBtn);
        VBox box = new VBox(8, subsidyList, form, subsidyFeedback);
        box.setPadding(new Insets(12));
        Tab tab = new Tab("Subsidies", box);
        tab.setClosable(false);
        return tab;
    }
}
