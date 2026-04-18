package com.fleetride.service;

import com.fleetride.domain.Attachment;
import com.fleetride.repository.AttachmentRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AttachmentService {
    public static final class AttachmentException extends RuntimeException {
        public AttachmentException(String msg) { super(msg); }
    }

    public static final long MAX_BYTES = 20L * 1024 * 1024;
    public static final Set<String> ALLOWED_MIME = Set.of("application/pdf", "image/jpeg", "image/png");

    private final AttachmentRepository repo;
    private final Path storageDir;
    private final IdGenerator ids;
    private final Clock clock;

    public AttachmentService(AttachmentRepository repo, Path storageDir, IdGenerator ids, Clock clock) {
        this.repo = repo;
        this.storageDir = storageDir;
        this.ids = ids;
        this.clock = clock;
    }

    public Attachment upload(String orderId, String filename, String mimeType, InputStream data, long sizeBytes) {
        if (!ALLOWED_MIME.contains(mimeType)) {
            throw new AttachmentException("disallowed mime: " + mimeType);
        }
        if (sizeBytes > MAX_BYTES) {
            throw new AttachmentException("file exceeds 20MB");
        }
        String id = ids.next();
        Path target = storageDir.resolve(id + "-" + sanitize(filename));
        IOUtil.uncheckedRun(() -> Files.createDirectories(storageDir));
        IOUtil.uncheckedRun(() -> Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING));
        long actual = IOUtil.unchecked(() -> Files.size(target));
        if (actual > MAX_BYTES) {
            IOUtil.uncheckedRun(() -> Files.deleteIfExists(target));
            throw new AttachmentException("file exceeds 20MB");
        }
        if (!IOUtil.unchecked(() -> validateMagicBytes(target, mimeType))) {
            IOUtil.uncheckedRun(() -> Files.deleteIfExists(target));
            throw new AttachmentException("format does not match mime");
        }
        String sha = IOUtil.unchecked(() -> sha256(target));
        Attachment a = new Attachment(id, orderId, filename, target.toString(),
                mimeType, actual, sha, clock.now());
        repo.save(a);
        return a;
    }

    public Optional<Attachment> find(String id) { return repo.findById(id); }
    public List<Attachment> listForOrder(String orderId) { return repo.findByOrder(orderId); }

    public void delete(String id) {
        repo.findById(id).ifPresent(a -> {
            IOUtil.uncheckedRun(() -> Files.deleteIfExists(Path.of(a.storedPath())));
            repo.delete(id);
        });
    }

    private String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(Path p) throws java.io.IOException {
        MessageDigest md = EncryptionService.unchecked(() -> MessageDigest.getInstance("SHA-256"));
        byte[] data = Files.readAllBytes(p);
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    private boolean validateMagicBytes(Path p, String mime) throws java.io.IOException {
        byte[] head = new byte[8];
        int n;
        try (InputStream in = Files.newInputStream(p)) {
            n = in.read(head);
        }
        byte[] expected = expectedMagic(mime);
        if (n < expected.length) return false;
        return java.util.Arrays.equals(head, 0, expected.length, expected, 0, expected.length);
    }

    private byte[] expectedMagic(String mime) {
        if ("application/pdf".equals(mime)) return PDF_MAGIC;
        if ("image/jpeg".equals(mime)) return JPEG_MAGIC;
        return PNG_MAGIC;
    }
}
