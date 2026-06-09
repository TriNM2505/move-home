# Feature Specification: Admin Detail Pages (Order / Driver / Customer)

**Feature Branch:** `012-admin-detail-pages`  
**Feature Number:** #12 of 30 — CORE (Admin oversight detail)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §20 Admin detail operations  
**Constitution reference:** v1.3.0 — HR-05, HR-10 (RBAC ADMIN), HR-11,
HR-13 (audit complete), HR-19, HR-20, HR-21, AC-08, AC-14, AC-15, AC-16  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Admin screens 6.7, 6.8, 6.9  
**Related specs:** Spec #001 Auth/RBAC; Spec #003 Customer Orders; Spec #005 Driver Onboarding;
Spec #006 Driver Workflow; Spec #007 Driver Financial; Spec #008 Manager Driver Approval;
Spec #009 Admin Withdrawal; Spec #010 Manager Disputes; Spec #011 Admin List Pages;
Spec #028 Admin Dashboard

---

## Goals

Admin cần ba trang chi tiết toàn diện để inspect bất kỳ Order, Driver hoặc Customer nào trong hệ
thống Move_home sau khi click một row từ Spec #011. Khác với Manager chỉ xem dữ liệu cần cho một
workflow quyết định cụ thể, Admin có quyền oversight rộng: xem lifecycle đầy đủ, dòng tiền,
documents, history, dispute và audit log nhằm hỗ trợ vận hành, fraud detection, compliance và
final defense demo.

Order detail phải tổng hợp thông tin đơn, parties, tuyến đường, pricing snapshot, payment,
transactions, rating, dispute và timeline canonical. Driver detail phải hiển thị profile, đúng sáu
document canonical qua signed Cloudinary URL, vehicle, deposit, earnings, withdrawals, ratings,
orders và audit. Customer detail phải hiển thị profile, thống kê, order history, wallet
transactions, dispute và login activity nhưng bảo vệ privacy: không hiển thị exact pickup/dropoff
address trong profile analytics, chỉ district và dữ liệu masked khi không cần thiết.

