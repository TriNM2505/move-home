# Feature Specification: Driver Workflow (Daily Operations)

**Feature Branch:** `006-driver-workflow`  
**Feature Number:** #6 of 30 — CORE (driver-side daily operations)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 3 (cùng Spec 005; location realtime hoàn thiện Sprint 4)

**CONTEXT.md reference:** v2.0 §2 Order State Machine, Driver Assignment, Final Payment,
Wallet & Escrow  
**Constitution reference:** v1.3.0 — HR-03, HR-04, HR-05, HR-06, HR-07, HR-08,
HR-10, HR-11, HR-13, HR-15, HR-18, HR-19, HR-20, HR-21, AC-06, AC-07, AC-08,
AC-09, AC-12, AC-13, AC-14, AC-15, AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Driver screens 4.5 đến 4.10  
**Related specs:** Spec #003 Customer Orders; Spec #005 Driver Onboarding; Payment spec;
Manager Assignment spec; Driver Financial spec; Dispute spec

---

## Goals

Hỗ trợ Driver `ACTIVE` thực hiện công việc hằng ngày từ lúc Manager phân công đơn đến khi hoàn
thành chuyến. Driver xem dashboard cá nhân, danh sách assignment đang chờ phản hồi, chi tiết
Customer/tuyến đường, chấp nhận hoặc từ chối trong thời hạn, cập nhật đã đến điểm đón, bắt đầu
vận chuyển, gửi vị trí và yêu cầu Customer thanh toán 70% còn lại. Chỉ sau khi VNPay IPN xác nhận
final payment, Driver mới được hoàn thành order.

Spec phải bảo vệ state machine bằng row lock, ownership và transition guard. Nó giải quyết race
giữa Driver response, Manager reassign và timeout; mọi thay đổi đều có audit. Location update
nhẹ, rate-limited và chỉ Customer owner được xem qua Spec #003. Driver có thể gọi Customer trong
assignment active nhưng không thấy dữ liệu Customer ngoài order được phân công.

Khi order `COMPLETED`, earnings chưa vào ví ngay. Escrow hai giờ cho phép Customer tạo
DamageReport; scheduled job chỉ credit `driver_wallet` và append `DRIVER_EARNING` khi window hết
và không có tranh chấp. Mục tiêu UX là thao tác quan trọng trong 1-2 tap trên mobile, feedback rõ,
resume sau crash và đủ Loading/Empty/Error. Sáu màn hình dùng Move_home forest green `#1B4D3E`,
amber `#F5A623`, Be Vietnam Pro và tiếng Việt có dấu.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Specs #002/#003/#005 →
> spec này → inventory/UI stubs → Code.

| Chủ đề | Quyết định canonical | Mapping/impact |
|--------|----------------------|----------------|
| Dispatch | Manager phân công thủ công | Driver không browse/pick order `CONFIRMED` |
| “Đơn có sẵn” | Assignment của chính Driver đang chờ response | Đổi label thành "Đơn được phân công" |
| HR-08 lock | Row lock bảo vệ response/reassign race | Không dùng lock để tự pick marketplace |
| Assignment state | Order `ASSIGNED`; decision ở `driver_assignment` | Accept không tạo status `ACCEPTED` |
| Reject | `ASSIGNED → CONFIRMED`, clear driver | Manager phân công lại; quota 3/ngày |
| Start | Driver đã accept + arrive rồi `ASSIGNED → IN_PROGRESS` | Không có transition `ACCEPTED` |
| Finish transport | `IN_PROGRESS → AWAITING_FINAL_PAYMENT` | Button "Yêu cầu thanh toán", chưa complete |
| Complete | `AWAITING_FINAL_PAYMENT → COMPLETED` sau verified final-payment IPN | Không complete trực tiếp từ in-progress |
| Earnings | Credit sau escrow 2 giờ, không ngay lúc complete | History phân biệt pending/released earning |
| Dispute | Driver có thể `IN_PROGRESS|AWAITING_FINAL_PAYMENT → IN_DISPUTE` | Full report flow thuộc Dispute spec |
| Location | Latest row từ Spec #003 | Không lưu GPS history trong scope |
| Online status | Dùng cho Manager suggestion/notifications | OFFLINE không hủy assignment đã có |

---

## Scope Summary

**In scope:**

1. `GET /api/driver/home` — KPI và current assignment.
2. `GET /api/driver/assignments` — assignment chờ Driver phản hồi.
3. `GET /api/driver/orders/{id}` — Driver detail theo ownership.
4. `POST /api/driver/assignments/{id}/accept` — accept có row lock.
5. `POST /api/driver/assignments/{id}/reject` — reject + quota.
6. `POST /api/driver/orders/{id}/arrive-pickup`.
7. `POST /api/driver/orders/{id}/start`.
8. `POST /api/driver/orders/{id}/request-final-payment`.
9. `POST /api/driver/orders/{id}/complete`.
10. `POST /api/driver/location`.
11. `POST /api/driver/availability`.
12. `GET /api/driver/orders/history`.
13. `GET /api/driver/profile`.
14. Escrow-release scheduled job và Driver earning audit.

**Out of scope:**

1. Manager assignment UI/algorithm.
2. VNPay IPN implementation và final-payment URL generation.
3. Driver withdrawal/deposit replenishment.
4. Full DisputeReport/DamageReport creation/resolution.
5. WebSocket push infrastructure; baseline polling/outbox events only.
6. Driver profile edit/re-approval.
7. Turn-by-turn navigation.
8. Chat; spec chỉ hỗ trợ `tel:` và optional Zalo deep link.

---

## User Stories

**P1:**

**US1:** Là Driver `ACTIVE`, tôi xem dashboard với KPI hôm nay, rating, availability và công việc
đang thực hiện.

**US2:** Là Driver, tôi xem assignment Manager gửi và thông tin cần thiết trước khi phản hồi.

**US3:** Là Driver, tôi accept assignment bằng một tap và hệ thống chống race/reassign bằng lock.

**US4:** Là Driver, tôi từ chối assignment với lý do trong quota để Manager phân công lại.

