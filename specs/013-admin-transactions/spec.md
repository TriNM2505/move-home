# Feature Specification: Admin System Transactions

**Feature Branch:** `013-admin-transactions`
**Feature Number:** #13 of 30 — CORE (financial oversight)
**Created:** 2026-06-04
**Version:** 1.0.0
**Status:** Draft
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §21 Financial oversight, §22 Transaction audit
**Constitution reference:** v1.3.0 — HR-10 (Admin RBAC), HR-13 (audit critical),
HR-19/20, AC-08 (BigDecimal VND), AC-14, AC-15, AC-16

---

## Goals

Admin cần một trang tập trung để quan sát toàn bộ luồng tài chính của Move_home
mà không phải ghép dữ liệu thủ công từ đơn hàng, ví tài xế, yêu cầu rút tiền,
khiếu nại và cổng thanh toán. Trang `admin/transactions.html` cung cấp danh sách
giao dịch bất biến, bộ lọc kết hợp, tìm kiếm, KPI theo kỳ, biểu đồ theo loại,
chi tiết liên kết và báo cáo đối soát.

Trang phải phân biệt rõ tiền đi qua cổng thanh toán, tiền ghi nhận trong ledger,
số dư ví tài xế, tiền đặt cọc và doanh thu phí nền tảng. Mọi số tiền dùng VND
nguyên, có dấu chấm phân cách khi hiển thị và không dùng phép tính floating-point.
Admin có thể truy vết từ một giao dịch đến order, withdrawal, dispute hoặc payment
liên quan, nhưng không thể sửa, xóa hay void giao dịch từ màn hình này.

Các KPI chính gồm tổng tiền vào đã xác nhận, tổng tiền ra đã xác nhận, doanh thu
phí nền tảng và tổng yêu cầu rút tiền đang chờ. Bộ lọc hỗ trợ loại giao dịch,
vai trò người dùng, trạng thái liên kết, khoảng ngày, khoảng tiền và từ khóa.
Báo cáo reconciliation kiểm tra từng invariant tài chính thay vì giả định rằng
một phép cộng inflow trừ outflow luôn bằng tổng số dư ví.

Mục tiêu là tạo nguồn quan sát audit-grade cho vận hành, điều tra sai lệch và
đối soát cuối kỳ. Kết quả phải khớp dữ liệu canonical trong database, bảo vệ
thông tin tham chiếu nhạy cảm, ghi audit cho mọi lần truy cập và vẫn đáp ứng
hiệu năng khi hệ thống đạt từ 100.000 đến hơn 1.000.000 giao dịch.

---

## Source-of-Truth Resolution

Spec này tuân theo các quyết định tài chính đã có trong Spec 004, 006, 007,
009 và 010.

1. Bảng ledger canonical là append-only `transaction`.
2. Customer không có customer wallet và không có luồng customer top-up trong
   phạm vi hiện tại của Spec 004.
3. `wallet_transaction` trong các mô tả legacy được hiểu là alias của projection
   đọc từ `transaction`, không tạo bảng ledger thứ hai.
4. Yêu cầu rút tiền `PENDING` không tạo `WITHDRAWAL_HOLD`.
5. Yêu cầu rút tiền bị từ chối hoặc hủy không tạo `WITHDRAWAL_REFUND`.
6. Chỉ withdrawal đã xử lý thành công mới tạo một transaction `WITHDRAWAL`.
7. `DISPUTE_DEDUCTION` là display alias của loại canonical
   `DAMAGE_DEDUCTION`.
8. `TOPUP` và `DEPOSIT` là display alias của `DEPOSIT_TOP_UP` dành cho tài xế.
9. `PAYMENT` là display alias của `ORDER_PAYMENT`.
10. `EARNINGS` là display alias của `DRIVER_EARNING`.
11. `ADJUSTMENT` được giữ ngoài enum canonical cho đến khi workflow phê duyệt
    điều chỉnh thủ công được đặc tả ở Sprint 6+.
12. `PLATFORM_FEE` là loại canonical bắt buộc để đối soát doanh thu commission.

### Canonical Transaction Types

| Canonical type | Nhãn tiếng Việt | Ý nghĩa | Dấu amount |
|---|---|---|---:|
| `DEPOSIT_TOP_UP` | Đặt cọc tài xế | Tài xế nộp thêm tiền cọc | Dương |
| `DEPOSIT_REFUND` | Hoàn cọc tài xế | Hoàn tiền cọc cho tài xế | Âm |
| `ORDER_PAYMENT` | Thanh toán đơn | Thanh toán đã xác nhận cho order | Dương |
| `DRIVER_EARNING` | Thu nhập tài xế | Ghi có thu nhập vào ví tài xế | Dương |
| `PLATFORM_FEE` | Phí nền tảng | Commission thuộc Move_home | Dương |
| `DAMAGE_DEDUCTION` | Khấu trừ khiếu nại | Khấu trừ do quyết định dispute | Âm |
| `REFUND` | Hoàn tiền | Hoàn tiền cho Customer qua phương thức gốc | Âm |
| `WITHDRAWAL` | Rút tiền | Tiền đã chuyển thành công cho tài xế | Âm |

### Display Alias Mapping

