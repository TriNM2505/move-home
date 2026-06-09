# Feature Specification: Admin Commission Settings (Pricing Configuration)

**Feature Branch:** `014-admin-commission-settings`
**Feature Number:** #14 of 30 — SUPPORT (system configuration)
**Created:** 2026-06-04
**Version:** 1.0.0
**Status:** Draft
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §23 Pricing configuration, §4 Pricing formula
**Constitution reference:** v1.3.0 — HR-10 (Admin RBAC), HR-13 (audit critical
for money config), HR-19/20, AC-08 (BigDecimal VND), AC-14 (VARCHAR+CHECK), AC-16

---

## Goals

Admin cần một trang cấu hình tập trung để điều chỉnh các tham số pricing và
financial policy của Move_home theo điều kiện thị trường mà không phải chờ một
release code mới. Trang cho phép xem và thay đổi commission, phụ phí giờ cao
điểm, các khung giờ cao điểm, tỷ lệ phụ phí ngõ nhỏ, tier phụ phí tầng, đơn giá
theo kilomet, phí bốc xếp theo loại xe, mức đặt cọc Driver và số tiền rút tối
thiểu.

Mỗi thay đổi là money-critical nên phải được validate chặt chẽ, hiển thị diff,
yêu cầu xác nhận, ghi history snapshot và audit log trong cùng transaction.
Optimistic locking ngăn hai Admin vô tình ghi đè cấu hình của nhau. Email thông
báo cho các Admin khác được gửi bất đồng bộ và không được rollback thay đổi đã
commit.

Critical invariant là backward compatibility: cấu hình mới chỉ được pricing
engine, onboarding và withdrawal flow đọc cho thao tác mới sau thời điểm save.
Booking draft đã quote giữ pricing snapshot trong thời hạn quote; order đã tạo
giữ toàn bộ snapshot giá và không bao giờ bị tính lại. Deposit attempt đã tạo và
withdrawal request đã tạo giữ amount/minimum snapshot theo policy tại thời điểm
tạo.

Mục tiêu là giúp Move_home phản ứng nhanh với biến động chi phí, vẫn bảo đảm báo
giá deterministic, audit-grade và giải thích được. Admin phải nhìn thấy ai thay
đổi gì, khi nào, từ giá trị nào sang giá trị nào và tác động dự kiến trên các
sample order trước khi quyết định lưu.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec 002/005/007 →
> spec này → `SCREEN_INVENTORY.md`/UI stub → Code.

| Chủ đề | Quyết định canonical | Hệ quả |
|---|---|---|
| Pricing formula | Giữ formula shape của Spec 002 | Admin chỉ đổi tham số, không đổi thuật toán |
| Base rate defaults | `20.000/30.000/40.000` VND/km | `15.000/20.000/25.000` trong prompt là proposal legacy |
| Alley surcharge | `base_fare × alley_rate`, mặc định `20%` | Không dùng fixed `200.000 VND` |
| Floor surcharge | Tier rate `0/20/30/50%` theo highest effective floor | Không dùng fixed `50.000/tầng` hoặc threshold đơn |
| Porter fee | Theo vehicle: `150k/200k/300k` mỗi người | Không dùng một fixed rate toàn hệ thống |
| Peak surcharge | `base_fare × peak_rate`, mặc định `30%` | Khung giờ `[start,end)` |
| Commission | Mặc định `30%` | Snapshot vào quote/order |
| Driver deposit | Mặc định `3.000.000 VND` | Config mới chỉ áp dụng deposit attempt mới |
| Minimum withdrawal | Mặc định `100.000 VND` | Config mới chỉ áp dụng request mới |
| Effective behavior | Versioned configuration | Không update order/deposit/withdrawal lịch sử |
| Rollback | Deferred | History read-only trong Sprint 5 |

Spec này mở rộng Spec 005 và Spec 007 theo hướng thay server constant bằng policy
versioned cho các workflow mới. Constraint cố định trong schema cũ phải được
migrate an toàn trước khi bật chức năng update deposit/minimum withdrawal.

---

## Scope Summary

**In scope:**

1. `GET /api/admin/settings/commission` — lấy cấu hình hiện hành.
2. `PATCH /api/admin/settings/commission` — cập nhật partial settings.
3. `GET /api/admin/settings/commission/history` — lịch sử thay đổi.
4. `POST /api/admin/settings/commission/preview` — preview 5 sample orders.
5. Strict validation và cross-field sanity checks.
6. Confirmation modal hiển thị diff trước khi save.
7. Optimistic locking bằng `version`.
8. Atomic update, history, audit và outbox email.
9. Admin-only RBAC.
10. Pricing/deposit/withdrawal snapshot compatibility.
11. Loading, empty và error states.

