# Feature Specification: Order Cancellation Refund (Khách hủy đơn — hoàn cọc)

**Feature Branch:** `022-order-cancellation-refund`  
**Feature Number:** #22 of 26 — CORE (money-critical)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 6 — ngoại lệ hoàn cọc theo HR-14 v1.4.0 (leader duyệt 2026-06-18)

**CONTEXT.md reference:** v2.0 §2 Order State Machine (row `CONFIRMED → CANCELLED` CUSTOMER),
§2 Huy don & Hoan tien, §2 Wallet & Commission  
**Constitution reference:** v1.4.0 — **HR-14** (ngoại lệ hoàn cọc v1.4.0), HR-05, HR-10, HR-13, HR-18,
HR-20, HR-21, AC-07, AC-08, AC-10, AC-12, AC-13, AC-14, AC-15, AC-16, ES-02, ES-03, ES-04  
**Screen reference:** `frontend/pages/manager/cancellation-refunds.html` — cần bổ sung vào
`docs/SCREEN_INVENTORY.md` (xem DS-05)  
**Related specs:** Spec #021 Customer Wallet & Withdrawal (đích đến của tiền hoàn);
Spec #003 Customer Orders (luồng hủy đơn); Spec #010 Manager Disputes (luồng hoàn tiền khác);
Spec #013 Admin System Transactions (sổ cái)

**Migration liên quan:** `V41__create_order_cancellation_refund.sql` (`order_cancellation_refund` +
`order_cancellation_photo`)

---

## Goals

Đặc tả luồng **khách chủ động hủy đơn đã cọc khi chưa có tài xế nhận** và được hoàn lại cọc 30% về
Ví khách hàng sau khi Manager duyệt thủ công.

Khi Customer hủy đơn ở trạng thái `CONFIRMED` (đã trả cọc 30%, `driver_id = NULL`), hệ thống chuyển đơn
sang `CANCELLED` và **tự động mở một yêu cầu hoàn cọc** (`order_cancellation_refund`, status `PENDING`)
kèm lý do khách nhập. Khách có thể đính kèm tối đa 3 ảnh bằng chứng qua Cloudinary signed upload.
Manager xem hàng đợi, mở chi tiết (kèm ảnh qua signed URL), rồi quyết định: **Hoàn cọc** (cộng 30% vào
`customer_wallet` + ghi `transaction` type `REFUND`) hoặc **Từ chối** kèm lý do (không đụng tiền).

Đây là **ngoại lệ chính sách được leader duyệt ngày 2026-06-18** và đã được codify vào Constitution
HR-14 v1.4.0: chính sách gốc CONTEXT §Hủy đơn quy định khách hủy từ `CONFIRMED` trở đi là **mất cọc**;
ngoại lệ này cho hoàn cọc **khi chưa có tài xế nào cam kết**, vì lúc đó chưa phát sinh chi phí vận hành.
Từ `ASSIGNED`/`ACCEPTED` trở đi khách **không hủy được** qua luồng này.

Spec định nghĩa điều kiện kích hoạt, công thức tính cọc, state machine, transaction boundaries, RBAC,
audit trail, upload ảnh và frontend contract. Tiền dùng `BigDecimal` scale=0 làm tròn **FLOOR** (AC-08),
ví không bao giờ âm (HR-18), mọi thay đổi số dư đi kèm bút toán append-only trong cùng transaction (AC-13).

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Trạng thái đồng bộ tài liệu

| Nguồn | Nội dung | Trạng thái |
|-------|----------|-----------|
| Constitution **HR-14 v1.4.0** | Đã thêm ngoại lệ hoàn cọc: CUSTOMER hủy `CONFIRMED` khi `driver_id = NULL` → tạo `order_cancellation_refund` PENDING kèm lý do + ảnh (tối đa 3, AC-10) → Manager duyệt → hoàn 30% về `customer_wallet` (REFUND, AC-13; HR-18). Migration `V41`. | ✅ **Đồng bộ với code** |
| `CONTEXT.md` §2 State Machine row `CONFIRMED → CANCELLED` CUSTOMER | Đã cập nhật 2026-06-18: "Khach huy khi CHUA co tai xe (driver_id NULL) → tao yeu cau hoan coc... Manager duyet → hoan coc 30% ve vi khach" | ✅ **Đồng bộ** |
| `CONTEXT.md` §2 Metadata bắt buộc khi CANCELLED | Đã cập nhật: "CUSTOMER huy o CONFIRMED khi CHUA co tai xe → tao yeu cau hoan coc (order_cancellation_refund), Manager duyet → hoan coc 30% ve customer_wallet" | ✅ **Đồng bộ** |
| `CONTEXT.md` §2 Hủy đơn & Hoàn tiền | Đã cập nhật gạch đầu dòng thứ 2 với ghi chú "(Cap nhat 2026-06-18 — leader duyet...)" | ✅ **Đồng bộ** |

> **Nền tảng vững:** Khác Spec #021 (ví khách — còn chờ leader duyệt), ngoại lệ hoàn cọc đã được
> codify vào **Constitution HR-14 v1.4.0** và **CONTEXT §Hủy đơn** cùng ngày 2026-06-18, **trước** khi
> spec này được viết. Spec này chỉ chi tiết hoá một quyết định đã có hiệu lực ở cấp trên.

### Ranh giới cần làm rõ trước khi triển khai

| Chủ đề | Vấn đề | Trạng thái |
|--------|--------|-----------|
| Đích đến của tiền hoàn | HR-14 và CONTEXT §Hủy đơn đều chỉ định `customer_wallet`, nhưng CONTEXT §Wallet ở chỗ khác vẫn ghi "KHONG co vi cho Customer" | ⚠️ CONTEXT **tự mâu thuẫn** — phụ thuộc Spec #021 OQ-1 |
| `RefundRecord` | HR-14 quy định "RefundRecord VAN chi tao khi COMPANY huy (khong doi)", nhưng thiết kế hiện hành **không có bảng `refund_record`** và không luồng nào tạo nó. Vậy COMPANY hủy đơn thì tiền khách đi đâu? | ⚠️ **OQ-1** (spec này) |
| Điều kiện `driver_id = NULL` | HR-14 + CONTEXT quy định hoàn cọc chỉ khi **chưa có tài xế**; spec này đặc tả guard tương ứng ở FR-004 | ⚠️ Cần chốt — xem OQ-2, DS-01 |

### Quyết định canonical

| Chủ đề | Canonical | Nguồn |
|--------|-----------|-------|
| Trạng thái được hủy + hoàn cọc | `CONFIRMED` (đã cọc 30%) | HR-14 v1.4.0 |
| Trạng thái hủy không hoàn | `PENDING`, `PENDING_PAYMENT` — khách chưa trả gì | CONTEXT §Hủy đơn |
| Trạng thái không hủy được | `ASSIGNED` trở đi — cọc thuộc công ty, liên hệ Manager | HR-14 |
| Số tiền hoàn | `FLOOR(total_quote × commission_rate_snapshot)` | AC-08 |
| Người duyệt | **MANAGER** — không phải Admin | CONTEXT §3 RBAC ("Xu ly RefundRecord: Manager Yes") |
| Đích đến tiền | `customer_wallet` + bút toán `REFUND` | HR-14 v1.4.0 |
| Bảng | `order_cancellation_refund` — luồng **riêng**, không phải `RefundRecord` | HR-14 v1.4.0 |

---

## Scope Summary

