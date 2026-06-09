# Feature Specification: Admin Reports & Analytics

**Feature Branch:** `016-admin-reports`
**Feature Number:** #16 of 18 — SUPPORT (deep analytics)
**Created:** 2026-06-04
**Version:** 1.0.0
**Status:** Draft
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §24 Analytics + reports
**Constitution reference:** v1.3.0 — HR-10 (Admin RBAC), HR-13 (audit),
HR-19/20, AC-08, AC-14, AC-15, AC-16

---

## Goals

Admin cần một trang phân tích sâu để hiểu xu hướng kinh doanh, hiệu quả vận
hành, chất lượng nguồn cung Driver và hành vi Customer của Move_home. Khác với
Dashboard Spec 015 cung cấp snapshot nhanh theo thời gian gần thực, Reports cho
phép chọn kỳ, so sánh với kỳ trước, xem breakdown và truy vết insight sang các
trang danh sách liên quan.

Báo cáo tài chính hiển thị gross booking value, doanh thu platform fee, refund,
damage-recovery và management net contribution. Báo cáo vận hành đo số order,
tỷ lệ hoàn thành, tỷ lệ khiếu nại, giá trị đơn trung bình, khoảng cách và thời
gian hoàn thành. Báo cáo Driver gồm top earners, rating distribution, utilization
và churn proxy. Báo cáo Customer gồm active users, basic 30-day retention, mức
chi tiêu và top spenders. Heatmap 7 ngày × 24 giờ giúp nhận diện nhu cầu theo
giờ để hỗ trợ quyết định pricing và điều phối.

Mọi metric phải có định nghĩa, nguồn dữ liệu, date dimension và cách xử lý zero
data rõ ràng. Các kết quả tiền tệ dùng VND nguyên đồng; compare period dùng cùng
độ dài và cùng timezone. Nếu dữ liệu nguồn chưa đủ, API phải trả data-quality
warning thay vì suy diễn số liệu.

Mục tiêu là hỗ trợ leadership ra quyết định dựa trên dữ liệu, vẫn bảo đảm RBAC,
audit, hiệu năng và khả năng giải thích. Export, scheduled report, custom report
builder và predictive analytics được defer sang Sprint 6+.

---

## Source-of-Truth Resolution

| Chủ đề | Quyết định canonical | Hệ quả |
|---|---|---|
| Reports vs Dashboard | Reports là deep-dive theo kỳ; Dashboard là overview | Không thay thế Spec 015 |
| Financial ledger | Dùng append-only `transaction` từ Spec 013 | Không dùng `wallet_transaction` legacy |
| Platform revenue | `SUM(PLATFORM_FEE)` | Không suy ra từ current commission setting |
| Gross booking value | Tổng `service_order.total_quote` của order completed trong kỳ | Tách khỏi external cash inflow |
| Refund | `ABS(SUM(REFUND))` | Là outflow, không tự gọi là operating expense |
| Damage deduction | `ABS(SUM(DAMAGE_DEDUCTION))` hiển thị recovery riêng | Không tính là expense |
| Profit label | Dùng `management_net_contribution` | Không tuyên bố P&L kế toán đầy đủ |
| Order statuses | Canonical Spec 011 | Không dùng `PENDING|ACCEPTED|DISPUTED` aliases |
| Online ratio | Tính từ immutable online-status intervals nếu đủ | Thiếu interval trả `INSUFFICIENT_DATA` |
| Customer retention | Basic 30-day repeat retention | Advanced cohort analysis deferred |
| Timezone | UTC storage, `Asia/Ho_Chi_Minh` grouping | Boundary nhất quán |
| Compare | Previous equal-length period hoặc same period last year | Không compare range khác độ dài |

`management_net_contribution = platform_fee_revenue - refunds`.

Chỉ số này không bao gồm payroll, fuel, tax, infrastructure hoặc operating
expenses chưa có ledger, nên SHALL không được gắn nhãn “lợi nhuận ròng”.

---

## Scope Summary

**In scope:**

