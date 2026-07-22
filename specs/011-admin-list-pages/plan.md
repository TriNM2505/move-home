# Implementation Plan: Admin List Pages — Spec #011

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** không bảng mới (đọc entity có sẵn; search pg_trgm index). **Status:** As-built (CORE oversight).

## 1. Architectural Approach

4 trang danh sách Admin (orders/drivers/customers/withdrawals) với **server-side pagination** (10/20/50/
100), search debounce 300ms (case/accent-insensitive, bound params, pg_trgm GIN), status filter, sort
**allowlist** + secondary key `id` (chống injection tên cột, AC-04), badge tiếng Việt theo status. Chỉ
**ADMIN** (HR-10). Loại soft-deleted mặc định. Audit `ADMIN_LIST_ACCESSED` throttle 60s (không spam mỗi
poll). Auto-refresh 30s opt-in (pause khi tab ẩn/tương tác). Bank data masked. Row click → detail (#012).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AdminListService` | Query 4 list + filter/sort/search | `service/AdminListService.java` |
| `AdminListController` | Endpoint `/api/admin/{orders,drivers,customers,withdrawals}` | `controller/AdminListController.java` |
| `admin-common.js` | Pagination UI, ellipsis, page-size | `frontend/js/admin-common.js` |
| FE orders/drivers/customers/withdrawals | 4 màn (site-layout shell) | `frontend/pages/admin/*` |

## 3. Dependencies
Đọc `service_order`/`app_user`/`driver_profile`/`withdrawal_request`. Phụ thuộc #001 (RBAC). Row → #012.
**Out-of-scope #9 "Full audit-log viewer" đã tách sang #025** (sửa 2026-06-24).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Injection qua sort/search | Cao | Allowlist sort + bound params (AC-04) |
| Poll 30s spam audit | TB | Throttle 60s |
| Lộ soft-deleted/bank | TB | Filter `deleted_at` + mask |

## 5. Questions for Human
- Export CSV / bulk actions: defer Sprint 6+.

## 6. Constitution Check (tóm tắt)
HR-10/13, AC-04/15/16. Chi tiết: [`spec.md`](spec.md).
