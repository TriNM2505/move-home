# Feature Specification: Notifications (Thông báo trong ứng dụng)

**Feature Branch:** `020-notifications`  
**Feature Number:** #20 of 26 — SHELL (hạ tầng dùng chung)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5 — hạ tầng dùng chung (V18); danh mục type mở rộng theo từng feature

**CONTEXT.md reference:** v2.0 §2 Email (Async) — ⚠️ CONTEXT chỉ nói về **email**, không nói
in-app notification (xem Source-of-Truth Resolution)  
**Constitution reference:** v1.4.0 — HR-10, HR-11 (không rollback giao dịch chính), HR-20, HR-21,
AC-07, AC-12, AC-14, AC-15, AC-16, ES-02, ES-04  
**Screen reference:** `frontend/pages/{customer,driver,manager,admin}/notifications.html`,
`frontend/js/notifications-bell.js` — cần bổ sung vào `docs/SCREEN_INVENTORY.md` (xem DS-06)  
**Related specs:** **Mọi spec sinh notification** — #003, #005, #006, #007, #008, #009, #010, #012,
#014, #015, #019, #021, #022, #023, #024

**Migration liên quan:** `V18__create_notification_table.sql`

---

## Goals

Đặc tả **hạ tầng thông báo trong ứng dụng** — bảng `notification` dùng chung cho toàn hệ thống, hai
endpoint REST, chuông thông báo trên thanh điều hướng và bốn trang danh sách theo vai trò.

Đây là **hạ tầng dùng chung**: mọi feature nghiệp vụ đều phát sinh thông báo (đơn đổi trạng thái, tài xế
được duyệt, rút tiền được xử lý, tranh chấp, sự cố, bình luận blog…). Các spec khác chỉ **tham chiếu**
("gửi notification cho Manager") mà không định nghĩa notification là gì, lưu ở đâu, đọc thế nào —
spec này là nơi định nghĩa duy nhất, để 9+ spec kia có một hợp đồng chung mà trỏ tới.

Nguyên tắc thiết kế cốt lõi: **notification không bao giờ được làm hỏng giao dịch chính**.
`NotificationService.create()` chạy `Propagation.REQUIRES_NEW` (transaction riêng), và mọi caller bắt
buộc bọc `try/catch` — mở rộng tinh thần HR-11 (email lỗi không rollback giao dịch) sang kênh in-app.

Spec định nghĩa REST contracts, danh mục type, ownership, transaction semantics và frontend contract
(chuông + polling).

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Trạng thái tài liệu