1. `GET /api/admin/reports/financial`.
2. `GET /api/admin/reports/operations`.
3. `GET /api/admin/reports/drivers`.
4. `GET /api/admin/reports/customers`.
5. `GET /api/admin/reports/peak-hours`.
6. Date range predefined và custom.
7. Compare previous period hoặc same period last year.
8. Line, bar, doughnut/pie và heatmap charts.
9. Drill-through links sang Spec 011/012/013 bằng filter hợp lệ.
10. Admin-only RBAC.
11. Five-minute aggregation cache.
12. Throttled reports-access audit.
13. Data-quality warnings.
14. Loading, empty và error states.

**Out of scope:**

1. Real-time updates; dùng Dashboard Spec 015.
2. Accounting-grade statutory P&L.
3. Payroll, tax và operating-expense ledger.
4. Custom report builder.
5. CSV/PDF/Excel export.
6. Scheduled email reports.
7. Advanced cohort explorer.
8. Predictive analytics hoặc ML.
9. Materialized views trong Sprint 5.
10. Mutation từ report.

---

## User Stories

**P1:**

- **US1:** Là Admin, tôi xem báo cáo tài chính theo tháng hoặc quý để theo dõi
  gross booking value, platform fee, refund và contribution.
- **US2:** Là Admin, tôi xem operations report với completion, average order
  value và dispute rate.
- **US3:** Là Admin, tôi xem Driver leaderboard, rating distribution và
  utilization.
- **US4:** Là Admin, tôi xem Customer active users, retention và top spenders.
- **US5:** Là Admin, tôi chọn date range predefined hoặc custom.
- **US6:** Là Admin, tôi compare kỳ hiện tại với kỳ trước hoặc cùng kỳ năm trước.
- **US7:** Là Admin, tôi xem peak-hours heatmap để hỗ trợ pricing và điều phối.

**P2:**

- **US8:** Là Admin, tôi export report PDF/CSV → defer Sprint 6+.
- **US9:** Là Admin, tôi schedule weekly report email → defer Sprint 6+.

---

## Functional Requirements

> EARS notation: WHEN | WHILE | WHERE | IF/THEN

### Nhóm 1 — Financial Report (FR-001..FR-007)

**FR-001 — Financial endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/reports/financial?period_start=&period_end=&compare_with=PREVIOUS_PERIOD&group_by=day`,
THE SYSTEM SHALL return a financial management report for the resolved period.

**FR-002 — Financial response**

WHEN financial aggregation succeeds,
THE SYSTEM SHALL return:

```json
{
  "period": {"start": "2026-06-01", "end": "2026-07-01"},
  "gross_booking_value": {
    "total": 100000000,
    "breakdown_by_vehicle": {
      "TRUCK_500KG": 30000000,
      "TRUCK_1T": 50000000,
      "TRUCK_15T": 20000000
    }
  },
  "platform_fee": {"total": 30000000, "effective_rate": "0.3000"},
  "refunds": 2000000,
  "damage_recovery": 1500000,
  "management_net_contribution": 28000000,
  "compare": {
    "gross_booking_value_change_percent": "15.50",
    "platform_fee_change_percent": "12.30",
    "contribution_change_percent": "18.20"
  },
  "trend": []
}
```

**FR-003 — Financial formulas**

WHERE financial metrics are calculated,
THE SYSTEM SHALL use:

- Gross booking value = completed order `total_quote`, grouped by
  `completed_at`.
- Platform fee = `SUM(transaction.amount)` where type `PLATFORM_FEE`, grouped
  by transaction `created_at`.
- Refunds = absolute `SUM(REFUND)`.
- Damage recovery = absolute `SUM(DAMAGE_DEDUCTION)`.
- Management net contribution = platform fee minus refunds.
- Effective rate = platform fee divided by gross booking value.

Damage recovery SHALL be displayed separately and SHALL NOT reduce expense or
inflate contribution.

**FR-004 — Financial trend**

WHEN `group_by=day|week|month`,
THE SYSTEM SHALL return zero-filled chronological trend buckets containing
gross booking value, platform fee, refunds and contribution.

IF a group is incompatible with the range limit,
THEN THE SYSTEM SHALL return HTTP `422 INVALID_GROUPING`.

**FR-005 — Financial comparison**

WHEN compare is enabled,
THE SYSTEM SHALL calculate the same financial formulas over the resolved
comparison range and return decimal-string percentage changes.

IF the comparison denominator is zero,
THEN the corresponding change percent SHALL be `null`.

**FR-006 — Financial presentation**

WHEN the financial report renders,
THE frontend SHALL display four KPI cards, one trend line chart, one vehicle
breakdown bar chart and a formula tooltip for every KPI.

Money SHALL use dot separators and VND labels.

**FR-007 — Financial consistency**

WHERE the report and Spec 013 use the same period and timezone,
THE SYSTEM SHALL make `platform_fee`, `refunds` and damage-recovery totals match
the canonical transaction aggregates.

If reconciliation status is known to be mismatched,
THEN the report SHALL display a data-quality warning.

---

### Nhóm 2 — Operations Report (FR-008..FR-014)

**FR-008 — Operations endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/reports/operations?period_start=&period_end=&compare_with=PREVIOUS_PERIOD&group_by=day`,
THE SYSTEM SHALL return operational metrics for orders created in the period,
except metrics explicitly based on completion time.

