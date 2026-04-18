# FleetRide Dispatch & Billing Console

**Project type: desktop application (JavaFX).** This is not a web or backend
service — there is no HTTP listener, no server port, no browser UI. It is a
single-operator JavaFX desktop app that ride-dispatch offices run on a Windows
11 workstation (baseline 1920×1080, high-DPI aware). All state is local to that
machine: a SQLite database file, a SHA-256 fingerprinted attachment directory,
and a signed-update cache. The app must work offline indefinitely.

## Contents

- [Who this is for](#who-this-is-for)
- [Running the app](#running-the-app)
- [Demo credentials](#demo-credentials)
- [Verifying the app works](#verifying-the-app-works)
- [Running the tests](#running-the-tests)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Security model](#security-model)
- [Update signing & rollback](#update-signing--rollback)
- [Scheduled jobs](#scheduled-jobs)
- [Durable checkpoints](#durable-checkpoints)
- [Invoicing, coupons, subsidies, attachments](#invoicing-coupons-subsidies-attachments)
- [Configuration center](#configuration-center)
- [Storage layout](#storage-layout)
- [Logs](#logs)

## Who this is for

An **operator** (dispatcher, finance clerk, or administrator) sitting at the
workstation. The sections below tell them — or an evaluator cloning this
repo — how to install the prerequisites, launch the app, and sign in. The
spec target is Windows 11; see the platform notes at the end of this
section for what changes on macOS/Linux.

## Running the app

The only host-side prerequisite is **Docker**. A cross-build container
compiles the app, assembles a Windows JRE via `jlink`, wraps it with
`launch4j` into a Windows `.exe`, and drops a portable zip into `./dist/`.
No JDK, no Maven, no JavaFX install on the host.

### 1. Build the Windows `.exe`

From any host that has Docker (macOS, Linux, or Windows):

```bash
./build_exe.sh
```

First run downloads a Windows JDK, JavaFX 17 Windows jmods, and launch4j
(the resulting image is ~1 GB, one-time). Subsequent builds reuse the
cached image and take ~30 s.

Output:

```
dist/FleetRide-1.0.0-windows-x64.zip
```

Inside the zip:

```
FleetRide/
├── FleetRide.exe        ← launcher stub (double-click this)
├── runtime/             ← bundled Windows JRE (jlink-produced)
└── lib/
    ├── fleetride-console.jar
    └── <runtime dependencies>
```

### 2. Open the `.exe` on a Windows 11 machine

> ⚠️ The `.exe` runs only on Windows. On macOS or Linux it's inert
> unless you have a Windows VM or Wine. The spec target is Windows 11;
> **steps (2) through (4) below and the entire "Verifying the app
> works" section assume you're at the Windows 11 machine now** — if
> you built the zip on a Mac or Linux host, copy it across before
> continuing.

On a Windows 11 workstation:

1. Copy `FleetRide-1.0.0-windows-x64.zip` onto the machine and unzip it
   (right-click → Extract All). You'll get a `FleetRide/` folder.
2. Provision the trusted update public key in the data directory (the app
   fail-closes at startup without it):

   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\.fleetride" | Out-Null
   # On Windows 11, install Git for Windows or use WSL for openssl:
   openssl genrsa -out "$env:TEMP\update-private.pem" 2048
   openssl rsa -in "$env:TEMP\update-private.pem" -pubout `
       -out "$env:USERPROFILE\.fleetride\update-public.pem"
   ```

3. Set the master key + optional bootstrap admin for the current session:

   ```powershell
   $env:FLEETRIDE_MASTER_KEY = "choose-a-long-passphrase"
   $env:FLEETRIDE_BOOTSTRAP_ADMIN = "admin:admin123"
   ```

   (For a permanent install, set them as user-level environment variables
   via *Settings → System → About → Advanced system settings →
   Environment Variables*, then double-click `FleetRide.exe` like any
   other desktop app.)

4. Double-click `FleetRide.exe`. The **"FleetRide – Sign in"** window
   opens. Sign in as `admin` / `admin123`.

Startup target is under 5 s on a standard office PC.

### Platform notes

- **Windows 11** — the spec target. `.exe` double-click, native system
  tray, native window chrome, native keyboard shortcuts. The bundled
  runtime also works on Windows 10 if you happen to be on one.
- **macOS / Linux** — `./build_exe.sh` still runs and produces the
  Windows `.exe`; you just can't open it locally. Ship the zip to a
  Windows host (VM, spare machine, CI runner) to test.
- **Code-signed MSI installer** — `build_exe.sh` produces a *portable*
  zip, not a signed installer. Shipping a signed `.exe`/`.msi` needs a
  real code-signing certificate and is typically done by a signing
  service on a Windows build machine; that's not wired up here.

## Demo credentials

The easiest way to exercise every role: launch with
`FLEETRIDE_BOOTSTRAP_ADMIN=admin:admin123` against an empty data
directory. Sign in as **admin / admin123**, then use
**Administration → Users & roles** to add the other roles. Suggested
accounts — one per role:

| Role | Username | Password | What the account can do |
|------|----------|----------|-------------------------|
| Administrator | `admin` | `admin123` | Everything. Configures pricing, coupons, subsidies, updates, users, audit/health. |
| Dispatcher | `dispatcher` | `dispatch123` | Trip intake, trip & carpool management, customers, attachments, order timeline. Cannot see Finance or Administration. |
| Finance clerk | `finance` | `finance123` | Settlement, invoices, reconciliation CSV export, read-only order timeline. Cannot dispatch or administer. |

These strings are for local demos only. They are neither baked into the
source nor accepted as defaults at runtime — the sign-in window still
requires the operator to type them.

## Verifying the app works

After launching and signing in as `admin`, the following golden path proves
the end-to-end wiring:

1. **Dispatch → Customers → Add customer.** Enter a name and phone, click
   "Add customer". The row appears in the list with no payment token.
2. **Dispatch → New Trip (`Ctrl+N`).** Pick the new customer, fill pickup
   and drop-off addresses, accept the default vehicle/priority, click
   "Create order". The feedback strip shows `Created <id>`.
3. **Dispatch → Order Timeline.** The order appears in `PENDING_MATCH`.
   Right-click it → Accept → Start Trip → Complete. The state column moves
   to `COMPLETED`.
4. **Finance → Invoices.** Choose the completed order in the picker, click
   "Issue invoice". The invoice appears in `ISSUED`. Select it, click
   "Mark paid" → status becomes `PAID`.
5. **Finance → Settlement.** The order's balance now reads `0.00`.
6. **Administration → Health & audit.** The health tab reads
   `Overall: HEALTHY`; the audit tab lists every action taken above.

If any step fails, the app is not wired correctly for this machine — stop
and check the structured JSON log in the data directory for the specific
error (`%USERPROFILE%\.fleetride\fleetride.log` on Windows,
`~/.fleetride/fleetride.log` on macOS/Linux). The log is auto-redacted.

### Role-based navigation check

Sign out (Session menu) and sign in as the dispatcher demo account. The menu
bar should show only **Dispatch** and **Session** — no Finance, no
Administration. Sign in as the finance clerk demo account: you should see
**Dispatch**, **Finance**, **Session**, but Dispatch should no longer list
New Trip / Trips & Carpools / Customers / Attachments. That is the
role-gated navigation working as designed.

## Running the tests

**Docker is mandatory.** The test suite runs under Monocle's headless JavaFX
glass platform so real `Stage`, `Scene`, and `Button.fire()` paths execute
in CI without a display server.

```bash
./run_tests.sh
```

This builds the Maven container and runs `mvn -B clean verify`. The suite
includes 660+ JUnit 5 tests covering domain, service, security, repository,
log, integration, **and** the JavaFX UI layer (login/bootstrap flow, menu
visibility per role, every critical window, role-gated end-to-end user
actions). JaCoCo enforces:

- **≥ 75% line / ≥ 55% branch** on domain, service, security, repository,
  and log code. A handful of SQLite-only migration branches and admin-only
  secured-service paths are only exercised by end-to-end upgrade scenarios.
- **100% line + 100% branch** on pure UI policy classes (`MenuPolicy`,
  `BootstrapEnv`, `OrderSearchFilter`, `MimeGuesser`, `UpdateTrustCheck`,
  `StatusIndicators`).
- **≥ 65% line** on every JavaFX window class (`*Window`) and on
  `ErrorAlerts`. Some paths inside the windows (`FileChooser`,
  `TextInputDialog`, blocking `Alert.showAndWait()`) cannot be driven
  from a headless test.
- **≥ 85% line** across the bundle overall.

Only three classes are excluded from coverage, each with a justification in
`pom.xml`:

- `TrayPresence` — uses `java.awt.SystemTray`, which has no headless mode.
- `ClipboardHelper` — requires a live OS clipboard.
- `FleetRideApp` — the `javafx.application.Application` bootstrap wiring
  only runs under `launch()`; its in-instance helpers (login, menu bar,
  shortcut binding) are still covered via a test seam (`attach()`), and
  those execute against Monocle.

The JaCoCo HTML report is written to `target/site/jacoco/index.html`.

Direct invocation without the wrapper:

```bash
docker compose run --rm tests
```

Tests never install or require Maven on the host. The image is
`maven:3.9.6-eclipse-temurin-17`.

## Configuration

All runtime configuration is via environment variables or `-D` system
properties (set by the operator's shortcut, not by a config file):

| Property / env | Default | Purpose |
|----------------|---------|---------|
| `fleetride.dataDir` | `~/.fleetride` | Parent directory for DB, logs, attachments, updates, machine-id |
| `fleetride.masterKey` / `FLEETRIDE_MASTER_KEY` | **required** | AES-256 master key used to encrypt user passwords and payment tokens at rest. The app refuses to start if neither is set to a non-empty value. Losing this key is unrecoverable: password verification works by decrypting the stored ciphertext. |
| `fleetride.updatePublicKey` | `<dataDir>/update-public.pem` | PEM-encoded RSA public key used to verify update packages. **The app refuses to start if this file is missing** — there is no self-signed fallback. |
| `FLEETRIDE_BOOTSTRAP_ADMIN` (env) | _(unset)_ | One-shot `user:password` admin provisioning on first launch. Parsed by `BootstrapEnv`: must contain a single `:` with a non-empty username. Consumed only when the user table is empty. |

The **machine identifier** lives in `<dataDir>/machine-id` and is derived from
a 32-byte cryptographic random. It cannot be set from the command line, and
callers of `ShareLinkService.resolve(token)` do **not** pass a machine ID —
the host reads its own from disk. This prevents same-machine-check spoofing.

## Architecture

```
┌──────────────────────────────────────────────┐
│ JavaFX UI (ui package)                       │
│   • Sign-in / bootstrap window               │
│   • Trip intake window                       │
│   • Order timeline window (context menu)     │
│   • Settlement & reconciliation window       │
│   • Invoice window (Finance)                 │
│   • Configuration Center window (Admin)      │
│   • Trips & Carpools window                  │
│   • Coupons & subsidies window               │
│   • Updates window (Admin)                   │
│   • Users & roles window (Admin)             │
│   • Health & audit window (Admin)            │
│   • System tray presence (lock/unlock, …)    │
└────────────────────┬─────────────────────────┘
                     │ Secured{Order,Customer,Payment,
                     │        Reconciliation,Attachment,
                     │        Config,Invoice}Service
                     │       → Authorizer → AuthService
                     ▼
┌──────────────────────────────────────────────┐
│ Services                                     │
│   OrderService, PaymentService,              │
│   InvoiceService, PricingEngine,             │
│   OrderStateMachine, AttachmentService,      │
│   ShareLinkService (machine-bound),          │
│   ReconciliationService, ScheduledJobService,│
│   UpdateService (RSA/SHA-256 + rollback),    │
│   CheckpointService, AuditService,           │
│   ConfigService (DB-backed), HealthService,  │
│   EncryptionService, StructuredLogger,       │
│   MachineIdProvider                          │
└────────────────────┬─────────────────────────┘
                     ▼
┌──────────────────────────────────────────────┐
│ Repositories (SQLite + in-memory)            │
│   users / customers / orders / payments /    │
│   attachments / disputes / invoices /        │
│   audit_events / share_links / checkpoints / │
│   job_runs / update_history / update_state / │
│   settings / dictionaries / templates        │
└──────────────────────────────────────────────┘
```

### Menus and shortcuts

Built per role by `MenuPolicy`:

- **Dispatcher** sees Dispatch (New Trip, Trips & Carpools, Customers,
  Attachments, Order Timeline, Search Orders). No Finance or Administration.
- **Finance clerk** sees Dispatch (Order Timeline + Search only), Finance
  (Settlement, Invoices, Export reconciliation CSV).
- **Administrator** sees everything, plus Administration (Users & roles,
  Configuration center, Coupons & subsidies, Updates, Health & audit).

| Shortcut | Action |
|----------|--------|
| `Ctrl+N` | New Trip (dispatcher/admin only) |
| `Ctrl+F` | Search orders — prompts for a query then opens Order Timeline filtered by id, customer id, state, pickup/dropoff address, floor notes, or coupon. Inside the timeline, `Ctrl+F` focuses the inline search field. |
| `Ctrl+S` | Save — triggers the active window's save action. |
| `Ctrl+Shift+E` | Export reconciliation CSV (finance/admin only). |

Right-click an order: Accept, Start Trip, Complete, Cancel, Open Dispute,
Copy Order ID, Copy Pickup Address, Copy Dropoff Address.

System tray popup: Open Console, Status, Lock Session, Unlock Session, Exit.

## Security model

### Password & payment-token encryption at rest

Passwords and payment tokens are encrypted at rest with AES-256-GCM under
the admin-provided master key. The master key is derived once from
`FLEETRIDE_MASTER_KEY` / `fleetride.masterKey` via PBKDF2-HMAC-SHA256
(210 000 iterations) so the same master key yields the same AES key across
restarts. Every ciphertext carries its own random 96-bit IV and a 128-bit
GCM tag — so the same password produces a different ciphertext on every
call. `verifyPassword` decrypts the stored ciphertext and constant-time
compares the plaintext; a malformed or tampered ciphertext returns `false`
instead of throwing. Masked display uses `MaskingUtil.maskLast4` and
`maskPhone`.

Operational note: losing the master key is unrecoverable — AES passwords
are reversible only with the key, so both the DB and the key must be
protected together.

### Role-based authorization & strict data scope

Three roles: `DISPATCHER`, `FINANCE_CLERK`, `ADMINISTRATOR`. The
authorization matrix lives in `Authorizer.defaultMatrix()`. Every
`Secured*Service` call:

1. Requires the appropriate `Permission` (function-level).
2. Enforces resource ownership on write paths (object-level). Admins are
   the only identity permitted to cross ownership boundaries.
3. Filters `list`/`find`/`listByState` results to rows the caller owns,
   except for `ADMINISTRATOR` and `FINANCE_CLERK` (`canSeeAll`). Null
   ownership is **not** a visibility shortcut — dispatchers cannot see or
   act on resources that lack an owner.

### Machine-bound share links

`ShareLinkService` ties every token to the host's own machine identifier via
`MachineIdProvider`. The resolver reads the machine ID from disk and rejects
tokens issued on a different host. Callers cannot pass the machine ID at
resolve time.

### Token-gated attachment reads

`SecuredAttachmentService.issueShareToken(attachmentId, ttlHours)` creates a
short-lived, machine-bound share token scoped to a specific attachment. An
outside consumer calls `SecuredAttachmentService.resolveByToken(token)`:

- Role-based authorization is **not** applied — possession of a valid,
  unexpired, same-machine token is the sole authorization.
- Tokens are refused if they are unknown, expired, issued on another
  machine, or scoped to any resource other than `attachment:<id>`.
- Deleting the underlying attachment invalidates every outstanding token
  because the resolver checks the attachment still exists.

## Update signing & rollback

An update package is an **RSA/SHA-256 signed ZIP** carrying the application
payload — templates, dictionaries, bundled resources. `UpdateService.apply`
verifies the signature against the trusted public key, then:

1. Extracts the ZIP into a staging directory beside the target so a partial
   failure cannot corrupt the prior active payload.
2. Atomically renames the staging directory to
   `<installRoot>/versions/<version>/` (replacing any existing dir at that
   path).
3. Archives the previous `active.pkg` as `prior-<version>-<ns>.pkg` and
   records it in `update_history`, then copies the new package to
   `active.pkg`.
4. Writes the new version to `<installRoot>/active-version` and updates
   `update_state`.

Rollback re-extracts the prior package into its version directory, swaps
`active.pkg`, updates `active-version` and `update_state`, and removes the
history row. The startup activation hook enforces that the payload directory
for the recorded current version actually exists on disk.

**Payload overlay wired into runtime reads.** After every successful apply,
rollback, or startup activation, `ConfigService` is re-pointed at the
active payload directory. Any file under `<activePayloadRoot>/templates/`
or `<activePayloadRoot>/dictionaries/` becomes a read-through overlay:
`ConfigService.template(key)` and `dictionary(key)` check the overlay first
and only fall back to the admin-authored DB values when the overlay does
not supply the key. Rollback flips the overlay back to the prior payload in
the same call. File keys are stripped of `.txt`, `.tmpl`, and `.dict`
extensions.

**Hardening.** Version strings are restricted to `[A-Za-z0-9._-]+` so a
tampered manifest cannot path-traverse out of `versions/`. ZIP entries
whose normalized path would escape the target directory (classic zip-slip)
are rejected during extraction.

**Trust is fail-closed at startup.** If the trusted public key is missing
the app refuses to start — `UpdateTrustCheck.require(path)` throws. There
is no self-signed / generated fallback. `SecuredUpdateService` then gates
every read and every apply/rollback call on
`UPDATE_APPLY`/`UPDATE_ROLLBACK` permission so non-admins cannot reach the
raw service even if they reach the window.

Generating a signer key pair (one-time, on the release-engineering
workstation, not on the operator's machine):

```bash
openssl genrsa -out update-private.pem 2048
openssl rsa -in update-private.pem -pubout -out update-public.pem

# Deploy update-public.pem as <dataDir>/update-public.pem (or wire it via
# fleetride.updatePublicKey). Keep update-private.pem off the operator's
# workstation.

openssl dgst -sha256 -sign update-private.pem -out fleet-1.2.0.pkg.sig fleet-1.2.0.pkg
```

## Scheduled jobs

`ScheduledJobService` runs three jobs with persistent run logs (`job_runs`
table):

| Job | Default cadence | Purpose |
|-----|-----------------|---------|
| `hourly-timeout` | every 5 minutes (configurable) | Cancels pending-match orders older than the configured auto-cancel window (default 15 min). Runs on a sub-window cadence so the 15-minute rule can actually fire on time. |
| `nightly-overdue` | every 24 hours | For every `COMPLETED` order with an unpaid balance, adds `pricing.overdueFeePerSweep` to `orders.overdue_fee_cents` and includes it in the balance. |
| `nightly-quota` | every 24 hours | Resets each customer's monthly usage counters — both `subsidy_used_cents` and `monthly_rides_used` — so the subsidy cap and ride quota start fresh each period. `monthly_ride_quota` (the cap itself) is left untouched. Customers with neither counter dirty are skipped. |

## Durable checkpoints

All state-mutating order transitions (`ACCEPT`, `START`, `COMPLETE`,
`CANCEL`, `DISPUTE`) are wrapped in `CheckpointService.runIdempotent`.
Checkpoints are persisted to the `checkpoints` table: `PENDING` →
`COMMITTED`. On startup, `AppContext.recoverPendingCheckpoints()` runs
`CheckpointRecoveryService`, which inspects every `PENDING` entry and
either:

- **Commits it** if the domain object already reflects the target state
  (the side-effect landed but the commit step was interrupted);
- **Clears it** otherwise, leaving the next normal call free to retry
  cleanly.

## Invoicing, coupons, subsidies, attachments

- **Invoices** live in the Finance menu (`SecuredInvoiceService`): issue
  for an order, mark paid, cancel. All writes go through the `invoices`
  SQLite table and emit audit events.
- **Coupons & subsidies** live in Administration
  (`SecuredCouponService` + `SecuredSubsidyService`). Coupons can be
  percent-off (capped by `pricing.maxCouponPercent`) or fixed amount, with
  a minimum-order threshold. Subsidies are per-customer monthly caps.
  `OrderService.quoteResolving(order)` looks up the coupon from the order's
  recorded code and the subsidy from the customer id.
- **Attachments** live in Dispatch (`SecuredAttachmentService`). Every
  read/list/upload/delete/share-token operation is gated on access to the
  owning order, not merely on the `ATTACHMENT_READ` permission. Dispatchers
  only see attachments for orders they own; admin and finance can see all.
  PDF/JPG/PNG only, max 20 MB, SHA-256 fingerprinted.

## Configuration center

The admin-only Configuration Center window edits every pricing/policy
knob and persists it to the `settings` table:

- Pricing: base fare, per-mile, per-minute, priority multiplier,
  late-cancel fee, per-floor surcharge, free-floor threshold.
- Business policy: deposit percent, monthly subsidy cap, max coupon
  percent, coupon minimum order, auto-cancel window, late-cancel window,
  dispute window, overdue fee per sweep.
- Dictionaries and templates (arbitrary key/value, persisted in their own
  tables).

`ConfigService` loads persisted values on construction, so `PricingEngine`
sees the last-saved settings after every restart.

## Storage layout

```
<dataDir>/
├── fleetride.db              # SQLite (WAL + FK enabled)
├── fleetride.log             # structured JSON log, auto-redacted
├── machine-id                # 32-byte random, read as the host identifier
├── attachments/              # PDF/JPG/PNG copies, SHA-256 fingerprinted
├── updates/                  # active.pkg + prior-<version>-<ns>.pkg + update-public.pem
└── update-public.pem         # signer's public key
```

## Logs

`StructuredLogger` emits one JSON line per event. `Redactor` automatically
scrubs:

- Keys containing `password`, `pwd`, `token`, `secret`, `authorization`,
  `apikey`, `card`, `phone`, `ssn`.
- Values matching credit-card, phone, or email patterns.

## Directory map

```
src/
├── main/java/com/fleetride/
│   ├── AppContext.java
│   ├── Main.java / ui/FleetRideApp
│   ├── domain/                      ← entities, enums
│   ├── service/                     ← business logic
│   ├── security/                    ← Authorizer, Permission, Secured{…}Service
│   ├── repository/                  ← interfaces + in-memory impls
│   │   └── sqlite/                  ← Database, Migrations, SQLite repos
│   ├── log/                         ← StructuredLogger, Redactor
│   └── ui/                          ← JavaFX windows + tray + config center
│                                      + MenuPolicy / BootstrapEnv /
│                                        OrderSearchFilter / MimeGuesser /
│                                        UpdateTrustCheck / ErrorAlerts
└── test/java/com/fleetride/
    ├── domain/                      ← entity unit tests
    ├── service/                     ← service unit tests
    ├── security/                    ← authorization tests
    ├── repository/                  ← in-memory + SQLite tests
    ├── log/                         ← logger + redactor tests
    ├── integration/                 ← AppContext smoke, SQLite persistence, restart idempotency
    └── ui/                          ← pure UI policy tests + Monocle-backed
                                       window tests (login/bootstrap flow,
                                       role-gated menus, every window's
                                       real user action paths)
```