| Alias từ UI/spec legacy | Canonical type | Cách xử lý |
|---|---|---|
| `TOPUP` | `DEPOSIT_TOP_UP` | Chỉ áp dụng cho Driver deposit |
| `PAYMENT` | `ORDER_PAYMENT` | Chuẩn hóa khi đọc |
| `EARNINGS` | `DRIVER_EARNING` | Chuẩn hóa khi đọc |
| `DEPOSIT` | `DEPOSIT_TOP_UP` | Chuẩn hóa khi đọc |
| `DISPUTE_DEDUCTION` | `DAMAGE_DEDUCTION` | Chuẩn hóa khi đọc |
| `WITHDRAWAL_HOLD` | Không tồn tại | Lấy trạng thái từ withdrawal request |
| `WITHDRAWAL_REFUND` | Không tồn tại | Không phát sinh ledger row |
| `ADJUSTMENT` | Deferred | Trả lỗi filter không được hỗ trợ |

---

## Scope Summary

**In scope:**

1. `GET /api/admin/transactions` - danh sách transaction toàn hệ thống.
2. `GET /api/admin/transactions/summary` - KPI tổng hợp theo kỳ.
3. `GET /api/admin/transactions/by-type` - dữ liệu biểu đồ theo loại.
4. `GET /api/admin/transactions/{id}` - chi tiết một transaction.
5. `GET /api/admin/transactions/reconciliation` - báo cáo đối soát.
6. Pagination server-side với page size tối đa 100.
7. Filter theo type, user role, linked status, ngày và amount.
8. Search theo order code, email và reference liên quan.
9. Liên kết đến order, withdrawal, dispute và user detail.
10. RBAC chỉ dành cho Admin.
11. Audit log mọi lần truy cập, có throttle.
12. Query index và cache cho aggregation.
13. Trạng thái empty, loading và error đầy đủ.
14. Mask dữ liệu tài chính nhạy cảm trong response.

**Out of scope:**

1. Sửa, xóa hoặc void transaction.
2. Tạo Customer wallet hoặc Customer top-up.
3. Manual adjustment endpoint.
4. Workflow phê duyệt adjustment.
5. Bank statement integration.
6. Tax report generation.
7. Multi-currency; hệ thống chỉ dùng VND.
8. Export CSV thực thi; nút hiển thị disabled đến Sprint 6+.
9. Phát hiện anomaly tự động.
10. Tự động sửa discrepancy.
11. Partitioning transaction table; đánh giá lại ở Sprint 6+.
12. Realtime WebSocket; dùng polling 60 giây.

---

## User Stories

**P1:**

- **US1:** As an Admin, I view all system transactions với pagination và filter
  để monitor cashflow.
- **US2:** As an Admin, I filter by canonical transaction type để tập trung vào
  một luồng tiền cụ thể.
- **US3:** As an Admin, I see KPI cards cho tiền vào, tiền ra, platform fee và
  pending withdrawals.
- **US4:** As an Admin, I filter by date range để xem báo cáo theo tháng,
  quý hoặc khoảng tùy chỉnh.
- **US5:** As an Admin, I search by order code, user email hoặc reference để
  truy vết giao dịch.
- **US6:** As an Admin, I click một row để xem detail và mở entity liên quan.
- **US7:** As an Admin, I run reconciliation report để kiểm tra integrity của
  ledger và số dư.

**P2:**

- **US8:** As an Admin, I export filtered transactions to CSV → defer Sprint 6+.
- **US9:** As an Admin, I see anomaly alerts cho giao dịch bất thường → defer.

---

## Functional Requirements

> EARS notation: WHEN | WHILE | WHERE | IF/THEN

### Nhóm 1 — Transactions List (FR-001..FR-008)

**FR-001 — Paginated transactions endpoint**

WHEN an authenticated Admin requests
`GET /api/admin/transactions?page=0&size=20&type=ALL&user_role=ALL&linked_status=ALL&date_from=&date_to=&amount_min=&amount_max=&search=&sort=created_at,desc`,
THE SYSTEM SHALL return a Spring `Page<TransactionListItemDTO>` from the
canonical append-only `transaction` ledger.

The endpoint SHALL:

- Default `page` to `0`.
- Default `size` to `20`.
- Permit sizes `10`, `20`, `50` and `100`.
- Reject size greater than `100`.
- Return `totalElements`, `totalPages`, `number`, `size`, `first` and `last`.
- Use a deterministic secondary sort by `id DESC`.

**FR-002 — Admin-only list access**

WHERE a caller accesses the transactions list,
THE SYSTEM SHALL require authenticated role `ADMIN`.

IF the caller is unauthenticated,
THEN THE SYSTEM SHALL return HTTP `401`.

IF the caller is authenticated without role `ADMIN`,
THEN THE SYSTEM SHALL return HTTP `403`.

**FR-003 — Combined filters**

WHEN an Admin supplies one or more list filters,
THE SYSTEM SHALL combine all supplied filters with logical `AND`.

Supported filters SHALL be:

- `type`: `ALL` or one canonical transaction type.
- `user_role`: `ALL`, `CUSTOMER`, `DRIVER` or `SYSTEM`.
- `linked_status`: `ALL`, `COMMITTED`, `PENDING_EXTERNAL`,
  `FAILED_EXTERNAL` or `CANCELLED_EXTERNAL`.
- `date_from` and `date_to` over `transaction.created_at`.
- `amount_min` and `amount_max` over absolute amount.
- `search` over supported search fields.

`linked_status` SHALL be derived from linked payment or withdrawal entities.
It SHALL NOT be persisted as a transaction lifecycle state.

**FR-004 — Search behavior**

WHEN an Admin enters a non-blank search term,
THE SYSTEM SHALL perform a case-insensitive, parameterized search against:

- `service_order.order_code`.
- `app_user.email`.
- `payment_transaction.vnp_txn_ref`.
- `withdrawal_request.bank_txn_ref`.
- Exact transaction UUID.

The frontend SHALL debounce input by `300ms`.

The backend SHALL trim leading and trailing whitespace.

The backend SHALL treat `%`, `_`, quotes and backslashes as literal characters
unless escaped by the query builder.

**FR-005 — Sort behavior**

WHEN an Admin requests sorting,
THE SYSTEM SHALL allow only:

- `created_at`.
- `amount`.
- `type`.

IF sort is missing,
THEN THE SYSTEM SHALL use `created_at DESC, id DESC`.

IF an unsupported sort field is supplied,
THEN THE SYSTEM SHALL return HTTP `400` with code `INVALID_SORT_FIELD`.

**FR-006 — Transaction list DTO**

WHEN the list endpoint returns a row,
THE SYSTEM SHALL include:

```json
{
  "id": "uuid",
  "type": "ORDER_PAYMENT",
  "type_label": "Thanh toán đơn",
  "amount": 1250000,
  "balance_after": null,
  "user_id": "uuid-or-null",
  "user_name": "Nguyễn Văn A",
  "user_role": "CUSTOMER",
  "user_email": "n***@example.com",
  "related_order_id": "uuid-or-null",
  "order_code": "MH-20260604-001",
  "related_withdrawal_id": null,
  "related_dispute_id": null,
  "vnpay_txn_ref_masked": "****4821",
  "bank_txn_ref_masked": null,
  "linked_status": "COMMITTED",
  "description": "Thanh toán đơn MH-20260604-001",
  "created_at": "2026-06-04T08:30:00Z"
}
```

`balance_after` SHALL be nullable for transaction types that do not mutate a
Driver wallet balance.

**FR-007 — List presentation**

WHEN the frontend renders transaction rows,
THE SYSTEM SHALL show columns:

- Loại.
- Người dùng.
- Số tiền.
- Mô tả.
- Liên quan.
- Trạng thái liên kết.
- Thời gian.

The frontend SHALL:

- Format VND with dot thousand separators.
- Show positive amounts with `+`.
- Show negative amounts with `-`.
- Use the canonical Vietnamese type labels.
- Show a tooltip explaining derived linked status.
- Open transaction detail when a row is clicked.

**FR-008 — List UI states and pagination**

WHILE the list request is pending,
THE SYSTEM SHALL display a loading skeleton.

IF the request succeeds with zero rows,
THEN THE SYSTEM SHALL display `Không có giao dịch nào`.

IF the request fails,
THEN THE SYSTEM SHALL display a Vietnamese error state and a `Thử lại` action.

WHEN the Admin changes filter, search or page size,
THE SYSTEM SHALL reset the current page to `0`.

---

### Nhóm 2 — Transactions Summary (FR-009..FR-014)

**FR-009 — Summary endpoint**

WHEN an authenticated Admin requests
`GET /api/admin/transactions/summary?period=THIS_MONTH`,
THE SYSTEM SHALL return financial KPI aggregates for the resolved period.

Supported periods SHALL be:

- `THIS_MONTH`.
- `LAST_MONTH`.
- `THIS_YEAR`.
- `CUSTOM`.

`CUSTOM` SHALL require valid `date_from` and `date_to`.

**FR-010 — Summary response**

WHEN summary aggregation completes,
THE SYSTEM SHALL return:

```json
{
  "period_start": "2026-06-01",
  "period_end": "2026-06-30",
  "confirmed_inflow": {
    "order_payments": 80000000,
    "driver_deposit_top_ups": 9000000,
    "grand_total": 89000000
  },
  "confirmed_outflow": {
    "refunds": 2000000,
    "withdrawals": 60000000,
    "driver_deposit_refunds": 1000000,
    "grand_total": 63000000
  },
  "platform_fee_revenue": 24000000,
  "pending_withdrawals": 5000000,
  "transaction_count": 1234,
  "generated_at": "2026-06-04T08:35:00Z"
}
```

The response SHALL NOT label Driver earnings or damage deductions as external
cash inflow or outflow.

**FR-011 — Summary money semantics**

WHERE summary values are calculated,
THE SYSTEM SHALL use explicit type-based formulas.

The formulas SHALL be:

- Confirmed inflow =
  `SUM(ORDER_PAYMENT) + SUM(DEPOSIT_TOP_UP)`.
- Confirmed outflow =
  `ABS(SUM(REFUND)) + ABS(SUM(WITHDRAWAL)) + ABS(SUM(DEPOSIT_REFUND))`.
- Platform fee revenue =
  `SUM(PLATFORM_FEE)`.
- Pending withdrawals =
  `SUM(withdrawal_request.amount WHERE status = 'PENDING')`.

The system SHALL NOT compute profit by blindly summing every signed transaction.

**FR-012 — Summary period boundaries**

WHEN a period is resolved,
THE SYSTEM SHALL apply the configured business timezone consistently.

Period filtering SHALL use an inclusive start and exclusive end.

IF `date_from` is after `date_to`,
THEN THE SYSTEM SHALL return HTTP `400` with code `INVALID_DATE_RANGE`.

IF a custom period exceeds `366` days,
THEN THE SYSTEM SHALL return HTTP `400` with code `DATE_RANGE_TOO_LARGE`.

**FR-013 — KPI cards**

WHEN summary data loads,
THE SYSTEM SHALL display four KPI cards:

