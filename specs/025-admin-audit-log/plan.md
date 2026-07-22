# Implementation Plan: Admin Audit Log Viewer — Spec #025

> **Reconstructed plan (spec-after)** — tái dựng từ code + [`spec.md`](spec.md) v1.0.0.
> **Migration:** V22. **Status:** As-built.

## 1. Architectural Approach

**Mặt đọc** của hạ tầng audit (HR-13). Mặt ghi `AuditService.log()` dùng khắp dự án (duyệt tài xế, rút
tiền, dispute, hoàn cọc, khoá tài khoản...). Bảng `audit_log` **append-only bất biến** (không
UPDATE/DELETE/soft delete — AC-09 "audit log không được xoá"). Endpoint `GET /api/admin/audit-logs` tra
cứu **toàn cục** với 4 bộ lọc (`action`, `entityType`, `from`, `to`) — khác Spec #012 (audit theo 1
entity). RBAC **ADMIN + MANAGER**. Ghi audit best-effort (`try/catch`, không phá nghiệp vụ; không log
`actorEmail`/`detail` ra file để tránh rò rỉ PII).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AuditLog` entity | Bản ghi audit (append-only) | `.../AuditLog.java` |
| `AuditService.log()` | API nội bộ ghi (best-effort) | `.../AuditService.java` |
| `AuditLogWriter` | Tách riêng persist | `.../AuditLogWriter.java` |
| `AuditLogController` | `GET /api/admin/audit-logs` + filter + pagination | `.../AuditLogController.java` |
| `AuditLogQuery`/repository | Query động theo 4 filter | `.../*.java` |
| FE `admin/audit-log.html` | Trang tra cứu (L/E/E states) | `frontend/pages/admin/audit-log.html` |

## 3. Data Flow

```
Mọi service state-change ──AuditService.log(action, entityType, entityId, detail)──REQUIRES_NEW──> audit_log (append-only)
Admin/Manager GET /api/admin/audit-logs?action&entityType&from&to&page ──> AuditLogQuery ──> Page<AuditLogResponse>
```

## 4. Dependencies

`V22` (độc lập) → AuditService + writer → controller/query → FE. 7 spec ghi audit (#008/#009/#010/#012/
#021/#022/#023) là caller.

## 5. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| **Mâu thuẫn Spec #011** — audit-log viewer nằm Out-of-scope #9 của #011 | TB | Sửa #011 #9 → "thuộc Spec #025" | Source-of-Truth |
| `audit_log` chỉ có `action/entity_type/entity_id/detail` — **thiếu 3/6 field HR-13** (`from_state`, `to_state`, `actor_role` không phải cột) | TB | HR-13 fields nằm trong `detail` JSON; làm rõ/đối chiếu | DS-01 |
| CONTEXT §3 RBAC không có dòng "xem nhật ký hệ thống" | Thấp | Thêm 1 dòng RBAC (Admin/Manager) | OQ-1 |
| Thiếu filter theo `actor` | Thấp | Bổ sung sau | DS-02 |

## 6. Questions for Human

- **OQ-1:** Manager có được xem audit toàn cục không? (spec cho ADMIN+MANAGER; CONTEXT §3 chưa có dòng này)

## 7. Constitution Check (tóm tắt)

HR-13 (audit) là lý do tồn tại; append-only đúng AC-09. **DS-01:** cấu trúc bảng chỉ map 3/6 field HR-13
trực tiếp (còn lại trong `detail`). RBAC HR-10. Chi tiết: spec §Constitution Compliance.