**Out of scope:**

1. A/B testing pricing.
2. Geographic pricing zones.
3. Dynamic pricing AI.
4. Promotion codes.
5. Tier-based commission theo Driver.
6. Rollback thực thi.
7. Monthly revenue simulation từ dữ liệu lịch sử.
8. Thay đổi pricing formula shape.
9. Retroactive reprice order.
10. Retroactive change deposit hoặc withdrawal request.

---

## User Stories

**P1:**

- **US1:** As an Admin, I view current settings với version, người và thời điểm
  cập nhật gần nhất.
- **US2:** As an Admin, I edit commission rate và xem tác động trên sample order.
- **US3:** As an Admin, I edit danh sách peak-hour ranges không overlap.
- **US4:** As an Admin, I edit canonical alley, floor và porter surcharge rates.
- **US5:** As an Admin, I edit base rate per kilomet cho từng loại xe.
- **US6:** As an Admin, I review highlighted diff trong confirmation modal trước
  khi save.
- **US7:** As an Admin, I view paginated history để truy vết mọi thay đổi.

**P2:**

- **US8:** As an Admin, I rollback một historical version → defer Sprint 6+.
- **US9:** As an Admin, I simulate revenue impact trên dữ liệu tháng trước →
  defer.

---

## Functional Requirements

> EARS notation: WHEN | WHILE | WHERE | IF/THEN

### Nhóm 1 — Get Current Settings (FR-001..FR-005)

**FR-001 — Current settings endpoint**

WHEN an authenticated Admin calls `GET /api/admin/settings/commission`,
THE SYSTEM SHALL return the single active settings version with HTTP `200`.

The response SHALL include:

```json
{
  "version": 7,
  "commission_rate": "0.3000",
  "peak_surcharge_rate": "0.3000",
  "peak_hours": [
    {"start": "07:00", "end": "09:00"},
    {"start": "17:00", "end": "19:00"}
  ],
  "alley_surcharge_rate": "0.2000",
  "floor_surcharge_tiers": [
    {"min_floor": 2, "max_floor": 3, "rate": "0.2000"},
    {"min_floor": 4, "max_floor": 5, "rate": "0.3000"},
    {"min_floor": 6, "max_floor": 30, "rate": "0.5000"}
  ],
  "base_rate_per_km": {
    "TRUCK_500KG": 20000,
    "TRUCK_1T": 30000,
    "TRUCK_15T": 40000
  },
  "porter_fee_per_person": {
    "TRUCK_500KG": 150000,
    "TRUCK_1T": 200000,
    "TRUCK_15T": 300000
  },
  "driver_deposit_vnd": 3000000,
  "min_withdrawal_vnd": 100000,
  "last_updated_at": "2026-06-04T10:30:00Z",
  "last_updated_by": {"id": "uuid", "full_name": "Admin Move_home"}
}
```

**FR-002 — Admin-only read**

WHERE the current settings endpoint is accessed,
THE SYSTEM SHALL require role `ADMIN`.

IF the caller is authenticated without role `ADMIN`,
THEN THE SYSTEM SHALL return HTTP `403`.

IF no valid authentication exists,
THEN THE SYSTEM SHALL return HTTP `401`.

**FR-003 — Form rendering**

WHEN current settings load successfully,
THE frontend SHALL render editable controls grouped into:

- Hoa hồng và giờ cao điểm.
- Phụ phí.
- Giá theo loại xe.
- Chính sách Driver.
- Metadata version và cập nhật gần nhất.

All money SHALL display as VND integer with dot separators.

All percentage values SHALL display as percent while preserving four-decimal
rate precision in API payloads.

**FR-004 — Settings availability failure**

WHERE the active settings row is missing or invalid,
THE SYSTEM SHALL fail closed with HTTP `503 SETTINGS_UNAVAILABLE`.

The pricing engine SHALL NOT silently replace missing production settings with
frontend values.

The frontend SHALL display `Không thể tải cấu hình hiện hành` and a retry
action.

**FR-005 — Dirty-form protection**

WHILE the Admin has unsaved changes,
THE frontend SHALL mark the form as changed, enable preview/save actions and
warn before navigation away.