**US5:** Là Driver đã accept, tôi đánh dấu đã đến điểm đón và bắt đầu vận chuyển.

**US6:** Là Driver đang vận chuyển, tôi gửi vị trí và yêu cầu Customer trả 70% còn lại.

**US7:** Là Driver, tôi complete sau khi final payment hợp lệ và thấy earnings đang chờ escrow.

**US8:** Là Driver, tôi bật/tắt trạng thái sẵn sàng và xem lịch sử/earnings từng order.

**P2:**

**US9:** Là Driver, tôi nhận notification khi Manager phân assignment mới.

**US10:** Là Driver, tôi gọi hoặc mở Zalo Customer từ order active khi cần phối hợp.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Driver Home Dashboard (FR-001..FR-005)

**FR-001**
WHEN Driver `ACTIVE` gọi `GET /api/driver/home`, THE system SHALL trả HTTP 200:

```json
{
  "today_assignments": 3,
  "today_completed": 1,
  "today_released_earnings": 750000,
  "pending_assignments": 1,
  "average_rating": "4.80",
  "total_completed": 142,
  "availability": "ONLINE",
  "current_order": null
}
```

**FR-002**
WHEN tính KPI hôm nay, THE system SHALL dùng calendar day `Asia/Ho_Chi_Minh`, money
NUMERIC/BigDecimal scale=0 và chỉ count order/earning thuộc Driver từ JWT.

**FR-003**
WHEN Driver có order `ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT`, THE dashboard SHALL trả
`current_order` và frontend SHALL hiển thị CTA nổi bật "Quay lại công việc hiện tại".

**FR-004**
WHEN render home, frontend SHALL hiển thị bốn KPI, availability toggle, CTA assignment/history và
nav nhất quán: Trang chủ, Đơn được phân công, Đang giao, Lịch sử, Hồ sơ, Thu nhập, Rút tiền.

**FR-005**
WHILE dashboard fetch, frontend SHALL render skeleton; WHERE API lỗi, SHALL hiển thị
"Không thể tải trang tài xế" + "Thử lại"; WHERE Driver chưa ACTIVE, SHALL redirect onboarding.

---

### Nhóm 2 — Assigned Orders Browse & Detail (FR-006..FR-013)

**FR-006**
WHEN Driver gọi `GET /api/driver/assignments?page=0&size=20&status=PENDING_RESPONSE`, THE system
SHALL trả chỉ `driver_assignment.driver_id` từ JWT, status `PENDING_RESPONSE`, chưa hết hạn,
sort `response_deadline ASC`, dưới dạng Spring Page.

**FR-007**
WHEN assignment item serialize, THE system SHALL trả order code, pickup/dropoff district,
scheduled time, assigned vehicle, estimated earning, response deadline và distance đến pickup
nếu latest location còn mới; SHALL không trả order không được phân cho Driver.

**FR-008**
WHEN tính `estimated_earning`, THE system SHALL dùng
`total_quote - ROUND(total_quote * commission_rate_snapshot, 0)` và label "Ước tính sau escrow";
không hứa tiền đã available.

**FR-009**
WHEN `available-orders.html` render, frontend SHALL đổi heading thành "Đơn được phân công",
filter `Chờ phản hồi|Đã nhận|Đã từ chối`, auto-refresh mỗi 30 giây và hiển thị countdown deadline.

**FR-010**
WHERE Driver không có assignment, frontend SHALL hiển thị "Bạn chưa có đơn được phân công";
WHILE fetch SHALL render card skeleton; WHERE API lỗi SHALL hiển thị retry.

**FR-011**
WHEN Driver gọi `GET /api/driver/orders/{id}`, THE system SHALL verify order `driver_id` hoặc
assignment owner match JWT và trả Customer contact, route, notes, pricing, vehicle, timeline và
allowed actions.

**FR-012**
WHERE Driver truy cập order không được phân cho mình, THE system SHALL trả HTTP 403
`ORDER_ASSIGNMENT_OWNERSHIP_REQUIRED`, không tiết lộ Customer/route/payment.

**FR-013**
IF assignment/order đang active, THEN detail SHALL expose masked Customer phone + call action và
optional validated Zalo deep link; WHERE assignment rejected/expired/history, SHALL mask contact
và ẩn communication actions.

---

### Nhóm 3 — Accept/Reject Assignment with Lock (FR-014..FR-021)

**FR-014**
WHEN Driver gọi `POST /api/driver/assignments/{id}/accept` với `Idempotency-Key`, THE system SHALL
lock assignment và `service_order` bằng `SELECT ... FOR UPDATE`, verify owner/deadline/status.

**FR-015**
WHEN accept hợp lệ, THE system SHALL set assignment `ACCEPTED`, `responded_at=NOW()`,
`service_order.accepted_at=NOW()` nhưng giữ order status `ASSIGNED`, insert audit
`DRIVER_ACCEPTED_ASSIGNMENT`, publish Customer notification và trả HTTP 200.

**FR-016**
WHERE assignment đã expired, rejected, accepted bởi response khác, hoặc Manager đã reassign,
THE system SHALL trả HTTP 409 `ASSIGNMENT_NO_LONGER_AVAILABLE`, không mutate.

**FR-017**
WHERE Driver không `ACTIVE`, vehicle assignment không thuộc Driver hoặc Driver đã có order
`IN_PROGRESS|AWAITING_FINAL_PAYMENT`, THE system SHALL trả HTTP 409 với error cụ thể.

**FR-018**
WHEN Driver gọi `POST /api/driver/assignments/{id}/reject` body
`{"reason_code":"SCHEDULE_CONFLICT","note":"Đang ở xa điểm đón"}`, THE system SHALL lock rows,
validate reason và quota.

**FR-019**
WHEN reject hợp lệ, THE system SHALL mark assignment `REJECTED`, increment daily reject counter,
transition order `ASSIGNED → CONFIRMED`, clear order driver/vehicle assignment, audit và publish
event `ORDER_REQUIRES_REASSIGNMENT`.

