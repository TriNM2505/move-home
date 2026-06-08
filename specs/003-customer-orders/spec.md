# Feature Specification: Customer Orders Management

**Feature Branch:** `003-customer-orders`  
**Feature Number:** #3 of 30 — CORE (post-booking lifecycle)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 2 (cùng Spec 002; real-time tracking và rating hoàn thiện Sprint 4)

**CONTEXT.md reference:** v2.0 §2 Order lifecycle, Order State Machine, Hủy đơn & Hoàn tiền  
**Constitution reference:** v1.3.0 — HR-03, HR-05, HR-07, HR-10, HR-11, HR-13,
HR-19, HR-20, HR-21, AC-06, AC-07, AC-08, AC-09, AC-12, AC-14, AC-15, AC-16,
ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Customer screens 3.9 đến 3.13  
**Related specs:** Spec #002 Booking; Spec #004 Payment; Spec #006 Driver Workflow;
Spec #010 Disputes

---

## Goals

Xây dựng trải nghiệm quản lý đơn sau đặt cho Customer, từ lúc order chờ thanh toán cọc đến khi
hoàn thành, hủy hoặc phát sinh tranh chấp. Customer phải nhìn thấy đơn trong ba view rõ ràng:
đơn đang chờ, đơn đang giao và lịch sử. Mỗi order có trang chi tiết duy nhất hiển thị dữ liệu
tuyến đường, tài xế, báo giá snapshot, thanh toán và timeline audit theo thời gian thực tế.

Spec bảo vệ toàn vẹn state machine bằng cách chỉ cho phép Customer hủy ở các trạng thái được
`CONTEXT.md` cho phép, trả HTTP 409 cho transition không hợp lệ và ghi audit cho mọi thay đổi.
Customer chỉ được đọc hoặc thao tác trên order do chính mình sở hữu. Trang active hỗ trợ vị trí
Driver qua Leaflet + OpenStreetMap, ưu tiên polling 5 giây trong Sprint 4 và để sẵn boundary cho
WebSocket/SSE nâng cấp sau.

Sau khi order `COMPLETED`, Customer có thể gửi đúng một đánh giá gồm 1-5 sao, tối đa năm tags và
nhận xét tùy chọn. Rating mới cập nhật thống kê Driver trong transaction nhất quán. Toàn bộ năm
màn hình phải dùng Move_home forest green `#1B4D3E`, amber `#F5A623`, Be Vietnam Pro, tiếng Việt
có dấu và đủ trạng thái Loading/Empty/Error. List lớn dùng server-side pagination theo AC-15.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec #002 → Spec này →
> `SCREEN_INVENTORY.md`/UI stub → Code.

| Chủ đề | Quyết định canonical | Mapping UI/legacy |
|--------|----------------------|-------------------|
| Trạng thái đầu vào | Spec #002 kết thúc tại `PENDING_PAYMENT` | Stub `PENDING` là alias display, không lưu DB |
| Đơn đang chờ | `PENDING_PAYMENT`, `CONFIRMED`, `ASSIGNED` | "Đang chờ thanh toán", "Chờ phân công", "Đã có tài xế" |
| Đơn active | `IN_PROGRESS`, `AWAITING_FINAL_PAYMENT` | Stub `ACCEPTED` map sang `ASSIGNED`; không tạo status `ACCEPTED` mới |
| Lịch sử | `COMPLETED`, `CANCELLED`, `IN_DISPUTE` | Stub `DISPUTED` map sang `IN_DISPUTE` |
| Customer cancel | Chỉ `PENDING_PAYMENT` hoặc `CONFIRMED` | Không cho hủy `ASSIGNED`/`IN_PROGRESS`; liên hệ Manager |
| Refund khi Customer hủy | Không refund; `PENDING_PAYMENT` chưa mất tiền, `CONFIRMED` mất cọc | RefundRecord chỉ khi COMPANY hủy, ngoài scope |
| Real-time | Polling 5 giây là baseline Sprint 4 | WebSocket/SSE là nâng cấp, không block Sprint 2 |
| Rating window | Sau `COMPLETED`, trong escrow 2 giờ | Quá hạn trả 409 `RATING_WINDOW_EXPIRED` |
| Timeline | Đọc từ `order_audit_log` | Không suy diễn timeline chỉ từ current status |
| Schema | Mở rộng V5 qua Flyway migration mới | Không sửa migration đã chạy |

---

## Scope Summary

**In scope:**

1. `GET /api/customer/orders/pending` — list order đang chờ theo canonical mapping.
2. `GET /api/customer/orders/active` — list order đang vận chuyển hoặc chờ final payment.
3. `GET /api/customer/orders/history` — lịch sử server-side pagination.
4. `GET /api/customer/orders/{id}` — chi tiết order của chính Customer.
5. `POST /api/customer/orders/{id}/cancel` — Customer hủy hợp lệ.
6. `GET /api/customer/orders/{id}/location` — vị trí Driver hiện tại cho active order.
7. `GET /api/customer/orders/{id}/rate-form` — kiểm tra eligibility đánh giá.
8. `POST /api/customer/orders/{id}/rate` — tạo một rating.
9. Timeline từ `order_audit_log`, thông tin Driver và pricing snapshot.
10. Filter pills, server-side pagination và Empty/Loading/Error states.
11. Flyway migration cho `driver_location`, `order_rating` và indexes.
12. Audit log cho cancel và rating.

**Out of scope:**

