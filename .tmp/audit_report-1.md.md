# FleetRide Dispatch & Billing Console Static Audit

## 1. Verdict
- Overall conclusion: **Partial Pass**

The repository is a substantial JavaFX/Maven desktop application with local SQLite persistence, role-aware service wrappers, attachments, invoicing, updates, scheduled jobs, and extensive non-UI tests. However, several prompt-critical requirements are either implemented incorrectly or not implemented at all, including 15-minute cancellation enforcement, the late-cancel fee rule, required dispatcher search/export desktop behaviors, and a dispatcher-facing UI path that statically appears to fail due to finance-only permission checks.

## 2. Scope and Static Verification Boundary
- What was reviewed:
  `README.md`, `pom.xml`, Java source under `src/main/java`, tests under `src/test/java`, existing generated reports/artifacts under `target/` where useful for static evidence.
- What was not reviewed:
  Runtime behavior, actual JavaFX rendering on Windows 11/high-DPI, startup timing, 30-day stability, tray behavior on a real desktop session, Docker/runtime environment behavior.
- What was intentionally not executed:
  The application, tests, Docker, external services, update-package flows, or any manual UI interaction.
- Claims requiring manual verification:
  Windows 11 rendering/high-DPI behavior, startup under 5 seconds, 30-day continuous stability, actual tray UX, crash recovery under real process interruption, and real signed-update operational behavior.

## 3. Repository / Requirement Mapping Summary
- Prompt core goal:
  Offline JavaFX dispatcher/finance/admin desktop console for ride/carpool intake, trip state management, payments/refunds/invoices, reconciliation export, admin policies, attachments, scheduled jobs, local SQLite persistence, and machine-local security controls.
- Main implementation areas mapped:
  JavaFX shell/windows in `src/main/java/com/fleetride/ui`, business logic in `service`, role enforcement in `security`, SQLite persistence/migrations in `repository/sqlite`, logging in `log`, and broad unit/integration coverage in `src/test/java`.
- Major constraints checked:
  Local-only SQLite storage, AES-backed payment token encryption, role enforcement, order-state rules, pricing defaults, scheduled jobs, attachment allowlist/size validation, same-machine share tokens, reconciliation CSV export, and update signing/rollback.

## 4. Section-by-section Review

### 4.1 Hard Gates

#### 4.1.1 Documentation and static verifiability
- Conclusion: **Pass**
- Rationale:
  The project has a clear `README`, Maven manifest, documented entry points, configuration, storage layout, security model, and test commands. The documented entry point (`com.fleetride.ui.FleetRideApp`) matches `pom.xml` and `Main.java`.
- Evidence:
  `README.md:72-153`, `README.md:296-336`, `pom.xml:56-123`, `src/main/java/com/fleetride/Main.java:5-9`, `src/main/java/com/fleetride/ui/FleetRideApp.java:31-75`

#### 4.1.2 Material deviation from the Prompt
- Conclusion: **Partial Pass**
- Rationale:
  The implementation is centered on the requested business case, but it materially weakens or misses several explicit prompt requirements: no search-orders workflow, `Ctrl+Shift+E` does not export, floor-level notes are absent from both UI and data model, late-cancel fees are not applied to pending-match cancellations, and the 15-minute auto-cancel rule is only enforced by an hourly scheduler.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:161-175`, `src/main/java/com/fleetride/ui/FleetRideApp.java:239-249`, `src/main/java/com/fleetride/ui/TripIntakeWindow.java:43-76`, `src/main/java/com/fleetride/domain/Order.java:7-30`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:19-55`, `src/main/java/com/fleetride/service/OrderService.java:133-145`, `src/main/java/com/fleetride/ui/FleetRideApp.java:72`, `src/main/java/com/fleetride/service/ScheduledJobService.java:144-149`

### 4.2 Delivery Completeness

#### 4.2.1 Coverage of explicit core functional requirements
- Conclusion: **Partial Pass**
- Rationale:
  Many core areas exist: trip intake, order timeline, settlement, invoices, attachments, scheduled jobs, config center, update flow, SQLite repositories, and security wrappers. Still, several explicit requirements are incomplete or misimplemented: search orders, floor-level notes, true export shortcut behavior, exact 15-minute timeout enforcement, and the prompt’s late-cancel rule.
