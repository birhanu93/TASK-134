package com.fleetride.security;

import com.fleetride.repository.UpdateHistoryRepository;
import com.fleetride.service.UpdateService;

import java.nio.file.Path;
import java.util.List;

public final class SecuredUpdateService {
    private final UpdateService delegate;
    private final Authorizer authz;
    private final Runnable afterActivation;

    public SecuredUpdateService(UpdateService delegate, Authorizer authz) {
        this(delegate, authz, () -> {});
    }

    public SecuredUpdateService(UpdateService delegate, Authorizer authz, Runnable afterActivation) {
        this.delegate = delegate;
        this.authz = authz;
        this.afterActivation = java.util.Objects.requireNonNull(afterActivation,
                "afterActivation — pass () -> {} if no hook is wanted");
    }

    public String currentVersion() {
        authz.require(Permission.UPDATE_APPLY);
        return delegate.currentVersion();
    }

    public void apply(Path updatePackage, String declaredVersion, Path signatureFile) {
        authz.require(Permission.UPDATE_APPLY);
        delegate.apply(updatePackage, declaredVersion, signatureFile);
        // Trigger downstream reload so consumers (ConfigService, etc.) pick up the new
        // payload immediately — otherwise the "active" version would only take effect
        // on the next process restart.
        afterActivation.run();
    }

    public void rollback() {
        authz.require(Permission.UPDATE_ROLLBACK);
        delegate.rollback();
        afterActivation.run();
    }

    public List<UpdateHistoryRepository.Record> history() {
        authz.require(Permission.UPDATE_APPLY);
        return delegate.history();
    }

    public java.util.Optional<Path> activePayloadRoot() {
        authz.require(Permission.UPDATE_APPLY);
        return delegate.activePayloadRoot();
    }
}