**FR-020**
WHERE Driver đã từ chối đủ 3 assignment trong ngày `Asia/Ho_Chi_Minh`, THE reject endpoint SHALL
trả HTTP 409 `DAILY_REJECT_QUOTA_EXCEEDED`; Manager/support xử lý ngoại lệ.

**FR-021**
WHEN accept/reject transaction commit, email/notification SHALL async; WHERE delivery lỗi, SHALL
không rollback response. Idempotent retry SHALL replay kết quả, không double quota/audit.

---

### Nhóm 4 — Arrive Pickup & Start (FR-022..FR-026)

**FR-022**
WHEN Driver owner của accepted assignment gọi
`POST /api/driver/orders/{id}/arrive-pickup`, THE system SHALL require order `ASSIGNED`,
assignment `ACCEPTED`, set `arrived_pickup_at=NOW()`, audit `DRIVER_ARRIVED_PICKUP` và trả 200.

**FR-023**
WHERE arrive đã được ghi, idempotent retry SHALL trả timestamp cũ; WHERE assignment chưa accept
hoặc order state khác, SHALL trả HTTP 409.

**FR-024**
WHEN Driver gọi `POST /api/driver/orders/{id}/start`, THE system SHALL require owner,
order `ASSIGNED`, assignment `ACCEPTED`, `arrived_pickup_at IS NOT NULL`; transition
`ASSIGNED → IN_PROGRESS`, set `started_at`, availability `BUSY`, audit và publish Customer event.

**FR-025**
WHERE Driver có order khác `IN_PROGRESS|AWAITING_FINAL_PAYMENT`, start SHALL trả HTTP 409
`DRIVER_ALREADY_BUSY`; transaction SHALL use lock/index guard để chống hai start đồng thời.

**FR-026**
WHEN start thành công, frontend SHALL redirect `in-progress.html?id=<order_id>`, bắt đầu location
updates và hiển thị Customer contact; email Customer "Tài xế đã bắt đầu vận chuyển" gửi async.

---

### Nhóm 5 — Final Payment, Complete & Earnings (FR-027..FR-034)

**FR-027**
WHEN Driver owner gọi `POST /api/driver/orders/{id}/request-final-payment`, THE system SHALL
require status `IN_PROGRESS`, transition sang `AWAITING_FINAL_PAYMENT`, set
`final_payment_requested_at`, audit và yêu cầu Payment spec tạo VNPay URL 70%.

**FR-028**
WHERE order không `IN_PROGRESS`, Driver không owner hoặc có open onsite dispute, request final
payment SHALL trả HTTP 409/403 tương ứng và không mutate.

**FR-029**
WHILE order `AWAITING_FINAL_PAYMENT`, frontend SHALL hiển thị "Đang chờ khách thanh toán",
poll payment status, CTA gọi Customer và action "Báo cáo tranh chấp"; SHALL không hiển thị
"Hoàn thành" trước verified IPN.

**FR-030**
WHEN Payment spec xử lý final-payment IPN hợp lệ, THE system SHALL set
`final_payment_paid_at`/payment audit nhưng giữ order `AWAITING_FINAL_PAYMENT`; return URL không
được phép set field này.

**FR-031**
WHEN Driver gọi `POST /api/driver/orders/{id}/complete`, THE system SHALL require owner,
status `AWAITING_FINAL_PAYMENT`, `final_payment_paid_at IS NOT NULL`, không open dispute;
transition `COMPLETED`, set `completed_at`, availability `ONLINE`, audit và trả earning preview.

**FR-032**
WHERE order `IN_DISPUTE`, final payment chưa verified hoặc state khác, complete SHALL trả HTTP 409;
HR-06/HR-07 SHALL chặn Driver đóng dispute.

**FR-033**
WHEN order complete, THE system SHALL snapshot
`commission_amount=ROUND(total_quote*commission_rate_snapshot,0)` và
`driver_earning=total_quote-commission_amount`, set `escrow_release_at=completed_at+2h`,
`escrow_processed=false`; SHALL không credit wallet ngay.

**FR-034**
WHILE completed order qua escrow 2 giờ và không open DamageReport/dispute, scheduled job mỗi
5 phút SHALL lock order/wallet, credit `driver_wallet.balance`, append one `DRIVER_EARNING`
transaction, update totals, set `escrow_processed=true` và audit `EARNING_RELEASED`.

---

### Nhóm 6 — Location Update (FR-035..FR-039)

**FR-035**
WHEN Driver `ACTIVE` gọi `POST /api/driver/location` body
`{"lat":21.0285110,"lng":105.8048170,"heading":120.5,"speed_kmh":24.2}`,
THE system SHALL validate ranges and UPSERT latest `driver_location`.

**FR-036**
WHEN location update accepted, THE system SHALL set `driver_id`, current active `order_id`
nullable, coordinates, heading, speed, `recorded_at=NOW()` và trả HTTP 204 dưới 100 ms target.

**FR-037**
WHERE Driver gửi quá một update/10 giây, THE system SHALL trả HTTP 429; WHERE coordinates ngoài
lat/lng range, speed `<0` hoặc `>180`, SHALL trả HTTP 422.

**FR-038**
WHILE Driver availability `OFFLINE` và không có active order, location update SHALL trả 204 no-op;
WHILE có `IN_PROGRESS|AWAITING_FINAL_PAYMENT`, location SHALL vẫn update dù toggle attempt OFFLINE
bị chặn.

**FR-039**
WHEN location update cho active order commit, THE system SHALL publish lightweight
`DRIVER_LOCATION_UPDATED` event; Customer read/poll ownership và stale behavior thuộc Spec #003.

---

### Nhóm 7 — Availability Toggle (FR-040..FR-042)

**FR-040**
WHEN Driver `ACTIVE` gọi `POST /api/driver/availability` body `{"status":"ONLINE|OFFLINE"}`,
THE system SHALL update `driver_profile.online_status`, `last_active_at` và audit.

**FR-041**
WHERE Driver có order `IN_PROGRESS|AWAITING_FINAL_PAYMENT`, request `OFFLINE` SHALL trả HTTP 409
`ACTIVE_ORDER_REQUIRES_ONLINE`; system SHALL keep `BUSY`.