**In scope:**

1. `PUT /api/customer/orders/{id}/cancel` — khách hủy đơn (mọi trạng thái cho phép).
2. Tự động mở `order_cancellation_refund` PENDING khi hủy từ `CONFIRMED`.
3. `POST /api/customer/orders/{id}/cancellation-photos` — đính kèm ảnh bằng chứng (tối đa 3).
4. `GET /api/manager/cancellation-refunds` — hàng đợi Manager, filter theo status.
5. `GET /api/manager/cancellation-refunds/{id}` — chi tiết + ảnh qua signed URL.
6. `POST /api/manager/cancellation-refunds/{id}/refund` — duyệt hoàn cọc.
7. `POST /api/manager/cancellation-refunds/{id}/reject` — từ chối kèm lý do.
8. Công thức tính cọc, money invariants, transaction boundaries.
9. Audit trail + notification cho Manager và Customer.
10. Loading/Empty/Error states cho màn Manager.

**Out of scope:**

1. Ví khách hàng (số dư, rút tiền) — Spec #021; spec này chỉ mô tả **bút toán cộng ví**.
2. Hoàn tiền do tranh chấp (DamageReport/DisputeReport) — Spec #010.
3. Hoàn tiền do COMPANY hủy đơn — ngoài phạm vi bản này, xem OQ-1.
4. Hủy đơn do khách báo tài xế không khớp (`reportDriverMismatch`) — luồng riêng, hoàn tiền thủ công.
5. Hủy đơn tự động do timeout 15 phút (SYSTEM) — không hoàn (khách chưa cọc thành công), HR-09.
6. Tài xế/Manager hủy đơn.
7. Hoàn tiền một phần (partial refund) — chỉ hoàn đủ 30% hoặc không hoàn.

---

## User Stories

**P1:**

**US1:** Là Customer, tôi hủy đơn đã cọc khi chưa có tài xế nào nhận và nêu lý do, để không bị mất
cọc oan khi đổi ý sớm.

**US2:** Là Customer, tôi đính kèm ảnh bằng chứng (tối đa 3) cho yêu cầu hủy để Manager có cơ sở duyệt.

**US3:** Là Customer, tôi nhận thông báo khi yêu cầu hoàn cọc được duyệt và thấy tiền vào ví ngay.

**US4:** Là Manager, tôi xem hàng đợi yêu cầu hoàn cọc theo FIFO để xử lý yêu cầu chờ lâu nhất trước.

**US5:** Là Manager, tôi xem chi tiết yêu cầu (lý do, ảnh, thông tin đơn, số cọc) trước khi quyết định.

**US6:** Là Manager, tôi bấm "Hoàn cọc" để cộng 30% vào ví khách trong một thao tác.

**P2:**

**US7:** Là Manager, tôi từ chối yêu cầu không hợp lệ kèm lý do; tiền khách không bị ảnh hưởng.

**US8:** Là Customer, tôi nhận thông báo kèm lý do khi yêu cầu bị từ chối.