1. Driver accept, bắt đầu/chuyển trạng thái chuyến — Spec #006.
2. VNPay IPN, final payment và reconciliation — Spec #004.
3. Tạo/xử lý DamageReport hoặc DisputeReport — Spec #010.
4. COMPANY cancellation và RefundRecord processing — Spec #007.
5. Chat Customer-Driver.
6. Push notification native; spec chỉ định nghĩa event boundary.
7. Export CSV history.
8. Customer sửa order sau đặt.

---

## User Stories

**P1 (CORE):**

**US1:** Là Customer, tôi xem được mọi đơn đang chờ của mình để biết đơn nào chờ thanh toán,
chờ phân công hoặc đã có tài xế.

**US2:** Là Customer, tôi xem được đơn active và vị trí gần nhất của Driver trên bản đồ để biết
tiến độ vận chuyển.

**US3:** Là Customer, tôi duyệt lịch sử có pagination và filter status để tìm lại đơn cũ.

**US4:** Là Customer, tôi mở một order để xem đầy đủ tuyến đường, giá, tài xế và timeline.

**US5:** Là Customer, tôi hủy order khi còn ở trạng thái cho phép và hiểu rõ hậu quả tiền cọc.

**US6:** Là Customer, tôi đánh giá Driver một lần sau khi order hoàn thành để phản hồi chất lượng.

**P2 (Nice-to-have):**

**US7:** Là Customer, tôi nhận notification event khi order được phân công Driver hoặc đổi state.

**US8:** Là Customer, tôi thấy ETA ước tính từ vị trí Driver gần nhất khi dữ liệu location còn mới.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR phải có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Pending Orders List (FR-001..FR-006)

**FR-001**
WHEN Customer `ACTIVE` gọi
`GET /api/customer/orders/pending?page=0&size=10&status=ALL`,
THE system SHALL query chỉ rows có `customer_id` từ JWT, `deleted_at IS NULL`, status thuộc
`PENDING_PAYMENT|CONFIRMED|ASSIGNED`, sort `scheduled_at ASC, created_at DESC`, và trả HTTP 200:

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 10,
  "first": true,
  "last": true
}
```

**FR-002**
WHEN query parameter `status` là `ALL`, `PENDING_PAYMENT`, `CONFIRMED` hoặc `ASSIGNED`,
THE system SHALL áp dụng filter tương ứng; WHERE status khác enum trên, SHALL trả HTTP 422:

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ.",
  "details": [{"field": "status", "message": "Trạng thái lọc không được hỗ trợ."}]
}
```

**FR-003**
WHEN pending list có dữ liệu, THE frontend SHALL render mỗi card với `order_code`,
pickup → dropoff, `scheduled_at` theo `Asia/Ho_Chi_Minh`, `total_quote` VND, status badge và
`driver_summary` nullable; click card SHALL navigate `order-detail.html?id=<uuid>`.

**FR-004**
WHILE order status là `PENDING_PAYMENT` hoặc `CONFIRMED`, THE frontend SHALL hiển thị button
"Hủy đơn"; WHILE status là `ASSIGNED`, SHALL ẩn button và hiển thị "Liên hệ hỗ trợ để hủy".

**FR-005**
WHERE response `totalElements = 0`, THE frontend SHALL hiển thị empty state
"Bạn chưa có đơn nào đang chờ" và CTA "Đặt đơn ngay"; WHILE fetch pending, SHALL hiển thị
skeleton; WHERE API lỗi, SHALL hiển thị "Không thể tải danh sách đơn" + button "Thử lại".

**FR-006**
WHEN pending pagination được render, THE frontend SHALL có Previous/Next, page numbers với
ellipsis, selector `10|20|50|100` và text
"Hiển thị X-Y trong Z đơn"; WHERE `size > 100` hoặc `page < 0`, backend SHALL trả HTTP 422.

---

### Nhóm 2 — Active Orders & Real-time Tracking (FR-007..FR-013)

**FR-007**
WHEN Customer gọi `GET /api/customer/orders/active`, THE system SHALL trả orders của Customer
có status `IN_PROGRESS|AWAITING_FINAL_PAYMENT`, sort `updated_at DESC`, cùng driver summary và
location gần nhất nếu có.

**FR-008**
WHEN Customer mở `my-orders-active.html?id=<order_id>`, THE frontend SHALL render Leaflet map
với OpenStreetMap tiles, pickup marker, dropoff marker và driver marker; WHERE không có active
order, SHALL hiển thị "Bạn chưa có đơn nào đang giao".

**FR-009**
WHILE order status thuộc `IN_PROGRESS|AWAITING_FINAL_PAYMENT`, THE frontend SHALL gọi
`GET /api/customer/orders/{id}/location` mỗi 5 giây, tối đa 12 call/phút; response HTTP 200:

```json
{
  "driver_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "lat": 21.0285110,
  "lng": 105.8048170,
  "recorded_at": "2026-06-04T03:30:00Z",
  "stale": false
}
```

**FR-010**
WHEN location `recorded_at` cũ hơn 30 giây, THE system SHALL trả `stale=true` và frontend SHALL
hiển thị "Vị trí tài xế chưa được cập nhật"; WHERE cũ hơn 5 phút, SHALL ẩn ETA và không di chuyển
marker bằng dữ liệu stale.

**FR-011**
WHEN active order có Driver, THE frontend SHALL hiển thị tên, avatar, số điện thoại masked,
biển số, loại xe và `average_rating`; IF Customer click "Gọi tài xế", THEN mở
`tel:<driver_phone>` sau confirm dialog.

