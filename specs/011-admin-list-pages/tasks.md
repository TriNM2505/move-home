# Tasks: Admin List Pages — Spec #011

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | 4 list endpoint (orders/drivers/customers/withdrawals) server-side pagination | `AdminListController`/`AdminListService` | Goals | ✅ |
| T-02 | Search debounce 300ms (pg_trgm, bound params) + status filter | service | Goals | ✅ |
| T-03 | Sort allowlist + secondary key id (AC-04) | service | Goals | ✅ |
| T-04 | Badge tiếng Việt theo status + mask bank | FE + service | Goals | ✅ |
| T-05 | Chỉ ADMIN (HR-10) + loại soft-deleted | controller | Canonical | ✅ |
| T-06 | Audit ADMIN_LIST_ACCESSED throttle 60s | `AuditService` | Goals | ✅ |
| T-07 | Auto-refresh 30s opt-in (pause khi ẩn/tương tác) | `admin-common.js` | Goals | ✅ |
| T-08 | FE orders/drivers/customers/withdrawals | `frontend/pages/admin/*` | Screen | ✅ |
| T-09 | Out-of-scope #9 audit-viewer → tách #025 | `spec.md` | D-14 | ✅ (sửa 2026-06-24) |
| T-10 | Export CSV / bulk actions | — | Deferred | ⏳ |

**Done:** T-01..T-09 ✅. Export/bulk defer.
