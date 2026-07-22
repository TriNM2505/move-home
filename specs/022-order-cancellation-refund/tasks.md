# Tasks: Order Cancellation Refund — Spec #022

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ deferred · 🚫 blocked

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration `order_cancellation_refund` + `order_cancellation_photo` + CHECK terminal | `V41` | Data Model | ✅ |
| T-02 | Entity + repository (FIFO pending, theo khách) | `order/*.java` | Data Model | ✅ |
| T-03 | `PUT orders/{id}/cancel` — nhánh CONFIRMED + driver=null → mở refund PENDING | `CustomerOrderActionService` | Scope 1–2 | ✅ |
| T-04 | Tính cọc `FLOOR(total_quote × commission_rate_snapshot)` (BigDecimal AC-08) | service | Canonical | ✅ |
| T-05 | Upload ảnh bằng chứng ≤3 (Cloudinary signed) | `OrderCancellationPhotoService` | Scope 3, AC-10 | ✅ |
| T-06 | Manager hàng đợi FIFO + filter status | `ManagerCancellationRefundService` | Scope 4 | ✅ |
| T-07 | Manager chi tiết + ảnh qua signed URL | service/controller | Scope 5 | ✅ |
| T-08 | Manager **Hoàn cọc** (lock ví, REFUND cùng TX, audit, notify) | service | Scope 6, AC-13/HR-18 | ✅ |
| T-09 | Manager **Từ chối** (không đụng tiền, lý do, notify) | service | Scope 7 | ✅ |
| T-10 | RBAC MANAGER (không phải Admin) + guard trạng thái (HR-05) | controller | Canonical, HR-10 | ✅ |
| T-11 | FE `manager/cancellation-refunds.html` (L/E/E states) | `frontend/pages/manager/cancellation-refunds.html` | Scope 10 | ✅ |
| T-12 | **Làm rõ luồng COMPANY hủy đơn** (RefundRecord vs không có bảng) | — | OQ-1 | ⏳ |
| T-13 | Amendment CONTEXT §Wallet (gỡ mâu thuẫn "không có ví Customer") | — | OQ-1 / #021 | 🚫 blocked (theo #021) |

**Done (code):** T-01..T-11 ✅. Vướng: đích tiền là `customer_wallet` của #021 (BLOCKED) → T-13 chờ
#021 OQ-1. T-12 (COMPANY cancel) là khoảng trống chính sách cần leader.
