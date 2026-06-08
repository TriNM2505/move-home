# Feature Specification: Customer Booking Flow

**Feature Branch:** `002-customer-booking`  
**Feature Number:** #2 of 30 — CORE (foundation for revenue generation)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 2 (TBD ngày, sau Thu Ba 2026-06-02 demo)

**CONTEXT.md reference:** v2.0 §2 Order flow, Pricing formula, Maps API & OSRM, Thanh toán  
**Constitution reference:** v1.3.0 — HR-03, HR-04, HR-05, HR-09, HR-10, HR-11, HR-13,
HR-15, HR-19, HR-20, HR-21, AC-06, AC-07, AC-08, AC-09, AC-11, AC-12, AC-14, AC-16  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Customer booking Step 1-6 + Booking Success  
**Related specs:** Spec #001 Auth/RBAC; Spec #004 Payment; Spec #006 Driver Workflow

---

## Goals

Xây dựng luồng đặt đơn chuyển nhà hoàn chỉnh cho Customer đã xác thực email và có trạng thái
`ACTIVE`. Luồng gồm sáu bước có URL riêng: chọn loại xe, nhập điểm đón, nhập điểm trả, nhập chi
tiết vận chuyển, xem báo giá và xác nhận thanh toán. Mỗi bước phải lưu được trạng thái draft,
cho phép quay lại hoặc refresh trình duyệt mà không mất dữ liệu, đồng thời chỉ cho chính Customer
sở hữu draft đọc hoặc cập nhật.

Spec này định nghĩa chặt chẽ dữ liệu đầu vào, quy tắc validate, contract REST API, mô hình
`booking_draft`, thay đổi cần thiết cho bảng `service_order`, công thức báo giá, tích hợp OSRM,
fallback khoảng cách quận, snapshot giá và ranh giới bàn giao sang Spec #004 Payment. Kết quả
cuối của phạm vi booking là một `service_order` bất biến về giá ở trạng thái `PENDING_PAYMENT`,
kèm URL thanh toán cọc 30% do payment module tạo. Chỉ VNPay IPN hợp lệ mới được phép chuyển đơn
sang `CONFIRMED`; return URL không thay đổi DB.

Mục tiêu UX là giúp Customer tự tạo đơn và nhận báo giá minh bạch trong dưới 5 phút mà không cần
gọi điện. Tất cả nội dung user-facing phải dùng tiếng Việt có dấu, giao diện dùng Move_home
forest green + amber + Be Vietnam Pro, và các lỗi phải chỉ rõ field sai để Customer sửa ngay.
Pricing phải deterministic: cùng input và cùng pricing snapshot luôn tạo cùng breakdown.

---

## Source-of-Truth Resolution

> Khi prompt, UI stub, schema hiện tại và `CONTEXT.md` khác nhau, áp dụng hierarchy:
> `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec này → Code.

| Chủ đề | Quyết định áp dụng trong spec | Lý do |
|--------|-------------------------------|-------|
| Trạng thái sau confirm | `PENDING_PAYMENT`, không phải `PENDING` | `CONTEXT.md` định nghĩa order phải cọc 30% trước khi `CONFIRMED` |
| Draft storage | Bảng riêng `booking_draft`, không thêm `DRAFT` vào `service_order` | Giữ state machine production sạch; schema V5 yêu cầu nhiều field NOT NULL |
| Driver dispatch | Manager phân công thủ công sau `CONFIRMED` | `CONTEXT.md` A1; không auto-dispatch hoặc Driver tự pick trong spec này |
| Phương thức thanh toán | Chỉ VNPay 30% deposit trong production | `CONTEXT.md` xác nhận 100% VNPay, không COD, không ví Customer |
| Loại xe domain | `TRUCK_500KG`, `TRUCK_1T`, `TRUCK_15T` cho 3 UI cards | Đồng bộ 7 stub hiện có; mapping tới bảng giá domain được snapshot |
| Đơn giá/km | 20.000 / 30.000 / 40.000 VND | Giá đã duyệt trong `CONTEXT.md`; giá khởi điểm trên stub chỉ là display placeholder |
| Alley surcharge | `base_fare × 20%` | `CONTEXT.md` thắng mức fixed 200.000 VND trong prompt/stub |
| Floor surcharge | Tier 0% / 20% / 30% / 50%, lấy tầng cao hơn | `CONTEXT.md` thắng công thức 50.000 VND/tầng trong prompt |
| Porter fee | Theo loại xe: 150.000 / 200.000 / 300.000 VND/người | `CONTEXT.md` thắng mức fixed 300.000 VND/người |
| Booking Success | Chỉ hiển thị sau khi order được tạo và payment URL đã sinh | Trạng thái DB vẫn `PENDING_PAYMENT` cho đến IPN |
| Schema hiện tại | Phải mở rộng qua Flyway migration mới | V5 hiện thiếu draft, vehicle, surcharge breakdown và address metadata |

---

## Scope Summary

**In scope:**

1. `POST /api/customer/booking-drafts` — tạo draft sau Step 1.
2. `PATCH /api/customer/booking-drafts/{id}` — cập nhật Step 2-4.
3. `GET /api/customer/booking-drafts/{id}` — resume draft của chính Customer.
4. `DELETE /api/customer/booking-drafts/{id}` — hủy draft idempotently.
5. `POST /api/customer/booking-drafts/{id}/quote` — tính và snapshot báo giá Step 5.
6. `POST /api/customer/booking-drafts/{id}/confirm` — tạo `service_order` `PENDING_PAYMENT`.
7. Pricing formula: base + peak + alley + floor + porter.
8. Geocoding address và OSRM route distance/duration.
9. Fallback bảng khoảng cách 12 quận khi geocoding/OSRM không khả dụng.
10. Tạo payment intent deposit 30% qua boundary contract với Spec #004.
11. Booking Success screen hiển thị order code, summary và payment state.
12. Audit log cho quote snapshot, order creation và state transition.
13. Email xác nhận tạo đơn gửi async.
14. Draft persistence backend + localStorage reference.
15. Flyway migrations để bổ sung schema cần thiết.

**Out of scope:**

1. Xử lý VNPay IPN, verify HMAC và chuyển `PENDING_PAYMENT → CONFIRMED` — Spec #004.
2. Thanh toán 70% tại chỗ — Spec #004.
3. Manager phân công Driver — Spec #006.
4. Driver accept/reject, tracking và workflow vận chuyển — Spec #006.
5. Customer hủy order và RefundRecord — Spec #007.
6. Customer sửa order sau khi đã `CONFIRMED`.
7. DamageReport, DisputeReport, escrow và Driver wallet.
8. Public Guest quote endpoint.
9. Maps autocomplete và interactive route editor.
10. COD hoặc Customer wallet payment.

---

## User Stories

**P1 (CORE — bắt buộc Sprint 2 demo):**

**US1:** Là Customer đã verified email và `ACTIVE`, tôi có thể chọn loại xe ở Step 1 để bắt
  đầu tạo booking draft.
**US2:** Là Customer, tôi có thể nhập điểm đón ở Step 2 với quận, địa chỉ chi tiết, tầng,
  thang máy và ngõ nhỏ để hệ thống hiểu điều kiện tiếp cận.
**US3:** Là Customer, tôi có thể nhập điểm trả ở Step 3 với cùng quy tắc validate như điểm đón.
**US4:** Là Customer, tôi có thể chọn lịch hẹn, số người bốc xếp, dịch vụ thêm và ghi chú ở
  Step 4.
**US5:** Là Customer ở Step 5, tôi thấy báo giá itemized gồm base, peak, alley, floor, porter,
  tổng cộng, khoảng cách và thời lượng ước tính.
**US6:** Là Customer ở Step 6, tôi có thể xác nhận booking, nhận mã đơn và tiếp tục thanh toán
  cọc 30% qua VNPay.
**US7:** Là Customer, draft của tôi tồn tại qua page refresh và chỉ tôi có quyền resume.

**P2 (Nice-to-have, không block Sprint 2 demo):**

**US8:** Là Customer, tôi có thể quay lại bước trước mà không mất dữ liệu đã nhập.
**US9:** Là Customer, tôi thấy cảnh báo "ước tính" khi hệ thống phải dùng fallback khoảng cách
  quận thay vì OSRM.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven happy path) | WHILE (state-driven) |
> WHERE (unwanted/error path) | IF/THEN (optional branch).
> Mỗi FR thuộc ít nhất một mẫu EARS và có HTTP code, JSON contract hoặc quy tắc dữ liệu cụ thể.

---

### Nhóm 1 — Step 1 Vehicle Selection & Draft Creation (FR-001..FR-005)

**FR-001**
WHEN Customer có JWT hợp lệ với `role=CUSTOMER`, `status=ACTIVE` mở
`/customer/booking-step1-vehicle.html`, THE system SHALL hiển thị đúng 3 lựa chọn:

| `vehicle_type` | Label UI | Mô tả | Đơn giá/km snapshot | Phí bốc xếp/người |
|----------------|----------|-------|--------------------|-------------------|
| `TRUCK_500KG` | Xe tải 500kg | Phù hợp đồ ít, 1-2 người | 20.000 VND | 150.000 VND |
| `TRUCK_1T` | Xe tải 1 tấn | Phù hợp gia đình nhỏ | 30.000 VND | 200.000 VND |
| `TRUCK_15T` | Xe tải 1.5 tấn | Phù hợp gia đình lớn | 40.000 VND | 300.000 VND |

**FR-002**
WHEN Customer chọn một vehicle card, THE frontend SHALL:
1. bỏ selection cũ;
2. thêm forest green border `#1B4D3E`;
3. hiển thị amber checkmark `#F5A623`;
4. enable button "Tiếp tục";
5. lưu `{ "vehicle_type": "<enum>", "current_step": 1 }` vào
   `localStorage.booking_draft`.