**FR-009 — Operations response**

WHEN operations aggregation succeeds,
THE SYSTEM SHALL return:

```json
{
  "orders": {
    "total_created": 1500,
    "completed": 1350,
    "cancelled": 100,
    "in_dispute": 50,
    "terminal_eligible": 1500,
    "completion_rate": "0.9000",
    "dispute_rate": "0.0333"
  },
  "average_order_value": 200000,
  "average_distance_km": "12.50",
  "peak_hour_orders_rate": "0.3500",
  "average_completion_time_minutes": "65.00",
  "status_distribution": {},
  "completion_trend": [],
  "compare": {}
}
```

**FR-010 — Operations formulas**

WHERE operations metrics are calculated,
THE SYSTEM SHALL define:

- `total_created`: orders with `created_at` in period.
- `terminal_eligible`: created-period orders currently in
  `COMPLETED|CANCELLED|IN_DISPUTE`.
- Completion rate: completed divided by terminal eligible.
- Dispute rate: `IN_DISPUTE` divided by terminal eligible.
- Average order value: average `total_quote` of completed orders by
  `completed_at` in period.
- Average completion time: average `completed_at - started_at`.

IF a denominator is zero,
THEN the rate SHALL be `"0.0000"` and include a warning.

**FR-011 — Status distribution**

WHEN status distribution is returned,
THE SYSTEM SHALL include every canonical order status from Spec 011 with zero
for missing statuses.

Legacy aliases SHALL never appear in response or chart labels.

**FR-012 — Peak order classification**

WHEN calculating `peak_hour_orders_rate`,
THE SYSTEM SHALL convert `scheduled_at` to `Asia/Ho_Chi_Minh` and apply the
pricing snapshot peak ranges associated with each order.

The system SHALL NOT apply only the current Admin settings retroactively.

**FR-013 — Operations charts and drill-through**

WHEN operations data renders,
THE frontend SHALL show KPI cards, status doughnut chart and completion-rate
trend line.

WHEN an Admin selects a status segment,
THE frontend SHALL navigate to Spec 011 orders list using a canonical status
and date filter where supported.

**FR-014 — Operations compare**

WHEN compare is enabled,
THE SYSTEM SHALL return percentage changes for total created, completion rate,
dispute rate and average order value using the same definitions and date
dimensions in both periods.

---

### Nhóm 3 — Drivers Report (FR-015..FR-021)