- `Tổng tiền vào đã xác nhận`.
- `Tổng tiền ra đã xác nhận`.
- `Doanh thu phí nền tảng`.
- `Rút tiền đang chờ`.

Each card SHALL show:

- VND amount.
- Resolved period label.
- Loading state.
- Error state independent from the list.
- Tooltip with its formula.

**FR-014 — Summary cache**

WHILE summary data is unchanged,
THE SYSTEM SHALL cache each period and filter combination for up to `5` minutes.

WHEN a relevant committed transaction is inserted,
THE SYSTEM SHALL invalidate affected current-period cache entries.

WHEN a withdrawal request changes pending status,
THE SYSTEM SHALL invalidate pending-withdrawal KPI cache entries.

---

### Nhóm 3 — Transactions by Type (Charts) (FR-015..FR-019)

**FR-015 — By-type endpoint**

WHEN an authenticated Admin requests
`GET /api/admin/transactions/by-type?period=THIS_MONTH&group_by=day`,
THE SYSTEM SHALL return a chronological time series grouped by canonical type.

Supported `group_by` values SHALL be:

- `day`.
- `week`.
- `month`.

**FR-016 — Chart response**

WHEN chart aggregation completes,
THE SYSTEM SHALL return zero-filled buckets:

```json
[
  {
    "bucket_start": "2026-06-01",
    "deposit_top_up": 5000000,
    "deposit_refund": 0,
    "order_payment": 8000000,
    "driver_earning": 6000000,
    "platform_fee": 2000000,
    "damage_deduction": 0,
    "refund": 500000,
    "withdrawal": 3000000
  }
]
```

All chart values SHALL be non-negative absolute magnitudes.

The tooltip SHALL explain that direction is represented by transaction type,
not by a negative chart bar.

**FR-017 — Chart range limits**

WHEN chart data is requested,
THE SYSTEM SHALL enforce:

- `day`: maximum `90` days.
- `week`: maximum `104` weeks.
- `month`: maximum `60` months.

IF the requested range exceeds the selected grouping limit,
THEN THE SYSTEM SHALL return HTTP `400` with code
`CHART_RANGE_TOO_LARGE`.

**FR-018 — Chart presentation**

WHEN the frontend renders chart data,
THE SYSTEM SHALL display a Chart.js stacked bar chart.

The chart SHALL:

- Use dates on the x-axis.
- Use VND on the y-axis.
- Match badge colors used in the list.
- Permit hiding individual types from the legend.
- Show the exact VND value in the hover tooltip.
- Display `Không có dữ liệu trong kỳ` for an empty period.

**FR-019 — Chart aggregation accuracy**

WHERE time-series buckets are generated,
THE SYSTEM SHALL assign each transaction to exactly one bucket using
`transaction.created_at` and the configured business timezone.

IF late-arriving imported records are present,
THEN THE SYSTEM SHALL include them according to their persisted `created_at`.

The sum of all chart buckets for a type SHALL equal the summary query for the
same type, period and timezone.

---

### Nhóm 4 — Transaction Detail (FR-020..FR-024)

**FR-020 — Detail endpoint**

WHEN an authenticated Admin requests
`GET /api/admin/transactions/{id}`,
THE SYSTEM SHALL return the canonical transaction and its linked entities.

IF the transaction does not exist,
THEN THE SYSTEM SHALL return HTTP `404` with code `TRANSACTION_NOT_FOUND`.

**FR-021 — Detail response**

WHEN transaction detail is returned,
THE SYSTEM SHALL include:

```json
{
  "transaction": {
    "id": "uuid",
    "type": "DAMAGE_DEDUCTION",
    "amount": -500000,
    "balance_after": 2500000,
    "description": "Khấu trừ dispute",
    "created_at": "2026-06-04T08:30:00Z"
  },
  "user": {
    "id": "uuid",
    "name": "Nguyễn Văn B",
    "email_masked": "b***@example.com",
    "phone_masked": "098****567",
    "role": "DRIVER"
  },
  "related_order": null,
  "related_withdrawal": null,
  "related_dispute": {
    "id": "uuid",
    "status": "RESOLVED_DEDUCT",
    "claim_amount": 500000
  },
  "related_payment": null,
  "audit_log": []
}
```

**FR-022 — Detail modal**

WHEN an Admin clicks a transaction row,
THE SYSTEM SHALL open a detail modal with four sections:

- Thông tin giao dịch.
- Người dùng.
- Entity liên quan.
- Audit timeline.

The modal SHALL:

- Preserve current list filters behind the modal.
- Support browser close and Escape.
- Show a copy action for transaction UUID.
- Mask sensitive reference values by default.
- Provide links only to routes allowed by Admin RBAC.

**FR-023 — Related entity navigation**

WHEN a detail response contains a related entity,
THE SYSTEM SHALL provide navigation to:

- `/admin/order-detail.html?id={id}` for an order.
- `/admin/withdrawal-detail.html?id={id}` when that Spec 012 route exists.
- `/manager/dispute-detail.html?id={id}` only if Admin access is supported.
- `/admin/driver-detail.html?id={id}` for a Driver.
- `/admin/customer-detail.html?id={id}` for a Customer.

IF a related entity was removed from a non-ledger projection,
THEN THE SYSTEM SHALL retain the transaction detail and show
`Entity liên quan không còn khả dụng`.

**FR-024 — Detail privacy and immutability**

WHERE transaction detail is displayed,
THE SYSTEM SHALL mask full bank account numbers, VNPay references, IP addresses
and personal contact data unless explicitly required by an authorized audit
workflow.

