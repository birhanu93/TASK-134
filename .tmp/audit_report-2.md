# FleetRide Static Audit (Updated Second Pass)

## 1. Verdict
- Overall conclusion: **Partial Pass**

## 2. Scope and Static Verification Boundary
- Reviewed: `README.md`, `pom.xml`, Java source under `src/main/java`, tests under `src/test/java`, and the existing audit artifact under `.tmp/`.
- Not reviewed: live JavaFX rendering, Windows 11 behavior, system-tray behavior on the target OS, performance under load, long-run stability, or packaged deployment behavior outside what is statically shown in source and tests.
- Intentionally not executed: application startup, tests, Docker, external services, and manual UI interaction.
- Manual verification required for: Windows 11/high-DPI rendering, startup under 5 seconds, 30-day continuous stability, tray/minimize behavior, clipboard behavior, and real end-user update deployment outside the tested in-process activation path.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal: an offline JavaFX desktop console for dispatch, finance, and administration, with multi-window trip/order/settlement workflows, SQLite persistence, local security boundaries, attachments, scheduled jobs, reconciliation, and signed offline updates with rollback.
- Main implementation areas reviewed: JavaFX shell/windows, domain and service layers, security wrappers, SQLite repositories/migrations, configuration/update subsystems, and unit/integration/security tests.
- Compared with the prior audit, the previously blocking gaps are now statically addressed: customer ownership is enforced on order/trip create flows, and update payload activation is wired into runtime configuration/template reads with integration tests.

## 4. Section-by-section Review

### 1. Hard Gates

#### 1.1 Documentation and static verifiability
- Conclusion: **Partial Pass**
- Rationale: Startup/bootstrap, role menus, shortcuts, storage, configuration, and update behavior are documented and now mostly consistent with code, but the README still understates the nightly quota job by describing only subsidy reset while the implementation also resets monthly ride usage.
- Evidence: `README.md:72`, `README.md:109`, `README.md:217`, `README.md:279`, `README.md:287`, `src/main/java/com/fleetride/service/ScheduledJobService.java:109`, `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:172`
- Manual verification note: Runtime usability of the documented desktop flows still requires human execution.

#### 1.2 Whether the delivered project materially deviates from the Prompt
- Conclusion: **Pass**
- Rationale: The codebase is now centered on the prompt’s offline dispatch/billing workflow, including explicit trip/carpool handling, secured customer/order/trip flows, settlement, scheduled jobs, attachments, and a signed update path that affects runtime config/template behavior.
- Evidence: `src/main/java/com/fleetride/domain/Trip.java:6`, `src/main/java/com/fleetride/service/TripService.java:24`, `src/main/java/com/fleetride/security/SecuredOrderService.java:53`, `src/main/java/com/fleetride/security/SecuredTripService.java:58`, `src/main/java/com/fleetride/service/ConfigService.java:17`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:47`

### 2. Delivery Completeness

#### 2.1 Core prompt requirement coverage
- Conclusion: **Pass**
- Rationale: Core flows are present across trips/carpools, order lifecycle, pricing/policy enforcement, payments/refunds/reconciliation, attachments/share links, local roles, scheduled jobs, and offline signed updates with rollback and startup activation.
- Evidence: `src/main/java/com/fleetride/ui/TripManagementWindow.java:33`, `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:37`, `src/main/java/com/fleetride/ui/SettlementWindow.java:28`, `src/main/java/com/fleetride/service/ScheduledJobService.java:73`, `src/main/java/com/fleetride/service/UpdateService.java:27`, `src/main/java/com/fleetride/service/ConfigService.java:112`
- Manual verification note: Desktop UX quality and operational behavior remain manual-verification items.

#### 2.2 Basic 0-to-1 deliverable completeness
- Conclusion: **Pass**
- Rationale: This is a full repository with documentation, modular source, persistence, security, UI, and a large test suite rather than a demo or fragment.
- Evidence: `README.md:1`, `pom.xml:1`, `src/main/java/com/fleetride/AppContext.java:81`

### 3. Engineering and Architecture Quality

#### 3.1 Module decomposition and structure
- Conclusion: **Pass**
- Rationale: Responsibilities are separated cleanly across domain, repositories, services, secured wrappers, and JavaFX windows. The trip/carpool slice is integrated through schema, repositories, services, UI, and tests rather than bolted on.
- Evidence: `src/main/java/com/fleetride/AppContext.java:129`, `src/main/java/com/fleetride/repository/sqlite/SqliteTripRepository.java:14`, `src/main/java/com/fleetride/security/SecuredTripService.java:23`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:142`

