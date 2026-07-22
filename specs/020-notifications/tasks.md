# Tasks: Notifications — Spec #020

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ deferred · 🚫 blocked

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration bảng `notification` | `V18__create_notification_table.sql` | Data Model | ✅ |
| T-02 | Entity + `NotificationType` (24 hằng số) + repository | `.../Notification*.java` | FR-021, FR-019 | ✅ |
| T-03 | `create()` REQUIRES_NEW + null-check | `NotificationService` | FR-013..017 | ✅ |
| T-04 | `GET /api/notifications` (pagination clamp, ownership) | `NotificationController` | FR-001..007 | ✅ |
| T-05 | `PATCH /{id}/read` (idempotent, `findByIdAndUserId`, 404 ẩn) | `NotificationController` | FR-008..012 | ✅ |
| T-06 | Pattern `safeNotify` tích hợp ở 9+ service | các service | FR-017, FR-018 | ✅ |
| T-07 | FE `notifications-bell.js` (chuông + badge + escape XSS) | `frontend/js/notifications-bell.js` | FR-027..029, FR-031 | ✅ |
| T-08 | FE 4× `notifications.html` (L/E/E states) | `frontend/pages/*/notifications.html` | FR-030, FR-032 | ✅ |
| T-09 | Endpoint `unread-count` (badge đúng khi >5) | — | DS-02 / OQ-2 | ⏳ |
| T-10 | Chuyển `create()` sang `afterCommit` (chống thông báo "ma") | `NotificationService` | DS-07 / OQ-3 | ⏳ |
| T-11 | Index `(user_id, created_at DESC)` + FK app_user (migration mới) | — | DS-08 / OQ-4 | ⏳ |
| T-12 | Chuẩn hoá 1 đường tạo (bỏ `repo.save()` trực tiếp) | `WalletService`... | DS-10 | ⏳ |
| T-13 | CHECK/test danh mục `type` (gom 5 type hardcode) | — | DS-01 | ⏳ |
| T-14 | Mark-all-read + deep-link + cleanup job | — | DS-03/04/09 | ⏳ |

**Done (1.0):** T-01..T-08 ✅. Ưu tiên xử lý T-09/T-10/T-11 trước khi seed dữ liệu lớn / feature tiền tích hợp sâu.
