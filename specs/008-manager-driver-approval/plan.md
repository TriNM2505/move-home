# Implementation Plan: Manager Driver Approval — Spec #008

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V4/V10 (driver_profile), V14 (driver_document), V22 (audit). **Status:** As-built (CORE).

## 1. Architectural Approach

Manager là **gatekeeper** nguồn cung Driver: xử lý hồ sơ `PENDING_APPROVAL` (FIFO chờ lâu nhất trước),
xem giấy tờ (GPLX, đăng ký xe, 3 ảnh xe qua **signed Cloudinary URL TTL ≤1h**) + bằng chứng cọc 3tr.
**Approve** → `app_user.status = ACTIVE` + email → Driver vào workflow. **Reject** (bắt buộc lý do) →
`REJECTED` (không terminal, cho re-submit #005), **giữ cọc**. Lock `app_user` chống 2 Manager xử lý cùng
lúc (1 thắng, còn lại 409 — HR-05). **Chỉ MANAGER** (Admin 403). Audit mọi quyết định (HR-13).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `ManagerDriverApprovalController` + service | list/detail/approve/reject | `controller/ManagerDriverApprovalController.java` |
| `driver_document` (signed URL) | Xem giấy tờ | `service/DriverDocumentService.java` |
| `audit_log` | Lịch sử quyết định | `V22` |
| FE driver-approvals, driver-detail, driver-rejected | 3 màn | `frontend/pages/manager/*` |

## 3. Dependencies
`V4`/`V10`/`V14`/`V22`. Phụ thuộc #005 (hồ sơ onboarding), Cloudinary. Bàn giao Driver ACTIVE → #006.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| 2 Manager duyệt đồng thời | Cao | Lock `app_user` → 1 commit, còn lại 409 |
| Admin tự duyệt (sai role) | TB | Chỉ MANAGER (Admin 403) — khác prompt cũ |
| Lộ raw Cloudinary URL | TB | Signed URL TTL ≤1h (AC-10) |

## 5. Questions for Human
- Re-submit khi REJECTED: full edit flow (giao với #005).

## 6. Constitution Check (tóm tắt)
HR-05/10/13, AC-10. Chi tiết: [`spec.md`](spec.md).
