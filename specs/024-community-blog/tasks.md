# Tasks: Community Blog — Spec #024

> 🚫 **CHƯA HIỆN THỰC & BLOCKED.** Không V42/V43, không code (chỉ FE stub blog-detail).
> Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). 🚫 blocked · ⏳ chưa làm

| ID | Task | Pha | Spec ref | TT |
|----|------|-----|----------|----|
| T-00 | **Chốt OQ-1** (blog vào scope? gỡ mâu thuẫn Spec #017) + amend CONTEXT + cấp số `V42`/`V43` | — | OQ-1 | 🚫 blocked |
| T-01 | Migration `blog_post` + `blog_post_photo` (`V42` — chờ số) | A | Data Model | ⏳ |
| T-02 | Migration `blog_comment` (`V43` — chờ số) | B | Data Model | ⏳ |
| T-03 | Entity + repository (soft delete AC-09) | A | Data Model | ⏳ |
| T-04 | Đăng bài (≤1000 ký tự, rating tuỳ chọn) + ảnh ≤3 (Cloudinary `type=upload`) | A | Goals | ⏳ |
| T-05 | Feed public (ẩn PII tác giả đã xoá mềm — HR-17) | A | Goals | ⏳ |
| T-06 | Bình luận (Customer/Manager badge) + notification `BLOG_COMMENT` | B | #020 | ⏳ |
| T-07 | Kiểm duyệt: ẩn/hiện bài+bình luận, xoá mềm + dọn ảnh Cloudinary (AC-10) | C | Goals | ⏳ |
| T-08 | Chống spam: rate limit 5 bài/giờ + 20 bình luận/giờ (HR-16) | C | Goals | ⏳ |
| T-09 | FE homepage feed + hoàn thiện `public/blog-detail.html` (hiện chỉ stub) | A–C | Screen | ⏳ (stub) |

**Định nghĩa Done:** T-00 blocker phải xong. Toàn bộ ⏳/🚫 — **chưa build**. FE `blog-detail.html` chỉ
là stub HTML, chưa nối BE.