**FR-012**
WHEN active page render timeline, THE system SHALL trả và frontend SHALL hiển thị canonical
events theo thứ tự thời gian: `ORDER_CONFIRMED`, `DRIVER_ASSIGNED`, `ORDER_IN_PROGRESS`,
`FINAL_PAYMENT_REQUESTED`, `ORDER_COMPLETED`; event chưa xảy ra hiển thị pending style.

**FR-013**
IF WebSocket/SSE được triển khai ở Sprint 4, THEN event `DRIVER_LOCATION_UPDATED` SHALL thay thế
polling khi connection healthy; WHERE connection mất hơn 10 giây, frontend SHALL tự fallback
về polling 5 giây mà không reload trang.

---

### Nhóm 3 — History List (FR-014..FR-019)

**FR-014**
WHEN Customer gọi
`GET /api/customer/orders/history?page=0&size=10&status=ALL&sort=completedAt,desc`,
THE system SHALL query status `COMPLETED|CANCELLED|IN_DISPUTE`, chỉ order owner, và trả Spring
`Page<OrderHistoryItem>` với đầy đủ metadata pagination.

**FR-015**
WHEN history filter là `ALL`, `COMPLETED`, `CANCELLED` hoặc `IN_DISPUTE`, THE backend SHALL
filter tương ứng; frontend SHALL map label "Tất cả", "Hoàn thành", "Đã hủy", "Khiếu nại".

**FR-016**
WHEN history có dữ liệu, THE frontend SHALL render table columns:
`Mã đơn`, `Ngày`, `Điểm đón`, `Điểm trả`, `Trạng thái`, `Tổng tiền`; mỗi row click SHALL mở
`order-detail.html?id=<uuid>` và keyboard Enter SHALL có cùng hành vi.

**FR-017**
WHEN history page size thay đổi giữa `10|20|50|100`, THE frontend SHALL reset về `page=0`,
gọi lại API và cập nhật query string; WHERE size ngoài tập hợp, backend SHALL trả HTTP 422.

**FR-018**
WHERE history không có row, THE frontend SHALL hiển thị "Bạn chưa có lịch sử đơn"; WHILE API
đang tải, SHALL render table skeleton; WHERE API lỗi, SHALL giữ filter hiện tại và hiển thị
"Không thể tải lịch sử đơn" với button "Tải lại".

**FR-019**
WHEN Customer chuyển page/filter liên tiếp, THE frontend SHALL cancel request cũ bằng
`AbortController`; WHERE response cũ về sau response mới, SHALL không overwrite state mới.

---

### Nhóm 4 — Order Detail Page (FR-020..FR-028)

**FR-020**
WHEN Customer gọi `GET /api/customer/orders/{id}`, THE system SHALL verify owner từ JWT và trả
HTTP 200 với năm section: order info, driver, route/map, pricing/payment breakdown, audit timeline.

**FR-021**
WHEN detail response được serialize, THE system SHALL trả pricing snapshot bất biến:

```json
{
  "base_fare": 300000,
  "peak_surcharge": 90000,
  "alley_surcharge": 60000,
  "floor_surcharge": 90000,
  "porter_fee": 400000,
  "total_quote": 940000,
  "deposit_amount": 282000,
  "final_payment_amount": 658000,
  "commission_rate_snapshot": "0.3000"
}
```

**FR-022**
WHEN order chưa có `driver_id`, THE frontend SHALL hiển thị "Chưa có tài xế được phân công";
IF driver tồn tại, THEN SHALL hiển thị driver profile và link gọi chỉ trong order active.

**FR-023**
WHEN detail route section render, THE frontend SHALL hiển thị pickup/dropoff address đầy đủ,
district, floor/elevator/alley metadata và map preview; WHERE tọa độ thiếu, SHALL hiển thị text
route mà không fail toàn page.

**FR-024**
WHEN detail timeline được load, THE system SHALL query `order_audit_log` theo
`order_id`, sort `created_at ASC, id ASC`, trả event type, localized label, actor role và timestamp;
metadata nội bộ nhạy cảm SHALL không xuất hiện trong Customer DTO.

**FR-025**
WHILE status là `PENDING_PAYMENT|CONFIRMED`, THE detail page SHALL hiển thị "Hủy đơn";
WHILE status là `COMPLETED` và rating chưa tồn tại trong 2 giờ, SHALL hiển thị "Đánh giá tài xế";
WHILE status là `IN_DISPUTE`, SHALL hiển thị "Xem khiếu nại".

**FR-026**
WHEN Customer click "Hủy đơn", THE frontend SHALL mở modal tiếng Việt hiển thị hậu quả:
`PENDING_PAYMENT` không mất tiền; `CONFIRMED` mất cọc 30%; submit SHALL yêu cầu reason 5-500 ký tự.

**FR-027**
WHERE order id không tồn tại hoặc soft-deleted, THE system SHALL trả HTTP 404 `ORDER_NOT_FOUND`;
WHERE order thuộc Customer khác, SHALL trả HTTP 403 `ORDER_OWNERSHIP_REQUIRED` theo HR-10.

**FR-028**
WHILE detail fetch, frontend SHALL hiển thị skeleton cho năm section; WHERE một optional section
như map/location lỗi, SHALL hiển thị lỗi cục bộ và vẫn render order info/pricing/timeline.

---

### Nhóm 5 — Cancel Order (FR-029..FR-032)