WHEN the form is reset or saved successfully,
THE frontend SHALL clear the dirty state.

---

### Nhóm 2 — Update Settings (FR-006..FR-014)

**FR-006 — Partial update endpoint**

WHEN an authenticated Admin submits
`PATCH /api/admin/settings/commission`,
THE SYSTEM SHALL accept a partial update body containing `version`, optional
`note` and one or more supported setting fields.

Fields not supplied SHALL retain current values.

An empty patch SHALL return HTTP `422 NO_SETTINGS_CHANGED`.

**FR-007 — Rate validation**

WHEN rate fields are supplied,
THE SYSTEM SHALL enforce:

- `commission_rate`: `0.0500..0.5000`.
- `peak_surcharge_rate`: `0.0000..1.0000`.
- `alley_surcharge_rate`: `0.0000..1.0000`.
- Every floor tier rate: `0.0000..1.0000`.
- Maximum four decimal places.

WHERE a rate is invalid,
THE SYSTEM SHALL return HTTP `422` with field-specific details.

**FR-008 — Peak-hour validation**

WHEN `peak_hours` is supplied,
THE SYSTEM SHALL require an array of `0..6` ranges using `HH:mm`.

Each range SHALL:

- Use minute values `00..59`.
- Have `start < end`.
- Use `[start,end)` semantics.
- Stay within one local calendar day.
- Not overlap or duplicate another range.

Adjacent ranges MAY be normalized into one range.

**FR-009 — Vehicle pricing validation**

WHEN `base_rate_per_km` or `porter_fee_per_person` is supplied,
THE SYSTEM SHALL require exactly the keys:

- `TRUCK_500KG`.
- `TRUCK_1T`.
- `TRUCK_15T`.

Each base rate SHALL be integer VND `5.000..100.000`.

Each porter fee SHALL be integer VND `0..2.000.000`.

Unknown or missing vehicle keys SHALL return HTTP `422`.

**FR-010 — Floor-tier validation**

WHEN `floor_surcharge_tiers` is supplied,
THE SYSTEM SHALL require ordered, non-overlapping ranges within floors `0..30`.

The tiers SHALL preserve:

- Floor `0..1` has no surcharge.
- Elevator makes effective floor zero.
- Every floor from `2..30` maps to exactly one tier.

WHERE any floor is uncovered or covered twice,
THE SYSTEM SHALL return HTTP `422 INVALID_FLOOR_TIERS`.

**FR-011 — Financial policy validation**

WHEN Driver policy values are supplied,
THE SYSTEM SHALL enforce:

- `driver_deposit_vnd`: integer VND `0..50.000.000`.
- `min_withdrawal_vnd`: integer VND `50.000..1.000.000`.
- `note`: optional, trimmed, maximum `1.000` Unicode characters.

The system SHALL warn, but not reject, when the new minimum withdrawal exceeds
some current Driver balances because it only affects new requests.

**FR-012 — Atomic update transaction**

WHEN a valid update with matching version is confirmed,
THE SYSTEM SHALL execute atomically:

```text
BEGIN
  lock commission_settings row
  verify version
  calculate full old/new snapshots and diff
  update commission_settings and increment version
  insert commission_settings_history
  insert audit_log SETTINGS_UPDATED
  insert admin-email outbox event
COMMIT
```

IF any mandatory database write fails,
THEN THE SYSTEM SHALL rollback every settings, history, audit and outbox write.

**FR-013 — Update response**

WHEN an update commits,
THE SYSTEM SHALL return HTTP `200`:

```json
{
  "message": "Đã cập nhật cấu hình",
  "new_settings": {},
  "effective_from": "2026-06-04T10:45:00Z",
  "version": 8
}
```

The frontend SHALL show a success toast and reload the authoritative settings.

It SHALL NOT redirect away from the page automatically.

**FR-014 — Snapshot application boundary**

WHEN a settings update commits,
THE SYSTEM SHALL apply version `N+1` only to:

- Quotes calculated after `effective_from`.
- Deposit attempts initiated after `effective_from`.
- Withdrawal requests submitted after `effective_from`.

Existing quote snapshots, orders, deposit attempts and withdrawal requests
SHALL remain unchanged.

---

### Nhóm 3 — Settings History (FR-015..FR-019)