The detail modal SHALL NOT expose edit, delete, void or retry actions.

The API SHALL NOT accept mutation methods on `/api/admin/transactions/{id}`.

---

### Nhóm 5 — Reconciliation Report (FR-025..FR-029)

**FR-025 — Reconciliation endpoint**

WHEN an authenticated Admin requests
`GET /api/admin/transactions/reconciliation?date_from=&date_to=`,
THE SYSTEM SHALL run read-only reconciliation checks over the selected range.

IF no range is supplied,
THEN THE SYSTEM SHALL run full-history reconciliation.

IF a partial range lacks a trusted opening balance snapshot,
THEN THE SYSTEM SHALL return status `INCOMPLETE_BASELINE` and SHALL NOT claim
that the period is balanced.

**FR-026 — Reconciliation invariants**

WHERE reconciliation runs,
THE SYSTEM SHALL evaluate at least these invariants:

1. Each Driver wallet closing balance equals opening balance plus all
   wallet-affecting transactions.
2. Total Driver earnings equals `SUM(DRIVER_EARNING)`.
3. Total withdrawn equals `ABS(SUM(WITHDRAWAL))`.
4. Each processed withdrawal maps to exactly one `WITHDRAWAL` transaction.
5. No pending, rejected or cancelled withdrawal maps to a `WITHDRAWAL`.
6. Driver deposit balance equals top-ups minus refunds and applicable
   deductions.
7. Each completed order financial split reconciles payment, Driver earning
   and platform fee according to the order pricing contract.
8. Each refund maps to an authorized cancellation or dispute outcome.
9. Each damage deduction maps to a resolved dispute.
10. No transaction UUID is duplicated.

**FR-027 — Reconciliation response**

WHEN reconciliation completes,
THE SYSTEM SHALL return:

```json
{
  "status": "MISMATCH",
  "is_balanced": false,
  "baseline_status": "AVAILABLE",
  "period_start": "2026-06-01T00:00:00Z",
  "period_end": "2026-07-01T00:00:00Z",
  "checks": [
    {
      "code": "DRIVER_WALLET_BALANCE",
      "expected": 40000000,
      "actual": 39500000,
      "discrepancy": -500000,
      "mismatch_count": 1
    }
  ],
  "total_mismatch_count": 1,
  "generated_at": "2026-06-04T08:40:00Z"
}
```

Each check SHALL state its formula and source tables in response metadata or
the API documentation.

**FR-028 — Reconciliation presentation**

WHEN reconciliation data is displayed,
THE SYSTEM SHALL show:

- Overall balanced, mismatch or incomplete-baseline status.
- One row per invariant.
- Expected value.
- Actual value.
- Discrepancy.
- Mismatch count.
- Generated timestamp.
- A `Chạy lại đối soát` action.

WHERE any discrepancy is non-zero,
THE SYSTEM SHALL highlight the affected row in red and show a critical warning.

WHERE all checks pass,
THE SYSTEM SHALL show `Đối soát cân bằng`.

**FR-029 — No automatic reconciliation repair**

IF reconciliation detects a mismatch,
THEN THE SYSTEM SHALL record an audit event and present diagnostic identifiers.

The system SHALL NOT:

- Insert balancing transactions.
- Mutate wallet balances.
- Change linked entity status.
- Hide a failed invariant.
- Mark the report balanced through rounding.

Any corrective workflow SHALL be specified separately.

---

### Nhóm 6 — Audit + Performance (FR-030..FR-034)

**FR-030 — Access audit**

WHEN an Admin accesses list, summary, chart, detail or reconciliation data,
THE SYSTEM SHALL insert an audit event with:

- `event_type`.
- `admin_id`.
- Endpoint category.
- Sanitized filters.
- Result count where applicable.
- Timestamp.
- Request correlation ID.

List refresh events SHALL use `ADMIN_TRANSACTIONS_VIEWED`.

Reconciliation events SHALL use `ADMIN_RECONCILIATION_RUN`.

**FR-031 — Audit throttling and sanitization**

WHILE the same Admin repeatedly loads the same list filter set,
THE SYSTEM SHALL write at most one `ADMIN_TRANSACTIONS_VIEWED` event per
`60` seconds.

The throttle SHALL NOT apply to:

- Transaction detail access.
- Reconciliation runs.
- Authorization failures.
- Future export actions.

Audit metadata SHALL NOT store raw search terms that may contain email,
bank reference or VNPay reference.

**FR-032 — Query indexes**

WHERE transaction queries are executed,
THE SYSTEM SHALL use indexes supporting:

- Created time plus deterministic ID.
- Type plus created time.
- User plus created time.
- Related order.
- Related withdrawal.
- Related dispute.
- Exact payment reference.
- Exact bank transaction reference.

The implementation SHALL verify query plans with production-like volume before
release.

**FR-033 — Aggregation cache and refresh**

WHILE summary and chart requests repeat with identical normalized parameters,
THE SYSTEM SHALL use a cache with maximum TTL `5` minutes.

Reconciliation SHALL NOT use a stale cached result when the Admin presses
`Chạy lại đối soát`.

The frontend SHALL poll list and KPI data every `60` seconds only while:

- The page is visible.
- Auto-refresh is enabled.
- No filter input or modal interaction is active.

**FR-034 — Pagination and load protection**

WHEN a list request is received,
THE SYSTEM SHALL enforce page size maximum `100`.

WHEN an aggregation request exceeds its allowed date range,
THE SYSTEM SHALL reject it before executing an expensive database query.