- Evidence:
  `src/main/java/com/fleetride/ui/TripIntakeWindow.java:34-117`, `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:24-103`, `src/main/java/com/fleetride/ui/SettlementWindow.java:30-100`, `src/main/java/com/fleetride/ui/InvoiceWindow.java:27-84`, `src/main/java/com/fleetride/ui/AttachmentWindow.java:31-100`, `src/main/java/com/fleetride/ui/ConfigCenterWindow.java:44-176`, `src/main/java/com/fleetride/service/UpdateService.java:32-70`

#### 4.2.2 Basic end-to-end deliverable vs partial/demo
- Conclusion: **Pass**
- Rationale:
  This is a coherent multi-package application rather than a code fragment. It includes README, build/test config, UI shell, persistence, business services, repositories, logging, and a broad test suite.
- Evidence:
  `README.md:1-336`, `pom.xml:1-123`, `src/main/java/com/fleetride/AppContext.java:74-237`, `src/test/java/com/fleetride/integration/AppContextIntegrationTest.java:32-60`

### 4.3 Engineering and Architecture Quality

#### 4.3.1 Structure and module decomposition
- Conclusion: **Pass**
- Rationale:
  The codebase is reasonably decomposed into domain, services, security wrappers, repositories, SQLite adapters, logging, and UI windows. `AppContext` wires these together cleanly.
- Evidence:
  `README.md:316-336`, `src/main/java/com/fleetride/AppContext.java:74-196`

#### 4.3.2 Maintainability and extensibility
- Conclusion: **Partial Pass**
- Rationale:
  The service/repository split is maintainable, but some important behavior is wired only at UI level or through menu visibility rather than consistently enforced service boundaries, and some prompt features are simplified out of the domain model entirely.
- Evidence:
  `src/main/java/com/fleetride/security/SecuredOrderService.java:21-130`, `src/main/java/com/fleetride/ui/UserAdminWindow.java:31-69`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:36-71`, `src/main/java/com/fleetride/domain/Order.java:7-30`

### 4.4 Engineering Details and Professionalism

#### 4.4.1 Error handling, logging, validation, API design
- Conclusion: **Partial Pass**
- Rationale:
  There is substantial validation, structured logging, redaction, and repository-level exception wrapping. However, some user-visible flows are statically inconsistent with role permissions, and some required business rules are encoded incorrectly.
- Evidence:
  `src/main/java/com/fleetride/log/StructuredLogger.java:23-65`, `src/main/java/com/fleetride/log/Redactor.java:19-50`, `src/main/java/com/fleetride/service/AttachmentService.java:35-59`, `src/main/java/com/fleetride/service/OrderService.java:133-145`, `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:52-55`, `src/main/java/com/fleetride/ui/StatusIndicators.java:30-36`

#### 4.4.2 Real product vs demo shape
- Conclusion: **Pass**
- Rationale:
  The repository looks like a real application with persistence, audit/update machinery, role-aware services, and a broad test suite, not a teaching-only sample.
- Evidence:
  `src/main/java/com/fleetride/AppContext.java:137-195`, `src/main/java/com/fleetride/repository/sqlite/Database.java:32-112`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:71-816`

### 4.5 Prompt Understanding and Requirement Fit

#### 4.5.1 Business goal, scenario, and implicit constraints
- Conclusion: **Partial Pass**
- Rationale:
  The implementation broadly understands the dispatcher/finance/admin offline-desktop scenario, but several explicit operational semantics are missed: exact auto-cancel timing, cancellation-fee conditions, search/export shortcut behaviors, and floor-level notes.
- Evidence:
  `src/main/java/com/fleetride/service/OrderStateMachine.java:53-56`, `src/main/java/com/fleetride/ui/FleetRideApp.java:230-249`, `src/main/java/com/fleetride/service/OrderService.java:136-140`, `src/main/java/com/fleetride/ui/TripIntakeWindow.java:43-76`

### 4.6 Aesthetics

