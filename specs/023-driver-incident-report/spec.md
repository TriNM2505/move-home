# Feature Specification: Driver Incident Report (Tài xế báo sự cố giữa chuyến)

**Feature Branch:** `023-driver-incident-report`  
**Feature Number:** #23 of 26 — CORE (money-critical + state machine)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 7 — sự cố vận chuyển + điều phối lại đơn (leader duyệt 2026-06-04)

**CONTEXT.md reference:** v2.0 §2 Order State Machine, §2 Phan cong & Dieu phoi, §2 Wallet & Commission,
§2 DamageReport (thứ tự trừ tiền tài xế)  
**Constitution reference:** v1.4.0 — HR-05, HR-08, HR-10, HR-13, HR-18, HR-20, HR-21, AC-07, AC-08,
AC-10, AC-12, AC-13, AC-14, AC-15, AC-16, ES-02, ES-03, ES-04  
**Screen reference:** `frontend/pages/manager/driver-incidents.html`,
`frontend/pages/driver/in-progress.html` — cần bổ sung vào `docs/SCREEN_INVENTORY.md` (xem DS-06)  
**Related specs:** Spec #006 Driver Workflow (nhận đơn, trạng thái chuyến); Spec #021 Customer Wallet
(đích tiền hoàn); Spec #022 Order Cancellation Refund (luồng mirror); Spec #007 Driver Financial (ví
tài xế, cọc 3 triệu); Spec #010 Manager Disputes

**Migration liên quan:** `V44__create_driver_incident_report.sql` (`driver_incident_report` +
`driver_incident_photo`)

---

## Goals

Đặc tả luồng **tài xế báo sự cố giữa chuyến** — hỏng xe hoặc lý do bất ngờ khiến không thể tiếp tục
giao hàng — và cơ chế điều phối lại đơn cho tài xế khác, kèm chính sách bồi thường khi không tìm được
người thay thế.

Khi tài xế đang ở `ACCEPTED` (trên đường đến điểm đón / đã đến nơi) hoặc `IN_PROGRESS` (đang giao) bấm
"Báo sự cố" + mô tả + tối đa 3 ảnh, hệ thống tạo `driver_incident_report` status `PENDING` và báo
Manager. Manager xác nhận → đơn được **bán lại pool** (`status = CONFIRMED`, `driver_id = NULL`) để tài
xế khác nhận, đồng thời mở **cửa sổ 15 phút**. Nếu có tài xế nhận lại trong cửa sổ → sự cố tự đóng
(`RESOLVED_REASSIGNED`). Nếu quá 15 phút không ai nhận → Manager bấm "Hoàn cọc + bồi thường":
khách nhận **cọc 30% + 200.000 đ** vào ví, tài xế gây sự cố **bị trừ 200.000 đ** (cọc → ví →
SUSPENDED), đơn về `CANCELLED` (COMPANY).

**Chính sách phân bổ thiệt hại (quyết định leader 2026-06-04):** công ty chịu phần cọc hoàn cho khách;
tài xế chỉ chịu phần bồi thường 200.000 đ. Đây là chính sách **mới**, chưa có trong CONTEXT gốc.

Spec định nghĩa điều kiện kích hoạt, state machine kép (sự cố × đơn hàng), công thức tiền, thứ tự trừ
tiền tài xế, transaction boundaries, RBAC, audit trail và frontend contract. Tiền dùng `BigDecimal`
scale=0 (AC-08), ví không âm (HR-18), mọi thay đổi số dư kèm bút toán trong cùng transaction (AC-13).

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Trạng thái đồng bộ tài liệu

| Nguồn | Nội dung | Trạng thái |
|-------|----------|-----------|
| `CONTEXT.md` §2 Order State Machine | **Không có** transition `ACCEPTED → CONFIRMED` hay `IN_PROGRESS → CONFIRMED` do sự cố tài xế | ❌ **Thiếu** |
| `CONTEXT.md` §2 State Machine row `ASSIGNED → ASSIGNED` DRIVER | Chỉ có "Driver tu choi → reset, Manager phan Driver khac" — tức từ chối **trước khi** đi, không phải hỏng xe **giữa chuyến** | ⚠️ Khác luồng |
| `CONTEXT.md` §2 DisputeReport | "Driver tao tai cho khi khach khong chiu tra 70%" — luồng khác hẳn | ⚠️ Không áp dụng |
| Constitution | **Không có** HR/AC nào về sự cố vận chuyển | ❌ **Thiếu** |
| Chính sách bồi thường 200.000 đ | **Không có** trong CONTEXT (chỉ có DamageReport "Driver bồi thường 100%") | ❌ **Thiếu** |
| Quyết định leader 2026-06-04 | Chốt: cửa sổ 15 phút, bồi thường 200.000 đ, công ty chịu cọc | ✅ Nguồn của spec này |

> **Khoảng trống tài liệu:** Khác Spec #022 (được HR-14 v1.4.0 + CONTEXT đồng bộ trước khi spec ra đời),
> luồng sự cố này **chưa được ghi vào bất kỳ tài liệu cấp trên nào**. Spec này là nơi đầu tiên đặc tả
> nó, và vì vậy phải kéo theo việc cập nhật CONTEXT §2 State Machine (3 transition mới) + chính sách
> bồi thường — xem OQ-1 và DS-02. **Không triển khai trước khi OQ-1 được chốt.**

### Khác biệt với các luồng "trừ tiền tài xế" đã có

| | **DamageReport** (CONTEXT §2) | **Driver Incident** (spec này) |
|---|---|---|
| Ai báo | Customer | **Driver** |
| Khi nào | Trong 2h escrow sau `COMPLETED` | Giữa chuyến (`ACCEPTED`/`IN_PROGRESS`) |
| Trạng thái đơn | `COMPLETED` → `IN_DISPUTE` | `ACCEPTED`/`IN_PROGRESS` → `CONFIRMED` → `CANCELLED` |
| Tài xế chịu | **100%** giá trị hư hỏng | **Cố định 200.000 đ** |
| Công ty chịu | 0 | **Phần cọc 30%** hoàn cho khách |
| Thứ tự trừ | Cọc → ví → SUSPENDED | Cọc → ví → SUSPENDED (**giống**) |
| Đích tiền khách | Không có ví (CONTEXT gốc) | `customer_wallet` |

### Quyết định canonical

| Chủ đề | Canonical | Nguồn |
|--------|-----------|-------|
| Trạng thái được báo sự cố | `ACCEPTED`, `IN_PROGRESS` — tức trước khi khách trả 70% | Leader 2026-06-04 |
| Cửa sổ chờ tài xế mới | **15 phút** — cấu hình `app.incident.reassign-minutes` | Leader 2026-06-04 |
| Mức bồi thường | **200.000 đ** cố định — cấu hình `app.incident.compensation` | Leader 2026-06-04 |
| Khách nhận | `FLOOR(cọc 30%) + 200.000` vào `customer_wallet` | Leader 2026-06-04 |
| Tài xế mất | **Chỉ 200.000 đ** — không phải toàn bộ thiệt hại | Leader 2026-06-04 |
| Công ty chịu | Phần cọc 30% hoàn cho khách | Hệ quả của công thức trên |
| Người xử lý | **MANAGER** — không phải Admin | CONTEXT §3 RBAC (điều phối vận hành) |

**Lý do phân bổ thiệt hại như trên:** hỏng xe giữa chuyến thường là rủi ro khách quan, không phải lỗi
cố ý của tài xế. Bắt tài xế gánh toàn bộ (như DamageReport 100%) sẽ khiến tài xế giấu sự cố và bỏ đơn
im lặng — tệ hơn nhiều cho khách. Mức phạt cố định 200.000 đ đủ để răn đe việc lạm dụng nhưng không
khiến tài xế phá sản vì một lần hỏng xe; phần cọc công ty chịu được coi là chi phí vận hành của mô hình
marketplace.

---

## Scope Summary

**In scope:**

1. `POST /api/driver/orders/{id}/incident` — tài xế báo sự cố, trả `incidentId`.
2. `POST /api/driver/orders/{id}/incident/photos` — đính kèm ảnh (tối đa 3).
3. `GET /api/manager/incidents` — hàng đợi Manager, filter status, cờ `overdue`.
4. `GET /api/manager/incidents/{id}` — chi tiết + ảnh signed URL.
5. `POST /api/manager/incidents/{id}/confirm` — xác nhận, bán đơn lại pool, mở cửa sổ 15 phút.
6. `POST /api/manager/incidents/{id}/compensate` — hoàn cọc + bồi thường, phạt tài xế, huỷ đơn.
7. Tự đóng sự cố khi tài xế khác nhận lại (`resolveReassigned` hook trong `acceptOrder`).
8. Thứ tự trừ tiền phạt tài xế: cọc → ví → SUSPENDED.
9. Money invariants, transaction boundaries, audit trail, notification.
10. Loading/Empty/Error states cho màn Manager.

