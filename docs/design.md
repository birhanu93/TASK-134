# FleetRide Dispatch & Billing Console Design

## Overview

FleetRide is an offline-first JavaFX desktop console for ride and carpool dispatch and settlement operations on Windows 11. The application supports three local roles (Dispatcher, Finance Clerk, Administrator) and a multi-window workflow that allows trip intake, order timeline management, and settlement/reconciliation activities in parallel. All operational data is stored locally in SQLite, and sensitive fields are encrypted at rest.

## Goals and non-goals

### Goals

- Provide deterministic offline dispatch lifecycle management for orders with enforceable state rules and time-based automation.
- Support desktop-native operator workflows: multi-window layout, system tray controls, right-click context actions, global shortcuts, and clipboard actions.
- Provide finance-grade local settlement ledger handling including deposits, final payments, partial refunds, and CSV export.
- Maintain local security controls: role-based authorization, encrypted sensitive fields, masked displays, attachment controls, and expiring machine-local share tokens.
- Deliver stability and operability for 30-day continuous operation with idempotent recovery and startup under 5 seconds.

### Non-goals

- No cloud synchronization, remote API dependency, or internet-required workflows.
- No real payment gateway capture/authorization (card-on-file is recorded as placeholder tender metadata).
- No cross-machine share link portability.
- No mobile or browser UI.

## Architecture

The application follows a modular layered desktop architecture:

1. Presentation layer (JavaFX)
   - Windows:
     - Trip Intake Window
     - Order Timeline Window
     - Settlement/Reconciliation Window
   - Shared desktop shell:
     - Global command bus for keyboard shortcuts (`Ctrl+N`, `Ctrl+F`, `Ctrl+S`, `Ctrl+Shift+E`)
     - Context menu actions on orders
     - System tray controller (minimize/restore, lock/unlock, status badges)

2. Application service layer
   - `OrderService`: create/update state transitions, timeout checks, dispute openings.
   - `PricingService`: fare calculation, coupon/subsidy application with limits.
   - `SettlementService`: deposits/finals/refunds, reconciliation, export.
   - `AttachmentService`: local file intake, validation, fingerprinting, tokenized access.
   - `AdminConfigService`: policy/config dictionary/template management.
   - `SchedulerService`: hourly and nightly jobs plus quota reclamation.
   - `RecoveryService`: crash-safe checkpoints and idempotent command replay.
   - `HealthService`: startup/runtime health checks and diagnostics.

3. Persistence layer
   - SQLite DB (WAL mode for durability and concurrent reads).
   - Controlled local asset directory for attachments.
   - Local config/update package directory.
   - Event and job logs in DB plus rolling local file logs.

4. Security utilities
   - AES-256-GCM field encryption (passwords, payment tokens) using an admin-set local
     master key (`FLEETRIDE_MASTER_KEY` / `fleetride.masterKey`). Password verification
     decrypts the stored ciphertext and constant-time compares to the presented password;
     administrators must guard the master key as strongly as the DB itself.
   - Role/permission checks enforced inside `Secured*Service` wrappers — the UI can hide
     menus but cannot substitute for the server-side gate.
   - Sensitive display masking (last 4 characters).
   - Share token manager with machine binding and TTL enforcement.
   - Updates are only trusted via a pre-provisioned RSA public key; if the key is missing
     at startup, the application refuses to launch rather than accept any signature.

5. Update subsystem
   - Offline signed package import.
   - Signature verification and compatibility checks.
   - Staged apply with rollback snapshot and reboot-safe switch-over.

## Data model

Core entities:

- `User`
  - `id`, `username`, `role` (`DISPATCHER`, `FINANCE_CLERK`, `ADMIN`)
  - `encrypted_password` (AES-256-GCM under the admin-set local master key; verification decrypts the stored ciphertext and constant-time compares to the presented password), `created_at`, `is_locked`

- `Customer`
  - `id`, `name`, `phone_masked`, `email_masked`, `notes`
  - subsidy fields: `monthly_subsidy_cap` (default 50.00), `subsidy_used_month`