#### 3.2 Maintainability and extensibility
- Conclusion: **Partial Pass**
- Rationale: The architecture is maintainable overall, and the update/config integration is now materially stronger, but schema integrity around order-to-trip linkage is still weaker than the rest of the model because `orders.trip_id` is stored without a foreign-key constraint.
- Evidence: `src/main/java/com/fleetride/service/ConfigService.java:17`, `src/main/java/com/fleetride/AppContext.java:227`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:24`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:64`

### 4. Engineering Details and Professionalism

#### 4.1 Error handling, logging, validation, API/service design
- Conclusion: **Partial Pass**
- Rationale: Validation and security are substantially improved: create flows now verify customer visibility, pricing setters reject invalid policy values, and update activation has explicit reload hooks and tests. The remaining notable engineering gap is relational integrity for `orders.trip_id`, which can still drift independently of `trips.id`.
- Evidence: `src/main/java/com/fleetride/security/SecuredOrderService.java:57`, `src/main/java/com/fleetride/security/SecuredTripService.java:62`, `src/main/java/com/fleetride/domain/PricingConfig.java:42`, `src/main/java/com/fleetride/security/SecuredUpdateService.java:29`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:24`

#### 4.2 Product-like organization vs demo-level implementation
- Conclusion: **Pass**
- Rationale: The repository reads like a real application with business-specific windows, secured services, persistence, migrations, scheduling, audit, and update handling. The remaining issues are targeted correctness gaps, not signs of a demo-only implementation.
- Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:58`, `src/main/java/com/fleetride/ui/TripManagementWindow.java:33`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:23`

### 5. Prompt Understanding and Requirement Fit

#### 5.1 Business-goal and constraint fit
- Conclusion: **Pass**
- Rationale: The implementation now matches the dispatch/finance/admin desktop brief closely, including multi-window workflows, carpool trip management, local-only storage/security, scheduled offline jobs, and runtime-consumed signed updates.
- Evidence: `README.md:111`, `src/main/java/com/fleetride/ui/FleetRideApp.java:141`, `src/main/java/com/fleetride/service/TripService.java:56`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:29`

### 6. Aesthetics

