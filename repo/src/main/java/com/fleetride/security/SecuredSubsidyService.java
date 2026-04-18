package com.fleetride.security;

import com.fleetride.domain.Money;
import com.fleetride.domain.Subsidy;
import com.fleetride.service.SubsidyService;

import java.util.List;
import java.util.Optional;

public final class SecuredSubsidyService {
    private final SubsidyService delegate;
    private final Authorizer authz;

    public SecuredSubsidyService(SubsidyService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public Subsidy assign(String customerId, Money monthlyCap) {
        authz.require(Permission.SUBSIDY_MANAGE);
        return delegate.assign(customerId, monthlyCap);
    }

    public Optional<Subsidy> find(String customerId) {
        authz.require(Permission.SUBSIDY_READ);
        return delegate.find(customerId);
    }

    public List<Subsidy> list() {
        authz.require(Permission.SUBSIDY_READ);
        return delegate.list();
    }

    public void revoke(String customerId) {
        authz.require(Permission.SUBSIDY_MANAGE);
        delegate.revoke(customerId);
    }
}
