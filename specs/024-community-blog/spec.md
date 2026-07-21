# Feature Specification: Community Blog (Blog cộng đồng)

**Feature Branch:** `024-community-blog`  
**Feature Number:** #24 of 26 — SHELL (nội dung cộng đồng, không đụng tiền)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft — **BLOCKED** chờ leader duyệt OQ-1 (xem Source-of-Truth Resolution)  
**Sprint Target:** Sprint 7 — 3 pha (A: đăng bài + feed; B: bình luận; C: kiểm duyệt + chống spam)

**CONTEXT.md reference:** v2.0 §2 Guest Mode, §3 RBAC — ⚠️ **CONTEXT không nhắc blog** (xem
Source-of-Truth Resolution)  
**Constitution reference:** v1.4.0 — HR-01, HR-10, HR-16 (rate limit), HR-17 (public endpoint không lộ
PII), HR-20, HR-21, AC-09 (soft delete), AC-10 (Cloudinary), AC-11, AC-12, AC-14, AC-15, AC-16, ES-02,
ES-03, ES-04  
**Screen reference:** `frontend/pages/public/blog-detail.html`, feed trên homepage —
cần bổ sung vào `docs/SCREEN_INVENTORY.md` (xem DS-05)  
**Related specs:** Spec #017 Public Marketing (⚠️ **mâu thuẫn** — blog nằm trong Out-of-scope);
Spec #020 Notifications (thông báo bình luận); Spec #001 Auth/RBAC

**Migration liên quan:** `V42__create_blog_post.sql` (`blog_post` + `blog_post_photo`),
`V43__create_blog_comment.sql` (`blog_comment`)

---

## Goals

Đặc tả **Blog cộng đồng (Community Wall)** — nơi khách hàng đăng bài review kèm ảnh về dịch vụ chuyển
nhà, trao đổi công khai, và Manager phản hồi với badge "Quản lý".

Guest (chưa đăng nhập) **xem được** feed và bình luận — đây là nội dung marketing công khai giúp khách
tiềm năng thấy đánh giá thật. Customer đã kích hoạt + xác thực email được **đăng bài** (nội dung ≤ 1000
ký tự, rating 1–5 sao tuỳ chọn, tối đa 3 ảnh) và **bình luận**. Manager bình luận để trả lời và
**kiểm duyệt**: ẩn/hiện bài, ẩn/hiện bình luận, xoá mềm bài kèm dọn ảnh Cloudinary.

Vì là nội dung công khai do người dùng tạo (UGC), spec đặc biệt chú trọng: **chống spam** (rate limit
5 bài/giờ + 20 bình luận/giờ theo user, cửa sổ trượt in-memory), **không lộ PII qua endpoint public**
(HR-17 — tác giả đã xoá mềm hiển thị "Người dùng Move_home"), **soft delete** (AC-09), và **cleanup ảnh
Cloudinary** khi xoá bài (AC-10).

Khác các luồng ảnh khác trong dự án (dispute, cancellation, incident dùng `type=authenticated` + signed
URL), ảnh blog dùng Cloudinary **`type=upload`** (public delivery URL) vì feed là nội dung công khai —
nhưng vẫn là **signed upload server-side** để không lộ API key phía client (AC-10).

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.

### Quyết định kiến trúc — thêm Blog cộng đồng vào scope

Blog là **feature mới hoàn toàn**, không nằm trong 30 feature của `CONTEXT.md` v2.0 §7, và bị **Spec
#017 loại trừ tường minh** (Out-of-scope #1: *"Blog, news, careers và press releases"*). Spec này đề
xuất đưa nó vào scope như **feature #31**.

**Lý do đề xuất:**