**FR-015 — Drivers endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/reports/drivers?period_start=&period_end=&compare_with=PREVIOUS_PERIOD`,
THE SYSTEM SHALL return Driver supply and performance analytics.

**FR-016 — Driver response**

WHEN Driver aggregation succeeds,
THE SYSTEM SHALL return:

```json
{
  "total_drivers_at_period_end": 500,
  "active_drivers_at_period_end": 350,
  "online_ratio_average": "0.6500",
  "top_earners": [],
  "rating_distribution": {
    "star_5": 200,
    "star_4": 100,
    "star_3": 20,
    "star_2": 5,
    "star_1": 2
  },
  "average_rating_overall": "4.60",
  "operational_churn_proxy_count": 5,
  "data_quality": []
}
```

**FR-017 — Top earners**

WHEN top earners are calculated,
THE SYSTEM SHALL rank up to 10 Drivers by `SUM(DRIVER_EARNING)` released in the
period, descending by earnings then Driver ID.

Each item SHALL contain Driver ID, masked/display name, earnings, earning-order
count and current average rating.

**FR-018 — Rating distribution**

WHEN rating metrics are calculated,
THE SYSTEM SHALL use `order_rating.created_at` within the period.

The sum of star buckets SHALL equal rating count, and overall average SHALL be
computed from the same rows.

**FR-019 — Online ratio**

WHEN sufficient immutable `ONLINE|OFFLINE|BUSY` transition events exist,
THE SYSTEM SHALL calculate online ratio as
`(ONLINE duration + BUSY duration) / observable duration` for eligible Drivers.

WHERE intervals are incomplete,
THE SYSTEM SHALL return `online_ratio_average=null` and data-quality code
`INSUFFICIENT_ONLINE_INTERVALS`.

**FR-020 — Churn proxy**

WHEN operational churn proxy is calculated,
THE SYSTEM SHALL count Drivers who were active before the period end but have
no order, online-status or authenticated activity in the trailing 30 days.

The UI SHALL label this `Tài xế không hoạt động 30 ngày`, not contractual churn.

**FR-021 — Driver presentation**

WHEN Driver report renders,
THE frontend SHALL show KPI cards, top-earners leaderboard and rating bar chart.

Clicking a leaderboard row SHALL navigate to
`/admin/driver-detail.html?id={driverId}`.

---

### Nhóm 4 — Customers Report (FR-022..FR-027)

**FR-022 — Customers endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/reports/customers?period_start=&period_end=&compare_with=PREVIOUS_PERIOD`,
THE SYSTEM SHALL return Customer acquisition, activity and spend analytics.

**FR-023 — Customer response**

WHEN Customer aggregation succeeds,
THE SYSTEM SHALL return:

```json
{
  "total_customers_at_period_end": 2000,
  "active_users": {"dau_average": "50.00", "mau": 500},
  "retention_rate_30d": "0.4500",
  "top_spenders": [],
  "average_spend_per_paying_customer": 500000,
  "new_customers_in_period": 150,
  "compare": {},
  "data_quality": []
}
```

**FR-024 — Active Customer definition**

WHEN active users are calculated,
THE SYSTEM SHALL treat a Customer as active on a day if they have a successful
login audit or Customer-owned order activity on that day.

MAU SHALL count distinct active Customers in the trailing 30 days ending at
`period_end`.

**FR-025 — Basic retention definition**

WHEN `retention_rate_30d` is calculated,
THE SYSTEM SHALL divide eligible Customers with at least one completed order in
the trailing 30 days by eligible Customers whose first completed order occurred
before that trailing window.

WHERE no eligible Customers exist,
THE rate SHALL be `null` with data-quality warning.

**FR-026 — Spend metrics**

WHEN spend metrics are calculated,
THE SYSTEM SHALL use completed order `total_quote` grouped by `completed_at`.

Top spenders SHALL return at most 10 Customers with ID, masked/display name,
completed orders and spend; average spend SHALL divide by paying Customers,
not all registered Customers.

**FR-027 — Customer presentation**

WHEN Customer report renders,
THE frontend SHALL show KPI cards, retention trend and top-spenders table.

Clicking a top spender SHALL navigate to
`/admin/customer-detail.html?id={customerId}`.

---

### Nhóm 5 — Peak Hours Heatmap (FR-028..FR-031)

**FR-028 — Peak-hours endpoint**

WHEN an authenticated Admin calls
`GET /api/admin/reports/peak-hours?period_start=&period_end=`,
THE SYSTEM SHALL return a complete `7 × 24` matrix grouped by local scheduled
weekday and hour.

**FR-029 — Heatmap response**

WHEN heatmap aggregation succeeds,
THE SYSTEM SHALL return 168 cells containing weekday `1..7`, hour `0..23`,
order count and completed count.