Mỗi trang dùng sticky header với entity, status và actions; tabs tách Overview, History, Audit và
domain sections; read-only mặc định. Admin có thể suspend hoặc reactivate Customer/Driver với
confirm modal, lý do rõ ràng, row lock, token revocation và audit atomic. Suspension không được
phá state machine của order đang chạy; active order cần escalation thủ công. Force-cancel và
export PDF được hiển thị disabled để defer Sprint 6+. Mục tiêu là oversight đầy đủ nhưng vẫn
giữ giới hạn quyền, privacy và audit trail có thể bảo vệ được.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → domain specs → Spec #011 →
> spec này → `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Detail authority | Chỉ `ADMIN` | Manager/Driver/Customer nhận 403 |
| Admin read scope | Full operational detail cần thiết | Không trả password/token/secret/raw private URL |
| Driver documents | Sáu document canonical từ Spec #008 | Không yêu cầu hoặc hiển thị CCCD/selfie |
| Evidence delivery | Signed Cloudinary URL TTL tối đa một giờ | Không expose raw public id/private URL |
| Order timeline | `order_audit_log` + linked domain events | Không suy diễn chỉ từ current status |
| User audit | Unified `audit_log`/auth audit projection | Metadata được redact theo allowlist |
| Suspension targets | Chỉ `DRIVER|CUSTOMER` | Không suspend self, Manager hoặc Admin trong spec này |
| Suspension effect | Status `SUSPENDED`, revoke sessions, block actions ngay | Không hard-delete hoặc sửa financial history |
| Active orders | Không auto-cancel `ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT|IN_DISPUTE` | Tạo escalation/support task; domain state machine vẫn thắng |
| Eligible pending orders | Customer-owned `PENDING_PAYMENT|CONFIRMED` MAY cancel theo policy | Mọi transition phải dùng domain service/audit, không SQL bulk update |
| Reactivation state | Restore `suspension_previous_status` | Không suy luận mơ hồ từ audit; default `ACTIVE` chỉ cho legacy reviewed rows |
| Force cancel | Deferred Sprint 6+ | Button disabled; endpoint contract reserved nhưng không enabled |
| Nested history | Server-side pagination | Không embed unbounded arrays trong detail |
| Money | VND integer/BigDecimal scale 0 | Read-only; không sửa wallet từ detail |

Prompt cũ mô tả Admin “xem mọi thứ”, CCCD, auto-cancel mọi pending order hoặc restore state từ
audit log. Implementation SHALL theo bảng trên: quyền cao không đồng nghĩa bỏ qua privacy, domain
state machine hoặc source of truth.

---

## Scope Summary

**In scope:**

1. `GET /api/admin/orders/{id}` — full Order detail.
2. `GET /api/admin/drivers/{id}` — full Driver detail với signed documents.
3. `GET /api/admin/customers/{id}` — full Customer detail với privacy controls.
4. `GET /api/admin/{entityType}/{id}/audit-log` — paginated audit events.
5. `GET /api/admin/drivers/{id}/orders-history` — paginated Driver orders.
6. `GET /api/admin/customers/{id}/orders-history` — paginated Customer orders.
7. `POST /api/admin/users/{id}/suspend` — suspend Customer/Driver.
8. `POST /api/admin/users/{id}/activate` — reactivate suspended Customer/Driver.
9. Reserved `POST /api/admin/orders/{id}/force-cancel` contract, disabled đến Sprint 6+.
10. Sticky headers, tabs, section-level loading/error và nested pagination.
11. Signed document/photo viewers.
12. Audit log filter theo event/date.
13. Atomic status change, refresh-token revocation và audit.
14. Async notifications sau commit.

**Out of scope:**

1. Edit profile fields.
2. Edit immutable order/pricing/payment history.
3. Force completion.
4. Manager/Admin invitation, promote hoặc role change.
5. Suspend/revoke Staff accounts.
6. Bulk actions.
7. Export PDF/CSV — defer Sprint 6+.
8. Force-cancel implementation — reserved/disabled.
9. Fraud scoring tự động.
10. Exact Customer route-address analytics.

---

## User Stories

**P1 (CORE):**

**US1:** Là Admin, tôi xem full Order detail với timeline, payment, rating, dispute và audit để
điều tra toàn bộ lifecycle.

**US2:** Là Admin, tôi xem Driver detail với documents, vehicle, deposit, earnings, withdrawals,
ratings và order history.

**US3:** Là Admin, tôi xem Customer detail với order history, wallet transactions, dispute,
district activity và login history.

**US4:** Là Admin, tôi lọc audit log theo event type và khoảng ngày để phát hiện hành vi bất
thường.

**US5:** Là Admin, tôi đình chỉ Driver hoặc Customer với lý do để chặn truy cập và hành động ngay.

**US6:** Là Admin, tôi khôi phục một tài khoản đang suspended sau khi đã xác minh.

**P2:**

**US7:** Là Admin, tôi force-cancel order bị kẹt khi domain policy cho phép — defer Sprint 6+.

**US8:** Là Admin, tôi export report Order/User dạng PDF — defer Sprint 6+.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Order Detail (FR-001..FR-008)

**FR-001**  
WHEN authenticated Admin gọi `GET /api/admin/orders/{id}`, THE system SHALL trả HTTP 200 cho
`service_order` tồn tại; WHERE UUID sai format, SHALL trả HTTP 400 `INVALID_PATH_PARAMETER`;
WHERE order không tồn tại hoặc soft-deleted, SHALL trả HTTP 404 `ORDER_NOT_FOUND`.

**FR-002**  
WHEN Order detail được serialize, THE response SHALL có top-level sections:

```json
{
  "order": {},
  "customer": {},
  "driver": null,
  "route": {},
  "pricing_breakdown": {},
  "payment_summary": {},
  "related_transactions": [],
  "rating": null,
  "dispute_summary": null,
  "timeline_preview": [],
  "allowed_actions": []
}
```

`order` SHALL gồm canonical status, code, vehicle, schedule, created/completed/cancelled timestamps
và operational notes đã sanitize; SHALL không trả secret/payment signature.

**FR-003**  
WHEN Customer/Driver section được xây, THE system SHALL trả id, name, masked phone, email,
status và aggregate summary; WHERE order chưa có Driver, `driver=null`; links SHALL navigate
đúng Admin detail route của related entity.

**FR-004**  
WHEN pricing section được trả, THE system SHALL dùng immutable snapshot gồm `base_fare`,
`peak_surcharge`, `alley_surcharge`, `floor_surcharge`, `porter_fee`, `total_quote`,
`deposit_amount`, `final_payment_amount`, `commission_rate_snapshot`, `commission_amount` và
`driver_earning`; amounts SHALL là VND integer và tổng SHALL reconcile.

**FR-005**  
WHEN payment/transaction section được trả, THE system SHALL gồm method, canonical status,
masked VNPay references, paid timestamps và related append-only transactions; SHALL không trả
HMAC, full bank account hoặc gateway secret; transactions preview SHALL giới hạn tối đa 20.

**FR-006**  
WHEN timeline preview được xây, THE system SHALL merge `order_audit_log` và linked trusted
events như payment, rating, dispute bằng timestamp/id ổn định; each item SHALL có event type,
localized label, actor summary, timestamp và redacted metadata; SHALL không suy diễn event chưa
xảy ra.

**FR-007**  
WHEN Order có rating hoặc dispute, THE response SHALL trả summary tương ứng với stars/tags/time
hoặc dispute id/type/status/claim/resolution; WHERE không có, SHALL trả null; Admin SHALL navigate
được đến Manager dispute detail read-only nếu route được hỗ trợ.

**FR-008**  
WHEN `admin/order-detail.html?id={id}` render, THE frontend SHALL có sticky header chứa order
code, canonical status badge và total; năm tabs `Tổng quan`, `Tuyến đường`, `Thanh toán`,
`Đánh giá/Khiếu nại`, `Audit`; button “Hủy đơn (Admin)” và “Tải báo cáo PDF” SHALL disabled với
“Sắp ra mắt”; WHILE loading/error SHALL dùng page/section states theo AC-16.

---

### Nhóm 2 — Driver Detail (FR-009..FR-018)

**FR-009**  
WHEN Admin gọi `GET /api/admin/drivers/{id}`, THE system SHALL trả HTTP 200 chỉ khi target có
`role='DRIVER'` và không soft-deleted; WHERE user tồn tại nhưng role khác, SHALL trả HTTP 404
`DRIVER_NOT_FOUND` để giữ contract entity route.

**FR-010**  
WHEN Driver detail được serialize, THE response SHALL chứa:

```json
{
  "user": {},
  "profile": {},
  "documents_summary": {},
  "vehicles": [],
  "deposit": {},
  "wallet": {},
  "stats": {},
  "rating_distribution": {},
  "recent_orders": [],
  "recent_withdrawals": [],
  "online_status": "OFFLINE",
  "last_known_location": null,
  "allowed_actions": []
}
```

**FR-011**  
WHEN user/profile section được trả, THE system SHALL gồm full name, email, phone, date of birth,
operating districts, account status, email verified, created/last-login/last-active timestamps,
license class và suspension summary; SHALL không trả password hash, refresh token hoặc exact
location history.

**FR-012**  
WHEN documents được lấy, THE system SHALL chỉ dùng sáu canonical pairs:
`DRIVING_LICENSE/FRONT`, `DRIVING_LICENSE/BACK`, `VEHICLE_REGISTRATION/FRONT`,
`VEHICLE_PHOTO/FRONT`, `VEHICLE_PHOTO/REAR`, `VEHICLE_PHOTO/SIDE`; each item SHALL có status,
reviewer/time và signed URL TTL tối đa 3600 giây; SHALL không yêu cầu CCCD/selfie.

**FR-013**  
WHEN vehicles/deposit được trả, THE system SHALL gồm tất cả non-deleted vehicles với primary
marker, plate, type, year, status và signed photos; deposit SHALL gồm amount/status/paid time và
masked VNPay ref từ canonical `driver_deposit`; inconsistent primary/deposit data SHALL tạo
warning, không được tự sửa.

**FR-014**  
WHEN Driver stats được tính, THE system SHALL trả `total_completed_orders`,
`total_cancelled_orders`, `total_dispute_count`, `total_earnings`, `current_balance`,
`total_withdrawn`, `deposit_amount`, `average_rating`, `total_ratings_count`; aggregates SHALL
không bị nhân chéo do JOIN và money SHALL reconcile với wallet transactions.

**FR-015**  
WHEN rating distribution được trả, THE system SHALL có count star 1..5 và tổng bằng
`total_ratings_count`; recent orders/withdrawals preview SHALL giới hạn 10 mỗi loại, sort newest
first và dùng canonical status/amount labels.

**FR-016**  
WHEN Driver online status được trả, THE system SHALL dùng `ONLINE|OFFLINE|BUSY`; last known
location SHALL chỉ trả lat/lng khi record mới hơn năm phút và Driver có active order, kèm
`updated_at`; WHERE stale/không active, SHALL trả null để bảo vệ privacy.

**FR-017**  
WHEN Driver status là `ACTIVE` hoặc onboarding/rejected state, frontend SHALL hiển thị action
“Đình chỉ tài xế” chỉ khi `allowed_actions` chứa `SUSPEND_USER`; WHEN status `SUSPENDED`, SHALL
hiển thị “Khôi phục”; Admin SHALL không thấy action “Duyệt lại hồ sơ” vì approval thuộc Spec #008.

**FR-018**  
WHEN `admin/driver-detail.html?id={id}` render, THE frontend SHALL có sticky header và tabs
`Tổng quan`, `Giấy tờ`, `Hồ sơ xe`, `Đơn hàng`, `Thu nhập và Rút tiền`, `Audit`; signed images
SHALL mở accessible lightbox; section error SHALL không làm mất các section đã tải thành công.

---

### Nhóm 3 — Customer Detail (FR-019..FR-028)

**FR-019**  
WHEN Admin gọi `GET /api/admin/customers/{id}`, THE system SHALL trả HTTP 200 chỉ khi target có
`role='CUSTOMER'` và không soft-deleted; WHERE target không tồn tại/role khác, SHALL trả 404
`CUSTOMER_NOT_FOUND`.

**FR-020**  
WHEN Customer detail được serialize, THE response SHALL chứa:

```json
{
  "user": {},
  "stats": {},
  "recent_orders": [],
  "wallet_summary": {},
  "recent_wallet_transactions": [],
  "dispute_history_preview": [],
  "district_activity": [],
  "login_history": [],
  "allowed_actions": []
}
```

**FR-021**  
WHEN Customer user section được trả, THE system SHALL gồm full name, email, partially masked
phone, status, email verified, signed avatar URL nếu có, created/last-login timestamps và
suspension summary; SHALL không trả password, refresh token, date-of-birth nếu không cần cho
oversight hoặc exact residential address.

**FR-022**  
WHEN Customer stats được tính, THE system SHALL trả `total_orders`, `total_completed`,
`total_cancelled`, `total_dispute_count`, `total_spent`, `wallet_balance`, `total_topped_up`,
`first_order_at`, `last_order_at`; money SHALL dùng BigDecimal/NUMERIC scale 0 và aggregates
SHALL không nhân chéo.

**FR-023**  
WHEN recent orders được trả, THE system SHALL giới hạn 10 newest rows với code, canonical status,
pickup/dropoff districts, total và timestamps; SHALL không trả exact pickup/dropoff addresses
trong Customer detail analytics.

**FR-024**  
WHEN wallet section được trả, THE system SHALL trả current balance, totals và tối đa 20 recent
append-only wallet transactions với masked references; SHALL không cho phép adjustment hoặc
mutation từ detail page.

**FR-025**  
WHEN dispute history preview được trả, THE system SHALL gồm tối đa 10 dispute do Customer tạo,
với order code, claim type/amount, status và resolution summary; internal Manager comments SHALL
không xuất hiện trong Customer detail.

**FR-026**  
WHEN district activity được tính, THE system SHALL trả unique pickup/dropoff district và counts;
SHALL không trả exact address, coordinates hoặc route sequence; login history SHALL giới hạn 20
events, mask IP phù hợp và redact user-agent fingerprint nhạy cảm.

**FR-027**  
WHEN Customer status không `SUSPENDED`, frontend SHALL hiển thị “Đình chỉ tài khoản” chỉ khi
allowed; WHEN `SUSPENDED`, SHALL hiển thị “Khôi phục”; all action modals SHALL nêu rõ tác động
đến session và active orders.

**FR-028**  
WHEN `admin/customer-detail.html?id={id}` render, THE frontend SHALL có sticky header và tabs
`Tổng quan`, `Đơn hàng`, `Ví và Giao dịch`, `Khiếu nại`, `Audit`; WHILE loading/empty/error,
SHALL render states theo section; phone SHALL hiển thị dạng masked như `098****567`.

---

### Nhóm 4 — Audit Log Filter (FR-029..FR-033)

**FR-029**  
WHEN Admin gọi
`GET /api/admin/{entityType}/{id}/audit-log?page=0&size=50&event_type=ALL&date_from=&date_to=`,
THE system SHALL cho phép `entityType=orders|drivers|customers`, verify entity tồn tại và trả
Spring Page events sort `created_at DESC, id DESC`.

**FR-030**  
WHEN audit item được serialize, THE response SHALL gồm `event_type`, localized label, actor id,
actor display name, actor role, masked IP, summarized user agent, timestamp, previous/new state
và metadata đã redact theo event allowlist; SHALL không trả secret, token, password, signed URL,
full bank account hoặc internal raw exception.

**FR-031**  
WHEN `event_type` là `ALL` hoặc value thuộc server allowlist cho entity, THE system SHALL filter
tương ứng; WHERE invalid event/date range, range lớn hơn 366 ngày hoặc page size ngoài
`10|20|50|100`, SHALL trả HTTP 422 structured error.

**FR-032**  
WHEN Audit tab render, THE frontend SHALL hiển thị timeline icons/colors theo event category,
actor, timestamp, state transition và expandable redacted metadata; WHERE content rỗng, SHALL
hiển thị “Không có log audit”; pagination SHALL theo AC-15.

**FR-033**  
WHEN Admin xem detail hoặc audit page, THE system SHALL append/throttle read audit
`ADMIN_ENTITY_DETAIL_ACCESSED` tối đa một event mỗi Admin/entity/60 giây; audit viewing SHALL
không tự xuất hiện trong chính response đang tải để tránh recursion/confusion.

---

### Nhóm 5 — Suspend User (FR-034..FR-039)

**FR-034**  
WHEN Admin click suspend, THE frontend SHALL mở destructive confirm modal hiển thị target,
current status, active-order warnings, textarea reason và optional duration; confirm SHALL
disabled đến khi Admin nhập chính xác target name/code hoặc xác nhận checkbox mạnh.

**FR-035**  
WHEN Admin gọi `POST /api/admin/users/{id}/suspend` với `Idempotency-Key: <uuid>`, body SHALL là:

```json
{
  "reason": "Phát hiện hoạt động bất thường, cần tạm khóa để xác minh giao dịch.",
  "duration_days": 30
}
```

`reason` SHALL required, trim length `30..1000`, có chữ cái; `duration_days` optional integer
`1..365`, null nghĩa indefinite.

**FR-036**  
WHEN suspend request bắt đầu, THE backend SHALL mở transaction, verify actor authoritative role
`ADMIN`, lock target `app_user FOR UPDATE`, verify target role `DRIVER|CUSTOMER`, target khác
actor, không soft-deleted và status khác `SUSPENDED`; WHERE target là Staff/self hoặc state
invalid, SHALL rollback và trả HTTP 403/409 phù hợp.

**FR-037**  
WHEN suspend validation thành công, THE system SHALL trong cùng transaction update:

```sql
UPDATE app_user
SET suspension_previous_status = status,
    status = 'SUSPENDED',
    suspended_at = NOW(),
    suspended_by = :admin_id,
    suspension_reason = :reason,
    suspension_until = :suspension_until,
    updated_at = NOW()