IF the database query exceeds the configured timeout,
THEN THE SYSTEM SHALL return a controlled error with correlation ID and SHALL
NOT return partial financial totals.

---

### Nhóm 7 — RBAC + Read-only (FR-035..FR-037)

**FR-035 — Admin RBAC on every endpoint**

WHERE any `/api/admin/transactions` endpoint is accessed,
THE SYSTEM SHALL require role `ADMIN` according to Spec 001.

Authorization SHALL be enforced in the backend regardless of frontend route
visibility.

Every denied attempt SHALL be security-audited without exposing financial data.

**FR-036 — Immutable transaction contract**

WHERE the canonical `transaction` ledger is used,
THE SYSTEM SHALL treat committed rows as append-only.

The application role SHALL NOT expose update or delete operations for committed
transactions.

IF a mutation is attempted,
THEN THE SYSTEM SHALL reject it, preserve the original row and audit the attempt.

Corrections SHALL require a separately authorized compensating transaction
workflow defined in a future spec.

**FR-037 — Deferred export and adjustment controls**

WHEN the frontend renders `Xuất CSV` or `Điều chỉnh thủ công`,
THE SYSTEM SHALL show those controls as disabled with `Sắp ra mắt`.

IF a caller attempts an undeclared export or adjustment endpoint,
THEN THE SYSTEM SHALL return HTTP `404` or `405` and SHALL NOT create financial
data.

Future export access SHALL record filters, row count and Admin identity.

---

## Non-Functional Requirements

**NFR-001 — List performance**

The list API SHALL complete in under `800ms` at p95 for page size `20` with
at least `100.000` transaction rows.

**NFR-002 — Summary performance**

Summary KPI queries SHALL complete in under `1.5s` at p95 for supported ranges.

**NFR-003 — Chart performance**

Chart data SHALL complete in under `1s` at p95 for a `30`-day daily range.

**NFR-004 — Detail performance**

Transaction detail SHALL complete in under `500ms` at p95.

**NFR-005 — Reconciliation performance**

Full reconciliation SHALL complete in under `3s` at p95 at current target
volume, or return a controlled timeout without partial conclusions.

**NFR-006 — Immutability**

Committed transactions SHALL be immutable per HR-13.

**NFR-007 — Money precision**

All money SHALL use Java `BigDecimal` and PostgreSQL `NUMERIC(15,0)`.

JSON money values SHALL be integer VND.

**NFR-008 — Cache**

Aggregation cache TTL SHALL not exceed `5` minutes.

**NFR-009 — Accessibility**

Type and status meaning SHALL not rely on color alone.

Badges SHALL include readable labels and sufficient contrast.

**NFR-010 — Privacy**

Sensitive references and personal data SHALL be masked in list responses,
logs and client-side telemetry.

**NFR-011 — Reliability**

Financial aggregate endpoints SHALL never return partial totals as successful
responses.

**NFR-012 — Observability**

All requests SHALL carry a correlation ID and expose latency, cache-hit and
error metrics without exposing money references.

---

## API Endpoints Summary

| Method | Endpoint | Purpose | Pagination | Cache |
|---|---|---|---|---|
| GET | `/api/admin/transactions` | Danh sách giao dịch | Yes, max 100 | No |
| GET | `/api/admin/transactions/summary` | KPI theo kỳ | No | 5 phút |
| GET | `/api/admin/transactions/by-type` | Dữ liệu chart | No | 5 phút |
| GET | `/api/admin/transactions/{id}` | Chi tiết giao dịch | No | No |
| GET | `/api/admin/transactions/reconciliation` | Đối soát ledger | No | No |

### Common Response Rules

1. Success responses use HTTP `200`.
2. Validation errors use HTTP `400`.
3. Unauthenticated requests use HTTP `401`.
4. Unauthorized requests use HTTP `403`.
5. Missing transaction uses HTTP `404`.
6. Query timeout uses HTTP `503`.
7. Error body includes stable `code`, Vietnamese `message` and
   `correlation_id`.
8. Responses never expose raw database exception text.

### List Query Parameters

| Parameter | Type | Default | Validation |
|---|---|---|---|
| `page` | integer | `0` | Minimum `0` |
| `size` | integer | `20` | `10`, `20`, `50`, `100` |
| `type` | enum | `ALL` | Canonical type or `ALL` |
| `user_role` | enum | `ALL` | `ALL`, `CUSTOMER`, `DRIVER`, `SYSTEM` |
| `linked_status` | enum | `ALL` | Supported derived status |
| `date_from` | ISO date/time | empty | Must not exceed `date_to` |
| `date_to` | ISO date/time | empty | Exclusive upper bound |
| `amount_min` | integer VND | empty | Minimum `0` |
| `amount_max` | integer VND | empty | At least `amount_min` |
| `search` | string | empty | Maximum `100` characters |
| `sort` | string | `created_at,desc` | Allowlisted fields only |

---

## Data Model

The implementation SHALL reuse the canonical `transaction` table from Spec 004,
006 and 007.

It SHALL NOT create a second writable ledger named `wallet_transaction`.

