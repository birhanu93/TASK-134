package com.fleetride.security;

import com.fleetride.service.ReconciliationService;

import java.io.IOException;
import java.nio.file.Path;

public final class SecuredReconciliationService {
    private final ReconciliationService delegate;
    private final Authorizer authz;

    public SecuredReconciliationService(ReconciliationService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public Path exportCsv(Path file) throws IOException {
        authz.require(Permission.RECONCILIATION_EXPORT);
        return delegate.exportCsv(file);
    }

    public Path exportPaymentsCsv(Path file) throws IOException {
        authz.require(Permission.RECONCILIATION_EXPORT);
        return delegate.exportPaymentsCsv(file);
    }
}