**FR-003**
WHEN Customer click "Tiếp tục" ở Step 1, THE frontend SHALL gọi
`POST /api/customer/booking-drafts` với body:
```json
{ "vehicle_type": "TRUCK_1T" }
```
và backend SHALL trả HTTP 201:
```json
{
  "draft_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "current_step": 2,
  "expires_at": "2026-06-05T10:00:00Z"
}
```
Sau đó frontend redirect đến
`/customer/booking-step2-pickup.html?draft_id=<uuid>`.

**FR-004**
WHEN backend tạo booking draft, THE system SHALL insert đúng một row `booking_draft` với:
- `customer_id` lấy từ JWT, không nhận từ request body;
- `vehicle_type` thuộc enum hợp lệ;
- `current_step = 2`;
- `quoted_at = NULL`, `confirmed_order_id = NULL`;
- `expires_at = NOW() + INTERVAL '24 hours'`;
- `created_at = updated_at = NOW()` UTC.

**FR-005**
WHERE request Step 1 không authenticated, role khác `CUSTOMER`, status khác `ACTIVE`, hoặc
`vehicle_type` ngoài `TRUCK_500KG|TRUCK_1T|TRUCK_15T`, THE system SHALL:
- trả 401 `AUTHENTICATION_REQUIRED` nếu thiếu/invalid JWT;
- trả 403 `FORBIDDEN` nếu role khác Customer;
- trả 403 `ACCOUNT_NOT_ACTIVE` nếu Customer chưa ACTIVE;
- trả 422 `VALIDATION_ERROR` cho vehicle type sai;
- không insert draft.

---

### Nhóm 2 — Step 2 Pickup Address (FR-006..FR-010)

**FR-006**
WHEN Customer owner mở Step 2 với `draft_id` hợp lệ, THE system SHALL trả và frontend SHALL
hiển thị dropdown gồm đúng 12 district codes:
`BA_DINH`, `HOAN_KIEM`, `HAI_BA_TRUNG`, `DONG_DA`, `TAY_HO`, `CAU_GIAY`,
`THANH_XUAN`, `LONG_BIEN`, `HA_DONG`, `HOANG_MAI`, `BAC_TU_LIEM`, `NAM_TU_LIEM`.
Label UI phải là tên tiếng Việt có dấu.

**FR-007**
WHEN Customer submit Step 2, THE frontend SHALL gọi
`PATCH /api/customer/booking-drafts/{id}` với body:
```json
{
  "step": 2,
  "data": {
    "pickup_district": "BA_DINH",
    "pickup_address": "Số 25 ngõ 68 phố Đội Cấn, phường Đội Cấn",
    "pickup_floor": 3,
    "pickup_has_elevator": false,
    "pickup_has_alley": true
  }
}
```

**FR-008**
WHEN backend validate pickup data, THE system SHALL enforce:
- `pickup_district`: một trong 12 codes FR-006;
- `pickup_address`: trim, 10-200 ký tự;
- `pickup_floor`: integer từ 0 đến 30, mặc định 0;
- `pickup_has_elevator`: boolean, mặc định false;
- `pickup_has_alley`: boolean, mặc định false.
Nếu tất cả hợp lệ, update draft, set `current_step = GREATEST(current_step, 3)`, trả HTTP 200.

**FR-009**
WHEN pickup address hợp lệ được lưu, THE system SHALL enqueue geocoding attempt để lấy
`pickup_lat NUMERIC(10,7)` và `pickup_lng NUMERIC(10,7)`. Geocoding input phải ghép:
`<pickup_address>, <district label>, Hà Nội, Việt Nam`; kết quả ngoài bounding box Hà Nội
`lat 20.80..21.25`, `lng 105.65..106.05` bị bỏ qua.