**FR-042**
WHILE Driver `OFFLINE`, Manager assignment suggestions/notifications SHALL exclude Driver;
existing `ASSIGNED` assignment vẫn hiển thị và phải phản hồi trước deadline.

---

### Nhóm 8 — History & Profile (FR-043..FR-047)

**FR-043**
WHEN Driver gọi `GET /api/driver/orders/history?page=0&size=20&status=ALL`, THE system SHALL query
owner orders status `COMPLETED|CANCELLED|IN_DISPUTE`, sort latest event DESC và trả Spring Page.

**FR-044**
WHEN history item serialize, THE system SHALL trả order code, masked Customer name, route,
total quote, earning preview/released amount, earning status `PENDING_ESCROW|RELEASED|HELD`,
order status, completed/cancelled timestamp và rating nullable.

**FR-045**
WHEN history page render, frontend SHALL có filter, pagination `10|20|50|100`, stats tháng,
Empty "Chưa có lịch sử đơn", Loading skeleton và Error retry theo AC-15/16.

**FR-046**
WHEN Driver gọi `GET /api/driver/profile`, THE system SHALL trả personal summary, GPLX readonly,
approved vehicles, availability, total completed, released earnings và rating; SHALL không trả
document raw URLs hoặc Manager internal notes.

**FR-047**
WHERE role không Driver, account không `ACTIVE`, hoặc Driver truy cập profile/order người khác,
THE system SHALL trả HTTP 403; profile edit/re-approval SHALL không được cung cấp trong spec này.

---

## Non-Functional Requirements

**NFR-001**
Home dashboard SHALL có P90 dưới 500 ms.

**NFR-002**
Assignment list/detail SHALL có P90 dưới 800 ms với page 20.

**NFR-003**
Accept/reject/start/complete APIs SHALL có P90 dưới 1 giây kể cả DB lock acquisition.

**NFR-004**
Location update SHALL có P90 dưới 100 ms và tối đa một request/10 giây/Driver.

**NFR-005**
100 concurrent stale accept/reject/reassign attempts SHALL không deadlock và chỉ một outcome commit.

**NFR-006**
History page 20 SHALL có P90 dưới 500 ms với 10.000 rows/Driver.

**NFR-007**
Commission/earning calculation SHALL deterministic đến từng VND.

**NFR-008**
Mọi state/money transition SHALL có immutable audit và correlation id.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/driver/home` | none | 200 dashboard | Active Driver |
| GET | `/api/driver/assignments` | page,size,status | 200 Page | Active Driver |
| GET | `/api/driver/orders/{id}` | order id | 200 detail | Assigned Driver |
| POST | `/api/driver/assignments/{id}/accept` | idempotency header | 200 | Assignment owner |
| POST | `/api/driver/assignments/{id}/reject` | reason + idempotency | 200 | Assignment owner |
| POST | `/api/driver/orders/{id}/arrive-pickup` | idempotency header | 200 | Order Driver |
| POST | `/api/driver/orders/{id}/start` | idempotency header | 200 | Order Driver |
| POST | `/api/driver/orders/{id}/request-final-payment` | none | 200 payment state | Order Driver |
| POST | `/api/driver/orders/{id}/complete` | none | 200 completed DTO | Order Driver |
| POST | `/api/driver/location` | lat,lng,heading,speed | 204 | Active Driver |
| POST | `/api/driver/availability` | ONLINE/OFFLINE | 200 | Active Driver |
| GET | `/api/driver/orders/history` | page,size,status | 200 Page | Active Driver |
| GET | `/api/driver/profile` | none | 200 profile | Active Driver |

---

## Data Model

### Extend `service_order`

```sql
ALTER TABLE service_order
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS arrived_pickup_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS final_payment_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS final_payment_paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS commission_amount NUMERIC(15,0),
    ADD COLUMN IF NOT EXISTS driver_earning NUMERIC(15,0),
    ADD COLUMN IF NOT EXISTS escrow_release_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS escrow_processed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_driver_money
    CHECK (
        (commission_amount IS NULL OR commission_amount >= 0)
        AND (driver_earning IS NULL OR driver_earning >= 0)
    );
```

Canonical status CHECK từ Specs #002/#003 SHALL gồm:
`PENDING_PAYMENT|CONFIRMED|ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT|COMPLETED|IN_DISPUTE|CANCELLED`.

### Table `driver_assignment`

```sql
CREATE TABLE driver_assignment (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id           UUID        NOT NULL REFERENCES service_order(id),
    driver_id          UUID        NOT NULL REFERENCES app_user(id),
    vehicle_id         UUID        NOT NULL REFERENCES driver_vehicle(id),
    assigned_by        UUID        NOT NULL REFERENCES app_user(id),
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING_RESPONSE'
        CHECK (status IN ('PENDING_RESPONSE', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'REPLACED')),
    rejection_reason   VARCHAR(30),
    rejection_note     VARCHAR(500),
    assigned_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    response_deadline  TIMESTAMPTZ NOT NULL,
    responded_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_assignment PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_driver_assignment_current
    ON driver_assignment (order_id)
    WHERE status IN ('PENDING_RESPONSE', 'ACCEPTED');

CREATE INDEX idx_driver_assignment_driver_pending
    ON driver_assignment (driver_id, response_deadline)
    WHERE status = 'PENDING_RESPONSE';
```

### Extend `driver_profile` and `driver_location`

```sql
ALTER TABLE driver_profile
    ADD COLUMN IF NOT EXISTS online_status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE'
        CHECK (online_status IN ('ONLINE', 'OFFLINE', 'BUSY')),
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS daily_reject_count INTEGER NOT NULL DEFAULT 0
        CHECK (daily_reject_count BETWEEN 0 AND 3),
    ADD COLUMN IF NOT EXISTS daily_reject_date DATE;

ALTER TABLE driver_location
    ADD COLUMN IF NOT EXISTS heading NUMERIC(5,2)
        CHECK (heading IS NULL OR (heading >= 0 AND heading < 360)),
    ADD COLUMN IF NOT EXISTS speed_kmh NUMERIC(6,2)
        CHECK (speed_kmh IS NULL OR (speed_kmh >= 0 AND speed_kmh <= 180));
```

### Table `driver_wallet`

```sql
CREATE TABLE driver_wallet (
    driver_id        UUID          NOT NULL REFERENCES app_user(id),
    balance          NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    deposit_balance  NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (deposit_balance >= 0),
    total_earnings   NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_earnings >= 0),
    total_withdrawn  NUMERIC(15,0) NOT NULL DEFAULT 0 CHECK (total_withdrawn >= 0),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_wallet PRIMARY KEY (driver_id)
);
```

Append-only V6 `transaction` type `DRIVER_EARNING` là money audit. Earning release SHALL insert
transaction và update wallet trong cùng DB transaction theo HR-18/AC-13.

### Accept Lock SQL

```sql
BEGIN;