Orders without `scheduled_at` SHALL be excluded and counted in
`excluded_missing_schedule`.

**FR-030 — Heatmap presentation**

WHEN heatmap data renders,
THE frontend SHALL use a Chart.js matrix plugin or accessible custom Canvas
with a scale from white at zero to dark forest green at maximum.

Every cell SHALL expose weekday, hour and count through tooltip and accessible
summary text.

**FR-031 — Heatmap insight**

WHEN the heatmap contains data,
THE SYSTEM SHALL identify the top five cells by order count and return them as
descriptive insights.

Insights SHALL be advisory and SHALL NOT automatically change commission or
pricing settings.

---

### Nhóm 6 — Date Range Picker (FR-032..FR-035)

**FR-032 — Predefined periods**

WHEN the Admin selects a predefined period,
THE frontend and backend SHALL support:

- `TODAY`.
- `THIS_WEEK`.
- `THIS_MONTH`.
- `LAST_MONTH`.
- `Q1`, `Q2`, `Q3`, `Q4` with selected year.
- `THIS_YEAR`.
- `CUSTOM`.

**FR-033 — Range validation**

WHEN a custom range is submitted,
THE SYSTEM SHALL require `period_start < period_end`, maximum `365` calendar
days and `period_start` not after the current local date.

Invalid ranges SHALL return HTTP `422 INVALID_DATE_RANGE`.

**FR-034 — Compare period resolution**

WHEN `compare_with=PREVIOUS_PERIOD`,
THE SYSTEM SHALL resolve the immediately preceding equal-length range.

WHEN `compare_with=SAME_PERIOD_LAST_YEAR`,
THE SYSTEM SHALL resolve the equivalent prior-year dates with leap-day handling.

WHEN `compare_with=NONE`,
THE SYSTEM SHALL omit compare metrics.

**FR-035 — Shared picker behavior**

WHEN the Admin changes period or compare option,
THE frontend SHALL update the URL, cancel stale requests and reload all five
report sections using identical normalized boundaries.

Partial section failure SHALL not erase successful sections.

---

### Nhóm 7 — RBAC + Cache (FR-036..FR-038)

**FR-036 — Admin RBAC and read-only**

WHERE any `/api/admin/reports/*` endpoint is accessed,
THE SYSTEM SHALL require authenticated role `ADMIN`.

Authenticated non-Admins SHALL receive HTTP `403`; anonymous callers SHALL
receive HTTP `401`.

Reports SHALL expose no mutation method.

**FR-037 — Cache strategy**

WHILE identical normalized report parameters are requested,
THE SYSTEM SHALL cache each endpoint response for at most `5` minutes.

Cache keys SHALL include endpoint, period, compare range, group and metric
contract version.

Relevant order, transaction, rating, user-activity or Driver-status events
SHALL invalidate affected current-period cache entries.

**FR-038 — Audit and performance**

WHEN an Admin successfully views reports,
THE SYSTEM SHALL audit `REPORTS_VIEWED` with Admin ID, sanitized period,
compare option and report sections.

Repeated identical views SHALL be throttled to one audit per Admin/filter hash
per `60` seconds.

Every endpoint SHALL complete under `3s` at p95 for a 90-day range with at
least 100.000 orders.

---

## Metric Dictionary

| Metric | Source/date dimension | Formula summary |
|---|---|---|
| Gross booking value | Completed orders / `completed_at` | Sum `total_quote` |
| Platform fee | `transaction` / `created_at` | Sum `PLATFORM_FEE` |
| Refunds | `transaction` / `created_at` | Absolute sum `REFUND` |
| Damage recovery | `transaction` / `created_at` | Absolute sum `DAMAGE_DEDUCTION` |
| Management contribution | Transaction ledger | Platform fee minus refunds |
| Completion rate | Orders created in period/current status | Completed / terminal eligible |
| Dispute rate | Orders created in period/current status | In dispute / terminal eligible |
| Average order value | Completed orders / `completed_at` | Avg `total_quote` |
| Driver earnings | `transaction` / `created_at` | Sum `DRIVER_EARNING` |
| Customer spend | Completed orders / `completed_at` | Sum `total_quote` |
| Peak heatmap | Orders / local `scheduled_at` | Count by weekday/hour |