| Vấn đề | Cách blog cộng đồng giải quyết |
|--------|-------------------------------|
| Guest vào 6 trang public chỉ thấy nội dung công ty tự nói về mình — không có bằng chứng xã hội | Feed review thật của khách, kèm ảnh, kèm sao |
| `order_rating` (Spec #026) chỉ Manager/Admin đọc được — khách tiềm năng không thấy | Blog là kênh công khai để khách tự kể trải nghiệm |
| Khách có phản hồi tốt không có chỗ chia sẻ; phản hồi xấu chỉ đi vào tranh chấp | Kênh trao đổi công khai có Manager tham gia |
| Công ty không có kênh thể hiện sự phản hồi trước công chúng | Manager trả lời với badge "Quản lý" ngay dưới bài |

**Đánh đổi phải chấp nhận:** đây là **UGC công khai** — mở ra rủi ro spam, XSS, nội dung độc hại và
nghĩa vụ kiểm duyệt mà 30 feature gốc không có. Đó là lý do bản 1.0.0 bắt buộc kèm: gate ACTIVE +
email verified (FR-011), rate limit (FR-041..FR-044), kiểm duyệt Manager (FR-035..FR-040), và
escape XSS phía FE (FR-031, OQ-4).

**Xung đột với Spec #017 cần leader xử lý:**

| Nguồn | Nội dung | Cần làm gì |
|-------|----------|-----------|
| **Spec #017** Out-of-scope **#1** | "Blog, news, careers và press releases" | Sửa thành *"News, careers, press releases — blog cộng đồng thuộc Spec #024"* |
| `CONTEXT.md` §Guest Mode | Guest xem **6 trang public** | Nâng lên **8** (feed + chi tiết bài) |
| `CONTEXT.md` §7 (30 feature) | Không có blog | Thêm feature #31 — 🟡 SHELL |
| `CONTEXT.md` §3 RBAC | Không có dòng nào về UGC | Thêm 3 dòng: đăng bài / bình luận / kiểm duyệt |
| Constitution | Không có HR/AC nào về UGC | Cân nhắc AC mới về kiểm duyệt nội dung |

### Quyết định canonical đề xuất (hiệu lực khi OQ-1 được duyệt)

| Chủ đề | Đề nghị canonical | Ghi chú |
|--------|-------------------|---------|
| Blog cộng đồng | **Feature #31** — 🟡 SHELL, không đụng tiền | Thêm vào CONTEXT §7 |
| Guest xem blog | **Có** — 6 → **8 trang public** | Amend CONTEXT §Guest Mode |
| Ai đăng bài | Customer **ACTIVE + email verified** | Gate chống bot (FR-011) |
| Ai bình luận | Customer (ACTIVE + verified) hoặc Manager | Driver và Admin **không** (FR-026) |
| Ai kiểm duyệt | **MANAGER** — không phải Admin | Xem OQ-3 |
| Ảnh | Cloudinary signed upload server-side, delivery **`type=upload`** (public) | **Cố ý khác** dispute/incident/cancellation — xem bảng so sánh ở Data Model |
| Rate limit | Theo **user**, in-memory, cửa sổ trượt 1 giờ | Khác cơ chế HR-16 (60 req/IP/phút) — xem DS-08 |

---

## Scope Summary

**In scope:**

1. `GET /api/public/blog/feed` — feed công khai, server-side pagination.
2. `GET /api/public/blog/posts/{postId}/comments` — bình luận công khai của một bài.
3. `POST /api/customer/blog/posts` — Customer đăng bài (multipart: content + rating + ≤3 ảnh).
4. `POST /api/customer/blog/posts/{postId}/comments` — Customer bình luận.
5. `POST /api/manager/blog/posts/{postId}/comments` — Manager trả lời (badge "Quản lý").
6. `POST /api/manager/blog/posts/{postId}/hide` · `/unhide` — ẩn/hiện bài.
7. `DELETE /api/manager/blog/posts/{postId}` — xoá mềm bài + dọn ảnh Cloudinary.
8. `POST /api/manager/blog/comments/{commentId}/hide` — ẩn bình luận.
9. `DELETE /api/manager/blog/comments/{commentId}` — xoá mềm bình luận.
10. Rate limit chống spam theo user.
11. Notification cho chủ bài khi có phản hồi.
12. Loading/Empty/Error states cho feed và trang chi tiết.

**Out of scope:**

1. Sửa bài/bình luận sau khi đăng — không có endpoint.
2. Customer tự xoá bài của mình — **chỉ Manager xoá được** (xem DS-01).
3. Like/react/share bài viết.
4. Bình luận lồng nhau (reply theo cây) — chỉ 1 cấp phẳng.
5. Tag/category/tìm kiếm bài viết.
6. Trang cá nhân tác giả — hoãn sang bản sau (DS-09).
7. Báo cáo bài vi phạm bởi người dùng.
8. Admin kiểm duyệt — chỉ Manager (xem OQ-3).
9. Kiểm duyệt tự động (lọc từ khoá, AI moderation).
10. Rating blog ảnh hưởng `driver_profile.average_rating` — **hoàn toàn tách biệt** với `order_rating`
    (Spec #026).

---

## User Stories

**P1:**

**US1:** Là Guest, tôi đọc review thật của khách hàng khác trước khi quyết định đặt dịch vụ.

**US2:** Là Customer, tôi đăng bài review kèm ảnh về trải nghiệm chuyển nhà của mình.

**US3:** Là Customer, tôi chấm 1–5 sao cho dịch vụ khi đăng bài (tuỳ chọn).

**US4:** Là Customer, tôi bình luận dưới bài của người khác để trao đổi kinh nghiệm.

**US5:** Là Manager, tôi trả lời bình luận với badge "Quản lý" để khách thấy công ty có phản hồi.

**US6:** Là Customer, tôi nhận thông báo khi có người phản hồi bài viết của mình.

**P2:**

**US7:** Là Manager, tôi ẩn bài viết vi phạm khỏi feed công khai mà không xoá hẳn.

**US8:** Là Manager, tôi xoá hẳn bài spam và ảnh của nó được dọn khỏi Cloudinary.

**US9:** Là Manager, tôi ẩn bình luận vi phạm.

**US10:** Là hệ thống, tôi chặn user spam quá 5 bài/giờ hoặc 20 bình luận/giờ.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch).
>
> Tổng: **44 FR**, trong đó **20 FR có mệnh đề WHERE** (45.5% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Feed công khai (FR-001..FR-008)

**FR-001**  
WHEN bất kỳ ai (kể cả Guest) gọi `GET /api/public/blog/feed?page={p}&size={s}`, THE system SHALL trả
`Page<BlogPostResponse>` gồm các bài `status = VISIBLE` **và** `deleted_at IS NULL`, sắp xếp
`created_at` giảm dần (AC-15).

**FR-002**  
WHEN `size` không truyền, THE system SHALL dùng default `10`; THE system SHALL clamp `page` về
`max(page, 0)` và `size` về `min(max(size,1), 50)` — **không** trả lỗi validation cho giá trị ngoài
khoảng.

**FR-003**  
WHEN trả mỗi bài, THE system SHALL bao gồm `id`, `authorName`, `authorAvatar`, `content`, `rating`,
`photoUrls[]`, `commentCount`, `createdAt`.

**FR-004**  
WHERE tác giả bài đã bị **xoá mềm** (không tìm thấy trong `findAllById`), THE system SHALL hiển thị
`authorName = "Người dùng Move_home"` và `authorAvatar = null` — SHALL không lộ PII hay để lỗi
`NullPointerException` (HR-17).

**FR-005**  
WHEN dựng feed, THE system SHALL **batch load** tác giả, ảnh và số bình luận theo lô (`findAllById`,
`findByPostIdInOrderByUploadedAtAsc`, `countVisibleByPostIds`) — SHALL không truy vấn N+1.

**FR-006**  
WHERE feed rỗng, THE system SHALL trả `PageImpl` rỗng kèm `totalElements` đúng — SHALL không throw.

**FR-007**  
WHILE endpoint nằm dưới prefix `/api/public/*`, THE system SHALL cho phép truy cập không cần JWT
(`permitAll`) theo HR-17.

**FR-008**  
WHILE trả dữ liệu qua endpoint public, THE system SHALL **không** trả email, số điện thoại, id nội bộ
của tác giả hay bất kỳ PII nào ngoài `fullName` + `avatarUrl` (HR-17).

---

### Nhóm 2 — Customer đăng bài (FR-009..FR-018)

**FR-009**  
WHEN Customer gọi `POST /api/customer/blog/posts` (multipart: `content`, `rating?`, `files[]?`) và mọi
điều kiện thoả, THE system SHALL tạo `blog_post` status `VISIBLE`, đính kèm ảnh, và trả
`BlogPostResponse` với `commentCount = 0`.

**FR-010**  
WHERE `@AuthenticationPrincipal` là `null`, THE system SHALL trả HTTP 401 `AUTHENTICATION_REQUIRED`
"Vui lòng đăng nhập để tiếp tục."

**FR-011**  
WHERE user **chưa xác thực email** (`emailVerified = false`) **hoặc** `status != ACTIVE`, THE system
SHALL trả HTTP 403 `ACCOUNT_NOT_ACTIVE` "Tài khoản cần được kích hoạt và xác thực email để tham gia."
— chống spam cơ bản.

**FR-012**  
WHERE `content` rỗng sau trim, THE system SHALL trả HTTP 422 `INVALID_CONTENT` "Nội dung không được để
trống."; WHERE dài hơn **1000 ký tự**, SHALL trả 422 "Nội dung tối đa 1000 ký tự."

**FR-013**  
WHERE `rating` khác `null` và nằm ngoài `[1, 5]`, THE system SHALL trả HTTP 422 `INVALID_RATING`
"Đánh giá phải từ 1 đến 5 sao."

**FR-014**  
WHEN `rating` là `null`, THE system SHALL cho phép — bài chỉ có nội dung/ảnh, không chấm điểm.

**FR-015**  
WHEN lưu `rating`, THE system SHALL convert sang `Short` để khớp cột `SMALLINT` (V42); DB CHECK SHALL
enforce `rating IS NULL OR rating BETWEEN 1 AND 5`.

**FR-016**  
WHERE user đã đăng **5 bài trong 1 giờ** gần nhất, THE system SHALL trả HTTP 429 (map thành
`RATE_LIMITED`) và SHALL không tạo bài.

**FR-017**  
WHEN kiểm tra rate limit, THE system SHALL kiểm tra **sau** khi validate nội dung/rating — request sai
định dạng SHALL không tính vào quota.

**FR-018**  
WHEN bài được tạo, THE system SHALL log dòng `Blog: customer {id} dang bai {postId} ({n} anh)`.

---

### Nhóm 3 — Ảnh bài đăng (FR-019..FR-024)

**FR-019**  
WHEN đính kèm ảnh, THE system SHALL cho phép tối đa **3 ảnh**/bài; WHERE vượt quá, SHALL trả lỗi và
SHALL không tạo ảnh nào.

**FR-020**  
WHERE file lớn hơn **1.5 MB**, THE system SHALL từ chối; WHERE nội dung không phải ảnh hợp lệ theo magic
number, SHALL từ chối và SHALL không gọi Cloudinary (AC-10).

**FR-021**  
WHEN upload lên Cloudinary, THE system SHALL dùng **signed upload server-side** với `type = "upload"`
(public delivery) — khác `type = "authenticated"` của dispute/incident/cancellation, vì feed là nội dung
công khai không cần signed URL.

**FR-022**  
WHEN lưu ảnh, THE system SHALL lưu cả `url` (public `secure_url`, dùng trực tiếp làm `<img src>`) và
`public_id` (để destroy khi xoá bài).

**FR-023**  
WHEN Manager xoá bài, THE system SHALL gọi `cloudinary.uploader().destroy(public_id)` cho **mọi** ảnh
của bài trước khi xoá mềm bài (AC-10 cleanup).

**FR-024**  
WHERE việc destroy ảnh trên Cloudinary lỗi, THE system SHALL log warning và **vẫn tiếp tục** xoá bài —
lỗi dọn ảnh SHALL không chặn thao tác kiểm duyệt.

---

### Nhóm 4 — Bình luận (FR-025..FR-034)

**FR-025**  
WHEN Customer gọi `POST /api/customer/blog/posts/{postId}/comments` hoặc Manager gọi
`POST /api/manager/blog/posts/{postId}/comments` với `content` hợp lệ, THE system SHALL tạo
`blog_comment` status `VISIBLE` với `author_role` snapshot và trả `BlogCommentResponse`.

**FR-026**  
WHERE role không phải `CUSTOMER` **và** không phải `MANAGER`, THE system SHALL trả HTTP 403 `FORBIDDEN`
"Chỉ khách hàng hoặc quản lý mới được bình luận." — Driver và Admin **không** bình luận được.

**FR-027**  
WHILE người bình luận là Customer, THE system SHALL kiểm tra `requireActiveCustomer` (email verified +
ACTIVE); WHILE là Manager, SHALL **bỏ qua** kiểm tra này.

**FR-028**  
WHEN lưu bình luận, THE system SHALL snapshot `author_role` (`CUSTOMER`/`MANAGER`) vào bản ghi để render
badge mà không cần join `app_user`; DB CHECK SHALL chỉ chấp nhận hai giá trị này.

**FR-029**  
WHERE bài không tồn tại **hoặc** `status != VISIBLE`, THE system SHALL trả HTTP 404 `POST_NOT_FOUND`
"Không tìm thấy bài viết." — không bình luận được dưới bài đã bị ẩn.

**FR-030**  
WHERE user đã bình luận **20 lần trong 1 giờ** gần nhất, THE system SHALL trả HTTP 429; rate limit SHALL
được kiểm tra **sau** khi xác thực bài tồn tại.

**FR-031**  
WHEN bình luận được tạo **và** người bình luận **không phải** chủ bài, THE system SHALL tạo notification
type `BLOG_COMMENT` cho chủ bài với title "Có phản hồi mới trên bài viết của bạn" và message chứa tên
người phản hồi + snippet nội dung (cắt 80 ký tự + "…").

**FR-032**  
WHERE người bình luận **là** chủ bài, THE system SHALL **không** tạo notification — không tự báo mình.

**FR-033**  
WHERE việc tạo notification ném `RuntimeException`, THE system SHALL log warning và **vẫn trả** bình
luận thành công — lỗi notification SHALL không rollback bình luận (tinh thần HR-11).

**FR-034**  
WHEN bất kỳ ai (kể cả Guest) gọi `GET /api/public/blog/posts/{postId}/comments`, THE system SHALL trả
`List<BlogCommentResponse>` các bình luận `VISIBLE` + chưa xoá, sắp xếp `created_at` **tăng dần**
(cũ → mới); WHERE bài không tồn tại, SHALL trả 404 `POST_NOT_FOUND`.

---

### Nhóm 5 — Manager kiểm duyệt (FR-035..FR-040)

**FR-035**  
WHEN Manager gọi `POST /api/manager/blog/posts/{postId}/hide`, THE system SHALL set
`status = 'HIDDEN'`; bài SHALL biến mất khỏi feed công khai nhưng **vẫn tồn tại** trong DB.

**FR-036**  
WHEN Manager gọi `POST /api/manager/blog/posts/{postId}/unhide`, THE system SHALL set
`status = 'VISIBLE'` — thao tác đảo ngược được.

**FR-037**  
WHEN Manager gọi `DELETE /api/manager/blog/posts/{postId}`, THE system SHALL destroy ảnh Cloudinary rồi
**xoá mềm** bài qua `@SQLDelete` (set `deleted_at`) — SHALL không `DELETE FROM` (AC-09).

**FR-038**  
WHEN Manager gọi `POST /api/manager/blog/comments/{commentId}/hide`, THE system SHALL set
`status = 'HIDDEN'`; WHEN gọi `DELETE /api/manager/blog/comments/{commentId}`, SHALL xoá mềm.

**FR-039**  
WHERE bài hoặc bình luận không tồn tại, THE system SHALL trả HTTP 404 `POST_NOT_FOUND` "Không tìm thấy
bài viết." / `COMMENT_NOT_FOUND` "Không tìm thấy bình luận."

**FR-040**  
WHILE mọi endpoint `/api/manager/blog/**` chạy, THE system SHALL enforce role `MANAGER`; WHERE role khác
(kể cả `ADMIN`), SHALL trả HTTP 403 (HR-10).

---

### Nhóm 6 — Rate limit chống spam (FR-041..FR-044)

**FR-041**  
WHILE ứng dụng chạy, THE system SHALL duy trì rate limiter **in-memory** với cửa sổ trượt **1 giờ**,
key = `userId + ":" + action`, lưu trong `ConcurrentHashMap<String, Deque<Instant>>`.

**FR-042**  
WHEN kiểm tra quota, THE system SHALL loại bỏ các mốc thời gian đã ra khỏi cửa sổ 1 giờ trước khi đếm;
truy cập `Deque` SHALL được `synchronized` để an toàn đa luồng.

**FR-043**  
WHERE số lượt trong cửa sổ đạt `MAX_POSTS_PER_HOUR = 5` (đăng bài) hoặc `MAX_COMMENTS_PER_HOUR = 20`
(bình luận), THE system SHALL trả HTTP 429 với message tiếng Việt nêu rõ loại hành động bị chặn.

**FR-044**  
WHERE ứng dụng restart, THE system SHALL reset toàn bộ quota — **chấp nhận được** cho đồ án; SHALL không
dùng Redis hay bảng DB.

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /feed` (size=10) p95 | < 800 ms |
| NFR-002 | `GET /comments` p95 | < 500 ms |
| NFR-003 | `POST /posts` (kèm 3 ảnh) p95 | < 5000 ms |
| NFR-004 | `POST /comments` p95 | < 400 ms |
| NFR-005 | Feed query | Không N+1 — batch load (FR-005) |
| NFR-006 | Pagination | Default 10, max 50 (AC-15) |
| NFR-007 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-008 | Empty/Loading/Error states | Bắt buộc feed + chi tiết (AC-16) |
| NFR-009 | Vietnamese diacritics | 100% text user-facing (HR-20) |
| NFR-010 | Ảnh | Không BLOB/Base64; Cloudinary public delivery (AC-10) |
| NFR-011 | Rate limit | 5 bài/giờ, 20 bình luận/giờ per user |
| NFR-012 | PII | Endpoint public không lộ email/phone (HR-17) |
| NFR-013 | Multipart | `max-request-size = 8MB` cho 3 ảnh/bài (đã cấu hình) |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/public/blog/feed` | **Public** | `page`, `size` | 200 `Page<BlogPostResponse>` | Guest xem được |
| GET | `/api/public/blog/posts/{postId}/comments` | **Public** | — | 200 `List<BlogCommentResponse>` | Cũ → mới |
| POST | `/api/customer/blog/posts` | CUSTOMER | multipart `content`, `rating?`, `files[]?` | 200 `BlogPostResponse` | ≤ 3 ảnh |
| POST | `/api/customer/blog/posts/{postId}/comments` | CUSTOMER | `CreateBlogCommentRequest{content}` | 200 `BlogCommentResponse` | |
| POST | `/api/manager/blog/posts/{postId}/comments` | MANAGER | `{content}` | 200 `BlogCommentResponse` | Badge "Quản lý" |
| POST | `/api/manager/blog/posts/{postId}/hide` | MANAGER | — | 200 | |
| POST | `/api/manager/blog/posts/{postId}/unhide` | MANAGER | — | 200 | |
| DELETE | `/api/manager/blog/posts/{postId}` | MANAGER | — | 200 | + destroy ảnh |
| POST | `/api/manager/blog/comments/{commentId}/hide` | MANAGER | — | 200 | |
| DELETE | `/api/manager/blog/comments/{commentId}` | MANAGER | — | 200 | Soft delete |

### Standard Error (ES-04)

```json
{
  "error_code": "ACCOUNT_NOT_ACTIVE",
  "message": "Tài khoản cần được kích hoạt và xác thực email để tham gia.",
  "details": []
}
```

---

## Data Model

### Schema Design

Blog cần 3 bảng, chia theo 2 migration khớp với 2 pha triển khai:

| Migration | Pha | Nội dung |
|-----------|-----|----------|
| `V42__create_blog_post.sql` | A — đăng bài + feed | `blog_post`, `blog_post_photo`, 2 indexes, trigger `updated_at` |
| `V43__create_blog_comment.sql` | B — bình luận | `blog_comment`, 1 index, trigger `updated_at` |

Pha C (kiểm duyệt + chống spam) **không cần migration**: cột `status VISIBLE/HIDDEN` đã có sẵn từ V42/V43
và rate limiter chạy in-memory.

### Table `blog_post` (V42)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `author_id` | `UUID` NOT NULL → `app_user(id)` | Xoá mềm tác giả **không** xoá bài |
| `content` | `TEXT` NOT NULL | Service validate 1..1000 ký tự |
| `rating` | `SMALLINT` | `CHECK (NULL OR BETWEEN 1 AND 5)` — Java map `Short` |
| `status` | `VARCHAR(20)` NOT NULL DEFAULT `'VISIBLE'` | `CHECK IN ('VISIBLE','HIDDEN')` — AC-14 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` NOT NULL | Trigger `trg_blog_post_updated_at` |
| `deleted_at` | `TIMESTAMPTZ` | **Soft delete** (AC-09) |

**Indexes:**
- `idx_blog_post_feed` — partial `WHERE status='VISIBLE' AND deleted_at IS NULL`, `(created_at DESC, id DESC)`
- `idx_blog_post_author` — `(author_id, created_at DESC)` — **chưa dùng** (trang cá nhân out of scope)

### Table `blog_post_photo` (V42)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `post_id` | `UUID` NOT NULL → `blog_post(id)` | |
| `url` | `VARCHAR(500)` NOT NULL | Public `secure_url` — dùng trực tiếp `<img src>` |
| `public_id` | `VARCHAR(255)` NOT NULL | Để destroy khi xoá bài |
| `uploaded_by_user_id` | `UUID` → `app_user(id)` | |
| `uploaded_at` | `TIMESTAMPTZ` NOT NULL | |

**Index:** `idx_blog_post_photo_post` — `(post_id, uploaded_at)`

> ⚠️ **Ghi nhận:** `blog_post_photo` **không có** `deleted_at` (khác `blog_post`). Ảnh bị destroy hẳn
> trên Cloudinary khi xoá bài, nhưng hàng DB vẫn còn. Xem DS-03.

### Table `blog_comment` (V43)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `post_id` | `UUID` NOT NULL → `blog_post(id)` | |
| `author_id` | `UUID` NOT NULL → `app_user(id)` | |
| `author_role` | `VARCHAR(20)` NOT NULL | `CHECK IN ('CUSTOMER','MANAGER')` — snapshot render badge |
| `content` | `TEXT` NOT NULL | Service validate 1..1000 |
| `status` | `VARCHAR(20)` NOT NULL DEFAULT `'VISIBLE'` | `CHECK IN ('VISIBLE','HIDDEN')` |
| `created_at` / `updated_at` / `deleted_at` | `TIMESTAMPTZ` | Soft delete (AC-09) |

**Index:** `idx_blog_comment_post` — partial `WHERE status='VISIBLE' AND deleted_at IS NULL`,
`(post_id, created_at ASC, id ASC)`

### So sánh chiến lược ảnh toàn dự án

| Luồng | Cloudinary `type` | Delivery | Lý do |
|-------|-------------------|----------|-------|
| DamageReport / Dispute (V35) | `authenticated` | Signed URL | Riêng tư — tranh chấp |
| Cancellation (V41) | `authenticated` | Signed URL | Riêng tư — bằng chứng huỷ |
| Incident (V44) | `authenticated` | Signed URL | Riêng tư — bằng chứng sự cố |
| **Blog (V42)** | **`upload`** | **Public URL** | **Công khai — Guest xem được** |

---

## Permission Matrix

| Hành động | GUEST | CUSTOMER (chưa verify) | CUSTOMER (ACTIVE+verified) | DRIVER | MANAGER | ADMIN |
|-----------|-------|------------------------|----------------------------|--------|---------|-------|
| Xem feed | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Xem bình luận | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Đăng bài | ❌ 401 | ❌ 403 | ✅ | ❌ | ❌ | ❌ |
| Bình luận | ❌ 401 | ❌ 403 | ✅ | ❌ 403 | ✅ | ❌ 403 |
| Ẩn/hiện bài | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ 403 |
| Xoá bài | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ 403 |
| Ẩn/xoá bình luận | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ 403 |

> ⚠️ **Đáng chú ý:** Customer **không xoá được bài của chính mình** (DS-01); Admin **không có quyền gì**
> với blog (OQ-3); Driver **không bình luận được** dù có tài khoản.

---

## Transaction Boundaries

### Đăng bài

```
BEGIN  -- BlogService.createPost @Transactional
  requireActiveCustomer(author)        -- 403 nếu chưa verify/không ACTIVE
  trimmed = requireContent(content)    -- 422 nếu rỗng/> 1000
  assert rating == null || 1 <= rating <= 5   -- 422 INVALID_RATING
  rateLimiter.checkPost(author.id)     -- 429 nếu > 5 bài/giờ
  INSERT blog_post(VISIBLE, rating as Short)
  photos = photoService.attachPhotos(post.id, author.id, files)
     ├─ validate ≤ 3 ảnh, ≤ 1.5MB/ảnh, magic number
     └─ upload Cloudinary (type=upload, public) → INSERT blog_post_photo
  log
COMMIT
```

> ⚠️ Upload Cloudinary nằm **trong** transaction — giữ connection pool (max 5) suốt vài giây. Xem DS-04.

### Bình luận

```
BEGIN  -- BlogService.addComment @Transactional
  assert role ∈ {CUSTOMER, MANAGER}          -- 403
  if CUSTOMER: requireActiveCustomer(author)  -- 403
  trimmed = requireContent(content)           -- 422
  post = findById(postId).filter(VISIBLE)     -- 404 POST_NOT_FOUND
  rateLimiter.checkComment(author.id)         -- 429 nếu > 20/giờ
  INSERT blog_comment(authorRole = role.name(), VISIBLE)
  IF post.authorId != author.id:
      try: notificationService.create(post.authorId, BLOG_COMMENT, ...)
      catch RuntimeException: log warn      -- không rollback (FR-033)
COMMIT
```

### Xoá bài (kiểm duyệt)

```
BEGIN  -- BlogService.deletePost @Transactional
  post = findById(postId)                  -- 404
  photoService.deletePhotosByPost(postId)
     └─ foreach photo: try cloudinary.destroy(publicId) catch → log warn (FR-024)
  postRepository.delete(post)              -- @SQLDelete → SET deleted_at (AC-09)
COMMIT
```

---

## State Machine

### `blog_post` / `blog_comment`

```
   Customer POST /api/customer/blog/posts
   (ACTIVE + email verified + ≤5 bài/giờ)
                    │
                    ▼
              [VISIBLE] ◄──── Manager /unhide ────┐
                    │                             │
      Manager /hide │                             │
                    ▼                             │
               [HIDDEN] ─────────────────────────┘
                    │
                    │  (từ VISIBLE hoặc HIDDEN)
      Manager DELETE│
                    ▼
          [deleted_at != NULL]
        + Cloudinary assets destroyed
              (terminal, soft)
```

| Từ | Sang | Actor | Hệ quả |
|----|------|-------|--------|
| (init) | `VISIBLE` | CUSTOMER | Hiện trên feed |
| `VISIBLE` | `HIDDEN` | MANAGER | Ẩn khỏi feed, vẫn trong DB |
| `HIDDEN` | `VISIBLE` | MANAGER | Hiện lại |
| bất kỳ | soft deleted | MANAGER | `deleted_at` set + ảnh Cloudinary destroyed |

> **Lưu ý:** không có state machine phức tạp — đây là SHELL feature. Không có HTTP 409 nào vì mọi
> transition đều hợp lệ (hide bài đã hidden → vẫn 200, idempotent).

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | Đăng bài/bình luận khi chưa đăng nhập | "Vui lòng đăng nhập để tiếp tục." |
| 403 | `ACCOUNT_NOT_ACTIVE` | Customer chưa verify email / không ACTIVE | "Tài khoản cần được kích hoạt và xác thực email để tham gia." |
| 403 | `FORBIDDEN` | Driver/Admin bình luận | "Chỉ khách hàng hoặc quản lý mới được bình luận." |
| 403 | `FORBIDDEN` | Non-Manager gọi endpoint kiểm duyệt | — |
| 404 | `POST_NOT_FOUND` | Bài không tồn tại / không VISIBLE khi bình luận | "Không tìm thấy bài viết." |
| 404 | `COMMENT_NOT_FOUND` | Bình luận không tồn tại | "Không tìm thấy bình luận." |
| 422 | `INVALID_CONTENT` | Nội dung rỗng / > 1000 ký tự | "Nội dung không được để trống." / "Nội dung tối đa 1000 ký tự." |
| 422 | `INVALID_RATING` | `rating` ngoài [1,5] | "Đánh giá phải từ 1 đến 5 sao." |
| 422 | `INVALID_FILE` | Ảnh rỗng / > 1.5 MB / sai magic number | (từ `BlogPhotoService`) |
| 429 | `RATE_LIMITED` | > 5 bài/giờ hoặc > 20 bình luận/giờ | Message tiếng Việt nêu loại hành động |
| 502 | `CLOUDINARY_UNAVAILABLE` | Cloudinary lỗi khi upload | — |

---

## Frontend Screen Contract

### Feed blog trên homepage (`public/index.html`)

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Danh sách bài | `GET /api/public/blog/feed?page=0&size=10` | Guest xem được |
| Card bài | `authorName`, `authorAvatar`, `content`, `rating` (sao), `photoUrls[]`, `commentCount`, `createdAt` | |
| Ảnh | `photoUrls[]` public URL | Dùng trực tiếp `<img src>`, không cần ký |
| Sao đánh giá | `rating` 1–5, ẩn nếu `null` | |
| Nút "Đăng bài" | Chỉ hiện khi đã đăng nhập role CUSTOMER | Guest → modal "Đăng nhập để tiếp tục" |
| Loading | "Đang tải..." | AC-16 |
| Empty | "Chưa có bài viết nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + "Thử lại" | AC-16 |

### `public/blog-detail.html` — chi tiết bài + bình luận

| Thành phần | Contract |
|------------|----------|
| Nội dung bài | Từ feed hoặc state |
| Danh sách bình luận | `GET /api/public/blog/posts/{postId}/comments` — cũ → mới |
| Badge tác giả | `authorRole = MANAGER` → badge "Quản lý" (forest green); `CUSTOMER` → không badge |
| Form bình luận | `POST /api/customer/blog/posts/{postId}/comments`; ẩn với Guest |
| Lỗi 403/429 | Hiển thị message tiếng Việt từ backend |

### Form đăng bài

| Trường | Validate client |
|--------|-----------------|
| Nội dung | Bắt buộc, ≤ 1000 ký tự (đếm ký tự hiển thị) |
| Rating | Tuỳ chọn, 1–5 sao |
| Ảnh | Tối đa 3, mỗi ảnh ≤ 1.5 MB, JPEG/PNG/WebP |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Public endpoint | `/api/public/blog/**` → `permitAll` (HR-17) |
| PII qua public | Chỉ `fullName` + `avatarUrl`; **không** email/phone/id nội bộ (FR-008) |
| Tác giả đã xoá mềm | Hiển thị "Người dùng Move_home", không lộ dữ liệu cũ (FR-004) |
| Gate đăng bài | Phải ACTIVE + email verified — chống bot/spam (FR-011) |
| Rate limit | 5 bài/giờ + 20 bình luận/giờ per user (FR-041..FR-044) |
| Ảnh | Signed upload server-side (không lộ API key); delivery public **có chủ ý** |
| Kiểm duyệt | Chỉ Manager; hide có thể đảo ngược, delete là soft delete |
| Cleanup | Destroy Cloudinary asset khi xoá bài (AC-10) |
| XSS | ⚠️ `content` là free text → **frontend PHẢI escape** trước khi render. Backend không sanitize (DS-02) |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-024-01 | Guest gọi feed không cần JWT → 200 | Test không header |
| AC-024-02 | Feed chỉ trả bài VISIBLE + chưa xoá | DB có HIDDEN/deleted → không xuất hiện |
| AC-024-03 | Feed sắp xếp mới nhất trước | Manual |
| AC-024-04 | Feed batch load — không N+1 | Bật `show-sql`, đếm query |
| AC-024-05 | Tác giả xoá mềm → "Người dùng Move_home" | Test |
| AC-024-06 | Feed không lộ email/phone | Grep response |
| AC-024-07 | Customer chưa verify đăng bài → 403 | Test |
| AC-024-08 | Guest đăng bài → 401 | Test |
| AC-024-09 | Nội dung 1001 ký tự → 422 | Test |
| AC-024-10 | `rating = 6` → 422 | Test |
| AC-024-11 | `rating = null` → OK | Test |
| AC-024-12 | Ảnh thứ 4 → lỗi | Test |
| AC-024-13 | Bài thứ 6 trong 1 giờ → 429 | Test |
| AC-024-14 | Bình luận thứ 21 trong 1 giờ → 429 | Test |
| AC-024-15 | Driver bình luận → 403 | Test |
| AC-024-16 | Manager bình luận → `authorRole = MANAGER` | DB check |
| AC-024-17 | Bình luận dưới bài HIDDEN → 404 | Test |
| AC-024-18 | Chủ bài tự bình luận → **không** có notification | DB check |
| AC-024-19 | Người khác bình luận → chủ bài có notification `BLOG_COMMENT` | DB check |
| AC-024-20 | Notification lỗi → bình luận vẫn thành công | Mock throw |
| AC-024-21 | Manager xoá bài → `deleted_at` set + ảnh destroyed | DB + Cloudinary |
| AC-024-22 | Cloudinary destroy lỗi → bài vẫn xoá được | Mock throw |
| AC-024-23 | Admin gọi endpoint kiểm duyệt → 403 | Test RBAC |
| AC-024-24 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Tác giả bị xoá mềm sau khi đăng bài** → bài vẫn hiện, tên hiển thị "Người dùng Move_home"
   (FR-004). Không lộ PII, không crash.
2. **Manager ẩn bài đang có bình luận** → bình luận vẫn tồn tại nhưng không ai vào xem được (bài không
   trên feed); bình luận mới → 404 vì bài không VISIBLE.
3. **Xoá bài có bình luận** → bài soft delete, **bình luận không bị xoá theo** — vẫn còn trong DB với
   `post_id` trỏ tới bài đã xoá. Xem DS-06.
4. **Restart app** → rate limit reset, user spam lại được ngay (FR-044). Chấp nhận cho đồ án.
5. **Hide bài đã HIDDEN** → vẫn 200, idempotent, không lỗi.
6. **Ảnh Cloudinary bị xoá thủ công trên dashboard** → `url` trong DB trỏ tới ảnh chết → `<img>` vỡ.
   Không có health check.
7. **Content chứa HTML/script** → backend lưu nguyên; **frontend phải escape**. Nếu FE dùng
   `innerHTML` không escape → **XSS thật** (DS-02).
8. **3 ảnh × 1.5 MB = 4.5 MB** < `max-request-size = 8MB` → OK. Nhưng `max-file-size = 2MB` per file
   cũng thoả vì mỗi ảnh ≤ 1.5 MB.
9. **Rating trong blog vs `order_rating`** → hoàn toàn tách biệt; rating blog **không** ảnh hưởng
   `driver_profile.average_rating`. Khách có thể chấm 1 sao trên blog mà tài xế vẫn 5 sao.
10. **Bình luận của Manager rồi Manager bị đổi role** → `author_role` đã snapshot nên badge vẫn "Quản lý"
    — đúng ý đồ (lịch sử bất biến).

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-024-01 | Unit | `requireContent("")` | 422 |
| TC-024-02 | Unit | `requireContent(1001 ký tự)` | 422 |
| TC-024-03 | Unit | `requireContent("  abc  ")` | `"abc"` |
| TC-024-04 | Unit | `requireActiveCustomer` chưa verify | 403 |
| TC-024-05 | Unit | `requireActiveCustomer` status SUSPENDED | 403 |
| TC-024-06 | Unit | `toResponse` với author null | "Người dùng Move_home" |
| TC-024-07 | Unit | `BlogRateLimiter.checkPost` lần 6 trong 1h | 429 |
| TC-024-08 | Unit | `BlogRateLimiter` sau khi hết cửa sổ 1h | Cho phép |
| TC-024-09 | Unit | Rating `Short` conversion | `5` → `(short)5` |
| TC-024-10 | Integration | Guest gọi feed | 200 |
| TC-024-11 | Integration | Feed loại bài HIDDEN | Không xuất hiện |
| TC-024-12 | Integration | Feed loại bài soft-deleted | Không xuất hiện |
| TC-024-13 | Integration | `createPost` rating = 6 | 422 |
| TC-024-14 | Integration | `createPost` 4 ảnh | Lỗi |
| TC-024-15 | Integration | `addComment` bởi DRIVER | 403 |
| TC-024-16 | Integration | `addComment` bởi MANAGER | 200, `authorRole=MANAGER` |
| TC-024-17 | Integration | `addComment` bài HIDDEN | 404 |
| TC-024-18 | Integration | `addComment` bởi chủ bài | Không notification |
| TC-024-19 | Integration | `addComment` bởi người khác | Có notification |
| TC-024-20 | Integration | `deletePost` | `deleted_at` set, `destroy` được gọi |
| TC-024-21 | Integration | `deletePost` khi Cloudinary throw | Bài vẫn xoá |
| TC-024-22 | Integration | `setPostHidden(true)` rồi feed | Bài biến mất |
| TC-024-23 | Integration | Admin gọi `/api/manager/blog/**` | 403 |
| TC-024-24 | Integration | `listComments` bài không tồn tại | 404 |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Customer xoá/sửa bài của chính mình** — bản này chỉ Manager xoá được | Khách đăng nhầm phải nhờ Manager gỡ; về nguyên tắc người dùng nên có quyền gỡ nội dung của chính mình | `DELETE /api/customer/blog/posts/{id}` + ownership check. Xem OQ-2 |
| DS-02 | **Sanitize `content` phía server** — bản này dựa hoàn toàn vào FE escape (FR-031) | **Rủi ro bảo mật cao nhất của spec:** UGC công khai + Guest đọc được. Nếu một chỗ nào đó trên FE render bằng `innerHTML` không escape → XSS stored, ảnh hưởng cả khách chưa đăng nhập | Sanitize server-side (allowlist) **hoặc** kiểm tra toàn bộ điểm render dùng `textContent`. Xem OQ-4 |
| DS-03 | Chính sách xoá cho `blog_post_photo` — bảng này không có `deleted_at` trong khi `blog_post` có | Khi xoá bài, ảnh bị destroy trên Cloudinary nhưng hàng DB còn lại với `url` trỏ tới ảnh chết | Thêm `deleted_at` hoặc hard delete hàng ảnh cùng lúc destroy |
| DS-04 | Đưa upload Cloudinary ra ngoài `@Transactional` của `createPost` | Giữ connection pool (max 5) vài giây mỗi bài → nghẽn khi nhiều khách đăng cùng lúc | Upload trước, chỉ mở TX khi INSERT |
| DS-05 | Bổ sung feed blog + `blog-detail.html` vào `SCREEN_INVENTORY.md`; cập nhật CONTEXT §Guest Mode (6 → 8 trang public) | Số màn hình và số trang public báo cáo sai | Cập nhật cùng lúc với OQ-1 |
| DS-06 | **Cascade xoá bình luận khi xoá bài** — bản này không cascade | Bình luận mồ côi trỏ tới bài đã xoá; không hiển thị nhưng tích tụ trong DB | Soft delete cascade hoặc job dọn. Xem OQ-5 |
| DS-07 | Rate limit **in-memory** — reset khi restart, không hoạt động khi scale nhiều instance | Chấp nhận được cho phạm vi đồ án (1 instance). Production cần Redis | Chuyển sang Redis khi scale |
| DS-08 | **Làm rõ quan hệ với HR-16** — bản này rate limit theo **user** (5 bài/giờ, 20 bình luận/giờ), HR-16 quy định 60 req/IP/phút cho mọi POST | Không rõ blog có phải tuân HR-16 song song không; hai cơ chế có thể chồng chéo | Làm rõ HR-16 áp dụng cho tầng nào |
| DS-09 | Trang cá nhân tác giả — index `idx_blog_post_author` đã thiết kế sẵn nhưng bản này chưa dùng | Index tốn chi phí ghi mà chưa có người đọc | Giữ; sẽ dùng khi làm trang cá nhân |
| DS-10 | **Làm rõ hai hệ thống rating** — `blog_post.rating` (công khai, không ảnh hưởng tài xế) vs `order_rating` (Spec #026, ảnh hưởng `driver_profile.average_rating`) | Hai thang sao song song dễ gây nhầm khi báo cáo: khách chấm 1 sao trên blog mà tài xế vẫn 5 sao | Ghi rõ trong CONTEXT §7 khi thêm feature #31. Xem OQ-6 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Blog có được công nhận là feature chính thức không?** Nếu có → thêm vào CONTEXT §7 (feature #31), sửa Spec #017 Out-of-scope #1, cập nhật §Guest Mode (6 → 8 trang public) | Toàn bộ spec | **High** |
| OQ-2 | Customer có được xoá bài của mình không? (DS-01) | DS-01 | **High** |
| OQ-3 | **Admin có quyền kiểm duyệt blog không?** Hiện Admin bị 403 hoàn toàn — kể cả xem danh sách bài ẩn | — | Medium |
| OQ-4 | **Chống XSS: sanitize server-side hay dựa vào FE escape?** UGC công khai + Guest đọc được → phải chốt trước Pha A (DS-02) | DS-02 | **High** |
| OQ-5 | Xoá bài có nên cascade xoá bình luận không? (DS-06) | — | Medium |
| OQ-6 | Rating blog có nên gộp với `order_rating` không, hay giữ tách? (DS-10) | — | Low |
| OQ-7 | Có cần trang kiểm duyệt riêng cho Manager không? Hiện Manager phải biết `postId` để gọi API — **không có UI** | — | Medium |

---

## Rollout Plan

> ⛔ **Điều kiện tiên quyết:** OQ-1 phải được leader duyệt trước — Spec #017 đang loại trừ blog tường
> minh, nên triển khai khi chưa sửa #017 là đi ngược một spec đã duyệt.

**Phụ thuộc:** Spec #020 (notification) — thông báo cho chủ bài khi có phản hồi (FR-031).

**Giai đoạn 0 — Duyệt:**

1. Leader duyệt OQ-1: thêm feature #31 vào CONTEXT §7, sửa Spec #017 Out-of-scope #1, nâng Guest Mode
   6 → 8 trang public, thêm 3 dòng RBAC.
2. Chốt OQ-4 (chiến lược chống XSS) **trước khi** mở feed cho Guest.

**Pha A — Đăng bài + feed:**

3. `V42` — `blog_post` + `blog_post_photo`. Không đụng bảng hiện có, không backfill.
4. `POST /api/customer/blog/posts` + `GET /api/public/blog/feed`.
5. Feed trên homepage. **Kiểm tra escape XSS trước khi mở public.**

**Pha B — Bình luận:**

6. `V43` — `blog_comment`.
7. Customer + Manager bình luận; notification cho chủ bài.
8. `public/blog-detail.html`.

**Pha C — Kiểm duyệt + chống spam:**

9. `BlogRateLimiter` (không cần migration).
10. Endpoint hide/unhide/delete cho Manager.

**Rủi ro cần theo dõi khi rollout:**

- **DS-02 (XSS)** là rủi ro cao nhất: đây là feature duy nhất trong dự án cho phép người dùng đăng nội
  dung tự do mà **Guest chưa đăng nhập đọc được**. Phải chốt trước Pha A.
- Rate limit chỉ bật ở Pha C — trong khi Pha A/B đã mở cho người dùng thật. Cân nhắc đưa
  `BlogRateLimiter` lên Pha A để tránh cửa sổ spam.
- Cleanup Cloudinary (FR-023) là luồng **duy nhất trong dự án** làm đúng AC-10 cleanup — dùng làm mẫu
  cho DS-08 của Spec #022 và #023.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #24 Community Blog  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | PASS | Cloudinary qua env |
| HR-02 BCrypt | N/A | |
| HR-03/04 IPN | N/A | |
| HR-05 Transition sai → 409 | N/A | Không có state machine nghiệp vụ |
| HR-06/07 DamageReport | N/A | |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | FR-011, FR-026, FR-040 |
| HR-11 Email không rollback | PASS (tinh thần) | FR-033 notification try/catch |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log | ⚠️ **GAP** | Kiểm duyệt (hide/delete bài, bình luận) **không ghi `AuditLog`** — chỉ `log.info`. HR-13 yêu cầu audit cho state change; blog không nằm trong danh sách entity của HR-13 (Order/Trip/RefundRecord/DamageReport) nên **có thể coi N/A**, nhưng kiểm duyệt là hành động quyền lực nên nên có |
| HR-14 RefundRecord | N/A | |
| HR-15 Idempotency | N/A | |
| HR-16 Rate limit | ⚠️ **PARTIAL** | Có rate limit (5 bài/giờ, 20 bình luận/giờ) nhưng theo **user**, không theo **IP 60 req/phút** như HR-16 quy định. Chặt hơn về nghiệp vụ nhưng khác cơ chế (DS-08) |
| HR-17 Public không lộ PII | PASS | FR-004, FR-008 — chỉ `fullName` + `avatar`; tác giả xoá → tên trung tính |
| HR-18 Wallet | N/A | |
| HR-19 Brand identity | PASS | Badge "Quản lý" forest green |
| HR-20 Tiếng Việt có dấu | PASS | Toàn bộ message **có dấu đầy đủ** |
| HR-21 Tránh reserved words | PASS | `blog_post`, `blog_post_photo`, `blog_comment` |

**Layer 1 Result:** 2 vấn đề — HR-13 (thiếu audit kiểm duyệt), HR-16 (khác cơ chế rate limit).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | PASS | JPA |
| AC-05 Chat | N/A | |
| AC-06 Maps | N/A | |
| AC-07 Timezone | PASS | TIMESTAMPTZ UTC |
| AC-08 BigDecimal | N/A | Blog không đụng tiền |
| AC-09 Soft delete | ✅ **PASS** | `blog_post` + `blog_comment` có `deleted_at` + `@SQLDelete`. **Lệch nhẹ:** `blog_post_photo` không có (DS-03) |
| AC-10 Cloudinary | ✅ **PASS** | Signed upload server-side ✅, magic number ✅, ≤1.5MB ✅, ≤3 ảnh ✅, **cleanup `destroy()` khi xoá bài ✅** (luồng duy nhất trong dự án làm đúng cleanup!). **Lệch có chủ ý:** delivery `type=upload` public thay vì signed URL — vì feed công khai |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V42, V43 |
| AC-13 Money audit | N/A | |
| AC-14 VARCHAR + CHECK | PASS | `status`, `author_role`, `rating` |
| AC-15 Pagination | PASS | Default 10, max 50 |
| AC-16 Empty/Loading/Error | PASS | Feed + chi tiết |

**Layer 2 Result:** ALL PASS; AC-10 lệch delivery **có lý do chính đáng**; AC-09 lệch nhẹ ở bảng ảnh.

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PARTIAL | `/hide`, `/unhide` là verb — chấp nhận cho action sub-resource |
| ES-03 Bean Validation + 422 | PARTIAL | `@Valid` cho `CreateBlogCommentRequest`; đăng bài dùng `@RequestParam` multipart nên validate thủ công |
| ES-04 Error format | PARTIAL | `"CODE\|Message"` map qua advice |
| ES-05 Test coverage | ⚠️ **CHƯA VERIFY** | Blog là SHELL — chỉ cần integration test happy path. Đã thấy `BlogServiceTest` tồn tại |
| ES-06/07 Commits | PASS | `feat(blog): Blog cong dong homepage (feed, binh luan, kiem duyet)` |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 19/21 PASS, 2 vấn đề (HR-13 audit, HR-16 cơ chế rate limit)  
Layer 2 : 16/16 PASS (AC-09/AC-10 lệch nhẹ có lý do)  
Layer 3 : 5/8 PASS, ES-02/03/04 partial, ES-05 chưa verify  
Status  : **BLOCKED — chờ leader quyết OQ-1** (blog có được công nhận không, vì Spec #017 đang ghi
out-of-scope) **và OQ-4** (XSS — cần kiểm tra ngay).
Nếu OQ-1 = có → cập nhật CONTEXT §7 + §Guest Mode + Spec #017, spec này chuyển `Approved`.
================================