SELECT da.id, da.order_id, da.driver_id, da.status, da.response_deadline
FROM driver_assignment da
WHERE da.id = :assignment_id
FOR UPDATE;

SELECT so.id, so.status, so.driver_id, so.version
FROM service_order so
WHERE so.id = :order_id
FOR UPDATE;

UPDATE driver_assignment
SET status = 'ACCEPTED',
    responded_at = NOW()
WHERE id = :assignment_id
  AND driver_id = :driver_id
  AND status = 'PENDING_RESPONSE'
  AND response_deadline >= NOW();

UPDATE service_order
SET accepted_at = NOW(),
    version = version + 1
WHERE id = :order_id
  AND driver_id = :driver_id
  AND status = 'ASSIGNED';

COMMIT;
```

Service SHALL verify both update row counts bằng 1 before audit/commit.

### Escrow Release SQL

```sql
BEGIN;

SELECT id, driver_id, driver_earning
FROM service_order
WHERE id = :order_id
  AND status = 'COMPLETED'
  AND escrow_processed = FALSE
  AND escrow_release_at <= NOW()
FOR UPDATE;

UPDATE driver_wallet
SET balance = balance + :driver_earning,
    total_earnings = total_earnings + :driver_earning,
    updated_at = NOW()
WHERE driver_id = :driver_id;

INSERT INTO transaction
    (user_id, type, amount, related_order_id, description)
VALUES
    (:driver_id, 'DRIVER_EARNING', :driver_earning, :order_id, 'Thu nhập sau escrow');

UPDATE service_order
SET escrow_processed = TRUE,
    version = version + 1
WHERE id = :order_id
  AND escrow_processed = FALSE;

COMMIT;
```

Job SHALL additionally assert no open DamageReport/DisputeReport before wallet update.

---

## State Machines

### Canonical Order Lifecycle — Driver Perspective

```text
CONFIRMED
  ↓ Manager assigns Driver + Vehicle
ASSIGNED / assignment=PENDING_RESPONSE
  ├─ Driver rejects/timeouts ─────────────→ CONFIRMED
  └─ Driver accepts ──────────────────────→ ASSIGNED / assignment=ACCEPTED
       ↓ arrive pickup + start
IN_PROGRESS
  ├─ request final payment ───────────────→ AWAITING_FINAL_PAYMENT
  └─ onsite dispute ──────────────────────→ IN_DISPUTE
AWAITING_FINAL_PAYMENT
  ├─ verified final IPN + Driver complete → COMPLETED
  └─ onsite dispute ──────────────────────→ IN_DISPUTE
COMPLETED
  └─ Customer DamageReport trong 2h ─────→ IN_DISPUTE
```

`ACCEPTED`, `PENDING` và `DISPUTED` chỉ là legacy/UI aliases; không thêm vào canonical order CHECK.

### Availability

```text
OFFLINE ↔ ONLINE
ONLINE ── start order ──→ BUSY
BUSY ── complete/cancel/reassign resolution ──→ ONLINE
```

### Earning

```text
NOT_CALCULATED
  ↓ order COMPLETED
PENDING_ESCROW
  ├─ no dispute after 2h → RELEASED
  └─ dispute/damage ─────→ HELD
```

---

## Error Matrix

| HTTP | `error_code` | Khi nào |
|------|--------------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn |
| 403 | `FORBIDDEN` | Role/account không đủ quyền |
| 403 | `ORDER_ASSIGNMENT_OWNERSHIP_REQUIRED` | Order/assignment Driver khác |
| 404 | `ORDER_NOT_FOUND` | Order không tồn tại/soft-deleted |
| 409 | `INVALID_STATUS_TRANSITION` | State không hợp lệ |
| 409 | `ASSIGNMENT_NO_LONGER_AVAILABLE` | Race/reassign/expired |
| 409 | `DRIVER_ALREADY_BUSY` | Driver có active order |
| 409 | `DAILY_REJECT_QUOTA_EXCEEDED` | Từ chối quá 3/ngày |
| 409 | `FINAL_PAYMENT_NOT_CONFIRMED` | Complete trước IPN |
| 409 | `ACTIVE_ORDER_REQUIRES_ONLINE` | Toggle offline khi active |
| 422 | `VALIDATION_ERROR` | Payload/filter/location sai |
| 429 | `RATE_LIMITED` | Location/endpoint quá rate |

---

## Query & Action Contracts

### Dashboard Aggregation

Dashboard query SHALL aggregate theo Driver owner và ngày Việt Nam, không load full entity:

```sql
SELECT
    COUNT(*) FILTER (
        WHERE da.assigned_at >= :day_start_utc
          AND da.assigned_at < :day_end_utc
    ) AS today_assignments,
    COUNT(*) FILTER (
        WHERE so.status = 'COMPLETED'
          AND so.completed_at >= :day_start_utc
          AND so.completed_at < :day_end_utc
    ) AS today_completed,
    COALESCE(SUM(t.amount) FILTER (
        WHERE t.type = 'DRIVER_EARNING'
          AND t.created_at >= :day_start_utc
          AND t.created_at < :day_end_utc
    ), 0) AS today_released_earnings
