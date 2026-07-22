# Tasks: Customer Orders Management — Spec #003

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳ deferred

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | List pending/active/history (server-side pagination) | `CustomerOrderQueryService` | Scope 1–3 | ✅ |
| T-02 | Chi tiết đơn (owner-only) + timeline từ order_audit_log | `CustomerOrderQueryService` | Scope 4 | ✅ |
| T-03 | Cancel hợp lệ (guard trạng thái → 409, HR-05) | `CustomerOrderActionService` | Scope 5 | ✅ |
| T-04 | Vị trí Driver cho active order (Leaflet+OSM, polling 5s) | `driver_location` (V20) | Scope 6 | ✅ |
| T-05 | Rate-form eligibility + tạo đánh giá (1–5 sao, tags, comment) | `order_rating` (V9) | Scope 7 | ✅ |
| T-06 | FE my-orders-pending/active/history, order-detail, order-rate | `frontend/pages/customer/*` | Screen | ✅ |
| T-07 | Đồng bộ cửa sổ rating 2h→24h (theo #026) | — | D-13 | ⏳ |
| T-08 | Realtime WebSocket/SSE thay polling | — | Deferred | ⏳ |

**Done:** T-01..T-06 ✅. T-07 (rating window) đồng bộ theo #026/CONTEXT D-13.
