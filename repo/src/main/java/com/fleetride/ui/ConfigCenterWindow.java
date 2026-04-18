package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Money;
import com.fleetride.domain.PricingConfig;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;

public final class ConfigCenterWindow implements Saveable {
    private final AppContext ctx;

    TextField baseFare;
    TextField perMile;
    TextField perMinute;
    TextField priority;
    TextField lateCancel;
    TextField perFloor;
    TextField freeFloor;
    TextField deposit;
    TextField subsidyCap;
    TextField maxCoupon;
    TextField couponMinOrder;
    TextField autoCancel;
    TextField lateCancelWindow;
    TextField disputeWindow;
    TextField overduePerSweep;
    Label feedback;
    TextField dictKey;
    TextField dictValue;
    TextField tmplKey;
    TextField tmplValue;
    ListView<String> dictionaryEntries;
    ListView<String> templateEntries;
    Button dictSaveBtn;
    Button tmplSaveBtn;

    public ConfigCenterWindow(AppContext ctx) { this.ctx = ctx; }

    public Stage build() {
        TabPane tabs = new TabPane();
        tabs.getTabs().add(pricingTab());
        tabs.getTabs().add(dictionaryTab());
        tabs.getTabs().add(templateTab());
        Stage stage = new Stage();
        stage.setTitle("Configuration Center (Administrator)");
        stage.setScene(new Scene(tabs, 860, 640));
        return stage;
    }

    private Tab pricingTab() {
        PricingConfig p = ctx.securedConfigService.pricing();
        baseFare = tf("baseFare", p.baseFare().amount().toPlainString());
        perMile = tf("perMile", p.perMile().amount().toPlainString());
        perMinute = tf("perMinute", p.perMinute().amount().toPlainString());
        priority = tf("priority", p.priorityMultiplier().toPlainString());
        lateCancel = tf("lateCancel", p.lateCancelFee().amount().toPlainString());
        perFloor = tf("perFloor", p.perFloorSurcharge().amount().toPlainString());
        freeFloor = tf("freeFloor", Integer.toString(p.freeFloorThreshold()));
        deposit = tf("deposit", p.depositPercent().toPlainString());
        subsidyCap = tf("subsidyCap", p.monthlySubsidyCap().amount().toPlainString());
        maxCoupon = tf("maxCoupon", p.maxCouponPercent().toPlainString());
        couponMinOrder = tf("couponMinOrder", p.couponMinimumOrder().amount().toPlainString());
        autoCancel = tf("autoCancel", Integer.toString(p.autoCancelMinutes()));
        lateCancelWindow = tf("lateCancelWindow", Integer.toString(p.lateCancelWindowMinutes()));
        disputeWindow = tf("disputeWindow", Integer.toString(p.disputeWindowDays()));
        overduePerSweep = tf("overduePerSweep", p.overdueFeePerSweep().amount().toPlainString());

        Button saveBtn = new Button("Save pricing (Ctrl+S)");
        saveBtn.setId("savePricing");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());
        feedback = new Label();
        feedback.setId("feedback");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(6); grid.setPadding(new Insets(12));
        int r = 0;
        r = row(grid, r, "Base fare", baseFare);
        r = row(grid, r, "Per mile", perMile);
        r = row(grid, r, "Per minute", perMinute);
        r = row(grid, r, "Priority multiplier", priority);
        r = row(grid, r, "Late cancel fee", lateCancel);
        r = row(grid, r, "Per-floor surcharge", perFloor);
        r = row(grid, r, "Free floor threshold", freeFloor);
        r = row(grid, r, "Deposit percent (0.2 = 20%)", deposit);
        r = row(grid, r, "Monthly subsidy cap", subsidyCap);
        r = row(grid, r, "Max coupon percent (0.2 = 20%)", maxCoupon);
        r = row(grid, r, "Coupon minimum order", couponMinOrder);
        r = row(grid, r, "Auto-cancel window (min)", autoCancel);
        r = row(grid, r, "Late-cancel window (min)", lateCancelWindow);
        r = row(grid, r, "Dispute window (days)", disputeWindow);
        r = row(grid, r, "Overdue fee per sweep", overduePerSweep);
        grid.add(new HBox(8, saveBtn, feedback), 0, r, 2, 1);