**FR-029**
WHEN Customer owner submit `POST /api/customer/orders/{id}/cancel` với
`Idempotency-Key: <uuid>` và body `{"reason":"Thay đổi kế hoạch chuyển nhà"}`, THE system SHALL
lock row bằng `SELECT ... FOR UPDATE`, validate reason 5-500 ký tự và status.

**FR-030**
WHEN locked order status là `PENDING_PAYMENT` hoặc `CONFIRMED`, THE system SHALL transition sang
`CANCELLED`, set `cancelled_by='CUSTOMER'`, `cancellation_reason`, `cancelled_at=NOW()`,
insert audit event `ORDER_CANCELLED_BY_CUSTOMER`, commit và trả HTTP 200:

```json
{
  "order_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "status": "CANCELLED",
  "refund_created": false,
  "message": "Đơn đã được hủy."
}
```

**FR-031**
WHERE cancel request gặp status khác `PENDING_PAYMENT|CONFIRMED`, THE system SHALL trả HTTP 409
`INVALID_STATUS_TRANSITION`, không đổi order và không insert audit; đặc biệt không cho Customer
hủy `ASSIGNED`, `IN_PROGRESS`, `AWAITING_FINAL_PAYMENT`, `COMPLETED` hoặc `IN_DISPUTE`.

**FR-032**
WHEN cancel `PENDING_PAYMENT`, THE system SHALL invalidate payment intent còn mở; WHEN cancel
`CONFIRMED`, SHALL giữ cọc theo policy và không tạo RefundRecord; WHERE email notification lỗi,
SHALL không rollback cancel transaction theo HR-11.

---

### Nhóm 6 — Rate Driver (FR-033..FR-039)

**FR-033**
WHEN Customer gọi `GET /api/customer/orders/{id}/rate-form`, THE system SHALL verify owner,
`status=COMPLETED`, driver tồn tại, `completed_at + INTERVAL '2 hours' >= NOW()` và chưa có rating;
nếu eligible, trả HTTP 200 với driver summary, order summary và tags cho phép.

**FR-034**
WHEN Customer submit `POST /api/customer/orders/{id}/rate`, THE request body SHALL có:

```json
{
  "stars": 5,
  "tags": ["Đúng giờ", "Lịch sự", "Xe sạch"],
  "comment": "Tài xế hỗ trợ cẩn thận."
}
```

**FR-035**
WHEN validate rating payload, THE system SHALL enforce `stars` integer 1-5; `tags` unique,
tối đa 5 và chỉ thuộc `Đúng giờ|Lịch sự|Xe sạch|Hỗ trợ tốt|Cẩn thận`; `comment` trim, nullable,
tối đa 500 ký tự; WHERE sai, SHALL trả HTTP 422 theo ES-04.

**FR-036**
WHEN rating hợp lệ, THE system SHALL trong một transaction:
insert `order_rating`, lock `driver_profile`, tính lại
`average_rating = ROUND(SUM(stars)::numeric / COUNT(*), 2)`, insert
`ORDER_RATED` audit event và trả HTTP 201.

**FR-037**
WHERE order không `COMPLETED`, THE system SHALL trả HTTP 409 `ORDER_NOT_COMPLETED`;
WHERE quá 2 giờ từ `completed_at`, SHALL trả HTTP 409 `RATING_WINDOW_EXPIRED`;
WHERE order đã rating, SHALL trả HTTP 409 `ORDER_ALREADY_RATED`.

**FR-038**
WHEN rating response thành công, THE frontend SHALL disable form, hiển thị
"Cảm ơn bạn đã đánh giá tài xế" và CTA "Xem lịch sử đơn"; SHALL không redirect sang ví Customer
vì hệ thống không có Customer wallet.

**FR-039**
WHEN rating bị xóa/ẩn bởi moderation trong spec tương lai, THE system SHALL không hard-delete
row; spec này chỉ tạo rating và không cung cấp update/delete endpoint cho Customer.

---

### Nhóm 7 — RBAC, Ownership & Notifications (FR-040..FR-042)

**FR-040**
WHERE JWT thiếu/hết hạn, THE system SHALL trả HTTP 401 `AUTHENTICATION_REQUIRED`; WHERE role
khác `CUSTOMER`, SHALL trả HTTP 403 `FORBIDDEN`; mọi endpoint trong spec SHALL lấy `customer_id`
từ JWT, không từ body/query.

**FR-041**
WHERE authenticated Customer truy cập order có `customer_id` khác JWT subject, THE system SHALL
trả HTTP 403 `ORDER_OWNERSHIP_REQUIRED` cho detail, location, cancel và rating; response SHALL
không tiết lộ order code, driver hoặc trạng thái.

**FR-042**
WHEN order nhận event `DRIVER_ASSIGNED`, `ORDER_IN_PROGRESS`, `FINAL_PAYMENT_REQUESTED`,
`ORDER_COMPLETED` hoặc `ORDER_CANCELLED`, THE system SHALL publish `CUSTOMER_ORDER_UPDATED`
qua outbox với `order_id`, `customer_id`, `event_type`, `occurred_at`; notification delivery lỗi
SHALL không rollback state transition.

---

## Non-Functional Requirements

**NFR-001**
List API với page 20 items SHALL có P90 dưới 500 ms và P99 dưới 1 giây ở 50 request/giây.

**NFR-002**
Location polling SHALL đúng chu kỳ 5 giây, tối đa 12 call/phút/tab và dừng khi tab hidden hơn
30 giây hoặc order không còn active.