**FR-015 — History endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/settings/commission/history?page=0&size=20&date_from=&date_to=`,
THE SYSTEM SHALL return a Spring Page sorted by
`changed_at DESC, id DESC`.

Page size SHALL support `10|20|50|100` and SHALL not exceed `100`.

**FR-016 — History item contract**

WHEN a history row is serialized,
THE SYSTEM SHALL include:

```json
{
  "id": "uuid",
  "from_version": 7,
  "to_version": 8,
  "changed_by": {"id": "uuid", "name": "Admin Move_home"},
  "old_values": {},
  "new_values": {},
  "diff": [
    {"field": "commission_rate", "from": "0.3000", "to": "0.2800"}
  ],
  "note": "Điều chỉnh theo chính sách tháng 6",
  "changed_at": "2026-06-04T10:45:00Z"
}
```

Snapshots SHALL be immutable.

**FR-017 — History filters**

WHEN date filters are supplied,
THE SYSTEM SHALL validate and filter `changed_at` using UTC boundaries derived
from `Asia/Ho_Chi_Minh`.

WHERE `date_from > date_to` or range exceeds `366` days,
THE SYSTEM SHALL return HTTP `422 INVALID_DATE_RANGE`.

**FR-018 — History presentation**

WHEN history loads,
THE frontend SHALL show a timeline with changed Admin, timestamp, note, version
transition and highlighted field-level diff.

Money and percentage fields SHALL use localized formatting.

Unchanged fields SHALL remain collapsed by default.

**FR-019 — History UI states**

WHILE history is loading,
THE frontend SHALL display a skeleton.

IF no history rows exist,
THEN THE frontend SHALL display `Chưa có thay đổi nào`.

WHERE history loading fails,
THE frontend SHALL display a local error and retry without clearing the edit
form.

---

### Nhóm 4 — Preview Impact (FR-020..FR-023)

**FR-020 — Preview endpoint**

WHEN an authenticated Admin calls
`POST /api/admin/settings/commission/preview` with proposed partial settings
and current `version`,
THE SYSTEM SHALL validate and merge the proposal without persisting it.

Preview SHALL use the same validation and pricing calculator as PATCH.

**FR-021 — Sample orders**

WHEN preview is valid,
THE SYSTEM SHALL calculate old and proposed totals for five deterministic
samples covering:

1. 10 km off-peak with no surcharge.
2. 10 km peak.
3. 10 km with alley.
4. 10 km with floor tier and no elevator.
5. 10 km with two porters.

Samples SHALL cover all three vehicle types across the set.

**FR-022 — Preview response**

WHEN preview calculation completes,
THE SYSTEM SHALL return:

```json
{
  "base_version": 7,
  "samples": [
    {
      "description": "10 km, xe 1 tấn, ngoài giờ cao điểm",
      "old_total": 300000,
      "new_total": 320000,
      "diff": 20000,
      "change_percent": "6.67"
    }
  ],
  "warnings": [],
  "persisted": false
}
```

The endpoint SHALL NOT claim monthly revenue impact because that simulation is
deferred.

**FR-023 — Preview presentation**

WHEN preview data loads,
THE frontend SHALL display an old/new/diff comparison table and warnings.

WHERE preview validation fails,
THE frontend SHALL map errors to the same form fields used by PATCH.

Preview SHALL never enable save when the current form is invalid.

---

### Nhóm 5 — Confirmation + Rollback Stub (FR-024..FR-028)

**FR-024 — Confirmation modal**

WHEN an Admin selects `Lưu cấu hình` on a valid dirty form,
THE frontend SHALL open `Bạn có chắc muốn thay đổi?` modal before PATCH.

The modal SHALL show:

- Every changed field.
- Old and proposed values.
- Effective-only-for-new-workflows warning.
- Admin note.
- Current version.

**FR-025 — Confirm and cancel**

WHEN the Admin selects confirm,
THE frontend SHALL submit exactly one PATCH request and disable modal actions
until response.

WHEN the Admin selects cancel,
THE frontend SHALL close the modal without changing the form or database.

**FR-026 — Confirmation accessibility**

WHILE the modal is open,
THE frontend SHALL trap keyboard focus, support Escape for cancel and provide
Vietnamese accessible labels.

The destructive-impact confirmation action SHALL use a clear warning style,
not rely only on color.

**FR-027 — Rollback stub**

WHEN history rows render,
THE frontend SHALL show `Khôi phục cấu hình này` as disabled with
`Sắp ra mắt`.

IF a caller attempts an undeclared rollback endpoint,
THEN THE SYSTEM SHALL return HTTP `404` or `405` and SHALL NOT change settings.

**FR-028 — Save failure handling**

WHERE PATCH returns validation, conflict or server error,
THE frontend SHALL keep unsaved values, close or update the modal safely and
display a Vietnamese actionable error.

The frontend SHALL not show success or clear dirty state until HTTP `200`.

---

### Nhóm 6 — Audit + Notifications (FR-029..FR-031)

**FR-029 — Critical audit**

WHEN settings are updated,
THE SYSTEM SHALL insert immutable audit event `SETTINGS_UPDATED` containing:

- Admin ID and role.
- Old and new version.
- Full normalized diff.
- Admin note.
- Timestamp UTC.
- Correlation ID.

Audit SHALL not store authentication secrets.

**FR-030 — Admin email notification**

WHEN an update commits,
THE SYSTEM SHALL asynchronously email all active Admin accounts except or
including the actor according to notification preference.

The email SHALL state who changed settings, when, version and a human-readable
diff.

Email failure SHALL not rollback the update and SHALL be retried.

**FR-031 — Webhook deferred**

WHEN the frontend or backend represents external monitoring webhook,
THE SYSTEM SHALL mark it deferred to Sprint 6+.

No webhook secret or outbound call SHALL be introduced by this feature.

---

### Nhóm 7 — RBAC + Concurrency (FR-032..FR-034)

**FR-032 — Admin RBAC**

WHERE any Spec 014 endpoint is accessed,
THE SYSTEM SHALL require authenticated role `ADMIN` in the backend.

Authenticated non-Admin access SHALL return HTTP `403` and be security-audited.

Frontend route guards SHALL not replace backend authorization.

**FR-033 — Optimistic locking**

WHEN PATCH or preview receives `version`,
THE SYSTEM SHALL compare it with the active settings version.

IF PATCH version mismatches,
THEN THE SYSTEM SHALL return HTTP `409 SETTINGS_VERSION_CONFLICT` with message
`Cấu hình đã thay đổi từ Admin khác, vui lòng tải lại`.

No update, history, audit-update or notification event SHALL be created.

**FR-034 — Concurrent quote consistency**

WHEN a quote calculation overlaps a settings update,
THE SYSTEM SHALL read one complete committed settings version and snapshot that
version with all pricing fields.

The quote SHALL never combine fields from two settings versions.

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-001 | GET current settings p95 dưới `200ms` |
| NFR-002 | PATCH settings p95 dưới `1s`, không tính email async |
| NFR-003 | History page p95 dưới `500ms` |
| NFR-004 | Preview 5 sample orders p95 dưới `1s` |
| NFR-005 | Audit log bắt buộc và atomic với update |
| NFR-006 | Email notification async theo HR-11 |
| NFR-007 | Strict validation trước khi save |
| NFR-008 | Pricing snapshot backward compatibility theo Spec 002 |
| NFR-009 | Money dùng BigDecimal/NUMERIC scale zero |
| NFR-010 | Rate dùng BigDecimal/NUMERIC, không Float |
| NFR-011 | UI có Loading/Empty/Error theo AC-16 |
| NFR-012 | Mọi timestamp lưu UTC, hiển thị giờ Việt Nam |

---

## API Endpoints Summary

| Method | Endpoint | Purpose | Auth |
|---|---|---|---|
| GET | `/api/admin/settings/commission` | Current settings | Admin |
| PATCH | `/api/admin/settings/commission` | Atomic partial update | Admin |
| GET | `/api/admin/settings/commission/history` | Paginated history | Admin |
| POST | `/api/admin/settings/commission/preview` | Non-persisting preview | Admin |

### Common Errors

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu hoặc invalid |
| 403 | `FORBIDDEN` | Không phải Admin |
| 409 | `SETTINGS_VERSION_CONFLICT` | Optimistic lock mismatch |
| 422 | `VALIDATION_ERROR` | Field/cross-field invalid |
| 422 | `NO_SETTINGS_CHANGED` | Empty/equivalent patch |
| 503 | `SETTINGS_UNAVAILABLE` | Current configuration unavailable |
| 500 | `AUDIT_WRITE_FAILED` | Critical audit không commit |

Errors SHALL follow ES-04:

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Cấu hình không hợp lệ.",
  "details": [
    {"field": "peak_hours[1]", "message": "Khung giờ bị trùng lặp."}
  ]
}
```

