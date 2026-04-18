package com.fleetride.repository;

import com.fleetride.domain.Attachment;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {
    void save(Attachment a);
    Optional<Attachment> findById(String id);
    List<Attachment> findByOrder(String orderId);
    void delete(String id);
}
