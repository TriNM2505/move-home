# Tasks: Manager Disputes Resolution — Spec #010

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration dispute + evidence + comment + photo + pending_deduct | V16/V34/V35/V37 | Data Model | ✅ |
| T-02 | Xem toàn cảnh dispute (2 bên, ảnh signed URL, lịch sử, comment) | `DisputeService` | Goals | ✅ |
| T-03 | 3 outcome: RESOLVED_REFUND / RESOLVED_DEDUCT / CLOSED_NO_FAULT | `DisputeService` | Goals | ✅ |
| T-04 | Trừ tài xế: ví → cọc (thiếu → shortfall + deadline → SUSPENDED) | service + `PenaltyEnforcementScheduler` (V34) | Goals | ✅ |
| T-05 | Hoàn khách → customer_wallet + REFUND | `CustomerRefundService` | Goals | ✅ |
| T-06 | Lock dispute trước ví, atomic + audit cùng TX (rollback nếu audit fail) | service | Goals | ✅ |
| T-07 | RBAC MANAGER+ADMIN | controller | Canonical | ✅ |
| T-08 | FE disputes, dispute-detail (+ deep-link chat) | `frontend/pages/manager/*` | Screen | ✅ |
| T-09 | Đồng bộ CONTEXT §DamageReport (mô hình dispute + 24h) | — | D-03/D-13 | ⏳ |
| T-10 | Chuyển IN_DISPUTE → COMPLETED/CANCELLED sau resolve | — | "integration future" | ⏳ |

**Done:** T-01..T-08 ✅. ⚠️ T-05 (hoàn khách) phụ thuộc ví #021 (D-11). T-09/T-10 tồn.
