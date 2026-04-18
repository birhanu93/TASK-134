package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CouponSubsidyWindowTest extends FxTestBase {

    @Test
    void createPercentAndFixedCoupons(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                CouponSubsidyWindow w = new CouponSubsidyWindow(ctx);
                w.build();
                w.couponCode.setText("WELCOME10");
                w.couponType.setValue("PERCENT");
                w.couponValue.setText("0.10");
                w.couponMinOrder.setText("25.00");
                w.couponSaveBtn.fire();

                w.couponCode.setText("FIVEOFF");
                w.couponType.setValue("FIXED");
                w.couponValue.setText("5.00");
                w.couponMinOrder.setText("20.00");
                w.couponSaveBtn.fire();

                assertEquals(2, w.couponList.getItems().size());
            });

            assertEquals(2, ctx.securedCouponService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void deleteSelectedCoupon(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            ctx.securedCouponService.createPercent("GONE", new java.math.BigDecimal("0.05"),
                    com.fleetride.domain.Money.of("10.00"));

            runFx(() -> {
                CouponSubsidyWindow w = new CouponSubsidyWindow(ctx);
                w.build();
                w.couponList.getSelectionModel().select(0);
                w.couponDeleteBtn.fire();
                assertEquals(0, w.couponList.getItems().size());
            });
            assertEquals(0, ctx.securedCouponService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void assignAndRevokeSubsidy(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Customer c = ctx.securedCustomerService.create("Subsidized", "555-0500", null);

            runFx(() -> {
                CouponSubsidyWindow w = new CouponSubsidyWindow(ctx);
                w.build();
                w.subsidyCustomer.setValue(c);
                w.subsidyCap.setText("75.00");
                w.subsidyAssignBtn.fire();
                assertEquals("Subsidy assigned.", w.subsidyFeedback.getText());
                assertEquals(1, w.subsidyList.getItems().size());

                w.subsidyList.getSelectionModel().select(0);
                w.subsidyRevokeBtn.fire();
                assertEquals(0, w.subsidyList.getItems().size());
            });
            assertEquals(0, ctx.securedSubsidyService.list().size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void invalidCouponValueSurfacesError(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            ErrorAlerts.reset();
            runFx(() -> {
                CouponSubsidyWindow w = new CouponSubsidyWindow(ctx);
                w.build();
                w.couponCode.setText("BAD");
                w.couponType.setValue("PERCENT");
                w.couponValue.setText("not-a-number");
                w.couponMinOrder.setText("10.00");
                w.couponSaveBtn.fire();
            });
            assertNotNull(ErrorAlerts.lastError());
        } finally {
            ctx.close();
        }
    }

    @Test
    void buttonsNoopWhenNothingSelected(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");

            runFx(() -> {
                CouponSubsidyWindow w = new CouponSubsidyWindow(ctx);
                w.build();
                w.couponDeleteBtn.fire();   // no selection
                w.subsidyAssignBtn.fire();  // no customer picked
                w.subsidyRevokeBtn.fire();  // no selection
            });
            assertEquals(0, ctx.securedCouponService.list().size());
            assertEquals(0, ctx.securedSubsidyService.list().size());
        } finally {
            ctx.close();
        }
    }
}
