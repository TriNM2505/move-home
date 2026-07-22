# Implementation Plan: Community Blog — Spec #024

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Status:** 🚫 **CHƯA HIỆN THỰC & BLOCKED trên nhánh `upload/spec`.** Spec = *Draft — BLOCKED chờ
> leader duyệt OQ-1*. Không có `V42`/`V43` (max = V41), không code Java blog (grep rỗng 2026-06-24),
> chỉ FE stub `frontend/pages/public/blog-detail.html`. ⚠️ **Memory cũ "blog Pha A xong (V42)" lỗi thời
> cho nhánh này.**

## 1. Architectural Approach (đề xuất)

Blog cộng đồng (Community Wall): Customer đã xác thực đăng bài review (≤1000 ký tự, rating 1–5 tuỳ chọn,
≤3 ảnh) + bình luận; Guest **xem** feed + bình luận (nội dung marketing công khai — social proof);
Manager bình luận (badge "Quản lý") + kiểm duyệt (ẩn/hiện, xoá mềm + dọn ảnh Cloudinary). 3 pha: **A**
đăng bài+feed, **B** bình luận, **C** kiểm duyệt + chống spam.

Khác các luồng ảnh khác (dispute/cancellation dùng `type=authenticated` + signed URL), ảnh blog dùng
Cloudinary **`type=upload`** (public delivery) vì feed công khai — nhưng vẫn signed upload server-side
(AC-10). Chống spam: rate limit 5 bài/giờ + 20 bình luận/giờ (in-memory sliding window). Không lộ PII
(HR-17): tác giả đã xoá mềm hiển thị "Người dùng Move_home".

## 2. Components (dự kiến — CHƯA tồn tại)

| Thành phần | Trách nhiệm | File (dự kiến) |
|------------|-------------|----------------|
| `BlogPost`, `BlogPostPhoto`, `BlogComment` | Entity | (chưa có) |
| `BlogService`, `BlogPhotoService` | Đăng bài/feed, ảnh, bình luận, kiểm duyệt | (chưa có) |
| `BlogController` (public + authenticated) | REST feed/post/comment/moderation | (chưa có) |
| FE homepage feed + `public/blog-detail.html` | UI (chỉ stub HTML tồn tại) | `frontend/pages/public/blog-detail.html` |

## 3. Dependencies

`V42` (blog_post + blog_post_photo) + `V43` (blog_comment) — **chưa tạo, cần leader cấp số**. Phụ thuộc
#020 (notification `BLOG_COMMENT`), #001 (auth), #017 (public marketing — ⚠️ mâu thuẫn).

## 4. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| **Mâu thuẫn Spec #017** — blog nằm trong Out-of-scope của #017 | **Cao** | Leader quyết đưa blog vào scope (feature #31) hay bỏ | OQ-1 |
| CONTEXT không nhắc blog (feature mới hoàn toàn) | TB | Amendment CONTEXT §7 nếu duyệt | OQ-1 |
| UGC công khai → spam, PII, XSS (nội dung người dùng) | Cao | Rate limit + escape + ẩn PII (HR-17) + soft delete | Goals |
| Ảnh `type=upload` public khác pattern authenticated toàn dự án | TB | Ghi rõ ngoại lệ có chủ ý | Goals |

## 5. Questions for Human (BLOCKER)

- **OQ-1 (BLOCKER):** Blog có vào scope không? (Spec #017 loại trừ tường minh; CONTEXT không nhắc). Nếu
  có → feature #31 + amendment CONTEXT + **cấp số `V42`/`V43`**. Nếu không → huỷ spec, xoá FE stub.

## 6. Constitution Check (tóm tắt)

Chưa build → chưa chạy thực chất. Sẽ áp HR-16 (rate limit), HR-17 (không lộ PII public), AC-09 (soft
delete), AC-10 (Cloudinary). Chi tiết: spec §Constitution Compliance.
