package com.fleetride.security;

import com.fleetride.domain.AuditEvent;
import com.fleetride.service.AuditService;

import java.util.List;

public final class SecuredAuditService {
    private final AuditService delegate;
    private final Authorizer authz;

    public SecuredAuditService(AuditService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public List<AuditEvent> all() {
        authz.require(Permission.AUDIT_READ);
        return delegate.all();
    }
}