**Out of scope:**

1. Ví khách hàng — Spec #021; spec này chỉ mô tả **bút toán cộng ví**.
2. Ví tài xế / cọc 3 triệu / nạp lại cọc — Spec #007.
3. Luồng tài xế nhận đơn (`acceptOrder`) — Spec #006; spec này chỉ mô tả **hook** `resolveReassigned`.
4. DamageReport (khách báo hư hỏng sau COMPLETED) — Spec #010.
5. DisputeReport (khách không trả 70%) — Spec #010.
6. Tự động compensate bằng scheduled job — bản này Manager bấm tay (DS-01).
7. Khôi phục tài khoản tài xế sau SUSPENDED — Spec #007/#012.
8. Tài xế xem lịch sử sự cố của mình — **chưa có endpoint** (DS-04).

---

## User Stories

**P1:**

**US1:** Là Driver, khi xe hỏng giữa chuyến tôi bấm "Báo sự cố" + mô tả để không bị phạt oan vì bỏ đơn.

**US2:** Là Driver, tôi đính kèm ảnh (xe hỏng, hiện trường) làm bằng chứng cho Manager.

**US3:** Là Manager, tôi xem hàng đợi sự cố chờ xác nhận theo FIFO để xử lý nhanh nhất có thể.

**US4:** Là Manager, tôi xác nhận sự cố để đơn quay lại pool cho tài xế khác nhận, giữ khách hàng.

**US5:** Là Customer, tôi nhận thông báo rõ ràng rằng đơn đang tìm tài xế mới và biết chính sách nếu
sau 15 phút không có ai nhận.

**US6:** Là Manager, khi quá 15 phút không ai nhận, tôi bấm một nút để hoàn cọc + bồi thường cho khách
và phạt tài xế gây sự cố.

**P2:**

**US7:** Là Driver báo sự cố, tôi được thông báo khi đơn đã có tài xế mới để yên tâm nhận đơn khác.

**US8:** Là Manager, tôi thấy cờ "quá hạn" trên hàng đợi để biết sự cố nào đã đủ điều kiện bồi thường.

**US9:** Là Driver, tôi được thông báo rõ số tiền bị trừ và lý do khi bị phạt.

