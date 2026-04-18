package com.fleetride.service;

import com.fleetride.domain.Money;
import com.fleetride.domain.PricingConfig;
import com.fleetride.repository.InMemoryConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServiceTest {

    @Test
    void rejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigService(null, new InMemoryConfigRepository()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigService(new PricingConfig(), null));
    }

    @Test
    void dictionaryAndTemplate() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        c.setDictionary("colors.primary", "blue");
        assertEquals("blue", c.dictionary("colors.primary").orElseThrow());
        assertTrue(c.dictionary("missing").isEmpty());
        assertEquals(1, c.allDictionaries().size());

        c.setTemplate("welcome", "Hello {name}");
        assertEquals("Hello {name}", c.template("welcome").orElseThrow());
        assertTrue(c.template("missing").isEmpty());
        assertEquals(1, c.allTemplates().size());

        String rendered = c.render("welcome", Map.of("name", "Alice"));
        assertEquals("Hello Alice", rendered);
    }

    @Test
    void renderMissingTemplateThrows() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        assertThrows(IllegalArgumentException.class, () -> c.render("nope", Map.of()));
    }

    @Test
    void setDictionaryRejectsBlank() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        assertThrows(IllegalArgumentException.class, () -> c.setDictionary(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> c.setDictionary(" ", "x"));
    }

    @Test
    void setTemplateRejectsBlank() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        assertThrows(IllegalArgumentException.class, () -> c.setTemplate(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> c.setTemplate(" ", "x"));
    }

    @Test
    void pricingAccessor() {
        PricingConfig p = new PricingConfig();
        ConfigService c = new ConfigService(p, new InMemoryConfigRepository());
        assertSame(p, c.pricing());
    }

    @Test
    void pricingUpdatesPersist() {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.setBaseFare(Money.of("4.00"));
        c.setPerMile(Money.of("2.00"));
        c.setPerMinute(Money.of("0.50"));
        c.setPriorityMultiplier(new BigDecimal("1.30"));
        c.setLateCancelFee(Money.of("6.50"));
        c.setPerFloorSurcharge(Money.of("1.25"));
        c.setFreeFloorThreshold(4);
        c.setDepositPercent(new BigDecimal("0.30"));
        c.setMonthlySubsidyCap(Money.of("75.00"));
        // Coupon percent max is hard-capped at 20% by spec.
        c.setMaxCouponPercent(new BigDecimal("0.15"));
        c.setCouponMinimumOrder(Money.of("40.00"));
        c.setAutoCancelMinutes(20);
        c.setLateCancelWindowMinutes(12);
        c.setDisputeWindowDays(10);
        c.setOverdueFeePerSweep(Money.of("7.00"));

        PricingConfig fresh = new PricingConfig();
        new ConfigService(fresh, repo);
        assertEquals(Money.of("4.00"), fresh.baseFare());
        assertEquals(Money.of("2.00"), fresh.perMile());
        assertEquals(Money.of("0.50"), fresh.perMinute());
        assertEquals(new BigDecimal("1.30"), fresh.priorityMultiplier());
        assertEquals(Money.of("6.50"), fresh.lateCancelFee());
        assertEquals(Money.of("1.25"), fresh.perFloorSurcharge());
        assertEquals(4, fresh.freeFloorThreshold());
        assertEquals(new BigDecimal("0.30"), fresh.depositPercent());
        assertEquals(Money.of("75.00"), fresh.monthlySubsidyCap());
        assertEquals(new BigDecimal("0.15"), fresh.maxCouponPercent());
        assertEquals(Money.of("40.00"), fresh.couponMinimumOrder());
        assertEquals(20, fresh.autoCancelMinutes());
        assertEquals(12, fresh.lateCancelWindowMinutes());
        assertEquals(10, fresh.disputeWindowDays());
        assertEquals(Money.of("7.00"), fresh.overdueFeePerSweep());
    }

    @Test
    void setterRejectsInvalidValueAndDoesNotPersist() {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setMaxCouponPercent(new BigDecimal("0.50")));
        // Invalid value must neither mutate the pricing object nor land in the settings store.
        assertEquals(new BigDecimal("0.20"), c.pricing().maxCouponPercent());
        assertTrue(repo.getSetting("pricing.maxCouponPercent").isEmpty());
    }

    @Test
    void setterRejectsNegativeMoney() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setBaseFare(Money.of("-1.00")));
        assertThrows(PricingConfig.InvalidPolicyException.class,
                () -> c.setLateCancelFee(Money.of("-5.00")));
    }

    @Test
    void overlayFromPayloadShadowsDbValues(@TempDir Path dir) throws Exception {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.setTemplate("welcome", "DB-hello {name}");
        c.setDictionary("vehicle", "DB-value");
        assertEquals("DB-hello {name}", c.template("welcome").orElseThrow());

        Path payload = dir.resolve("payload");
        Files.createDirectories(payload.resolve("templates"));
        Files.createDirectories(payload.resolve("dictionaries"));
        Files.writeString(payload.resolve("templates/welcome.txt"), "payload-hello {name}");
        Files.writeString(payload.resolve("dictionaries/vehicle.txt"), "payload-value");
        Files.writeString(payload.resolve("templates/fresh.txt"), "new {key}");

        c.reloadFromActivePayload(payload);
        // Payload entries shadow DB values at read time.
        assertEquals("payload-hello Alice", c.render("welcome", Map.of("name", "Alice")));
        assertEquals("payload-value", c.dictionary("vehicle").orElseThrow());
        // Payload-only key is visible too.
        assertEquals("new foo", c.render("fresh", Map.of("key", "foo")));
        assertEquals(2, c.overlayTemplateCount());
        assertEquals(1, c.overlayDictionaryCount());
    }

    @Test
    void overlayFallsThroughToDbForUnmatchedKeys(@TempDir Path dir) throws Exception {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.setTemplate("db-only", "db-tpl");
        Path payload = dir.resolve("payload");
        Files.createDirectories(payload.resolve("templates"));
        Files.writeString(payload.resolve("templates/overlay-only.txt"), "ov");
        c.reloadFromActivePayload(payload);
        assertEquals("db-tpl", c.template("db-only").orElseThrow());
        assertEquals("ov", c.template("overlay-only").orElseThrow());
    }

    @Test
    void reloadWithNullClearsOverlay(@TempDir Path dir) throws Exception {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.setTemplate("welcome", "db");
        Path payload = dir.resolve("payload");
        Files.createDirectories(payload.resolve("templates"));
        Files.writeString(payload.resolve("templates/welcome.txt"), "overlay");
        c.reloadFromActivePayload(payload);
        assertEquals("overlay", c.template("welcome").orElseThrow());
        c.reloadFromActivePayload(null);
        assertEquals("db", c.template("welcome").orElseThrow());
        assertEquals(0, c.overlayTemplateCount());
        assertTrue(c.activePayloadRoot().isEmpty());
    }

    @Test
    void reloadFromMissingDirectoryIsNoop(@TempDir Path dir) {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.reloadFromActivePayload(dir.resolve("does-not-exist"));
        assertEquals(0, c.overlayTemplateCount());
        assertEquals(0, c.overlayDictionaryCount());
    }

    @Test
    void allTemplatesAndDictionariesMergeOverlayOverDb(@TempDir Path dir) throws Exception {
        InMemoryConfigRepository repo = new InMemoryConfigRepository();
        ConfigService c = new ConfigService(new PricingConfig(), repo);
        c.setTemplate("welcome", "db-value");
        c.setTemplate("db-only", "db-db");
        Path payload = dir.resolve("payload");
        Files.createDirectories(payload.resolve("templates"));
        Files.writeString(payload.resolve("templates/welcome.txt"), "overlay-value");
        Files.writeString(payload.resolve("templates/overlay-only.txt"), "overlay-overlay");
        c.reloadFromActivePayload(payload);
        Map<String, String> merged = c.allTemplates();
        assertEquals("overlay-value", merged.get("welcome"));
        assertEquals("db-db", merged.get("db-only"));
        assertEquals("overlay-overlay", merged.get("overlay-only"));
    }

    @Test
    void setterRejectsNonPositiveTimeWindow() {
        ConfigService c = new ConfigService(new PricingConfig(), new InMemoryConfigRepository());
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setAutoCancelMinutes(0));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setLateCancelWindowMinutes(-3));
        assertThrows(PricingConfig.InvalidPolicyException.class, () -> c.setDisputeWindowDays(0));
    }
}
