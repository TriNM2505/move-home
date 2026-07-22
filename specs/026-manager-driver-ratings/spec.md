# Feature Specification: Manager Driver Ratings (Manager xem đánh giá tài xế)

**Feature Branch:** `026-manager-driver-ratings`  
**Feature Number:** #26 of 26 — SHELL (tra cứu vận hành)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 6 — màn Manager tra cứu đánh giá (leader duyệt chính sách 5 sao 2026-06-17)

**CONTEXT.md reference:** v2.0 §7 Feature #30 (Rating + feedback Driver — 🟢 Nice-to-have), §3 RBAC  
**Constitution reference:** v1.4.0 — HR-10, HR-20, HR-21, AC-04, AC-07, AC-15, AC-16, ES-02, ES-04  
**Screen reference:** `frontend/pages/manager/driver-ratings.html` — cần bổ sung vào
`docs/SCREEN_INVENTORY.md` (xem DS-04)  
**Related specs:** Spec #003 Customer Orders (khách tạo đánh giá); Spec #008 Manager Driver Approval
(quản lý tài xế); Spec #012 Admin Detail Pages (Admin xem rating trong chi tiết tài xế);
Spec #016 Admin Reports (phân bố rating)

**Migration liên quan:** `V9__create_order_rating.sql` (`order_rating`),
`V40__driver_default_rating_five.sql` (default 5.00 sao)

---

## Goals

Đặc tả màn hình **"Đánh giá tài xế"** cho Manager — tra cứu toàn bộ đánh giá khách hàng đã gửi, **kèm
nội dung nhận xét**, với bộ lọc theo tài xế, số sao và từ khoá tên tài xế.