**NFR-003**
Cancel API SHALL phản hồi P90 dưới 1 giây và bảo đảm exactly-once với idempotency key.

**NFR-004**
Rating mới SHALL xuất hiện trong `driver_profile.average_rating` và Driver DTO trong vòng 1 phút.

**NFR-005**
Map view với pickup, dropoff và một driver marker SHALL interactive dưới 2 giây trên mạng 4G.

**NFR-006**
History pagination SHALL hỗ trợ ít nhất 1.000 order/Customer mà không load toàn bộ rows vào memory.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/customer/orders/pending` | `page,size,status` | 200 `Page<OrderCard>` | Customer owner |
| GET | `/api/customer/orders/active` | none | 200 `List<ActiveOrder>` | Customer owner |
| GET | `/api/customer/orders/history` | `page,size,status,sort` | 200 `Page<HistoryItem>` | Customer owner |
| GET | `/api/customer/orders/{id}` | path UUID | 200 `OrderDetail` | Customer owner |
| GET | `/api/customer/orders/{id}/location` | path UUID | 200 `DriverLocationDto` | Customer owner |
| POST | `/api/customer/orders/{id}/cancel` | reason + idempotency key | 200 cancelled DTO | Customer owner |
| GET | `/api/customer/orders/{id}/rate-form` | path UUID | 200 eligibility DTO | Customer owner |
| POST | `/api/customer/orders/{id}/rate` | stars, tags, comment | 201 rating DTO | Customer owner |

### Common Page Response

```json
{
  "content": [
    {
      "id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
      "order_code": "MH2026060400001",
      "pickup_district": "Ba Đình",
      "dropoff_district": "Cầu Giấy",
      "scheduled_at": "2026-06-06T01:30:00Z",
      "total_quote": 940000,
      "status": "CONFIRMED",
      "status_label": "Chờ phân công tài xế"
    }
  ],
  "totalElements": 121,
  "totalPages": 13,
  "number": 0,
  "size": 10,
  "first": true,
  "last": false
}
```

### Common Error Response

```json
{
  "error_code": "INVALID_STATUS_TRANSITION",
  "message": "Không thể hủy đơn ở trạng thái hiện tại.",
  "details": [
    {
      "field": "status",
      "message": "Chỉ được hủy đơn đang chờ thanh toán hoặc chờ phân công."
    }
  ],
  "timestamp": "2026-06-04T04:15:00Z",
  "path": "/api/customer/orders/5af5e878-52b0-4fb8-a9cb-8af517594e89/cancel"
}
```

---

## Data Model

### Migration Strategy

Không sửa `V5__create_service_order_table.sql`. Tạo migration mới để:

1. thay status CHECK legacy bằng canonical states;
2. bổ sung `cancelled_by`, payment timestamps và optimistic `version`;
3. tạo `driver_location`;
4. tạo `order_rating`;
5. bảo đảm `order_audit_log` từ Spec #002 tồn tại;
6. thêm partial indexes cho ba Customer list.

### Extend `service_order`

```sql
ALTER TABLE service_order
    ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(20),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_cancelled_by
    CHECK (cancelled_by IS NULL OR cancelled_by IN ('CUSTOMER', 'COMPANY', 'SYSTEM'));

ALTER TABLE service_order DROP CONSTRAINT IF EXISTS service_order_status_check;

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_status
    CHECK (status IN (
        'PENDING_PAYMENT',
        'CONFIRMED',
        'ASSIGNED',
        'IN_PROGRESS',
        'AWAITING_FINAL_PAYMENT',
        'COMPLETED',
        'IN_DISPUTE',
        'CANCELLED'
    ));
```

Migration phải backfill status legacy trước khi enforce CHECK:

```sql
UPDATE service_order SET status = 'CONFIRMED' WHERE status = 'PENDING';
UPDATE service_order SET status = 'ASSIGNED' WHERE status = 'ACCEPTED';
UPDATE service_order SET status = 'IN_DISPUTE' WHERE status = 'DISPUTED';
```

### Table `driver_location`

```sql
CREATE TABLE driver_location (
    driver_id         UUID           NOT NULL REFERENCES app_user(id),
    current_order_id  UUID           REFERENCES service_order(id),
    lat               NUMERIC(10, 7) NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng               NUMERIC(10, 7) NOT NULL CHECK (lng BETWEEN -180 AND 180),
    recorded_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_location PRIMARY KEY (driver_id)
);

CREATE INDEX idx_driver_location_current_order
    ON driver_location (current_order_id)
    WHERE current_order_id IS NOT NULL;
```

`driver_location` lưu latest location cho read path nhẹ. Lịch sử GPS chi tiết không thuộc scope.

### Table `order_rating`

```sql
CREATE TABLE order_rating (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    order_id     UUID        NOT NULL REFERENCES service_order(id),
    customer_id  UUID        NOT NULL REFERENCES app_user(id),
    driver_id    UUID        NOT NULL REFERENCES app_user(id),
    stars        SMALLINT    NOT NULL CHECK (stars BETWEEN 1 AND 5),
    tags         TEXT[]      NOT NULL DEFAULT ARRAY[]::TEXT[],
    comment      VARCHAR(500),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT pk_order_rating PRIMARY KEY (id),
    CONSTRAINT uq_order_rating_order UNIQUE (order_id),
    CONSTRAINT ck_order_rating_tags_count CHECK (cardinality(tags) <= 5)
);