```sql
-- Canonical transaction types.
ALTER TABLE transaction
  DROP CONSTRAINT IF EXISTS transaction_type_check;

ALTER TABLE transaction
  ADD CONSTRAINT transaction_type_check
  CHECK (type IN (
    'DEPOSIT_TOP_UP',
    'DEPOSIT_REFUND',
    'ORDER_PAYMENT',
    'DRIVER_EARNING',
    'PLATFORM_FEE',
    'DAMAGE_DEDUCTION',
    'REFUND',
    'WITHDRAWAL'
  ));

-- Optional links required for cross-entity traceability.
ALTER TABLE transaction
  ADD COLUMN IF NOT EXISTS related_dispute_id UUID REFERENCES dispute(id);

ALTER TABLE transaction
  ADD COLUMN IF NOT EXISTS related_payment_id UUID REFERENCES payment_transaction(id);

-- Critical list and relationship indexes.
CREATE INDEX IF NOT EXISTS idx_transaction_created_id
  ON transaction(created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_type_created
  ON transaction(type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_user_created
  ON transaction(user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transaction_order
  ON transaction(order_id, created_at DESC)
  WHERE order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_withdrawal
  ON transaction(withdrawal_request_id)
  WHERE withdrawal_request_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_dispute
  ON transaction(related_dispute_id)
  WHERE related_dispute_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_vnp_txn_ref
  ON payment_transaction(vnp_txn_ref)
  WHERE vnp_txn_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_withdrawal_bank_txn_ref
  ON withdrawal_request(bank_txn_ref)
  WHERE bank_txn_ref IS NOT NULL;
```

### Read Projection

The Admin list SHALL use a read-only query projection joining:

- `transaction`.
- `app_user`.
- `service_order`.
- `payment_transaction`.
- `withdrawal_request`.
- `dispute`.

The projection SHALL:

- Derive `user_role` from `app_user.role`.
- Derive `linked_status` from linked entities.
- Mask references before serialization.
- Preserve rows whose optional linked entity is absent.
- Avoid denormalizing mutable user role into the immutable ledger.

### Money Sign Rules

| Type | Ledger sign | Wallet impact | External cash meaning |
|---|---:|---|---|
| `DEPOSIT_TOP_UP` | Positive | Increase Driver deposit | Confirmed inflow |
| `DEPOSIT_REFUND` | Negative | Decrease Driver deposit | Confirmed outflow |
| `ORDER_PAYMENT` | Positive | No Customer wallet | Confirmed inflow |
| `DRIVER_EARNING` | Positive | Increase Driver balance | Internal allocation |
| `PLATFORM_FEE` | Positive | No Driver balance increase | Revenue allocation |
| `DAMAGE_DEDUCTION` | Negative | Decrease Driver balance/deposit | Internal deduction |
| `REFUND` | Negative | No Customer wallet | Confirmed outflow |
| `WITHDRAWAL` | Negative | Decrease Driver balance | Confirmed outflow |

### Immutability Controls

1. Service code SHALL expose insert and read operations only.
2. Database permissions SHOULD deny application-role update and delete.
3. Any migration modifying historical rows SHALL require a reviewed,
   auditable migration plan.
4. A failed linked operation SHALL not leave an orphan transaction.
5. Money-producing workflows SHALL remain atomic per HR-11.

---

## State Machine

Không có state machine mới cho canonical transaction.

Một transaction row chỉ tồn tại khi money event đã committed.

```text
Money workflow pending
  ↓ successful atomic commit
Canonical transaction row [immutable terminal record]

Linked payment status:
PENDING → SUCCESS | FAILED | CANCELLED

Linked withdrawal status:
PENDING → APPROVED → PROCESSED
PENDING → REJECTED | CANCELLED

Only PROCESSED withdrawal creates WITHDRAWAL transaction.
```

`linked_status` chỉ là projection để Admin hiểu trạng thái entity liên quan.

Nó không cho phép chuyển trạng thái transaction.

HR-05 áp dụng cho state machine của payment, withdrawal, order và dispute.

---

## Acceptance Criteria

**AC-01 — Admin list**

GIVEN an Admin and at least 100.000 transactions,
WHEN the Admin opens the page,
THEN the first 20 rows load in under `800ms` at p95 and use deterministic
newest-first ordering.

**AC-02 — Combined filters**

GIVEN transactions across multiple types, roles and dates,
WHEN the Admin combines type, role, date and amount filters,
THEN every returned row satisfies every supplied filter.

**AC-03 — Search debounce**

GIVEN the Admin types multiple characters within `300ms`,
WHEN typing stops,
THEN the frontend issues only one final search request.

**AC-04 — Canonical taxonomy**

GIVEN existing financial records,
WHEN the list renders,
THEN it shows canonical types and does not invent Customer top-up,
withdrawal hold or withdrawal refund rows.

**AC-05 — KPI accuracy**

GIVEN a known fixture ledger,
WHEN summary is requested,
THEN every KPI exactly matches the documented SQL-equivalent formula.

**AC-06 — Chart consistency**

GIVEN a selected period,
WHEN chart and summary data are compared,
THEN totals by canonical type match for the same timezone and range.

**AC-07 — Reconciliation discrepancy**

GIVEN a Driver wallet balance intentionally differs by `500.000 VND`,
WHEN reconciliation runs,
THEN status is `MISMATCH`, discrepancy is `-500000` or the equivalent signed
difference, and no repair is performed.

**AC-08 — Immutable transactions**

GIVEN an Admin attempts update or delete on a committed transaction,
WHEN the request reaches the backend,
THEN it is rejected and the original row remains unchanged.

**AC-09 — RBAC**

GIVEN a Customer, Driver or Manager token,
WHEN it accesses any Spec 013 endpoint,
THEN the response is HTTP `403` with no financial payload.

**AC-10 — Pagination**

GIVEN more rows than the selected page size,
WHEN the Admin uses next, previous or a valid page number,
THEN the correct deterministic page loads and page size never exceeds `100`.

