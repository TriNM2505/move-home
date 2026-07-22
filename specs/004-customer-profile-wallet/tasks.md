# Tasks: Customer Profile & Wallet — Spec #004

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳ · 🚫

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration avatar_url | V33 | Data Model | ✅ |
| T-02 | Profile get/edit-form/patch (email chỉ đọc) | `CustomerProfileService` | Scope 1–3 | ✅ |
| T-03 | Avatar signed Cloudinary upload (signature + confirm, AC-10) | service + `CloudinaryConfig` | Scope 4–5 | ✅ |
| T-04 | Change-password (verify cũ + revoke sessions, HR-02) | `CustomerProfileService` | Scope 6 | ✅ |
| T-05 | `my-wallet.html` = "Lịch sử thanh toán chỉ đọc" (đọc transaction) | FE + service | Goals | ⚠️ mâu thuẫn #021 |
| T-06 | **Chốt: my-wallet là lịch-sử-chỉ-đọc (#004) hay ví thật (#021)?** | — | D-11 | 🚫 blocked |

**Done:** T-01..T-04 ✅. T-05 mâu thuẫn với ví #021 đã build → **T-06 chờ leader (D-11)**.