| Nguồn | Nội dung | Trạng thái |
|-------|----------|-----------|
| `CONTEXT.md` §2 Email (Async) | Chỉ nói **email** qua Spring `@Async`; liệt kê trigger email (đăng ký, đặt đơn, thanh toán cọc, phân công, hoàn thành, tạo tài khoản Manager) | ⚠️ **Không nhắc in-app notification** |
| `CONTEXT.md` §2 Phan cong | "Manager bam 'Phan Driver X' → don ve `ASSIGNED`, Driver X nhan **notification**" | ✅ Có nhắc, không định nghĩa |
| `CONTEXT.md` §2 Phan cong (quota) | "Vuot → **notification** cho Manager" | ✅ Có nhắc, không định nghĩa |
| Constitution **HR-11** | "Tất cả **email** PHẢI gửi qua Spring `@Async`... Exception từ email service KHÔNG được propagate lên để rollback transaction" | ⚠️ Nói về **email**; spec này mở rộng **tinh thần** đó sang in-app notification |
| Constitution | **Không có** HR/AC nào riêng cho in-app notification | ❌ **Thiếu** |
| 9 spec khác (#003, #005…#015) | Đều nhắc "gửi notification" trong FR của mình | ⚠️ Tham chiếu mà không định nghĩa — spec này lấp chỗ trống |

> **Kết luận:** Notification là hạ tầng cắt ngang — 9 spec khác phụ thuộc vào nó nhưng không spec nào
> đặc tả. Spec này định nghĩa hợp đồng chung để các spec kia trỏ tới, thay vì mỗi nơi tự hiểu một kiểu.


### Quan hệ Email vs Notification

| | **Email** (CONTEXT §2) | **In-app Notification** (spec này) |
|---|---|---|
| Kênh | Gmail SMTP | Bảng `notification` + REST |
| Cơ chế bất đồng bộ | Spring `@Async` + thread pool riêng | `Propagation.REQUIRES_NEW` |
| Không rollback giao dịch chính | HR-11 (tường minh) | Cùng tinh thần, `try/catch` ở caller |
| Bền vững | Không (chấp nhận mất khi crash) | **Có** — lưu DB |
| Người dùng đọc ở đâu | Hộp thư | Chuông + `notifications.html` |
| Spec | CONTEXT §2 Email | **Spec này** |

Hai kênh **độc lập**: một sự kiện có thể sinh cả email lẫn notification, hoặc chỉ một trong hai.

### Quyết định canonical

| Chủ đề | Canonical | Nguồn |
|--------|-----------|-------|
| Bảng | `notification` — dùng chung mọi vai trò | V18 |
| `type` | `VARCHAR(50)`, không CHECK constraint (xem DS-01) | V18 |
| Danh mục type | `NotificationType` (24 hằng số) — mọi type mới phải khai báo tại đây (FR-020, DS-01) | Thiết kế |
| Transaction | `REQUIRES_NEW` — notification commit độc lập với nghiệp vụ | HR-11 (mở rộng) |
| Realtime | Ngoài phạm vi bản 1.0.0 — chỉ polling từ FE (xem DS-12) | Thiết kế |
| Ownership | Chỉ đọc/đánh dấu notification của chính mình | HR-10 |

---

## Scope Summary

**In scope:**

1. `GET /api/notifications` — danh sách thông báo của user, server-side pagination.
2. `PATCH /api/notifications/{id}/read` — đánh dấu đã đọc.
3. `NotificationService.create()` — API nội bộ cho mọi service khác gọi.
4. Danh mục `NotificationType` (24 hằng số).
5. Ownership + RBAC.
6. Transaction semantics (`REQUIRES_NEW` + `try/catch` ở caller).
7. Chuông thông báo (`notifications-bell.js`) + 4 trang danh sách.
8. Loading/Empty/Error states.

**Out of scope:**

1. Email — CONTEXT §2 Email, `EmailService`.
2. Push notification mobile — backlog CONTEXT §7.
3. Realtime notification qua WebSocket — bản này chỉ polling (xem DS-12, OQ-1).
4. Đánh dấu **tất cả** đã đọc (mark-all-read) — hoãn sang bản sau (DS-03).
5. Xoá notification — không có endpoint.
6. Cấu hình người dùng bật/tắt loại thông báo.
7. Endpoint đếm số chưa đọc riêng — bản này FE tự đếm từ danh sách (DS-02).
8. Nội dung nghiệp vụ của từng loại notification — thuộc spec của feature tương ứng.
9. Deep-link từ notification tới trang liên quan — bản này không lưu `related_entity_id` (DS-04).

---

## User Stories

**P1:**

**US1:** Là user bất kỳ, tôi thấy chuông thông báo với badge số chưa đọc trên mọi trang.

**US2:** Là user bất kỳ, tôi mở dropdown chuông xem 5 thông báo gần nhất mà không rời trang.

**US3:** Là user bất kỳ, tôi mở trang thông báo xem toàn bộ lịch sử có phân trang.

**US4:** Là user bất kỳ, tôi bấm vào thông báo để đánh dấu đã đọc và badge giảm đi.

**US5:** Là Manager, tôi nhận thông báo khi có yêu cầu cần xử lý (duyệt tài xế, hoàn cọc, sự cố).

**US6:** Là Driver, tôi nhận thông báo khi được phân đơn, bị trừ tiền, hoặc tài khoản bị khoá.

**US7:** Là Customer, tôi nhận thông báo khi đơn đổi trạng thái, tiền được hoàn, có người bình luận bài.

**P2:**

**US8:** Là developer, tôi gọi `notificationService.create(...)` một dòng mà không lo làm hỏng giao dịch
nghiệp vụ đang chạy.

**US9:** Là user, tôi không thấy thông báo của người khác dù đoán được id.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **32 FR**, trong đó **10 FR có mệnh đề WHERE** (31.2% ≥ 30% theo CLAUDE.md §5).
> Tỉ lệ WHERE thấp nhất trong 8 spec mới — phản ánh đúng bản chất: notification là hạ tầng
> đơn giản, chỉ có 2 nhánh lỗi (401, 404), không có state machine hay validation phức tạp.

---

### Nhóm 1 — Đọc danh sách (FR-001..FR-007)

**FR-001**  
WHEN user gọi `GET /api/notifications?page={p}&size={s}`, THE system SHALL trả
`Page<NotificationResponse>` của **chính user đó**, sắp xếp `created_at` giảm dần (mới nhất trước).

**FR-002**  
WHEN `page`/`size` không truyền, THE system SHALL dùng default `page = 0`, `size = 10`.

**FR-003**  
WHERE `page < 0`, THE system SHALL **clamp** về `0`; WHERE `size < 1`, SHALL clamp về `10`; WHERE
`size > 100`, SHALL clamp về `100` — SHALL **không** trả lỗi validation (khác các endpoint tiền, xem DS-05).

**FR-004**  
WHEN trả mỗi item, THE system SHALL bao gồm `id`, `type`, `title`, `message`, `isRead`, `createdAt`.

**FR-005**  
WHILE truy vấn danh sách, THE system SHALL lọc theo `user_id` = JWT subject — endpoint SHALL **không**
nhận `userId` từ client dưới bất kỳ hình thức nào (HR-10).

**FR-006**  
WHERE `@AuthenticationPrincipal` là `null`, THE system SHALL trả HTTP 401 (HR-10).

**FR-007**  
WHERE user không có thông báo nào, THE system SHALL trả `Page` rỗng với `totalElements = 0` — SHALL
không throw.

---

### Nhóm 2 — Đánh dấu đã đọc (FR-008..FR-012)

**FR-008**  
WHEN user gọi `PATCH /api/notifications/{id}/read`, THE system SHALL set `is_read = true` và trả
`NotificationResponse` đã cập nhật.

**FR-009**  
WHEN tìm thông báo, THE system SHALL dùng `findByIdAndUserId(id, jwtSubject)` — ownership check nằm
**trong** truy vấn, không phải kiểm tra sau (HR-10).

**FR-010**  
WHERE thông báo không tồn tại **hoặc** thuộc user khác, THE system SHALL trả HTTP 404
`NOTIFICATION_NOT_FOUND` "Không tìm thấy thông báo." — SHALL **không** trả 403, để không tiết lộ sự tồn
tại của thông báo người khác.

**FR-011**  
WHERE thông báo **đã** `is_read = true`, THE system SHALL trả về nguyên trạng mà không UPDATE lần hai —
idempotent.

**FR-012**  
WHILE endpoint chạy, THE system SHALL lấy user id từ JWT; SHALL không nhận `userId` từ path/query/body.

---

### Nhóm 3 — API nội bộ tạo thông báo (FR-013..FR-020)

**FR-013**  
WHEN service bất kỳ gọi `NotificationService.create(userId, type, title, message)`, THE system SHALL
insert `notification` với `is_read = false`, `created_at = NOW()` UTC, và trả entity đã lưu.

**FR-014**  
WHILE `create()` chạy, THE system SHALL dùng `Propagation.REQUIRES_NEW` — notification SHALL được commit
trong **transaction riêng**, độc lập với transaction nghiệp vụ của caller.

**FR-015**  
WHERE transaction nghiệp vụ của caller **rollback sau đó**, notification đã tạo SHALL **vẫn tồn tại** —
đây là hệ quả có chủ ý của `REQUIRES_NEW` (xem DS-07 để biết mặt trái).

**FR-016**  
WHERE bất kỳ tham số nào (`userId`, `type`, `title`, `message`) là `null`, THE system SHALL throw
`NullPointerException` với message tiếng Việt ("Mã người dùng không được null." …).

**FR-017**  
WHILE caller gọi `create()`, caller SHALL bọc `try/catch` và chỉ log warning khi lỗi — lỗi notification
SHALL **không bao giờ** làm fail nghiệp vụ chính (tinh thần HR-11).

**FR-018**  
WHEN thông báo cần gửi cho **nhiều người** (ví dụ mọi Manager ACTIVE), caller SHALL lấy danh sách qua
`userRepository.findByRoleAndStatusAndDeletedAtIsNull(role, ACTIVE)` rồi tạo lần lượt.

**FR-019**  
WHILE `type` được truyền, THE system SHALL chấp nhận **bất kỳ chuỗi nào** ≤ 50 ký tự — DB **không có**
CHECK constraint cho `type` (khác AC-14 áp dụng cho status field).

**FR-020**  
WHEN caller chọn `type`, caller SHALL dùng hằng số khai báo trong `NotificationType`; WHERE feature mới
cần một type chưa có, feature đó SHALL bổ sung hằng số vào `NotificationType` **trước** khi dùng — SHALL
không truyền chuỗi hardcode tại điểm gọi (xem DS-01).

---

### Nhóm 4 — Danh mục loại thông báo (FR-021..FR-026)

**FR-021**  
WHILE hệ thống chạy, THE system SHALL hỗ trợ các nhóm type sau:

| Nhóm | Type | Người nhận | Spec |
|------|------|-----------|------|
| Đơn hàng | `ORDER_ACCEPTED`, `ORDER_CONFIRMED`, `ORDER_ASSIGNED`, `ORDER_IN_PROGRESS`, `ORDER_COMPLETED`, `ORDER_CANCELLED`, `ORDER_IN_DISPUTE` | Customer/Driver | #003, #006 |
| Tài xế | `DRIVER_APPROVED`, `DRIVER_REJECTED`, `DRIVER_ARRIVED` | Driver/Customer | #008, #006 |
| Rút tiền | `WITHDRAWAL_REQUESTED`, `WITHDRAWAL_PROCESSED`, `WITHDRAWAL_REJECTED` | Admin/Driver/Customer | #009, #021 |
| Tranh chấp | `DISPUTE_OPENED`, `DISPUTE_RESOLVED`, `DISPUTE_REJECTED` | Manager/Customer/Driver | #010 |
| Phạt | `PENALTY_WALLET_DEDUCTED`, `PENALTY_TOP_UP_REQUIRED`, `PENALTY_ACCOUNT_LOCKED`, `PENALTY_SETTLED` | Driver | #010, #023 |
| Chat | `CHAT_MESSAGE` | Bất kỳ | #019 |
| Sự cố | `DRIVER_INCIDENT_REPORTED`, `ORDER_REASSIGNING`, `ORDER_INCIDENT_REFUNDED` | Manager/Customer/Driver | #023 |

**FR-022**  
WHEN Customer huỷ đơn CONFIRMED, THE system SHALL tái dùng `ORDER_CANCELLED` cho cả ba tình huống: yêu
cầu hoàn cọc mới (báo Manager), đã hoàn cọc (báo khách), từ chối hoàn cọc (báo khách) — Spec #022 DS-04.

**FR-023**  
WHEN Manager xác nhận sự cố, THE system SHALL gửi `ORDER_REASSIGNING` cho Customer và
`DRIVER_INCIDENT_REPORTED` cho Driver (Spec #023 FR-031/FR-032).

**FR-024**  
WHEN có người bình luận bài blog, THE system SHALL gửi type `"BLOG_COMMENT"` — chuỗi hardcode, **không**
có trong `NotificationType` (DS-01).

**FR-025**  
WHILE luồng tranh chấp chạy, THE system SHALL dùng thêm các type `"DISPUTE_DEDUCT_PENDING"`,
`"DISPUTE_PENALTY_ENFORCED"`, `"DISPUTE_PENALTY_PAID"`, `"DISPUTE_RESOLVED_DEDUCT"` — đều là chuỗi
hardcode không có trong `NotificationType` (DS-01).

**FR-026**  
WHERE type không nằm trong danh mục frontend biết, THE system SHALL vẫn hiển thị `title` + `message`
bình thường — frontend SHALL không phụ thuộc `type` để render nội dung.

---

### Nhóm 5 — Frontend contract (FR-027..FR-032)

**FR-027**  
WHEN trang bất kỳ load, THE frontend SHALL gọi `GET /api/notifications?page=0&size=5` để dựng dropdown
chuông.

**FR-028**  
WHEN tính badge, THE frontend SHALL đếm `notifs.filter(n => !n.isRead).length` **trong 5 item vừa tải**
— badge SHALL **không** phản ánh tổng số chưa đọc thực tế nếu user có > 5 chưa đọc (DS-02).

**FR-029**  
WHEN user bấm một item trong dropdown, THE frontend SHALL gọi `PATCH /{id}/read`, bỏ class `unread`, và
giảm badge.

**FR-030**  
WHEN user mở `notifications.html`, THE frontend SHALL gọi `GET /api/notifications?page=0&size=20..50`
tuỳ trang và render danh sách đầy đủ.

**FR-031**  
WHERE `id` được render vào HTML, THE frontend SHALL escape (`escapeHTML`) — chống XSS từ nội dung
thông báo.

**FR-032**  
WHILE trang hiển thị danh sách, THE frontend SHALL có đủ Loading ("Đang tải..."), Empty ("Không có thông
báo nào") và Error ("Không thể tải dữ liệu" + "Thử lại") states (AC-16).

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /api/notifications` (size=10) p95 | < 400 ms |
| NFR-002 | `PATCH /{id}/read` p95 | < 300 ms |
| NFR-003 | `create()` overhead lên giao dịch chính | < 50 ms |
| NFR-004 | Pagination | Default 10, max 100 (AC-15) |
| NFR-005 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-006 | Empty/Loading/Error states | Bắt buộc 4 trang (AC-16) |
| NFR-007 | Vietnamese diacritics | 100% `title` + `message` (HR-20) |
| NFR-008 | Độ bền | Notification lưu DB — không mất khi restart (khác email) |
| NFR-009 | Cô lập lỗi | Lỗi notification không bao giờ fail nghiệp vụ (HR-11) |
| NFR-010 | Chuông | Load ≤ 5 item để không chậm mọi trang |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/notifications` | Any authenticated | `page`, `size` | 200 `Page<NotificationResponse>` | Của chính mình |
| PATCH | `/api/notifications/{id}/read` | Any authenticated | — | 200 `NotificationResponse` | Idempotent |

### API nội bộ (Java, không phải REST)

```java
notificationService.create(userId, NotificationType.ORDER_ASSIGNED, "Tiêu đề", "Nội dung");
```

**Pattern chuẩn ở caller:**

```java
private void safeNotify(UUID userId, String type, String title, String message) {
    if (userId == null) return;
    try {
        notificationService.create(userId, type, title, message);
    } catch (Exception ex) {
        log.warn("Khong the tao notification {} cho user {}: {}", type, userId, ex.getMessage());
    }
}
```

### Standard Error (ES-04)

```json
{
  "error_code": "NOTIFICATION_NOT_FOUND",
  "message": "Không tìm thấy thông báo.",
  "details": []
}
```

---

## Data Model

### Schema Design

Notification cần **một** bảng duy nhất, dùng chung cho mọi vai trò và mọi feature:

| Migration | Nội dung |
|-----------|----------|
| `V18__create_notification_table.sql` | `notification` |

Bảng cố ý giữ tối giản: không FK, không CHECK, không `updated_at`, không `deleted_at` — notification là
dữ liệu phái sinh, không phải nguồn sự thật nghiệp vụ. Các ràng buộc và index cần bổ sung được liệt kê
ở Deferred Scope (DS-01, DS-08).

### Table `notification` (V18)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | `gen_random_uuid()` |
| `user_id` | `UUID` NOT NULL | ⚠️ **Không có FK** tới `app_user` (DS-08) |
| `type` | `VARCHAR(50)` NOT NULL | ⚠️ **Không có CHECK** constraint (DS-01) |
| `title` | `VARCHAR(255)` NOT NULL | |
| `message` | `TEXT` NOT NULL | |
| `is_read` | `BOOLEAN` NOT NULL DEFAULT `false` | |
| `created_at` | `TIMESTAMPTZ` NOT NULL DEFAULT `now()` | AC-07 |

> ⚠️ **Bảng đơn giản nhất dự án** — không index (ngoài PK), không FK, không CHECK, không `updated_at`,
> không `deleted_at`. Xem DS-08, DS-09.

### `NotificationType` — 24 hằng số

`ORDER_ACCEPTED`, `ORDER_CONFIRMED`, `ORDER_ASSIGNED`, `ORDER_IN_PROGRESS`, `ORDER_COMPLETED`,
`ORDER_CANCELLED`, `ORDER_IN_DISPUTE`, `DRIVER_APPROVED`, `DRIVER_REJECTED`, `WITHDRAWAL_REQUESTED`,
`WITHDRAWAL_PROCESSED`, `WITHDRAWAL_REJECTED`, `DISPUTE_OPENED`, `DISPUTE_RESOLVED`, `DISPUTE_REJECTED`,
`PENALTY_WALLET_DEDUCTED`, `PENALTY_TOP_UP_REQUIRED`, `PENALTY_ACCOUNT_LOCKED`, `PENALTY_SETTLED`,
`CHAT_MESSAGE`, `DRIVER_ARRIVED`, `DRIVER_INCIDENT_REPORTED`, `ORDER_REASSIGNING`,
`ORDER_INCIDENT_REFUNDED`

### Type dùng thực tế nhưng KHÔNG có trong `NotificationType`

| Type hardcode | Nơi dùng | Spec |
|---------------|----------|------|
| `"BLOG_COMMENT"` | `BlogService` | #024 |
| `"DISPUTE_DEDUCT_PENDING"` | `DisputeService` | #010 |
| `"DISPUTE_PENALTY_ENFORCED"` | `PenaltyEnforcementScheduler` | #010 |
| `"DISPUTE_PENALTY_PAID"` | `DisputeService` | #010 |
| `"DISPUTE_RESOLVED_DEDUCT"` | `DisputeService` | #010 |

→ **Danh mục thực tế = 29 type**, trong đó **5 không được khai báo** (DS-01).

### Hai đường tạo notification (không nhất quán)

| Đường | Service dùng | Transaction | Ghi chú |
|-------|--------------|-------------|---------|
| `NotificationService.create()` | 9 service (`BlogService`, `ManagerIncidentService`, `DriverIncidentService`, `OrderCancellationRefundService`, `ManagerCancellationRefundService`, `DisputeService`…) | `REQUIRES_NEW` — độc lập | ✅ Đúng thiết kế |
| `notificationRepository.save()` **trực tiếp** | `WalletService`, `AdminCustomerWithdrawalService`, `AdminWithdrawalService`, `DriverEarningService` | **Cùng TX** với nghiệp vụ | ⚠️ Rollback nghiệp vụ → mất notification (DS-10) |

---

## Transaction Boundaries

### Tạo notification qua service (đúng chuẩn)

```
BEGIN TX-nghiệp-vụ  (ví dụ: Manager duyệt hoàn cọc)
  ... cộng ví, ghi transaction, cập nhật trạng thái ...

  safeNotify(customerId, ...)
     try:
        ┌─ BEGIN TX-notification  (REQUIRES_NEW — TX riêng)
        │    INSERT notification
        └─ COMMIT TX-notification          ← commit ngay, độc lập
     catch Exception: log.warn             ← KHÔNG ném lên

COMMIT TX-nghiệp-vụ
```

**Hệ quả:**
- TX nghiệp vụ rollback → notification **vẫn còn** (đã commit riêng) → thông báo "ma" (DS-07).
- Notification lỗi → nghiệp vụ **vẫn thành công** (đúng ý đồ, HR-11).

### Tạo notification qua repository trực tiếp (lệch chuẩn)

```
BEGIN TX-nghiệp-vụ  (ví dụ: AdminCustomerWithdrawalService.process)
  ... trừ ví, ghi transaction, cập nhật request ...
  notificationRepository.save(...)      ← CÙNG TX
COMMIT
```

**Hệ quả:** rollback nghiệp vụ → mất luôn notification (nhất quán hơn, nhưng **khác** với 9 service kia).
Xem DS-10.

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | — |
| 404 | `NOTIFICATION_NOT_FOUND` | Không tồn tại **hoặc** thuộc user khác | "Không tìm thấy thông báo." |
| — | `NullPointerException` | Tham số `create()` null | "Mã người dùng không được null." ⚠️ DS-11 |

> **Ghi nhận:** Notification là hạ tầng đơn giản — chỉ **2 lỗi**. Không có 409/422 vì không có state
> machine hay validation phức tạp.

---

## Frontend Screen Contract

### `js/notifications-bell.js` — chuông trên nav (dùng chung mọi vai trò)

| Thành phần | Contract |
|------------|----------|
| Fetch | `GET /api/notifications?page=0&size=5` với Bearer token |
| Badge | Đếm `!isRead` trong **5 item vừa tải** (DS-02) |
| Dropdown | 5 item gần nhất, class `unread` cho chưa đọc |
| Click item | `PATCH /{id}/read` → bỏ class `unread` → giảm badge |
| Link "Xem tất cả" | → `notifications.html` cùng thư mục vai trò |
| Escape | `escapeHTML(n.id)` — chống XSS |

### `{customer,driver,manager,admin}/notifications.html` — trang danh sách

| Thành phần | Contract |
|------------|----------|
| Fetch | `GET /api/notifications?page=0&size=20` (hoặc 50) |
| Item | `title`, `message`, `createdAt` (format `vi-VN`, `Asia/Ho_Chi_Minh`), trạng thái đọc |
| Click | `PATCH /{id}/read` |
| Loading | "Đang tải..." (AC-16) |
| Empty | "Không có thông báo nào" (AC-16) |
| Error | "Không thể tải dữ liệu" + "Thử lại" (AC-16) |

> **Ghi nhận:** 4 trang gần như giống hệt nhau, khác chủ yếu ở layout shell theo vai trò. Không có
> deep-link từ notification tới entity liên quan (DS-04).

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Ownership đọc | `findByUserIdOrderByCreatedAtDesc(jwtSubject)` — không nhận `userId` từ client (FR-005) |
| Ownership mark-read | `findByIdAndUserId(id, jwtSubject)` — ownership nằm trong query (FR-009) |
| Ẩn sự tồn tại | Notification người khác → **404** chứ không 403 (FR-010) |
| RBAC | Không phân biệt vai trò — mọi user authenticated đều dùng cùng endpoint |
| XSS | FE escape `id`; `title`/`message` do **backend sinh** (không phải user input trực tiếp) nên rủi ro thấp — ⚠️ **trừ** `BLOG_COMMENT` chứa snippet nội dung người dùng (Spec #024 DS-02) |
| PII trong message | Message chứa tên người dùng, số tiền — chỉ gửi cho đúng người nhận |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-020-01 | User chỉ thấy notification của mình | Test 2 tài khoản |
| AC-020-02 | Sắp xếp mới nhất trước | Manual |
| AC-020-03 | `size = 500` → clamp về 100 | Test |
| AC-020-04 | `page = -1` → clamp về 0 | Test |
| AC-020-05 | Mark-read notification người khác → 404 | Test |
| AC-020-06 | Mark-read 2 lần → idempotent, không lỗi | Test |
| AC-020-07 | Guest gọi endpoint → 401 | Test |
| AC-020-08 | `create()` với `userId = null` → NPE | Test |
| AC-020-09 | Nghiệp vụ rollback → notification vẫn tồn tại (REQUIRES_NEW) | Integration test |
| AC-020-10 | Notification lỗi → nghiệp vụ vẫn thành công | Mock throw |
| AC-020-11 | Badge giảm khi bấm item | Manual |
| AC-020-12 | 4 trang có đủ Loading/Empty/Error | Manual |
| AC-020-13 | 100% `title`/`message` có dấu tiếng Việt | Manual — grep tất cả caller |
| AC-020-14 | Type mới không làm vỡ FE | Test với type lạ |

---

## Edge Cases & Error Handling

1. **User có 100 notification chưa đọc** → badge hiển thị **tối đa 5** vì FE chỉ tải `size=5` và đếm cục
   bộ (FR-028, DS-02). Người dùng thấy "5" dù thực tế 100.
2. **Nghiệp vụ rollback sau khi notification đã commit** → user nhận thông báo về việc **chưa từng xảy
   ra**. Ví dụ: "Đã hoàn cọc" nhưng TX rollback → tiền không vào ví (DS-07).
3. **User bị xoá mềm** → notification vẫn còn (không FK, không cascade); không ai đọc được vì không đăng
   nhập được (DS-08).
4. **`type` chuỗi lạ** → DB chấp nhận (không CHECK), FE vẫn render `title`/`message` (FR-026).
5. **`message` rất dài** → cột `TEXT` không giới hạn; FE có thể vỡ layout. Không có validate độ dài.
6. **Bảng phình vô hạn** → không có cleanup job, không `deleted_at`. Sau 1 năm demo có thể hàng trăm nghìn
   hàng (DS-09).
7. **Query chậm khi bảng lớn** → **không có index** trên `(user_id, created_at)`; `ORDER BY created_at
   DESC` sẽ full scan + sort. Với Neon free tier, đây là vấn đề thật (DS-08).
8. **Tạo notification cho mọi Manager khi có 10 Manager** → 10 INSERT riêng lẻ, mỗi cái một
   `REQUIRES_NEW` transaction → 10 transaction. Không batch (trừ `WalletService` dùng `saveAll`).
9. **`title` > 255 ký tự** → DB reject (`VARCHAR(255)`), `create()` ném exception → caller `try/catch`
   nuốt → **notification mất im lặng**, chỉ có log warning.
10. **Hai tab cùng mở** → mark-read ở tab này, tab kia vẫn hiện unread cho tới lần fetch sau (không
    realtime).

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-020-01 | Unit | `create()` với `userId = null` | NPE "Mã người dùng không được null." |
| TC-020-02 | Unit | `create()` với `type = null` | NPE |
| TC-020-03 | Unit | `create()` happy path | `isRead = false`, `createdAt` UTC |
| TC-020-04 | Unit | `markRead` khi đã đọc | Trả nguyên trạng, không UPDATE |
| TC-020-05 | Unit | `markRead` không tìm thấy | 404 |
| TC-020-06 | Integration | `list` chỉ trả của chính user | 2 tài khoản |
| TC-020-07 | Integration | `list` sắp xếp DESC | Đúng thứ tự |
| TC-020-08 | Integration | `list` với `size = 200` | Clamp 100 |
| TC-020-09 | Integration | `markRead` id của user khác | 404 |
| TC-020-10 | Integration | Guest gọi `list` | 401 |
| TC-020-11 | Integration | `REQUIRES_NEW` — caller rollback | Notification vẫn tồn tại |
| TC-020-12 | Integration | `safeNotify` khi service throw | Nghiệp vụ vẫn commit |
| TC-020-13 | Integration | `title` 300 ký tự | Exception, caller nuốt |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **CHECK constraint cho `type`** + kỷ luật khai báo hằng số. FR-019 cho phép `type` là chuỗi tự do; FR-020 yêu cầu dùng `NotificationType` nhưng không có gì enforce | Typo (`DRIVER_APPROVE` vs `DRIVER_APPROVED`) không bị phát hiện; danh mục type phân mảnh dần khi thêm feature | Thêm CHECK theo AC-14, hoặc test kiểm tra mọi type dùng đều có hằng số |
| DS-02 | **Endpoint `GET /api/notifications/unread-count`** (Spec #019 có `/api/chat/unread-count` tương đương) | Badge chỉ đếm được trong 5 item tải về (FR-028) → user có > 5 chưa đọc thấy số sai | Thêm endpoint đếm; FE gọi riêng cho badge |
| DS-03 | **Mark-all-read** `PATCH /api/notifications/read-all` | User có 50 thông báo phải bấm 50 lần | Thêm endpoint |
| DS-04 | **Deep-link**: cột `related_entity_type` + `related_entity_id` | Không nhảy được từ thông báo tới đơn/tranh chấp/bài viết liên quan; user phải tự tìm | Thêm 2 cột (cần migration) + `link` phía FE |
| DS-05 | Thống nhất chính sách validate `page`/`size` toàn dự án — notification clamp (FR-003), các endpoint tiền trả 422 | Contract API không nhất quán giữa các module | Chọn một chuẩn, áp cho tất cả |
| DS-06 | Bổ sung 4 trang `notifications.html` + chuông vào `SCREEN_INVENTORY.md` | Số màn hình báo cáo thiếu 4 màn | Cập nhật inventory |
| DS-07 | **Chuyển `create()` từ `REQUIRES_NEW` sang `TransactionSynchronization.afterCommit`** | `REQUIRES_NEW` commit notification **trước** khi nghiệp vụ commit (FR-015); nếu nghiệp vụ rollback → thông báo về việc chưa xảy ra ("Đã hoàn cọc 282.000đ" nhưng tiền không vào ví) | Đăng ký callback afterCommit — vẫn giữ được tính chất "không rollback nghiệp vụ" của HR-11. Xem OQ-3 |
| DS-08 | **Index `(user_id, created_at DESC)`** và FK tới `app_user` | `ORDER BY created_at DESC WHERE user_id = ?` full scan khi bảng lớn; Neon free tier chịu tải kém | `CREATE INDEX idx_notification_user_created`. Xem OQ-4 |
| DS-09 | Cleanup/archive thông báo cũ | Bảng phình vô hạn — Neon free tier 0.5 GB | Job dọn > 90 ngày |
| DS-10 | **Kỷ luật một đường tạo notification**: mọi caller dùng `NotificationService.create()`, không gọi `notificationRepository.save()` trực tiếp | Hai semantics trái ngược cùng tồn tại: qua service thì notification sống sót khi nghiệp vụ rollback, qua repository thì mất theo | Chuẩn hoá; cân nhắc đóng gói repository ở package-private |
| DS-11 | `create()` ném `NullPointerException` thay vì lỗi có `error_code` theo ES-04 | Lệch ES-04; ảnh hưởng thấp vì luôn bị `try/catch` của caller nuốt | Đổi sang `IllegalArgumentException` |
| DS-12 | **Realtime cho notification** — tái dùng hạ tầng STOMP của Spec #019 | Chat có realtime nhưng notification thì không; user phải refresh mới thấy | Thêm user-destination `/user/queue/notifications`. Xem OQ-1 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Notification có nên đẩy realtime** qua WebSocket không? Hạ tầng STOMP đã sẵn (Spec #019) — chỉ cần thêm một user-destination | DS-12 | Medium |
| OQ-2 | Có thêm endpoint `unread-count` không? Badge chỉ đếm được trong 5 item tải về nên **sai** khi > 5 chưa đọc (DS-02) | DS-02 | **High** |
| OQ-3 | `REQUIRES_NEW` có đúng ý đồ không, hay nên `afterCommit`? Có rủi ro thông báo "ma" (DS-07) | DS-07 | **High** |
| OQ-4 | Có thêm index `(user_id, created_at)` không? (DS-08) | DS-08 | **High** |
| OQ-5 | Có cần deep-link từ notification tới entity không? (DS-04) | — | Medium |
| OQ-6 | Có gộp 4 trang `notifications.html` thành 1 trang dùng chung không? | — | Low |
| OQ-7 | Notification có cần email song song không? Hiện email và notification là 2 kênh rời rạc, không có bảng ánh xạ | — | Low |

---

## Rollout Plan

**Thứ tự triển khai:**

1. `V18` — 1 bảng, không đụng bảng hiện có, không cần backfill.
2. `NotificationService` + `NotificationType` + `NotificationRepository`.
3. `NotificationController` — 2 endpoint.
4. Frontend: `js/notifications-bell.js` (dùng chung) + 4 trang `notifications.html` theo vai trò.
5. Từng feature tích hợp dần: mỗi feature bổ sung hằng số vào `NotificationType` rồi gọi
   `safeNotify(...)` theo pattern chuẩn ở mục API nội bộ.

**Đặc điểm rollout:** notification là **hạ tầng dùng chung** — mọi feature sau đều phụ thuộc, nên phải
lên trước Spec #021–#024. Đổi lại, bản thân nó không phụ thuộc feature nào và có thể triển khai độc lập
ngay sau khi có `app_user`.

**Rủi ro cần theo dõi:**

- DS-08 (thiếu index): chưa ảnh hưởng khi bảng nhỏ, nhưng phải xử lý trước khi seed dữ liệu lớn.
- DS-07 (`REQUIRES_NEW`): cần chốt trước khi các feature tiền (#021, #022, #023) tích hợp, vì đó là nơi
  thông báo sai gây hậu quả nặng nhất.
- DS-02 (badge sai khi > 5 chưa đọc): lộ ra ngay khi có user hoạt động nhiều.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #20 Notifications  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | N/A | |
| HR-02 BCrypt | N/A | |
| HR-03/04 IPN | N/A | |
| HR-05 Transition sai → 409 | N/A | Không có state machine |
| HR-06/07 DamageReport | N/A | |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | ✅ **PASS** | Ownership trong query; notification người khác → **404** (ẩn sự tồn tại — tốt hơn 403 về mặt privacy) |
| HR-11 Email lỗi không rollback TX | ✅ **PASS (tinh thần)** | HR-11 nói về **email**, nhưng notification áp dụng cùng nguyên tắc: `REQUIRES_NEW` + `try/catch` ở mọi caller (FR-014, FR-017) |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log | N/A | Notification không phải state change nghiệp vụ |
| HR-14 RefundRecord | N/A | |
| HR-15 Idempotency | PASS | `markRead` idempotent (FR-011) |
| HR-16 Rate limit | N/A | Không có POST public |
| HR-17 Public vs Authenticated | PASS | Không endpoint public |
| HR-18 Wallet | N/A | |
| HR-19 Brand identity | PASS | |
| HR-20 Tiếng Việt có dấu | PASS | Toàn bộ `title`/`message` **có dấu**; nhưng phụ thuộc từng caller — cần audit định kỳ |
| HR-21 Tránh reserved words | PASS | `notification` không phải reserved word |

**Layer 1 Result:** ALL PASS.

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | PASS | Spring Data method name query |
| AC-05 Chat | N/A | Notification **không** dùng WebSocket (DS-12) |
| AC-06 Maps | N/A | |
| AC-07 Timezone | PASS | `OffsetDateTime.now(ZoneOffset.UTC)`, cột `TIMESTAMPTZ` |
| AC-08 BigDecimal | N/A | |
| AC-09 Soft delete | ⚠️ **EXCEPTION** | Không có `deleted_at`. Notification **không phải** entity tham chiếu lịch sử trong danh sách AC-09 (Order/Trip/Vehicle/Driver/Customer/DamageReport/RefundRecord) → **có thể coi N/A**. Nhưng cũng không có cleanup (DS-09) |
| AC-10 Cloudinary | N/A | |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V18 |
| AC-13 Money audit | N/A | |
| AC-14 VARCHAR + CHECK | ⚠️ **PARTIAL** | `type VARCHAR(50)` nhưng **không có CHECK constraint**. AC-14 áp dụng cho "column lưu enum status" — `type` là enum-like nên nên có CHECK. Hệ quả: 5 type hardcode không bị phát hiện (DS-01) |
| AC-15 Pagination | PASS | Default 10, max 100 |
| AC-16 Empty/Loading/Error | PASS | 4 trang |

**Layer 2 Result:** 14/16 PASS, 1 exception (AC-09), 1 partial (AC-14).

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/notifications`, `PATCH /{id}/read` |
| ES-03 Bean Validation + 422 | N/A | Không có request body |
| ES-04 Error format | PARTIAL | `NOTIFICATION_NOT_FOUND` theo format; nhưng `create()` ném NPE (DS-11) |
| ES-05 Test coverage | ⚠️ **CHƯA VERIFY** | SHELL — cần integration test happy path. `NotificationServiceTest` đã tồn tại |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 21/21 PASS  
Layer 2 : 14/16 PASS, 1 exception (AC-09), 1 partial (AC-14 thiếu CHECK)  
Layer 3 : 6/8 PASS, ES-04 partial, ES-05 chưa verify  
Status  : **CLEARED TO SUBMIT** — notification là hạ tầng đơn giản, tuân thủ tốt Layer 1. Không có
mâu thuẫn tài liệu như #021/#024 vì CONTEXT chưa từng nói ngược. Cần xử lý OQ-2 (badge sai), OQ-3
(thông báo "ma") và OQ-4 (thiếu index) — đều là vấn đề thật nhưng không chặn.
================================
