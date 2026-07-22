# Tasks: Public Marketing Pages — Spec #017

> ⚠️ **Build một phần.** Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ chưa làm · 🚫 blocked

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | 6 trang public (landing/about/how-it-works/pricing/contact/terms) | `frontend/pages/public/*` + `*.html` | Goals | ✅ |
| T-02 | Pricing calculator client-side (reference snapshot, không API) | FE + estimate-step1..5 | Goals | ✅ |
| T-03 | Guest ước tính giá `PublicQuoteController` | `controller/PublicQuoteController.java` | Goals | ✅ |
| T-04 | Nội dung chỉ mô tả capability thật (gỡ GPS realtime/bảo hiểm) | FE | Goals | ✅ |
| T-05 | `/api/public/**` permitAll (HR-17) | `SecurityConfig` | Goals | ✅ |
| T-06 | **`POST /api/public/contact` + bảng `contact_submission`** (validation + rate limit 3/h + honeypot + email async) | — | Goals | 🚫 chưa build |
| T-07 | Migration `contact_submission` (cần leader cấp số) | — | Data Model | 🚫 chờ số |

**Done:** T-01..T-05 ✅ (FE + calculator + estimate). **T-06/T-07 CHƯA build** — contact form BE + bảng
thiếu (grep + migration rỗng 2026-06-24). Blog out-of-scope (#024).
