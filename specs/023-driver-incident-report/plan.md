# Implementation Plan: Driver Incident Report — Spec #023

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Status:** 🚫 **CHƯA HIỆN THỰC trên nhánh `upload/spec`** — spec đề xuất `V44` nhưng **migration cao
> nhất là V41**, và **không có code Java incident** (grep rỗng 2026-06-24). Đây là plan cho việc sẽ làm,
> **không phải as-built**. Spec ghi rõ: *"Không triển khai trước khi OQ-1 được chốt."*

## 1. Architectural Approach (đề xuất)

Tài xế ở `ACCEPTED`/`IN_PROGRESS` báo sự cố (hỏng xe...) + ≤3 ảnh → `driver_incident_report` PENDING →
Manager xác nhận → **bán đơn lại pool** (`CONFIRMED`, `driver_id=NULL`) + mở **cửa sổ 15 phút**. Có tài
xế nhận lại → `RESOLVED_REASSIGNED`. Quá 15 phút → Manager "Hoàn cọc + bồi thường": khách nhận
`FLOOR(cọc 30%) + 200.000đ` vào `customer_wallet`, tài xế bị trừ **200.000đ** (cọc → ví → SUSPENDED),
đơn `CANCELLED` (COMPANY). **Phân bổ thiệt hại: công ty chịu cọc, tài xế chỉ chịu 200k** (khác
DamageReport 100%).

## 2. Components (dự kiến — CHƯA tồn tại)

| Thành phần | Trách nhiệm | File (dự kiến) |
|------------|-------------|----------------|
| `DriverIncidentReport` + `DriverIncidentPhoto` | Entity | (chưa có) |
| `DriverIncidentService` | Tạo báo sự cố | (chưa có) |
| `ManagerIncidentService` | Xác nhận, bán pool, cửa sổ 15', compensate | (chưa có) |
| Hook `resolveReassigned` trong `acceptOrder` | Tự đóng khi tài xế mới nhận | (sửa `DriverOrderService` — chưa có) |
| Scheduler quét cửa sổ 15' quá hạn | Auto-flag overdue | (chưa có) |
| FE `manager/driver-incidents.html` + nút ở `driver/in-progress.html` | UI | (chưa có) |

## 3. Dependencies

`V44` (chưa tạo — **cần leader cấp số**) → service/controller → hook `acceptOrder` (#006) → FE. Phụ
thuộc `customer_wallet` (#021, BLOCKED) làm đích tiền, `driver_wallet`/cọc (#007) để trừ tài xế.

## 4. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| **Chưa có tài liệu cấp trên** — CONTEXT không có transition sự cố + chính sách 200k | **Cao** | Amendment CONTEXT §State Machine (3 transition) + chính sách bồi thường | OQ-1 / DS-02 |
| State machine kép (sự cố × đơn) phức tạp, dễ sai HR-05 | Cao | Formal state diagram trước khi code | — |
| Cửa sổ 15' cần scheduler + xử lý race khi tài xế nhận lại đúng lúc hết hạn | Cao | Lock + kiểm tra trạng thái trong TX | — |
| Đích tiền `customer_wallet` (#021 BLOCKED) | Cao | Chờ #021 OQ-1 | #021 D-11 |
| Trùng cửa sổ với hội thoại C↔D khi đổi tài xế (Spec #019 DS-02) | Cao | Xử lý reassign conversation | #019 DS-02 |

## 5. Questions for Human (BLOCKER)

- **OQ-1 (BLOCKER):** Chốt chính sách sự cố (cửa sổ 15', bồi thường 200k, công ty chịu cọc) +
  **amendment CONTEXT §State Machine** (3 transition mới: `ACCEPTED→CONFIRMED`, `IN_PROGRESS→CONFIRMED`
  do sự cố). Spec cấm triển khai trước khi chốt.
- **Cấp số migration `V44`** khi được duyệt (không tự đoán).

## 6. Constitution Check (tóm tắt)

Chưa chạy được thực chất vì chưa build. Money rules (AC-08/AC-13/HR-18) + audit (HR-13) sẽ áp khi làm.
Chi tiết: spec §Constitution Compliance.