**US9:** Là Manager, tôi lọc danh sách theo trạng thái (PENDING/REFUNDED/REJECTED) để tra cứu lịch sử.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **48 FR**, trong đó **22 FR có mệnh đề WHERE** (45.8% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Khách hủy đơn (FR-001..FR-008)

**FR-001**  
WHEN Customer gọi `PUT /api/customer/orders/{id}/cancel` với `reason` hợp lệ và đơn ở trạng thái cho
phép, THE system SHALL set `cancelled_at = NOW()`, `cancellation_reason = reason`, chuyển đơn sang
`CANCELLED` qua `OrderStatusTransitionService`, và trả `CancelOrderResponse`.

**FR-002**  
WHILE đơn ở một trong `PENDING`, `PENDING_PAYMENT`, `CONFIRMED`, THE system SHALL cho phép Customer hủy.

**FR-003**  
WHERE đơn ở trạng thái ngoài ba trạng thái trên (ví dụ `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`,
`AWAITING_FINAL_PAYMENT`, `COMPLETED`), THE system SHALL throw `IllegalStateException` với message
"Chỉ có thể hủy đơn ở trạng thái đang chờ xử lý." và SHALL không đổi trạng thái đơn (HR-05).

**FR-004**  
WHEN đơn được hủy từ trạng thái `CONFIRMED`, THE system SHALL gọi
`OrderCancellationRefundService.openForCancelledOrder()` tạo yêu cầu hoàn cọc PENDING, và trả
`refundRequested = true` với message "Đơn hàng đã được hủy. Yêu cầu hoàn cọc đã gửi tới quản lý để
xem xét."

**FR-005**  
WHEN đơn được hủy từ `PENDING` hoặc `PENDING_PAYMENT`, THE system SHALL **không** tạo yêu cầu hoàn cọc
và trả message "Đơn hàng đã được hủy." — khách chưa trả gì nên không có gì để hoàn.

**FR-006**  
WHERE `order.customer_id` khác JWT subject, THE system SHALL từ chối thao tác (HR-10); đơn SHALL được
load bằng `findOwnedOrderForUpdate` (pessimistic lock).

**FR-007**  
WHEN hủy đơn, THE system SHALL ghi audit log dòng
`order_state_audit actor_id=... actor_role=CUSTOMER timestamp=... from_state={previous}
to_state=CANCELLED entity_id=...` (HR-13).

**FR-008**  
WHILE tạo yêu cầu hoàn cọc, THE system SHALL chạy trong **cùng transaction** của `cancelOrder`
(`Propagation.MANDATORY`) — WHERE không có transaction đang mở, SHALL throw
`IllegalTransactionStateException`; đơn CANCELLED và yêu cầu hoàn cọc SHALL luôn được tạo cùng nhau
hoặc cùng rollback.

---

### Nhóm 2 — Mở yêu cầu hoàn cọc (FR-009..FR-014)

**FR-009**  
WHEN mở yêu cầu, THE system SHALL insert `order_cancellation_refund` với `order_id`, `customer_id`,
`reason`, `status = 'PENDING'`; `refund_amount`, `rejection_reason`, `processed_by`, `processed_at`
SHALL đều `NULL` theo CHECK constraint `ck_order_cancellation_refund_terminal`.

**FR-010**  
WHERE đơn **đã có** yêu cầu hoàn cọc (`existsByOrderId` = true), THE system SHALL log warning và
**return im lặng** thay vì throw — idempotent theo đơn.

**FR-011**  
WHILE bảng tồn tại, THE system SHALL enforce `UNIQUE (order_id)` — mỗi đơn tối đa **một** yêu cầu hoàn
cọc, vì mỗi đơn chỉ hủy được một lần.

**FR-012**  
WHEN yêu cầu được mở, THE system SHALL tạo notification cho **mọi Manager ACTIVE** với title "Yêu cầu
hoàn cọc do hủy đơn" và message chứa `order_code`.

**FR-013**  
WHERE việc tạo notification lỗi, THE system SHALL log warning và **vẫn hoàn tất** việc mở yêu cầu — lỗi
notification SHALL không rollback giao dịch chính (tinh thần HR-11).

**FR-014**  
WHEN yêu cầu được mở, THE system SHALL log dòng
`cancellation_refund_opened actor_id=... order_id=... order_code=...` (HR-13).

> **Quyết định:** notification tái dùng `NotificationType.ORDER_CANCELLED` thay vì tạo type mới, để
> không phát sinh migration ở bản này. Xem DS-04.

---

### Nhóm 3 — Đính kèm ảnh bằng chứng (FR-015..FR-023)

**FR-015**  
WHEN Customer gọi `POST /api/customer/orders/{id}/cancellation-photos` với multipart `file`, THE system
SHALL validate, upload lên Cloudinary signed upload server-side, lưu `order_cancellation_photo` và trả
HTTP 201 (AC-10).

**FR-016**  
WHERE không tìm thấy yêu cầu hoàn cọc của đơn đó **thuộc chính customer đó**
(`findByOrderIdAndCustomerId`), THE system SHALL trả HTTP 404 `CANCELLATION_NOT_FOUND` "Không tìm thấy
yêu cầu hủy đơn để đính kèm ảnh." — đây đồng thời là ownership check (HR-10).

**FR-017**  
WHERE yêu cầu không còn ở `PENDING`, THE system SHALL trả HTTP 409 `CANCELLATION_ALREADY_PROCESSED`
"Yêu cầu hủy đơn đã được xử lý, không thể đính kèm ảnh."

**FR-018**  
WHERE yêu cầu đã có **3 ảnh**, THE system SHALL trả HTTP 422 `TOO_MANY_PHOTOS` "Mỗi yêu cầu hủy chỉ
được đính kèm tối đa 3 ảnh." và SHALL không gọi Cloudinary (tiết kiệm quota free tier).

**FR-019**  
WHERE file rỗng, THE system SHALL trả 422 `INVALID_FILE` "Tệp tải lên không được để trống.";
WHERE `file.size > 1_572_864` bytes (1.5 MB), SHALL trả 422 "Kích thước ảnh không được vượt quá 1,5 MB."

**FR-020**  
WHERE nội dung file không khớp **magic number** của JPEG (`FF D8 FF`), PNG
(`89 50 4E 47 0D 0A 1A 0A`) hoặc WebP (`RIFF....WEBP`), THE system SHALL trả 422 `INVALID_FILE`
"Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ." và SHALL không gọi Cloudinary (AC-10).

**FR-021**  
WHEN upload lên Cloudinary, THE system SHALL dùng `folder = "movehome/cancellations/{cancellationId}"`,
`resource_type = "image"`, `type = "authenticated"`; SHALL lưu cả `secure_url` và `public_id`.

**FR-022**  
WHERE Cloudinary không khả dụng hoặc không trả `secure_url`/`public_id` hợp lệ, THE system SHALL trả
HTTP 502 `CLOUDINARY_UNAVAILABLE` "Không thể tải ảnh lên. Vui lòng thử lại."

**FR-023**  
WHEN Manager xem chi tiết, THE system SHALL sinh **signed URL** cho từng ảnh qua
`cloudinary.url().type("authenticated").signed(true)`; ảnh SHALL không bao giờ truy cập được bằng URL
công khai (AC-10).

---

### Nhóm 4 — Manager xem hàng đợi (FR-024..FR-030)

**FR-024**  
WHEN Manager gọi `GET /api/manager/cancellation-refunds?status={s}&page={p}&size={z}`, THE system SHALL
trả `Page<CancellationRefundListItem>`.

**FR-025**  
WHILE `status = 'PENDING'`, THE system SHALL sắp xếp **`created_at` ASC** (FIFO — chờ lâu nhất trước);
WHILE `status` là `REFUNDED`/`REJECTED` hoặc không truyền, SHALL sắp xếp `created_at` DESC (mới nhất trước).

**FR-026**  
WHEN `status` là `null`, rỗng hoặc `"ALL"` (không phân biệt hoa thường), THE system SHALL trả **tất cả**
trạng thái.

**FR-027**  
WHERE `status` không thuộc `{PENDING, REFUNDED, REJECTED}`, THE system SHALL trả HTTP 422
`VALIDATION_ERROR` "Trạng thái yêu cầu hoàn cọc không hợp lệ."

**FR-028**  
WHEN trả mỗi item, THE system SHALL bao gồm `id`, `orderId`, `orderCode`, `customerId`, `customerName`,
`reason`, `status`, `depositAmount` (tính từ đơn), `refundAmount`, `createdAt`, `processedAt`.

**FR-029**  
WHERE `page < 0`, THE system SHALL trả 422 "Số trang không hợp lệ."; WHERE `size <= 0` hoặc `size > 100`,
SHALL trả 422 "Kích thước trang phải từ 1 đến 100." Default `size` = 20 (AC-15).

**FR-030**  
WHEN Manager gọi `GET /api/manager/cancellation-refunds/{id}`, THE system SHALL trả
`CancellationRefundDetailResponse` gồm thông tin yêu cầu, `photoUrls` (signed), và `OrderSummary`
(`pickupAddress`, `dropoffAddress`, `totalQuote`, `scheduledAt`); WHERE không tìm thấy, SHALL trả 404
`CANCELLATION_NOT_FOUND` "Không tìm thấy yêu cầu hoàn cọc."

---

### Nhóm 5 — Manager duyệt hoàn cọc (FR-031..FR-040)

**FR-031**  
WHEN Manager gọi `POST /api/manager/cancellation-refunds/{id}/refund`, THE system SHALL: cộng tiền cọc
vào `customer_wallet.balance`, ghi `transaction` type `REFUND` dương với `related_order_id` +
`balance_after`, set yêu cầu `REFUNDED` + `refund_amount` + `processed_by` + `processed_at` — **tất cả
trong một transaction DB** (AC-13).

**FR-032**  
WHEN tính tiền cọc, THE system SHALL dùng công thức
`deposit = total_quote × commission_rate_snapshot`, làm tròn **FLOOR**, scale=0 (AC-08); WHERE
`commission_rate_snapshot` là `NULL`, SHALL dùng default `0.3000`; WHERE `total_quote` là `NULL`, SHALL
dùng `ZERO`.

**FR-033**  
WHERE `deposit <= 0`, THE system SHALL trả HTTP 422 `NO_DEPOSIT_TO_REFUND` "Đơn không có tiền cọc để
hoàn." và SHALL không cộng ví.

**FR-034**  
WHERE yêu cầu không còn ở `PENDING`, THE system SHALL trả HTTP 409 `CANCELLATION_ALREADY_PROCESSED`
"Yêu cầu hoàn cọc đã được xử lý." — chống duyệt hai lần (HR-05).

**FR-035**  
WHEN load yêu cầu để xử lý, THE system SHALL dùng `findByIdForUpdate` (pessimistic lock); đơn cũng SHALL
được load bằng `findByIdForUpdate` — hai Manager bấm duyệt đồng thời chỉ một người thắng.

**FR-036**  
WHERE không tìm thấy đơn của yêu cầu, THE system SHALL trả HTTP 404 `ORDER_NOT_FOUND` "Không tìm thấy
đơn hàng."

**FR-037**  
WHILE cộng ví, THE system SHALL gọi `CustomerRefundService.refundForCancellation()` với
`Propagation.MANDATORY`; ví SHALL được tạo trước bằng `insertIfMissing` nếu chưa tồn tại; số dư mới
SHALL được ghi vào `balance_after` của bút toán.

**FR-038**  
WHEN duyệt thành công, THE system SHALL ghi `AuditLog` action `CANCELLATION_REFUNDED`, `entityType` =
`ORDER_CANCELLATION_REFUND`, detail JSON `{"order_code":"...","refund_amount":...}` (HR-13).

**FR-039**  
WHEN duyệt thành công, THE system SHALL tạo notification cho Customer với title "Đã hoàn cọc đơn đã hủy"
và message chứa số tiền + "vào ví".

**FR-040**  
WHEN duyệt thành công, THE system SHALL trả `CancellationRefundDetailResponse` mới nhất (gọi lại
`detail()`), để frontend hiển thị trạng thái đã cập nhật mà không cần fetch thêm.

---

### Nhóm 6 — Manager từ chối (FR-041..FR-044)

**FR-041**  
WHEN Manager gọi `POST /api/manager/cancellation-refunds/{id}/reject` với `reason` hợp lệ, THE system
SHALL set `REJECTED` + `rejection_reason` + `processed_by` + `processed_at`, và SHALL **không** đụng ví,
**không** ghi transaction.

**FR-042**  
WHERE `reason` sau khi trim có độ dài < 3 hoặc > 500 ký tự, THE system SHALL trả HTTP 422
`VALIDATION_ERROR` "Lý do từ chối phải từ 3 đến 500 ký tự."

**FR-043**  
WHERE yêu cầu không còn `PENDING`, THE system SHALL trả 409 `CANCELLATION_ALREADY_PROCESSED`.

**FR-044**  
WHEN từ chối thành công, THE system SHALL ghi `AuditLog` action `CANCELLATION_REFUND_REJECTED` và tạo
notification cho Customer với title "Yêu cầu hoàn cọc bị từ chối" kèm lý do.

---

### Nhóm 7 — RBAC & Data Integrity (FR-045..FR-048)

**FR-045**  
WHILE mọi endpoint `/api/manager/cancellation-refunds/**` chạy, THE system SHALL enforce
`@PreAuthorize("hasRole('MANAGER')")`; WHERE role khác (kể cả ADMIN), SHALL trả HTTP 403 (HR-10).

**FR-046**  
WHILE endpoint `/api/customer/orders/{id}/cancellation-photos` chạy, THE system SHALL enforce
`@PreAuthorize("hasRole('CUSTOMER')")` và ownership qua `findByOrderIdAndCustomerId`.

**FR-047**  
WHERE CHECK `ck_order_cancellation_refund_terminal` bị vi phạm (ví dụ `REFUNDED` mà `refund_amount`
`NULL` hoặc `<= 0`), THE system SHALL để DB reject transaction thay vì lưu bản ghi không nhất quán.

**FR-048**  
WHILE `status = 'REFUNDED'`, THE system SHALL đảm bảo `refund_amount > 0` **và** `rejection_reason IS NULL`;
WHILE `status = 'REJECTED'`, SHALL đảm bảo `rejection_reason IS NOT NULL` **và** `refund_amount IS NULL`
— theo CHECK constraint.

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `PUT /cancel` p95 | < 800 ms |
| NFR-002 | `GET /api/manager/cancellation-refunds` p95 | < 800 ms |
| NFR-003 | `GET /{id}` (kèm ký signed URL) p95 | < 1000 ms |
| NFR-004 | `POST /{id}/refund` p95 | < 1000 ms |
| NFR-005 | Upload ảnh (1.5 MB) p95 | < 3000 ms |
| NFR-006 | Pagination | Default 20, max 100 (AC-15) |
| NFR-007 | Money precision | FLOOR scale=0, `NUMERIC(15,0)` (AC-08) |
| NFR-008 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-009 | Empty/Loading/Error states | Bắt buộc màn Manager (AC-16) |
| NFR-010 | Vietnamese diacritics | 100% text user-facing (HR-20) |
| NFR-011 | Ảnh | Không lưu BLOB/Base64; chỉ Cloudinary (AC-10) |
| NFR-012 | Concurrency | 2 Manager duyệt đồng thời → chỉ 1 thành công |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| PUT | `/api/customer/orders/{id}/cancel` | CUSTOMER | `CancelOrderRequest{reason}` | 200 `CancelOrderResponse` | `refundRequested` bool |
| POST | `/api/customer/orders/{id}/cancellation-photos` | CUSTOMER | multipart `file` | 201 (no body) | Tối đa 3 ảnh |
| GET | `/api/manager/cancellation-refunds` | MANAGER | `status`, `page`, `size` | 200 `Page<CancellationRefundListItem>` | PENDING = FIFO |
| GET | `/api/manager/cancellation-refunds/{id}` | MANAGER | — | 200 `CancellationRefundDetailResponse` | Kèm signed photo URLs |
| POST | `/api/manager/cancellation-refunds/{id}/refund` | MANAGER | — (no body) | 200 `CancellationRefundDetailResponse` | Cộng ví |
| POST | `/api/manager/cancellation-refunds/{id}/reject` | MANAGER | `RejectCancellationRequest{reason}` | 200 `CancellationRefundDetailResponse` | Không đụng tiền |

### Standard Error (ES-04)

```json
{
  "error_code": "CANCELLATION_ALREADY_PROCESSED",
  "message": "Yêu cầu hoàn cọc đã được xử lý.",
  "details": []
}
```

> Service throw `ResponseStatusException` reason dạng `"ERROR_CODE|Message"`; map sang envelope ES-04
> do `RestControllerAdvice` chung (Spec #018).

---

## Data Model

### Schema Design

Luồng hoàn cọc cần 2 bảng mới, gộp trong **một** migration vì chúng luôn đi cùng nhau (yêu cầu + ảnh
bằng chứng):

| Migration | Nội dung |
|-----------|----------|
| `V41__create_order_cancellation_refund.sql` | `order_cancellation_refund`, `order_cancellation_photo`, 2 indexes, trigger `updated_at`, 2 CHECK constraints |

Không tạo bảng ledger riêng — bút toán `REFUND` ghi vào `transaction` dùng chung (Spec #021).
Cấu trúc `order_cancellation_photo` **mirror** `dispute_photo` (V35) để hai luồng ảnh bằng chứng nhất
quán; Spec #023 sau đó mirror lại thiết kế này cho `driver_incident_photo` (V44).

### Table `order_cancellation_refund` (V41)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | `gen_random_uuid()` |
| `order_id` | `UUID` NOT NULL → `service_order(id)` | `UNIQUE` — mỗi đơn 1 yêu cầu |
| `customer_id` | `UUID` NOT NULL → `app_user(id)` | |
| `reason` | `VARCHAR(500)` NOT NULL | Lý do khách nhập khi hủy |
| `status` | `VARCHAR(20)` NOT NULL DEFAULT `'PENDING'` | `CHECK IN (PENDING, REFUNDED, REJECTED)` — AC-14 |
| `refund_amount` | `NUMERIC(15,0)` | `CHECK (NULL OR >= 0)`; NOT NULL khi REFUNDED |
| `rejection_reason` | `VARCHAR(500)` | NOT NULL khi REJECTED |
| `processed_by` | `UUID` → `app_user(id)` | Manager xử lý |
| `processed_at` | `TIMESTAMPTZ` | |
| `created_at` / `updated_at` | `TIMESTAMPTZ` NOT NULL | Trigger `trg_order_cancellation_refund_updated_at` |

**Constraints:**
- `uq_order_cancellation_refund_order` — `UNIQUE (order_id)`
- `ck_order_cancellation_refund_terminal` — toàn vẹn field theo status:
  - `PENDING`: `processed_by`, `processed_at`, `refund_amount`, `rejection_reason` đều NULL
  - `REFUNDED`: `processed_by`, `processed_at` NOT NULL, `refund_amount > 0`, `rejection_reason` NULL
  - `REJECTED`: `processed_by`, `processed_at`, `rejection_reason` NOT NULL, `refund_amount` NULL

**Indexes:**
- `idx_order_cancellation_refund_pending` — partial `WHERE status = 'PENDING'`, `(created_at ASC, id ASC)` (FIFO)
- `idx_order_cancellation_refund_customer` — `(customer_id, created_at DESC, id DESC)`

### Table `order_cancellation_photo` (V41)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `cancellation_id` | `UUID` NOT NULL → `order_cancellation_refund(id)` | |
| `url` | `VARCHAR(500)` NOT NULL | Cloudinary `secure_url` |
| `public_id` | `VARCHAR(255)` NOT NULL | Để ký URL + xoá asset |
| `uploaded_by_user_id` | `UUID` → `app_user(id)` | |
| `uploaded_at` | `TIMESTAMPTZ` NOT NULL | |

**Index:** `idx_order_cancellation_photo_cancellation` — `(cancellation_id, uploaded_at)`

**Giới hạn (enforce ở service):** tối đa 3 ảnh/yêu cầu; ≤ 1.5 MB/ảnh; JPEG/PNG/WebP theo magic number.

### Bảng liên quan (không thuộc spec này)

| Bảng | Vai trò | Spec |
|------|---------|------|
| `customer_wallet` | Đích đến tiền hoàn | #021 |
| `transaction` | Bút toán `REFUND` | #021, #013 |
| `service_order` | Nguồn `total_quote`, `commission_rate_snapshot` | #002 |

---

## Money Invariants

| ID | Invariant | Enforce ở đâu |
|----|-----------|---------------|
| MI-001 | `refund_amount = FLOOR(total_quote × commission_rate_snapshot)` | `depositOf()` (FR-032) |
| MI-002 | Mỗi đơn hoàn cọc tối đa **một lần** | `UNIQUE (order_id)` + guard `PENDING` dưới lock |
| MI-003 | REFUNDED ⇒ tồn tại đúng một `transaction(REFUND)` với `related_order_id` | Transaction boundary |
| MI-004 | REJECTED ⇒ **không** có transaction nào | FR-041 |
| MI-005 | Cộng ví + ghi bút toán trong cùng TX | `Propagation.MANDATORY` (AC-13) |
| MI-006 | Ví không âm (cộng tiền nên luôn thoả) | DB CHECK (HR-18) |
| MI-007 | `refund_amount > 0` khi REFUNDED | DB CHECK + FR-033 |
| MI-008 | Làm tròn FLOOR — công ty không bao giờ hoàn dư | AC-08 |

### Ví dụ tính cọc

```
Đơn: total_quote = 940.000 đ, commission_rate_snapshot = 0.3000
deposit = 940000 × 0.3000 = 282000.0 → FLOOR scale=0 → 282.000 đ
→ Manager duyệt → customer_wallet.balance += 282.000
→ transaction(REFUND, +282000, related_order_id, balance_after)

Đơn: total_quote = 1.000.001 đ, rate = 0.3000
deposit = 300000.3 → FLOOR → 300.000 đ  (không phải 300.001 — FLOOR bảo vệ công ty)
```

---

## Transaction Boundaries

### Khách hủy đơn (tạo yêu cầu)

```
BEGIN  -- CustomerOrderActionService.cancelOrder @Transactional
  reason = normalize(request.reason)
  order  = findOwnedOrderForUpdate(customerId, orderId)   -- lock + ownership (HR-10)
  assert order.status ∈ {PENDING, PENDING_PAYMENT, CONFIRMED}   -- IllegalStateException nếu sai
  previousStatus = order.status
  order.cancelledAt = NOW(); order.cancellationReason = reason
  transition(order → CANCELLED)                            -- audit HR-13 + notification
  log order_state_audit

  IF previousStatus == CONFIRMED:
      openForCancelledOrder(order, customerId, reason)     -- Propagation.MANDATORY
        ├─ IF existsByOrderId(order.id) → log warn, return  (idempotent)
        ├─ INSERT order_cancellation_refund(PENDING)
        ├─ notifyManagers(orderCode)   -- try/catch, lỗi không rollback
        └─ log cancellation_refund_opened
COMMIT
```

### Đính kèm ảnh

```
BEGIN
  refund = findByOrderIdAndCustomerId(orderId, customerId)  -- 404 nếu không thấy (ownership)
  assert refund.status == PENDING                            -- 409 nếu đã xử lý
  assert countByCancellationId(refund.id) < 3                -- 422 nếu đủ 3
  content = validateAndReadImage(file)                       -- 422 nếu rỗng/quá 1.5MB/sai magic number
  result  = cloudinary.upload(content, folder, type=authenticated)   -- 502 nếu lỗi
  INSERT order_cancellation_photo(url, public_id, uploaded_by)
COMMIT
```

### Manager duyệt hoàn cọc

```
BEGIN  -- ManagerCancellationRefundService.refund @Transactional
  row   = findByIdForUpdate(id)          -- lock; 404 nếu không thấy
  assert row.status == PENDING            -- 409 CANCELLATION_ALREADY_PROCESSED
  order = orderRepository.findByIdForUpdate(row.orderId)   -- lock; 404 nếu không thấy
  deposit = FLOOR(order.totalQuote × order.commissionRateSnapshot)
  assert deposit > 0                      -- 422 NO_DEPOSIT_TO_REFUND

  customerRefundService.refundForCancellation(...)   -- Propagation.MANDATORY
    ├─ walletRepository.insertIfMissing(customerId)
    ├─ wallet = findByCustomerIdForUpdate(customerId)   -- lock
    ├─ wallet.balance += deposit
    └─ INSERT transaction(REFUND, +deposit, related_order_id, balance_after)

  row.status = REFUNDED; row.refundAmount = deposit
  row.processedBy = actor.id; row.processedAt = NOW()
  INSERT AuditLog(CANCELLATION_REFUNDED)
  notify customer      -- safeNotify: try/catch
COMMIT
```

### Manager từ chối

```
BEGIN
  reason = normalizeReason(rawReason)     -- 422 nếu < 3 hoặc > 500 ký tự
  row    = findByIdForUpdate(id)
  assert row.status == PENDING             -- 409
  row.status = REJECTED; row.rejectionReason = reason
  row.processedBy = actor.id; row.processedAt = NOW()
  INSERT AuditLog(CANCELLATION_REFUND_REJECTED)
  notify customer
COMMIT   -- KHÔNG đụng ví, KHÔNG ghi transaction
```

---

## State Machine

### `order_cancellation_refund`

```
   Customer PUT /api/customer/orders/{id}/cancel
   (chỉ khi đơn đang CONFIRMED — đã cọc 30%)
                    │
                    ▼
               [PENDING] ─── Customer POST /cancellation-photos (≤ 3 ảnh)
                    │        (chỉ khi còn PENDING)
        ┌───────────┴───────────┐
        │                       │
 Manager /refund         Manager /reject
 (deposit > 0)           (reason 3–500 ký tự)
        │                       │
        ▼                       ▼
   [REFUNDED]              [REJECTED]
 ví +30% + transaction   ví KHÔNG đổi
   (terminal)              (terminal)
```

| Từ | Sang | Actor | Điều kiện | Hệ quả tiền |
|----|------|-------|-----------|-------------|
| (init) | `PENDING` | CUSTOMER | Đơn hủy từ `CONFIRMED` | Không |
| `PENDING` | `REFUNDED` | MANAGER | `deposit > 0` | Ví **+30%** + `REFUND` |
| `PENDING` | `REJECTED` | MANAGER | `reason` 3–500 ký tự | Không |

Mọi transition ngoài bảng → HTTP 409 `CANCELLATION_ALREADY_PROCESSED` (HR-05).

### Quan hệ với Order State Machine (CONTEXT §2)

```
PENDING_PAYMENT ──CUSTOMER hủy──> CANCELLED   (không tạo yêu cầu — FR-005)
CONFIRMED       ──CUSTOMER hủy──> CANCELLED   (TẠO yêu cầu hoàn cọc — FR-004)
ASSIGNED+       ──CUSTOMER hủy──> ❌ 409/IllegalState (FR-003)
```

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | — |
| 403 | `FORBIDDEN` | Sai role (kể cả ADMIN gọi endpoint Manager) | — |
| 404 | `CANCELLATION_NOT_FOUND` | Yêu cầu không tồn tại / không thuộc khách | "Không tìm thấy yêu cầu hoàn cọc." / "Không tìm thấy yêu cầu hủy đơn để đính kèm ảnh." |
| 404 | `ORDER_NOT_FOUND` | Đơn của yêu cầu không tồn tại | "Không tìm thấy đơn hàng." |
| 409 | `CANCELLATION_ALREADY_PROCESSED` | Yêu cầu không còn PENDING | "Yêu cầu hoàn cọc đã được xử lý." / "Yêu cầu hủy đơn đã được xử lý, không thể đính kèm ảnh." |
| 422 | `NO_DEPOSIT_TO_REFUND` | `deposit <= 0` | "Đơn không có tiền cọc để hoàn." |
| 422 | `TOO_MANY_PHOTOS` | Đã có 3 ảnh | "Mỗi yêu cầu hủy chỉ được đính kèm tối đa 3 ảnh." |
| 422 | `INVALID_FILE` | File rỗng / > 1.5 MB / sai magic number | "Tệp phải là ảnh JPEG, PNG hoặc WebP hợp lệ." |
| 422 | `VALIDATION_ERROR` | `reason` sai độ dài / `status` sai / `page`,`size` sai | "Lý do từ chối phải từ 3 đến 500 ký tự." |
| 502 | `CLOUDINARY_UNAVAILABLE` | Cloudinary lỗi | "Không thể tải ảnh lên. Vui lòng thử lại." |
| — | `IllegalStateException` | Hủy đơn sai trạng thái | "Chỉ có thể hủy đơn ở trạng thái đang chờ xử lý." ⚠️ xem DS-02 |

---

## Frontend Screen Contract

### `manager/cancellation-refunds.html` — "Yêu cầu hoàn cọc"

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Filter pills | "Tất cả / Chờ duyệt / Đã hoàn / Đã từ chối" → `?status=` | `ALL`/`PENDING`/`REFUNDED`/`REJECTED` |
| Bảng | `GET /api/manager/cancellation-refunds?status&page&size` | PENDING = FIFO |
| Cột | Mã đơn, Khách hàng, Lý do, Số cọc, Số đã hoàn, Trạng thái, Ngày tạo, Ngày xử lý | Tiền dùng `tnum` |
| Badge trạng thái | `PENDING` → "Chờ duyệt" (warning) · `REFUNDED` → "Đã hoàn cọc" (success) · `REJECTED` → "Đã từ chối" (danger) | Theo design-internal-reference |
| Modal chi tiết | `GET /{id}` | Lý do, ảnh (signed URL), thông tin đơn, số cọc |
| Ảnh bằng chứng | `photoUrls[]` từ detail | Signed URL — hết hạn thì load lại detail |
| Nút "Hoàn cọc" | `POST /{id}/refund` | Confirm dialog trước khi gọi |
| Nút "Từ chối" | `POST /{id}/reject` + modal nhập lý do | Validate ≥ 3 ký tự client-side |
| Loading | "Đang tải..." | AC-16 |
| Empty | "Không có yêu cầu hoàn cọc nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + nút "Tải lại" | AC-16 |

### Màn Customer liên quan

| Màn | Vai trò |
|-----|---------|
| `customer/order-detail.html` | Nút "Hủy đơn" → `PUT /cancel`; sau khi hủy hiện form đính kèm ảnh nếu `refundRequested = true` |
| `customer/my-wallet.html` | Nơi khách thấy tiền hoàn về (Spec #021) |
| `customer/notifications.html` | Thông báo duyệt/từ chối |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Ownership hủy đơn | `findOwnedOrderForUpdate(customerId, orderId)` — khách chỉ hủy đơn mình (HR-10) |
| Ownership ảnh | `findByOrderIdAndCustomerId` — khách chỉ đính kèm cho yêu cầu của mình (FR-016) |
| RBAC Manager | `/api/manager/cancellation-refunds/**` → MANAGER only; **Admin cũng bị 403** |
| Ảnh Cloudinary | `type = "authenticated"` — không truy cập được bằng URL công khai |
| Signed URL | Sinh mới mỗi lần Manager mở detail; vòng đời bảo mật dựa JWT 15 phút (AC-10) |
| Magic number | Validate trước khi gọi Cloudinary — chống upload file độc hại + tiết kiệm quota |
| Audit detail | JSON chứa `order_code` + `refund_amount`; không chứa PII khách |
| Secrets | Cloudinary credentials qua env (HR-01) |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-022-01 | Hủy đơn `CONFIRMED` → đơn CANCELLED + yêu cầu PENDING được tạo | E2E |
| AC-022-02 | Hủy đơn `PENDING_PAYMENT` → CANCELLED, **không** tạo yêu cầu | E2E |
| AC-022-03 | Hủy đơn `ASSIGNED` → lỗi, đơn không đổi | Test |
| AC-022-04 | Hủy đơn của người khác → 403/404 | Test |
| AC-022-05 | Hủy 2 lần cùng đơn → yêu cầu chỉ tạo 1 (UNIQUE order_id) | Test |
| AC-022-06 | Đính kèm ảnh thứ 4 → 422 `TOO_MANY_PHOTOS` | Test |
| AC-022-07 | Upload file `.txt` đổi đuôi `.jpg` → 422 (magic number) | Test |
| AC-022-08 | Upload ảnh 2 MB → 422 | Test |
| AC-022-09 | Đính kèm ảnh sau khi Manager đã duyệt → 409 | Test |
| AC-022-10 | Manager duyệt → ví khách +FLOOR(total×rate), transaction REFUND tồn tại | DB check |
| AC-022-11 | Manager duyệt lần 2 → 409 | Test |
| AC-022-12 | Manager từ chối → ví **không đổi**, không có transaction | DB check |
| AC-022-13 | Từ chối với lý do 2 ký tự → 422 | Test |
| AC-022-14 | Admin gọi endpoint Manager → 403 | Test RBAC |
| AC-022-15 | Filter PENDING sắp xếp FIFO (cũ nhất trước) | Manual |
| AC-022-16 | Ảnh chỉ xem được qua signed URL | Thử URL công khai → 401 |
| AC-022-17 | Màn Manager có đủ Loading/Empty/Error | Manual |
| AC-022-18 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Đơn `CONFIRMED` nhưng `driver_id != NULL`** (Manager vừa phân tài xế, khách bấm hủy cùng lúc) →
   code **vẫn tạo yêu cầu hoàn cọc** vì chỉ kiểm tra `status`. Ngược HR-14 ("khi CHUA co tai xe").
   Xem **DS-01**. Thực tế rủi ro thấp: trạng thái chuyển `CONFIRMED → ASSIGNED` khi phân tài xế, nên
   `CONFIRMED` + `driver_id != NULL` là cửa sổ rất hẹp.
2. **Khách hủy rồi không đính kèm ảnh** → yêu cầu vẫn hợp lệ; ảnh là tuỳ chọn, không bắt buộc.
3. **Manager duyệt khi đơn đã bị xoá mềm** → `findByIdForUpdate` không filter `deleted_at` → có thể vẫn
   duyệt. Rủi ro thấp (đơn CANCELLED hiếm khi bị xoá mềm).
4. **`total_quote = 0`** (đơn lỗi) → `deposit = 0` → 422 `NO_DEPOSIT_TO_REFUND`, Manager phải từ chối.
5. **Hai Manager cùng bấm "Hoàn cọc"** → `findByIdForUpdate` lock; người thứ hai nhận 409.
6. **Cloudinary hết quota giữa lúc upload** → 502; yêu cầu hoàn cọc **vẫn tồn tại**, khách thử lại sau.
7. **Notification lỗi** → log warning, giao dịch chính vẫn commit (FR-013, `safeNotify`).
8. **Khách hủy đơn `CONFIRMED` đã trả bằng ví** (Spec #021) → hoàn về ví, tiền quay lại đúng nơi xuất phát.
9. **Signed URL hết hạn khi Manager đang xem** → ảnh load fail; Manager reload detail để lấy URL mới.
10. **Yêu cầu PENDING tồn đọng nhiều ngày** → không có cơ chế auto-expire hay cảnh báo SLA. Xem DS-06.

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-022-01 | Unit | `depositOf(total=940000, rate=0.3)` | `282000` |
| TC-022-02 | Unit | `depositOf(total=1000001, rate=0.3)` | `300000` (FLOOR) |
| TC-022-03 | Unit | `depositOf(total=null, rate=null)` | `0` |
| TC-022-04 | Unit | `depositOf` với `rate = null` | Dùng default `0.3000` |
| TC-022-05 | Unit | `normalizeReason("ab")` | 422 |
| TC-022-06 | Unit | `normalizeReason(501 ký tự)` | 422 |
| TC-022-07 | Unit | `normalizeStatus("all")` | `null` (tất cả) |
| TC-022-08 | Unit | `normalizeStatus("FOO")` | 422 |
| TC-022-09 | Unit | `isJpeg`/`isPng`/`isWebp` với magic number đúng | true |
| TC-022-10 | Unit | `validateAndReadImage` file 2 MB | 422 |
| TC-022-11 | Integration | Hủy đơn CONFIRMED | Đơn CANCELLED + refund PENDING + `refundRequested=true` |
| TC-022-12 | Integration | Hủy đơn PENDING_PAYMENT | CANCELLED, không có refund row |
| TC-022-13 | Integration | Hủy đơn IN_PROGRESS | Lỗi, đơn giữ nguyên |
| TC-022-14 | Integration | `openForCancelledOrder` gọi 2 lần | Chỉ 1 row (idempotent) |
| TC-022-15 | Integration | Upload ảnh thứ 4 | 422 |
| TC-022-16 | Integration | Upload khi refund đã REFUNDED | 409 |
| TC-022-17 | Integration | Manager refund happy path | Ví tăng đúng, row REFUNDED, transaction tồn tại |
| TC-022-18 | Integration | Manager refund lần 2 | 409 |
| TC-022-19 | Integration | Manager refund đơn `total_quote=0` | 422 |
| TC-022-20 | Integration | Manager reject happy path | Row REJECTED, ví không đổi |
| TC-022-21 | Integration | Manager reject lần 2 | 409 |
| TC-022-22 | Integration | Customer gọi endpoint Manager | 403 |
| TC-022-23 | Integration | Admin gọi endpoint Manager | 403 |
| TC-022-24 | Integration | List `status=PENDING` | Sắp xếp created_at ASC |
| TC-022-25 | Integration | List `status=REFUNDED` | Sắp xếp created_at DESC |
| TC-022-26 | Concurrency | 2 thread cùng refund 1 yêu cầu | 1 thành công, 1 nhận 409 |
| TC-022-27 | Integration | `Σ transaction(REFUND) == refund_amount` | Khớp |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Chốt guard `driver_id = NULL`** — HR-14 và CONTEXT quy định hoàn cọc chỉ khi "CHUA co tai xe"; FR-004 của spec này đặc tả guard theo `status == CONFIRMED`. Cần xác nhận hai điều kiện này tương đương | Cửa sổ hẹp nhưng có thật: nếu đơn ở `CONFIRMED` mà `driver_id != NULL` (Manager vừa phân tài xế, khách bấm hủy cùng lúc) thì yêu cầu hoàn cọc vẫn mở — ngược chính sách HR-14 | Thêm guard tường minh `order.driverId == null` vào FR-004. Xem OQ-2 |
| DS-02 | **Chuẩn hoá lỗi hủy đơn sai trạng thái** — FR-003 dùng `IllegalStateException`, không có `error_code` theo ES-04 | HTTP status phụ thuộc advice chung, có thể ra 500 thay vì 409 mà HR-05 yêu cầu | Đổi sang `ResponseStatusException(CONFLICT, "INVALID_ORDER_STATUS\|...")` |
| DS-03 | **Endpoint Customer xem yêu cầu hoàn cọc của mình** — bản này chỉ Manager tra cứu được | Khách hủy xong không biết trạng thái yêu cầu, chỉ nhận được notification một chiều | `GET /api/customer/orders/{id}/cancellation-refund` |
| DS-04 | **Notification type riêng** — bản này tái dùng `ORDER_CANCELLED` cho 3 tình huống (yêu cầu mới, đã hoàn, bị từ chối) để tránh phát sinh migration | Không lọc/đếm được theo loại; icon và màu trên FE giống nhau cho 3 sự kiện khác hẳn nhau | Thêm 3 type vào `NotificationType` (Spec #020 DS-01) |
| DS-05 | Bổ sung `manager/cancellation-refunds.html` vào `SCREEN_INVENTORY.md` | Số màn hình báo cáo thiếu | Cập nhật inventory |
| DS-06 | **KPI + SLA cho hàng đợi PENDING** — bản này không có `oldestWaitingDays`/`countPendingOver24h` như hàng đợi rút tiền (Spec #021 FR-047) | Yêu cầu tồn đọng không ai phát hiện; khách chờ vô thời hạn nếu Manager quên | Thêm KPI vào `GET /api/manager/cancellation-refunds` |
| DS-07 | Chính sách soft delete cho 2 bảng mới — cả hai không có `deleted_at` | Lệch AC-09 về hình thức. Chấp nhận được: đây là bản ghi audit tài chính, không được xoá | Leader ghi nhận ngoại lệ AC-09 |
| DS-08 | **Cleanup ảnh Cloudinary** khi yêu cầu bị từ chối/đóng — AC-10 yêu cầu destroy asset | Ảnh bằng chứng tích tụ vĩnh viễn trên free tier 25 GB. Spec #024 đã có mẫu cleanup đúng để tham chiếu | `@PreRemove` hoặc job dọn định kỳ |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **`RefundRecord` có tồn tại không?** HR-14 nói "RefundRecord VAN chi tao khi COMPANY huy (khong doi)" và CONTEXT §Hủy đơn mô tả đầy đủ luồng (tạo PENDING → xin STK qua chat → chuyển khoản → PROCESSED). Nhưng **không có bảng `refund_record` trong DB** và không có code nào tạo nó. Vậy khi COMPANY hủy đơn, tiền khách đi đâu? | HR-14, Spec #021 | **High** |
| OQ-2 | Có nên thêm guard `driver_id = NULL` (DS-01) để khớp HR-14 không? | DS-01 | High |
| OQ-3 | Ảnh bằng chứng có nên **bắt buộc** ≥ 1 khi hủy không? Hiện là tuỳ chọn | — | Medium |
| OQ-4 | Yêu cầu PENDING có nên auto-refund sau N ngày Manager không xử lý? | DS-06 | Medium |
| OQ-5 | Khách có được xem trạng thái yêu cầu hoàn cọc không? (DS-03) | — | Medium |
| OQ-6 | Có hoàn cọc một phần (ví dụ 50%) khi khách hủy sát giờ không? | — | Low |

---

## Rollout Plan

**Phụ thuộc:** Spec #021 (ví khách) phải lên **trước** — không có `customer_wallet` thì không có đích
đến cho tiền hoàn. Nếu Spec #021 OQ-1 bị từ chối, spec này phải thiết kế lại đích đến (quay về
`RefundRecord` thủ công).

**Thứ tự triển khai:**

1. Chốt OQ-1 (`RefundRecord` cho COMPANY-cancel) và OQ-2 (guard `driver_id`) — cả hai ảnh hưởng nội
   dung HR-14, nên chốt trước khi code.
2. `V41` — 2 bảng + 2 index + trigger + CHECK. Không đụng bảng hiện có, không backfill.
3. Backend: `OrderCancellationRefundService` (mở yêu cầu, `Propagation.MANDATORY` trong `cancelOrder`)
   → `OrderCancellationPhotoService` → `ManagerCancellationRefundService` (duyệt/từ chối).
4. Frontend: nút "Hủy đơn" + form đính kèm ảnh ở `customer/order-detail.html` →
   `manager/cancellation-refunds.html`.

**Rủi ro cần theo dõi khi rollout:**

- CHECK `ck_order_cancellation_refund_terminal` chặn cứng bản ghi không nhất quán (FR-047) — nếu service
  set thiếu field khi chuyển trạng thái, DB reject cả transaction. Test kỹ 3 nhánh PENDING/REFUNDED/
  REJECTED trước khi lên.
- `UNIQUE (order_id)` đảm bảo mỗi đơn một yêu cầu (FR-011) — nhưng cũng nghĩa là đơn hủy rồi không thể
  mở yêu cầu thứ hai kể cả khi Manager từ chối nhầm. Chốt hành vi này với leader.
- DS-01 (guard `driver_id`): xử lý trước khi Spec #023 lên, vì sự cố tài xế cũng đưa đơn về `CONFIRMED`
  — hai luồng giao nhau ở đúng trạng thái này.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #22 Order Cancellation Refund  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | PASS | Cloudinary qua env |
| HR-02 BCrypt | N/A | |
| HR-03 IPN nguồn duy nhất | N/A | Không đụng IPN |
| HR-04 Verify HMAC | N/A | |
| HR-05 Transition sai → 409 | ⚠️ **PARTIAL** | Manager refund/reject sai trạng thái → 409 đúng. Nhưng `cancelOrder` sai trạng thái throw `IllegalStateException` (DS-02) — có thể không ra 409 |
| HR-06/07 DamageReport | N/A | Spec #010 |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | FR-006, FR-016, FR-045, FR-046 |
| HR-11 Email không rollback | PASS (tinh thần) | FR-013, `safeNotify` try/catch |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log state change | PASS | FR-007, FR-014, FR-038, FR-044 |
| HR-14 RefundRecord + ngoại lệ hoàn cọc | ⚠️ **PARTIAL** | Luồng hoàn cọc khớp ngoại lệ v1.4.0 **trừ** điều kiện `driver_id = NULL` (DS-01). Phần "RefundRecord khi COMPANY huy" **không tồn tại trong code** (OQ-1) |
| HR-15 Idempotency | PASS | `UNIQUE (order_id)` + guard PENDING dưới lock |
| HR-16 Rate limit login | N/A | |
| HR-17 Public vs Authenticated | PASS | Không endpoint public |
| HR-18 Wallet không âm | PASS | Chỉ cộng tiền; DB CHECK vẫn bảo vệ |
| HR-19 Brand identity | PASS | Forest green + amber + Be Vietnam Pro |
| HR-20 Tiếng Việt có dấu | PASS | Toàn bộ message trong 2 service này **có dấu đầy đủ** |
| HR-21 Tránh reserved words | PASS | `order_cancellation_refund`, `order_cancellation_photo` |

**Layer 1 Result:** 2 partial — HR-05 (DS-02), HR-14 (DS-01 + OQ-1).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | PASS | JPA |
| AC-05 Chat | N/A | |
| AC-06 Maps fallback | N/A | |
| AC-07 Timezone | PASS | TIMESTAMPTZ UTC |
| AC-08 BigDecimal scale=0 | PASS | FLOOR (FR-032) |
| AC-09 Soft delete | ⚠️ **EXCEPTION** | 2 bảng mới không có `deleted_at` (DS-07) |
| AC-10 Cloudinary signed upload | ⚠️ **PARTIAL** | Signed upload server-side ✅, magic number ✅, ≤1.5MB ✅, tối đa 3 ảnh ✅, signed URL ✅. **Thiếu cleanup asset** (DS-08). Signed URL **không set `expires_at` 1h** — dựa JWT 15 phút (giống known issue Cloudinary free plan) |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V41 |
| AC-13 Money audit trail | PASS (tinh thần) | Cộng ví + INSERT bút toán cùng TX, có `balance_after`. Bảng tên `transaction` — xem Spec #021 DS-05 |
| AC-14 VARCHAR + CHECK | PASS | `status` VARCHAR(20) + CHECK |
| AC-15 Pagination | PASS | Default 20, max 100 |
| AC-16 Empty/Loading/Error | PASS | Màn Manager |

**Layer 2 Result:** 2 exception (AC-09, AC-10 partial).

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/cancellation-refunds` + action sub-resource |
| ES-03 Bean Validation + 422 | PASS | Validate thủ công + 422 |
| ES-04 Error format | PARTIAL | `"CODE|Message"` map qua advice; `IllegalStateException` không theo format (DS-02) |
| ES-05 Test coverage ≥70% CORE | ⚠️ **CHƯA VERIFY** | Cần coverage cho `ManagerCancellationRefundService`, `OrderCancellationRefundService`, `OrderCancellationPhotoService` |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 19/21 PASS, 2 partial (HR-05 → DS-02, HR-14 → DS-01/OQ-1)  
Layer 2 : 14/16 PASS, 2 exception documented (AC-09, AC-10)  
Layer 3 : 6/8 PASS, ES-04 partial, ES-05 chưa verify  
Status  : **CLEARED TO SUBMIT với điều kiện** — luồng này đã được HR-14 v1.4.0 và CONTEXT §Hủy đơn
đồng bộ từ 2026-06-18, nên không bị block như Spec #021. Cần trả lời OQ-1 (RefundRecord) và quyết
DS-01 (guard `driver_id`).
================================
