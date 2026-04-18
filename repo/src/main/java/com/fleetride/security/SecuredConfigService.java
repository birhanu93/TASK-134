package com.fleetride.security;

import com.fleetride.domain.Money;
import com.fleetride.domain.PricingConfig;
import com.fleetride.service.ConfigService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public final class SecuredConfigService {
    private final ConfigService delegate;
    private final Authorizer authz;

    public SecuredConfigService(ConfigService delegate, Authorizer authz) {
        this.delegate = delegate;
        this.authz = authz;
    }

    public PricingConfig pricing() {
        authz.require(Permission.CONFIG_READ);
        return delegate.pricing();
    }

    public void setBaseFare(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setBaseFare(m); }
    public void setPerMile(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setPerMile(m); }
    public void setPerMinute(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setPerMinute(m); }
    public void setPriorityMultiplier(BigDecimal v) { authz.require(Permission.CONFIG_WRITE); delegate.setPriorityMultiplier(v); }
    public void setLateCancelFee(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setLateCancelFee(m); }
    public void setPerFloorSurcharge(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setPerFloorSurcharge(m); }
    public void setFreeFloorThreshold(int v) { authz.require(Permission.CONFIG_WRITE); delegate.setFreeFloorThreshold(v); }
    public void setDepositPercent(BigDecimal v) { authz.require(Permission.CONFIG_WRITE); delegate.setDepositPercent(v); }
    public void setMonthlySubsidyCap(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setMonthlySubsidyCap(m); }
    public void setMaxCouponPercent(BigDecimal v) { authz.require(Permission.CONFIG_WRITE); delegate.setMaxCouponPercent(v); }
    public void setCouponMinimumOrder(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setCouponMinimumOrder(m); }
    public void setAutoCancelMinutes(int v) { authz.require(Permission.CONFIG_WRITE); delegate.setAutoCancelMinutes(v); }
    public void setLateCancelWindowMinutes(int v) { authz.require(Permission.CONFIG_WRITE); delegate.setLateCancelWindowMinutes(v); }
    public void setDisputeWindowDays(int v) { authz.require(Permission.CONFIG_WRITE); delegate.setDisputeWindowDays(v); }
    public void setOverdueFeePerSweep(Money m) { authz.require(Permission.CONFIG_WRITE); delegate.setOverdueFeePerSweep(m); }

    public void setDictionary(String key, String value) {
        authz.require(Permission.CONFIG_WRITE);
        delegate.setDictionary(key, value);
    }

    public Optional<String> dictionary(String key) {
        authz.require(Permission.CONFIG_READ);
        return delegate.dictionary(key);
    }

    public Map<String, String> allDictionaries() {
        authz.require(Permission.CONFIG_READ);
        return delegate.allDictionaries();
    }

    public void setTemplate(String key, String value) {
        authz.require(Permission.CONFIG_WRITE);
        delegate.setTemplate(key, value);
    }

    public Optional<String> template(String key) {
        authz.require(Permission.CONFIG_READ);
        return delegate.template(key);
    }

    public Map<String, String> allTemplates() {
        authz.require(Permission.CONFIG_READ);
        return delegate.allTemplates();
    }

    public String render(String templateKey, Map<String, String> params) {
        authz.require(Permission.CONFIG_READ);
        return delegate.render(templateKey, params);
    }
}