**FR-010**
WHERE draft không tồn tại, hết hạn, đã confirm, thuộc Customer khác, hoặc payload pickup sai,
THE system SHALL lần lượt trả:
- 404 `DRAFT_NOT_FOUND`;
- 410 `DRAFT_EXPIRED`;
- 409 `DRAFT_ALREADY_CONFIRMED`;
- 403 `FORBIDDEN`;
- 422 `VALIDATION_ERROR` với toàn bộ `details[]`;
và không update bất kỳ field nào.

---

### Nhóm 3 — Step 3 Dropoff Address (FR-011..FR-015)

**FR-011**
WHEN Customer owner mở Step 3, THE frontend SHALL load draft hiện tại và hiển thị dropdown
dropoff gồm đúng 12 district codes tại FR-006, không tự copy pickup sang dropoff.

**FR-012**
WHEN Customer submit Step 3, THE frontend SHALL gọi
`PATCH /api/customer/booking-drafts/{id}` với body:
```json
{
  "step": 3,
  "data": {
    "dropoff_district": "CAU_GIAY",
    "dropoff_address": "Tòa A2, phố Duy Tân, phường Dịch Vọng Hậu",
    "dropoff_floor": 5,
    "dropoff_has_elevator": false,
    "dropoff_has_alley": false
  }
}
```

**FR-013**
WHEN backend validate dropoff data, THE system SHALL áp dụng cùng constraint Step 2:
district thuộc whitelist; address 10-200 ký tự; floor integer 0-30; elevator/alley boolean.
Nếu hợp lệ, update draft, set `current_step = GREATEST(current_step, 4)`, trả HTTP 200.

**FR-014**
WHEN dropoff address hợp lệ được lưu, THE system SHALL enqueue geocoding attempt theo cùng
bounding box Hà Nội như FR-009 và lưu `dropoff_lat`, `dropoff_lng` nếu kết quả đáng tin cậy.

**FR-015**
WHERE normalized pickup address và normalized dropoff address giống hệt nhau, THE system SHALL
trả HTTP 422:
```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ.",
  "details": [{
    "field": "dropoff_address",
    "message": "Điểm trả phải khác điểm đón."
  }]
}
```
và giữ draft ở Step 3.

---

### Nhóm 4 — Step 4 Schedule & Service Details (FR-016..FR-021)

**FR-016**
WHEN Customer submit Step 4, THE frontend SHALL gọi
`PATCH /api/customer/booking-drafts/{id}` với body:
```json
{
  "step": 4,
  "data": {
    "scheduled_at": "2026-06-06T01:30:00Z",
    "porter_count": 2,
    "packing_assistance": true,
    "assembly_assistance": false,
    "fragile_item_protection": true,
    "notes": "Có tủ lạnh nhỏ và khoảng 12 thùng đồ."
  }
}
```

**FR-017**
WHEN backend validate `scheduled_at`, THE system SHALL require timestamp ISO-8601 có timezone,
không sớm hơn `NOW() + INTERVAL '1 hour'`, không muộn hơn `NOW() + INTERVAL '30 days'`, và lưu
dưới dạng `TIMESTAMPTZ` UTC.

**FR-018**
WHEN backend validate porter và additional services, THE system SHALL require:
- `porter_count` integer thuộc `0|1|2|3`;
- `packing_assistance`, `assembly_assistance`, `fragile_item_protection` là boolean;
- additional services chỉ được lưu để hiển thị/notes trong Sprint 2, không cộng giá nếu chưa có
  pricing config được duyệt riêng.

**FR-019**
WHEN backend validate `notes`, THE system SHALL trim whitespace, cho phép null/blank, giới hạn
500 ký tự Unicode, và lưu text nguyên bản đã sanitize; HTML/script không được thực thi khi render.

**FR-020**
WHEN Step 4 hợp lệ, THE system SHALL update draft, invalidate quote cũ bằng cách set tất cả
pricing fields và `quoted_at` về NULL, set `current_step = GREATEST(current_step, 5)`, rồi trả
HTTP 200 `{ "draft_id": "<uuid>", "current_step": 5, "quote_invalidated": true }`.

**FR-021**
WHERE schedule ngoài range, porter ngoài 0-3, notes quá 500 ký tự, hoặc Step 2/3 chưa đầy đủ,
THE system SHALL trả HTTP 422 với mọi field sai trong `details[]`; không update một phần và
không giữ quote cũ.

---

### Nhóm 5 — Step 5 Quote Calculation & OSRM (FR-022..FR-030)

**FR-022**
WHEN Customer owner gọi `POST /api/customer/booking-drafts/{id}/quote`, THE system SHALL verify
draft có đủ vehicle, pickup, dropoff, schedule và porter data; sau đó lấy pricing config hiện
hành và snapshot:
```json
{
  "commission_rate": "0.3000",
  "peak_rate": "0.30",
  "alley_rate": "0.20",
  "vehicle_rate_per_km": 30000,
  "porter_rate_per_person": 200000
}
```

**FR-023**
WHEN cả pickup và dropoff có lat/lng hợp lệ, THE system SHALL gọi OSRM:
`GET https://router.project-osrm.org/route/v1/driving/{pickup_lng},{pickup_lat};{dropoff_lng},{dropoff_lat}?overview=false&steps=false`
với connect timeout 1 giây, read timeout 3 giây, và chỉ chấp nhận response
`code="Ok"` có `routes[0].distance > 0`.

**FR-024**
WHEN OSRM trả route hợp lệ, THE system SHALL tính:
- `distance_km = CEIL(distance_meters / 100.0) / 10.0` (làm tròn lên 0,1 km);
- `duration_minutes = CEIL(duration_seconds / 60.0)`;
- `distance_source = 'OSRM'`;
và cache kết quả theo rounded coordinates trong tối đa 24 giờ.

**FR-025**
WHERE geocoding thiếu tọa độ, OSRM timeout, OSRM HTTP non-2xx, response malformed hoặc không có
route, THE system SHALL lookup bảng `district_distance` theo cặp district không phân biệt chiều,
set `distance_source='DISTRICT_FALLBACK'`, và response phải có
`is_estimated=true`, `estimate_label="Khoảng cách ước tính theo quận"`.

**FR-026**
WHEN distance được xác định, THE system SHALL tính `base_fare` bằng BigDecimal scale=0:
```text
TRUCK_500KG: base_fare = distance_km × 20.000 VND/km
TRUCK_1T:    base_fare = distance_km × 30.000 VND/km
TRUCK_15T:   base_fare = distance_km × 40.000 VND/km
```
Kết quả mỗi component tiền được làm tròn `HALF_UP` về VND nguyên đồng.

**FR-027**
WHEN tính peak và alley surcharge, THE system SHALL:
- convert `scheduled_at` sang `Asia/Ho_Chi_Minh`;
- set `peak_surcharge = base_fare × 0.30` nếu local time nằm trong `[07:00,09:00)` hoặc
  `[17:00,19:00)`, ngược lại 0;
- set `alley_surcharge = base_fare × 0.20` nếu pickup hoặc dropoff có alley, ngược lại 0;
- không nhân chéo các surcharge.

