# FleetRide Local Interface Specification

## Scope

FleetRide is a desktop-only, offline JavaFX application with no remote backend dependency. This specification defines local service contracts, command/event semantics, storage validation rules, and export interfaces.

## Versioning strategy

- Spec version: `1.0.0`
- Command schema versioning:
  - Every persisted command includes `schemaVersion`.
  - Backward-compatible additions increment minor version.
  - Breaking field/validation changes increment major version and require local migration.
- Config/version compatibility:
  - `AppConfig.minSupportedSchemaVersion` gates startup if local DB schema is too old.

## Error model

All local service operations return either success payload or a structured error:

```json
{
  "errorCode": "ORDER_INVALID_STATE_TRANSITION",
  "message": "Cannot complete order from PENDING_MATCH state.",
  "details": {
    "orderId": "ORD-20260327-00012",
    "currentState": "PENDING_MATCH",
    "requestedAction": "COMPLETE"
  },
  "retryable": false
}
```

Standard error codes:

- `VALIDATION_ERROR`
- `NOT_FOUND`
- `UNAUTHORIZED`
- `ORDER_INVALID_STATE_TRANSITION`
- `ORDER_DISPUTE_WINDOW_EXPIRED`
- `ORDER_TIMEOUT_ALREADY_PROCESSED`
- `PRICING_RULE_VIOLATION`
- `COUPON_INELIGIBLE`
- `SUBSIDY_CAP_EXCEEDED`
- `ATTACHMENT_TYPE_NOT_ALLOWED`
- `ATTACHMENT_TOO_LARGE`
- `ATTACHMENT_FORMAT_INVALID`
- `SHARE_TOKEN_EXPIRED`
- `SHARE_TOKEN_MACHINE_MISMATCH`
- `DB_CONFLICT_IDEMPOTENCY_KEY`
- `UPDATE_SIGNATURE_INVALID`

## Domain enums

```text
Role = DISPATCHER | FINANCE_CLERK | ADMIN
OrderState = PENDING_MATCH | ACCEPTED | IN_PROGRESS | COMPLETED | CANCELED | IN_DISPUTE
TenderType = CASH | CARD_ON_FILE | CHECK
CouponType = PERCENT | FIXED
Priority = NORMAL | PRIORITY
VehicleType = SEDAN | SUV | VAN | ACCESSIBLE
```

## Core DTO schemas

### CreateOrderRequest

```json
{
  "customerId": "CUS-00123",
  "pickupAddress": "100 Main St",
  "dropoffAddress": "200 Oak Ave",
  "riderCount": 3,
  "timeWindowStart": "2026-03-27T14:00:00",
  "timeWindowEnd": "2026-03-27T15:00:00",
  "scheduledPickupAt": "2026-03-27T14:20:00",
  "vehicleType": "SUV",
  "priority": "PRIORITY",
  "pickupFloor": 5,
  "dropoffFloor": 2,
  "pickupNotes": "Use side entrance",
  "dropoffNotes": "Call on arrival",
  "idempotencyKey": "f38a2a9d-a95a-4c61-b6f1-aa4f89df80e6"
}
```

Validation:

- `riderCount`: integer between 1 and 6.
- `timeWindowStart < timeWindowEnd`.
- `scheduledPickupAt` must be within time window.
- `pickupFloor` and `dropoffFloor` minimum 0.
- `idempotencyKey` required for write commands.

### TransitionOrderStateRequest

```json
{
  "orderId": "ORD-20260327-00012",
  "action": "ACCEPT",
  "reason": null,
  "occurredAt": "2026-03-27T14:05:00",
  "idempotencyKey": "5e9e5928-87d8-4e0a-9a6b-e0d18fc2e506"
}
```

Allowed actions and mappings:

- `ACCEPT`: `PENDING_MATCH -> ACCEPTED`
- `START_TRIP`: `ACCEPTED -> IN_PROGRESS`
- `COMPLETE`: `IN_PROGRESS -> COMPLETED`
- `CANCEL`: `PENDING_MATCH|ACCEPTED|IN_PROGRESS -> CANCELED`
- `OPEN_DISPUTE`: `COMPLETED -> IN_DISPUTE` only if `occurredAt <= completedAt + 7 days`

### PricingInput

```json
{
  "orderId": "ORD-20260327-00012",
  "distanceMiles": 12.5,
  "durationMinutes": 31,
  "priority": "PRIORITY",
  "pickupFloor": 5,
  "dropoffFloor": 2,
  "couponCode": "SPRING15",
  "customerId": "CUS-00123"
}
```

### PricingResult

```json
{
  "baseFare": 3.5,
  "distanceComponent": 22.5,
  "timeComponent": 10.85,
  "priorityMultiplier": 1.25,
  "floorSurcharge": 2.0,
  "couponDiscount": 7.37,
  "subsidyApplied": 10.0,
  "totalFare": 33.17,
  "currency": "USD"
}
```

Rules:

- Base formula: `3.50 + (1.80 * miles) + (0.35 * minutes)`.
- Priority multiplier: `1.25` if priority.
- Floor surcharge: `$1.00` for each floor above 3 (pickup and drop-off independently, then summed).
- Coupon:
  - `PERCENT` max 20%.
  - `FIXED` amount requires order total meeting coupon minimum threshold.
- Subsidy cannot exceed remaining monthly cap for customer (default cap 50.00).

### RecordPaymentRequest

```json
{
  "orderId": "ORD-20260327-00012",
  "entryType": "DEPOSIT",
  "tenderType": "CASH",
  "amount": 6.63,
  "reference": "front-desk-receipt-488",
  "postedAt": "2026-03-27T14:10:00",
  "idempotencyKey": "d26fe0c3-7b74-4a68-b4a1-c934987fa6f4"
}
```

Rules:

- Deposit default target is 20% of computed fare (configurable).
- `entryType` in `DEPOSIT | FINAL_PAYMENT | REFUND | FEE | ADJUSTMENT`.
- Refund must not exceed paid amount minus prior refunds.

### AttachmentUploadRequest

```json
{
  "ownerType": "ORDER",
  "ownerId": "ORD-20260327-00012",
  "fileName": "waiver-signed.png",
  "sourcePath": "C:\\temp\\waiver-signed.png",
  "uploadedBy": "USR-0009"
}
```

Validation:

- Allowlist extensions/types: PDF, JPG, PNG.
- Max file size: 20 MB.
- Content header must match expected format.
- SHA-256 fingerprint is required and persisted.

### CreateShareTokenRequest

```json
{
  "attachmentId": "ATT-77881",
  "ttlHours": 24,
  "requestedBy": "USR-0002"
}
```

Rules:

- Default `ttlHours = 24`.
- Token resolution requires same machine fingerprint.
- Expired tokens are invalid and non-renewable (new token required).

## Local service contracts

### OrderService

```java
OrderView createOrder(CreateOrderRequest request);
OrderView transitionOrder(TransitionOrderStateRequest request);
OrderView getOrderById(String orderId);
Page<OrderView> searchOrders(OrderSearchQuery query);
int autoCancelPendingOrders(Instant now); // scheduler hourly
```

### PricingService

```java
PricingResult calculate(PricingInput input);
PricingResult recalculateForOrder(String orderId, String reason);
```

### SettlementService

```java
LedgerEntryView recordEntry(RecordPaymentRequest request);
LedgerSummary getOrderSettlement(String orderId);
RefundResult issuePartialRefund(String orderId, BigDecimal amount, String reason, String idempotencyKey);
CsvExportResult exportReconciliation(LocalDate from, LocalDate to, Path outputPath);
```

### AttachmentService

```java
AttachmentView attach(AttachmentUploadRequest request);
ShareTokenView createShareToken(CreateShareTokenRequest request);
ResolvedAttachment resolveShareToken(String token, String machineFingerprint);
```

