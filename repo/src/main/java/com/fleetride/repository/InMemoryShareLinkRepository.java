package com.fleetride.repository;

import com.fleetride.domain.ShareLink;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryShareLinkRepository implements ShareLinkRepository {
    private final Map<String, ShareLink> store = new ConcurrentHashMap<>();

    @Override
    public void save(ShareLink link) { store.put(link.token(), link); }

    @Override
    public Optional<ShareLink> findByToken(String token) { return Optional.ofNullable(store.get(token)); }

    @Override
    public void delete(String token) { store.remove(token); }
}