CREATE INDEX idx_order_rating_driver_created
    ON order_rating (driver_id, created_at DESC)
    WHERE deleted_at IS NULL;
```

Allowed tags phải validate ở Java và có thể enforce DB:

```sql
ALTER TABLE order_rating
    ADD CONSTRAINT ck_order_rating_allowed_tags
    CHECK (
        tags <@ ARRAY[
            'Đúng giờ',
            'Lịch sự',
            'Xe sạch',
            'Hỗ trợ tốt',
            'Cẩn thận'
        ]::TEXT[]
    );
```

### Audit & Customer List Indexes

```sql
CREATE INDEX idx_service_order_customer_pending
    ON service_order (customer_id, scheduled_at ASC, created_at DESC)
    WHERE deleted_at IS NULL
      AND status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ASSIGNED');

CREATE INDEX idx_service_order_customer_active
    ON service_order (customer_id, updated_at DESC)
    WHERE deleted_at IS NULL
      AND status IN ('IN_PROGRESS', 'AWAITING_FINAL_PAYMENT');

CREATE INDEX idx_service_order_customer_history
    ON service_order (customer_id, completed_at DESC, created_at DESC)
    WHERE deleted_at IS NULL
      AND status IN ('COMPLETED', 'CANCELLED', 'IN_DISPUTE');
```

Cancel transaction:

```sql
BEGIN;

SELECT id, status, customer_id
FROM service_order
WHERE id = :order_id
  AND deleted_at IS NULL
FOR UPDATE;

UPDATE service_order
SET status = 'CANCELLED',
    cancelled_by = 'CUSTOMER',
    cancellation_reason = :reason,
    cancelled_at = NOW(),
    version = version + 1
WHERE id = :order_id
  AND customer_id = :customer_id
  AND status IN ('PENDING_PAYMENT', 'CONFIRMED');

INSERT INTO order_audit_log
    (order_id, event_type, actor_id, actor_role, from_state, to_state, metadata)
VALUES
    (:order_id, 'ORDER_CANCELLED_BY_CUSTOMER', :customer_id, 'CUSTOMER',
     :from_state, 'CANCELLED', jsonb_build_object('reason', :reason));

COMMIT;
```

Application SHALL verify update row count bằng 1 trước khi insert audit/commit.

Rating aggregate query:

```sql
SELECT ROUND(AVG(stars)::numeric, 2) AS average_rating,
       COUNT(*) AS rating_count
FROM order_rating
WHERE driver_id = :driver_id
  AND deleted_at IS NULL;