#### 4.6.1 Visual and interaction fit
- Conclusion: **Cannot Confirm Statistically**
- Rationale:
  The repository contains multiple JavaFX windows and desktop interactions, but visual quality, Windows 11 fit, high-DPI behavior, hover/click states, and rendering consistency cannot be proven without launching the UI.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:119-220`, `src/main/java/com/fleetride/ui/TripIntakeWindow.java:34-111`, `src/main/java/com/fleetride/ui/SettlementWindow.java:30-73`
- Manual verification note:
  Manual UI verification is required on a Windows 11 desktop with high-DPI scaling.

## 5. Issues / Suggestions (Severity-Rated)

### Blocker / High

1. Severity: **High**
   Title: 15-minute auto-cancel rule is not enforceable with the shipped scheduler cadence
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/domain/PricingConfig.java:17-18`, `src/main/java/com/fleetride/service/OrderStateMachine.java:53-56`, `src/main/java/com/fleetride/ui/FleetRideApp.java:72`, `src/main/java/com/fleetride/service/ScheduledJobService.java:135-149`, `README.md:227-233`
   Impact: Orders can remain in `PENDING_MATCH` well beyond 15 minutes because the only automatic enforcement is an hourly sweep.
   Minimum actionable fix: Run timeout enforcement on a cadence that can satisfy the 15-minute SLA, or compute the next-due trigger per order rather than relying on a fixed hourly sweep.

2. Severity: **High**
   Title: Late-cancel fee logic contradicts the prompt for pending-match cancellations
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/service/OrderService.java:136-140`, `src/main/java/com/fleetride/service/PricingEngine.java:95-100`, `src/test/java/com/fleetride/service/OrderServiceTest.java:127-133`
   Impact: An order canceled within 10 minutes of pickup while still pending-match is charged `$0.00`, even though the prompt requires a `$5.00` fee based on pickup timing.
   Minimum actionable fix: Apply the timing-based cancellation fee regardless of whether the order has been accepted, unless the product intentionally defines a narrower policy and documents that deviation.

3. Severity: **High**
   Title: Dispatcher Order Timeline/status path statically routes through finance-only payment permissions
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:52-55`, `src/main/java/com/fleetride/ui/StatusIndicators.java:30-36`, `src/main/java/com/fleetride/security/SecuredPaymentService.java:49-51`, `src/main/java/com/fleetride/security/Authorizer.java:71-73`, `src/main/java/com/fleetride/ui/FleetRideApp.java:135-137`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:506-515`
   Impact: A dispatcher-accessible window and tray status supplier call `PAYMENT_READ`-protected logic that dispatchers are forbidden to use, so a required dispatcher workflow is statically inconsistent and likely to throw.
   Minimum actionable fix: Separate dispatcher-safe status metrics from finance-only balance checks, or grant/mediate only the minimal overdue indicator needed for dispatcher status views.

### Medium

4. Severity: **Medium**
   Title: Required order search workflow is missing; `Ctrl+F` opens a window instead of search
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:161-163`, `src/main/java/com/fleetride/ui/FleetRideApp.java:239-241`, `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:24-50`
   Impact: The prompt explicitly requires `Ctrl+F` to search orders, but the implementation only opens the Order Timeline and provides no search field/filter behavior.
   Minimum actionable fix: Add a searchable order view or search dialog and wire `Ctrl+F` to focus/search rather than merely opening the timeline.

