# Implementation Plan: Manager Driver Ratings — Spec #026

> **Reconstructed plan (spec-after)** — tái dựng từ code + [`spec.md`](spec.md) v1.0.0.
> **Migration:** V9, V40. **Status:** As-built.

## 1. Architectural Approach

**Mặt đọc dành cho Manager** của `order_rating`: `GET /api/manager/driver-ratings` — danh sách đánh giá
**kèm nội dung nhận xét** (khác Admin chỉ xem sao/thống kê ở #012/#016), 3 bộ lọc `driverId`/`stars`/
`keyword`. Chính sách **5.00 sao mặc định** khi tài xế chưa có đánh giá (V40, leader 2026-06-17). Cửa sổ
đánh giá **24h** (tách khỏi escrow tiền 2h — leader 2026-06-17). Sort cố định `createdAt DESC` (không
nhận sort từ client — chống injection tên cột, AC-04).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `OrderRating` entity (V9) | Đánh giá 1–5 + comment | `.../OrderRating.java` |
| `ManagerDriverRatingService` | Query danh sách + filter + default 5 sao | `service/ManagerDriverRatingService.java` |
| `ManagerDriverRatingController` | `GET /api/manager/driver-ratings` | `controller/ManagerDriverRatingController.java` |
| FE `manager/driver-ratings.html` | Trang tra cứu (L/E/E states) | `frontend/pages/manager/driver-ratings.html` |

## 3. Implementation Note (bẫy PostgreSQL — quan trọng)

Bind tham số `null` vào `lower(:param)` → PostgreSQL suy kiểu `bytea` → `function lower(bytea) does not
exist`. Query **bắt buộc** convert `String` + lowercase **trong Java** trước khi bind (FR-012/013). Phải
test trên **PostgreSQL thật** — **H2 không tái hiện**. (Khớp bài học đã ghi trong memory dự án về
`lower(bytea)`/null param JPQL.)

## 4. Dependencies

`V9` (order_rating, do Spec #003 tạo) + `V40` (default 5.00). Phụ thuộc Customer tạo đánh giá (#003).
Bổ trợ #012/#016 (Admin xem rating).

## 5. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| Bẫy `lower(bytea)` khi bind null | TB | Convert String+lowercase trong Java; test trên Postgres thật | Impl Notes |
| **CONTEXT lệch** — §2 nói rating trong escrow 2h; thực tế 24h + default 5 sao chưa vào CONTEXT | TB | Amendment CONTEXT §2/§7 | DS-02 |
| Default 5 sao "thổi phồng" tài xế mới chưa có đánh giá | Thấp (chủ ý) | Ghi rõ đánh đổi | DS-01 |
| Sort từ client → injection tên cột | Thấp | Sort cố định server (AC-04) | Canonical |

## 6. Questions for Human

- **DS-02:** Amend CONTEXT §2 (rating 24h, không phải 2h escrow) + §7 (chính sách default 5 sao) — hiện
  2 quyết định leader 2026-06-17 chỉ nằm trong comment code/migration.

## 7. Constitution Check (tóm tắt)

RBAC MANAGER (HR-10), AC-04 (không nối chuỗi/sort động), AC-15/16, HR-20. Đánh giá **bất biến** (Manager
không ẩn/xoá — đúng ý đồ). Chi tiết: spec §Constitution Compliance.