**US10:** Là Driver, khi bị trừ hết cọc và ví, tôi nhận thông báo tài khoản bị khoá kèm số tiền cần nạp
lại.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **56 FR**, trong đó **26 FR có mệnh đề WHERE** (46.4% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Tài xế báo sự cố (FR-001..FR-009)

**FR-001**  
WHEN Driver gọi `POST /api/driver/orders/{id}/incident` với `reason` hợp lệ và đơn ở trạng thái cho
phép, THE system SHALL tạo `driver_incident_report` status `PENDING` và trả HTTP 200 với
`{"incidentId": UUID}` để frontend upload ảnh tiếp theo.

**FR-002**  
WHILE đơn ở `ACCEPTED` (trên đường / đã đến điểm đón) hoặc `IN_PROGRESS` (đang giao), THE system SHALL
cho phép báo sự cố — tức **trước khi** khách trả 70%.

**FR-003**  
WHERE đơn ở trạng thái khác (ví dụ `AWAITING_FINAL_PAYMENT`, `COMPLETED`, `CONFIRMED`), THE system SHALL
trả HTTP 409 `INVALID_STATE` "Chỉ báo sự cố khi đang trên đường đến điểm đón hoặc đang giao hàng."
(HR-05).

**FR-004**  
WHERE `order.driver_id` khác JWT subject, THE system SHALL throw `AccessDeniedException` "Bạn chỉ có
thể báo sự cố cho đơn của chính mình." (HR-10).

**FR-005**  
WHERE đơn không tồn tại hoặc `deleted_at != NULL`, THE system SHALL trả HTTP 404 `ORDER_NOT_FOUND`
"Không tìm thấy đơn hàng."

**FR-006**  
WHERE `reason` là `null` hoặc rỗng, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Vui lòng mô tả
sự cố."; WHERE `reason` sau trim dài hơn 500 ký tự, SHALL trả 422 "Mô tả sự cố không được vượt quá 500
ký tự."

**FR-007**  
WHERE đơn đã có sự cố **đang mở** (status `PENDING` hoặc `CONFIRMED`), THE system SHALL trả HTTP 409
`INCIDENT_ALREADY_OPEN` "Đơn này đã có báo cáo sự cố đang được xử lý."

**FR-008**  
WHEN tạo báo cáo, THE system SHALL snapshot `order_status_snapshot` = trạng thái đơn **tại thời điểm
báo** (`ACCEPTED` hoặc `IN_PROGRESS`) để phục vụ audit; DB CHECK SHALL chỉ chấp nhận hai giá trị này.

**FR-009**  
WHEN báo cáo được tạo, THE system SHALL tạo notification `DRIVER_INCIDENT_REPORTED` cho **mọi Manager
ACTIVE** và log dòng `driver_incident_reported actor_id=... actor_role=DRIVER order_id=... order_code=...
order_status=... incident_id=...` (HR-13); WHERE notification lỗi, SHALL log warning và vẫn trả 200.

---

### Nhóm 2 — Đính kèm ảnh (FR-010..FR-016)

**FR-010**  
WHEN Driver gọi `POST /api/driver/orders/{id}/incident/photos` với multipart `file`, THE system SHALL
validate, upload Cloudinary signed upload server-side, lưu `driver_incident_photo` và trả HTTP 200
(AC-10).

**FR-011**  
WHERE không tìm thấy sự cố `PENDING` của đơn đó thuộc chính tài xế đó, THE system SHALL trả HTTP 404 —
đây đồng thời là ownership check (HR-10).

**FR-012**  
WHERE sự cố đã có **3 ảnh**, THE system SHALL trả HTTP 422 và SHALL không gọi Cloudinary (tiết kiệm
quota free tier).

**FR-013**  
WHERE file rỗng hoặc `size > 1.5 MB`, THE system SHALL trả HTTP 422 `INVALID_FILE`.

**FR-014**  
WHERE nội dung file không khớp magic number JPEG/PNG/WebP, THE system SHALL trả 422 `INVALID_FILE` và
SHALL không gọi Cloudinary (AC-10).

**FR-015**  
WHEN upload, THE system SHALL dùng `resource_type = "image"`, `type = "authenticated"`; SHALL lưu cả
`secure_url` và `public_id`.

**FR-016**  
WHEN Manager xem chi tiết, THE system SHALL sinh **signed URL** cho từng ảnh; ảnh SHALL không truy cập
được bằng URL công khai (AC-10).

---

### Nhóm 3 — Manager xem hàng đợi (FR-017..FR-023)

**FR-017**  
WHEN Manager gọi `GET /api/manager/incidents?status={s}&page={p}&size={z}`, THE system SHALL trả
`Page<IncidentListItem>`.

**FR-018**  
WHILE `status` được truyền, THE system SHALL sắp xếp `created_at` **ASC** (FIFO); WHILE `status` là
`null`/rỗng/`"ALL"`, SHALL trả tất cả sắp xếp `created_at` **DESC**.

**FR-019**  
WHERE `status` không thuộc `{PENDING, CONFIRMED, RESOLVED_REASSIGNED, COMPENSATED}`, THE system SHALL
trả HTTP 422 `VALIDATION_ERROR` "Trạng thái sự cố không hợp lệ."

**FR-020**  
WHEN trả mỗi item, THE system SHALL bao gồm `id`, `orderId`, `orderCode`, `driverId`, `driverName`,
`reason`, `status`, `orderStatusSnapshot`, `reassignDeadline`, `overdue`, `createdAt`, `confirmedAt`,
`refundAmount`, `penaltyAmount`.

**FR-021**  
WHEN tính cờ `overdue`, THE system SHALL trả `true` khi và chỉ khi `status = CONFIRMED`
**và** `reassign_deadline != NULL` **và** `NOW() > reassign_deadline` — cờ này cho Manager biết sự cố
đã đủ điều kiện bồi thường.

**FR-022**  
WHERE `page < 0`, THE system SHALL trả 422 "Số trang không hợp lệ."; WHERE `size <= 0` hoặc `size > 100`,
SHALL trả 422 "Kích thước trang phải từ 1 đến 100." Default `size` = 20 (AC-15).

**FR-023**  
WHEN Manager gọi `GET /api/manager/incidents/{id}`, THE system SHALL trả `IncidentDetailResponse` gồm
thông tin sự cố, tài xế (tên + SĐT), khách (tên + SĐT), `depositAmount`, `compensationAmount`,
`photoUrls` (signed) và `OrderSummary`; WHERE không tìm thấy, SHALL trả 404 `INCIDENT_NOT_FOUND`
"Không tìm thấy sự cố vận chuyển."

---

### Nhóm 4 — Manager xác nhận sự cố (FR-024..FR-033)

**FR-024**  
WHEN Manager gọi `POST /api/manager/incidents/{id}/confirm`, THE system SHALL: set `order.driver_id = NULL`,
`order.arrived_at = NULL`, `order.started_at = NULL`, `order.status = CONFIRMED`; set sự cố `CONFIRMED`
+ `confirmed_by` + `confirmed_at` + `reassign_deadline = NOW() + 15 phút` — tất cả trong một transaction.

**FR-025**  
WHERE sự cố không ở `PENDING`, THE system SHALL trả HTTP 409 `INCIDENT_ALREADY_PROCESSED` "Sự cố đã
được xử lý." (HR-05).

**FR-026**  
WHERE `order.driver_id` khác `report.driver_id`, **hoặc** đơn không còn ở `ACCEPTED`/`IN_PROGRESS`,
THE system SHALL trả HTTP 409 `ORDER_STATE_CHANGED` "Đơn đã đổi trạng thái, không thể xác nhận sự cố."
— chống thao tác tranh chấp khi đơn đã bị xử lý bởi luồng khác.

**FR-027**  
WHERE không tìm thấy đơn hoặc đơn `deleted_at != NULL`, THE system SHALL trả 404 `ORDER_NOT_FOUND`.

**FR-028**  
WHEN load sự cố và đơn để xử lý, THE system SHALL dùng `findByIdForUpdate` (pessimistic lock) cho cả hai
— hai Manager bấm xác nhận đồng thời chỉ một người thắng (HR-08).

**FR-029**  
WHEN reset đơn về pool, THE system SHALL set trạng thái **trực tiếp** (`order.setStatus(CONFIRMED)`)
thay vì publish `OrderStatusChangedEvent`, để tránh gửi notification "đơn đã xác nhận" gây hiểu nhầm cho
khách — khách đã có thông báo riêng (FR-031).

**FR-030**  
WHEN xác nhận thành công, THE system SHALL ghi `AuditLog` action `INCIDENT_CONFIRMED`, `entityType` =
`DRIVER_INCIDENT`, detail JSON `{"order_code":"...","reassign_deadline":"..."}` (HR-13).

**FR-031**  
WHEN xác nhận thành công, THE system SHALL tạo notification `ORDER_REASSIGNING` cho Customer với nội
dung xin lỗi, giải thích đang tìm tài xế mới, và nêu rõ chính sách "nếu sau 15 phút không có tài xế nào
nhận, chúng tôi sẽ hoàn cọc và bồi thường theo chính sách".

**FR-032**  
WHEN xác nhận thành công, THE system SHALL tạo notification `DRIVER_INCIDENT_REPORTED` cho tài xế báo
sự cố với nội dung "Sự cố đã được xác nhận... Bạn có thể nhận đơn mới."

**FR-033**  
WHEN xác nhận thành công, THE system SHALL trả `IncidentDetailResponse` mới nhất (gọi lại `detail()`).

---

### Nhóm 5 — Tài xế khác nhận lại đơn (FR-034..FR-037)

**FR-034**  
WHEN một Driver khác gọi `acceptOrder` cho đơn đang ở pool, THE system SHALL gọi
`DriverIncidentService.resolveReassigned(orderId)` **trong cùng transaction** của `acceptOrder`
(`Propagation.MANDATORY`).

**FR-035**  
WHEN tồn tại sự cố `CONFIRMED` của đơn đó, THE system SHALL chuyển sự cố sang `RESOLVED_REASSIGNED`, tạo
notification cho tài xế báo sự cố ("Đơn đã có tài xế mới"), và log `incident_resolved_reassigned`.

**FR-036**  
WHERE không tồn tại sự cố `CONFIRMED` nào cho đơn (đơn bình thường, chưa từng có sự cố), THE system
SHALL **bỏ qua im lặng** — idempotent, không throw.

**FR-037**  
WHILE đơn đang ở pool sau sự cố, THE system SHALL áp dụng cùng điều kiện nhận đơn như đơn thường: tài xế
đang có đơn chưa hoàn thành SHALL không nhận thêm (Spec #006).

---

### Nhóm 6 — Manager hoàn cọc + bồi thường (FR-038..FR-050)

**FR-038**  
WHEN Manager gọi `POST /api/manager/incidents/{id}/compensate` và mọi điều kiện thoả, THE system SHALL:
(1) cộng `deposit + 200.000` vào `customer_wallet`, (2) trừ `200.000` từ tài xế gây sự cố, (3) đưa đơn
về `CANCELLED`, (4) set sự cố `COMPENSATED` — tất cả trong một transaction DB (AC-13).

**FR-039**  
WHERE sự cố không ở `CONFIRMED`, THE system SHALL trả HTTP 409 `INCIDENT_NOT_CONFIRMED` "Chỉ hoàn cọc +
bồi thường cho sự cố đã xác nhận."

**FR-040**  
WHERE `reassign_deadline` là `NULL` **hoặc** `NOW() < reassign_deadline`, THE system SHALL trả HTTP 409
`REASSIGN_WINDOW_NOT_OVER` "Chưa quá 15 phút chờ tài xế mới, chưa thể hoàn cọc." — Manager không được
bồi thường sớm.

**FR-041**  
WHERE đơn không còn ở `CONFIRMED` **hoặc** `order.driver_id != NULL` (đã có tài xế nhận lại), THE system
SHALL trả HTTP 409 `ORDER_ALREADY_REASSIGNED` "Đơn đã có tài xế nhận lại, không thể hoàn cọc."

**FR-042**  
WHEN tính tiền hoàn khách, THE system SHALL dùng
`refundToCustomer = FLOOR(total_quote × commission_rate_snapshot) + compensationAmount`, scale=0
(AC-08); WHERE `commission_rate_snapshot` là `NULL`, SHALL dùng default `0.3000`.

**FR-043**  
WHEN cộng ví khách, THE system SHALL gọi `CustomerRefundService.refundForCancellation()` ghi **một** bút
toán `REFUND` dương với description "Hoàn cọc + bồi thường sự cố vận chuyển đơn {orderCode}".

**FR-044**  
WHEN trừ tiền phạt tài xế, THE system SHALL trừ theo **thứ tự**: (1) cọc `driver_profile.deposit_amount`,
(2) ví `driver_wallet.balance`, (3) SUSPENDED nếu vẫn thiếu — nhất quán CONTEXT §DamageReport.

**FR-045**  
WHILE trừ từng nguồn, THE system SHALL chỉ trừ `min(số dư nguồn, số còn lại)` và ghi **một bút toán
`DAMAGE_DEDUCTION` âm riêng** cho mỗi nguồn (description "(trừ cọc)" / "(trừ ví)"); số dư SHALL không
bao giờ âm (HR-18).

**FR-046**  
WHERE tổng cọc + ví tài xế **không đủ** 200.000, THE system SHALL: trừ tối đa có thể, set
`user.status = SUSPENDED`, lưu `suspension_previous_status`, `suspended_at`, và `suspension_reason` chứa
số tiền cần nạp lại để đủ cọc 3.000.000 và số còn nợ.

**FR-047**  
WHERE tài xế **đã** ở `SUSPENDED` hoặc không tìm thấy user, THE system SHALL bỏ qua bước suspend (không
ghi đè `suspension_previous_status` đã có).

**FR-048**  
WHEN huỷ đơn, THE system SHALL set `status = CANCELLED`, `cancelled_at = NOW()`, và
`cancellation_reason` = "Sự cố vận chuyển: không tìm được tài xế thay thế trong {N} phút
(cancelled_by=COMPANY)."

**FR-049**  
WHEN bồi thường thành công, THE system SHALL ghi `AuditLog` action `INCIDENT_COMPENSATED` với detail
JSON `{"order_code":"...","refund":...,"penalty_collected":...}`; SHALL lưu `refund_amount` =
`refundToCustomer` và `penalty_amount` = **số tiền thực thu được** (có thể < 200.000 nếu tài xế không
đủ tiền).

**FR-050**  
WHEN bồi thường thành công, THE system SHALL tạo notification `ORDER_INCIDENT_REFUNDED` cho Customer
(nêu tổng tiền + phần bồi thường) và `PENALTY_WALLET_DEDUCTED` cho Driver (nêu số tiền bị trừ);
WHERE tài xế bị khoá, SHALL thêm notification `PENALTY_ACCOUNT_LOCKED`.

---

### Nhóm 7 — RBAC & Data Integrity (FR-051..FR-056)

**FR-051**  
WHILE mọi endpoint `/api/driver/orders/{id}/incident**` chạy, THE system SHALL enforce
`@PreAuthorize("hasRole('DRIVER')")` + ownership qua `order.driver_id`.

**FR-052**  
WHILE mọi endpoint `/api/manager/incidents/**` chạy, THE system SHALL enforce
`@PreAuthorize("hasRole('MANAGER')")`; WHERE role khác (kể cả ADMIN), SHALL trả HTTP 403 (HR-10).

**FR-053**  
WHILE bảng tồn tại, THE system SHALL enforce partial unique index `uq_driver_incident_open_per_order`
— tối đa **một** sự cố đang mở (`PENDING`/`CONFIRMED`) trên một đơn tại một thời điểm.

**FR-054**  
WHILE một đơn đi qua vòng đời, THE system SHALL cho phép **nhiều** sự cố lần lượt (tài xế mới nhận lại
rồi lại hỏng xe) — vì unique index chỉ chặn sự cố **đang mở**.

**FR-055**  
WHERE DB CHECK `order_status_snapshot IN ('ACCEPTED','IN_PROGRESS')` hoặc
`status IN ('PENDING','CONFIRMED','RESOLVED_REASSIGNED','COMPENSATED')` bị vi phạm, THE system SHALL để
DB reject transaction (AC-14).

**FR-056**  
WHILE cấu hình vắng mặt trong `application.properties`, THE system SHALL dùng default
`app.incident.reassign-minutes = 15` và `app.incident.compensation = 200000`.

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `POST /incident` p95 | < 800 ms |
| NFR-002 | `GET /api/manager/incidents` p95 | < 1000 ms |
| NFR-003 | `GET /{id}` (kèm ký signed URL) p95 | < 1000 ms |
| NFR-004 | `POST /{id}/confirm` p95 | < 1000 ms |
| NFR-005 | `POST /{id}/compensate` p95 | < 1500 ms (3 bút toán + suspend) |
| NFR-006 | Upload ảnh (1.5 MB) p95 | < 3000 ms |
| NFR-007 | Pagination | Default 20, max 100 (AC-15) |
| NFR-008 | Money precision | FLOOR scale=0, `NUMERIC(15,0)` (AC-08) |
| NFR-009 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-010 | Empty/Loading/Error states | Bắt buộc màn Manager (AC-16) |
| NFR-011 | Vietnamese diacritics | 100% text user-facing (HR-20) |
| NFR-012 | Concurrency | 2 Manager confirm/compensate đồng thời → chỉ 1 thành công |
| NFR-013 | Cửa sổ reassign | Cấu hình được để demo hạ xuống (vd 1 phút) |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| POST | `/api/driver/orders/{id}/incident` | DRIVER | `{reason}` | 200 `{incidentId}` | Bước 1 |
| POST | `/api/driver/orders/{id}/incident/photos` | DRIVER | multipart `file` | 200 | Bước 2, tối đa 3 |
| GET | `/api/manager/incidents` | MANAGER | `status`, `page`, `size` | 200 `Page<IncidentListItem>` | Có cờ `overdue` |
| GET | `/api/manager/incidents/{id}` | MANAGER | — | 200 `IncidentDetailResponse` | Kèm signed URLs |
| POST | `/api/manager/incidents/{id}/confirm` | MANAGER | — | 200 `IncidentDetailResponse` | Bán đơn lại pool |
| POST | `/api/manager/incidents/{id}/compensate` | MANAGER | — | 200 `IncidentDetailResponse` | Hoàn + phạt + huỷ |

### Standard Error (ES-04)

```json
{
  "error_code": "REASSIGN_WINDOW_NOT_OVER",
  "message": "Chưa quá 15 phút chờ tài xế mới, chưa thể hoàn cọc.",
  "details": []
}
```

---

## Data Model

### Schema Design

Luồng sự cố cần 2 bảng mới, gộp trong **một** migration (yêu cầu + ảnh bằng chứng luôn đi cùng nhau):

| Migration | Nội dung |
|-----------|----------|
| `V44__create_driver_incident_report.sql` | `driver_incident_report`, `driver_incident_photo`, 4 indexes, trigger `updated_at`, CHECK constraints |

Cấu trúc **mirror `V41`** (Spec #022) để hai luồng "yêu cầu + ảnh + Manager duyệt" nhất quán. Không tạo
bảng ledger riêng — bút toán `REFUND` và `DAMAGE_DEDUCTION` ghi vào `transaction` dùng chung.

Index `idx_driver_incident_confirmed` `(reassign_deadline ASC)` partial `WHERE status='CONFIRMED'` được
thiết kế sẵn cho scheduled job quét quá hạn (DS-01) — dù bản 1.0.0 chưa dùng tới.

### Table `driver_incident_report` (V44)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `order_id` | `UUID` NOT NULL → `service_order(id)` | |
| `driver_id` | `UUID` NOT NULL → `app_user(id)` | Tài xế gây sự cố |
| `reason` | `VARCHAR(500)` NOT NULL | Mô tả tài xế nhập |
| `order_status_snapshot` | `VARCHAR(30)` NOT NULL | `CHECK IN ('ACCEPTED','IN_PROGRESS')` |
| `status` | `VARCHAR(30)` NOT NULL DEFAULT `'PENDING'` | `CHECK IN ('PENDING','CONFIRMED','RESOLVED_REASSIGNED','COMPENSATED')` — AC-14 |
| `reassign_deadline` | `TIMESTAMPTZ` | Set khi CONFIRMED; NULL khi PENDING |
| `confirmed_by` / `confirmed_at` | `UUID` → `app_user(id)` / `TIMESTAMPTZ` | Manager xác nhận |
| `refund_amount` | `NUMERIC(15,0)` | `CHECK (NULL OR >= 0)`; = cọc + 200k khi COMPENSATED |
| `penalty_amount` | `NUMERIC(15,0)` | `CHECK (NULL OR >= 0)`; = số **thực thu** từ tài xế |
| `compensated_by` / `compensated_at` | `UUID` / `TIMESTAMPTZ` | |
| `created_at` / `updated_at` | `TIMESTAMPTZ` NOT NULL | Trigger `trg_driver_incident_report_updated_at` |

**Indexes:**
- `uq_driver_incident_open_per_order` — **partial UNIQUE** `WHERE status IN ('PENDING','CONFIRMED')`
- `idx_driver_incident_pending` — partial `WHERE status = 'PENDING'`, `(created_at ASC, id ASC)` (FIFO)
- `idx_driver_incident_confirmed` — partial `WHERE status = 'CONFIRMED'`, `(reassign_deadline ASC, id ASC)`
- `idx_driver_incident_driver` — `(driver_id, created_at DESC, id DESC)`

> ⚠️ **Ràng buộc còn thiếu:** thiết kế này chưa có CHECK ràng buộc field theo status (khác V41 có
> `ck_order_cancellation_refund_terminal`). Xem DS-03.

### Table `driver_incident_photo` (V44)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `incident_id` | `UUID` NOT NULL → `driver_incident_report(id)` | |
| `url` | `VARCHAR(500)` NOT NULL | Cloudinary `secure_url` |
| `public_id` | `VARCHAR(255)` NOT NULL | Để ký URL |
| `uploaded_by_user_id` | `UUID` → `app_user(id)` | |
| `uploaded_at` | `TIMESTAMPTZ` NOT NULL | |

**Index:** `idx_driver_incident_photo_incident` — `(incident_id, uploaded_at)`

### Bảng liên quan (không thuộc spec này)

| Bảng | Vai trò | Spec |
|------|---------|------|
| `customer_wallet` | Nhận cọc + bồi thường | #021 |
| `driver_wallet` | Bị trừ phạt | #007 |
| `driver_profile.deposit_amount` | Bị trừ phạt trước | #007 |
| `transaction` | Bút toán `REFUND` + `DAMAGE_DEDUCTION` | #013 |
| `app_user.status` | SUSPENDED khi không đủ tiền | #012 |
| `service_order` | Nguồn `total_quote`, `commission_rate_snapshot` | #002 |

### Cấu hình

| Property | Default | Ý nghĩa |
|----------|---------|---------|
| `app.incident.reassign-minutes` | `15` | Cửa sổ chờ tài xế mới |
| `app.incident.compensation` | `200000` | Mức bồi thường cố định (VND) |

> ⚠️ Cả hai **chưa được khai báo** trong `application.properties` — đang chạy bằng default trong
> `@Value`. Xem DS-05.

---

## Money Invariants

| ID | Invariant | Enforce ở đâu |
|----|-----------|---------------|
| MI-001 | `refund_amount = FLOOR(total × rate) + compensation` | FR-042 |
| MI-002 | Khách nhận đúng **một** bút toán `REFUND` dương | FR-043 |
| MI-003 | Tài xế bị trừ tối đa `compensation` (200k), không hơn | FR-044/FR-045 |
| MI-004 | `penalty_amount` = **số thực thu**, có thể < 200k | FR-049 |
| MI-005 | Công ty chịu phần cọc — không thu lại từ tài xế | Suy ra từ công thức |
| MI-006 | Mỗi lần trừ nguồn nào ghi **một** bút toán `DAMAGE_DEDUCTION` âm riêng | FR-045 |
| MI-007 | `driver_wallet.balance >= 0` và `deposit_amount >= 0` | `min()` + HR-18 |
| MI-008 | `customer_wallet.balance >= 0` | DB CHECK (chỉ cộng) |
| MI-009 | Cộng ví + trừ tài xế + huỷ đơn trong **cùng** TX | FR-038 (AC-13) |
| MI-010 | Sự cố `RESOLVED_REASSIGNED` ⇒ **không** có bút toán nào | State machine |

### Ví dụ phân bổ tiền

```
Đơn: total_quote = 940.000 đ, rate = 0.3000
deposit       = FLOOR(940000 × 0.3)  = 282.000 đ
compensation  = 200.000 đ (cấu hình)
─────────────────────────────────────────────
Khách nhận    = 282.000 + 200.000    = 482.000 đ  → customer_wallet (REFUND +482000)
Tài xế mất    = 200.000 đ                          → cọc/ví (DAMAGE_DEDUCTION -200000)
Công ty chịu  = 282.000 đ                          (phần cọc — không thu lại)
```

**Trường hợp tài xế không đủ tiền:**

```
Tài xế: deposit_amount = 150.000, wallet.balance = 30.000
penalty = 200.000
├─ Trừ cọc:  min(150.000, 200.000) = 150.000  → DAMAGE_DEDUCTION -150000 (trừ cọc)
│  còn lại: 50.000
├─ Trừ ví:   min(30.000, 50.000)   =  30.000  → DAMAGE_DEDUCTION -30000 (trừ ví)
│  còn lại: 20.000
└─ Vẫn thiếu 20.000 → SUSPENDED
   suspension_reason: "Thiếu tiền bồi thường... Cần nạp 2.850.000 VND để khôi phục đủ tiền cọc
   3.000.000 VND. Còn nợ 20.000 VND tiền bồi thường chưa thu được."

penalty_amount lưu = 150.000 + 30.000 = 180.000  (số thực thu, KHÔNG phải 200.000)
Khách VẪN nhận đủ 482.000 → công ty chịu thêm 20.000 thiếu hụt.
```

---

## Transaction Boundaries

### Tài xế báo sự cố

```
BEGIN  -- DriverIncidentService.reportIncident @Transactional
  order = findByIdForUpdate(orderId), filter deletedAt == null   -- 404 nếu không thấy
  assert order.driverId == driverId          -- AccessDeniedException (HR-10)
  assert order.status ∈ {ACCEPTED, IN_PROGRESS}   -- 409 INVALID_STATE
  reason = validateReason(reason)                  -- 422 nếu rỗng/> 500
  assert !existsByOrderIdAndStatusIn(orderId, {PENDING, CONFIRMED})  -- 409 INCIDENT_ALREADY_OPEN
  INSERT driver_incident_report(PENDING, order_status_snapshot = order.status)
  notifyManagers(orderCode)     -- try/catch, lỗi không rollback
  log driver_incident_reported
COMMIT  → trả {incidentId}
```

### Manager xác nhận (bán đơn lại pool)

```
BEGIN  -- ManagerIncidentService.confirm @Transactional
  report = findByIdForUpdate(id)                  -- lock; 404 nếu không thấy
  assert report.status == PENDING                  -- 409 INCIDENT_ALREADY_PROCESSED
  order  = orderRepository.findByIdForUpdate(report.orderId), filter deletedAt == null  -- 404
  assert order.driverId == report.driverId
     AND order.status ∈ {ACCEPTED, IN_PROGRESS}    -- 409 ORDER_STATE_CHANGED

  -- Bán lại pool (KHÔNG publish event — FR-029)
  order.driverId = NULL; order.arrivedAt = NULL; order.startedAt = NULL
  order.status = CONFIRMED

  report.status = CONFIRMED
  report.confirmedBy = actor.id; report.confirmedAt = NOW()
  report.reassignDeadline = NOW() + reassignMinutes

  INSERT AuditLog(INCIDENT_CONFIRMED)
  notify customer (ORDER_REASSIGNING)      -- safeNotify
  notify driver   (DRIVER_INCIDENT_REPORTED)
COMMIT
```

### Tài xế khác nhận lại

```
BEGIN  -- DriverOrderService.acceptOrder @Transactional
  ... logic nhận đơn thường (lock, kiểm tra bận, transition → ACCEPTED) ...
  driverIncidentService.resolveReassigned(orderId)     -- Propagation.MANDATORY
    └─ IF tồn tại report CONFIRMED của đơn:
         report.status = RESOLVED_REASSIGNED
         notify tài xế cũ ("Đơn đã có tài xế mới")
         log incident_resolved_reassigned
       ELSE: bỏ qua im lặng (idempotent)
COMMIT
```

### Manager hoàn cọc + bồi thường

```
BEGIN  -- ManagerIncidentService.compensate @Transactional
  report = findByIdForUpdate(id)
  assert report.status == CONFIRMED                       -- 409 INCIDENT_NOT_CONFIRMED
  assert report.reassignDeadline != NULL
     AND NOW() >= report.reassignDeadline                 -- 409 REASSIGN_WINDOW_NOT_OVER
  order = findByIdForUpdate(report.orderId), filter deletedAt == null   -- 404
  assert order.status == CONFIRMED AND order.driverId == NULL  -- 409 ORDER_ALREADY_REASSIGNED

  deposit          = FLOOR(order.totalQuote × order.commissionRateSnapshot)
  compensation     = compensationAmountVnd
  refundToCustomer = deposit + compensation

  -- 1) Cộng ví khách (Propagation.MANDATORY)
  customerRefundService.refundForCancellation(order.customerId, order.id, refundToCustomer, ...)
     ├─ wallet.balance += refundToCustomer
     └─ INSERT transaction(REFUND, +refundToCustomer, related_order_id, balance_after)

  -- 2) Trừ tài xế: cọc → ví → SUSPENDED
  penaltyCollected = deductPenaltyFromDriver(report.driverId, order.id, orderCode, compensation)
     ├─ depositPart = min(profile.depositAmount, remaining)
     │    profile.depositAmount -= depositPart
     │    INSERT transaction(DAMAGE_DEDUCTION, -depositPart, "(trừ cọc)")
     ├─ walletPart = min(wallet.balance, remaining)
     │    wallet.balance -= walletPart
     │    INSERT transaction(DAMAGE_DEDUCTION, -walletPart, balance_after, "(trừ ví)")
     └─ IF remaining > 0: suspendDriverForPenalty(...)
          driver.suspensionPreviousStatus = driver.status
          driver.status = SUSPENDED; driver.suspendedAt = NOW()
          driver.suspensionReason = "Thiếu tiền bồi thường... Cần nạp {X} VND..."
          notify driver (PENALTY_ACCOUNT_LOCKED)

  -- 3) Huỷ đơn (COMPANY)
  order.status = CANCELLED; order.cancelledAt = NOW()
  order.cancellationReason = "Sự cố vận chuyển: không tìm được tài xế thay thế trong {N} phút
                              (cancelled_by=COMPANY)."

  report.status = COMPENSATED
  report.refundAmount = refundToCustomer; report.penaltyAmount = penaltyCollected
  report.compensatedBy = actor.id; report.compensatedAt = NOW()

  INSERT AuditLog(INCIDENT_COMPENSATED)
  notify customer (ORDER_INCIDENT_REFUNDED)
  notify driver   (PENALTY_WALLET_DEDUCTED)
COMMIT
```

---

## State Machine

### `driver_incident_report`

```
   Driver POST /api/driver/orders/{id}/incident
   (chỉ khi đơn ACCEPTED hoặc IN_PROGRESS)
                    │
                    ▼
               [PENDING] ─── Driver POST /incident/photos (≤ 3 ảnh)
                    │
       Manager /confirm  (đơn còn đúng tài xế + đúng trạng thái)
                    │  → đơn: driver_id=NULL, status=CONFIRMED (về pool)
                    │  → reassign_deadline = NOW() + 15 phút
                    ▼
              [CONFIRMED] ────────────────────────────┐
                    │                                 │
    Tài xế khác acceptOrder                Manager /compensate
    (trong hoặc ngoài 15 phút)             (CHỈ khi NOW() > deadline
                    │                       VÀ đơn vẫn ở pool)
                    ▼                                 ▼
       [RESOLVED_REASSIGNED]                   [COMPENSATED]
        không có bút toán nào          khách +cọc+200k · tài xế -200k
            (terminal)                      đơn → CANCELLED
                                               (terminal)
```

| Từ | Sang | Actor | Điều kiện | Hệ quả tiền |
|----|------|-------|-----------|-------------|
| (init) | `PENDING` | DRIVER | Đơn `ACCEPTED`/`IN_PROGRESS`, chưa có sự cố mở | Không |
| `PENDING` | `CONFIRMED` | MANAGER | Đơn còn đúng tài xế + trạng thái | Không |
| `CONFIRMED` | `RESOLVED_REASSIGNED` | DRIVER (khác) | Nhận lại đơn từ pool | Không |
| `CONFIRMED` | `COMPENSATED` | MANAGER | Quá deadline + đơn vẫn ở pool | Khách **+cọc+200k**, tài xế **−200k** |

Mọi transition ngoài bảng → HTTP 409 (HR-05).

### Tác động lên Order State Machine (CONTEXT §2)

```
ACCEPTED ────┐
             ├── Manager confirm sự cố ──> CONFIRMED (driver_id=NULL, về pool)
IN_PROGRESS ─┘                                 │
                                               ├── Driver khác accept ──> ACCEPTED (bình thường)
                                               └── Manager compensate ──> CANCELLED (COMPANY)
```

> ⚠️ **Hai transition này KHÔNG có trong CONTEXT §2 State Machine.** Xem DS-02.

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | — |
| 403 | `FORBIDDEN` | Sai role (kể cả ADMIN gọi endpoint Manager) | — |
| 403 | — (`AccessDeniedException`) | Báo sự cố cho đơn người khác | "Bạn chỉ có thể báo sự cố cho đơn của chính mình." |
| 404 | `ORDER_NOT_FOUND` | Đơn không tồn tại/đã xoá mềm | "Không tìm thấy đơn hàng." |
| 404 | `INCIDENT_NOT_FOUND` | Sự cố không tồn tại | "Không tìm thấy sự cố vận chuyển." |
| 409 | `INVALID_STATE` | Đơn không ở `ACCEPTED`/`IN_PROGRESS` | "Chỉ báo sự cố khi đang trên đường đến điểm đón hoặc đang giao hàng." |
| 409 | `INCIDENT_ALREADY_OPEN` | Đơn đã có sự cố mở | "Đơn này đã có báo cáo sự cố đang được xử lý." |
| 409 | `INCIDENT_ALREADY_PROCESSED` | Sự cố không còn PENDING khi confirm | "Sự cố đã được xử lý." |
| 409 | `ORDER_STATE_CHANGED` | Đơn đổi tài xế/trạng thái trước khi confirm | "Đơn đã đổi trạng thái, không thể xác nhận sự cố." |
| 409 | `INCIDENT_NOT_CONFIRMED` | Compensate khi sự cố chưa CONFIRMED | "Chỉ hoàn cọc + bồi thường cho sự cố đã xác nhận." |
| 409 | `REASSIGN_WINDOW_NOT_OVER` | Compensate trước deadline | "Chưa quá 15 phút chờ tài xế mới, chưa thể hoàn cọc." |
| 409 | `ORDER_ALREADY_REASSIGNED` | Đơn đã có tài xế nhận lại | "Đơn đã có tài xế nhận lại, không thể hoàn cọc." |
| 409 | `DRIVER_WALLET_NOT_FOUND` | Không tìm thấy ví tài xế | "Không tìm thấy ví tài xế." |
| 422 | `VALIDATION_ERROR` | `reason` rỗng/> 500, `status` sai, `page`/`size` sai | "Vui lòng mô tả sự cố." |
| 422 | `INVALID_FILE` | Ảnh rỗng/> 1.5 MB/sai magic number | — |
| 502 | `CLOUDINARY_UNAVAILABLE` | Cloudinary lỗi | — |

---

## Frontend Screen Contract

### `manager/driver-incidents.html` — "Sự cố tài xế"

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| 3 KPI | 3 lần gọi `?status=PENDING\|CONFIRMED\|COMPENSATED&size=1` đọc `totalElements` | Chờ xác nhận / Đang tìm tài xế / Đã bồi thường |
| Filter | `?status=` | ALL/PENDING/CONFIRMED/RESOLVED_REASSIGNED/COMPENSATED |
| Bảng | `GET /api/manager/incidents?page&size&status` | |
| Cột | Mã đơn, Tài xế, Mô tả, Trạng thái, Trạng thái đơn lúc báo, Hạn tìm tài xế, Ngày báo | |
| Badge trạng thái | `PENDING` → "Chờ xác nhận" (warning) · `CONFIRMED` → "Đang tìm tài xế" (primary) · `RESOLVED_REASSIGNED` → "Đã có tài xế mới" (success) · `COMPENSATED` → "Đã bồi thường" (neutral) | |
| Cờ quá hạn | `overdue = true` → badge "Quá hạn" (danger) | Bật nút Bồi thường |
| Modal chi tiết | `GET /{id}` | Mô tả, ảnh, tài xế, khách, số cọc, mức bồi thường |
| Ảnh | `photoUrls[]` signed | Hết hạn → load lại detail |
| Nút "Xác nhận sự cố" | `POST /{id}/confirm` | Chỉ hiện khi `PENDING` |
| Nút "Hoàn cọc + bồi thường" | `POST /{id}/compensate` | Chỉ enable khi `CONFIRMED` + `overdue` |
| Loading/Empty/Error | AC-16 | "Không có sự cố nào" |

### `driver/in-progress.html` — "Đang giao"

| Thành phần | Contract |
|------------|----------|
| Nút "Báo sự cố" | Hiện khi đơn `ACCEPTED`/`IN_PROGRESS` |
| Modal mô tả | Textarea ≤ 500 ký tự, bắt buộc |
| Submit | `POST /api/driver/orders/{id}/incident` → nhận `incidentId` |
| Upload ảnh | `POST /api/driver/orders/{id}/incident/photos` — lặp tối đa 3 lần |
| Sau khi gửi | Hiển thị "Đã gửi báo cáo, chờ quản lý xác nhận" |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Ownership báo sự cố | `order.driver_id == JWT.sub` (FR-004) |
| Ownership ảnh | Sự cố phải thuộc đơn của chính tài xế (FR-011) |
| RBAC Manager | `/api/manager/incidents/**` → MANAGER only; **Admin cũng bị 403** |
| Ảnh Cloudinary | `type = "authenticated"` — không truy cập bằng URL công khai |
| Signed URL | Sinh mới mỗi lần mở detail; vòng đời dựa JWT 15 phút (AC-10) |
| Magic number | Validate trước khi gọi Cloudinary |
| PII trong detail | Manager thấy SĐT tài xế + khách — cần thiết để gọi điều phối |
| Audit detail | JSON chứa `order_code`, `refund`, `penalty_collected`; không chứa PII |
| Suspension reason | Chứa số tiền — hiển thị cho chính tài xế, không public |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-023-01 | Báo sự cố khi `ACCEPTED` → tạo PENDING, trả `incidentId` | E2E |
| AC-023-02 | Báo sự cố khi `IN_PROGRESS` → tạo PENDING | E2E |
| AC-023-03 | Báo sự cố khi `COMPLETED` → 409 `INVALID_STATE` | Test |
| AC-023-04 | Báo sự cố cho đơn người khác → 403 | Test |
| AC-023-05 | Báo sự cố 2 lần khi đã có sự cố mở → 409 | Test |
| AC-023-06 | Ảnh thứ 4 → 422 | Test |
| AC-023-07 | Manager confirm → đơn `driver_id=NULL`, `status=CONFIRMED`, deadline = +15p | DB check |
| AC-023-08 | Manager confirm khi đơn đã đổi trạng thái → 409 `ORDER_STATE_CHANGED` | Test |
| AC-023-09 | Tài xế khác nhận lại → sự cố `RESOLVED_REASSIGNED` | E2E |
| AC-023-10 | Compensate trước deadline → 409 `REASSIGN_WINDOW_NOT_OVER` | Test |
| AC-023-11 | Compensate khi đơn đã có tài xế → 409 `ORDER_ALREADY_REASSIGNED` | Test |
| AC-023-12 | Compensate happy path → khách +cọc+200k, tài xế −200k, đơn CANCELLED | DB check |
| AC-023-13 | Tài xế đủ cọc → trừ hết từ cọc, ví không đổi | DB check |
| AC-023-14 | Tài xế thiếu cọc → trừ cọc rồi ví, 2 bút toán riêng | DB check |
| AC-023-15 | Tài xế thiếu cả hai → SUSPENDED + `penalty_amount` = số thực thu | DB check |
| AC-023-16 | Tài xế đã SUSPENDED → không ghi đè `suspension_previous_status` | Test |
| AC-023-17 | Ví tài xế không bao giờ âm | DB CHECK |
| AC-023-18 | Admin gọi endpoint Manager → 403 | Test RBAC |
| AC-023-19 | Đơn có 2 sự cố lần lượt (hỏng → nhận lại → hỏng) | E2E |
| AC-023-20 | Màn Manager đủ Loading/Empty/Error | Manual |
| AC-023-21 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Tài xế khác nhận lại đơn SAU deadline nhưng TRƯỚC khi Manager bấm compensate** → `acceptOrder`
   thành công → sự cố `RESOLVED_REASSIGNED`; Manager bấm compensate sau đó nhận 409
   `ORDER_ALREADY_REASSIGNED`. **Đúng** — khách được phục vụ, không cần bồi thường.
2. **Manager không bao giờ bấm compensate** → đơn kẹt ở `CONFIRMED` (pool) vô thời hạn, khách chờ mãi.
   **Không có scheduled job tự động** — xem DS-01.
3. **Tài xế gây sự cố tự nhận lại chính đơn đó** → code **không chặn**; `acceptOrder` chỉ kiểm tra tài xế
   có đang bận không. Xem DS-07.
4. **`total_quote = 0`** → `deposit = 0` → khách vẫn nhận 200.000 (bồi thường), tài xế vẫn bị trừ 200.000.
   Không có guard `NO_DEPOSIT_TO_REFUND` như Spec #022 — **có chủ ý**, vì bồi thường không phụ thuộc cọc.
5. **Tài xế có `deposit_amount = 0` và ví = 0** → `penalty_collected = 0`, SUSPENDED ngay, khách vẫn
   nhận đủ → công ty chịu toàn bộ 200.000.
6. **Hai Manager cùng bấm confirm** → `findByIdForUpdate` lock; người thứ hai nhận 409.
7. **Đơn bị xoá mềm giữa lúc xử lý** → `filter(deletedAt == null)` → 404.
8. **Sự cố `PENDING` mà Manager không xác nhận** → đơn vẫn ở `ACCEPTED`/`IN_PROGRESS` với tài xế cũ;
   tài xế bị kẹt không nhận đơn mới được (bận). Không có SLA — xem DS-01.
9. **Cloudinary lỗi khi upload ảnh** → 502; báo cáo sự cố **vẫn tồn tại** (đã tạo ở bước 1), tài xế thử
   lại ảnh sau.
10. **`compensationAmountVnd` đổi giữa chừng** (restart app với config mới) → sự cố cũ đã COMPENSATED giữ
    `refund_amount`/`penalty_amount` đã snapshot; sự cố mới dùng giá trị mới. **Đúng** — snapshot pattern.

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-023-01 | Unit | `validateReason(null)` | 422 |
| TC-023-02 | Unit | `validateReason(501 ký tự)` | 422 |
| TC-023-03 | Unit | `depositOf(total=940000, rate=0.3)` | `282000` |
| TC-023-04 | Unit | `isOverdue(CONFIRMED, deadline quá khứ)` | true |
| TC-023-05 | Unit | `isOverdue(PENDING, deadline null)` | false |
| TC-023-06 | Unit | `normalizeStatus("all")` | null |
| TC-023-07 | Unit | `normalizeStatus("FOO")` | 422 |
| TC-023-08 | Integration | Báo sự cố khi ACCEPTED | 200 + incidentId |
| TC-023-09 | Integration | Báo sự cố khi AWAITING_FINAL_PAYMENT | 409 |
| TC-023-10 | Integration | Báo sự cố đơn tài xế khác | 403 |
| TC-023-11 | Integration | Báo sự cố 2 lần | 409 INCIDENT_ALREADY_OPEN |
| TC-023-12 | Integration | Confirm happy path | Đơn về pool, deadline set |
| TC-023-13 | Integration | Confirm lần 2 | 409 |
| TC-023-14 | Integration | Confirm khi đơn đã đổi tài xế | 409 ORDER_STATE_CHANGED |
| TC-023-15 | Integration | `resolveReassigned` khi có CONFIRMED | → RESOLVED_REASSIGNED |
| TC-023-16 | Integration | `resolveReassigned` khi không có sự cố | Bỏ qua, không lỗi |
| TC-023-17 | Integration | Compensate trước deadline | 409 |
| TC-023-18 | Integration | Compensate khi đơn đã reassign | 409 |
| TC-023-19 | Integration | Compensate happy path | Khách +482k, tài xế −200k, đơn CANCELLED |
| TC-023-20 | Integration | `deductPenaltyFromDriver` cọc đủ | 1 bút toán (trừ cọc) |
| TC-023-21 | Integration | `deductPenaltyFromDriver` cọc thiếu, ví đủ | 2 bút toán |
| TC-023-22 | Integration | `deductPenaltyFromDriver` cả hai thiếu | SUSPENDED, penalty < 200k |
| TC-023-23 | Integration | Tài xế đã SUSPENDED | Không ghi đè previous_status |
| TC-023-24 | Integration | Driver gọi endpoint Manager | 403 |
| TC-023-25 | Concurrency | 2 thread cùng confirm | 1 thành công, 1 nhận 409 |
| TC-023-26 | Concurrency | 2 thread cùng compensate | 1 thành công, 1 nhận 409 |
| TC-023-27 | Integration | Đơn 2 sự cố lần lượt | Partial unique index cho phép |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Scheduled job tự động compensate khi quá deadline** — bản này yêu cầu Manager bấm tay (FR-038) | **Rủi ro vận hành lớn nhất của spec:** Manager quên → khách chờ vô thời hạn, đơn kẹt ở pool, không ai được thông báo. Escrow tiền (CONTEXT §2) có job chạy mỗi 5 phút; luồng này thì không | Job quét `status=CONFIRMED AND reassign_deadline < NOW()`. Index `idx_driver_incident_confirmed` đã thiết kế sẵn cho việc này. Xem OQ-2 |
| DS-02 | **Cập nhật CONTEXT §2 State Machine** — 3 transition mới: `ACCEPTED → CONFIRMED`, `IN_PROGRESS → CONFIRMED` (bán lại pool), `CONFIRMED → CANCELLED (COMPANY)` (bồi thường) | Bảng transition trong CONTEXT thiếu 3 dòng → tài liệu state machine không đầy đủ; hội đồng đối chiếu sẽ thấy hụt | Thêm 3 dòng vào CONTEXT §2 + ghi chính sách bồi thường. Xem OQ-1 |
| DS-03 | **CHECK constraint ràng buộc field theo status** cho `driver_incident_report` — `V41` (Spec #022) có `ck_order_cancellation_refund_terminal`, `V44` thì không | DB không chặn bản ghi không nhất quán: `COMPENSATED` mà `refund_amount` NULL, hoặc `PENDING` mà đã có `confirmed_by` | Thêm CHECK tương tự V41 (cần migration mới) |
| DS-04 | **Endpoint tài xế xem sự cố của mình** — bản này chỉ Manager tra cứu được | Tài xế báo xong không biết trạng thái, chỉ nhận notification một chiều. Index `idx_driver_incident_driver` đã có sẵn nhưng chưa dùng | `GET /api/driver/incidents` |
| DS-05 | **Khai báo 2 property vào `application.properties`**: `app.incident.reassign-minutes=15`, `app.incident.compensation=200000` — bản này dựa vào default trong `@Value` | Đọc properties không thấy cấu hình; demo muốn hạ 15 phút → 1 phút phải sửa code | Thêm 2 dòng (theo mẫu `app.escrow.hold-minutes`) |
| DS-06 | Bổ sung `manager/driver-incidents.html` vào `SCREEN_INVENTORY.md` | Số màn hình báo cáo thiếu | Cập nhật inventory |
| DS-07 | **Chặn tài xế gây sự cố tự nhận lại chính đơn đó** — bản này không có guard | Tài xế báo sự cố để thoát đơn khó rồi nhận lại chính đơn đó khi đổi ý; vô lý về nghiệp vụ | Guard trong `acceptOrder`: chặn nếu tồn tại sự cố `CONFIRMED` của chính driver này trên đơn. Xem OQ-3 |
| DS-08 | **Cleanup ảnh Cloudinary** khi sự cố đóng — AC-10 yêu cầu destroy asset | Ảnh bằng chứng tích tụ vĩnh viễn trên free tier 25 GB. Spec #024 có mẫu cleanup đúng để tham chiếu | Job dọn định kỳ |
| DS-09 | Chính sách soft delete cho 2 bảng mới — cả hai không có `deleted_at` | Lệch AC-09 về hình thức. Chấp nhận được: bản ghi audit tài chính, không được xoá | Leader ghi nhận ngoại lệ AC-09 |
| DS-10 | **Quota số lần báo sự cố / tài xế** — bản này không giới hạn | Tài xế lạm dụng để né đơn khó: mất 200.000 đ nếu không ai nhận lại, mất **0 đ** nếu có người nhận lại → chi phí lạm dụng gần bằng không | Quota theo mẫu "từ chối 3 đơn/ngày" (CONTEXT §Phân công). Xem OQ-4 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | Chính sách 200.000 đ + công ty chịu cọc có được ghi vào CONTEXT/Constitution không? Hiện chỉ tồn tại trong comment `V44` + code | Tài liệu | **High** |
| OQ-2 | Có thêm scheduled job auto-compensate không? (DS-01) | DS-01 | **High** |
| OQ-3 | Có chặn tài xế gây sự cố nhận lại chính đơn đó không? (DS-07) | DS-07 | Medium |
| OQ-4 | Có quota số lần báo sự cố/tài xế/tháng không? (DS-10) | DS-10 | Medium |
| OQ-5 | Tài xế có được xem lịch sử sự cố của mình không? (DS-04) | — | Medium |
| OQ-6 | Khi tài xế báo sự cố lúc `IN_PROGRESS` (đồ đã lên xe), đồ khách xử lý thế nào? Quy trình vận hành ngoài hệ thống? | Nghiệp vụ | **High** |
| OQ-7 | Mức 200.000 đ có nên theo % giá trị đơn thay vì cố định không? | — | Low |

---

## Rollout Plan

> ⛔ **Điều kiện tiên quyết:** OQ-1 (ghi chính sách bồi thường + 3 transition vào CONTEXT §2) phải chốt
> trước khi code — đây là chính sách tiền mới, chưa có ở bất kỳ tài liệu cấp trên nào.

**Phụ thuộc:**

- Spec #021 (ví khách) — đích đến của tiền hoàn + bồi thường.
- Spec #007 (ví tài xế + cọc 3 triệu) — nguồn trừ tiền phạt.
- Spec #006 (nhận đơn) — hook `resolveReassigned` gắn vào `acceptOrder`.
- Spec #019 (chat) — xem DS-02 của spec đó: hội thoại `CUSTOMER_DRIVER` chưa xử lý việc đơn đổi tài xế,
  mà luồng này chính là nguyên nhân gây đổi tài xế. **Hai spec phải lên cùng nhau hoặc #019 lên trước.**

**Thứ tự triển khai:**

1. Chốt OQ-1 + cập nhật CONTEXT §2 (3 transition + chính sách 200.000 đ).
2. `V44` — 2 bảng + 4 index + trigger. Không đụng bảng hiện có, không backfill.
3. Thêm 2 property vào `application.properties` (DS-05) — cho phép demo hạ 15 phút xuống 1 phút.
4. Backend: `DriverIncidentService` (báo sự cố) → `DriverIncidentPhotoService` →
   `ManagerIncidentService` (confirm/compensate) → hook `resolveReassigned` trong `acceptOrder`.
5. Frontend: nút "Báo sự cố" ở `driver/in-progress.html` → `manager/driver-incidents.html`.

**Rủi ro cần theo dõi khi rollout:**

- **DS-01 (thiếu scheduled job)** là rủi ro vận hành nghiêm trọng nhất: toàn bộ cơ chế bồi thường phụ
  thuộc Manager nhớ bấm nút. Cân nhắc đưa job vào ngay bản 1.0.0 thay vì hoãn.
- Luồng `compensate` đụng **4 nguồn tiền** trong một transaction (ví khách +, cọc tài xế −, ví tài xế −,
  trạng thái đơn) — test kỹ nhánh tài xế không đủ tiền (TC-023-22) và concurrency (TC-023-26).
- Đơn quay lại pool ở `CONFIRMED` — trùng trạng thái mà Spec #022 dùng cho hủy đơn hoàn cọc. Kiểm tra
  hai luồng không xung đột khi khách bấm hủy đúng lúc đơn vừa bị bán lại pool.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #23 Driver Incident Report  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | PASS | Cloudinary qua env |
| HR-02 BCrypt | N/A | |
| HR-03 IPN nguồn duy nhất | N/A | |
| HR-04 Verify HMAC | N/A | |
| HR-05 Transition sai → 409 | PASS | FR-003, FR-025, FR-026, FR-039, FR-040, FR-041 |
| HR-06 Driver không COMPLETED khi IN_DISPUTE | N/A | Luồng khác |
| HR-07 Chỉ Manager/Admin đóng IN_DISPUTE | N/A | |
| HR-08 Driver concurrency lock | PASS | `findByIdForUpdate` cho cả report + order (FR-028) |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | FR-004, FR-011, FR-051, FR-052 |
| HR-11 Email không rollback | PASS (tinh thần) | `safeNotify` try/catch |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log state change | PASS | FR-009, FR-030, FR-049 |
| HR-14 RefundRecord | ⚠️ **EXCEPTION** | Hoàn tiền đi qua `customer_wallet`, không tạo RefundRecord dù đây là **COMPANY cancel** — HR-14 nói "RefundRecord PHẢI tạo khi và chỉ khi `cancelled_by = COMPANY`". Xem Spec #022 OQ-1 |
| HR-15 Idempotency | PASS | Partial unique index + guard trạng thái dưới lock |
| HR-16 Rate limit login | N/A | |
| HR-17 Public vs Authenticated | PASS | Không endpoint public |
| HR-18 Wallet không âm | PASS | `min()` khi trừ; DB CHECK (FR-045) |
| HR-19 Brand identity | PASS | |
| HR-20 Tiếng Việt có dấu | PASS | Toàn bộ message user-facing **có dấu đầy đủ** |
| HR-21 Tránh reserved words | PASS | `driver_incident_report`, `driver_incident_photo` |

**Layer 1 Result:** 1 exception — HR-14 (COMPANY cancel không tạo RefundRecord).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | PASS | JPA |
| AC-05 Chat | N/A | |
| AC-06 Maps fallback | N/A | |
| AC-07 Timezone | PASS | TIMESTAMPTZ UTC; deadline tính bằng `OffsetDateTime` UTC |
| AC-08 BigDecimal scale=0 | PASS | FLOOR cho cọc; `min()` cho phạt |
| AC-09 Soft delete | ⚠️ **EXCEPTION** | 2 bảng mới không có `deleted_at` (DS-09) |
| AC-10 Cloudinary signed upload | ⚠️ **PARTIAL** | Signed upload ✅, magic number ✅, ≤1.5MB ✅, tối đa 3 ✅, signed URL ✅. **Thiếu cleanup** (DS-08); URL không set `expires_at` 1h |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V44 |
| AC-13 Money audit trail | PASS (tinh thần) | Mỗi lần đổi số dư ghi bút toán trong cùng TX. **Lưu ý:** bút toán "(trừ cọc)" **không có `balance_after`** (vì cọc nằm ở `driver_profile`, không phải wallet) — lệch nhẹ AC-13 |
| AC-14 VARCHAR + CHECK | PASS | `status` + `order_status_snapshot` |
| AC-15 Pagination | PASS | Default 20, max 100 |
| AC-16 Empty/Loading/Error | PASS | Màn Manager |

**Layer 2 Result:** 2 exception (AC-09, AC-10), 1 lệch nhẹ (AC-13 `balance_after` cho bút toán trừ cọc).

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/incidents` + action sub-resource |
| ES-03 Bean Validation + 422 | PARTIAL | Validate thủ công (không `@Valid` — `ReportIncidentRequest` là record đơn giản) |
| ES-04 Error format | PARTIAL | `"CODE\|Message"` map qua advice; `AccessDeniedException` (FR-004) không theo format |
| ES-05 Test coverage ≥70% CORE | ⚠️ **CHƯA VERIFY** | Cần coverage cho `DriverIncidentService`, `ManagerIncidentService`, `DriverIncidentPhotoService` |
| ES-06/07 Commits | PASS | `feat(incident): tài xế báo sự cố vận chuyển + Manager điều phối lại đơn` |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 20/21 PASS, 1 exception (HR-14)  
Layer 2 : 14/16 PASS, 2 exception (AC-09, AC-10), AC-13 lệch nhẹ  
Layer 3 : 6/8 PASS, ES-03/ES-04 partial, ES-05 chưa verify  
Status  : **BLOCKED** — cần chốt OQ-1 (ghi chính sách bồi thường + 3 transition vào CONTEXT §2) trước
khi triển khai, vì đây là chính sách tiền chưa có ở bất kỳ tài liệu cấp trên nào. Cần thêm OQ-2
(scheduled job — rủi ro vận hành lớn nhất) và OQ-6 (quy trình xử lý đồ khách khi hỏng xe giữa chuyến —
vấn đề vận hành ngoài hệ thống mà spec không giải quyết được).
================================
