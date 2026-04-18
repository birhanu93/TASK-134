package com.fleetride.repository;

import com.fleetride.domain.ShareLink;

import java.util.Optional;

public interface ShareLinkRepository {
    void save(ShareLink link);
    Optional<ShareLink> findByToken(String token);
    void delete(String token);
}