WHERE id = :target_id
  AND role IN ('DRIVER', 'CUSTOMER')
  AND status <> 'SUSPENDED';

UPDATE refresh_token
SET revoked_at = NOW()
WHERE user_id = :target_id
  AND revoked_at IS NULL;
```

and insert audit `USER_SUSPENDED`; guarded update SHALL affect exactly one target.

**FR-038**  
WHEN suspended target có related orders, THE system SHALL invoke domain services sau target lock
theo lock order đã thống nhất: Customer orders ở `PENDING_PAYMENT|CONFIRMED` MAY cancel theo
policy/audit; Driver future assignments MAY reassign; orders
`ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT|IN_DISPUTE` SHALL không auto-cancel mà SHALL tạo
`SUSPENDED_USER_ACTIVE_ORDER_REVIEW` escalation và notify support/affected parties.

**FR-039**  
WHEN suspension transaction commit, THE system SHALL trả HTTP 200 message
“Đã đình chỉ tài khoản”, publish session-revoked/security event và enqueue email tiếng Việt chứa
sanitized reason/duration/support channel; WHERE email/notification fail, SHALL retry async và
không rollback; same idempotency key/payload SHALL replay response.

---

### Nhóm 6 — Activate User (FR-040..FR-043)

**FR-040**  
WHEN Admin click “Khôi phục”, THE frontend SHALL mở confirm modal hiển thị suspension reason,
time, previous status và optional note; SHALL cảnh báo khôi phục không tự resume/cancel/reassign
order cũ.

**FR-041**  
WHEN Admin gọi `POST /api/admin/users/{id}/activate` với idempotency key và body
`{"note":"Đã xác minh giao dịch và hoàn tất kiểm tra tài khoản."}`, THE system SHALL validate
note optional max 1000, lock target `app_user`, require role `DRIVER|CUSTOMER` và current status
`SUSPENDED`; WHERE không suspended, SHALL trả HTTP 409 `USER_NOT_SUSPENDED`.

**FR-042**  
WHEN activation validation thành công, THE system SHALL restore
`suspension_previous_status` nếu value hợp lệ cho target role; WHERE legacy row thiếu previous
status, SHALL default `ACTIVE` chỉ sau eligibility check; SHALL clear suspension fields, set
previous status null và insert audit `USER_REACTIVATED` trong cùng transaction.

**FR-043**  
WHEN activation commit, THE system SHALL trả HTTP 200 message “Đã kích hoạt lại tài khoản” và
enqueue email; SHALL không tạo refresh token, tự đăng nhập user, restore active order hoặc bypass
Driver deposit/onboarding requirements.

---

### Nhóm 7 — RBAC + Performance (FR-044..FR-046)

**FR-044**  
WHEN bất kỳ endpoint spec này được gọi, THE system SHALL yêu cầu valid JWT và authoritative role
`ADMIN`; WHERE caller role khác, SHALL trả HTTP 403; WHERE thiếu/hết hạn JWT, SHALL trả HTTP 401
theo Spec #001.

**FR-045**  
WHEN detail trả nested preview, THE system SHALL giới hạn recent orders/withdrawals/disputes ở
10, transactions/login events ở 20, timeline preview ở 50; full history/audit SHALL dùng
server-side pagination size `10|20|50|100`; SHALL tránh N+1 và unbounded response.

**FR-046**  
WHEN non-mutating detail GET thành công, THE system MAY cache section aggregates tối đa năm phút
theo entity/version; WHERE suspend/activate/domain state change commit, SHALL invalidate related
cache; sensitive signed URLs, location, login history và audit SHALL không cache shared.

---

## Non-Functional Requirements

**NFR-001 — Order detail latency**  
Order detail SHALL hoàn tất dưới 1,5 giây ở p95 với multiple joins và previews.

**NFR-002 — Driver detail latency**  
Driver detail SHALL hoàn tất dưới 1,5 giây ở p95; signed document generation không expose raw URL.

**NFR-003 — Customer detail latency**  
Customer detail SHALL hoàn tất dưới 1,5 giây ở p95, không scan exact addresses.

**NFR-004 — Audit performance**  
Audit query SHALL hoàn tất dưới 800 ms ở p95 với hơn một triệu rows và indexes.

**NFR-005 — Suspend transaction**  
Suspend/activate SHALL hoàn tất dưới ba giây ở p95; DB lock target dưới một giây ở p95.

**NFR-006 — Atomicity**  
Status change, token revocation, audit và required domain transitions SHALL all-or-nothing theo
transaction boundary; async notifications chạy sau commit.

**NFR-007 — Confirmation UX**  
Mọi destructive Admin action SHALL yêu cầu confirm modal chống thao tác nhầm.

**NFR-008 — Privacy**  
Customer analytics SHALL không trả exact addresses/coordinates; secrets/tokens luôn bị loại.

**NFR-009 — Availability**  
Section optional failure SHALL không làm mất core identity/status; action disabled nếu eligibility
hoặc state không xác minh được.

**NFR-010 — Accessibility**  
Sticky header, tabs, lightbox, tables, pagination và modals SHALL keyboard/screen-reader usable.

**NFR-011 — Audit durability**  
Mọi suspend/reactivate/domain transition SHALL có append-only audit; audit failure rollback.

**NFR-012 — UX quality**  
Ba pages SHALL responsive, dùng Move_home brand, tiếng Việt có dấu và đủ Loading/Empty/Error.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/admin/orders/{id}` | Path UUID | 200 Order detail | Admin |
| GET | `/api/admin/drivers/{id}` | Path UUID | 200 Driver detail | Admin |
| GET | `/api/admin/customers/{id}` | Path UUID | 200 Customer detail | Admin |
| GET | `/api/admin/{entityType}/{id}/audit-log` | `page,size,event_type,date_from,date_to` | 200 Page | Admin |
| GET | `/api/admin/drivers/{id}/orders-history` | `page,size,status,sort` | 200 Page | Admin |
| GET | `/api/admin/customers/{id}/orders-history` | `page,size,status,sort` | 200 Page | Admin |
| POST | `/api/admin/users/{id}/suspend` | `{reason,duration_days}` | 200 suspended | Admin |
| POST | `/api/admin/users/{id}/activate` | `{note}` | 200 reactivated | Admin |
| POST | `/api/admin/orders/{id}/force-cancel` | Deferred/disabled | 501 until Sprint 6+ | Admin |