```

---

## State Machine

### Canonical Order Lifecycle

```text
PENDING_PAYMENT
  ├─ Customer/System cancel ───────────────→ CANCELLED
  └─ valid deposit IPN (Spec #004) ───────→ CONFIRMED

CONFIRMED
  ├─ Customer cancel, mất cọc ─────────────→ CANCELLED
  └─ Manager assign (Spec #006) ──────────→ ASSIGNED

ASSIGNED
  ├─ Driver reject/reassign (Spec #006) ──→ ASSIGNED
  ├─ Driver start (Spec #006) ────────────→ IN_PROGRESS
  └─ Manager/company cancel ──────────────→ CANCELLED

IN_PROGRESS
  ├─ Driver request final payment ─────────→ AWAITING_FINAL_PAYMENT
  └─ Driver dispute ───────────────────────→ IN_DISPUTE

AWAITING_FINAL_PAYMENT
  ├─ valid final IPN + Driver complete ────→ COMPLETED
  └─ Driver dispute ───────────────────────→ IN_DISPUTE

COMPLETED
  └─ Customer DamageReport trong 2h ──────→ IN_DISPUTE

IN_DISPUTE
  ├─ Manager resolve ──────────────────────→ COMPLETED
  └─ Manager cancel ───────────────────────→ CANCELLED
```

Rating gắn vào `COMPLETED` order nhưng không làm thay đổi order status.

### Customer-owned Transitions in This Spec

| From | Action | To | Tiền/side effect |
|------|--------|----|------------------|
| `PENDING_PAYMENT` | Customer cancel | `CANCELLED` | Không mất tiền; invalidate intent |
| `CONFIRMED` | Customer cancel | `CANCELLED` | Mất cọc 30%; không RefundRecord |
| `COMPLETED` | Customer rate | `COMPLETED` | Insert rating; update Driver average |

Mọi transition khác do Customer gọi SHALL trả HTTP 409 theo HR-05.

---

## Error Matrix

| HTTP | `error_code` | Khi nào |
|------|--------------|---------|
| 400 | `MALFORMED_JSON` | JSON không parse được |
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn |
| 403 | `FORBIDDEN` | Role không phải Customer |
| 403 | `ORDER_OWNERSHIP_REQUIRED` | Order thuộc Customer khác |
| 404 | `ORDER_NOT_FOUND` | UUID không tồn tại/soft-deleted |
| 409 | `INVALID_STATUS_TRANSITION` | Cancel/rate sai state |
| 409 | `ORDER_ALREADY_RATED` | UNIQUE order rating đã tồn tại |
| 409 | `RATING_WINDOW_EXPIRED` | Quá 2 giờ sau completed |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Key cũ với payload khác |
| 422 | `VALIDATION_ERROR` | Query/body sai range/enum |
| 429 | `RATE_LIMITED` | Vượt giới hạn endpoint |
| 503 | `LOCATION_TEMPORARILY_UNAVAILABLE` | Location service tạm lỗi |

---

## Acceptance Criteria

**AC1**
Pending page chỉ hiển thị order của Customer hiện tại thuộc
`PENDING_PAYMENT|CONFIRMED|ASSIGNED`, với filter và pagination đúng.

**AC2**
Active page render map dưới 2 giây, polling mỗi 5 giây và cảnh báo rõ khi location stale.

**AC3**
History hỗ trợ filter `COMPLETED|CANCELLED|IN_DISPUTE`, page size `10|20|50|100` và hơn
1.000 order không lag.

**AC4**
Order detail hiển thị đủ năm section và timeline khớp `order_audit_log` theo thời gian tăng dần.

**AC5**
Customer hủy `PENDING_PAYMENT` hoặc `CONFIRMED` thành công; mọi state khác trả 409, không mutate.

**AC6**
Hai cancel request đồng thời/idempotent chỉ tạo một transition và một audit event.

**AC7**
Customer chỉ tạo được một rating 1-5 sao cho completed order của mình trong window 2 giờ.

**AC8**
Rating thành công cập nhật `driver_profile.average_rating` chính xác đến hai chữ số thập phân.

**AC9**
Năm màn hình có tiếng Việt đầy đủ, brand Move_home và đủ Loading/Empty/Error states.

---

## Edge Cases & Error Handling

| ID | Tình huống | Expected Behavior |
|----|------------|-------------------|
| EC-01 | Customer đoán UUID order người khác | 403, không lộ metadata |
| EC-02 | Order bị soft-delete giữa list và detail | Detail 404; list bỏ row |
| EC-03 | Cancel và Manager assign đồng thời | Row lock cho đúng một transition; request thua trả 409 |
| EC-04 | Cancel `CONFIRMED` sau khi đã cọc | Cancel thành công, không RefundRecord |
| EC-05 | Cancel `ASSIGNED` dù vừa phân công | 409; Customer liên hệ Manager |
| EC-06 | Location chưa từng được ghi | Map vẫn hiện route, thông báo chưa có vị trí |
| EC-07 | Location cũ hơn 5 phút | Ẩn ETA, giữ marker với stale warning |
| EC-08 | Driver không có avatar/phone/plate | Dùng fallback avatar; ẩn action không có dữ liệu |
| EC-09 | Rating submit đúng lúc hết window | Backend time quyết định; quá hạn trả 409 |
| EC-10 | Hai tab submit rating đồng thời | UNIQUE order_id cho một 201, một 409 |
| EC-11 | Rating tags trùng nhau | 422; không tự silently deduplicate |
| EC-12 | Audit metadata chứa thông tin nội bộ | Customer DTO loại bỏ field nhạy cảm |

---

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TC-01 | Customer A gọi pending list có order A/B | Chỉ order A xuất hiện |
| TC-02 | Filter pending `ASSIGNED`, size 20 | Chỉ assigned của owner, metadata page đúng |
| TC-03 | Location polling trong 60 giây | Tối đa 12 request |
| TC-04 | Location `recorded_at = now - 31s` | `stale=true`, UI cảnh báo |
| TC-05 | History Customer có 1.005 orders | 101 pages size 10, không full-table load |
| TC-06 | Cancel `PENDING_PAYMENT` hợp lệ | 200, CANCELLED, một audit, không refund |
| TC-07 | Cancel `IN_PROGRESS` | 409, status/audit không đổi |
| TC-08 | Hai request cancel cùng idempotency key | Cùng response, một transition |
| TC-09 | Rating 5 sao + ba tags hợp lệ | 201, aggregate Driver cập nhật |
| TC-10 | Rating stars=6/comment 501 chars | 422 với cả hai field errors |
| TC-11 | Rating order của Customer khác | 403, không insert |
| TC-12 | Submit rating lần hai | 409 `ORDER_ALREADY_RATED` |

### Required Automated Test Layers

1. Unit tests cho state transition guard, rating validation và status mapping.
2. Repository tests với PostgreSQL/Testcontainers cho partial index queries và row lock.
3. Integration tests cho ownership, pagination, cancel transaction và rating aggregate.
4. Contract tests cho DTO/error format.
5. Frontend tests cho Loading/Empty/Error, filters, stale location và action visibility.
6. CORE feature coverage tối thiểu 70% theo ES-05.

---

## Frontend Screen Contract

| Screen | Required behavior |
|--------|-------------------|
| `my-orders-pending.html` | Pending filters, cards, pagination, cancel modal |
| `my-orders-active.html` | Active selector, Leaflet map, driver card, polling/timeline |
| `my-orders-history.html` | History table, filters, server-side pagination |
| `order-detail.html?id=<uuid>` | Five sections, status-aware actions, partial error handling |
| `order-rate.html?id=<uuid>` | Eligibility load, stars, tags, comment, success state |

Mỗi screen SHALL:

1. kiểm tra JWT/role trước fetch;
2. dùng CSS variables từ `frontend/css/styles.css`;
3. không inline màu ngoài brand;
4. hiển thị text tiếng Việt có dấu;
5. format VND bằng `Intl.NumberFormat('vi-VN')`;
6. format timestamp UTC sang `Asia/Ho_Chi_Minh`;
7. có accessible labels, keyboard focus và disabled states;
8. không trust status/action lấy từ DOM hoặc localStorage.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Invalid state transition trả 409, không mutate |
| HR-07 | Customer không resolve `IN_DISPUTE` |
| HR-10 | Role + ownership bắt buộc mọi endpoint |
| HR-11 | Notification/email lỗi không rollback cancel |
| HR-13 | Cancel/rating và mọi state transition có audit |
| HR-19 | Move_home forest green + amber + Be Vietnam Pro |
| HR-20 | Toàn bộ UI/error user-facing có dấu tiếng Việt |
| HR-21 | Dùng `service_order`, `order_rating`, không reserved words |
| AC-07 | DB UTC, UI Asia/Ho_Chi_Minh |
| AC-08 | VND NUMERIC/BigDecimal, không float/double |
| AC-09 | Order/rating soft-delete |
| AC-12 | Schema qua Flyway migration |
| AC-14 | Status VARCHAR + CHECK, không PostgreSQL ENUM |
| AC-15 | History/pending dùng server-side pagination |
| AC-16 | Mỗi data-driven page có Empty/Loading/Error |
| ES-03 | Bean Validation + HTTP 422 |
| ES-04 | Error response thống nhất |
| ES-05 | CORE test coverage tối thiểu 70% |

---

## Query & Serialization Contracts

### Pending Query

Repository query SHALL parameterize toàn bộ input và tận dụng partial index:

```sql
SELECT so.id,
       so.order_code,
       so.pickup_district,
       so.dropoff_district,
       so.scheduled_at,
       so.total_quote,
       so.status,
       so.driver_id
FROM service_order so
WHERE so.customer_id = :customer_id
  AND so.deleted_at IS NULL
  AND so.status = ANY(:statuses)
ORDER BY so.scheduled_at ASC, so.created_at DESC
LIMIT :size OFFSET :offset;
```

`:statuses` chỉ được build server-side từ filter enum đã validate; không nối raw query string vào
SQL. Count query phải dùng cùng ownership/status predicates để `totalElements` chính xác.

### History Query

```sql
SELECT so.id,
       so.order_code,
       so.created_at,
       so.completed_at,
       so.cancelled_at,
       so.pickup_district,
       so.dropoff_district,
       so.status,
       so.total_quote
FROM service_order so
WHERE so.customer_id = :customer_id
  AND so.deleted_at IS NULL
  AND so.status = ANY(:statuses)
ORDER BY COALESCE(so.completed_at, so.cancelled_at, so.updated_at) DESC,
         so.id DESC
LIMIT :size OFFSET :offset;
```

Sort thứ hai theo `id` bảo đảm stable pagination khi nhiều order có cùng timestamp.

### Customer-safe Timeline DTO

Audit event trả cho Customer chỉ gồm:

```json
{
  "event_type": "DRIVER_ASSIGNED",
  "label": "Đã phân công tài xế",
  "occurred_at": "2026-06-04T03:30:00Z",
  "actor_label": "Move_home"
}
```

Response SHALL loại bỏ `ip_address`, internal actor id, HMAC/payment metadata, Manager notes,
idempotency key và raw exception. Label phải map server-side sang tiếng Việt có dấu.

---

## Observability & Operations

Backend SHALL phát các metrics không chứa PII:

| Metric | Type | Labels cho phép |
|--------|------|-----------------|
| `customer_order_list_duration_seconds` | Histogram | `view`, `status`, `outcome` |
| `customer_order_cancel_total` | Counter | `from_status`, `outcome` |
| `customer_order_rating_total` | Counter | `stars`, `outcome` |
| `driver_location_age_seconds` | Histogram | `stale` |
| `customer_order_api_error_total` | Counter | `endpoint`, `error_code` |

Log SHALL có correlation id, order id, actor role, event type và outcome; không log địa chỉ đầy
đủ, số điện thoại, nội dung comment hoặc tọa độ chính xác. Alert vận hành tối thiểu:

1. cancel error rate vượt 5% trong 10 phút;
2. P95 pending/history list vượt 1 giây trong 15 phút;
3. hơn 20% active orders có location stale trên 5 phút;
4. rating aggregate transaction fail ba lần liên tiếp;
5. ownership violation tăng đột biến theo IP/correlation source.

---

## Privacy & Retention

1. Location endpoint chỉ hoạt động khi order active và Customer là owner.
2. Sau `COMPLETED|CANCELLED|IN_DISPUTE`, Customer endpoint không trả latest Driver coordinates.
3. Phone Driver chỉ trả khi order `ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT`; lịch sử mask số.
4. Rating comment là user-generated content; phải escape khi render để chống stored XSS.
5. Order và rating dùng soft-delete; retention/hard-delete tuân theo policy toàn hệ thống sau này.
6. Cache Customer DTO, nếu có, phải key theo `customer_id + order_id` và invalidate khi state đổi.

---

## Out of Scope (Deferred to Other Specs)

1. Payment IPN, deposit/final payment URL và reconciliation — Spec #004.
2. Driver nhận đơn, cập nhật location và vận hành chuyến — Spec #006.
3. RefundRecord khi COMPANY hủy — Spec #007.
4. DamageReport/DisputeReport creation và Manager resolution — Spec #010.
5. WebSocket infrastructure production, chat, push notification native và CSV export.

---

## Open Questions

1. Chốt WebSocket/SSE provider trước Sprint 4; polling 5 giây là baseline được duyệt.
2. Chốt chính sách moderation rating và quyền ẩn comment trong spec Admin tương lai.
3. Chốt retention/location privacy cho lịch sử GPS; spec này chỉ giữ latest location.
4. Chốt Customer có được rating sau khi `IN_DISPUTE → COMPLETED` hay không và window tính lại từ
`resolved_at` hay `completed_at`.