---

## Data Model

The single active row SHALL store one complete, internally consistent settings
version.

History SHALL store immutable full before/after snapshots.

```sql
CREATE TABLE commission_settings (
  id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  commission_rate NUMERIC(5,4) NOT NULL DEFAULT 0.3000
    CHECK (commission_rate BETWEEN 0.0500 AND 0.5000),
  peak_surcharge_rate NUMERIC(5,4) NOT NULL DEFAULT 0.3000
    CHECK (peak_surcharge_rate BETWEEN 0.0000 AND 1.0000),
  peak_hours JSONB NOT NULL DEFAULT
    '[{"start":"07:00","end":"09:00"},{"start":"17:00","end":"19:00"}]',
  alley_surcharge_rate NUMERIC(5,4) NOT NULL DEFAULT 0.2000
    CHECK (alley_surcharge_rate BETWEEN 0.0000 AND 1.0000),
  floor_surcharge_tiers JSONB NOT NULL DEFAULT
    '[{"min_floor":2,"max_floor":3,"rate":0.2000},
      {"min_floor":4,"max_floor":5,"rate":0.3000},
      {"min_floor":6,"max_floor":30,"rate":0.5000}]',
  base_rate_per_km JSONB NOT NULL DEFAULT
    '{"TRUCK_500KG":20000,"TRUCK_1T":30000,"TRUCK_15T":40000}',
  porter_fee_per_person JSONB NOT NULL DEFAULT
    '{"TRUCK_500KG":150000,"TRUCK_1T":200000,"TRUCK_15T":300000}',
  driver_deposit_vnd NUMERIC(15,0) NOT NULL DEFAULT 3000000
    CHECK (driver_deposit_vnd BETWEEN 0 AND 50000000),
  min_withdrawal_vnd NUMERIC(15,0) NOT NULL DEFAULT 100000
    CHECK (min_withdrawal_vnd BETWEEN 50000 AND 1000000),
  version BIGINT NOT NULL DEFAULT 1 CHECK (version > 0),
  last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_updated_by UUID REFERENCES app_user(id)
);

INSERT INTO commission_settings (id)
VALUES (1)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE commission_settings_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  from_version BIGINT NOT NULL,
  to_version BIGINT NOT NULL,
  changed_by UUID NOT NULL REFERENCES app_user(id),
  old_values JSONB NOT NULL,
  new_values JSONB NOT NULL,
  diff JSONB NOT NULL,
  note VARCHAR(1000),
  changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_settings_history_to_version UNIQUE (to_version)
);

CREATE INDEX idx_settings_history_changed_at
  ON commission_settings_history(changed_at DESC, id DESC);
```