FROM app_user u
LEFT JOIN driver_assignment da ON da.driver_id = u.id
LEFT JOIN service_order so ON so.driver_id = u.id AND so.deleted_at IS NULL
LEFT JOIN transaction t ON t.user_id = u.id
WHERE u.id = :driver_id
  AND u.role = 'DRIVER'
  AND u.status = 'ACTIVE'
  AND u.deleted_at IS NULL;
```

Implementation MAY split aggregate queries để tránh join multiplication; tests SHALL verify totals
không bị nhân chéo. `:day_start_utc/:day_end_utc` được tính từ `Asia/Ho_Chi_Minh`.

### Assignment Page Query

```sql
SELECT da.id,
       da.order_id,
       da.vehicle_id,
       da.status,
       da.assigned_at,
       da.response_deadline,
       so.order_code,
       so.pickup_district,
       so.dropoff_district,
       so.scheduled_at,
       so.total_quote,
       so.commission_rate_snapshot
FROM driver_assignment da
JOIN service_order so ON so.id = da.order_id
WHERE da.driver_id = :driver_id
  AND da.status = ANY(:assignment_statuses)
  AND so.deleted_at IS NULL
ORDER BY da.response_deadline ASC, da.id ASC
LIMIT :size OFFSET :offset;
```

`:assignment_statuses` chỉ build từ server allowlist. Pending list SHALL exclude expired rows;
timeout job chuyển chúng sang `EXPIRED` và đưa order về `CONFIRMED` nếu assignment vẫn current.

### Allowed Actions Matrix

Backend detail DTO SHALL trả `allowed_actions`; frontend không tự suy diễn:

| Order state | Assignment state/condition | Allowed Driver actions |
|-------------|----------------------------|------------------------|
| `ASSIGNED` | `PENDING_RESPONSE`, before deadline | `ACCEPT_ASSIGNMENT`, `REJECT_ASSIGNMENT` |
| `ASSIGNED` | `ACCEPTED`, not arrived | `ARRIVE_PICKUP`, `CALL_CUSTOMER` |
| `ASSIGNED` | `ACCEPTED`, arrived | `START_ORDER`, `CALL_CUSTOMER` |
| `IN_PROGRESS` | owner | `UPDATE_LOCATION`, `REQUEST_FINAL_PAYMENT`, `REPORT_DISPUTE`, `CALL_CUSTOMER` |
| `AWAITING_FINAL_PAYMENT` | final payment pending | `UPDATE_LOCATION`, `REPORT_DISPUTE`, `CALL_CUSTOMER` |
| `AWAITING_FINAL_PAYMENT` | final payment verified | `COMPLETE_ORDER`, `UPDATE_LOCATION`, `CALL_CUSTOMER` |
| `COMPLETED` | escrow pending/released | `VIEW_HISTORY` |
| `IN_DISPUTE` | any | `VIEW_DISPUTE`; no complete |
| `CANCELLED` | any | `VIEW_HISTORY` |

### History Stable Pagination

```sql
SELECT so.id,
       so.order_code,
       so.status,
       so.pickup_district,
       so.dropoff_district,
       so.total_quote,
       so.driver_earning,
       so.escrow_processed,
       so.completed_at,
       so.cancelled_at
FROM service_order so
WHERE so.driver_id = :driver_id
  AND so.deleted_at IS NULL
  AND so.status = ANY(:history_statuses)
ORDER BY COALESCE(so.completed_at, so.cancelled_at, so.updated_at) DESC,
         so.id DESC
LIMIT :size OFFSET :offset;
```

Secondary sort `id` bảo đảm stable pagination khi timestamps bằng nhau.

---

## Earnings & Escrow Specification

### Canonical Formula

```text
commission_amount = ROUND(total_quote × commission_rate_snapshot, 0)
driver_earning     = total_quote − commission_amount

Invariant:
total_quote = commission_amount + driver_earning
commission_amount >= 0
driver_earning >= 0
```

Java SHALL dùng `BigDecimal`, `setScale(0, HALF_UP)` cho commission snapshot và subtraction cho
earning. Không tính `total_quote × (1-rate)` độc lập vì rounding có thể phá invariant.

### Deterministic Examples

| `total_quote` | `commission_rate_snapshot` | Commission | Driver earning |
|---------------|----------------------------|------------|----------------|
| 940.000 | `0.3000` | 282.000 | 658.000 |
| 1.000.001 | `0.3000` | 300.000 | 700.001 |
| 1.250.000 | `0.2500` | 312.500 | 937.500 |
| 128.000 | `0.3000` | 38.400 | 89.600 |

### Earning Status Derivation

| Condition | DTO status | Wallet effect |
|-----------|------------|---------------|
| Order active/not completed | `NOT_AVAILABLE` | None |
| Completed, before escrow release | `PENDING_ESCROW` | None |
| Completed, open dispute/damage | `HELD` | None |
| Completed, escrow processed | `RELEASED` | Balance credited once |
| Cancelled | `NOT_AVAILABLE` | None |

`driver_profile.total_revenue` legacy field MAY mirror released earning for reporting, but
`driver_wallet + transaction` remain financial source of truth.

### Reconciliation

Daily reconciliation SHALL compare:

```sql
SELECT w.driver_id,
       w.total_earnings,
       COALESCE(SUM(t.amount) FILTER (WHERE t.type = 'DRIVER_EARNING'), 0) AS audit_earnings
FROM driver_wallet w
LEFT JOIN transaction t ON t.user_id = w.driver_id
GROUP BY w.driver_id, w.total_earnings
HAVING w.total_earnings <>
       COALESCE(SUM(t.amount) FILTER (WHERE t.type = 'DRIVER_EARNING'), 0);
```

Any row is a financial integrity alert. Reconciliation SHALL not auto-edit money; Admin financial
workflow tạo adjustment audit nếu cần.

---

## Assignment Timeout & Reject Quota

Assignment timeout job chạy mỗi phút:

```text
select PENDING_RESPONSE assignments past deadline using FOR UPDATE SKIP LOCKED
mark assignment EXPIRED
if order still ASSIGNED to same Driver:
  set order CONFIRMED
  clear driver_id/assigned vehicle
  publish ORDER_REQUIRES_REASSIGNMENT
  audit ASSIGNMENT_EXPIRED
