# Tasks: Driver Onboarding (4-Step) — Spec #005

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration driver_profile onboarding + driver_document | V4/V10/V14 (+V29/31/32 doc types) | Data Model | ✅ |
| T-02 | Onboarding status/step/next-action + checklist giấy tờ | onboarding service | Scope 2–3 | ✅ |
| T-03 | Upload giấy tờ (GPLX/đăng ký/3 ảnh xe) Cloudinary signed (AC-10) | `DriverDocumentService` | Scope 4–5 | ✅ |
| T-04 | Tạo xe onboarding đầu tiên | vehicle service | Scope 6 | ✅ |
| T-05 | Cọc 3tr VNPay; chỉ IPN verified → PENDING_APPROVAL (HR-03/04) | `VnPay*` | Scope 7 | ✅ |
| T-06 | Ghi transaction DEPOSIT_TOP_UP + snapshot deposit_amount | service | Data Model | ✅ |
| T-07 | Guard không skip bước (theo app_user.status) | onboarding service | Goals | ✅ |
| T-08 | FE register-step1..3-deposit, pending-approval, driver-terms | `frontend/pages/driver/*` | Screen | ✅ |
| T-09 | Full re-submit edit flow khi REJECTED | — | #008 | ⏳ |

**Done:** T-01..T-08 ✅ (CORE nguồn cung Driver). T-09 chi tiết ở #008.
