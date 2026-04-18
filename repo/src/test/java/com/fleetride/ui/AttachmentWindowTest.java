package com.fleetride.ui;

import com.fleetride.AppContext;
import com.fleetride.domain.Address;
import com.fleetride.domain.Attachment;
import com.fleetride.domain.Customer;
import com.fleetride.domain.Order;
import com.fleetride.domain.ServicePriority;
import com.fleetride.domain.TimeWindow;
import com.fleetride.domain.VehicleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentWindowTest extends FxTestBase {

    /** Minimal bytes the attachment-service's magic-byte validator accepts for PDF. */
    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n'};
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private Order seedOrder(AppContext ctx) {
        Customer c = ctx.securedCustomerService.create("With Files", "555-0600", null);
        Address p = new Address("1 Files St", "City", null, null, 1);
        Address d = new Address("2 Files St", "City", null, null, 1);
        TimeWindow w = new TimeWindow(LocalDateTime.now().plusMinutes(5),
                LocalDateTime.now().plusMinutes(35));
        return ctx.securedOrderService.create(c.id(), p, d, 1, w,
                VehicleType.STANDARD, ServicePriority.NORMAL, 3.0, 10, null);
    }

    @Test
    void uploadFileAndIssueShareToken(@TempDir Path dir) throws Exception {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);

            Path file = dir.resolve("receipt.pdf");
            Files.write(file, PDF_HEADER);

            Attachment[] captured = new Attachment[1];
            runFx(() -> {
                AttachmentWindow w = new AttachmentWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                w.uploadFileForOrder(o.id(), file);
                assertEquals(1, w.list.getItems().size(),
                        "upload should populate the attachment list");
                captured[0] = w.list.getItems().get(0);
                w.list.getSelectionModel().select(captured[0]);
                w.issueShareToken(captured[0].id(), 1);
                assertTrue(w.feedback.getText().contains("Issued token"));
            });

            assertEquals("receipt.pdf", captured[0].filename());
            assertEquals("application/pdf", captured[0].mimeType());
            var listed = ctx.securedAttachmentService.listForOrder(o.id());
            assertEquals(1, listed.size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void deleteSelectedAttachment(@TempDir Path dir) throws Exception {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);
            Path file = dir.resolve("photo.png");
            Files.write(file, PNG_HEADER);

            runFx(() -> {
                AttachmentWindow w = new AttachmentWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                w.uploadFileForOrder(o.id(), file);
                w.list.getSelectionModel().select(0);
                w.deleteBtn.fire();
                assertEquals(0, w.list.getItems().size());
            });

            assertEquals(0, ctx.securedAttachmentService.listForOrder(o.id()).size());
        } finally {
            ctx.close();
        }
    }

    @Test
    void noOrderMeansNoList(@TempDir Path dir) {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);

            runFx(() -> {
                AttachmentWindow w = new AttachmentWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                assertEquals(0, w.list.getItems().size());
                w.orderBox.setValue(null);
                assertEquals(0, w.list.getItems().size());
                w.shareBtn.fire();    // no selection
                w.deleteBtn.fire();   // no selection
            });
        } finally {
            ctx.close();
        }
    }

    @Test
    void mimeMismatchSurfacesError(@TempDir Path dir) throws Exception {
        AppContext ctx = TestAppContextBuilder.build(dir);
        try {
            ctx.auth.bootstrapAdministrator("admin", "pw");
            ctx.auth.login("admin", "pw");
            Order o = seedOrder(ctx);
            // A file that claims PDF (by extension → MimeGuesser) but has no %PDF
            // magic bytes — the service rejects it.
            Path file = dir.resolve("tricky.pdf");
            Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});

            ErrorAlerts.reset();
            runFx(() -> {
                AttachmentWindow w = new AttachmentWindow(ctx);
                w.build();
                w.orderBox.setValue(o);
                w.uploadFileForOrder(o.id(), file);
            });
            assertNotNull(ErrorAlerts.lastError());
            assertEquals(0, ctx.securedAttachmentService.listForOrder(o.id()).size());
        } finally {
            ctx.close();
        }
    }
}
