# Tasks: Admin Audit Log Viewer — Spec #025

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ deferred

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration `audit_log` + 3 index (append-only) | `V22` | Data Model | ✅ |
| T-02 | `AuditLog` entity + repository (query động 4 filter) | `.../AuditLog*.java` | Scope 4 | ✅ |
| T-03 | `AuditService.log()` best-effort + `AuditLogWriter` | `.../Audit*.java` | Scope 2–3 | ✅ |
| T-04 | `GET /api/admin/audit-logs` + filter `action/entityType/from/to` + pagination | `AuditLogController` | Scope 1, 4 | ✅ |
| T-05 | RBAC ADMIN + MANAGER | controller | Scope 5, HR-10 | ✅ |
| T-06 | Không log PII (`actorEmail`/`detail`) ra file | `AuditService` | Goals | ✅ |
| T-07 | FE `admin/audit-log.html` (L/E/E states) | `frontend/pages/admin/audit-log.html` | Scope 6 | ✅ |
| T-08 | Sửa Spec #011 Out-of-scope #9 → "thuộc Spec #025" | `specs/011-*/spec.md` | Source-of-Truth | ⏳ |
| T-09 | Thêm dòng CONTEXT §3 RBAC "xem nhật ký hệ thống" (Admin/Manager) | `docs/CONTEXT.md` | OQ-1 | ⏳ |
| T-10 | Đối chiếu 6 field HR-13 vs cột thật (from_state/to_state/actor_role trong `detail`) | — | DS-01 | ⏳ |
| T-11 | Filter theo `actor` + xuất CSV | — | DS-02 | ⏳ |

**Done (code):** T-01..T-07 ✅. TODO tài liệu: T-08/T-09 (gỡ mâu thuẫn #011 + CONTEXT RBAC), T-10 (đối chiếu HR-13).
