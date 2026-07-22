# Tasks: Admin Detail Pages — Spec #012

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Order detail (parties/route/pricing/payment/tx/rating/dispute/timeline) | `AdminOrderDetailService` | Goals | ✅ |
| T-02 | Driver detail (6 document signed URL, deposit, earnings, withdrawals, ratings) | `AdminDetailService` | Goals | ✅ |
| T-03 | Customer detail (profile/stats/orders/tx/dispute/login, privacy district-only) | `AdminDetailService` | Goals | ✅ |
| T-04 | Suspend/reactivate Customer/Driver (confirm + lý do + lock + revoke token + audit) | `AdminUserAccountService` (V27) | Goals | ✅ |
| T-05 | Suspension không phá state machine đơn đang chạy | service | Goals | ✅ |
| T-06 | Audit-log theo entity `GET /api/admin/{entityType}/{id}/audit-log` | `AdminDetailController` | Goals | ✅ |
| T-07 | Chỉ ADMIN + không trả secret/token/raw URL | controller | Canonical | ✅ |
| T-08 | FE order-detail, driver-detail, customer-detail | `frontend/pages/admin/*` | Screen | ✅ |
| T-09 | Force-cancel + export PDF (disabled) | — | Deferred Sprint 6+ | ⏳ |

**Done:** T-01..T-08 ✅.
