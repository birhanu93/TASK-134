# Fix Check Report: First Audit Issues Re-evaluation

## Verdict
**Pass**

All issues from the first audit are now fixed in the static codebase. The implementation now covers the timeout cadence, pending-match late-cancel fee handling, dispatcher-safe status indicators, real order search, direct reconciliation export wiring, floor-note support across UI/model/persistence, stronger admin-window authorization, documentation clarifying that passwords are hashed rather than AES-encrypted, and a true tray-minimize flow that hides the window and restores it from the tray.

## Static Boundary
- Static-only review of the previously identified issues.
- No app execution, tests, Docker, or runtime verification was performed.
- Any tray/window-manager behavior remains a manual-verification item.

## Prior Issue Status

### 1. 15-minute auto-cancel rule not enforceable with shipped scheduler cadence
- Status: **Fixed**
- Rationale:
  The app now starts the timeout scheduler every 5 minutes instead of every 60 minutes, which is statically capable of enforcing a 15-minute auto-cancel window.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:70-73`
  `src/main/java/com/fleetride/service/ScheduledJobService.java:135-149`

### 2. Late-cancel fee logic contradicted the prompt for pending-match cancellations
- Status: **Fixed**
- Rationale:
  `OrderService.cancel` now always computes the cancellation fee, including for pending-match orders, and the tests now explicitly expect a `$5.00` fee for a pending cancellation within the late-cancel window.
- Evidence:
  `src/main/java/com/fleetride/service/OrderService.java:143-152`
  `src/main/java/com/fleetride/service/PricingEngine.java:95-100`
  `src/test/java/com/fleetride/service/OrderServiceTest.java:127-142`

### 3. Dispatcher Order Timeline/status path routed through finance-only payment permissions
- Status: **Fixed**
- Rationale:
  `StatusIndicators` now catches `Authorizer.ForbiddenException` and returns `null` for overdue balances when the caller lacks payment-read permission, preventing dispatcher status rendering from depending on finance-only access.
- Evidence:
  `src/main/java/com/fleetride/ui/StatusIndicators.java:34-50`
  `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:82-85`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:135-139`

### 4. Required order search workflow missing; `Ctrl+F` only opened a window
- Status: **Fixed**
- Rationale:
  The implementation adds a search dialog on `Ctrl+F`, supports an initial query, includes in-window filtering over id, customer id, state, address, notes, and coupon code, and the README now documents the search behavior correctly.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:161-166`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:242-244`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:288-294`
  `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:59-115`
  `README.md:109-114`

### 5. `Ctrl+Shift+E` and export menu did not perform export
- Status: **Fixed**
- Rationale:
  The finance menu item and global shortcut now call `exportReconciliationCsv()` directly, and that method opens a save dialog and invokes `securedReconciliationService.exportCsv(...)`.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:175-178`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:248-252`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:268-286`

### 6. Pickup/drop-off floor-level notes absent from UI and persistence model
- Status: **Fixed**
- Rationale:
  Floor-note fields now exist in the trip-intake UI, `Order` domain model, SQLite schema/migration, repository save/load logic, and order-timeline display/search.
- Evidence:
  `src/main/java/com/fleetride/ui/TripIntakeWindow.java:45-52`
  `src/main/java/com/fleetride/ui/TripIntakeWindow.java:67-72`
  `src/main/java/com/fleetride/ui/TripIntakeWindow.java:98-101`
  `src/main/java/com/fleetride/domain/Order.java:20-21`
  `src/main/java/com/fleetride/domain/Order.java:78-87`
  `src/main/java/com/fleetride/repository/sqlite/Migrations.java:22-37`
  `src/main/java/com/fleetride/repository/sqlite/Migrations.java:156-157`
  `src/main/java/com/fleetride/repository/sqlite/SqliteOrderRepository.java:27-41`
  `src/main/java/com/fleetride/repository/sqlite/SqliteOrderRepository.java:151-153`
  `src/main/java/com/fleetride/ui/OrderTimelineWindow.java:44-54`

### 7. Admin-sensitive UI data protected only by menu visibility, not explicit authorization
- Status: **Fixed**
- Rationale:
  `UserAdminWindow` now requires `USER_MANAGE` both on build and refresh/save, and `HealthAndAuditWindow` now requires `AUDIT_READ` on build plus explicit checks inside health/job/audit tab refresh flows, with `SCHEDULER_RUN` guarding job history.
- Evidence:
  `src/main/java/com/fleetride/ui/UserAdminWindow.java:32-35`
  `src/main/java/com/fleetride/ui/UserAdminWindow.java:57-66`
  `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:25-30`
  `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:40-50`
  `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:64-75`
  `src/main/java/com/fleetride/ui/HealthAndAuditWindow.java:90-101`

### 8. Password storage materially deviated from prompt wording without clear justification
- Status: **Fixed**
- Rationale:
  The implementation still uses PBKDF2 password hashing, but the code and docs now consistently describe that as the intentional design. The configuration docs explicitly say the AES master key is for payment tokens only, and `User` now exposes `passwordHash()` with the old `encryptedPassword()` accessor marked deprecated.
- Evidence:
  `README.md:146`
  `README.md:155-169`
  `src/main/java/com/fleetride/domain/User.java:8-9`
  `src/main/java/com/fleetride/domain/User.java:24-31`
  `src/main/java/com/fleetride/service/EncryptionService.java:81-107`

### 9. "Minimize to Tray" implemented as window iconify only
- Status: **Fixed**
- Rationale:
  The minimize action now routes through `TrayPresence.minimizeToTray()`, which hides the stage when the system tray is available and shows a restore hint via the tray icon. The tray open/double-click path restores the stage.
- Evidence:
  `src/main/java/com/fleetride/ui/FleetRideApp.java:218-220`
  `src/main/java/com/fleetride/ui/FleetRideApp.java:268-274`
  `src/main/java/com/fleetride/ui/TrayPresence.java:40-44`
  `src/main/java/com/fleetride/ui/TrayPresence.java:70-90`

## Summary
- Fixed: 9
- Partially fixed: 0
- Still failing: 0

## Remaining Action
- None within the scope of the first audit issues.