        Tab tab = new Tab("Pricing & policy", grid);
        tab.setClosable(false);
        return tab;
    }

    private static TextField tf(String id, String value) {
        TextField t = new TextField(value);
        t.setId(id);
        return t;
    }

    private int row(GridPane g, int r, String label, TextField f) {
        g.add(new Label(label), 0, r);
        g.add(f, 1, r);
        return r + 1;
    }

    @Override
    public void save() {
        try {
            ctx.securedConfigService.setBaseFare(Money.of(baseFare.getText()));
            ctx.securedConfigService.setPerMile(Money.of(perMile.getText()));
            ctx.securedConfigService.setPerMinute(Money.of(perMinute.getText()));
            ctx.securedConfigService.setPriorityMultiplier(new BigDecimal(priority.getText()));
            ctx.securedConfigService.setLateCancelFee(Money.of(lateCancel.getText()));
            ctx.securedConfigService.setPerFloorSurcharge(Money.of(perFloor.getText()));
            ctx.securedConfigService.setFreeFloorThreshold(Integer.parseInt(freeFloor.getText()));
            ctx.securedConfigService.setDepositPercent(new BigDecimal(deposit.getText()));
            ctx.securedConfigService.setMonthlySubsidyCap(Money.of(subsidyCap.getText()));
            ctx.securedConfigService.setMaxCouponPercent(new BigDecimal(maxCoupon.getText()));
            ctx.securedConfigService.setCouponMinimumOrder(Money.of(couponMinOrder.getText()));
            ctx.securedConfigService.setAutoCancelMinutes(Integer.parseInt(autoCancel.getText()));
            ctx.securedConfigService.setLateCancelWindowMinutes(Integer.parseInt(lateCancelWindow.getText()));
            ctx.securedConfigService.setDisputeWindowDays(Integer.parseInt(disputeWindow.getText()));
            ctx.securedConfigService.setOverdueFeePerSweep(Money.of(overduePerSweep.getText()));
            feedback.setText("Pricing & policy saved.");
        } catch (RuntimeException ex) {
            ErrorAlerts.error(ex.getMessage());
        }
    }

    private Tab dictionaryTab() {
        ListView<String> entries = new ListView<>();
        entries.setId("dictionaryEntries");
        dictionaryEntries = entries;
        Runnable refresh = () -> entries.getItems().setAll(
                ctx.securedConfigService.allDictionaries().entrySet().stream()
                        .map(e -> e.getKey() + " = " + e.getValue())
                        .sorted().toList());
        refresh.run();
        dictKey = tf("dictKey", "");
        dictKey.setPromptText("key");
        dictValue = tf("dictValue", "");
        dictValue.setPromptText("value");
        dictSaveBtn = new Button("Save entry");
        dictSaveBtn.setId("dictSave");
        Label feed = new Label();
        feed.setId("dictFeedback");
        dictSaveBtn.setOnAction(e -> {
            try {
                ctx.securedConfigService.setDictionary(dictKey.getText(), dictValue.getText());
                refresh.run();
                feed.setText("Saved " + dictKey.getText());
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        HBox form = new HBox(8, new Label("Key"), dictKey, new Label("Value"), dictValue, dictSaveBtn);
        VBox root = new VBox(8, entries, form, feed);
        root.setPadding(new Insets(12));
        Tab tab = new Tab("Dictionaries", root);
        tab.setClosable(false);
        return tab;
    }

    private Tab templateTab() {
        ListView<String> entries = new ListView<>();
        entries.setId("templateEntries");
        templateEntries = entries;
        Runnable refresh = () -> entries.getItems().setAll(
                ctx.securedConfigService.allTemplates().entrySet().stream()
                        .map(e -> e.getKey() + " = " + e.getValue())
                        .sorted().toList());
        refresh.run();
        tmplKey = tf("tmplKey", "");
        tmplKey.setPromptText("key");
        tmplValue = tf("tmplValue", "");
        tmplValue.setPromptText("value");
        tmplSaveBtn = new Button("Save entry");
        tmplSaveBtn.setId("tmplSave");
        Label feed = new Label();
        feed.setId("tmplFeedback");
        tmplSaveBtn.setOnAction(e -> {
            try {
                ctx.securedConfigService.setTemplate(tmplKey.getText(), tmplValue.getText());
                refresh.run();
                feed.setText("Saved " + tmplKey.getText());
            } catch (RuntimeException ex) {
                ErrorAlerts.error(ex.getMessage());
            }
        });
        HBox form = new HBox(8, new Label("Key"), tmplKey, new Label("Value"), tmplValue, tmplSaveBtn);
        VBox root = new VBox(8, entries, form, feed);
        root.setPadding(new Insets(12));
        Tab tab = new Tab("Templates", root);
        tab.setClosable(false);
        return tab;
    }
}