5. Severity: **Medium**
   Title: `Ctrl+Shift+E` and the Export menu do not perform export
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:172-175`, `src/main/java/com/fleetride/ui/FleetRideApp.java:245-249`, `src/main/java/com/fleetride/ui/SettlementWindow.java:51-63`
   Impact: The documented global export shortcut and finance menu item only open a settlement window; they do not execute reconciliation export as required.
   Minimum actionable fix: Invoke the CSV export flow directly from the shortcut/menu item, or focus an existing settlement window and trigger its export action.

6. Severity: **Medium**
   Title: Pickup/drop-off floor-level notes are absent from both UI and persistence model
   Conclusion: **Fail**
   Evidence: `src/main/java/com/fleetride/ui/TripIntakeWindow.java:43-76`, `src/main/java/com/fleetride/ui/TripIntakeWindow.java:85-94`, `src/main/java/com/fleetride/domain/Order.java:7-30`, `src/main/java/com/fleetride/repository/sqlite/Migrations.java:19-55`
   Impact: Dispatchers cannot capture the prompt’s required optional floor-level notes for pickup/drop-off, and there is nowhere to persist them.
   Minimum actionable fix: Extend the domain model, schema, repositories, and trip-intake UI with pickup/drop-off note fields.

7. Severity: **Medium**
   Title: Some admin-sensitive UI data is protected only by menu visibility, not by service-level authorization
   Conclusion: **Partial Fail**
   Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:179-198`, `src/main/java/com/fleetride/ui/UserAdminWindow.java:55-62`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:36-71`
   Impact: User listing and health/job-run inspection can be reached without explicit permission checks if those windows are instantiated outside the intended admin menu flow, weakening the prompt’s role/authorization boundary.
   Minimum actionable fix: Add secured admin services or explicit authorization checks in all admin windows before loading privileged data.

8. Severity: **Medium**
   Title: Password storage materially deviates from the prompt’s AES-encrypted-at-rest wording
   Conclusion: **Partial Fail**
   Evidence: `src/main/java/com/fleetride/repository/sqlite/Migrations.java:7-11`, `src/main/java/com/fleetride/service/AuthService.java:55-64`, `src/main/java/com/fleetride/service/EncryptionService.java:82-106`, `README.md:146-169`
   Impact: The system stores PBKDF2 password hashes in `password_hash`, not AES-encrypted password fields. This is arguably safer, but it is still a prompt deviation that should be explicitly justified.
   Minimum actionable fix: Either update the acceptance interpretation/documentation to justify hashing as the intentional security design, or change the requirement if literal AES encryption is mandatory.

9. Severity: **Medium**
   Title: “Minimize to tray” is implemented as window iconify only
   Conclusion: **Partial Fail**
   Evidence: `src/main/java/com/fleetride/ui/FleetRideApp.java:215-216`, `src/main/java/com/fleetride/ui/TrayPresence.java:31-74`
   Impact: The code clearly installs a tray icon, but the explicit minimize action only iconifies the stage and does not statically prove true tray-minimize behavior.
   Minimum actionable fix: Hide the stage from the taskbar/workspace and restore it through tray actions, or document the intended platform-specific behavior.

## 6. Security Review Summary

### Authentication entry points
- Conclusion: **Pass**
- Evidence and reasoning:
  Authentication is centralized in `AuthService` with bootstrap-admin, login, logout, lock, and unlock flows; registration requires an authenticated admin session. `FleetRideApp` uses those entry points at login/bootstrap time. Evidence: `src/main/java/com/fleetride/service/AuthService.java:31-99`, `src/main/java/com/fleetride/ui/FleetRideApp.java:77-117`

### Route-level authorization
- Conclusion: **Not Applicable**
- Evidence and reasoning:
  This is a JavaFX desktop application with no HTTP routes/controllers in the reviewed scope.

### Object-level authorization
- Conclusion: **Partial Pass**
- Evidence and reasoning:
  `SecuredOrderService`, `SecuredCustomerService`, and `SecuredAttachmentService` enforce owner-aware visibility and mutation rules, including null-owner hiding for dispatchers. Evidence: `src/main/java/com/fleetride/security/SecuredOrderService.java:84-129`, `src/main/java/com/fleetride/security/SecuredCustomerService.java:31-72`, `src/main/java/com/fleetride/security/SecuredAttachmentService.java:40-93`. However, payments/invoices are not object-scoped because finance/admin are allowed globally, and some admin UI data is not service-gated.

### Function-level authorization
- Conclusion: **Partial Pass**
- Evidence and reasoning:
  Most privileged operations call `Authorizer.require(...)` via `Secured*Service` wrappers. Evidence: `src/main/java/com/fleetride/security/Authorizer.java:30-95`, `src/main/java/com/fleetride/security/SecuredPaymentService.java:19-62`, `src/main/java/com/fleetride/security/SecuredConfigService.java:20-74`. The gap is that `UserAdminWindow` and parts of `HealthAndAuditWindow` read privileged data without explicit permission checks. Evidence: `src/main/java/com/fleetride/ui/UserAdminWindow.java:55-62`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:36-71`