All percentage/rate fields SHALL serialize as decimal strings to avoid
floating-point ambiguity.

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-001 | Each report API p95 dưới `3s` với range 90 ngày |
| NFR-002 | Cache hit rate mục tiêu trên `70%` cho repeated queries |
| NFR-003 | Chart render dưới `2s` với tối đa 1.000 points |
| NFR-004 | Heatmap render dưới `3s` |
| NFR-005 | Compare calculation deterministic |
| NFR-006 | Money integer VND/BigDecimal scale zero |
| NFR-007 | Aggregation queries dùng indexes và không join multiplication |
| NFR-008 | Current-period cache invalidated bởi relevant events |
| NFR-009 | Error không trả partial totals như successful complete report |
| NFR-010 | Charts accessible và không dựa vào màu duy nhất |
| NFR-011 | UTC storage, Asia/Ho_Chi_Minh display/grouping |
| NFR-012 | Data-quality warnings không bị cache quá TTL |

---

## API Endpoints Summary

| Method | Endpoint | Main content | Cache |
|---|---|---|---|
| GET | `/api/admin/reports/financial` | Financial management report | 5 phút |
| GET | `/api/admin/reports/operations` | Order operations | 5 phút |
| GET | `/api/admin/reports/drivers` | Driver supply/performance | 5 phút |
| GET | `/api/admin/reports/customers` | Customer activity/spend | 5 phút |
| GET | `/api/admin/reports/peak-hours` | 7 × 24 heatmap | 5 phút |

### Common Query Parameters

| Parameter | Values |
|---|---|
| `period_start` | Inclusive ISO local date |
| `period_end` | Exclusive ISO local date |
| `compare_with` | `NONE|PREVIOUS_PERIOD|SAME_PERIOD_LAST_YEAR` |
| `group_by` | `day|week|month` where supported |

### Common Errors

| HTTP | Code | Meaning |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | Missing/invalid JWT |
| 403 | `FORBIDDEN` | Non-Admin |
| 422 | `INVALID_DATE_RANGE` | Invalid/oversized range |
| 422 | `INVALID_COMPARE_OPTION` | Unsupported comparison |
| 422 | `INVALID_GROUPING` | Unsupported grouping/range |
| 503 | `REPORT_QUERY_TIMEOUT` | Aggregation exceeded limit |
| 500 | `AUDIT_WRITE_FAILED` | Critical audit failed |

---

## Data Model

No new business table is required.

The implementation SHALL reuse `service_order`, `transaction`, `app_user`,
`driver_profile`, `order_rating`, `dispute` and immutable audit/status events.

```sql
CREATE INDEX IF NOT EXISTS idx_order_report_created_status
  ON service_order(created_at, status)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_order_report_completed_vehicle
  ON service_order(completed_at, vehicle_type)
  WHERE status = 'COMPLETED' AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_order_report_scheduled
  ON service_order(scheduled_at)
  WHERE scheduled_at IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_order_report_started_completed
  ON service_order(started_at, completed_at)
  WHERE status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_transaction_report_type_created
  ON transaction(type, created_at);

CREATE INDEX IF NOT EXISTS idx_rating_report_created_stars
  ON order_rating(created_at, stars)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_report_role_created
  ON app_user(role, created_at)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_dispute_report_created_status
  ON dispute(created_at, status);
```

Materialized daily aggregates MAY be introduced in Sprint 6+ after benchmarks.

Indexes SHALL be created through Flyway and verified with production-like query
plans.

---

## Aggregation and Cache Contract

1. Queries SHALL pre-aggregate independent facts before joining dimensions.
2. A Driver with multiple orders/ratings SHALL appear once in leaderboards.
3. Zero-filled trend buckets SHALL use generated date series.
4. Cache keys SHALL use normalized UTC boundaries, not raw query strings.
5. Cache response SHALL include `generated_at`, `cache_hit` and
   `metric_contract_version`.
6. Cache invalidation MAY evict a whole current-period namespace for safety.
7. Historical closed periods MAY rely on TTL only.
8. Timeout/error responses SHALL not be cached.
9. Data-quality warnings SHALL travel with their metric response.
10. Compare metrics SHALL be calculated from the same contract version.