**AC-11 — UI states**

GIVEN loading, empty and failed API conditions,
WHEN each condition occurs,
THEN the page displays the corresponding Vietnamese loading, empty or error
state per AC-16.

**AC-12 — Vietnamese and money format**

GIVEN any rendered transaction,
WHEN the UI displays labels and amounts,
THEN Vietnamese diacritics are complete and `1000000` renders as
`1.000.000 VND`.

---

## Edge Cases & Error Handling

| ID | Edge case | Expected handling |
|---|---|---|
| EC-01 | Ledger rỗng | List rỗng, KPI zero, chart zero-filled; reconciliation chỉ balanced nếu opening balance cũng zero |
| EC-02 | `date_from > date_to` | HTTP `400 INVALID_DATE_RANGE` |
| EC-03 | Amount range âm hoặc đảo | HTTP `400 INVALID_AMOUNT_RANGE` |
| EC-04 | Filter alias không hỗ trợ | HTTP `400 UNSUPPORTED_TRANSACTION_TYPE` |
| EC-05 | Search có ký tự SQL đặc biệt | Dùng bound parameters và coi ký tự là literal |
| EC-06 | Search dài hơn 100 ký tự | HTTP `400 SEARCH_TERM_TOO_LONG` |
| EC-07 | Filter hợp lệ nhưng không có kết quả | Trả empty page, không coi là lỗi |
| EC-08 | Linked entity không còn khả dụng | Giữ transaction và hiển thị entity unavailable |
| EC-09 | Có insert mới khi phân trang | Dùng deterministic sort; UI có thể báo dữ liệu mới |
| EC-10 | Nhiều Admin aggregate đồng thời | Cache/query limits, không trả partial total |
| EC-11 | Query timeout | Controlled error, không trả KPI/reconciliation một phần |
| EC-12 | Thiếu opening snapshot | Trả `INCOMPLETE_BASELINE`, không trả balanced |
| EC-13 | Một processed withdrawal có nhiều ledger rows | Critical reconciliation mismatch |
| EC-14 | Pending withdrawal có `WITHDRAWAL` row | Critical reconciliation mismatch |
| EC-15 | Dữ liệu có phần lẻ dưới một VND | Flag invalid scale, không rounding away |
| EC-16 | Historical type không biết | Hiển thị `Không xác định`, audit và loại khỏi formula |
| EC-17 | Admin rời trang khi đang fetch | Cancel hoặc ignore stale response |
| EC-18 | Auto-refresh trong lúc tương tác | Pause và resume sau tương tác |
| EC-19 | Search reference nhạy cảm | Audit chỉ lưu sanitized filter hash |
| EC-20 | Export trước Sprint 6+ | Không gửi request hoặc tạo file |

---

## Test Cases

| ID | Given / When | Expected result |
|---|---|---|
| TC-01 | Admin requests default list with 25 rows | HTTP 200, size 20, deterministic newest-first, refs masked |
| TC-02 | Combine type, role, date, amount and order-code search | Every row matches all filters; debounce sends one request |
| TC-03 | Customer, Driver and Manager call all five endpoints | HTTP 403, no payload, denied attempts audited |
| TC-04 | Request summary over known financial fixtures | Inflow, outflow, fee and pending withdrawal formulas match exactly |
| TC-05 | Request daily chart and matching summary | Zero-filled buckets and per-type totals match |
| TC-06 | Open a deduction linked to dispute | Masked detail and authorized link shown; no mutation action |
| TC-07 | Run full reconciliation on valid fixture | `BALANCED`, every discrepancy zero, no mutation |
| TC-08 | Processed withdrawal has two ledger rows | `MISMATCH`, invariant fails, no balancing row inserted |
| TC-09 | Period reconciliation lacks opening snapshot | `INCOMPLETE_BASELINE`; UI never shows balanced |
| TC-10 | Attempt update/delete committed transaction | Attempts fail, original remains, attempts audited |
| TC-11 | Run queries with 1.000.000 realistic rows | Intended indexes used and p95 targets met |
| TC-12 | Render loading, empty, error and success fixtures | AC-16 states, Vietnamese labels, VND format and disabled export work |

---

## Constitution Compliance

| Rule | Compliance |
|---|---|
| HR-10 | Every endpoint requires backend role `ADMIN` |
| HR-11 | Spec reads committed results; source money workflows remain atomic |
| HR-13 | Ledger is append-only; access and reconciliation are audited |
| HR-19 | API behavior, errors and boundaries are explicitly specified |
| HR-20 | Cross-spec financial contracts are resolved explicitly |
| AC-08 | VND uses `BigDecimal` / `NUMERIC(15,0)` with scale zero |
| AC-14 | Canonical types use `VARCHAR + CHECK` |
| AC-15 | Server-side pagination is mandatory and capped at 100 |
| AC-16 | Loading, empty and error states are mandatory |

---

## Definition of Done

1. All five read-only endpoints satisfy this specification.
2. Exactly the canonical transaction taxonomy is used.
3. The Admin page supports pagination, filters, search and detail modal.
4. KPI and chart formulas pass fixture verification.
5. Reconciliation reports each required invariant.
6. No transaction mutation API exists.
7. Admin RBAC and audit tests pass.
8. Sensitive values are masked.
9. Performance targets are verified with production-like data.
10. Vietnamese copy and VND formatting are reviewed.
11. Spec 004, 006, 007, 009, 010 and 011 references remain consistent.
12. Export and manual adjustment remain deferred.
