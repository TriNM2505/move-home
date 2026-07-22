# Implementation Plan: Admin Dashboard — Spec #015

> Ref: [`spec.md`](spec.md) v1.0.0 (MINI/demo).
> **Migration:** đọc entity có sẵn + V99 seed. **Status:** As-built (SHELL, ưu tiên demo).
> ℹ️ CLAUDE.md gọi spec này là "028" — số cũ; canonical = **#015**.

## 1. Architectural Approach

1 trang tổng quan cho Admin (demo Thu Ba 2026-06-02): **6 KPI** (tổng đơn, doanh thu commission, tỷ lệ
hoàn thành; Driver ACTIVE, Driver chờ duyệt, đơn tranh chấp) + **2 chart Chart.js** (bar 30 ngày, line
12 tháng) + **2 table** (Driver PENDING_APPROVAL top 10, order gần nhất top 10). KPI "today/month" tính
theo **Asia/Ho_Chi_Minh** (AC-07). Chỉ ADMIN (HR-10). Seed V99 để chart/table đẹp khi demo.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AdminDashboardService` | KPI + revenue chart + top drivers + recent orders + status dist | `service/AdminDashboardService.java` |
| `AdminDashboardController` | `/api/admin/dashboard/**` (@PreAuthorize ADMIN) | `controller/AdminDashboardController.java` |
| FE `dashboard.js` + `charts-config.js` | Fetch + render KPI/chart | `frontend/js/dashboard.js` |
| FE `admin/dashboard.html` | Layout 2×3 KPI + chart + table | `frontend/pages/admin/dashboard.html` |

## 3. Dependencies
Đọc `service_order`/`app_user`/`driver_profile`/`transaction` + V99 seed. Phụ thuộc #001 (RBAC Admin).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Tính "today/month" sai timezone | TB | ZoneId Asia/Ho_Chi_Minh (AC-07) |
| Seed count lệch tài liệu | Thấp | Verify V99 (xem PROJECT_KNOWLEDGE §2.9) |

## 5. Questions for Human
- Realtime/date-range/export/drill-down: defer phase 2 (Out of scope).

## 6. Constitution Check (tóm tắt)
HR-10/13, AC-07/08. Chi tiết: [`spec.md`](spec.md).
