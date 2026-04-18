package com.fleetride.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Attachment {
    private final String id;
    private final String orderId;
    private final String filename;
    private final String storedPath;
    private final String mimeType;
    private final long sizeBytes;
    private final String sha256;
    private final LocalDateTime uploadedAt;

    public Attachment(String id, String orderId, String filename, String storedPath,
                      String mimeType, long sizeBytes, String sha256, LocalDateTime uploadedAt) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId required");
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename required");
        if (storedPath == null) throw new IllegalArgumentException("storedPath required");
        if (mimeType == null) throw new IllegalArgumentException("mimeType required");
        if (sha256 == null) throw new IllegalArgumentException("sha256 required");
        if (uploadedAt == null) throw new IllegalArgumentException("uploadedAt required");
        this.id = id;
        this.orderId = orderId;
        this.filename = filename;
        this.storedPath = storedPath;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedAt = uploadedAt;
    }

    public String id() { return id; }
    public String orderId() { return orderId; }
    public String filename() { return filename; }
    public String storedPath() { return storedPath; }
    public String mimeType() { return mimeType; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public LocalDateTime uploadedAt() { return uploadedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attachment a)) return false;
        return Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