### Common Error Format

```json
{
  "timestamp": "2026-06-09T03:20:00Z",
  "status": 409,
  "error_code": "USER_ALREADY_SUSPENDED",
  "message": "Tài khoản đã bị đình chỉ.",
  "path": "/api/admin/users/9ac469f5-47d8-441f-99c0-b1c6941c8fb3/suspend",
  "request_id": "01JY...",
  "details": {
    "current_status": "SUSPENDED"
  }
}
```

---

## Data Model

Spec này reuse domain tables và mở rộng `app_user` cho suspension source of truth.

### Suspension Extensions

```sql
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS suspension_previous_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS suspended_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS suspension_reason TEXT,
    ADD COLUMN IF NOT EXISTS suspension_until TIMESTAMPTZ;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_suspension_fields
    CHECK (
        (status = 'SUSPENDED'
            AND suspended_at IS NOT NULL
            AND suspended_by IS NOT NULL
            AND suspension_reason IS NOT NULL
            AND suspension_previous_status IS NOT NULL)
        OR
        (status <> 'SUSPENDED'
            AND suspended_at IS NULL
            AND suspended_by IS NULL
            AND suspension_reason IS NULL
            AND suspension_until IS NULL
            AND suspension_previous_status IS NULL)
    );
```

`suspension_previous_status` SHALL dùng `VARCHAR + CHECK`/service allowlist theo role, không
PostgreSQL ENUM. Legacy suspended rows cần backfill/manual review trước khi add constraint.