### Migration Compatibility

1. Existing `pricing_config` from Spec 002 SHALL be migrated or replaced by a
   read adapter backed by the active settings row.
2. Booking calculator SHALL read one settings version and persist
   `pricing_settings_version_snapshot`.
3. Existing order pricing snapshot columns SHALL remain authoritative.
4. Fixed `driver_deposit.amount = 3000000` DB CHECK from Spec 005 SHALL be
   replaced with a valid-range check before configurable deposit is enabled.
5. Fixed `withdrawal_request.amount >= 100000` CHECK from Spec 007 cannot
   enforce a dynamic setting; service validation SHALL use the settings
   snapshot and DB SHALL retain a broad safety minimum.
6. All schema changes SHALL use Flyway.

### Snapshot Fields

New quote/order snapshots SHALL include:

- `pricing_settings_version_snapshot`.
- `commission_rate_snapshot`.
- `peak_rate_snapshot`.
- `peak_hours_snapshot`.
- `alley_rate_snapshot`.
- `floor_tiers_snapshot`.
- `rate_per_km_snapshot`.
- `porter_rate_snapshot`.

New deposit attempts SHALL include:

- `settings_version_snapshot`.
- `required_deposit_amount_snapshot`.

New withdrawal requests SHALL include:

- `settings_version_snapshot`.
- `min_withdrawal_amount_snapshot`.

---

## State Machine

Không có business state machine mới.

Settings dùng version transition bất biến:

```text
VERSION N active
  ↓ Admin validates + confirms PATCH(version=N)
VERSION N+1 active
  + immutable history N → N+1
  + immutable audit SETTINGS_UPDATED

Concurrent PATCH(version=N) after N+1 active
  → HTTP 409 SETTINGS_VERSION_CONFLICT
```

Pricing snapshot behavior:

```text
Quote created with settings N
  → quote/order continues using snapshot N

Settings N+1 saved
  → only newly calculated quote uses N+1
```

---

## Pricing Application Contract

The pricing calculator SHALL remain the canonical calculator from Spec 002:

```text
base_fare = distance_km × rate_per_km_snapshot

peak_surcharge =
  scheduled local time in peak_hours_snapshot
  ? base_fare × peak_rate_snapshot
  : 0

alley_surcharge =
  pickup_has_alley OR dropoff_has_alley
  ? base_fare × alley_rate_snapshot
  : 0

effective_floor = elevator ? 0 : floor
highest_effective_floor = MAX(pickup_effective_floor, dropoff_effective_floor)
floor_surcharge = base_fare × matching_floor_tier_rate_snapshot

porter_fee = porter_count × porter_rate_snapshot
total_quote = base + peak + alley + floor + porter
deposit_amount = CEILING(total_quote × commission_rate_snapshot)
```

Every money component SHALL round to integer VND exactly as Spec 002.

Settings SHALL not alter route distance, order input or rounding rules.

---

## Acceptance Criteria

**AC-01 — Current settings**

GIVEN an Admin opens the page,
WHEN current settings load,
THEN the canonical values, version and last-updated metadata render under
`300ms`.

**AC-02 — Strict validation**

GIVEN invalid rates, money bounds, vehicle keys, overlapping peak hours or
invalid floor tiers,
WHEN preview or PATCH runs,
THEN HTTP `422` returns every field-specific error and nothing persists.

**AC-03 — Atomic update and audit**

GIVEN a valid confirmed update,
WHEN PATCH succeeds,
THEN settings, version, history, audit and outbox event commit atomically.

**AC-04 — History**

GIVEN multiple updates,
WHEN Admin opens history and paginates,
THEN full snapshots and highlighted diffs appear newest first.

**AC-05 — Confirmation modal**

GIVEN a valid dirty form,
WHEN Admin chooses save,
THEN a Vietnamese confirmation modal shows every diff before one PATCH request.

**AC-06 — Email notification**

GIVEN an update commits,
WHEN email processing succeeds or fails,
THEN the update remains committed and all active Admins are notified or retried.

**AC-07 — Concurrent update**

GIVEN two Admins loaded version `7`,
WHEN one saves version `8` and the other then saves,
THEN the second receives HTTP `409` and cannot overwrite version `8`.

**AC-08 — RBAC**

GIVEN Manager, Driver, Customer and anonymous callers,
WHEN they call Spec 014 endpoints,
THEN authenticated non-Admins receive `403`, anonymous receives `401`.

**AC-09 — Snapshot integrity**

GIVEN order A quoted under version `7`,
WHEN Admin saves version `8` and order B is quoted,
THEN A retains every version-7 snapshot and B uses one complete version-8
snapshot.

**AC-10 — Vietnamese UX**

GIVEN all page states and errors,
WHEN rendered,
THEN user-facing text has full Vietnamese diacritics and money uses VND format.

---

## Edge Cases & Error Handling

| ID | Edge case | Required behavior |
|---|---|---|
| EC-01 | Hai Admin PATCH cùng version | Một success; request thua nhận `409` |
| EC-02 | Commission lớn hơn 50% | `422`, không persist |
| EC-03 | Peak ranges overlap | `422 INVALID_PEAK_HOURS` |
| EC-04 | Peak range chạm nhau | Normalize hoặc chấp nhận không overlap |
| EC-05 | Peak range qua nửa đêm | `422`; split thành hai ranges |
| EC-06 | Peak hours array rỗng | Hợp lệ, nghĩa là không có peak |
| EC-07 | Floor tier thiếu floor 6 | `422 INVALID_FLOOR_TIERS` |
| EC-08 | Floor tier rate bằng zero | Hợp lệ nếu range coverage đầy đủ |
| EC-09 | Vehicle pricing thiếu một key | `422` |
| EC-10 | Deposit đổi khi payment attempt đang pending | Pending attempt giữ snapshot cũ |
| EC-11 | Minimum withdrawal đổi khi request pending | Request cũ giữ policy snapshot |
| EC-12 | Settings đổi khi quote đang tính | Quote dùng một committed version duy nhất |
| EC-13 | Quote cũ quá freshness window | Requote dùng config active mới theo Spec 002 |
| EC-14 | Audit insert fail | Rollback toàn bộ PATCH |
| EC-15 | Email fail | Update commit, outbox retry |
| EC-16 | Active settings row bị thiếu | GET/PATCH fail closed `503` |
| EC-17 | Empty/equivalent patch | `422 NO_SETTINGS_CHANGED` |
| EC-18 | History date range invalid | `422 INVALID_DATE_RANGE` |
| EC-19 | Admin navigate away với dirty form | Hiển thị cảnh báo |
| EC-20 | Rollback button được click | Không gọi mutation endpoint |