### AdminConfigService

```java
ConfigSnapshot getConfig();
ConfigSnapshot updateConfig(UpdateConfigRequest request, String idempotencyKey);
TemplateView upsertTemplate(TemplateRequest request);
DictionaryView upsertDictionary(DictionaryRequest request);
```

### SchedulerService

```java
JobRunResult runHourlyTimeoutEnforcement(Instant runAt);
JobRunResult runNightlyOverdueAndFeeCalculation(LocalDate businessDate);
JobRunResult runQuotaReclamation(LocalDate businessDate);
```

### UpdateService

```java
UpdateValidationResult validatePackage(Path signedPackagePath);
UpdateApplyResult applyPackage(Path signedPackagePath, boolean createRollbackSnapshot);
RollbackResult rollbackLastAppliedUpdate();
```

## Event contracts

Persisted event envelope:

```json
{
  "eventId": "EVT-019992",
  "eventType": "ORDER_STATE_CHANGED",
  "entityType": "ORDER",
  "entityId": "ORD-20260327-00012",
  "occurredAt": "2026-03-27T14:05:00",
  "actorUserId": "USR-0009",
  "payload": {},
  "schemaVersion": "1.0.0"
}
```

Event types:

- `ORDER_CREATED`
- `ORDER_STATE_CHANGED`
- `ORDER_AUTO_CANCELED`
- `DISPUTE_OPENED`
- `PRICING_COMPUTED`
- `LEDGER_ENTRY_POSTED`
- `REFUND_ISSUED`
- `ATTACHMENT_STORED`
- `SHARE_TOKEN_CREATED`
- `JOB_RUN_STARTED`
- `JOB_RUN_COMPLETED`
- `APP_LOCKED`
- `APP_UNLOCKED`
- `UPDATE_IMPORTED`
- `UPDATE_ROLLED_BACK`

## Scheduler contracts and idempotency

- Hourly timeout enforcement:
  - Input: current timestamp.
  - Effect: auto-cancel `PENDING_MATCH` orders older than 15 minutes since creation.
  - Idempotency key pattern: `job:timeout:<YYYY-MM-DDTHH>`.
- Nightly overdue/fee calculation:
  - Computes overdue balances and applicable fee postings.
  - Idempotency key pattern: `job:nightly:<YYYY-MM-DD>`.
- Quota reclamation:
  - Resets or reclaims monthly subsidy usage where policy requires.
  - Idempotency key pattern: `job:quota:<YYYY-MM-DD>`.

## Reconciliation CSV contract

File: UTF-8 CSV with header row.

Columns:

```text
order_id,customer_id,entry_type,tender_type,amount,currency,posted_at,reference,operator_user_id
```

Example row:

```text
ORD-20260327-00012,CUS-00123,DEPOSIT,CASH,6.63,USD,2026-03-27T14:10:00,front-desk-receipt-488,USR-0009
```

## Representative flow examples

### Example A: Priority trip with coupon and subsidy

1. `createOrder` with priority, pickup floor 5, drop-off floor 2.
2. `calculate` pricing:
   - base + distance + time, then priority multiplier, then floor surcharge.
   - apply `SPRING15` (15% <= 20% cap), then subsidy up to customer monthly remaining cap.
3. `recordEntry` for 20% deposit.
4. On completion, `recordEntry` final payment.

### Example B: Timeout auto-cancel and fee

1. Order remains in `PENDING_MATCH` for >15 minutes.
2. Hourly job calls `autoCancelPendingOrders`.
3. If cancellation timestamp is within 10 minutes of scheduled pickup, post `$5.00` fee entry.

### Example C: Dispute opening boundary

1. Order completed at `2026-03-27T16:10:00`.
2. `OPEN_DISPUTE` allowed until `2026-04-03T16:10:00` inclusive.
3. Requests after boundary return `ORDER_DISPUTE_WINDOW_EXPIRED`.
