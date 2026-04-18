package com.fleetride.service;

import com.fleetride.domain.Attachment;
import com.fleetride.repository.InMemoryAttachmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AttachmentServiceTest {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private AttachmentService svc;
    private final LocalDateTime t = LocalDateTime.of(2026, 3, 27, 10, 0);

    private AttachmentService build(Path dir) {
        InMemoryAttachmentRepository repo = new InMemoryAttachmentRepository();
        AtomicInteger n = new AtomicInteger();
        IdGenerator ids = () -> "a-" + n.incrementAndGet();
        return new AttachmentService(repo, dir, ids, () -> t);
    }

    @Test
    void uploadPdf(@TempDir Path dir) {
        svc = build(dir);
        Attachment a = svc.upload("o1", "waiver.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        assertEquals("application/pdf", a.mimeType());
        assertEquals(PDF.length, a.sizeBytes());
        assertNotNull(a.sha256());
        assertTrue(Files.exists(Path.of(a.storedPath())));
    }

    @Test
    void uploadJpegAndPng(@TempDir Path dir) {
        svc = build(dir);
        assertNotNull(svc.upload("o", "a.jpg", "image/jpeg",
                new ByteArrayInputStream(JPEG), JPEG.length));
        assertNotNull(svc.upload("o", "a.png", "image/png",
                new ByteArrayInputStream(PNG), PNG.length));
    }

    @Test
    void disallowedMime(@TempDir Path dir) {
        svc = build(dir);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "f.exe", "application/x-dos",
                        new ByteArrayInputStream(new byte[1]), 1));
    }

    @Test
    void exceedsDeclaredSize(@TempDir Path dir) {
        svc = build(dir);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "f.pdf", "application/pdf",
                        new ByteArrayInputStream(new byte[0]), AttachmentService.MAX_BYTES + 1));
    }

    @Test
    void actualFileSizeTooLarge(@TempDir Path dir) throws IOException {
        svc = build(dir);
        byte[] big = new byte[(int) AttachmentService.MAX_BYTES + 1000];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "big.pdf", "application/pdf",
                        new ByteArrayInputStream(big), 10));
    }

    @Test
    void formatMismatch(@TempDir Path dir) {
        svc = build(dir);
        // pdf bytes but declared as jpeg
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "f.jpg", "image/jpeg",
                        new ByteArrayInputStream(PDF), PDF.length));
    }

    @Test
    void formatMismatchPng(@TempDir Path dir) {
        svc = build(dir);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "f.png", "image/png",
                        new ByteArrayInputStream(PDF), PDF.length));
    }

    @Test
    void formatMismatchPdf(@TempDir Path dir) {
        svc = build(dir);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "f.pdf", "application/pdf",
                        new ByteArrayInputStream(JPEG), JPEG.length));
    }

    @Test
    void tooShortToValidate(@TempDir Path dir) {
        svc = build(dir);
        assertThrows(AttachmentService.AttachmentException.class,
                () -> svc.upload("o", "x.pdf", "application/pdf",
                        new ByteArrayInputStream(new byte[]{'%', 'P'}), 2));
    }

    @Test
    void findAndList(@TempDir Path dir) {
        svc = build(dir);
        Attachment a = svc.upload("o", "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        assertEquals(a, svc.find(a.id()).orElseThrow());
        assertEquals(1, svc.listForOrder("o").size());
        assertTrue(svc.listForOrder("other").isEmpty());
    }

    @Test
    void deleteRemovesFileAndRecord(@TempDir Path dir) {
        svc = build(dir);
        Attachment a = svc.upload("o", "f.pdf", "application/pdf",
                new ByteArrayInputStream(PDF), PDF.length);
        svc.delete(a.id());
        assertFalse(svc.find(a.id()).isPresent());
        assertFalse(Files.exists(Path.of(a.storedPath())));
    }

    @Test
    void deleteNonexistentNoOp(@TempDir Path dir) {
        svc = build(dir);
        svc.delete("never");
    }

    @Test
    void storageDirFailureWrapsIOException(@TempDir Path dir) throws IOException {
        // storageDir points to an existing file; createDirectories on it fails
        Path file = dir.resolve("not-a-dir");
        Files.writeString(file, "blocker");
        svc = build(file);
        assertThrows(UncheckedIOException.class,
                () -> svc.upload("o", "f.pdf", "application/pdf",
                        new ByteArrayInputStream(PDF), PDF.length));
    }
}
