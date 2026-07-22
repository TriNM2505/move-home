# Tasks: Admin Dashboard — Spec #015

> As-built (MINI/demo). Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ℹ️ CLAUDE.md gọi "028" là số cũ; canonical #015. ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | 6 KPI (đơn, doanh thu commission, tỷ lệ hoàn thành; Driver ACTIVE/chờ duyệt/tranh chấp) | `AdminDashboardService` | Scope 2 | ✅ |
| T-02 | 2 chart Chart.js (bar 30 ngày, line 12 tháng) | service + `charts-config.js` | Scope 3 | ✅ |
| T-03 | 2 table (Driver PENDING_APPROVAL top 10, order gần nhất top 10) | service | Scope 4 | ✅ |
| T-04 | KPI today/month theo Asia/Ho_Chi_Minh (AC-07) | service | Scope | ✅ |
| T-05 | Auth guard chỉ ADMIN (HR-10) | `AdminDashboardController` | Scope 1,6 | ✅ |
| T-06 | Seed V99 để chart/table đẹp khi demo | `V99` | Scope 5 | ✅ |
| T-07 | FE admin/dashboard + dashboard.js | `frontend/pages/admin/dashboard.html` | Screen | ✅ |
| T-08 | Realtime/date-range/export/drill-down | — | Out of scope phase 2 | ⏳ |

**Done:** T-01..T-07 ✅ (SHELL demo).
