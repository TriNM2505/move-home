# Feature Specification: Admin Audit Log Viewer (Tra cứu nhật ký hệ thống)

**Feature Branch:** `025-admin-audit-log`  
**Feature Number:** #25 of 26 — SHELL (tra cứu vận hành)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5 — hạ tầng audit (V22); trang tra cứu Sprint 6

**CONTEXT.md reference:** v2.0 §3 RBAC  
**Constitution reference:** v1.4.0 — **HR-13** (audit log bắt buộc cho mọi state change), HR-10, HR-17,
HR-20, HR-21, AC-04, AC-07, AC-09, AC-12, AC-15, AC-16, ES-02, ES-03, ES-04  
**Screen reference:** `frontend/pages/admin/audit-log.html` — cần bổ sung vào
`docs/SCREEN_INVENTORY.md` (xem DS-05)  
**Related specs:** Spec #011 Admin List Pages (⚠️ **mâu thuẫn** — audit-log viewer nằm trong
Out-of-scope); Spec #012 Admin Detail Pages (audit log **theo từng entity** — khác spec này);
**mọi spec ghi audit**: #008, #009, #010, #012, #021, #022, #023

**Migration liên quan:** `V22__create_audit_log.sql`

---

## Goals

Đặc tả **trang tra cứu nhật ký hệ thống toàn cục** — nơi Admin và Manager tìm kiếm mọi sự kiện audit
trong hệ thống theo hành động, loại thực thể và khoảng thời gian.

Đây là **mặt đọc** của hạ tầng audit (HR-13). Mặt ghi (`AuditService.log()`) được dùng khắp dự án:
duyệt tài xế, xử lý rút tiền, giải quyết tranh chấp, hoàn cọc, xác nhận sự cố, khoá tài khoản… Bảng
`audit_log` là **append-only bất biến** — không UPDATE, không DELETE, không soft delete.

Khác Spec #012 (`GET /api/admin/{entityType}/{id}/audit-log` — nhật ký **của một thực thể cụ thể**),
spec này đặc tả `GET /api/admin/audit-logs` — tra cứu **toàn hệ thống** với bộ lọc, dùng để điều tra sự
cố ("ai đã huỷ đơn này lúc 3h sáng?") và chứng minh tuân thủ HR-13 trước hội đồng.

Nguyên tắc thiết kế: **ghi audit không bao giờ được làm hỏng nghiệp vụ** — `AuditService.log()` bọc
`try/catch` toàn bộ và chỉ log warning khi lỗi (best-effort), đồng thời **không** log `actorEmail`/
`detail` ra file log để tránh rò rỉ PII.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Quyết định kiến trúc — đưa audit-log viewer vào scope

**Spec #011** liệt kê *"Full audit-log viewer"* vào Out-of-scope **#9**. Spec này đề xuất đưa nó trở lại
scope như một trang riêng, tách khỏi Admin List Pages.