**FR-028**
WHEN tính floor surcharge, THE system SHALL xác định effective floor cho từng điểm:
- nếu `has_elevator=true` thì effective floor = 0;
- lấy `highest_floor = MAX(pickup_effective_floor, dropoff_effective_floor)`;
- floor 0-1 → 0%;
- floor 2-3 → `base_fare × 0.20`;
- floor 4-5 → `base_fare × 0.30`;
- floor 6-30 → `base_fare × 0.50`.

**FR-029**
WHEN tính porter và total, THE system SHALL:
```text
porter_fee = porter_count × porter_rate_per_person[vehicle_type]
total_quote = base_fare + peak_surcharge + alley_surcharge + floor_surcharge + porter_fee
deposit_amount = total_quote × commission_rate_snapshot
final_payment_amount = total_quote - deposit_amount
```
Mọi amount dùng BigDecimal scale=0; `deposit_amount` làm tròn `CEILING`,
`final_payment_amount = total_quote - deposit_amount` để invariant tổng tiền luôn đúng.

**FR-030**
WHEN quote hoàn tất, THE system SHALL persist toàn bộ distance, duration, pricing components,
pricing config snapshots và `quoted_at=NOW()` vào draft, set `current_step=6`, rồi trả HTTP 200:
```json
{
  "draft_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "distance_km": "10.0",
  "duration_minutes": 28,
  "distance_source": "OSRM",
  "is_estimated": false,
  "base_fare": 300000,
  "peak_surcharge": 90000,
  "alley_surcharge": 60000,
  "floor_surcharge": 90000,
  "porter_fee": 400000,
  "total_quote": 940000,
  "deposit_amount": 282000,
  "final_payment_amount": 658000,
  "commission_rate_snapshot": "0.3000",
  "quoted_at": "2026-06-04T10:00:00Z"
}
```

---

### Nhóm 6 — Step 6 Confirm & Payment Boundary (FR-031..FR-038)

**FR-031**
WHEN Customer owner mở Step 6, THE frontend SHALL load quote snapshot và hiển thị duy nhất
payment method production `VNPAY_DEPOSIT_30_PERCENT`; các cards COD và Ví Move_home trong UI stub
phải bị ẩn hoặc disabled với label "Không khả dụng".

**FR-032**
WHEN Customer click "Xác nhận và thanh toán", THE frontend SHALL gọi
`POST /api/customer/booking-drafts/{id}/confirm` với header
`Idempotency-Key: <uuid-v4>` và body:
```json
{ "payment_method": "VNPAY_DEPOSIT_30_PERCENT" }
```

**FR-033**
WHEN confirm request hợp lệ, THE system SHALL lock draft bằng `SELECT ... FOR UPDATE`, verify:
- draft thuộc Customer;
- draft chưa confirm và chưa expire;
- quote tồn tại;
- quote không cũ hơn 30 phút;
- input data không thay đổi sau `quoted_at`;
- Idempotency-Key chưa được dùng cho payload khác.

**FR-034**
WHEN confirm transaction tạo order, THE system SHALL generate unique `order_code` theo format
`MHYYYYMMDDXXXXX`, trong đó date dùng `Asia/Ho_Chi_Minh` và `XXXXX` là sequence 5 chữ số; collision
phải retry tối đa 3 lần, sau đó trả HTTP 500 `ORDER_CODE_GENERATION_FAILED`.

**FR-035**
WHEN order code đã có, THE system SHALL insert `service_order` với:
- `customer_id` từ JWT, `driver_id=NULL`;
- toàn bộ route, schedule, notes và vehicle fields từ draft;
- toàn bộ pricing components + snapshots từ quote;
- `status='PENDING_PAYMENT'`;
- `order_code` unique;
- `deleted_at=NULL`;
và update `booking_draft.confirmed_order_id` trong cùng transaction.

**FR-036**
WHEN `service_order` được tạo ở `PENDING_PAYMENT`, THE system SHALL insert immutable audit event:
`{ event_type:'ORDER_CREATED', actor_id, actor_role:'CUSTOMER', entity_id:order_id,
from_state:null, to_state:'PENDING_PAYMENT', created_at:NOW() }` và publish domain event
`ORDER_PENDING_PAYMENT_CREATED` sau commit.

**FR-037**
WHEN domain event `ORDER_PENDING_PAYMENT_CREATED` được nhận, THE payment module SHALL tạo VNPay
deposit intent bằng `deposit_amount`, trả payment URL cho booking API, và booking API SHALL trả
HTTP 201:
```json
{
  "order_id": "a3d60328-0e73-49d8-aa9b-34e967780df0",
  "order_code": "MH2026060400001",
  "status": "PENDING_PAYMENT",
  "total_quote": 940000,
  "deposit_amount": 282000,
  "payment_url": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
  "payment_expires_at": "2026-06-04T10:15:00Z"
}
```

**FR-038**
WHERE confirm gặp duplicate Idempotency-Key, quote stale, invalid transition, payment module
không tạo được URL, hoặc DB transaction fail, THE system SHALL:
- trả lại cùng HTTP 201 response nếu key + payload đã thành công trước đó;
- trả 409 `IDEMPOTENCY_KEY_REUSED` nếu key dùng với payload khác;
- trả 409 `QUOTE_STALE` nếu quote quá 30 phút;
- trả 409 `INVALID_STATUS_TRANSITION` cho transition sai;
- rollback order nếu payment intent chưa thể tạo theo transaction/outbox strategy;
- không tạo hai orders cho cùng một confirm action.

---

### Nhóm 7 — Booking Success, Resume & Cleanup (FR-039..FR-042)

**FR-039**
WHEN frontend nhận HTTP 201 từ confirm, THE system SHALL redirect tới
`/customer/booking-success.html?order_code=<code>` và hiển thị order code, pickup, dropoff,
scheduled time, total quote, deposit amount, payment state `PENDING_PAYMENT`, CTA
"Thanh toán VNPay", "Xem chi tiết đơn", "Đặt thêm đơn".

**FR-040**
WHEN order được tạo thành công, THE system SHALL enqueue email async tiếng Việt tới Customer với
order code, route summary, scheduled time, total quote, deposit amount, payment deadline và link
thanh toán; lỗi email không rollback order hoặc payment intent.

**FR-041**
WHEN Customer owner gọi `GET /api/customer/booking-drafts/{id}`, THE system SHALL trả dữ liệu
draft theo các step đã hoàn thành, `current_step`, quote snapshot nếu có và `expires_at`; frontend
SHALL merge backend draft với localStorage theo `updated_at` mới hơn và không tin dữ liệu money
từ localStorage.

**FR-042**
WHILE system đang chạy, Scheduled Job mỗi 15 phút SHALL hard-delete `booking_draft` chưa confirm
có `expires_at < NOW()` và giữ draft đã confirm tối thiểu 7 ngày để audit/debug trước khi cleanup.
WHERE Customer gọi `DELETE /api/customer/booking-drafts/{id}`, THE system SHALL delete draft chưa
confirm của chính Customer và trả 204 idempotently.