### Audit Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_created
    ON audit_log (entity_type, entity_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity_event_created
    ON audit_log (entity_type, entity_id, event_type, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_created
    ON audit_log (actor_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_order_audit_order_created
    ON order_audit_log (order_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_refresh_token_active_user
    ON refresh_token (user_id)
    WHERE revoked_at IS NULL;
```

### Suspension Audit Contract

```json
{
  "event_type": "USER_SUSPENDED",
  "actor_id": "admin-uuid",
  "actor_role": "ADMIN",
  "entity_type": "USER",
  "entity_id": "target-uuid",
  "previous_state": "ACTIVE",
  "new_state": "SUSPENDED",
  "request_id": "01JY...",
  "metadata": {
    "target_role": "DRIVER",
    "reason_hash": "sha256:...",
    "duration_days": 30,
    "active_order_escalation_count": 1,
    "revoked_refresh_token_count": 2
  }
}
```

Audit metadata SHALL không chứa full suspension reason, tokens, signed URLs hoặc exact Customer
address. User-facing reason vẫn lưu trong protected `app_user.suspension_reason`.

---

## Query Contracts

### Driver Stats

Driver stats SHALL dùng isolated aggregates hoặc reporting projections:

```sql
SELECT
    COUNT(*) FILTER (WHERE status = 'COMPLETED') AS total_completed_orders,
    COUNT(*) FILTER (WHERE status = 'CANCELLED') AS total_cancelled_orders,
    COUNT(*) FILTER (WHERE status = 'IN_DISPUTE') AS total_dispute_count,
    COALESCE(SUM(driver_earning) FILTER (WHERE status = 'COMPLETED'), 0) AS order_earnings
FROM service_order
WHERE driver_id = :driver_id
  AND deleted_at IS NULL;
```

Wallet earnings source of truth vẫn là `driver_wallet + wallet_transaction`; mismatch SHALL tạo
warning, không tự sửa.

### Customer District Activity

```sql
SELECT district, SUM(event_count) AS usage_count
FROM (
    SELECT pickup_district AS district, COUNT(*) AS event_count
    FROM service_order
    WHERE customer_id = :customer_id AND deleted_at IS NULL
    GROUP BY pickup_district
    UNION ALL
    SELECT dropoff_district AS district, COUNT(*) AS event_count
    FROM service_order
    WHERE customer_id = :customer_id AND deleted_at IS NULL
    GROUP BY dropoff_district
) activity
GROUP BY district
ORDER BY usage_count DESC, district ASC;
```

Query SHALL không select exact address hoặc coordinates.

### Audit Projection

```sql
SELECT id,
       event_type,
       actor_id,
       actor_role,
       previous_state,
       new_state,
       metadata,
       created_at
FROM audit_log
WHERE entity_type = :entity_type
  AND entity_id = :entity_id
  AND (:all_events OR event_type = :event_type)
  AND created_at >= :from_utc
  AND created_at < :to_exclusive_utc
ORDER BY created_at DESC, id DESC
LIMIT :size OFFSET :offset;
```

Metadata SHALL pass redaction service trước serialization.

---

## Transaction Boundaries

### Suspend Transaction

```text
BEGIN
  verify Admin from authoritative app_user
  lock target app_user FOR UPDATE
  validate target role/status/self/deleted_at
  persist/check idempotency record
  discover related eligible orders and lock via domain-defined order
  update target to SUSPENDED
  revoke all active refresh tokens
  invoke allowed cancellation/reassignment transitions with domain audits
  create active-order escalation rows/events
  insert USER_SUSPENDED audit
  persist idempotency response
COMMIT
publish session revoked + enqueue notifications/email
```

WHERE any required audit, target update, token revoke query or allowed domain transition fails
unexpectedly, THE transaction SHALL rollback. Notifications occur after commit.

### Activate Transaction

```text
BEGIN
  verify Admin
  lock target app_user FOR UPDATE
  validate target role/status and previous status eligibility
  update target to restored status and clear suspension fields
  insert USER_REACTIVATED audit
  persist idempotency response
COMMIT
enqueue email
```

### Lock Order

```text
actor app_user → target app_user → related service_order/assignment
→ refresh_token → audit_log → idempotency record
```

Implementation SHALL coordinate with domain lock order before enabling related-order transitions.
If a safe global order cannot be proven, suspend SHALL create escalation instead of mutating orders.

---

## State Machine

```text
User Suspension:

DRIVER/CUSTOMER eligible non-suspended status
  |-- Admin suspend + reason --> SUSPENDED

SUSPENDED
  |-- Admin reactivate -------> suspension_previous_status
  |-- Expiry job future ------> suspension_previous_status

Staff:
MANAGER/ADMIN -- no suspend action in this spec

Order force-cancel:
Deferred/disabled until Sprint 6+ domain contract.
```

Rules:

1. Invalid suspend/reactivate transition trả HTTP 409 theo HR-05.
2. Suspend self/Staff trả HTTP 403.
3. Suspension không hard-delete user hoặc financial history.
4. Suspension không tự force-complete/cancel active/disputed order.
5. Reactivation không tự restore session hoặc operational assignment.
6. Auto-reactivation expiry job là future; `suspension_until` chỉ metadata trong Sprint 5.

---

## Action Eligibility Matrix

| Target | Condition | Allowed action | Blocking behavior |
|--------|-----------|----------------|-------------------|
| Driver | Any valid non-suspended Driver state | Suspend | Active order warning/escalation |
| Driver | `SUSPENDED`, previous status valid | Reactivate | Deposit/onboarding eligibility re-check |
| Customer | `PENDING_VERIFY|ACTIVE` | Suspend | Eligible pending orders domain-policy cancel |
| Customer | `SUSPENDED`, previous status valid | Reactivate | Does not restore orders/session |
| Manager/Admin | Any | None | 403 `STAFF_SUSPENSION_OUT_OF_SCOPE` |
| Self Admin | Any | None | 403 `CANNOT_SUSPEND_SELF` |
| Order | Any | View only | Force cancel disabled |

Backend detail DTO SHALL trả `allowed_actions` và `blocking_warnings`; frontend không tự suy diễn.

---

## Error Matrix

| Scenario | HTTP | `error_code` | Message |
|----------|------|--------------|---------|
| Không có JWT | 401 | `AUTHENTICATION_REQUIRED` | Phiên đăng nhập không hợp lệ |
| Không phải Admin | 403 | `FORBIDDEN` | Bạn không có quyền truy cập |
| Suspend self | 403 | `CANNOT_SUSPEND_SELF` | Không thể đình chỉ chính tài khoản của bạn |
| Target Staff | 403 | `STAFF_SUSPENSION_OUT_OF_SCOPE` | Không thể đình chỉ tài khoản nhân sự tại đây |
| Order không tồn tại | 404 | `ORDER_NOT_FOUND` | Không tìm thấy đơn hàng |
| Driver không tồn tại | 404 | `DRIVER_NOT_FOUND` | Không tìm thấy tài xế |
| Customer không tồn tại | 404 | `CUSTOMER_NOT_FOUND` | Không tìm thấy khách hàng |
| User đã suspended | 409 | `USER_ALREADY_SUSPENDED` | Tài khoản đã bị đình chỉ |
| User chưa suspended | 409 | `USER_NOT_SUSPENDED` | Tài khoản chưa bị đình chỉ |
| Previous status invalid | 409 | `INVALID_REACTIVATION_STATE` | Không thể xác định trạng thái khôi phục |
| Concurrent action | 409 | `USER_STATUS_CHANGED` | Trạng thái tài khoản đã thay đổi |
| Reason invalid | 422 | `INVALID_SUSPENSION_REASON` | Lý do đình chỉ không hợp lệ |
| Duration invalid | 422 | `INVALID_SUSPENSION_DURATION` | Thời hạn đình chỉ không hợp lệ |
| Audit filter invalid | 422 | `INVALID_AUDIT_FILTER` | Bộ lọc audit không hợp lệ |
| Signed URL failure | 503 | `DOCUMENT_PROVIDER_UNAVAILABLE` | Không thể tải tài liệu |
| Force cancel disabled | 501 | `FEATURE_NOT_ENABLED` | Tính năng chưa được kích hoạt |
| Audit write fail | 500 | `AUDIT_WRITE_FAILED` | Không thể hoàn tất thao tác |

---

## Frontend Screen Contract

### Shared Detail Layout

Mỗi page SHALL có sticky header, back link về Spec #011 list, entity title, canonical status,
primary metrics, tabs, actions từ `allowed_actions`, Loading/Error states và responsive layout.
Tabs SHALL update URL hash/query để reload/back giữ context.

### `frontend/pages/admin/order-detail.html?id={id}`

Required: overview, related parties, route, pricing, payment/transactions, timeline,
rating/dispute và Audit. Exact order route được phép trong single-order investigation; Customer
profile analytics vẫn không được hiển thị exact address. Legacy “Hủy đơn” SHALL disabled.

### `frontend/pages/admin/driver-detail.html?id={id}`

Required: profile header, stats, six canonical documents, vehicles, deposit, wallet/earnings,
orders, withdrawals, rating distribution, online status, privacy-safe location và Audit. Legacy
“Duyệt lại hồ sơ” SHALL bị loại; suspend/reactivate theo allowed actions.

### `frontend/pages/admin/customer-detail.html?id={id}`

Required: masked identity, stats, district-only recent order routes, wallet/transactions,
disputes, login activity và Audit. Legacy full phone/exact address SHALL không render.

---

## Security & Privacy

1. Chỉ Admin gọi được chín endpoints.
2. Password/token/HMAC/provider secret không xuất hiện trong response/log/audit.
3. Driver documents dùng signed URL TTL tối đa một giờ.
4. Customer profile analytics chỉ district, không exact route/address/coordinates.
5. Order detail MAY hiển thị exact route vì là investigation theo entity cụ thể.
6. Phone, IP, user agent và references được mask/redact theo context.
7. Audit metadata qua allowlist/redaction service.
8. Suspend/reactivate yêu cầu idempotency key, confirm modal và rate limit.
9. Staff/self suspension bị chặn.
10. Refresh tokens chỉ revoke, không trả raw value.
11. Money/history append-only và read-only từ detail.
12. Signed URLs, audit, login history và location không shared-cache.

---

## Observability & Reconciliation

| Metric | Type | Labels |
|--------|------|--------|
| `admin_detail_request_total` | Counter | `entity_type`, `result` |
| `admin_detail_duration_seconds` | Histogram | `entity_type`, `result` |
| `admin_audit_query_duration_seconds` | Histogram | `entity_type`, `has_filter` |
| `admin_user_action_total` | Counter | `action`, `target_role`, `result` |
| `admin_user_action_lock_wait_seconds` | Histogram | `action`, `result` |
| `admin_active_order_escalation_total` | Counter | `target_role`, `order_status` |
| `admin_detail_redaction_failure_total` | Counter | `entity_type`, `field_type` |

Alerts: audit/redaction failure; suspend/reactivate error spike; lock deadlock; detail p95 vượt
target; Driver wallet/order earning mismatch; Customer wallet transaction mismatch; sensitive
field serialization detected.

---

## Acceptance Criteria

**AC1 — Order detail completeness**  
GIVEN Order ở bất kỳ canonical state, WHEN Admin mở detail, THEN identity, parties, route,
pricing, payment, transactions, rating/dispute, timeline và audit navigation hiển thị đúng.

**AC2 — Driver detail completeness**  
GIVEN Driver có documents/vehicle/deposit/wallet/history, WHEN Admin mở detail, THEN đúng sáu
canonical documents signed, stats reconcile và nested previews bị giới hạn.

**AC3 — Customer detail privacy**  
GIVEN Customer có nhiều orders, WHEN Admin mở profile detail, THEN stats/wallet/dispute/login
hiển thị nhưng exact addresses/coordinates không xuất hiện.

**AC4 — Audit filter**  
GIVEN hơn 100 audit events, WHEN Admin filter event/date và paginate, THEN results newest-first,
stable, redacted và dưới performance target.

**AC5 — Suspend success**  
GIVEN eligible Driver/Customer non-suspended, WHEN Admin suspend với reason hợp lệ, THEN status,
suspension fields, token revocation và audit commit atomic; email async.

**AC6 — Active order protection**  
GIVEN target có `IN_PROGRESS|IN_DISPUTE` order, WHEN Admin suspend, THEN account bị khóa nhưng
order không auto-cancel; escalation/audit/notification được tạo.

**AC7 — Reactivate success**  
GIVEN suspended target có previous status hợp lệ, WHEN Admin reactivate, THEN status restore,
suspension fields clear, audit/email tạo và không auto-login/resume orders.

**AC8 — Concurrent user action**  
GIVEN hai Admin suspend/reactivate cùng user, WHEN chạy đồng thời, THEN đúng một valid transition
commit; request thua 409; không duplicate audit/email.

**AC9 — RBAC and Staff guard**  
GIVEN Admin/Manager/Driver/Customer token và Staff/self target, WHEN gọi APIs, THEN chỉ Admin
được đọc; suspend Staff/self bị 403.

**AC10 — UI quality**  
GIVEN loading, partial failure, empty nested history và terminal statuses, WHEN render ba pages,
THEN sticky header/tabs/states/keyboard behavior và tiếng Việt có dấu đúng.

**AC11 — Force cancel deferred**  
GIVEN Admin click force-cancel trong Sprint 5, WHEN page render/call reserved endpoint, THEN button
disabled hoặc API 501; order không đổi.

**AC12 — Audit failure rollback**  
GIVEN audit insert fail, WHEN suspend/reactivate chạy, THEN user/tokens/domain transitions rollback
và không gửi notification.

---

## Edge Cases & Error Handling

### EC-01 — Driver có order IN_PROGRESS khi suspend

Expected: Driver session revoke/status suspended; order không cancel; support escalation và
affected-party notification được tạo.

### EC-02 — Customer có active order khi suspend

Expected: account bị khóa; active order không auto-cancel; Driver/support được notify để xử lý.

### EC-03 — Customer có PENDING_PAYMENT/CONFIRMED orders

Expected: domain policy MAY cancel từng order với audit; nếu transition fail thì suspension
rollback hoặc escalation theo transaction contract, không SQL bulk update.

### EC-04 — Driver có future assignment

Expected: assignment MAY reassign bằng domain service; no duplicate Driver assignment.

### EC-05 — Suspend target đang PENDING_APPROVAL

Expected: status suspended và previous status lưu; reactivate restore pending approval, không
auto-approve.

### EC-06 — Suspend self

Expected: HTTP 403; không revoke Admin session; security audit optional.

### EC-07 — Suspend Manager/Admin khác

Expected: HTTP 403 out-of-scope; không state/token change.

### EC-08 — Hai Admin suspend cùng user

Expected: row lock; one success, one 409/idempotent replay; one state audit.

### EC-09 — Admin suspend và reactivate đồng thời

Expected: serialized transitions; final state khớp commit order, không field inconsistency.

### EC-10 — Reactivate non-suspended user

Expected: HTTP 409 `USER_NOT_SUSPENDED`; no writes.

### EC-11 — Legacy suspended row thiếu previous status

Expected: activation blocked/manual eligibility review hoặc safe ACTIVE only after role checks;
không đoán onboarding state.

### EC-12 — Suspension duration hết hạn

Expected: Sprint 5 không auto-reactivate; UI hiển thị expired warning; future job xử lý sau.

### EC-13 — Audit query date range quá lớn

Expected: HTTP 422 trên 366 ngày; yêu cầu thu hẹp range; không full-table scan.

### EC-14 — Audit metadata chứa secret

Expected: redaction removes field; security alert; raw metadata không serialize.

### EC-15 — Signed document URL hết hạn

Expected: Admin reload document section để lấy URL mới; raw public id không lộ.

### EC-16 — Driver có multiple primary vehicles

Expected: detail warning/data-integrity alert; không duplicate stats hoặc tự sửa.

### EC-17 — Wallet/order aggregate mismatch

Expected: detail warning và reconciliation alert; không auto-adjust money.

### EC-18 — Customer exact address lọt vào DTO

Expected: privacy contract/security test fail; response serialization blocked/redacted.

### EC-19 — Related entity soft-deleted

Expected: detail giữ immutable summary/masked fallback; link disabled; không 500 toàn page.

### EC-20 — Force-cancel button gọi API

Expected: 501 `FEATURE_NOT_ENABLED`; no order/audit state transition.

---

## Test Cases

### TC-001 — Order Detail Full Contract

**Type:** Integration  
**Given:** Completed Order có payment, rating, dispute và transactions.  
**When:** Admin tải detail.  
**Then:** Sections/timeline/snapshots đúng, references masked, no secrets.

### TC-002 — Driver Detail Documents And Stats

**Type:** Integration/Security  
**Given:** Driver có six documents, vehicles, deposit, ratings, wallet và histories.  
**When:** Admin tải detail.  
**Then:** Signed URLs, one primary marker, aggregates/reconciliation đúng, no CCCD requirement.

### TC-003 — Customer Detail Privacy

**Type:** Security/Integration  
**Given:** Customer có exact-address orders và login events.  
**When:** Admin tải detail.  
**Then:** Districts/masked identity hiển thị; exact addresses/coordinates/secrets không xuất hiện.

### TC-004 — Audit Pagination And Redaction

**Type:** Integration/Security  
**Given:** 150 mixed audit events, một event có secret fixture.  
**When:** Admin filter/date/paginate.  
**Then:** Stable newest-first Page, secret redacted, invalid range 422.

### TC-005 — Suspend Driver Happy Path

**Type:** Integration  
**Given:** Active Driver không active order, có two refresh tokens.  
**When:** Admin suspend.  
**Then:** Suspended fields set, tokens revoked, one audit/email outbox, previous state ACTIVE.

### TC-006 — Suspend With Active Order

**Type:** Integration  
**Given:** Driver có IN_PROGRESS order.  
**When:** Admin suspend.  
**Then:** Driver suspended, order unchanged, escalation/audit/notifications created.

### TC-007 — Reactivate Happy Path

**Type:** Integration  
**Given:** Suspended Customer previous ACTIVE.  
**When:** Admin reactivate.  
**Then:** ACTIVE restored, fields cleared, no token issued/order resumed, audit/email created.

### TC-008 — Concurrent Suspend

**Type:** PostgreSQL Concurrency  
**Given:** 50 Admin requests suspend same user.  
**When:** Calls start simultaneously.  
**Then:** One transition/audit/outbox, others replay/409, no deadlock.

### TC-009 — Staff And Self Guard

**Type:** Security  
**Given:** Target self Admin, other Admin, Manager, Driver, Customer.  
**When:** Admin suspend each.  
**Then:** Only Driver/Customer eligible; Staff/self 403; zero unintended token revocation.

### TC-010 — RBAC Matrix

**Type:** Security  
**Given:** Admin, Manager, Driver, Customer, anonymous.  
**When:** Each calls nine endpoints.  
**Then:** Admin follows contract; non-Admin 403; anonymous 401.

### TC-011 — Audit Failure Rollback

**Type:** Fault Injection  
**Given:** Audit insert forced fail.  
**When:** Admin suspend.  
**Then:** User status/tokens/order effects unchanged; no email.

### TC-012 — Force Cancel Disabled

**Type:** Contract  
**Given:** Any Order.  
**When:** Reserved force-cancel endpoint called.  
**Then:** HTTP 501, no writes.

---

## Required Automated Test Layers

1. Unit tests cho action eligibility, restore-state allowlist, redaction và DTO limits.
2. Integration tests cho three detail aggregates và nested pagination.
3. PostgreSQL/Testcontainers concurrency tests cho suspend/reactivate locks.
4. Contract tests với Order/Driver/Wallet/Withdrawal/Dispute owners.
5. Security tests cho RBAC, signed URLs, privacy, secret redaction và Staff/self guard.
6. Fault-injection tests cho audit/token/domain transition failure.
7. Frontend tests cho tabs, partial errors, lightbox, modals và disabled actions.
8. CORE coverage tối thiểu 70% theo ES-05.

---

## Migration & Rollout Plan

1. Xác nhận unified audit projection và domain event mappings.
2. Add suspension columns/indexes bằng Flyway; backfill legacy rows.
3. Deploy read-only Order detail và audit.
4. Deploy read-only Driver/Customer details với privacy/redaction tests.
5. Migrate UI stubs sang sticky headers/tabs và remove invalid actions.
6. Deploy nested history endpoints.
7. Deploy suspend/reactivate sau concurrency/domain contract tests.
8. Enable notification/outbox, metrics và reconciliation warnings.
9. Keep force-cancel/PDF disabled đến Sprint 6+.

Rollout SHALL dùng feature flag cho suspension. Nếu active-order domain lock order chưa được chứng
minh an toàn, action vẫn suspend user nhưng chỉ tạo escalation, không mutate related orders.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Invalid suspend/reactivate/domain transition trả 409 |
| HR-10 | Chỉ Admin; role khác 403 |
| HR-11 | Email/notification async, không rollback sau commit |
| HR-13 | Actions/domain transitions có audit atomic; detail reads có throttled audit |
| HR-19 | Ba pages dùng Move_home forest green/amber/Be Vietnam Pro |
| HR-20 | Mọi user-facing text/email có dấu tiếng Việt |
| HR-21 | Reuse `app_user`, `service_order`, không reserved names |
| AC-08 | Money DTO dùng BigDecimal/NUMERIC scale 0 |
| AC-14 | Suspension/status dùng VARCHAR + CHECK, không PostgreSQL ENUM |
| AC-15 | Audit/nested histories dùng server-side pagination |
| AC-16 | Page/section Loading/Empty/Error mandatory |
| ES-03 | Action/filter validation trả HTTP 422 |
| ES-04 | Common structured error format |
| ES-05 | CORE có integration/concurrency/security tests |