```

Daily reject quota sử dụng `Asia/Ho_Chi_Minh` date. Khi ngày mới bắt đầu, service reset logical
count trước increment; concurrent rejects phải lock Driver profile. Timeout/no-response có được
tính quota hay không là open decision, nhưng implementation SHALL không đoán.

Reject reason allowlist:

| Code | Label tiếng Việt |
|------|-------------------|
| `SCHEDULE_CONFLICT` | Trùng lịch |
| `TOO_FAR` | Quá xa điểm đón |
| `VEHICLE_UNAVAILABLE` | Xe tạm thời không sẵn sàng |
| `PERSONAL_REASON` | Lý do cá nhân |
| `OTHER` | Lý do khác |

`OTHER` requires note 10-500 chars; other codes allow optional note max 500.

---

## Integration Boundaries

### Manager Assignment Boundary

Manager Assignment spec owns `CONFIRMED → ASSIGNED`, selecting Driver + approved vehicle and
creating `driver_assignment(PENDING_RESPONSE)`. It SHALL publish `DRIVER_ASSIGNMENT_CREATED`.
This spec consumes assignment and owns Driver response.

### Payment Boundary

This spec owns `IN_PROGRESS → AWAITING_FINAL_PAYMENT` request. Payment spec owns VNPay URL/IPN,
sets `final_payment_paid_at` only after HMAC verification and publishes
`FINAL_PAYMENT_CONFIRMED`. Driver complete consumes that trusted field/event.

### Customer Tracking Boundary

This spec writes latest `driver_location`; Spec #003 owns Customer read access, stale marker,
polling/WebSocket fallback and Customer privacy.

### Dispute Boundary

This spec exposes action boundary `REPORT_DISPUTE` and blocks complete/escrow. Dispute spec owns
report payload, evidence, transition to/from `IN_DISPUTE` and Manager resolution.

### Driver Financial Boundary

This spec owns automatic escrow earning release because it is coupled to completion. Driver
Financial spec owns earnings dashboard details, withdrawal, deposit replenishment and adjustments.

---

## Acceptance Criteria

**AC1**
Dashboard chỉ trả KPI/assignment của Driver `ACTIVE`, money theo VND nguyên đồng.

**AC2**
Danh sách assignment không expose order chưa được Manager phân cho Driver.

**AC3**
100 response/reassign đồng thời chỉ một outcome commit, không deadlock hoặc duplicate audit.

**AC4**
Accept giữ order `ASSIGNED`; reject đưa order về `CONFIRMED`, clear assignment và tính quota đúng.

**AC5**
Chỉ accepted assignment đã arrive mới chuyển `ASSIGNED → IN_PROGRESS`.

**AC6**
Driver không thể complete trực tiếp từ `IN_PROGRESS` hoặc trước verified final-payment IPN.

**AC7**
Complete tạo earning snapshot nhưng wallet không tăng trước escrow hai giờ.

**AC8**
Escrow job credit wallet/transaction đúng một lần khi không có dispute.

**AC9**
Location update rate-limited, valid ranges và Customer tracking nhận latest location.

**AC10**
History pagination/stats phản ánh rõ earning pending/released/held.

**AC11**
Sáu màn hình dùng lifecycle canonical, brand Move_home, tiếng Việt và Loading/Empty/Error.

**AC12**
Mọi state/money transition có audit; notification lỗi không rollback.

---

## Edge Cases & Error Handling

| ID | Tình huống | Expected Behavior |
|----|------------|-------------------|
| EC-01 | Driver accept cùng lúc Manager reassign | Row lock cho một commit; request thua 409 |
| EC-02 | Driver accept assignment hết hạn | 409, order không đổi |
| EC-03 | Hai tab accept cùng key | Replay cùng response, một audit |
| EC-04 | Reject lần thứ tư trong ngày | 409 quota exceeded |
| EC-05 | Driver OFFLINE nhận assignment cũ | Assignment vẫn hiển thị; phải phản hồi |
| EC-06 | Network fail sau arrive commit | Retry trả timestamp cũ |
| EC-07 | Hai order start đồng thời | Một start; request còn lại 409 busy |
| EC-08 | App crash giữa IN_PROGRESS | Home/current order cho resume |
| EC-09 | Location update không active order | Lưu latest nếu ONLINE; current_order_id null |
| EC-10 | Location update khi offline/no order | 204 no-op |
| EC-11 | Return URL giả final payment success | Complete vẫn 409 |
| EC-12 | Customer không trả final payment | Order giữ AWAITING; Driver có dispute action |
| EC-13 | DamageReport mở trước escrow job | Earning HELD, wallet không tăng |
| EC-14 | Hai escrow workers cùng order | Lock/idempotency chỉ một earning |
| EC-15 | Notification/email lỗi | Workflow transaction vẫn commit |

---

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TC-01 | Dashboard Active Driver | KPI owner-only, correct timezone/money |
| TC-02 | Driver A request detail assignment B | 403, không lộ data |
| TC-03 | 100 threads accept/reassign same assignment | Một outcome, zero deadlock |
| TC-04 | Accept valid assignment | Assignment ACCEPTED, order remains ASSIGNED |
| TC-05 | Reject valid assignment | Order CONFIRMED, driver cleared, quota +1 |
| TC-06 | Start trước arrive | 409 |
| TC-07 | Start two orders concurrently | Một IN_PROGRESS |
| TC-08 | Complete từ IN_PROGRESS | 409 |
| TC-09 | Complete after valid final IPN | COMPLETED + pending escrow snapshot |
| TC-10 | Escrow job before/after 2h | Before no-op; after one earning release |
| TC-11 | Escrow with open dispute | HELD, no wallet transaction |
| TC-12 | Location calls every 2s | First accepted, excess 429 |

### Required Automated Test Layers

1. Unit tests cho state guards, quota, earning formula và action visibility.
2. PostgreSQL/Testcontainers concurrency tests cho accept/reject/start/escrow locks.
3. Integration tests cho RBAC ownership, lifecycle và location UPSERT.
4. Contract tests cho Payment/Manager/Customer notification boundaries.
5. Frontend tests cho canonical labels, resume, countdown và Empty/Loading/Error.
6. Security tests cho Customer PII, location access và audit redaction.
7. CORE coverage tối thiểu 70% theo ES-05.

---

## Frontend Screen Contract

| Screen | Canonical behavior |
|--------|--------------------|
| `home.html` | KPI, availability, current assignment/order CTA |
| `available-orders.html` | Rename "Đơn được phân công"; owner assignments only |
| `order-detail.html` | Assignment/order detail + accept/reject or active actions |
| `in-progress.html` | Arrive, start, request final payment, complete after IPN |
| `history.html` | Order + earning status history with pagination |
| `profile.html` | Read-only approved identity/vehicle/performance |

Frontend SHALL loại bỏ text "chọn đơn phù hợp" và direct `IN_PROGRESS → COMPLETED`. Buttons được
render từ `allowed_actions` backend, không từ hardcoded local status.

---

## Detailed API Contracts

### Accept Success

```json
{
  "assignment_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "order_id": "6bf5e878-52b0-4fb8-a9cb-8af517594e89",
  "assignment_status": "ACCEPTED",
  "order_status": "ASSIGNED",
  "accepted_at": "2026-06-04T10:30:00Z",
  "next_action": "NAVIGATE_TO_PICKUP"
}
```

### Request Final Payment Success

```json
{
  "order_id": "6bf5e878-52b0-4fb8-a9cb-8af517594e89",
  "status": "AWAITING_FINAL_PAYMENT",
  "final_payment_amount": 658000,
  "payment_state": "PENDING",
  "message": "Đã gửi yêu cầu thanh toán đến khách hàng."
}
```

### Complete Success

```json
{
  "order_id": "6bf5e878-52b0-4fb8-a9cb-8af517594e89",
  "status": "COMPLETED",
  "driver_earning": 658000,
  "earning_status": "PENDING_ESCROW",
  "escrow_release_at": "2026-06-04T12:30:00Z"
}
```

---

## Transaction & Concurrency Boundaries

Every mutation SHALL lock in consistent order: `driver_assignment` → `service_order` →
`driver_profile/driver_wallet`. Không transaction nào được lock ngược thứ tự để tránh deadlock.

Accept/reject/start/complete use `Idempotency-Key` records. Same key + same hash replays response;
same key + different payload trả 409. Lock timeout target 800 ms; timeout trả 409/503 retryable,
không retry vô hạn trong transaction.

Escrow worker SHALL use `FOR UPDATE SKIP LOCKED` để nhiều workers xử lý batch an toàn:

```sql
SELECT id
FROM service_order
WHERE status = 'COMPLETED'
  AND escrow_processed = FALSE
  AND escrow_release_at <= NOW()