---

## Pricing Formula Specification

### Vehicle Pricing Snapshot

| Vehicle type | Rate/km | Porter/person | Display capacity |
|--------------|---------|---------------|------------------|
| `TRUCK_500KG` | 20.000 VND | 150.000 VND | 500 kg |
| `TRUCK_1T` | 30.000 VND | 200.000 VND | 700 kg-1 tấn |
| `TRUCK_15T` | 40.000 VND | 300.000 VND | 1,5 tấn |

Rate được lấy từ `pricing_config` tại thời điểm quote và snapshot vào draft/order. Admin thay
đổi giá sau đó không ảnh hưởng quote đã confirm. Quote chưa confirm nhưng quá 30 phút phải tính
lại để nhận config mới.

### Canonical Formula

```text
base_fare = rate_per_km_snapshot × distance_km

peak_surcharge =
  IF local scheduled time in [07:00,09:00) OR [17:00,19:00)
  THEN base_fare × 0.30
  ELSE 0

alley_surcharge =
  IF pickup_has_alley OR dropoff_has_alley
  THEN base_fare × 0.20
  ELSE 0

highest_effective_floor =
  MAX(
    pickup_has_elevator ? 0 : pickup_floor,
    dropoff_has_elevator ? 0 : dropoff_floor
  )

floor_rate =
  highest_effective_floor <= 1 ? 0.00 :
  highest_effective_floor <= 3 ? 0.20 :
  highest_effective_floor <= 5 ? 0.30 :
                                 0.50

floor_surcharge = base_fare × floor_rate
porter_fee = porter_count × porter_rate_per_person_snapshot

total_quote =
  base_fare
  + peak_surcharge
  + alley_surcharge
  + floor_surcharge
  + porter_fee

deposit_amount = CEILING(total_quote × commission_rate_snapshot)
final_payment_amount = total_quote - deposit_amount
```

### Rounding Rules

1. `distance_km` dùng `NUMERIC(10,2)` nhưng OSRM route được làm tròn lên 0,1 km.
2. Mỗi money component dùng `BigDecimal` và `.setScale(0, HALF_UP)`.
3. `deposit_amount` dùng `CEILING` để công ty không nhận thiếu commission do phần lẻ.
4. `final_payment_amount` không tính độc lập bằng 70%; lấy `total_quote - deposit_amount`.
5. JSON money fields serialize thành integer, không trả string có `.00`.
6. Commission rate serialize string `"0.3000"` để giữ scale cấu hình.

### Pricing Example A — Happy Path trong CONTEXT.md

Input:

```json
{
  "vehicle_type": "TRUCK_1T",
  "distance_km": "10.0",
  "scheduled_local": "2026-06-06T08:30:00+07:00",
  "pickup_has_alley": true,
  "dropoff_has_alley": false,
  "pickup_floor": 3,
  "pickup_has_elevator": true,
  "dropoff_floor": 4,
  "dropoff_has_elevator": false,
  "porter_count": 2
}
```

Calculation:

```text
base_fare        = 10 × 30.000      = 300.000
peak_surcharge   = 300.000 × 30%    =  90.000
alley_surcharge  = 300.000 × 20%    =  60.000
floor_surcharge  = 300.000 × 30%    =  90.000
porter_fee       = 2 × 200.000      = 400.000
total_quote                           940.000
deposit_amount   = CEILING(940.000 × 30%) = 282.000
final_payment_amount                  658.000
```

### Pricing Example B — No Surcharge

```text
TRUCK_500KG, 6,4 km, 14:00, không ngõ nhỏ, tầng trệt, không porter:
base_fare        = 6,4 × 20.000      = 128.000
peak_surcharge   = 0
alley_surcharge  = 0
floor_surcharge  = 0
porter_fee       = 0
total_quote                           128.000
deposit_amount                         38.400
final_payment_amount                   89.600
```

### Pricing Example C — Highest Floor Rule

```text
TRUCK_15T, 12 km, 10:00, pickup tầng 10 có thang máy,
dropoff tầng 6 không thang máy, không alley, 1 porter:
base_fare        = 12 × 40.000       = 480.000
peak_surcharge   = 0
alley_surcharge  = 0
highest floor    = MAX(0, 6)         = 6
floor_surcharge  = 480.000 × 50%     = 240.000
porter_fee       = 1 × 300.000       = 300.000
total_quote                         1.020.000
deposit_amount                       306.000
final_payment_amount                 714.000
```

---

## API Endpoints Summary

| Method | Endpoint | Request body | Success response | Auth |
|--------|----------|--------------|------------------|------|
| POST | `/api/customer/booking-drafts` | `{ vehicle_type }` | 201 `{ draft_id, current_step, expires_at }` | Customer ACTIVE |
| GET | `/api/customer/booking-drafts/{id}` | none | 200 `{ draft_data }` | Customer owner |
| PATCH | `/api/customer/booking-drafts/{id}` | `{ step, data }` | 200 `{ updated_draft }` | Customer owner |
| POST | `/api/customer/booking-drafts/{id}/quote` | none | 200 `{ pricing_breakdown }` | Customer owner |
| POST | `/api/customer/booking-drafts/{id}/confirm` | `{ payment_method }` | 201 `{ order_id, order_code, payment_url }` | Customer owner |
| DELETE | `/api/customer/booking-drafts/{id}` | none | 204 no body | Customer owner |
| GET | `/api/customer/orders/by-code/{orderCode}` | none | 200 `{ order_summary }` | Customer owner |

Tất cả endpoints dùng `@PreAuthorize("hasRole('CUSTOMER')")`. Role khác Customer đã authenticated
nhận HTTP 403 theo HR-10. Guest nhận HTTP 401 `AUTHENTICATION_REQUIRED`.

### POST Draft Request/Response

```http
POST /api/customer/booking-drafts HTTP/1.1
Authorization: Bearer <access-token>
Content-Type: application/json

{ "vehicle_type": "TRUCK_1T" }
```

```http
HTTP/1.1 201 Created
Location: /api/customer/booking-drafts/5af5e878-52b0-4fb8-a9cb-8af517594e89
Content-Type: application/json

{
  "draft_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "vehicle_type": "TRUCK_1T",
  "current_step": 2,
  "expires_at": "2026-06-05T10:00:00Z"
}
```

### PATCH Draft Response

```json
{
  "draft_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "current_step": 4,
  "quote_invalidated": true,
  "updated_at": "2026-06-04T10:08:00Z"
}
```

### GET Resume Draft Response