### Tenant / user data isolation
- Conclusion: **Partial Pass**
- Evidence and reasoning:
  Dispatcher-owned orders/customers/attachments are filtered by owner, and tests explicitly cover cross-dispatcher isolation. Evidence: `src/main/java/com/fleetride/security/SecuredOrderService.java:92-129`, `src/main/java/com/fleetride/security/SecuredCustomerService.java:39-72`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:137-214`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:353-472`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:580-695`. Finance/admin intentionally bypass some owner scoping, which matches the business roles but means isolation is role-conditional, not universal.

### Admin / internal / debug protection
- Conclusion: **Partial Pass**
- Evidence and reasoning:
  Update apply/rollback and audit reads are permission-checked. Evidence: `src/main/java/com/fleetride/ui/UpdateWindow.java:39-64`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:77-89`. But user listing and health/job-run inspection rely on the admin menu instead of explicit authorization at the data-access point. Evidence: `src/main/java/com/fleetride/ui/UserAdminWindow.java:55-62`, `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:36-71`

## 7. Tests and Logging Review

### Unit tests
- Conclusion: **Pass**
- Rationale:
  The repo contains broad unit coverage across domain, services, security, repositories, and logging using JUnit 5. Evidence: `pom.xml:42-53`, `src/test/java/com/fleetride/service/OrderServiceTest.java:28-205`, `src/test/java/com/fleetride/service/AuthServiceTest.java:12-166`, `src/test/java/com/fleetride/log/StructuredLoggerTest.java:16-152`

### API / integration tests
- Conclusion: **Pass**
- Rationale:
  There is no HTTP API, but there are integration tests for `AppContext`, SQLite-backed persistence, and restart/idempotency behavior. Evidence: `src/test/java/com/fleetride/integration/AppContextIntegrationTest.java:32-60`, `src/test/java/com/fleetride/integration/RestartIdempotencyTest.java:33-118`

### Logging categories / observability
- Conclusion: **Partial Pass**
- Rationale:
  Structured JSON logging and redaction are implemented cleanly, and scheduler job runs are persisted. Evidence: `src/main/java/com/fleetride/log/StructuredLogger.java:39-104`, `src/main/java/com/fleetride/log/Redactor.java:19-50`, `src/main/java/com/fleetride/service/ScheduledJobService.java:165-179`. Health checks are minimal, though, and some important UI/security regressions are not covered by logging-specific tests.

### Sensitive-data leakage risk in logs / responses
- Conclusion: **Pass**
- Rationale:
  Sensitive keys/patterns are redacted in logs, and customer payment tokens are masked for display. Evidence: `src/main/java/com/fleetride/log/Redactor.java:9-50`, `src/main/java/com/fleetride/service/CustomerService.java:31-35`, `src/test/java/com/fleetride/log/StructuredLoggerTest.java:33-40`

## 8. Test Coverage Assessment (Static Audit)

### 8.1 Test Overview
- Unit tests and integration tests exist: yes
- Frameworks: JUnit 5, Mockito in test dependencies
- Test entry points: Maven Surefire via `mvn -B verify`
- Documentation provides test commands: yes, though README centers Docker-based execution
- Evidence:
  `pom.xml:42-53`, `pom.xml:58-120`, `README.md:121-137`

### 8.2 Coverage Mapping Table

