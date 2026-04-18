package com.fleetride.security;

import com.fleetride.repository.JobRunRepository;
import com.fleetride.service.ScheduledJobService;

import java.util.List;

public final class SecuredSchedulerService {
    private final ScheduledJobService delegate;
    private final Authorizer authz;

    public SecuredSchedulerService(ScheduledJobService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public List<JobRunRepository.Record> recentRuns(int limit) {
        authz.require(Permission.SCHEDULER_RUN);
        return delegate.recentRuns(limit);
    }

    public ScheduledJobService.JobReport hourlyTimeoutEnforcement() {
        authz.require(Permission.SCHEDULER_RUN);
        return delegate.hourlyTimeoutEnforcement();
    }

    public ScheduledJobService.JobReport nightlyOverdueSweep() {
        authz.require(Permission.SCHEDULER_RUN);
        return delegate.nightlyOverdueSweep();
    }

    public ScheduledJobService.JobReport nightlyQuotaReclamation() {
        authz.require(Permission.SCHEDULER_RUN);
        return delegate.nightlyQuotaReclamation();
    }
}
