# Tasks: Manager Driver Approval — Spec #008

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Danh sách PENDING_APPROVAL (FIFO chờ lâu nhất) | `ManagerDriverApprovalController` | Goals | ✅ |
| T-02 | Chi tiết hồ sơ + giấy tờ (signed URL ≤1h) + bằng chứng cọc | service + `DriverDocumentService` | Goals | ✅ |
| T-03 | Approve → app_user.status=ACTIVE + email + lock (HR-05) | service | Goals | ✅ |
| T-04 | Reject (bắt buộc lý do) → REJECTED (không terminal), giữ cọc | service | Goals | ✅ |
| T-05 | Chỉ MANAGER (Admin 403) | controller | Canonical | ✅ |
| T-06 | Audit quyết định (HR-13) | `AuditService` | Goals | ✅ |
| T-07 | FE driver-approvals, driver-detail, driver-rejected | `frontend/pages/manager/*` | Screen | ✅ |

**Done:** T-01..T-07 ✅ (CORE gatekeeping).