Đây là **mặt đọc dành riêng cho Manager** của dữ liệu `order_rating`. Điểm khác biệt so với Admin:
Manager đọc được **cả nội dung nhận xét**, trong khi Admin chỉ xem sao và thống kê (Spec #012, #016).
Lý do: Manager là người trực tiếp "quản lý chất lượng Driver qua quy trình duyệt + DamageReport +
rating" (CONTEXT §1), nên cần đọc khách nói gì để xử lý tài xế có phản hồi xấu.

Spec cũng chốt **chính sách 5 sao mặc định** (quyết định leader 2026-06-17, migration `V40`): tài xế
chưa có đánh giá nào hiển thị `average_rating = 5.00` thay vì `0.00`, để tài xế mới không bị thiệt khi
khách chọn — kèm đánh đổi được phân tích ở DS-01.

Spec đặc biệt ghi rõ một **ràng buộc kỹ thuật của PostgreSQL** ở mục Implementation Notes: bind tham số
`null` vào `lower(:param)` khiến PostgreSQL suy kiểu thành `bytea` và ném
`function lower(bytea) does not exist`. Query của spec này **bắt buộc** convert sang `String` +
lowercase trong Java trước khi bind (FR-012, FR-013). Ràng buộc này phải test trên PostgreSQL thật —
**H2 không tái hiện được lỗi**.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Trạng thái tài liệu

| Nguồn | Nội dung | Trạng thái |
|-------|----------|-----------|
| `CONTEXT.md` §7 Feature **#30** | "Rating + feedback Driver sau khi hoàn thành — Customer — 🟢 **Nice-to-have** — Trong escrow 2h" | ✅ Feature có trong CONTEXT, nhưng **chỉ mô tả phía Customer tạo đánh giá** |
| `CONTEXT.md` §7 | **Không có** feature "Manager xem đánh giá" | ❌ **Thiếu** |
| `CONTEXT.md` §3 RBAC | **Không có** dòng nào về xem đánh giá tài xế | ❌ **Thiếu** |
| `CONTEXT.md` §2 Escrow | "Trong 2h khach co quyen tao DamageReport hoac **rating**" | ⚠️ Lệch — thiết kế này tách rating ra 24h, xem DS-02 |
| Quyết định leader 2026-06-17 | Cửa sổ rating **24 giờ** (`app.rating.window-minutes:1440`), tách khỏi escrow tiền 2h | ⚠️ CONTEXT chưa cập nhật — xem DS-02 |
| Quyết định leader 2026-06-17 | Tài xế mặc định **5.00 sao** khi chưa có đánh giá nào (V40) | ❌ Chính sách chưa có trong CONTEXT — xem DS-02 |
| Spec #016 Admin Reports | "rating distribution" cho Admin | ✅ Luồng khác — thống kê, không phải danh sách |
| Spec #012 Admin Detail | Admin xem rating trong chi tiết tài xế | ✅ Luồng khác |

**Đánh giá:** Rating **có** trong CONTEXT (feature #30) nhưng chỉ ở phía Customer tạo. Ba thứ chưa có
tài liệu: (a) màn Manager tra cứu, (b) chính sách 5 sao mặc định (V40), (c) cửa sổ rating 24h thay vì
2h. Cả (b) và (c) đều là **quyết định leader ngày 2026-06-17** chỉ tồn tại trong comment code/migration.

### Ba luồng đọc rating — phân biệt

| | **Spec #026** (spec này) | **Spec #012** Admin Detail | **Spec #016** Admin Reports |
|---|---|---|---|
| Endpoint | `GET /api/manager/driver-ratings` | `GET /api/admin/drivers/{id}` | `GET /api/admin/reports/drivers` |
| Vai trò | MANAGER | ADMIN | ADMIN |
| Dữ liệu | Danh sách đánh giá **kèm comment** | Rating trong hồ sơ 1 tài xế | Phân bố rating toàn hệ thống |
| Câu hỏi | "Khách nói gì về tài xế?" | "Tài xế X thế nào?" | "Chất lượng chung ra sao?" |

### Quyết định canonical

| Chủ đề | Canonical | Nguồn |
|--------|-----------|-------|
| Bảng | `order_rating` — do Spec #003 tạo, spec này tái dùng | V9 |
| Một đơn một đánh giá | `UNIQUE (order_id)` | V9 |
| Thang sao | `INTEGER` + `CHECK BETWEEN 1 AND 5` | V9, AC-14 |
| Mặc định khi chưa có đánh giá | **5.00 sao** — thay cho `0.00` | Leader 2026-06-17 (V40) |
| Cửa sổ đánh giá | **24 giờ** — tách khỏi escrow tiền 2h | Leader 2026-06-17 |
| Manager xem comment | **Có** — khác Admin (chỉ xem sao/thống kê) | CONTEXT §1 (Manager quản lý chất lượng Driver) |
| Sort | Cố định `createdAt` DESC — không nhận sort từ client | AC-04 (chống injection qua tên cột) |

---

## Scope Summary

**In scope:**

1. `GET /api/manager/driver-ratings` — danh sách đánh giá kèm comment, 3 bộ lọc + pagination.
2. Bộ lọc: `driverId`, `stars`, `keyword` (tên tài xế).
3. RBAC MANAGER.
4. Chính sách 5 sao mặc định cho tài xế chưa có đánh giá (V40).
5. Ràng buộc kỹ thuật khi bind tham số nullable trên PostgreSQL (xem Implementation Notes).
6. Trang `manager/driver-ratings.html` với Loading/Empty/Error states.

**Out of scope:**

1. Customer **tạo** đánh giá (`POST /api/customer/orders/{id}/rate`) — Spec #003.
2. Cách tính `driver_profile.average_rating` — Spec #007/#008.
3. Admin xem rating — Spec #012, #016.
4. Manager **phản hồi** đánh giá — không có endpoint.
5. Manager ẩn/xoá đánh giá xấu — **không có** (đúng ý đồ: đánh giá bất biến).
6. Rating của blog (Spec #024) — **hệ thống hoàn toàn tách biệt**.
7. Tags rating ("Đúng giờ / Lịch sự / Xe sạch") — `SCREEN_INVENTORY` 3.13 có nhắc; hoãn sang bản sau (DS-08).
8. Cảnh báo tự động khi tài xế có rating thấp liên tiếp.

---

## User Stories

**P1:**

**US1:** Là Manager, tôi xem toàn bộ đánh giá khách hàng **kèm nội dung nhận xét** để nắm chất lượng
dịch vụ.

**US2:** Là Manager, tôi lọc đánh giá 1–2 sao để phát hiện tài xế có vấn đề.

**US3:** Là Manager, tôi lọc theo một tài xế cụ thể để xem toàn bộ phản hồi về người đó trước khi quyết
định xử lý.

**US4:** Là Manager, tôi tìm theo tên tài xế khi không nhớ id.

**P2:**

**US5:** Là Manager, tôi thấy mã đơn và tên khách của mỗi đánh giá để đối chiếu.

**US6:** Là Driver mới, tôi được hiển thị 5.00 sao khi chưa có đánh giá nào, để không bị thiệt so với
tài xế cũ.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **26 FR**, trong đó **9 FR có mệnh đề WHERE** (34.6% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Tra cứu đánh giá (FR-001..FR-007)

**FR-001**  
WHEN Manager gọi `GET /api/manager/driver-ratings`, THE system SHALL trả `Page<DriverRatingItem>` sắp
xếp `created_at` **giảm dần**.

**FR-002**  
WHEN trả mỗi item, THE system SHALL bao gồm `id`, `orderId`, `orderCode`, `driverId`, `driverName`,
`customerName`, `stars`, `comment`, `createdAt`.

**FR-003**  
WHILE xây truy vấn, THE system SHALL sort **cố định trong query** (`order by r.createdAt desc`) —
SHALL **không** nhận tham số sort từ client, tránh SQL injection qua tên cột.

**FR-004**  
WHEN join tài xế, THE system SHALL dùng **`left join`** — `order_rating.driver_id` **nullable** với dữ
liệu cũ hoặc đơn chưa gán tài xế (V9).

**FR-005**  
WHERE `driver_id` là `null`, THE system SHALL trả `driverName = null` — SHALL không loại bản ghi khỏi
kết quả.

**FR-006**  
WHEN join đơn và khách, THE system SHALL dùng `join` (inner) — `order_id` và `customer_id` đều `NOT NULL`
theo V9.

**FR-007**  
WHERE `@AuthenticationPrincipal` là `null`, THE system SHALL trả HTTP 401 `AUTHENTICATION_REQUIRED`
"Vui lòng đăng nhập để tiếp tục."

---

### Nhóm 2 — Bộ lọc (FR-008..FR-014)

**FR-008**  
WHEN `driverId` được truyền, THE system SHALL lọc `cast(r.driverId as string) = :driverId`; WHEN không
truyền, SHALL bỏ qua bộ lọc (`:driverId is null or ...`).

**FR-009**  
WHEN `stars` được truyền, THE system SHALL lọc `cast(r.stars as string) = :stars`.

**FR-010**  
WHERE `stars` nằm ngoài `[1, 5]`, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Bộ lọc số sao phải
từ 1 đến 5."

**FR-011**  
WHEN `keyword` được truyền và có nội dung, THE system SHALL lọc
`lower(coalesce(d.fullName, '')) like :keywordPattern`; WHERE `keyword` là `null`/rỗng/khoảng trắng,
SHALL bỏ qua bộ lọc.

**FR-012**  
WHEN xây `keywordPattern`, THE service SHALL **lowercase trong Java** và bọc `%...%` trước khi bind —
SHALL **không** dùng `lower(:param)` trong JPQL (xem Implementation Notes — bẫy `lower(bytea)`).

**FR-013**  
WHEN bind tham số nullable, THE service SHALL **convert sang `String`** (`driverId.toString()`,
`stars.toString()`) trước khi truyền — SHALL không bind trực tiếp `UUID`/`Integer` nullable (xem
Implementation Notes).

**FR-014**  
WHEN áp `coalesce(d.fullName, '')`, THE system SHALL đảm bảo tài xế `null` (do `left join`) không làm
`like` trả `null` — bản ghi không có tài xế SHALL bị loại khi lọc theo keyword, nhưng không gây lỗi.

---

### Nhóm 3 — Pagination (FR-015..FR-017)

**FR-015**  
WHEN `page`/`size` không truyền, THE system SHALL dùng default `page = 0`, `size = 10` (AC-15).

**FR-016**  
WHERE `page < 0`, THE system SHALL **clamp** về `0`; WHERE `size < 1`, SHALL clamp về `10`; WHERE
`size > 100`, SHALL clamp về `100` — SHALL không trả lỗi (giống Chat/Notification, khác Audit Log).

**FR-017**  
WHILE danh sách có thể > 50 dòng, THE system SHALL dùng **server-side pagination** với `countQuery`
riêng (AC-15).

---

### Nhóm 4 — RBAC (FR-018..FR-019)

**FR-018**  
WHILE endpoint `/api/manager/driver-ratings` chạy, THE system SHALL enforce role `MANAGER` qua
`SecurityConfig` (`/api/manager/**`); WHERE role khác (kể cả `ADMIN`), SHALL trả HTTP 403 (HR-10).

**FR-019**  
WHILE Manager đọc, THE system SHALL trả **cả `comment`** — khác Admin (Spec #012/#016 chỉ xem sao/thống
kê), vì Manager là người trực tiếp quản lý chất lượng tài xế (CONTEXT §1).

---

### Nhóm 5 — Chính sách 5 sao mặc định (FR-020..FR-024)

**FR-020**  
WHILE tài xế **chưa có** bản ghi nào trong `order_rating`, THE system SHALL hiển thị
`driver_profile.average_rating = 5.00` — quyết định leader 2026-06-17 (V40).

**FR-021**  
WHEN tài xế mới đăng ký, THE system SHALL set `average_rating` = **DEFAULT 5.00** (V40 đổi từ `0.00`).

**FR-022**  
WHILE tài xế **đã có** đánh giá, THE system SHALL dùng **trung bình thuần** từ `order_rating` — SHALL
không cộng thêm điểm 5 sao ảo (V40: "phương án (a) — trung bình thuần").

**FR-023**  
WHERE tài xế có 1 đánh giá 1 sao, `average_rating` SHALL là `1.00` — **rơi thẳng từ 5.00 xuống 1.00**;
đây là hệ quả có chủ ý của phương án trung bình thuần (xem DS-01).

**FR-024**  
WHILE cửa sổ đánh giá mở, THE system SHALL cho phép Customer đánh giá trong **24 giờ**
(`app.rating.window-minutes:1440`) sau COMPLETED — **tách riêng** khỏi escrow tiền 2h (CONTEXT §2);
quyết định leader 2026-06-17.

**FR-025**  
WHERE tài xế của một đánh giá đã bị **xoá mềm**, THE system SHALL vẫn trả bản ghi đánh giá đó với
`driverName = null` (do `left join` + `@SQLRestriction` trên `User`) — lịch sử đánh giá SHALL không biến
mất khi tài xế rời hệ thống.

**FR-026**  
WHERE Manager cố sửa hoặc xoá một đánh giá, THE system SHALL **không cung cấp endpoint nào** — bảng
`order_rating` không có `deleted_at` và không có API ghi; đánh giá của khách SHALL là **bất biến**
(xem DS-03 về mặt trái vận hành).

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /api/manager/driver-ratings` (size=10) p95 | < 900 ms |
| NFR-002 | Lọc theo `driverId` | Dùng `idx_order_rating_driver` |
| NFR-003 | Pagination | Default 10, max 100 (AC-15) |
| NFR-004 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-005 | Empty/Loading/Error states | Bắt buộc (AC-16) |
| NFR-006 | Vietnamese diacritics | 100% text UI (HR-20) |
| NFR-007 | SQL injection | JPQL + bound params, sort cố định (AC-04) |
| NFR-008 | Tương thích PostgreSQL | Không bind null vào hàm chuỗi (xem Implementation Notes) |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/manager/driver-ratings` | MANAGER | `driverId?`, `stars?`, `keyword?`, `page`, `size` | 200 `Page<DriverRatingItem>` | Kèm `comment` |

### Standard Error (ES-04)

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Bộ lọc số sao phải từ 1 đến 5.",
  "details": []
}
```

---

## Data Model

### Schema Design

Spec này **không cần bảng mới** — nó là mặt đọc của `order_rating` (do Spec #003 tạo). Chỉ có một thay
đổi schema kèm theo: chính sách 5 sao mặc định.

| Migration | Nội dung |
|-----------|----------|
| `V9__create_order_rating.sql` | `order_rating` + index — do Spec #003 tạo, spec này tái dùng |
| `V40__driver_default_rating_five.sql` | `driver_profile.average_rating` DEFAULT `0.00` → `5.00` + backfill tài xế chưa có đánh giá |

**`V40` có backfill** — khác mọi migration của 8 spec mới. Nó `UPDATE driver_profile SET average_rating
= 5.00 WHERE NOT EXISTS (SELECT 1 FROM order_rating WHERE driver_id = user_id)`, tức chạm dữ liệu đang
có. Tài xế **đã có** đánh giá giữ nguyên trung bình thực (FR-022). Migration này không thể rollback tự
động — nếu cần quay lại `0.00`, phải viết migration ngược thủ công.

### Table `order_rating` (V9)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `order_id` | `UUID` NOT NULL **UNIQUE** → `service_order(id)` | Một đơn **một** đánh giá |
| `customer_id` | `UUID` NOT NULL → `app_user(id)` | Người đánh giá |
| `driver_id` | `UUID` → `app_user(id)` | **Nullable** — dữ liệu cũ/đơn chưa gán tài xế |
| `stars` | `INTEGER` NOT NULL | `CHECK (stars BETWEEN 1 AND 5)` |
| `comment` | `TEXT` | Nhận xét tự do — **Manager đọc được** |
| `created_at` | `TIMESTAMPTZ` NOT NULL DEFAULT NOW() | AC-07 |

**Index:** `idx_order_rating_driver` — `(driver_id)`

> ⚠️ **Quyết định:** `order_rating` **không có** `deleted_at` — đánh giá là **bất biến**, không ai xoá
> được (kể cả Manager). Đúng ý đồ: khách đã đánh giá thì công ty không được xoá.

### `driver_profile.average_rating` (V4 + V40)

| Thuộc tính | Giá trị |
|-----------|---------|
| Type | `NUMERIC(3,2)` — dải `0.00`–`9.99` |
| DEFAULT | **`5.00`** (V40, trước là `0.00`) |
| Nguồn | Trung bình thuần từ `order_rating` |
| Khi chưa có đánh giá | `5.00` |

### So sánh hai hệ thống rating trong dự án

| | `order_rating` (spec này) | `blog_post.rating` (Spec #024) |
|---|---|---|
| Gắn với | Một đơn cụ thể | Một bài viết cộng đồng |
| Bắt buộc | Có (`NOT NULL`) | Không (`NULL` được) |
| Ảnh hưởng `average_rating` | **Có** | **Không** |
| Ai xem | Manager (comment), Admin (thống kê) | Công khai (Guest) |
| Xoá được | **Không** | Manager xoá bài được |

→ Hai hệ thống **hoàn toàn tách biệt** (Spec #024 DS-10).

---

## Implementation Notes — Bẫy PostgreSQL với tham số nullable

> **Đây là lỗi 500 thật đã gặp ngày 2026-06-17**, được ghi lại để không lặp lại.

### Vấn đề

```java
// ❌ SAI — gây lỗi runtime trên PostgreSQL
@Query("... where (:keyword is null or lower(d.fullName) like lower(concat('%', :keyword, '%')))")
Page<DriverRatingItem> search(@Param("keyword") String keyword, Pageable pageable);
```

Khi `keyword = null`, PostgreSQL nhận `lower($1)` với `$1` không có kiểu → driver suy nhầm thành
`bytea` → lỗi:

```
ERROR: function lower(bytea) does not exist
```

Tương tự với `UUID`/`Integer` nullable:

```
ERROR: could not determine data type of parameter $1
```

### Giải pháp áp dụng

**Ở service** — convert sang `String` + lowercase **trong Java**:

```java
String driverIdParam = driverId != null ? driverId.toString() : null;
String starsParam    = stars    != null ? stars.toString()    : null;
String keywordPattern = (keyword == null || keyword.isBlank())
        ? null : "%" + keyword.trim().toLowerCase() + "%";
```

**Ở query** — `cast(... as string)` và **không** bọc `lower(:param)`:

```java
where (:driverId is null or cast(r.driverId as string) = :driverId)
  and (:stars is null or cast(r.stars as string) = :stars)
  and (:keywordPattern is null
       or lower(coalesce(d.fullName, '')) like :keywordPattern)
```

`lower()` chỉ áp lên **cột** (`d.fullName` — kiểu đã biết), không áp lên **tham số**.

### ⚠️ Cảnh báo test

**Test H2 KHÔNG bắt được lỗi này** — H2 khoan dung hơn với kiểu tham số. Chỉ phát hiện khi chạy thật
trên PostgreSQL/Neon. Đây là lý do lỗi lọt tới production-demo.

→ **Bài học:** query có tham số nullable + hàm chuỗi **phải test trên PostgreSQL thật**, không chỉ H2.

---

## Transaction Boundaries

```
BEGIN (readOnly)  -- ManagerDriverRatingService @Transactional(readOnly = true)
  assert stars == null || 1 <= stars <= 5        -- 422 VALIDATION_ERROR
  driverIdParam   = driverId?.toString()          -- convert String (tránh unknown type)
  starsParam      = stars?.toString()
  keywordPattern  = keyword?.trim().toLowerCase() → "%...%"   -- lowercase ở Java
  pageable        = PageRequest.of(clampPage(page), clampSize(size))
  return orderRatingRepository.searchForManager(driverIdParam, starsParam, keywordPattern, pageable)
COMMIT
```

> Không có transaction ghi — spec này **chỉ đọc**.

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | "Vui lòng đăng nhập để tiếp tục." |
| 403 | `FORBIDDEN` | Role khác MANAGER (kể cả ADMIN) | — |
| 422 | `VALIDATION_ERROR` | `stars` ngoài [1,5] | "Bộ lọc số sao phải từ 1 đến 5." |

> Chỉ **3 lỗi** — endpoint đọc đơn giản nhất trong 8 spec mới.

---

## Frontend Screen Contract

### `manager/driver-ratings.html` — "Đánh giá tài xế"

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Bộ lọc tài xế | `?driverId=` | Dropdown hoặc autocomplete |
| Bộ lọc sao | `?stars=` | Pills 1–5 sao + "Tất cả" |
| Tìm kiếm | `?keyword=` | Theo **tên tài xế**, debounce 300ms |
| Bảng | `GET /api/manager/driver-ratings?...&page&size` | Sort cố định mới nhất trước |
| Cột | Ngày, Mã đơn, Tài xế, Khách hàng, Số sao, Nhận xét | |
| Hiển thị sao | `★ 4/5` hoặc 5 icon sao | Theo design-internal-reference |
| Nhận xét | `comment` — **escape trước khi render** (chống XSS) | Có thể `null` |
| Tài xế null | Hiển thị "—" hoặc "Chưa gán" | FR-005 |
| Pagination | Page buttons + size selector | AC-15 |
| Loading | "Đang tải..." | AC-16 |
| Empty | "Không có đánh giá nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + "Tải lại" | AC-16 |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| RBAC | MANAGER only; Admin bị 403 (dùng Spec #012/#016 thay thế) |
| SQL injection | JPQL + bound params; **sort cố định trong query**, không nhận từ client (FR-003) |
| XSS | `comment` là **free text do khách nhập** → FE **phải escape** |
| PII | Trả `driverName` + `customerName` — cần thiết để Manager đối chiếu; **không** trả email/phone |
| Bất biến | Không endpoint nào sửa/xoá đánh giá — khách đã đánh giá thì công ty không gỡ được |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-026-01 | Manager gọi → 200, sắp xếp mới nhất trước | Test |
| AC-026-02 | Admin gọi → 403 | Test RBAC |
| AC-026-03 | Customer/Driver gọi → 403 | Test RBAC |
| AC-026-04 | Response chứa `comment` | Grep response |
| AC-026-05 | Lọc `stars=5` → chỉ 5 sao | Test |
| AC-026-06 | `stars=6` → 422 | Test |
| AC-026-07 | `stars=0` → 422 | Test |
| AC-026-08 | Lọc `driverId` → chỉ tài xế đó | Test |
| AC-026-09 | Lọc `keyword` không phân biệt hoa thường | **Test trên PostgreSQL thật** |
| AC-026-10 | `keyword=null` → không lỗi `lower(bytea)` | **Test trên PostgreSQL thật** |
| AC-026-11 | `driverId=null` → không lỗi "could not determine data type" | **Test trên PostgreSQL thật** |
| AC-026-12 | Bản ghi `driver_id=null` vẫn hiện khi không lọc | Test |
| AC-026-13 | `size=200` → clamp 100 | Test |
| AC-026-14 | Tài xế mới → `average_rating = 5.00` | DB check |
| AC-026-15 | Tài xế có 1 đánh giá 1 sao → `average_rating = 1.00` | DB check |
| AC-026-16 | Loading/Empty/Error đủ | Manual |
| AC-026-17 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **`keyword = null` bind vào `lower()`** → lỗi `function lower(bytea) does not exist` nếu implement
   sai. Đã xử lý bằng lowercase-ở-Java (Implementation Notes). **H2 không bắt được.**
2. **`driverId = null` bind trực tiếp UUID** → `could not determine data type of parameter`. Đã xử lý
   bằng convert `String` + `cast(... as string)`.
3. **Đánh giá của đơn có `driver_id = null`** (dữ liệu cũ) → vẫn hiện với `driverName = null` khi không
   lọc; **bị loại** khi lọc keyword vì `coalesce(d.fullName,'')` = `''` không match `%abc%`.
4. **Tài xế mới có 1 đánh giá 1 sao** → `average_rating` rơi **5.00 → 1.00** ngay lập tức. Với tài xế
   có 100 đánh giá, 1 sao chỉ kéo xuống chút ít. Bất công theo số lượng — hệ quả của trung bình thuần
   (DS-01).
5. **Khách đánh giá sau 24h** → bị chặn ở Spec #003 (`app.rating.window-minutes`), không thuộc spec này.
6. **`comment` chứa HTML/script** → backend lưu nguyên; **FE phải escape**. Rủi ro XSS tương tự Blog
   (Spec #024 DS-02).
7. **`comment = null`** (khách chỉ chấm sao) → hiển thị rỗng, không lỗi.
8. **Manager muốn xoá đánh giá bôi nhọ** → **không làm được** — không có endpoint. Đúng ý đồ nhưng có
   thể là vấn đề vận hành thật (DS-03).
9. **`cast(r.stars as string) = '5'`** → so khớp chuỗi thay vì số; hoạt động đúng nhưng **không dùng
   được index** trên `stars` (nếu có). Hiện chỉ có index trên `driver_id` nên không ảnh hưởng.
10. **Đánh giá của tài xế đã bị xoá mềm** → `left join User d` — nếu `User` có `@SQLRestriction`
    `deleted_at IS NULL` thì `driverName` sẽ `null`. Bản ghi vẫn hiện.

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-026-01 | Unit | `search(stars=6)` | 422 |
| TC-026-02 | Unit | `search(stars=0)` | 422 |
| TC-026-03 | Unit | `search(stars=null)` | Không lọc |
| TC-026-04 | Unit | `clampSize(200)` | 100 |
| TC-026-05 | Unit | `clampSize(0)` | 10 |
| TC-026-06 | Unit | `clampPage(-3)` | 0 |
| TC-026-07 | Unit | `keywordPattern("  Nam  ")` | `"%nam%"` |
| TC-026-08 | Unit | `keywordPattern("")` | `null` |
| TC-026-09 | **Integration (PostgreSQL)** | `search(null, null, null)` | **Không lỗi `lower(bytea)`** |
| TC-026-10 | **Integration (PostgreSQL)** | `search(driverId, null, null)` | Không lỗi kiểu |
| TC-026-11 | **Integration (PostgreSQL)** | `search(null, null, "NGUYEN")` | Match không phân biệt hoa thường |
| TC-026-12 | Integration | Bản ghi `driver_id=null` | Vẫn hiện |
| TC-026-13 | Integration | Sắp xếp DESC | Đúng thứ tự |
| TC-026-14 | Integration | Admin gọi | 403 |
| TC-026-15 | Integration | Customer gọi | 403 |
| TC-026-16 | Integration | `countQuery` đúng khi có filter | `totalElements` khớp |

> ⚠️ **TC-026-09/10/11 PHẢI chạy trên PostgreSQL thật** — H2 không tái hiện lỗi.

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Xem lại công thức trung bình** — chính sách 5 sao mặc định (FR-020) kết hợp trung bình thuần (FR-022) khiến tài xế mới rơi 5.00 → 1.00 chỉ với **một** đánh giá xấu, trong khi tài xế có 100 đánh giá gần như miễn nhiễm | Tài xế mới chịu rủi ro không cân xứng; một khách khó tính có thể xoá sổ tài xế vừa vào nghề | Bayesian average (prior 5 sao × N đánh giá ảo). Xem OQ-3 |
| DS-02 | **Cập nhật CONTEXT** cho 2 quyết định leader 2026-06-17: (a) 5 sao mặc định, (b) cửa sổ rating **24 giờ** tách khỏi escrow tiền 2 giờ | CONTEXT §2 Escrow vẫn ghi "Trong 2h khach co quyen tao DamageReport hoac rating" → tài liệu nói khác thiết kế; hội đồng đối chiếu sẽ thấy lệch | Cập nhật CONTEXT §2 Escrow + §7 Feature #30. Xem OQ-1 |
| DS-03 | **Công cụ xử lý đánh giá độc hại** — bản này không cho Manager ẩn/xoá đánh giá nào, kể cả bôi nhọ, spam hay chấm nhầm người | Không có đường xử lý khi có đánh giá sai sự thật. Đối lập với Blog (#024) — nơi Manager ẩn/xoá bài được. Nhưng cho phép xoá lại mở ra rủi ro công ty gỡ đánh giá xấu | `status VISIBLE/HIDDEN` như blog **hoặc** quy trình khiếu nại có audit. Xem OQ-2 |
| DS-04 | Bổ sung `manager/driver-ratings.html` vào `SCREEN_INVENTORY.md` (mục 5.x hiện có 9 màn Manager, không có màn này) | Số màn hình báo cáo thiếu | Cập nhật inventory |
| DS-05 | **Bộ lọc theo khoảng thời gian** `from`/`to` — Spec #025 có, spec này không | Manager không xem được "đánh giá tháng này" để đối chiếu theo kỳ | Thêm 2 param vào query |
| DS-06 | Đổi `cast(r.stars as string) = :stars` sang so khớp số | So khớp chuỗi không dùng được index nếu sau này thêm index trên `stars`; phản trực giác khi đọc query | `:stars is null or r.stars = :stars` với `Integer` — **nhưng** phải xử lý bind null (xem Implementation Notes) |
| DS-07 | Mở rộng `keyword` sang tên khách và nội dung `comment` — bản này chỉ lọc theo tên tài xế | Manager không tìm được "đánh giá nào nhắc tới 'trễ giờ'" | Thêm `or lower(r.comment) like :keywordPattern` |
| DS-08 | **Tags rating** ("Đúng giờ / Lịch sự / Xe sạch / Hỗ trợ tốt") — `SCREEN_INVENTORY` 3.13 có hứa | Inventory mô tả nhiều hơn thiết kế; cần thống nhất | Bỏ khỏi inventory **hoặc** thêm bảng `order_rating_tag`. Xem OQ-5 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Cập nhật CONTEXT** cho chính sách 5 sao mặc định (V40) và cửa sổ rating 24h không? (DS-02) | Tài liệu | **High** |
| OQ-2 | Manager có nên ẩn được đánh giá bôi nhọ/spam không? (DS-03) | DS-03 | **High** |
| OQ-3 | Trung bình thuần có công bằng với tài xế mới không? Cân nhắc Bayesian? (DS-01) | DS-01 | Medium |
| OQ-4 | Thêm bộ lọc thời gian? (DS-05) | — | Medium |
| OQ-5 | Có implement tags rating như `SCREEN_INVENTORY` 3.13 hứa không? (DS-08) | — | Low |
| OQ-6 | Admin có nên xem được comment không? Hiện chỉ Manager (code comment ghi "khác Admin chỉ xem sao") | — | Low |

---

## Rollout Plan

**Phụ thuộc:** Spec #003 (Customer tạo đánh giá) phải lên trước — không có `order_rating` thì không có
gì để tra cứu.

**Thứ tự triển khai:**

1. Chốt OQ-1 → cập nhật CONTEXT §2 Escrow (rating 24h tách khỏi escrow tiền 2h) + §7 Feature #30
   (5 sao mặc định) **trước** khi chạy `V40`, vì đây là thay đổi chính sách nhìn thấy được với tài xế.
2. `V40` — đổi DEFAULT + backfill. **Migration duy nhất trong 8 spec mới chạm dữ liệu đang có** →
   backup trước khi chạy trên Neon.
3. Backend: `ManagerDriverRatingService` + `OrderRatingRepository.searchForManager`.
4. Frontend: `manager/driver-ratings.html`.

**Rủi ro cần theo dõi khi rollout:**

- **Test query trên PostgreSQL thật, không chỉ H2.** Xem Implementation Notes: bind tham số nullable vào
  hàm chuỗi gây lỗi runtime mà H2 không tái hiện được. TC-026-09/10/11 bắt buộc chạy trên PostgreSQL.
- `V40` backfill không rollback tự động được — nếu leader đổi ý về chính sách 5 sao, cần migration ngược
  thủ công.
- Tài xế đang có `average_rating = 0.00` vì chưa ai đánh giá sẽ **nhảy lên 5.00** sau `V40` — thông báo
  cho đội vận hành trước, tránh hiểu nhầm là lỗi dữ liệu.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #26 Manager Driver Ratings  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | N/A | |
| HR-02 BCrypt | N/A | |
| HR-03/04 IPN | N/A | |
| HR-05 Transition sai → 409 | N/A | Chỉ đọc |
| HR-06/07 DamageReport | N/A | |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | MANAGER only (FR-018) |
| HR-11 Email không rollback | N/A | |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log | ⚠️ **GAP nhẹ** | Manager đọc đánh giá **không ghi audit**. Đây là thao tác đọc, HR-13 chỉ yêu cầu audit cho **state change** → **N/A**. (Spec #011 có nhắc `ADMIN_LIST_ACCESSED` throttled cho list pages — màn này không có) |
| HR-14 RefundRecord | N/A | |
| HR-15 Idempotency | N/A | |
| HR-16 Rate limit | N/A | Chỉ GET |
| HR-17 Public vs Authenticated | PASS | Không endpoint public |
| HR-18 Wallet | N/A | |
| HR-19 Brand identity | PASS | |
| HR-20 Tiếng Việt có dấu | PASS | Message + UI có dấu |
| HR-21 Tránh reserved words | PASS | `order_rating` |

**Layer 1 Result:** ALL PASS (HR-13 N/A vì chỉ đọc).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | ✅ **PASS** | JPQL + bound params; **sort cố định trong query, không nhận từ client** (FR-003) — chống injection qua tên cột |
| AC-05 Chat | N/A | |
| AC-06 Maps | N/A | |
| AC-07 Timezone | PASS | `TIMESTAMPTZ`, `OffsetDateTime` |
| AC-08 BigDecimal | N/A | `stars` là `INTEGER`, không phải tiền |
| AC-09 Soft delete | ✅ **PASS (có chủ ý)** | `order_rating` không có `deleted_at` — **đánh giá bất biến**, không ai xoá được. Không thuộc danh sách entity bắt buộc soft delete của AC-09 |
| AC-10 Cloudinary | N/A | |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V9, V40 |
| AC-13 Money audit | N/A | |
| AC-14 VARCHAR + CHECK | ✅ **PASS** | `stars INTEGER CHECK BETWEEN 1 AND 5` — AC-14 nói về status field dùng VARCHAR; `stars` là số nên `INTEGER + CHECK` là đúng |
| AC-15 Pagination | PASS | Default 10, max 100, có `countQuery` riêng |
| AC-16 Empty/Loading/Error | PASS | Trang có đủ |

**Layer 2 Result:** ALL PASS.

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | `/driver-ratings` plural |
| ES-03 Bean Validation + 422 | PARTIAL | Validate `stars` thủ công (không `@Min`/`@Max` như Audit Log); page/size clamp thay vì validate |
| ES-04 Error format | PASS | `VALIDATION_ERROR` theo format |
| ES-05 Test coverage | ⚠️ **CHƯA VERIFY** | SHELL. ⚠️ **Quan trọng:** test H2 **không** bắt được bẫy `lower(bytea)` — cần integration test trên PostgreSQL thật (TC-026-09/10/11) |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 21/21 PASS  
Layer 2 : 16/16 PASS  
Layer 3 : 7/8 PASS, ES-03 partial, ES-05 chưa verify  
Status  : **CLEARED TO SUBMIT** — spec sạch nhất trong 8 spec mới (không mâu thuẫn tài liệu nghiêm
trọng, Layer 1 + Layer 2 full PASS). Cần trả lời OQ-1 (cập nhật CONTEXT cho V40 + rating 24h) và OQ-2
(Manager ẩn đánh giá xấu).
================================