---

## State Machine

Không có state machine mới.

Reports are read-only projections over canonical states:

```text
Order:
PENDING_PAYMENT → CONFIRMED → ASSIGNED → IN_PROGRESS
→ AWAITING_FINAL_PAYMENT → COMPLETED
Allowed branches → CANCELLED | IN_DISPUTE

Driver:
PENDING_VERIFY → PENDING_DOCUMENTS → PENDING_DEPOSIT
→ PENDING_APPROVAL → ACTIVE → SUSPENDED
```

Unknown statuses SHALL be excluded from rate denominators, reported through a
data-quality warning and never silently mapped to canonical states.

---

## Frontend Screen Contract

The page SHALL contain:

1. Header `Báo cáo phân tích`.
2. Shared date range and compare controls.
3. Disabled `Xuất CSV` and `Xuất PDF` controls with `Sắp ra mắt`.
4. Tabs or sections: Tài chính, Vận hành, Tài xế, Khách hàng, Giờ cao điểm.
5. Independent Loading/Empty/Error state for each section.
6. Formula and data-quality tooltips.
7. Generated-at and cache status label.
8. Responsive Chart.js charts.

The current static fake bars and hardcoded KPI values in
`frontend/pages/admin/reports.html` SHALL be replaced by API data.

Charts SHALL destroy old instances before rerender to avoid memory leaks.

Drill-through links SHALL use only routes and filters defined by Spec 011/012/013.

---

## Acceptance Criteria

**AC-01 — Financial performance and accuracy**

GIVEN 100.000 orders and canonical transactions,
WHEN Admin requests a 30-day financial report,
THEN it returns under `3s` and matches fixture formulas to each VND.

**AC-02 — Compare accuracy**

GIVEN current and previous equal-length ranges,
WHEN compare is enabled,
THEN every percent change uses the same metric contract and handles zero
denominator as null.

**AC-03 — Driver ranking**

GIVEN Driver earnings fixtures,
WHEN the Driver report loads,
THEN top earners are ordered by released `DRIVER_EARNING` totals without join
multiplication.

**AC-04 — Heatmap**

GIVEN orders scheduled across weekdays and hours,
WHEN peak-hours loads,
THEN exactly 168 cells reflect local-time counts and missing cells are zero.

**AC-05 — Cache**

GIVEN two identical calls within five minutes,
WHEN no relevant event invalidates cache,
THEN the second call returns the same metric contract and reports cache hit.

**AC-06 — Date validation**

GIVEN future, reversed or over-365-day ranges,
WHEN any endpoint is called,
THEN HTTP `422 INVALID_DATE_RANGE` is returned before expensive aggregation.

**AC-07 — Charts and states**

GIVEN success, empty, loading and failed section responses,
WHEN the page renders,
THEN charts or the correct Vietnamese AC-16 state appear independently.

**AC-08 — RBAC**

GIVEN Admin, Manager, Driver, Customer and anonymous callers,
WHEN they call report endpoints,
THEN only Admin succeeds.

**AC-09 — Vietnamese UX**

GIVEN every report section and warning,
WHEN rendered,
THEN all user-facing text has full Vietnamese diacritics.

**AC-10 — Money formatting**

GIVEN money value `1000000`,
WHEN rendered,
THEN it displays as `1.000.000 VND` and remains integer VND in JSON.

---

## Edge Cases & Error Handling