- `Order`
  - `id`, `customer_id`, `pickup_address`, `dropoff_address`
  - `rider_count` (1..6), `window_start`, `window_end`
  - `vehicle_type`, `service_priority`, `pickup_floor`, `dropoff_floor`
  - `status` (`PENDING_MATCH`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELED`, `IN_DISPUTE`)
  - `scheduled_pickup_at`, `accepted_at`, `started_at`, `completed_at`, `canceled_at`
  - `cancel_reason`, `cancel_fee_applied`, `dispute_deadline_at`

- `Trip` — carpool container grouping one or more rider orders
  - `id`, `vehicle_type`, `capacity` (1..6, i.e. seat count for the whole vehicle),
    `window_start`, `window_end`, `created_at`, `driver_placeholder`
  - `status` (`PLANNING`, `DISPATCHED`, `CLOSED`, `CANCELED`)
  - `owner_user_id`, `dispatched_at`, `closed_at`, `canceled_at`
  - Each rider `Order` carries a nullable `trip_id` foreign key; `Trip` in
    `PLANNING` enforces seat-capacity across its riders, propagates the shared
    window/vehicle, and cascades cancellation to its non-terminal rider orders.

- `PricingSnapshot`
  - `id`, `order_id`, `base_fare`, `distance_component`, `time_component`
  - `priority_multiplier`, `floor_surcharge`, `coupon_amount`, `subsidy_amount`, `total_fare`
  - stores immutable calculation inputs/outputs for audit

- `Coupon`
  - `id`, `code`, `type` (`PERCENT`, `FIXED`)
  - constraints: `percent <= 20`, optional `min_order_threshold`
  - validity dates and active flag

- `LedgerEntry`
  - `id`, `order_id`, `entry_type` (`DEPOSIT`, `FINAL_PAYMENT`, `REFUND`, `FEE`, `ADJUSTMENT`)
  - `tender_type` (`CASH`, `CARD_ON_FILE`, `CHECK`)
  - `amount`, `currency`, `posted_at`, `reference`

- `Attachment`
  - `id`, `owner_type`, `owner_id`, `original_name`, `stored_path`
  - `mime_type`, `size_bytes` (<= 20MB), `sha256`, `uploaded_by`, `uploaded_at`

- `ShareToken`
  - `id`, `attachment_id`, `token_hash`, `expires_at` (default +24h), `machine_fingerprint`, `created_by`

- `JobRun`
  - `id`, `job_name`, `started_at`, `completed_at`, `status`, `details_json`, `checkpoint_cursor`

- `AuditLog`
  - `id`, `actor_user_id`, `action`, `entity_type`, `entity_id`, `timestamp`, `payload_json`

- `AppConfig`
  - key/value scoped config for pricing, policy windows, quotas, templates, and dictionaries

## Core flows

1. Dispatch intake and progression
   - Dispatcher creates a standalone rider order (single-rider intake form) **or** a
     carpool `Trip` that groups one or more rider orders. Rider orders on a trip share
     the trip's vehicle type and scheduled window, and are bound via `orders.trip_id`.
   - Initial rider-order state is `PENDING_MATCH`. Initial trip status is `PLANNING`.
   - Context menu or shortcut-driven order transitions enforce legal path:
     - `PENDING_MATCH -> ACCEPTED -> IN_PROGRESS -> COMPLETED`
     - cancel path to `CANCELED` from pre-completion states
     - `COMPLETED -> IN_DISPUTE` only within 7 days of completion
   - `Trip` lifecycle: `PLANNING -> DISPATCHED -> CLOSED` (all rider orders terminal),
     with `cancel` permitted from `PLANNING`/`DISPATCHED` — the cancel cascades into
     non-terminal rider orders so a canceled trip leaves no ambiguously-pending riders.
   - A trip's capacity is enforced across `sum(riderCount)` of its non-canceled riders.
   - Hourly scheduler auto-cancels stale `PENDING_MATCH` orders older than 15 minutes.

2. Cancellation fee logic
   - On cancellation, compare cancellation timestamp against scheduled pickup timestamp.
   - If canceled within 10 minutes of scheduled pickup, add $5.00 fee ledger entry.

3. Offline pricing and settlement
   - Fare formula:
     - `3.50 + (1.80 * miles) + (0.35 * minutes)`
     - multiply by `1.25` when priority applies
     - add floor surcharge `1.00 * floors_above_3` (pickup and drop-off combined policy)
   - Apply coupon (percent capped at 20% or fixed amount with minimum threshold).
   - Apply customer subsidy subject to monthly cap.
   - Post deposit (default 20%), later final payment, and optional partial refunds.
   - Reconciliation window exports selected period ledger data to CSV.

4. Attachment intake and controlled sharing
   - Validate extension/content type allowlist (`pdf`, `jpg`, `png`) and max 20MB.
   - Compute SHA-256 and store deduplicated/safe file reference.
   - Create share token with TTL (default 24h) and machine binding; token resolution allowed only on same machine and within expiry.

5. Tray, lock, and operational awareness
   - Minimize to tray keeps scheduler/jobs running.
   - Lock mode hides sensitive data and requires re-authentication.
   - Tray indicator surfaces overdue balances and pending jobs count.

6. Recovery and idempotency
   - State transitions executed as idempotent commands with unique command IDs.
   - Checkpoints persist in-progress job/transition context.
   - On restart after crash, replay unfinished commands safely (skip already-applied IDs).

## Security and privacy considerations

- Encrypt password and payment token fields at rest with AES-256-GCM under the admin-provided master key sourced from secure local storage (system property / environment variable). Password verification is done by decrypting the stored ciphertext and constant-time comparing to the presented password.
- Never store or display full sensitive identifiers once masked view is required (last-4 display pattern).
- Enforce role-based command authorization for all state transitions, finance actions, exports, and admin panels.
- Validate attachment file signatures/headers in addition to extension checks.
- Restrict share token lookup by token hash, machine fingerprint, and expiration time.
- Audit all privileged changes (config changes, refunds, disputes, update imports, lock/unlock events).

## Performance and scalability constraints

- Startup target: under 5 seconds on a standard office PC by lazy-loading non-critical windows/services.
- UI remains responsive by moving DB/file/scheduler operations to background task executors.
- Index SQLite tables on status, timestamps, foreign keys, and reconciliation date ranges.
- Use pagination/virtualized lists for long timeline and ledger views.
- Keep job scans incremental via checkpoint cursors instead of full-table rescans.

## Reliability and failure handling

- Deterministic resource management:
  - try-with-resources for DB statements/result sets/files.
  - explicit shutdown hooks for executors and DB connection pool.
- Enable SQLite WAL + periodic checkpoint strategy for durability and read concurrency.
- Each scheduled job writes start/end status and resume cursor for crash recovery.
- Import update packages into staged directory; if validation/apply fails, restore prior version snapshot.
- Prevent duplicate processing with unique idempotency keys per transition/payment/refund operation.

## Observability and analytics

- Structured local logs for app, scheduler, update, and security domains.
- Job dashboard metrics:
  - pending timeout candidates, canceled-by-timeout counts, fee-generated counts
  - overdue balances count, reconciliation export success/failure counts
- Health checks:
  - DB writable/readable
  - attachment directory access
  - scheduler heartbeat freshness
  - encryption key availability
- Audit timeline per order and per user for operational traceability.

## Deployment/runtime assumptions

- OS: Windows 11 desktop, baseline resolution 1920x1080 with high-DPI scaling support.
- Runtime: Java 21+ with JavaFX, packaged as native installer/distribution for desktop use.
- Storage: Local SQLite database and controlled filesystem directories under app data root.
- Operation mode: Fully offline, single-machine authority for data and share token validation.
- Maintenance: Local admin performs key setup, configuration updates, and signed offline package imports.