```json
{
  "draft_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "vehicle_type": "TRUCK_1T",
  "pickup": {
    "district": "BA_DINH",
    "address": "Số 25 ngõ 68 phố Đội Cấn, phường Đội Cấn",
    "floor": 3,
    "has_elevator": false,
    "has_alley": true
  },
  "dropoff": {
    "district": "CAU_GIAY",
    "address": "Tòa A2, phố Duy Tân, phường Dịch Vọng Hậu",
    "floor": 5,
    "has_elevator": false,
    "has_alley": false
  },
  "scheduled_at": "2026-06-06T01:30:00Z",
  "porter_count": 2,
  "notes": "Có tủ lạnh nhỏ và khoảng 12 thùng đồ.",
  "current_step": 5,
  "quote": null,
  "expires_at": "2026-06-05T10:00:00Z",
  "updated_at": "2026-06-04T10:08:00Z"
}
```

### Confirm Headers

| Header | Required | Constraint |
|--------|----------|------------|
| `Authorization` | Yes | Bearer access JWT, Customer ACTIVE |
| `Content-Type` | Yes | `application/json` |
| `Idempotency-Key` | Yes | UUID v4, unique per logical confirm |
| `X-Request-ID` | Recommended | UUID for tracing; generated server-side if absent |

---

## Data Model

### Migration Strategy

Schema hiện tại `V5__create_service_order_table.sql` chưa đủ cho booking flow và không có
`booking_draft`, `pricing_config`, `district_distance`, `order_audit_log`, `idempotency_record`.
Implementation phải tạo Flyway migrations mới, không sửa V1-V6 đã chạy:

1. `V7__create_booking_draft.sql`
2. `V8__create_pricing_config_and_district_distance.sql`
3. `V9__extend_service_order_for_booking.sql`
4. `V10__create_order_audit_and_idempotency.sql`

### Bảng `booking_draft`

```sql
CREATE TABLE booking_draft (
    id                          UUID          NOT NULL DEFAULT gen_random_uuid(),
    customer_id                 UUID          NOT NULL REFERENCES app_user(id),

    vehicle_type                VARCHAR(20)   NOT NULL
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    current_step                INTEGER       NOT NULL DEFAULT 2
        CHECK (current_step BETWEEN 1 AND 6),

    pickup_district             VARCHAR(30),
    pickup_address              VARCHAR(200),
    pickup_floor                INTEGER       NOT NULL DEFAULT 0
        CHECK (pickup_floor BETWEEN 0 AND 30),
    pickup_has_elevator         BOOLEAN       NOT NULL DEFAULT FALSE,
    pickup_has_alley            BOOLEAN       NOT NULL DEFAULT FALSE,
    pickup_lat                  NUMERIC(10,7),
    pickup_lng                  NUMERIC(10,7),

    dropoff_district            VARCHAR(30),
    dropoff_address             VARCHAR(200),
    dropoff_floor               INTEGER       NOT NULL DEFAULT 0
        CHECK (dropoff_floor BETWEEN 0 AND 30),
    dropoff_has_elevator        BOOLEAN       NOT NULL DEFAULT FALSE,
    dropoff_has_alley           BOOLEAN       NOT NULL DEFAULT FALSE,
    dropoff_lat                 NUMERIC(10,7),
    dropoff_lng                 NUMERIC(10,7),

    scheduled_at                TIMESTAMPTZ,
    porter_count                INTEGER       NOT NULL DEFAULT 0
        CHECK (porter_count BETWEEN 0 AND 3),
    packing_assistance          BOOLEAN       NOT NULL DEFAULT FALSE,
    assembly_assistance         BOOLEAN       NOT NULL DEFAULT FALSE,
    fragile_item_protection     BOOLEAN       NOT NULL DEFAULT FALSE,
    notes                       VARCHAR(500),

    distance_km                 NUMERIC(10,2),
    duration_minutes            INTEGER,
    distance_source             VARCHAR(30)
        CHECK (distance_source IN ('OSRM', 'DISTRICT_FALLBACK')),

    rate_per_km_snapshot        NUMERIC(15,0),
    porter_rate_snapshot        NUMERIC(15,0),
    peak_rate_snapshot          NUMERIC(5,4),
    alley_rate_snapshot         NUMERIC(5,4),
    floor_rate_snapshot         NUMERIC(5,4),
    commission_rate_snapshot    NUMERIC(5,4),

    base_fare                   NUMERIC(15,0),
    peak_surcharge              NUMERIC(15,0),
    alley_surcharge             NUMERIC(15,0),
    floor_surcharge             NUMERIC(15,0),
    porter_fee                  NUMERIC(15,0),
    total_quote                 NUMERIC(15,0),
    deposit_amount              NUMERIC(15,0),
    final_payment_amount        NUMERIC(15,0),
    quoted_at                   TIMESTAMPTZ,

    confirmed_order_id          UUID REFERENCES service_order(id),
    expires_at                  TIMESTAMPTZ   NOT NULL,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_booking_draft PRIMARY KEY (id),
    CONSTRAINT ck_booking_draft_money_nonnegative CHECK (
        COALESCE(base_fare, 0) >= 0
        AND COALESCE(peak_surcharge, 0) >= 0
        AND COALESCE(alley_surcharge, 0) >= 0
        AND COALESCE(floor_surcharge, 0) >= 0
        AND COALESCE(porter_fee, 0) >= 0
        AND COALESCE(total_quote, 0) >= 0
        AND COALESCE(deposit_amount, 0) >= 0
        AND COALESCE(final_payment_amount, 0) >= 0
    )
);

CREATE INDEX idx_booking_draft_customer_updated
    ON booking_draft (customer_id, updated_at DESC);

CREATE INDEX idx_booking_draft_expiry
    ON booking_draft (expires_at)
    WHERE confirmed_order_id IS NULL;

CREATE TRIGGER trg_booking_draft_updated_at
    BEFORE UPDATE ON booking_draft
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### Bảng `pricing_config`

```sql
CREATE TABLE pricing_config (
    id                          UUID          NOT NULL DEFAULT gen_random_uuid(),
    vehicle_type                VARCHAR(20)   NOT NULL,
    rate_per_km                 NUMERIC(15,0) NOT NULL CHECK (rate_per_km > 0),
    porter_rate_per_person      NUMERIC(15,0) NOT NULL CHECK (porter_rate_per_person >= 0),
    peak_rate                   NUMERIC(5,4)  NOT NULL DEFAULT 0.3000,
    alley_rate                  NUMERIC(5,4)  NOT NULL DEFAULT 0.2000,
    commission_rate             NUMERIC(5,4)  NOT NULL DEFAULT 0.3000,
    active                      BOOLEAN       NOT NULL DEFAULT TRUE,
    effective_from              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_pricing_config PRIMARY KEY (id),
    CONSTRAINT ck_pricing_vehicle_type CHECK (
        vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')
    ),
    CONSTRAINT ck_pricing_rates CHECK (
        peak_rate BETWEEN 0 AND 1
        AND alley_rate BETWEEN 0 AND 1
        AND commission_rate BETWEEN 0 AND 1
    )
);

