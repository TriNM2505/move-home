# Tasks: Admin Commission Settings — Spec #014

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration commission_settings (singleton) + history | V16 | Data Model | ✅ |
| T-02 | Get config hiện tại | commission service | Goals | ✅ |
| T-03 | Patch config (validate range CHECK + diff + optimistic version) | service | Goals | ✅ |
| T-04 | Ghi history snapshot + audit trong cùng TX | service | Goals | ✅ |
| T-05 | Preview 5 sample order trước khi lưu | service | Goals | ✅ |
| T-06 | Backward-compat: order/booking/deposit/withdrawal cũ giữ snapshot | pricing engine | Invariant | ✅ |
| T-07 | Email async cho Admin khác (không rollback) | `EmailService` | Goals | ✅ |
| T-08 | Chỉ ADMIN | controller | HR-10 | ✅ |
| T-09 | FE admin/commission-settings | `frontend/pages/admin/commission-settings.html` | Screen | ✅ |

**Done:** T-01..T-09 ✅ (SUPPORT config money với snapshot pattern).