| ID | Edge case | Required behavior |
|---|---|---|
| EC-01 | Range dài hơn 365 ngày | `422 INVALID_DATE_RANGE` |
| EC-02 | `period_start` trong tương lai | `422 INVALID_DATE_RANGE` |
| EC-03 | Zero data trong kỳ | Zero KPI/buckets; section empty explanation |
| EC-04 | Compare period không có data | Compare percent null, không chia zero |
| EC-05 | Leap-day compare last year | Resolve valid equivalent prior-year dates |
| EC-06 | 100k+ orders | Indexed query under performance target |
| EC-07 | Unknown order status | Exclude denominator + data-quality warning |
| EC-08 | Missing vehicle type | Breakdown `UNKNOWN` warning, total preserved |
| EC-09 | Missing scheduled time | Exclude heatmap, increment excluded count |
| EC-10 | Missing started/completed time | Exclude duration average + warning |
| EC-11 | Incomplete online intervals | Online ratio null + warning |
| EC-12 | Audit/login data retention incomplete | Active/retention warning |
| EC-13 | Report query timeout | Controlled `503`, no partial success totals |
| EC-14 | Cache stale after relevant event | Invalidate current-period namespace |
| EC-15 | Response cũ về sau response mới | Frontend ignores stale response |
| EC-16 | One section fails | Other successful sections remain visible |
| EC-17 | Export clicked | No export request; show deferred state |
| EC-18 | Damage deduction present | Display recovery separately, not expense |

---

## Test Cases

| ID | Test | Expected result |
|---|---|---|
| TC-01 | Financial fixtures with fee/refund/deduction | Formulas exact; deduction separate |
| TC-02 | Operations fixtures across canonical statuses | Rates and distributions correct |
| TC-03 | Compare previous equal-length period | Percent changes deterministic |
| TC-04 | Driver earnings/rating/online fixtures | Ranking, ratings and ratio correct |
| TC-05 | Incomplete Driver intervals | Ratio null with data-quality warning |
| TC-06 | Customer activity/retention/spend fixtures | Definitions and top spenders correct |
| TC-07 | Peak-hours local-time fixtures | 168 cells and top-five insights correct |
| TC-08 | Repeat request then relevant insert | First miss, second hit, later invalidated |
| TC-09 | RBAC matrix | Admin 200; non-Admin 403; anonymous 401 |
| TC-10 | Invalid ranges/group/compare | Structured 422 before queries |
| TC-11 | 100k-order performance workload | All endpoints meet p95 target |
| TC-12 | Frontend section failures and stale requests | Independent states; stale ignored |

---

## Required Automated Test Layers

1. Unit tests for every metric formula and zero denominator.
2. Unit tests for period/compare/leap-day resolution.
3. Integration tests for all five endpoint contracts and RBAC.
4. PostgreSQL query-plan tests with 100.000+ orders.
5. Contract tests against Spec 013 transaction aggregates.
6. Data-quality tests for incomplete source events.
7. Cache hit/invalidation tests.
8. Frontend tests for charts, picker, section states and stale requests.

---

## Security and Observability

1. Leaderboards SHALL expose display names only and SHALL not return phone,
   email, address, payment reference or bank information.
2. Audit metadata SHALL store normalized ranges and section names, not result
   rows or personal identifiers.
3. Logs SHALL include correlation ID, report type, duration, cache outcome and
   safe data-quality codes.
4. Metrics SHALL track endpoint latency, cache hit rate, query timeout,
   invalid range and data-quality warning count.
5. Alerts SHOULD trigger when p95 exceeds target, cache hit rate remains below
   target or financial report totals diverge from Spec 013 aggregates.

---

## Constitution Compliance

| Rule | Compliance |
|---|---|
| HR-10 | Every endpoint is Admin-only |
| HR-13 | Report access audited with throttle |
| HR-19 | Move_home forest green/amber charts and controls |
| HR-20 | Vietnamese user-facing text |
| HR-21 | Reuses safe canonical table names |
| AC-08 | Money is BigDecimal/NUMERIC scale zero |
| AC-12 | Aggregation indexes through Flyway |
| AC-14 | Displays canonical VARCHAR statuses |
| AC-15 | Drill-through lists delegate pagination to Spec 011/013 |
| AC-16 | Independent Loading/Empty/Error states |

---

## Definition of Done

1. Five read-only report endpoints satisfy contracts.
2. Exactly 38 FR and nine User Stories are covered.
3. Metric dictionary is implemented and fixture-verified.
4. Compare periods use normalized equal contracts.
5. Financial results match Spec 013 aggregates.
6. Data-quality gaps are visible, never silently fabricated.
7. Five-minute cache and invalidation tests pass.
8. Admin RBAC and audit tests pass.
9. Static fake report values are replaced by API data.
10. Export and scheduled reports remain disabled/deferred.
