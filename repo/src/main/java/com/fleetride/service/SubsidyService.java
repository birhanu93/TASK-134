package com.fleetride.service;

import com.fleetride.domain.Money;
import com.fleetride.domain.Subsidy;
import com.fleetride.repository.SubsidyRepository;

import java.util.List;
import java.util.Optional;

public final class SubsidyService {
    public static final class SubsidyException extends RuntimeException {
        public SubsidyException(String msg) { super(msg); }
    }

    private final SubsidyRepository repo;
    private final AuditService audit;

    public SubsidyService(SubsidyRepository repo, AuditService audit) {
        if (repo == null) throw new IllegalArgumentException("repo required");
        if (audit == null) throw new IllegalArgumentException("audit required");
        this.repo = repo;
        this.audit = audit;
    }

    public Subsidy assign(String customerId, Money monthlyCap) {
        Subsidy s = new Subsidy(customerId, monthlyCap);
        repo.save(s);
        audit.record("system", "SUBSIDY_ASSIGN", customerId, monthlyCap.toString());
        return s;
    }

    public Optional<Subsidy> find(String customerId) {
        if (customerId == null || customerId.isBlank()) return Optional.empty();
        return repo.findByCustomer(customerId);
    }

    public Subsidy require(String customerId) {
        return find(customerId).orElseThrow(
                () -> new SubsidyException("no subsidy for customer " + customerId));
    }

    public List<Subsidy> list() { return repo.findAll(); }

    public void revoke(String customerId) {
        repo.delete(customerId);
        audit.record("system", "SUBSIDY_REVOKE", customerId, null);
    }
}