---

## Test Cases

| ID | Test | Expected result |
|---|---|---|
| TC-01 | GET current as Admin | Canonical DTO, version và metadata đúng |
| TC-02 | GET/PATCH as non-Admin | `403`, không lộ settings |
| TC-03 | PATCH valid commission + base rates | Version tăng một; history/audit/outbox cùng commit |
| TC-04 | PATCH invalid bounds và overlapping peak | `422` với mọi field error |
| TC-05 | Hai concurrent PATCH version giống nhau | Một `200`, một `409`, không lost update |
| TC-06 | Preview valid proposal | 5 deterministic samples; `persisted=false` |
| TC-07 | Update sau khi order A đã quote | A giữ snapshot cũ; order B dùng config mới |
| TC-08 | Update deposit khi attempt đang pending | Attempt cũ giữ amount; attempt mới dùng amount mới |
| TC-09 | Update minimum withdrawal | Request cũ giữ snapshot; request mới validate policy mới |
| TC-10 | Audit/email failure paths | Audit fail rollback; email fail không rollback |
| TC-11 | History pagination/date filter | Stable newest-first Page và diff đúng |
| TC-12 | Frontend states/modal | Loading/error/dirty warning/diff confirmation hoạt động |

---

## Required Automated Test Layers

1. Unit tests cho rate, peak range, floor tier và money validation.
2. Unit tests cho canonical pricing calculator và five-sample preview.
3. PostgreSQL integration tests cho single-row lock, version và history unique.
4. Concurrency test cho two-Admin lost-update prevention.
5. Integration tests cho RBAC, atomic audit và outbox behavior.
6. Contract tests với quote/order snapshots của Spec 002.
7. Contract tests với deposit/withdrawal snapshots của Spec 005/007.
8. Frontend tests cho dirty form, modal, diff và AC-16 states.

---

## Security, Audit and Observability

1. Backend Admin RBAC bắt buộc cho cả bốn endpoints.
2. Settings update audit là critical và không được throttle.
3. History không cho update/delete từ application role.
4. Note được sanitize khi render; không dùng raw `innerHTML`.
5. Logs không chứa JWT, email body hoặc full settings snapshot khi không cần.
6. Metrics SHALL include GET/PATCH/preview latency and outcome.
7. Metrics SHALL count validation failures by safe field name.
8. Alert SHALL trigger khi audit failure hoặc conflict rate tăng bất thường.
9. Alert SHOULD trigger khi active settings unavailable.
10. Correlation ID SHALL link PATCH, history, audit and outbox event.

---

## Constitution Compliance

| Rule | Compliance |
|---|---|
| HR-10 | Chỉ Admin gọi endpoints; role khác `403` |
| HR-11 | Email async, failure không rollback |
| HR-13 | Money configuration update có immutable audit |
| HR-19 | Trang dùng Move_home forest green/amber |
| HR-20 | Toàn bộ UI text có dấu tiếng Việt |
| HR-21 | Tables không dùng reserved names |
| AC-08 | Money BigDecimal/NUMERIC scale zero |
| AC-12 | Schema thay đổi qua Flyway |
| AC-14 | Không tạo PostgreSQL ENUM |
| AC-15 | History dùng server-side pagination |
| AC-16 | Loading/Empty/Error mandatory |
| ES-03/04 | Validation HTTP 422 và structured error |

---

## Definition of Done

1. Bốn endpoints hoạt động theo contract.
2. Có đúng một active settings row với optimistic version.
3. Validation và cross-field checks pass.
4. Preview và PATCH dùng cùng calculator/validator.
5. Update, history, audit và outbox commit atomically.
6. Quote/order/deposit/withdrawal snapshot tests pass.
7. Không có retroactive pricing mutation.
8. History pagination và diff render đúng.
9. Rollback và revenue simulation vẫn disabled/deferred.
10. Vietnamese UX, VND formatting và AC-16 states hoàn chỉnh.