#### 6.1 Visual and interaction design fit
- Conclusion: **Cannot Confirm Statistically**
- Rationale: Static JavaFX source proves menus, windows, context menus, and shortcut wiring exist, but not real visual quality, layout fidelity, or high-DPI behavior on Windows 11.
- Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:109`, `src/main/java/com/fleetride/ui/TripManagementWindow.java:46`, `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:37`
- Manual verification note: Required on target hardware/OS.

## 5. Issues / Suggestions (Severity-Rated)

### Medium

#### 1. Severity: Medium
- Title: `orders.trip_id` is not protected by a foreign-key constraint
- Conclusion: **Partial Pass**
- Evidence: `src/main/java/com/fleetride/repository/sqlite/Migrations.java:24`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:62`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:64`
- Impact: The trip/carpool model now exists, but SQLite schema integrity still allows an order row to reference a non-existent trip id. That can leave orphaned relationships or silently inconsistent carpool data if repository or migration bugs write bad values.
- Minimum actionable fix: Add `FOREIGN KEY(trip_id) REFERENCES trips(id)` in the `orders` table definition, plus a compatible migration strategy for existing databases and a regression test that rejects or repairs orphaned `trip_id` values.

### Low

#### 2. Severity: Low
- Title: README still misstates nightly quota reclamation behavior
- Conclusion: **Partial Pass**
- Evidence: `README.md:287`, `src/main/java/com/fleetride/service/ScheduledJobService.java:109`, `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:172`
- Impact: Static reviewers and operators are told the nightly quota job only resets subsidy usage, but the implementation also resets monthly ride usage. This weakens documentation accuracy for operations and acceptance review.
- Minimum actionable fix: Update the README scheduled-jobs section to describe both subsidy reset and monthly ride-usage reset.

## 6. Security Review Summary

### Authentication entry points
- Conclusion: **Pass**
- Evidence and reasoning: Authentication is centralized in `AuthService`, with explicit bootstrap, login, lock, unlock, and role-aware registration flow tied into the JavaFX shell. Evidence: `src/main/java/com/fleetride/service/AuthService.java:31`, `src/main/java/com/fleetride/ui/FleetRideApp.java:73`

### Route-level authorization
- Conclusion: **Not Applicable**
- Evidence and reasoning: This is a JavaFX desktop application with no HTTP route/controller layer.

### Object-level authorization
- Conclusion: **Pass**
- Evidence and reasoning: Order creation now resolves the customer and enforces visibility before creation, trip rider creation does the same, and masked payment token reads re-resolve the stored customer before returning data. Evidence: `src/main/java/com/fleetride/security/SecuredOrderService.java:57`, `src/main/java/com/fleetride/security/SecuredOrderService.java:72`, `src/main/java/com/fleetride/security/SecuredTripService.java:62`, `src/main/java/com/fleetride/security/SecuredTripService.java:144`, `src/main/java/com/fleetride/security/SecuredCustomerService.java:46`

### Function-level authorization
- Conclusion: **Pass**
- Evidence and reasoning: Permission checks remain consistent across orders, trips, customers, scheduler, updates, reconciliation, invoices, and admin flows. Evidence: `src/main/java/com/fleetride/security/Authorizer.java:30`, `src/main/java/com/fleetride/security/SecuredTripService.java:49`, `src/main/java/com/fleetride/security/SecuredUpdateService.java:24`

### Tenant / user data isolation
- Conclusion: **Pass**
- Evidence and reasoning: Dispatcher reads and writes are owner-scoped, finance/admin are the only `canSeeAll` roles, and the prior cross-owner customer-linking creation gap is now blocked by secured visibility checks with regression tests. Evidence: `src/main/java/com/fleetride/security/SecuredOrderService.java:79`, `src/main/java/com/fleetride/security/SecuredTripService.java:151`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:205`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:155`

### Admin / internal / debug protection
- Conclusion: **Pass**
- Evidence and reasoning: Updates, audit, scheduler, and user-management remain behind admin permissions and secured wrappers. Evidence: `src/main/java/com/fleetride/security/Authorizer.java:80`, `src/main/java/com/fleetride/security/SecuredAuditService.java:17`, `src/main/java/com/fleetride/security/SecuredSchedulerService.java:17`, `src/main/java/com/fleetride/security/SecuredUpdateService.java:29`

## 7. Tests and Logging Review

### Unit tests
- Conclusion: **Pass**
- Evidence: Unit/security coverage exists for trip management, customer-visibility enforcement on create paths, policy validation, quota reclamation, and masked-token authorization. Evidence: `src/test/java/com/fleetride/security/SecuredServicesTest.java:205`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:155`, `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:172`, `src/test/java/com/fleetride/domain/PricingConfigTest.java:99`

### API / integration tests
- Conclusion: **Pass**
- Evidence: Integration-style tests now cover update payload activation affecting runtime config/template reads, startup re-activation, rollback behavior, and failed-apply preservation of the active overlay. Evidence: `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:47`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:115`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:151`

### Logging categories / observability
- Conclusion: **Pass**
- Evidence: Structured log output and audit/job-run recording remain purposeful, and scheduled jobs log counts and quota-reset details rather than opaque prints. Evidence: `src/main/java/com/fleetride/log/StructuredLogger.java:39`, `src/main/java/com/fleetride/service/ScheduledJobService.java:120`, `src/main/java/com/fleetride/service/TripService.java:51`