| Requirement / Risk Point | Mapped Test Case(s) | Key Assertion / Fixture / Mock | Coverage Assessment | Gap | Minimum Test Addition |
|---|---|---|---|---|---|
| Auth bootstrap/login/register/lock | `src/test/java/com/fleetride/service/AuthServiceTest.java:31-166` | Admin bootstrap, non-admin register denial, lock/unlock assertions `src/test/java/com/fleetride/service/AuthServiceTest.java:45-63` | sufficient | None for core service paths | Add UI/bootstrap-window tests only if UI automation is introduced |
| Dispatcher/admin/finance role isolation on orders/customers/attachments | `src/test/java/com/fleetride/security/SecuredServicesTest.java:137-214`, `353-472`, `561-695` | Cross-dispatcher visibility and mutation denials, finance/admin exceptions | sufficient | No UI-level tests for windows that surface these services | Add JavaFX/controller tests for dispatcher/admin menus and blocked actions |
| Pricing and core order lifecycle | `src/test/java/com/fleetride/service/OrderServiceTest.java:76-177`, `src/test/java/com/fleetride/service/PricingEngineTest.java` | Quote creation, transitions, subsidy persistence, dispute opening | basically covered | Does not cover prompt-specific UI workflows | Add tests for search/export shortcut behavior once implemented |
| 15-minute auto-cancel enforcement | `src/test/java/com/fleetride/service/OrderServiceTest.java:180-193`, `src/test/java/com/fleetride/integration/RestartIdempotencyTest.java:77-103` | `autoCancelStale()` cancels after 20 minutes when explicitly invoked | insufficient | No test covers the shipped hourly scheduler cadence against a 15-minute SLA | Add scheduler-level test that proves orders cancel within policy timing |
| Late-cancel fee within 10 minutes of pickup | `src/test/java/com/fleetride/service/OrderServiceTest.java:135-150` | Fee only asserted after accept; pending cancel explicitly expects zero `src/test/java/com/fleetride/service/OrderServiceTest.java:127-133` | insufficient | Existing tests codify behavior that conflicts with the prompt | Add tests for pending-match cancellation within 10 minutes and after pickup cutoff semantics |
| Attachment validation and share-token controls | `src/test/java/com/fleetride/service/AttachmentServiceTest.java:34-148`, `src/test/java/com/fleetride/security/SecuredServicesTest.java:611-733` | MIME/size/magic validation, token scope, deleted-attachment invalidation | sufficient | No UI tests for attachment/share-link flows | Add UI/controller tests if desktop automation is added |
| Checkpoint persistence / restart idempotency | `src/test/java/com/fleetride/integration/RestartIdempotencyTest.java:51-118`, `src/test/java/com/fleetride/service/CheckpointRecoveryServiceTest.java` | Repeat accept rejected after restart; pending checkpoint persisted | basically covered | No real process-crash/manual recovery verification | Add an integration test around `AppContext.recoverPendingCheckpoints()` with persisted pending rows |
| Update signing / rollback | `src/test/java/com/fleetride/service/UpdateServiceTest.java:58-271` | Valid signature apply/rollback, tampered package rejection, missing-history cases | sufficient | No UI-level update-window coverage | Add window/controller tests if needed |
| Logging / redaction | `src/test/java/com/fleetride/log/StructuredLoggerTest.java:20-152`, `src/test/java/com/fleetride/log/RedactorTest.java` | Sensitive field redaction and JSON output assertions | sufficient | No tests for end-to-end log usage from UI/service flows | Add one integration test ensuring a real service path logs redacted data |
| UI-heavy prompt behaviors: order search, `Ctrl+Shift+E` export, dispatcher timeline status, tray/minimize, floor notes | none located in `src/test/java` | N/A | missing | The most prompt-specific desktop behaviors are untested, and UI packages are excluded from JaCoCo | Add targeted UI/controller tests for shortcuts, search, export, status indicators, and tray/minimize behavior |

### 8.3 Security Coverage Audit
- Authentication: **meaningfully covered**
  `src/test/java/com/fleetride/service/AuthServiceTest.java:31-166` covers bootstrap, login, bad password, non-admin registration, and lock/unlock.
- Route authorization: **not applicable**
  No HTTP/API routes are present.
- Object authorization: **meaningfully covered**
  `src/test/java/com/fleetride/security/SecuredServicesTest.java:137-214`, `353-472`, `580-695` exercises cross-owner order/customer/attachment restrictions.
- Tenant / data isolation: **meaningfully covered for owner-scoped services**
  Tests verify dispatcher A cannot see or mutate dispatcher B’s orders/customers/attachments, including null-owner edge cases.
- Admin / internal protection: **insufficient**
  There are strong tests for secured services, but no tests cover the admin-only windows that bypass service checks for user lists and health/job data, so those gaps could survive despite the current suite.

### 8.4 Final Coverage Judgment
- **Partial Pass**

Major service-layer risks are well covered: auth, secured services, attachments, updates, persistence, restart idempotency, and logging. But several severe requirement-fit defects could still survive while tests pass, because the current suite largely excludes UI behavior and does not cover the exact prompt semantics for scheduler timing, pending-match late-cancel fees, `Ctrl+F` search, `Ctrl+Shift+E` export, or the dispatcher status/permission conflict.

## 9. Final Notes
- This repository is materially more complete than a demo and has strong non-UI engineering discipline.
- The most important failures are not generic style issues; they are prompt-level behavioral mismatches and one dispatcher-facing authorization break in the UI call chain.
- Runtime claims about Windows behavior, tray UX, startup time, and long-run stability remain manual-verification items.
