# Implementation Plan: Notifications — Spec #020

> **Reconstructed plan (spec-after)** — tái dựng từ code + [`spec.md`](spec.md) v1.0.0.
> **Migration:** V18. **Status:** As-built.

## 1. Architectural Approach

Hạ tầng thông báo in-app **dùng chung** toàn hệ thống: 1 bảng `notification`, 2 REST endpoint, 1 API nội
bộ `NotificationService.create()`. Nguyên tắc cốt lõi: **notification không bao giờ làm hỏng nghiệp vụ
chính** — `create()` chạy `Propagation.REQUIRES_NEW`, mọi caller bọc `safeNotify(try/catch)` (mở rộng
tinh thần HR-11 từ email sang in-app). Chưa realtime — chỉ polling từ FE (DS-12).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `Notification` entity | Bảng tối giản (không FK/CHECK/deleted_at) | `.../Notification.java` |
| `NotificationType` | 24 hằng số type (thực tế dùng 29 — 5 hardcode) | `.../NotificationType.java` |
| `NotificationRepository` | `findByUserId...`, `findByIdAndUserId` | `.../NotificationRepository.java` |
| `NotificationService` | `create()` REQUIRES_NEW, list, markRead | `.../NotificationService.java` |
| `NotificationController` | `GET /api/notifications`, `PATCH /{id}/read` | `.../NotificationController.java` |
| FE `notifications-bell.js` | Chuông + badge (đếm trong 5 item) | `frontend/js/notifications-bell.js` |
| FE 4× `notifications.html` | Trang danh sách theo vai trò | `frontend/pages/{customer,driver,manager,admin}/notifications.html` |

## 3. Data Flow

```
Service nghiệp vụ ──safeNotify()──> NotificationService.create() ──REQUIRES_NEW (TX riêng)──> notification
FE (mọi trang) ──GET /api/notifications?size=5──> bell badge ; PATCH /{id}/read ──> isRead=true
```

## 4. Dependencies

`V18` (độc lập, chỉ cần `app_user`) → service+controller → FE bell + 4 trang → **mọi feature #021–#024
phụ thuộc** (phải lên trước chúng). Bản thân không phụ thuộc feature nào.

## 5. Risks & Mitigations (từ Deferred Scope)

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| `REQUIRES_NEW` commit trước nghiệp vụ → thông báo "ma" khi nghiệp vụ rollback | **Cao** | Chuyển sang `afterCommit` (giữ HR-11) | DS-07 / OQ-3 |
| Badge chỉ đếm trong 5 item → sai khi >5 chưa đọc | **Cao** | Endpoint `unread-count` | DS-02 / OQ-2 |
| Thiếu index `(user_id, created_at)` → full scan | **Cao** | `CREATE INDEX` (migration) | DS-08 / OQ-4 |
| 2 đường tạo (service REQUIRES_NEW vs repo trực tiếp) không nhất quán | TB | Chuẩn hoá 1 đường | DS-10 |
| `type` không CHECK → 5 type hardcode, typo không phát hiện | TB | CHECK/test danh mục | DS-01 / AC-14 partial |
| Bảng phình vô hạn (không cleanup/deleted_at) | TB | Job dọn >90 ngày | DS-09 |

## 6. Questions for Human

- **OQ-2 (High):** thêm `unread-count` endpoint? (badge đang sai khi >5)
- **OQ-3 (High):** `REQUIRES_NEW` giữ hay đổi `afterCommit`? (thông báo "ma")
- **OQ-4 (High):** thêm index `(user_id, created_at)`?

## 7. Constitution Check (tóm tắt)

Layer 1 **ALL PASS**. Layer 2: exception AC-09 (không deleted_at — coi N/A), **AC-14 partial** (type
không CHECK). Không block. Chi tiết: spec §Constitution Compliance.