ORDER BY escrow_release_at ASC
FOR UPDATE SKIP LOCKED
LIMIT 100;
```

---

## Migration & Rollout Plan

1. Áp dụng canonical status migration từ Specs #002/#003 trước.
2. Tạo `driver_assignment`, `driver_wallet`, service-order/profile/location extensions và indexes.
3. Backfill legacy `ACCEPTED → ASSIGNED`, tạo accepted assignment cho rows có driver.
4. Backfill wallet từ Driver earning transaction history; reconcile totals.
5. Deploy read-only dashboard/history/profile.
6. Deploy Manager assignment + Driver accept/reject dưới feature flag.
7. Deploy arrive/start/location.
8. Deploy final-payment/complete sau Payment contract tests.
9. Deploy escrow worker với dry-run metrics trước khi credit thật.
10. Thay labels/actions trong sáu UI stubs và chạy end-to-end lifecycle.

---

## Privacy, Security & Observability

Customer contact chỉ expose cho assigned Driver trong active lifecycle; history mask tên/số.
Location không log chính xác; audit/log không chứa full phone, address hoặc payment secrets.

| Metric | Type | Labels |
|--------|------|--------|
| `driver_assignment_response_total` | Counter | `action`, `outcome` |
| `driver_order_transition_total` | Counter | `from`, `to`, `outcome` |
| `driver_location_update_duration_seconds` | Histogram | `outcome` |
| `driver_escrow_release_total` | Counter | `outcome` |
| `driver_assignment_lock_wait_seconds` | Histogram | `outcome` |
| `driver_workflow_api_error_total` | Counter | `endpoint`, `error_code` |

Alerts: lock deadlock >0; final-payment complete rejection spike; stale location >20%; escrow due
over 15 phút; wallet/order reconciliation mismatch; invalid ownership spike.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-03/04 | Final payment chỉ từ verified IPN |
| HR-05/06/07 | State/dispute guards |
| HR-08 | Assignment/order pessimistic lock |
| HR-10 | Driver RBAC + ownership |
| HR-11 | Notification/email async |
| HR-13 | Mọi transition có audit |
| HR-15 | Idempotent payment/workflow |
| HR-18 | Wallet không âm + same-TX audit |
| HR-19/20 | Brand + tiếng Việt |
| HR-21 | Không reserved-word table mới |
| AC-08 | Money NUMERIC/BigDecimal |
| AC-12/13/14 | Flyway, money audit, VARCHAR CHECK |
| AC-15/16 | Pagination + UI states |
| ES-03/04/05 | Validation, error contract, CORE tests |

---

## Out of Scope (Deferred)

1. Manager assignment UI/algorithm và override.
2. Payment IPN/reconciliation internals.
3. Withdrawal, deposit replenishment và financial reports.
4. Full dispute/damage workflow.
5. WebSocket/chat/navigation/profile edit.

---

## Open Questions

1. Chốt assignment response deadline mặc định và timeout auto-reject policy.
2. Chốt timeout khi Customer không trả final 70% trong `AWAITING_FINAL_PAYMENT`.
3. Chốt Zalo deep-link/privacy policy.
4. Chốt Driver có thể đồng thời giữ bao nhiêu future `ASSIGNED` orders.
5. Chốt migration/reconciliation owner cho legacy earnings và wallets.
