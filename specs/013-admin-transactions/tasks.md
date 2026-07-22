# Tasks: Admin System Transactions — Spec #013

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Danh sách giao dịch (append-only) + filter kết hợp | admin transaction service | Goals | ✅ |
| T-02 | KPI theo kỳ (inflow/outflow xác nhận, platform fee, pending withdrawal) | service | Goals | ✅ |
| T-03 | Biểu đồ theo loại (stacked bar time series) | FE + service | Goals | ✅ |
| T-04 | Chi tiết liên kết (truy về order/withdrawal/dispute/payment) | service | Goals | ✅ |
| T-05 | Báo cáo reconciliation (kiểm từng invariant) | service | Goals | ✅ |
| T-06 | Chỉ ADMIN + mask ref nhạy cảm + không mutate | controller | Goals | ✅ |
| T-07 | FE admin/transactions + admin-transactions.js | `frontend/pages/admin/transactions.html` | Screen | ✅ |

**Done:** T-01..T-07 ✅ (sổ cái dùng chung; `wallet_transaction` = projection, không bảng thứ 2).