### Sensitive-data leakage risk in logs / responses
- Conclusion: **Pass**
- Evidence: Redaction utilities exist, sensitive customer tokens are masked, and access to masked tokens is now ownership-checked. No new static evidence of plaintext password/payment-token logging was found in the reviewed scope. Evidence: `src/main/java/com/fleetride/log/Redactor.java:8`, `src/main/java/com/fleetride/security/SecuredCustomerService.java:46`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:574`

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests exist: yes.
- API / integration-style tests exist: yes.
- Test frameworks: JUnit 5, Mockito, JaCoCo. Evidence: `pom.xml:17`, `pom.xml:42`, `pom.xml:75`
- Test entry points are documented: Maven verify and `run_tests.sh`. Evidence: `README.md:74`, `README.md:133`, `run_tests.sh:1`
- Documentation provides test commands: yes. Evidence: `README.md:74`, `README.md:137`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Trip/carpool domain and ownership boundaries | `src/test/java/com/fleetride/service/TripServiceTest.java`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:143`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:156` | Cross-dispatcher rider add is denied; unknown customer is rejected; admin path succeeds | sufficient | Schema-level referential integrity is not covered | Add a repository/schema test once `trip_id` foreign-key enforcement is introduced |
| Object-level customer isolation on order creation | `src/test/java/com/fleetride/security/SecuredServicesTest.java:206`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:219`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:242` | Dispatcher cannot create for another dispatcher’s customer; unknown customer rejected; admin can create for any customer | sufficient | None in current secured create path | Preserve as regression coverage |
| Config/policy validation | `src/test/java/com/fleetride/domain/PricingConfigTest.java:99`, `src/test/java/com/fleetride/service/ConfigServiceTest.java:110`, `src/test/java/com/fleetride/service/ConfigServiceTest.java:210` | Invalid percentages, negative money, and non-positive windows throw without persisting | sufficient | None for current static scope | Extend only as new policy knobs are added |
| Update payload affects live runtime reads | `src/test/java/com/fleetride/service/ConfigServiceTest.java:130`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:47`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:115`, `src/test/java/com/fleetride/integration/UpdatePayloadActivationIntegrationTest.java:151` | Overlay shadows DB values; apply changes rendered template/dictionary values; startup activation restores missing version dir; failed apply leaves active overlay untouched | sufficient | UI-level consumption is still not directly tested | Add end-to-end UI coverage only if desktop automation is introduced |
| Quota reclamation semantics | `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:165`, `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:172`, `src/test/java/com/fleetride/service/ScheduledJobServiceTest.java:188` | Resets subsidy and monthly ride usage while preserving quota cap and skipping clean customers | basically covered | README is stale even though tests are correct | Keep tests; fix docs |
| Sensitive token visibility | `src/test/java/com/fleetride/security/SecuredServicesTest.java:574`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:465`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:478` | Owner allowed; non-owner and forged detached copies denied | sufficient | None in reviewed scope | Maintain as regression coverage |
| Schema integrity for `orders.trip_id` linkage | No direct test found | No migration assertion or repository-level rejection of orphan `trip_id` values was found | missing | Severe relational drift could survive the current test suite | Add migration/repository tests after introducing FK or equivalent integrity enforcement |

### 8.3 Security Coverage Audit
- Authentication: **sufficiently covered**
  - Evidence: `src/test/java/com/fleetride/security/AuthorizerTest.java:37`
- Route authorization: **not applicable**
  - Evidence: no route layer exists.
- Object-level authorization: **sufficiently covered**
  - Evidence: `src/test/java/com/fleetride/security/SecuredServicesTest.java:206`, `src/test/java/com/fleetride/security/SecuredTripServiceTest.java:156`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:574`
- Tenant / data isolation: **sufficiently covered**
  - Evidence: owner-scoping tests now include cross-dispatcher negative cases for both order create and trip rider-order create.
- Admin / internal protection: **sufficiently covered**
  - Evidence: `src/test/java/com/fleetride/security/SecuredAdminBoundariesTest.java:77`

### 8.4 Final Coverage Judgment
- **Partial Pass**
- Major risks covered: trip/carpool ownership, order-create customer isolation, policy validation, quota-reset behavior, masked-token authorization, and runtime-consumed update activation.
- Remaining uncovered risk: schema-level integrity around `orders.trip_id` is not meaningfully covered, so tests could still pass while orphan trip links remain possible.

## 9. Final Notes
- The previously blocking findings from the earlier audit are now statically resolved: secured create flows enforce customer visibility, and signed update payloads are wired into runtime config/template reads with integration coverage.
- The remaining concerns are narrower and lower severity: one schema-integrity issue and one documentation mismatch. Those are enough to keep the verdict at **Partial Pass**, but the codebase is materially closer to acceptance than in the prior pass.