CREATE UNIQUE INDEX uq_pricing_config_active_vehicle
    ON pricing_config (vehicle_type)
    WHERE active = TRUE;
```

### Bảng `district_distance`

```sql
CREATE TABLE district_distance (
    origin_district       VARCHAR(30)   NOT NULL,
    destination_district  VARCHAR(30)   NOT NULL,
    distance_km           NUMERIC(10,2) NOT NULL CHECK (distance_km > 0),
    duration_minutes      INTEGER       NOT NULL CHECK (duration_minutes > 0),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_district_distance
        PRIMARY KEY (origin_district, destination_district),
    CONSTRAINT ck_district_pair_sorted
        CHECK (origin_district <= destination_district)
);
```

Application phải sort alphabetically hai district codes trước lookup để một cặp chỉ có một row.
Same-district fallback vẫn cần distance dương theo mức estimate nội quận.

### Mở rộng bảng `service_order`

```sql
ALTER TABLE service_order
    ADD COLUMN vehicle_type VARCHAR(20),
    ADD COLUMN pickup_floor INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pickup_has_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pickup_has_alley BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pickup_lat NUMERIC(10,7),
    ADD COLUMN pickup_lng NUMERIC(10,7),
    ADD COLUMN dropoff_floor INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN dropoff_has_elevator BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dropoff_has_alley BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dropoff_lat NUMERIC(10,7),
    ADD COLUMN dropoff_lng NUMERIC(10,7),
    ADD COLUMN porter_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN packing_assistance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN assembly_assistance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fragile_item_protection BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN distance_source VARCHAR(30),
    ADD COLUMN rate_per_km_snapshot NUMERIC(15,0),
    ADD COLUMN porter_rate_snapshot NUMERIC(15,0),
    ADD COLUMN peak_rate_snapshot NUMERIC(5,4),
    ADD COLUMN alley_rate_snapshot NUMERIC(5,4),
    ADD COLUMN floor_rate_snapshot NUMERIC(5,4),
    ADD COLUMN base_fare NUMERIC(15,0),
    ADD COLUMN peak_surcharge NUMERIC(15,0),
    ADD COLUMN alley_surcharge NUMERIC(15,0),
    ADD COLUMN floor_surcharge NUMERIC(15,0),
    ADD COLUMN porter_fee NUMERIC(15,0),
    ADD COLUMN deposit_amount NUMERIC(15,0),
    ADD COLUMN final_payment_amount NUMERIC(15,0);

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_vehicle_type
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    ADD CONSTRAINT ck_service_order_porter_count
        CHECK (porter_count BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_service_order_distance_source
        CHECK (distance_source IN ('OSRM', 'DISTRICT_FALLBACK'));
```

Migration phải thay status constraint hiện tại bằng state machine canonical từ `CONTEXT.md`:

```sql
ALTER TABLE service_order DROP CONSTRAINT IF EXISTS service_order_status_check;

ALTER TABLE service_order
    ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT';

ALTER TABLE service_order
    ADD CONSTRAINT ck_service_order_status CHECK (status IN (
        'PENDING_PAYMENT',
        'CONFIRMED',
        'ASSIGNED',
        'IN_PROGRESS',
        'AWAITING_FINAL_PAYMENT',
        'COMPLETED',
        'CANCELLED',
        'IN_DISPUTE'
    ));
```

Trước khi set các cột booking mới thành `NOT NULL`, migration phải backfill seed/legacy rows hoặc
chia thành migration expand → backfill → enforce. Không dùng `ddl-auto=update`.

### Bảng `order_audit_log`

```sql
CREATE TABLE order_audit_log (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    order_id      UUID         NOT NULL REFERENCES service_order(id),
    event_type    VARCHAR(50)  NOT NULL,
    actor_id      UUID         REFERENCES app_user(id),
    actor_role    VARCHAR(20),
    from_state    VARCHAR(30),
    to_state      VARCHAR(30),
    metadata      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_order_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_order_audit_order_created
    ON order_audit_log (order_id, created_at ASC);
```

### Bảng `idempotency_record`

```sql
CREATE TABLE idempotency_record (
    idempotency_key  UUID         NOT NULL,
    customer_id      UUID         NOT NULL REFERENCES app_user(id),
    operation        VARCHAR(50)  NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    response_status  INTEGER,
    response_body    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_idempotency_record PRIMARY KEY (idempotency_key, customer_id, operation)
);
```

---

## State Machine & Transaction Boundaries

### Booking Draft Lifecycle

```text
NO_DRAFT
  │ POST /booking-drafts
  ▼
DRAFT_STEP_2
  │ PATCH pickup
  ▼
DRAFT_STEP_3
  │ PATCH dropoff
  ▼
DRAFT_STEP_4
  │ PATCH details
  ▼
DRAFT_STEP_5
  │ POST quote
  ▼
DRAFT_STEP_6_QUOTED
  │ POST confirm
  ▼
CONFIRMED_DRAFT_REFERENCE
```

`booking_draft` không phải business state machine của order. Nó là working document có thể
hard-delete khi expire. Mọi update sau quote phải invalidate quote.

### Production Order Boundary

```text
(no service_order)
  │ POST confirm + create payment intent
  ▼
PENDING_PAYMENT                  ← Spec #002 kết thúc
  │ valid VNPay IPN deposit 30%  ← Spec #004
  ▼
CONFIRMED
  │ Manager assign Driver         ← Spec #006
  ▼
ASSIGNED → IN_PROGRESS → AWAITING_FINAL_PAYMENT → COMPLETED
```

### Confirm Transaction

Confirm phải bảo đảm exactly-once semantics:

```text
BEGIN
  lock booking_draft FOR UPDATE
  validate owner + expiry + quote freshness
  reserve idempotency key
  generate unique order code
  insert service_order(PENDING_PAYMENT)
  insert order_audit_log
  update booking_draft.confirmed_order_id
  insert outbox event ORDER_PENDING_PAYMENT_CREATED
COMMIT

async/outbox consumer:
  create VNPay deposit intent
  update idempotency response
```

Nếu payment intent creation synchronous và thất bại trước commit, rollback toàn bộ. Nếu dùng
outbox async, response phải thể hiện `payment_state=PENDING_URL` và frontend polling endpoint
payment; implementation phải chọn một strategy và test crash recovery.

---

## Non-Functional Requirements

**NFR-001**
Mỗi API draft/update/confirm không gọi OSRM SHALL có response time P90 dưới 1 giây và P99 dưới 2 giây ở tải 50 request/giây.

**NFR-002**
OSRM SHALL timeout sau 3 giây; district fallback SHALL trả kết quả trong 100 ms sau timeout.

**NFR-003**
Pricing SHALL deterministic: cùng input và cùng `pricing_config.version` phải cho cùng breakdown đến từng VND.

**NFR-004**
`service_order.order_code` SHALL có UNIQUE constraint; confirm đồng thời không được tạo hai mã hoặc hai order.

**NFR-005**
Email xác nhận SHALL gửi async qua outbox/Spring Mail và không làm chậm response confirm.

**NFR-006**
Draft chưa confirm SHALL expire sau 24 giờ; cron chạy mỗi 15 phút và hard-delete draft hết hạn không có `confirmed_order_id`.

---

## Acceptance Criteria

**AC1**
Customer hoàn thành sáu bước thì tạo đúng một order `PENDING_PAYMENT` với mã `MH-YYYYMMDD-XXXXX`.

**AC2**
Mọi breakdown khớp 100% công thức base + peak + alley + floor + porter và dùng số tiền nguyên VND.

**AC3**
Giờ bắt đầu trong `[07:00,09:00)` hoặc `[17:00,19:00)` áp dụng peak 30%; ngoài khoảng không áp dụng.

**AC4**
OSRM timeout thì fallback theo cặp quận trả distance hợp lệ, gắn `distance_source=FALLBACK`, sai số mục tiêu không quá 20%.

**AC5**
Refresh hoặc quay lại bước trước khôi phục đúng draft server-side; localStorage không được là nguồn giá authoritative.

**AC6**
Confirm thành công enqueue email tiếng Việt đúng địa chỉ Customer mà không block API.

**AC7**
Tạo draft, báo giá và confirm đều có audit event chứa actor, timestamp UTC và metadata tương ứng.

**AC8**
Bảy màn hình dùng Move_home forest green `#1B4D3E`, amber `#F5A623`, Be Vietnam Pro và text tiếng Việt có dấu.

**AC9**
Draft quá 24 giờ chưa confirm bị cron xóa; request tiếp theo trả HTTP 410 `DRAFT_EXPIRED`.

---

## Edge Cases & Error Handling

| ID | Tình huống | Hành vi bắt buộc |
|----|------------|------------------|
| EC-01 | JWT thiếu/hết hạn | 401 `AUTHENTICATION_REQUIRED`; không mutate dữ liệu |
| EC-02 | Customer đọc draft người khác | 403 `DRAFT_OWNERSHIP_REQUIRED`; không tiết lộ draft |
| EC-03 | Draft đã hết hạn | 410 `DRAFT_EXPIRED`; frontend đưa về Step 1 |
| EC-04 | Hai tab cùng confirm | Một request 201; request còn lại replay kết quả hoặc 409 |
| EC-05 | Cùng idempotency key nhưng body khác | 409 `IDEMPOTENCY_KEY_REUSED` |
| EC-06 | Update pickup/dropoff sau quote | Invalidate quote; confirm trả 409 `QUOTE_STALE` |
| EC-07 | OSRM timeout nhưng fallback có dữ liệu | Dùng fallback và hiển thị nhãn khoảng cách ước tính |
| EC-08 | OSRM timeout và fallback thiếu | 422 `ROUTE_UNAVAILABLE`; không sinh giá |
| EC-09 | Geocode ngoài 12 quận hỗ trợ | 422 tại field district/address |
| EC-10 | Pickup và dropoff trùng tọa độ | 422 `ROUTE_TOO_SHORT` |
| EC-11 | 06:59/09:00 hoặc 16:59/19:00 | Không tính peak; boundary xử lý theo `[start,end)` |
| EC-12 | Hai điểm đều trong ngõ | Chỉ tính một alley surcharge 20% base |
| EC-13 | Tầng cao nhưng có thang máy | Floor surcharge của điểm đó bằng 0 |
| EC-14 | VNPay intent tạm lỗi | Giữ order `PENDING_PAYMENT`; trả trạng thái retry rõ ràng |
| EC-15 | Email gửi lỗi | Confirm vẫn thành công; outbox retry và ghi lỗi vận hành |

---

## Test Cases

| ID | Input / Action | Expected Result |
|----|----------------|-----------------|
| TC-01 | 10 km, `TRUCK_1T`, peak, alley, tầng 4 không thang máy, 2 porter | Base 300.000; peak 90.000; alley 60.000; floor 60.000; porter 400.000; total 910.000 VND |
| TC-02 | 6,4 km, `TRUCK_500KG`, off-peak, không phụ phí | Base và total 128.000 VND |
| TC-03 | 12 km, `TRUCK_15T`, off-peak, tầng 6 không thang máy, 1 porter | Base 480.000; floor 240.000; porter 300.000; total 1.020.000 VND |
| TC-04 | Schedule lần lượt 06:59, 07:00, 08:59, 09:00 | Peak lần lượt false, true, true, false |
| TC-05 | Pickup và dropoff đều `has_alley=true` | Alley bằng đúng 20% base, không nhân đôi |
| TC-06 | OSRM vượt timeout 3 giây, fallback matrix có cặp quận | Quote 200 với `distance_source=FALLBACK` |
| TC-07 | PATCH draft người khác | 403 và database không đổi |
| TC-08 | Confirm hai lần cùng idempotency key | Cùng `order_id`, chỉ một `service_order` |
| TC-09 | Quote rồi thay `porter_count` trước confirm | Confirm 409 `QUOTE_STALE` |
| TC-10 | Cron gặp draft quá 24 giờ chưa confirm | Draft bị xóa; audit/metric cleanup tăng một |

---

## Frontend & Constitution Compliance

| Area | Yêu cầu |
|------|---------|
| Screens | `booking-step1-vehicle.html` đến `booking-step6-payment.html` và `booking-success.html` |
| State | `draft_id` lưu localStorage để resume; giá, owner và trạng thái luôn lấy từ backend |
| UX states | Mỗi trang data-driven có Loading, Empty/Error và nút "Thử lại" theo AC-16 |
| Brand/language | HR-19 và HR-20: CSS variables Move_home, Be Vietnam Pro, toàn bộ text user-facing có dấu |
| Security | HR-10 RBAC, HR-15 idempotency, ES-03 Bean Validation, ES-04 error format thống nhất |
| Data | HR-21 tên bảng an toàn; AC-08 BigDecimal/NUMERIC; AC-12 Flyway; AC-14 VARCHAR + CHECK |
| Workflow | HR-05 state machine; HR-13 audit log; AC-06 OSRM fallback; AC-07 UTC storage |

---

## Out of Scope (Deferred to Other Specs)

1. VNPay IPN/webhook, reconciliation và refund: Spec #004 Payment.
2. Manager dispatch, Driver workflow và tracking real-time: Spec #006.
3. Customer cancel, dispute và refund policy: Spec #007.
4. COD, Customer wallet payment, maps autocomplete và interactive route editor.

---

## Open Questions

1. Chốt geocoding provider production và quota trước Sprint 2 integration test.
2. Chốt synchronous payment URL hay outbox `PENDING_URL`; phải test crash recovery theo lựa chọn.
3. Chốt nguồn và người duyệt bảng fallback khoảng cách giữa 12 quận.
4. Chốt migration mapping cho các status legacy trước khi thay CHECK constraint.
