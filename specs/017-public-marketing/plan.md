# Implementation Plan: Public Marketing Pages — Spec #017

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Status:** ⚠️ **Build một phần** — FE pages có; **`/api/public/contact` + bảng `contact_submission`
> CHƯA build** (grep + migration rỗng 2026-06-24).

## 1. Architectural Approach

6 trang public (không JWT): landing, about, how-it-works, pricing, contact+FAQ, terms/privacy. Brand
Move_home (HR-19), tiếng Việt có dấu (HR-20), mobile-first. **Pricing calculator client-side** (không gọi
API, dùng public reference snapshot cùng shape công thức #002 — không phải quote authoritative). **Contact
form** = backend public duy nhất `/api/public/contact` (validation + rate limit 3/h + honeypot +
persistence `contact_submission` + email async) — ⚠️ **phần này chưa build**. Nội dung chỉ mô tả
capability **thật** (không quảng cáo GPS realtime/bảo hiểm). Blog **out-of-scope** (liên quan #024).

## 2. Components

| Thành phần | Trạng thái | File |
|------------|-----------|------|
| FE 6 trang public + estimate 1–5 (Guest calculator) | ✅ build | `frontend/pages/public/*`, `about/contact/pricing/how-it-works/terms.html` |
| `PublicQuoteController` (ước tính giá) | ✅ build | `controller/PublicQuoteController.java` |
| `POST /api/public/contact` + `contact_submission` | ❌ **chưa build** | (chưa có) |

## 3. Dependencies
`/api/public/**` permitAll (HR-17, #001). Contact cần **migration mới** (`contact_submission` — cần leader
cấp số) + rate limit (HR-16).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| **Contact form FE có nhưng BE chưa** → gửi lỗi | TB | Build `/api/public/contact` + bảng (cần số migration) |
| Calculator bị hiểu là quote thật | Thấp | Nhãn "ước tính", client-side |
| Public endpoint lộ PII | TB | Chỉ contact; honeypot + rate limit (HR-17) |

## 5. Questions for Human
- **Contact form:** build `/api/public/contact` + `contact_submission`? → cần **cấp số migration**.

## 6. Constitution Check (tóm tắt)
HR-16/17/19/20, AC-16. Chi tiết: [`spec.md`](spec.md).
