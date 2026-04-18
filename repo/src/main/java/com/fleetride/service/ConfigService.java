package com.fleetride.service;

import com.fleetride.domain.Money;
import com.fleetride.domain.PricingConfig;
import com.fleetride.repository.ConfigRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Admin configuration store: pricing, dictionaries, and templates. In addition to the
 * DB-backed settings authored by administrators, the service exposes a read-through
 * <b>payload overlay</b> sourced from the currently-active signed update package:
 * files under {@code <activePayloadRoot>/dictionaries/} and {@code /templates/} are
 * loaded as key→value maps and returned in preference to the DB. Applying or rolling
 * back an update calls {@link #reloadFromActivePayload(Path)} so runtime reads of
 * {@link #template(String)} and {@link #dictionary(String)} actually change — the
 * update affects real app behavior, not just stored metadata.
 */
public final class ConfigService {
    private final PricingConfig pricing;
    private final ConfigRepository repo;

    private final Map<String, String> templateOverlay = new HashMap<>();
    private final Map<String, String> dictionaryOverlay = new HashMap<>();
    private Path activePayloadRoot;

    public ConfigService(PricingConfig pricing, ConfigRepository repo) {
        if (pricing == null) throw new IllegalArgumentException("pricing required");
        if (repo == null) throw new IllegalArgumentException("repo required");
        this.pricing = pricing;
        this.repo = repo;
        loadPricing();
    }

    public PricingConfig pricing() { return pricing; }

    public void setBaseFare(Money m) { pricing.setBaseFare(m); repo.setSetting("pricing.baseFare", m.amount().toPlainString()); }
    public void setPerMile(Money m) { pricing.setPerMile(m); repo.setSetting("pricing.perMile", m.amount().toPlainString()); }
    public void setPerMinute(Money m) { pricing.setPerMinute(m); repo.setSetting("pricing.perMinute", m.amount().toPlainString()); }
    public void setPriorityMultiplier(BigDecimal v) { pricing.setPriorityMultiplier(v); repo.setSetting("pricing.priorityMultiplier", v.toPlainString()); }
    public void setLateCancelFee(Money m) { pricing.setLateCancelFee(m); repo.setSetting("pricing.lateCancelFee", m.amount().toPlainString()); }
    public void setPerFloorSurcharge(Money m) { pricing.setPerFloorSurcharge(m); repo.setSetting("pricing.perFloorSurcharge", m.amount().toPlainString()); }
    public void setFreeFloorThreshold(int v) { pricing.setFreeFloorThreshold(v); repo.setSetting("pricing.freeFloorThreshold", Integer.toString(v)); }
    public void setDepositPercent(BigDecimal v) { pricing.setDepositPercent(v); repo.setSetting("pricing.depositPercent", v.toPlainString()); }
    public void setMonthlySubsidyCap(Money m) { pricing.setMonthlySubsidyCap(m); repo.setSetting("pricing.monthlySubsidyCap", m.amount().toPlainString()); }
    public void setMaxCouponPercent(BigDecimal v) { pricing.setMaxCouponPercent(v); repo.setSetting("pricing.maxCouponPercent", v.toPlainString()); }
    public void setCouponMinimumOrder(Money m) { pricing.setCouponMinimumOrder(m); repo.setSetting("pricing.couponMinimumOrder", m.amount().toPlainString()); }
    public void setAutoCancelMinutes(int v) { pricing.setAutoCancelMinutes(v); repo.setSetting("pricing.autoCancelMinutes", Integer.toString(v)); }
    public void setLateCancelWindowMinutes(int v) { pricing.setLateCancelWindowMinutes(v); repo.setSetting("pricing.lateCancelWindowMinutes", Integer.toString(v)); }
    public void setDisputeWindowDays(int v) { pricing.setDisputeWindowDays(v); repo.setSetting("pricing.disputeWindowDays", Integer.toString(v)); }
    public void setOverdueFeePerSweep(Money m) { pricing.setOverdueFeePerSweep(m); repo.setSetting("pricing.overdueFeePerSweep", m.amount().toPlainString()); }

    public void setDictionary(String key, String value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
        repo.setDictionary(key, value);
    }

    public Optional<String> dictionary(String key) {
        String overlay = dictionaryOverlay.get(key);
        if (overlay != null) return Optional.of(overlay);
        return repo.getDictionary(key);
    }

    public Map<String, String> allDictionaries() {
        Map<String, String> merged = new LinkedHashMap<>(repo.allDictionaries());
        merged.putAll(dictionaryOverlay);
        return merged;
    }

    public void setTemplate(String key, String value) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key required");
        repo.setTemplate(key, value);
    }

    public Optional<String> template(String key) {
        String overlay = templateOverlay.get(key);
        if (overlay != null) return Optional.of(overlay);
        return repo.getTemplate(key);
    }

    public Map<String, String> allTemplates() {
        Map<String, String> merged = new LinkedHashMap<>(repo.allTemplates());
        merged.putAll(templateOverlay);
        return merged;
    }

    public String render(String templateKey, Map<String, String> params) {
        String tpl = template(templateKey)
                .orElseThrow(() -> new IllegalArgumentException("no template " + templateKey));
        String out = tpl;
        for (Map.Entry<String, String> e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /**
     * Re-read the read-through overlay from the given payload root. Called on startup
     * and after every update apply/rollback so {@link #template(String)} and
     * {@link #dictionary(String)} observe the newly-active payload. Passing
     * {@code null} clears the overlay and reverts to pure DB reads, which is the
     * correct behavior when no update has been applied yet.
     */
    public synchronized void reloadFromActivePayload(Path payloadRoot) {
        templateOverlay.clear();
        dictionaryOverlay.clear();
        this.activePayloadRoot = payloadRoot;
        if (payloadRoot == null) return;
        loadOverlayDir(payloadRoot.resolve("templates"), templateOverlay);
        loadOverlayDir(payloadRoot.resolve("dictionaries"), dictionaryOverlay);
    }

    public Optional<Path> activePayloadRoot() {
        return Optional.ofNullable(activePayloadRoot);
    }

    /** Size of the active template overlay, diagnostic use only. */
    public int overlayTemplateCount() { return templateOverlay.size(); }
    public int overlayDictionaryCount() { return dictionaryOverlay.size(); }

    private static void loadOverlayDir(Path dir, Map<String, String> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path relative = dir.relativize(p);
                String key = relative.toString().replace(java.io.File.separatorChar, '/');
                // Strip a trailing .txt/.tmpl/.dict extension so authors can name files
                // "welcome.txt" but still reference the key as "welcome".
                key = stripExtension(key);
                try {
                    out.put(key, Files.readString(p));
                } catch (IOException e) {
                    // Skip unreadable files rather than fail activation.
                }
            });
        } catch (IOException ignored) {
            // Directory disappeared between listing and reading — treat as empty.
        }
    }

    private static String stripExtension(String key) {
        int dot = key.lastIndexOf('.');
        if (dot <= 0) return key;
        String ext = key.substring(dot + 1).toLowerCase();
        if (ext.equals("txt") || ext.equals("tmpl") || ext.equals("dict")) {
            return key.substring(0, dot);
        }
        return key;
    }

    private void loadPricing() {
        repo.getSetting("pricing.baseFare").ifPresent(v -> pricing.setBaseFare(Money.of(v)));
        repo.getSetting("pricing.perMile").ifPresent(v -> pricing.setPerMile(Money.of(v)));
        repo.getSetting("pricing.perMinute").ifPresent(v -> pricing.setPerMinute(Money.of(v)));
        repo.getSetting("pricing.priorityMultiplier").ifPresent(v -> pricing.setPriorityMultiplier(new BigDecimal(v)));
        repo.getSetting("pricing.lateCancelFee").ifPresent(v -> pricing.setLateCancelFee(Money.of(v)));
        repo.getSetting("pricing.perFloorSurcharge").ifPresent(v -> pricing.setPerFloorSurcharge(Money.of(v)));
        repo.getSetting("pricing.freeFloorThreshold").ifPresent(v -> pricing.setFreeFloorThreshold(Integer.parseInt(v)));
        repo.getSetting("pricing.depositPercent").ifPresent(v -> pricing.setDepositPercent(new BigDecimal(v)));
        repo.getSetting("pricing.monthlySubsidyCap").ifPresent(v -> pricing.setMonthlySubsidyCap(Money.of(v)));
        repo.getSetting("pricing.maxCouponPercent").ifPresent(v -> pricing.setMaxCouponPercent(new BigDecimal(v)));
        repo.getSetting("pricing.couponMinimumOrder").ifPresent(v -> pricing.setCouponMinimumOrder(Money.of(v)));
        repo.getSetting("pricing.autoCancelMinutes").ifPresent(v -> pricing.setAutoCancelMinutes(Integer.parseInt(v)));
        repo.getSetting("pricing.lateCancelWindowMinutes").ifPresent(v -> pricing.setLateCancelWindowMinutes(Integer.parseInt(v)));
        repo.getSetting("pricing.disputeWindowDays").ifPresent(v -> pricing.setDisputeWindowDays(Integer.parseInt(v)));
        repo.getSetting("pricing.overdueFeePerSweep").ifPresent(v -> pricing.setOverdueFeePerSweep(Money.of(v)));
    }
}