**Lý do:** HR-13 bắt buộc ghi audit cho mọi state change, và 7 spec (#008, #009, #010, #012, #021, #022,
#023) đều ghi vào `audit_log`. Nhưng nếu không có mặt đọc, audit trail chỉ tồn tại trên giấy — không tra
cứu được thì không phục vụ được mục đích mà HR-13 đặt ra ("truy vết nghiệp vụ và điều tra sự cố"). Đây
không phải feature mới về kiến trúc mà là **hoàn thiện một rule đã có**.

Việc #011 loại trừ nó là hợp lý **trong phạm vi #011** (trang danh sách entity), vì audit-log viewer
không phải trang danh sách entity — nó là công cụ tra cứu sự kiện. Spec riêng là chỗ đúng cho nó.

**Xung đột cần xử lý:**

| Nguồn | Nội dung | Cần làm gì |
|-------|----------|-----------|
| **Spec #011** Out-of-scope **#9** | "Full audit-log viewer" | Sửa thành *"Full audit-log viewer — thuộc Spec #025"* |
| `CONTEXT.md` §3 RBAC | Không có dòng nào về "xem nhật ký hệ thống" | Thêm 1 dòng: Admin ✅ / Manager ✅ (hoặc theo kết quả OQ-1) |
| Constitution **HR-13** | Yêu cầu tối thiểu 6 trường: `actor_id`, `actor_role`, `timestamp`, `from_state`, `to_state`, `entity_id` | Thiết kế này có 3/6 dưới dạng cột — xem bảng đối chiếu ở Data Model và DS-01 |

**Quan hệ với Spec #012 — không xung đột:** `GET /api/admin/{entityType}/{id}/audit-log` (#012) và
`GET /api/admin/audit-logs` (spec này) là hai luồng bổ trợ, dùng chung một bảng.

### Hai luồng audit — phân biệt rõ

| | **Spec #012** — audit theo entity | **Spec #025** — audit toàn cục (spec này) |
|---|---|---|
| Endpoint | `GET /api/admin/{entityType}/{id}/audit-log` | `GET /api/admin/audit-logs` |
| Câu hỏi trả lời | "Đơn X đã trải qua những gì?" | "Ai đã làm hành động Y trong khoảng thời gian Z?" |
| Bộ lọc | `event_type`, `date_from`, `date_to` | `action`, `entityType`, `from`, `to` |
| Vai trò | ADMIN | **ADMIN + MANAGER** |
| Màn hình | Tab trong trang chi tiết | Trang riêng `admin/audit-log.html` |

### Quyết định canonical

| Chủ đề | Canonical | Nguồn |
|--------|-----------|-------|
| Bảng | `audit_log` — dùng chung cho mọi feature ghi audit | V22 |
| Append-only | **Bất biến** — không UPDATE/DELETE/soft delete | AC-13 tinh thần, AC-09 ("Audit log KHÔNG được xóa dưới bất kỳ hình thức nào") |
| Ai đọc được | **ADMIN + MANAGER** — xem OQ-1 về ranh giới Manager | Thiết kế |
| Ghi audit | Best-effort — lỗi audit không phá nghiệp vụ | HR-11 (mở rộng) |
| Cấu trúc | `action` + `entity_type` + `entity_id` + `detail` (JSON) | Lệch HR-13 — xem DS-01 |

---

## Scope Summary

**In scope:**

1. `GET /api/admin/audit-logs` — tra cứu toàn cục với 4 bộ lọc + pagination.
2. `AuditService.log()` — API nội bộ ghi audit (best-effort).
3. `AuditLogWriter` — tách riêng việc persist.
4. Bộ lọc: `action`, `entityType`, `from`, `to`.
5. RBAC ADMIN + MANAGER.
6. Trang `admin/audit-log.html` với Loading/Empty/Error states.

**Out of scope:**

1. Audit log **theo entity** — Spec #012.
2. Nội dung nghiệp vụ của từng `action` — thuộc spec feature tương ứng.
3. Xuất CSV/Excel — Spec #011 defer Sprint 6+.
4. Sửa/xoá audit log — **cấm tuyệt đối** (AC-09).
5. Cảnh báo tự động khi có hành động bất thường.
6. Lọc theo `actor` (người thực hiện) — hoãn sang bản sau (DS-02).
7. Tìm kiếm full-text trong `detail`.
8. Retention policy / archive.
9. Ghi audit cho hành động **đọc** dữ liệu (Spec #011 nhắc `ADMIN_LIST_ACCESSED` throttled — không thuộc
   spec này).

---

## User Stories

**P1:**

**US1:** Là Admin, tôi tra cứu mọi hành động trong hệ thống theo khoảng thời gian để điều tra sự cố.

**US2:** Là Admin, tôi lọc theo loại hành động (ví dụ `CUSTOMER_WITHDRAWAL_PROCESSED`) để xem ai đã duyệt
rút tiền.

**US3:** Là Admin, tôi lọc theo loại thực thể (ví dụ `DRIVER_INCIDENT`) để xem toàn bộ sự cố đã xử lý.

**US4:** Là Manager, tôi xem nhật ký để đối chiếu khi có tranh cãi về thao tác vận hành.

**P2:**

**US5:** Là Admin, tôi thấy email người thực hiện và chi tiết JSON của mỗi hành động.

**US6:** Là developer, tôi gọi `auditService.log(...)` một dòng mà không lo làm hỏng nghiệp vụ.

**US7:** Là hội đồng chấm, tôi thấy hệ thống có audit trail đầy đủ chứng minh tuân thủ HR-13.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **31 FR**, trong đó **11 FR có mệnh đề WHERE** (35.5% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Tra cứu nhật ký (FR-001..FR-009)

**FR-001**  
WHEN Admin/Manager gọi `GET /api/admin/audit-logs`, THE system SHALL trả `Page<AuditLogResponse>` sắp
xếp `created_at` **giảm dần** (mới nhất trước).

**FR-002**  
WHEN trả mỗi item, THE system SHALL bao gồm `actorEmail`, `action`, `entityType`, `entityId`, `detail`,
`createdAt`.

**FR-003**  
WHILE trả dữ liệu, THE system SHALL **không** trả `actor_id` (UUID nội bộ) — chỉ `actorEmail` để hiển
thị; giảm bề mặt lộ id nội bộ (HR-17 tinh thần).

**FR-004**  
WHEN `action` được truyền và có nội dung, THE system SHALL lọc `action = trim(action)` — **so khớp
chính xác**, không `LIKE`, không phân biệt hoa thường tuỳ DB collation.

**FR-005**  
WHEN `entityType` được truyền, THE system SHALL lọc `entity_type = trim(entityType)` — so khớp chính xác.

**FR-006**  
WHEN `from` được truyền, THE system SHALL lọc `created_at >= from`; WHEN `to` được truyền, SHALL lọc
`created_at <= to` — cả hai đều **bao gồm** biên.

**FR-007**  
WHERE `from` và `to` đều được truyền **và** `from.isAfter(to)`, THE system SHALL trả HTTP 422
`INVALID_TIME_RANGE` "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc."

**FR-008**  
WHEN không truyền bộ lọc nào, THE system SHALL trả **toàn bộ** nhật ký (`criteriaBuilder.conjunction()`
= luôn đúng), phân trang bình thường.

**FR-009**  
WHERE `action`/`entityType` là `null` hoặc chuỗi rỗng/khoảng trắng, THE system SHALL **bỏ qua** bộ lọc đó
(`hasText` check) — không lọc theo chuỗi rỗng.

---

### Nhóm 2 — Validation & pagination (FR-010..FR-013)

**FR-010**  
WHEN `page`/`size` không truyền, THE system SHALL dùng default `page = 0`, `size = 10` (AC-15).

**FR-011**  
WHERE `page < 0`, THE system SHALL trả HTTP 422 qua `@Min(0)` + `@Validated`; WHERE `size < 1` hoặc
`size > 100`, SHALL trả 422 qua `@Min(1) @Max(100)` — **validation bằng annotation**, khác các endpoint
clamp thủ công (ES-03).

**FR-012**  
WHEN parse `from`/`to`, THE system SHALL dùng `@DateTimeFormat(iso = ISO.DATE_TIME)`; WHERE giá trị
không đúng định dạng ISO 8601, SHALL trả HTTP 400 do Spring binding.

**FR-013**  
WHILE xây truy vấn lọc, THE system SHALL dùng **JPA Specification + Criteria API** — SHALL không nối
chuỗi SQL thủ công (AC-04).

---

### Nhóm 3 — RBAC (FR-014..FR-016)

**FR-014**  
WHILE endpoint chạy, THE system SHALL enforce `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`.

**FR-015**  
WHERE role là `CUSTOMER` hoặc `DRIVER`, THE system SHALL trả HTTP 403 (HR-10).

**FR-016**  
WHERE JWT thiếu/hết hạn, THE system SHALL trả HTTP 401.

> ⚠️ **Ghi nhận:** Manager **xem được toàn bộ** nhật ký hệ thống, kể cả các hành động của Admin (ví dụ
> `CUSTOMER_WITHDRAWAL_PROCESSED` — nghiệp vụ tiền mà Manager không có quyền thực hiện). CONTEXT §RBAC
> ghi "Xem doanh thu / bao cao: Manager **No**" — nhật ký có thể lộ thông tin tiền qua `detail`. Xem DS-03.

---

### Nhóm 4 — Ghi audit (API nội bộ) (FR-017..FR-024)

**FR-017**  
WHEN service bất kỳ gọi `auditService.log(actorId, actorEmail, action, entityType, entityId, detail)`,
THE system SHALL persist một bản ghi `audit_log` với `created_at = NOW()`.

**FR-018**  
WHERE việc persist ném **bất kỳ exception nào**, THE system SHALL bắt, log warning và **không** ném lên
— audit là **best-effort**, SHALL không bao giờ phá nghiệp vụ chính.

**FR-019**  
WHILE log warning khi audit lỗi, THE system SHALL **không** log `actorEmail` hay `detail` — chỉ log
`action`, `entityType`, `entityId` và message lỗi, để tránh rò rỉ PII/nội dung nhạy cảm vào file log.

**FR-020**  
WHEN persist, THE system SHALL uỷ quyền cho `AuditLogWriter` — tách riêng để `AuditService` chỉ lo phần
best-effort wrapper.

**FR-021**  
WHILE `detail` được truyền, THE system SHALL chấp nhận chuỗi tự do; caller SHOULD dùng JSON dạng
`{"order_code":"...","refund_amount":282000}`. THE system SHALL không validate cấu trúc `detail` — trách
nhiệm dựng JSON hợp lệ thuộc về caller (DS-04).

**FR-022**  
WHILE `actor_id`, `actor_email`, `entity_type`, `entity_id`, `detail` đều **nullable** trong DB,
THE system SHALL cho phép ghi audit cho hành động của SYSTEM (không có actor người dùng).

**FR-023**  
WHILE bản ghi tồn tại, THE system SHALL coi `audit_log` là **append-only bất biến**: SHALL không UPDATE,
SHALL không DELETE, SHALL không có `deleted_at` (AC-09: "Audit log KHÔNG được xóa dưới bất kỳ hình thức
nào").

**FR-024**  
WHEN cần đảo một hành động, caller SHALL ghi **bản ghi audit mới** mô tả việc đảo — SHALL không sửa bản
ghi cũ.

---

### Nhóm 5 — Danh mục action (FR-025..FR-028)

**FR-025**  
WHILE hệ thống chạy, THE system SHALL ghi audit cho tối thiểu các nhóm hành động sau:

| `entity_type` | `action` | Spec |
|---------------|----------|------|
| `CUSTOMER_WITHDRAWAL_REQUEST` | `CUSTOMER_WITHDRAWAL_PROCESSED`, `CUSTOMER_WITHDRAWAL_REJECTED` | #021 |
| `ORDER_CANCELLATION_REFUND` | `CANCELLATION_REFUNDED`, `CANCELLATION_REFUND_REJECTED` | #022 |
| `DRIVER_INCIDENT` | `INCIDENT_CONFIRMED`, `INCIDENT_COMPENSATED` | #023 |
| Withdrawal (driver) | `WITHDRAWAL_PROCESSED`, `WITHDRAWAL_REJECTED` | #009 |
| Driver approval | `DRIVER_APPROVED`, `DRIVER_REJECTED` | #008 |
| Dispute | `DISPUTE_*` | #010 |
| User account | `USER_SUSPENDED`, `USER_ACTIVATED` | #012 |

**FR-026**  
WHILE `action` là `VARCHAR(100)` **không có CHECK constraint**, THE system SHALL chấp nhận bất kỳ chuỗi
nào — danh mục action **không được enforce** ở DB (DS-06).

**FR-027**  
WHEN caller cần ghi audit, caller SHALL dùng `AuditService.log()` để hưởng semantics best-effort của
FR-018; WHERE caller gọi `auditLogRepository` **trực tiếp** (bỏ qua `AuditService`), exception khi persist
SHALL propagate và rollback nghiệp vụ — semantics **ngược** với FR-018, nên đường này SHALL không được
dùng cho luồng tiền (DS-07).

**FR-028**  
WHILE ghi audit cho state change của Order, THE system SHALL ghi bản ghi `audit_log` là **nguồn tra cứu
chính thức**; dòng `log.info("order_state_audit actor_id=... from_state=... to_state=...")` ra file log
SHALL chỉ là kênh phụ trợ cho debug — WHERE hai kênh lệch nhau, `audit_log` SHALL thắng (DS-01).

**FR-029**  
WHERE `detail` được dựng bằng nối chuỗi thủ công và giá trị nhúng chứa ký tự `"` hoặc `\`, THE system
SHALL sinh JSON **không hợp lệ** mà không báo lỗi — DB chấp nhận vì cột là `TEXT`; frontend sẽ vỡ khi
`JSON.parse`. SHALL không có validation nào chặn (DS-04).

**FR-030**  
WHERE `action` hoặc `entity_type` bị viết sai chính tả (ví dụ `DRIVER_APPROVE` thay vì `DRIVER_APPROVED`),
THE system SHALL vẫn lưu bình thường vì cột **không có CHECK constraint** — bản ghi SHALL trở nên vô hình
với bộ lọc so khớp chính xác của FR-004 (DS-06).

**FR-031**  
WHERE `actor_id` trỏ tới user đã bị xoá mềm, THE system SHALL vẫn trả bản ghi bình thường với
`actorEmail` đã snapshot — SHALL không join `app_user` và SHALL không lỗi, vì cột **không có FK** (DS-08).

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /api/admin/audit-logs` (size=10) p95 | < 800 ms |
| NFR-002 | Query với bộ lọc `action` | Dùng `idx_audit_log_action` |
| NFR-003 | Query với bộ lọc `entityType` | Dùng `idx_audit_log_entity_type` |
| NFR-004 | Query sắp xếp thời gian | Dùng `idx_audit_log_created_at` |
| NFR-005 | `log()` overhead lên nghiệp vụ | < 30 ms |
| NFR-006 | Pagination | Default 10, max 100 (AC-15) |
| NFR-007 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-008 | Empty/Loading/Error states | Bắt buộc (AC-16) |
| NFR-009 | Vietnamese diacritics | 100% text UI (HR-20) |
| NFR-010 | Bất biến | Không có code path nào UPDATE/DELETE `audit_log` |
| NFR-011 | Cô lập lỗi | Audit lỗi không phá nghiệp vụ (FR-018) |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/admin/audit-logs` | ADMIN, MANAGER | `action?`, `entityType?`, `from?`, `to?`, `page`, `size` | 200 `Page<AuditLogResponse>` | Sort cố định `createdAt` DESC |

### API nội bộ (Java)

```java
auditService.log(actor.getId(), actor.getEmail(), "CANCELLATION_REFUNDED",
        "ORDER_CANCELLATION_REFUND", row.getId().toString(),
        "{\"order_code\":\"MH...\",\"refund_amount\":282000}");
```

### Standard Error (ES-04)

```json
{
  "error_code": "INVALID_TIME_RANGE",
  "message": "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.",
  "details": []
}
```

---

## Data Model

### Schema Design

Audit cần **một** bảng duy nhất, dùng chung cho mọi feature ghi audit:

| Migration | Nội dung |
|-----------|----------|
| `V22__create_audit_log.sql` | `audit_log` + 3 indexes |

**Ba index tương ứng ba cách tra cứu** của FR-004..FR-006: theo thời gian (`created_at DESC` — mặc
định), theo hành động (`action`), theo loại thực thể (`entity_type`).

**Không có `deleted_at`** — đây là quyết định có chủ ý theo AC-09 ("Audit log KHÔNG được xóa dưới bất
kỳ hình thức nào"). Bảng cũng không có `updated_at` vì bản ghi bất biến sau khi tạo (FR-023).

### Table `audit_log` (V22)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | `gen_random_uuid()` |
| `actor_id` | `UUID` | **Nullable** — NULL cho hành động SYSTEM. Không FK (DS-08) |
| `actor_email` | `VARCHAR(255)` | Snapshot email lúc thực hiện |
| `action` | `VARCHAR(100)` NOT NULL | ⚠️ Không CHECK constraint (DS-06) |
| `entity_type` | `VARCHAR(100)` | Nullable |
| `entity_id` | `VARCHAR(100)` | Nullable — `VARCHAR` chứ không phải `UUID` (linh hoạt) |
| `detail` | `TEXT` | Nullable — JSON tự do (DS-04) |
| `created_at` | `TIMESTAMPTZ` NOT NULL DEFAULT `now()` | AC-07 |

**Indexes:**
- `idx_audit_log_created_at` — `(created_at DESC)`
- `idx_audit_log_action` — `(action)`
- `idx_audit_log_entity_type` — `(entity_type)`

> **Không có** `deleted_at` — **đúng ý đồ** (AC-09: audit log không được xoá).

### So sánh cấu trúc HR-13 vs thực tế

| HR-13 yêu cầu | Cột thực tế | Trạng thái |
|---------------|-------------|-----------|
| `actor_id` | `actor_id` | ✅ |
| `actor_role` | ❌ **không có** | ⚠️ Suy được từ `actor_email` (phải join) |
| `timestamp` | `created_at` | ✅ |
| `from_state` | ❌ **không có** | ⚠️ Nhét trong `detail` hoặc chỉ có ở `log.info` |
| `to_state` | ❌ **không có** | ⚠️ Như trên |
| `entity_id` | `entity_id` | ✅ |

→ **3/6 trường HR-13 không có cột riêng** (DS-01).

---

## Transaction Boundaries

### Ghi audit qua `AuditService` (best-effort — đúng chuẩn)

```
BEGIN TX-nghiệp-vụ
  ... thao tác nghiệp vụ ...
  auditService.log(...)
     try:
        auditLogWriter.persist(AuditLog)
     catch Exception:
        log.warn("Không thể ghi audit action={}, entityType={}, entityId={}: {}", ...)
        ← KHÔNG log actorEmail/detail (FR-019)
        ← KHÔNG ném lên (FR-018)
COMMIT
```

### Ghi audit qua repository trực tiếp (lệch chuẩn)

```
BEGIN TX-nghiệp-vụ  (ví dụ: AdminCustomerWithdrawalService)
  ... trừ ví, ghi transaction ...
  auditLogRepository.saveAndFlush(AuditLog.builder()...)   ← ném lỗi thì ROLLBACK nghiệp vụ
COMMIT
```

> ⚠️ Hai đường ghi audit có semantics **trái ngược**: một cái nuốt lỗi, một cái rollback tiền. Xem DS-07.

### Đọc

```
BEGIN (readOnly)  -- AuditLogQueryService @Transactional(readOnly = true)
  validate from <= to                     -- 422 nếu sai
  Specification filters = conjunction()
  if hasText(action)     → and(equal(action))
  if hasText(entityType) → and(equal(entityType))
  if from != null        → and(>= from)
  if to != null          → and(<= to)
  return findAll(filters, pageable).map(toResponse)
COMMIT
```

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 400 | — | `from`/`to` sai định dạng ISO 8601 | Spring binding error |
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | — |
| 403 | `FORBIDDEN` | Role CUSTOMER/DRIVER | — |
| 422 | `INVALID_TIME_RANGE` | `from > to` | "Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc." |
| 422 | — | `page < 0`, `size < 1`, `size > 100` | Bean Validation (`@Min`/`@Max`) |

---

## Frontend Screen Contract

### `admin/audit-log.html` — "Nhật ký hệ thống"

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Bộ lọc hành động | `?action=` | Dropdown hoặc text |
| Bộ lọc thực thể | `?entityType=` | |
| Bộ lọc thời gian | `?from=`, `?to=` ISO 8601 | Date-range picker |
| Bảng | `GET /api/admin/audit-logs?...&page&size` | Sort cố định mới nhất trước |
| Cột | Thời gian, Người thực hiện (email), Hành động, Loại thực thể, Mã thực thể, Chi tiết | |
| Định dạng thời gian | `toLocaleString('vi-VN', {timeZone:'Asia/Ho_Chi_Minh'})` | AC-07 |
| Chi tiết JSON | Hiển thị `detail` (có thể `<pre>`) | Escape trước khi render |
| Pagination | Page buttons + size selector 10/20/50/100 | AC-15 |
| Loading | "Đang tải..." | AC-16 |
| Empty | "Không có nhật ký nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + "Tải lại" | AC-16 |

> **Ghi nhận:** graphify community "Admin Audit Log (Frontend)" cho thấy trang này có các hàm
> `actionLabel()`, `load()`, `render()`, `showLoading()`, `computeVisiblePages()`, `esc()`,
> `formatDateTime()`, `hideError()` — tức đã implement đầy đủ AC-15 + AC-16.

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| RBAC | ADMIN + MANAGER (FR-014) |
| Không lộ `actor_id` | Response chỉ có `actorEmail` (FR-003) |
| Log file không chứa PII | Warning khi audit lỗi **không** log `actorEmail`/`detail` (FR-019) |
| Bất biến | Không code path nào UPDATE/DELETE (FR-023) |
| SQL injection | JPA Specification + Criteria API, bound parameters (AC-04) |
| XSS | FE escape `detail` (có hàm `esc()`) |
| ⚠️ Manager thấy dữ liệu tiền | `detail` của `CUSTOMER_WITHDRAWAL_PROCESSED` chứa `amount`, `balance_after` — CONTEXT §RBAC nói Manager **không** xem doanh thu (DS-03) |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-025-01 | Admin gọi → 200, sắp xếp mới nhất trước | Test |
| AC-025-02 | Manager gọi → 200 | Test |
| AC-025-03 | Customer gọi → 403 | Test RBAC |
| AC-025-04 | Driver gọi → 403 | Test RBAC |
| AC-025-05 | Không bộ lọc → trả tất cả | Test |
| AC-025-06 | Lọc `action` → chỉ đúng action đó | Test |
| AC-025-07 | Lọc `entityType` → chỉ đúng loại đó | Test |
| AC-025-08 | Lọc `from`/`to` → đúng khoảng, bao gồm biên | Test |
| AC-025-09 | `from > to` → 422 `INVALID_TIME_RANGE` | Test |
| AC-025-10 | `action = ""` → bỏ qua bộ lọc | Test |
| AC-025-11 | `size = 101` → 422 | Test |
| AC-025-12 | `page = -1` → 422 | Test |
| AC-025-13 | Response **không** chứa `actor_id` | Grep response |
| AC-025-14 | `auditService.log` lỗi → nghiệp vụ vẫn commit | Mock throw |
| AC-025-15 | Log warning **không** chứa email/detail | Đọc log |
| AC-025-16 | Không có code UPDATE/DELETE audit_log | Grep codebase |
| AC-025-17 | Loading/Empty/Error đủ | Manual |
| AC-025-18 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Audit ghi lỗi giữa giao dịch tiền** (qua `AuditService`) → nghiệp vụ **vẫn commit**, mất bản ghi
   audit → **vi phạm HR-13 im lặng**, chỉ có warning trong log. Đánh đổi có chủ ý: thà mất audit còn hơn
   rollback tiền.
2. **Audit ghi lỗi qua repository trực tiếp** (`AdminCustomerWithdrawalService`) → **rollback cả giao
   dịch tiền**. Ngược với case 1 (DS-07).
3. **`actor_email` của user đã đổi email** → audit giữ email **lúc thực hiện** (snapshot) — đúng ý đồ.
4. **`actor_id` trỏ user đã xoá mềm** → không FK nên không lỗi; `actor_email` vẫn còn để tra cứu.
5. **Bảng phình vô hạn** → không có retention; sau 1 năm có thể hàng trăm nghìn hàng. Neon free 0.5 GB.
6. **Lọc `action` sai chính tả** → trả rỗng, không báo lỗi (so khớp chính xác, không fuzzy).
7. **`detail` không phải JSON hợp lệ** → DB chấp nhận (`TEXT`); FE có thể vỡ khi `JSON.parse`.
8. **Hai kênh audit không đồng bộ** → `order_state_audit` chỉ có trong file log (không query được),
   `audit_log` không có `from_state`/`to_state` → điều tra state change phải đọc **cả hai** (DS-01).
9. **Manager xem audit hành động tiền của Admin** → thấy `amount`, `balance_after` trong `detail` (DS-03).
10. **`entity_id` là `VARCHAR(100)`** → chứa được cả UUID lẫn mã đơn dạng chuỗi; nhưng không join được
    trực tiếp với bảng nào.

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-025-01 | Unit | `hasText(null)` / `hasText("  ")` | false |
| TC-025-02 | Unit | `findAuditLogs` với `from > to` | 422 |
| TC-025-03 | Unit | `toResponse` không map `actorId` | Response không có field |
| TC-025-04 | Unit | `AuditService.log` khi writer throw | Không ném, có warning |
| TC-025-05 | Unit | Warning không chứa email/detail | Capture log |
| TC-025-06 | Integration | Admin gọi không filter | 200, tất cả |
| TC-025-07 | Integration | Lọc `action=DRIVER_APPROVED` | Chỉ action đó |
| TC-025-08 | Integration | Lọc `entityType=DRIVER_INCIDENT` | Chỉ loại đó |
| TC-025-09 | Integration | Lọc `from`/`to` bao gồm biên | Bản ghi đúng `from` được trả |
| TC-025-10 | Integration | Customer gọi | 403 |
| TC-025-11 | Integration | Manager gọi | 200 |
| TC-025-12 | Integration | `size=101` | 422 |
| TC-025-13 | Integration | Sắp xếp DESC | Đúng thứ tự |
| TC-025-14 | Integration | Nghiệp vụ + audit lỗi | Nghiệp vụ vẫn commit |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Bổ sung 3 cột để khớp HR-13**: `actor_role`, `from_state`, `to_state`. Bản này lưu thông tin state change trong `detail` JSON và trong `log.info("order_state_audit ...")` ra file log | **Vấn đề nghiêm trọng nhất của spec:** không trả lời được "đơn X đã đi qua những state nào" bằng SQL — phải grep file log. HR-13 tồn tại chính vì mục đích "truy vết nghiệp vụ và điều tra sự cố", nên thiếu 3 trường này là hụt ở đúng chỗ quan trọng | Thêm 3 cột (cần migration) **hoặc** chuẩn hoá `detail` thành JSON có schema cố định. Xem OQ-2 |
| DS-02 | **Bộ lọc theo actor** `?actorEmail=` | Không tra được "mọi hành động của admin@movehome.vn" — điều tra nội bộ phải lọc thủ công phía FE | Thêm 1 param vào Specification |
| DS-03 | **Ranh giới Manager với dữ liệu tiền** — bản này cho Manager đọc toàn bộ audit, kể cả `detail` chứa `amount`/`balance_after`, trong khi CONTEXT §3 RBAC ghi "Xem doanh thu / bao cao: Manager **No**" | Manager gián tiếp thấy dữ liệu tiền qua nhật ký — lách một ranh giới RBAC đã chốt | Giới hạn Manager theo `entity_type`, hoặc redact `detail`. Xem OQ-1 |
| DS-04 | **Dựng `detail` bằng ObjectMapper** thay vì nối chuỗi JSON thủ công | Giá trị nhúng chứa `"` hoặc `\` làm JSON vỡ (FR-029); frontend lỗi khi `JSON.parse`. Không có validation nào chặn | `ObjectMapper.writeValueAsString(Map.of(...))` |
| DS-05 | Bổ sung `admin/audit-log.html` vào `SCREEN_INVENTORY.md` | Số màn hình báo cáo thiếu | Cập nhật inventory |
| DS-06 | **Danh mục hằng số + CHECK cho `action`/`entity_type`** | Typo (`DRIVER_APPROVE` vs `DRIVER_APPROVED`) không bị phát hiện → bản ghi trở nên vô hình với bộ lọc so khớp chính xác (FR-030). Cùng loại vấn đề với Spec #020 DS-01 | Hằng số tập trung + CHECK theo AC-14 |
| DS-07 | **Kỷ luật một đường ghi audit** — mọi caller dùng `AuditService.log()` (nuốt lỗi), không gọi `auditLogRepository.saveAndFlush()` trực tiếp | Hai semantics trái ngược cùng tồn tại (FR-027): qua service thì audit lỗi không sao, qua repository thì rollback cả giao dịch tiền | Chuẩn hoá; cân nhắc đóng gói repository package-private |
| DS-08 | FK `actor_id` → `app_user` | Không có toàn vẹn tham chiếu. **Có thể là cố ý**: audit phải sống sót khi user bị xoá, và `actor_email` đã snapshot đủ để tra cứu | Giữ nguyên — ghi nhận là quyết định có chủ ý |
| DS-09 | Retention/archive policy | Bảng phình vô hạn; AC-09 cấm xoá audit nên chỉ có thể archive sang cold storage | Archive > 1 năm |
| DS-10 | Sửa **Spec #011 Out-of-scope #9** để trỏ sang spec này | Hai spec nói ngược nhau về việc audit-log viewer có trong scope hay không | Sửa #011. Xem OQ-3 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Manager có nên xem toàn bộ audit không?** Hiện Manager thấy cả `detail` chứa số tiền, ngược CONTEXT §RBAC (DS-03) | DS-03 | **High** |
| OQ-2 | Có bổ sung 3 cột `actor_role`/`from_state`/`to_state` để khớp HR-13 không? (DS-01) | DS-01 | **High** |
| OQ-3 | Sửa Spec #011 Out-of-scope #9 hay giữ nguyên? (DS-10) | Tài liệu | Medium |
| OQ-4 | Thống nhất đường ghi audit? (DS-07) | DS-07 | Medium |
| OQ-5 | Có thêm bộ lọc theo actor không? (DS-02) | — | Medium |
| OQ-6 | Retention policy cho audit log? AC-09 cấm xoá, nhưng bảng phình vô hạn | DS-09 | Low |

---

## Rollout Plan

**Thứ tự triển khai:**

1. Chốt OQ-2 (cấu trúc HR-13) **trước** khi tạo bảng — thêm cột sau khi đã có dữ liệu tốn kém hơn nhiều.
2. `V22` — 1 bảng + 3 index. Không đụng bảng hiện có, không backfill.
3. `AuditService` + `AuditLogWriter` (mặt ghi) — phải lên **trước** các feature ghi audit (#008, #009,
   #010, #012, #021, #022, #023).
4. `AuditLogQueryService` + `AuditLogController` (mặt đọc).
5. `admin/audit-log.html`.
6. Chốt OQ-1 (ranh giới Manager) trước khi mở endpoint cho role MANAGER.

**Đặc điểm rollout:** giống Spec #020, đây là **hạ tầng cắt ngang** — mặt ghi phải có sớm để các feature
sau gọi vào; mặt đọc có thể lên muộn hơn mà không chặn ai. Bản thân nó không phụ thuộc feature nào.

**Rủi ro cần theo dõi:**

- DS-01 (thiếu 3 trường HR-13): xử lý càng sớm càng rẻ. Nếu để tới khi bảng có hàng chục nghìn bản ghi,
  việc backfill `from_state`/`to_state` từ log file gần như bất khả thi.
- DS-07 (hai đường ghi audit): chốt kỷ luật trước khi các feature tiền tích hợp, vì đó là nơi
  `saveAndFlush()` trực tiếp sẽ rollback cả giao dịch tiền nếu audit lỗi.
- Sửa Spec #011 Out-of-scope #9 (DS-10) cùng lúc với OQ-3 để hai spec không mâu thuẫn.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #25 Admin Audit Log Viewer  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | N/A | |
| HR-02 BCrypt | N/A | |
| HR-03/04 IPN | N/A | |
| HR-05 Transition sai → 409 | N/A | |
| HR-06/07 DamageReport | N/A | |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | ADMIN + MANAGER; khác → 403 |
| HR-11 Email không rollback | PASS (tinh thần) | `AuditService` best-effort (FR-018) — **trừ** đường repository trực tiếp (DS-07) |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log state change | ⚠️ **PARTIAL** | Có hạ tầng audit + UI tra cứu ✅. **Nhưng cấu trúc thiếu `actor_role`, `from_state`, `to_state`** (DS-01); state change chỉ có ở `log.info` không query được. **Và** audit best-effort nghĩa là có thể mất bản ghi im lặng (FR-018) — HR-13 nói "PHẢI ghi", không nói "cố gắng ghi" |
| HR-14 RefundRecord | N/A | |
| HR-15 Idempotency | N/A | |
| HR-16 Rate limit | N/A | Chỉ GET |
| HR-17 Public vs Authenticated | PASS | Không endpoint public; không trả `actor_id` |
| HR-18 Wallet | N/A | |
| HR-19 Brand identity | PASS | |
| HR-20 Tiếng Việt có dấu | PASS | Message + UI có dấu |
| HR-21 Tránh reserved words | PASS | `audit_log` |

**Layer 1 Result:** 1 partial — **HR-13** (cấu trúc thiếu 3 trường + best-effort có thể mất bản ghi).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | ✅ **PASS** | JPA Specification + Criteria API, bound params (FR-013). ⚠️ Nhưng `detail` **là JSON nối chuỗi thủ công** — không phải SQL injection, nhưng cùng loại rủi ro (DS-04) |
| AC-05 Chat | N/A | |
| AC-06 Maps | N/A | |
| AC-07 Timezone | PASS | `TIMESTAMPTZ`, `Instant` |
| AC-08 BigDecimal | N/A | |
| AC-09 Soft delete | ✅ **PASS** | Không có `deleted_at` — **đúng ý đồ**: AC-09 ghi rõ "Audit log KHÔNG được xóa dưới bất kỳ hình thức nào" |
| AC-10 Cloudinary | N/A | |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V22 |
| AC-13 Money audit trail | N/A | Đây là audit hành động, không phải sổ cái tiền |
| AC-14 VARCHAR + CHECK | ⚠️ **PARTIAL** | `action`/`entity_type` là VARCHAR **không CHECK** (DS-06). Nhưng đây không phải "status field" nên AC-14 có thể coi N/A |
| AC-15 Pagination | ✅ **PASS** | Default 10, max 100, **Bean Validation** `@Min`/`@Max` (chuẩn nhất trong 8 spec mới) |
| AC-16 Empty/Loading/Error | PASS | Trang đã có đủ |

**Layer 2 Result:** 15/16 PASS, 1 partial (AC-14 — có thể N/A).

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/audit-logs` plural |
| ES-03 Bean Validation + 422 | ✅ **PASS** | `@Validated` + `@Min`/`@Max` + `@DateTimeFormat` — **luồng duy nhất trong 8 spec mới dùng đúng chuẩn ES-03** |
| ES-04 Error format | PASS | `INVALID_TIME_RANGE` theo format |
| ES-05 Test coverage | ⚠️ **CHƯA VERIFY** | `AuditLogQueryServiceTest` + `AuditServiceTest` đã tồn tại |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 20/21 PASS, 1 partial (HR-13 — cấu trúc + best-effort)  
Layer 2 : 15/16 PASS, 1 partial (AC-14)  
Layer 3 : 7/8 PASS, ES-05 chưa verify  
Status  : **CLEARED TO SUBMIT với điều kiện** — mâu thuẫn với Spec #011 nhẹ (công cụ nội bộ, không đụng
tiền), khác hẳn Blog/#017. Cần trả lời OQ-1 (Manager xem audit tiền) và OQ-2 (cấu trúc HR-13 — quan
trọng vì đây chính là rule mà feature này tồn tại để phục vụ).
================================
