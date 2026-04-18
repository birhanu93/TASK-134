package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigCenterWindowTest extends FxTestBase {

    @Test
    void savePersistsAllPricingFields(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                ConfigCenterWindow w = new ConfigCenterWindow(ctx);
                w.build();
                w.baseFare.setText("4.00");
                w.perMile.setText("2.00");
                w.perMinute.setText("0.40");
                w.priority.setText("1.30");
                w.lateCancel.setText("5.50");
                w.perFloor.setText("1.25");
                w.freeFloor.setText("4");
                w.deposit.setText("0.25");
                w.subsidyCap.setText("60.00");
                w.maxCoupon.setText("0.15");
                w.couponMinOrder.setText("30.00");
                w.autoCancel.setText("20");
                w.lateCancelWindow.setText("12");
                w.disputeWindow.setText("10");
                w.overduePerSweep.setText("2.50");
                w.save();
                assertEquals("Pricing & policy saved.", w.feedback.getText());
            });

            var p = ctx.securedConfigService.pricing();
            assertEquals(Money.of("4.00"), p.baseFare());
            assertEquals(Money.of("2.00"), p.perMile());
            assertEquals(Money.of("0.40"), p.perMinute());
            assertEquals(new BigDecimal("1.30"), p.priorityMultiplier());
            assertEquals(Money.of("5.50"), p.lateCancelFee());
            assertEquals(Money.of("1.25"), p.perFloorSurcharge());
            assertEquals(4, p.freeFloorThreshold());
            assertEquals(new BigDecimal("0.25"), p.depositPercent());
            assertEquals(Money.of("60.00"), p.monthlySubsidyCap());
            assertEquals(new BigDecimal("0.15"), p.maxCouponPercent());
            assertEquals(Money.of("30.00"), p.couponMinimumOrder());
            assertEquals(20, p.autoCancelMinutes());
            assertEquals(12, p.lateCancelWindowMinutes());
            assertEquals(10, p.disputeWindowDays());
            assertEquals(Money.of("2.50"), p.overdueFeePerSweep());
        } finally {
            ctx.close();
        }
    }

    @Test
    void malformedMoneySurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                ConfigCenterWindow w = new ConfigCenterWindow(ctx);
                w.build();
                w.baseFare.setText("not-a-number");
                w.save();
            });
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void dictionaryAndTemplateTabsPersist(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                ConfigCenterWindow w = new ConfigCenterWindow(ctx);
                w.build();
                w.dictKey.setText("cities.home");
                w.dictValue.setText("Springfield");
                w.dictSaveBtn.fire();
                assertTrue(w.dictionaryEntries.getItems().stream()
                        .anyMatch(s -> s.startsWith("cities.home")));

                w.tmplKey.setText("welcome");
                w.tmplValue.setText("Hi {name}");
                w.tmplSaveBtn.fire();
                assertTrue(w.templateEntries.getItems().stream()
                        .anyMatch(s -> s.startsWith("welcome")));
            });

            assertEquals("Springfield", ctx.securedConfigService.allDictionaries().get("cities.home"));
            assertEquals("Hi {name}", ctx.securedConfigService.allTemplates().get("welcome"));
        } finally {
            ctx.close();
        }
    }
}
