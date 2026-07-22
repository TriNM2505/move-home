# Implementation Plan: Admin Reports & Analytics — Spec #016

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Status:** 🚫 **KHÔNG CÒN BUILD trên nhánh này** — commit gần đây "**gỡ module Admin Reports**"; grep
> `AdminReport`/`/api/admin/reports` **rỗng** (2026-06-24). Đây là plan cho spec đã defer/gỡ, **không phải
> as-built**.

## 1. Architectural Approach (đề xuất — đã gỡ khỏi build)

Trang phân tích sâu theo kỳ (khác Dashboard #015 snapshot): báo cáo **financial** (gross booking value,
platform fee, refund, damage-recovery, management_net_contribution), **operations** (completion/dispute
rate, avg order value), **drivers** (top earners, rating distribution, utilization, churn proxy),
**customers** (DAU/MAU, 30d retention, top spenders), **heatmap 7×24**. Dùng sổ cái `transaction` (#013).
Zero-data → `INSUFFICIENT_DATA` warning (không suy diễn).

## 2. Components (dự kiến — hiện KHÔNG tồn tại)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AdminReportController` + service | 5 nhóm báo cáo | (đã gỡ) |
| FE `admin/reports.html` | Charts + date range | (không có) |

## 3. Dependencies
Đọc `transaction` (#013), `order_rating`, `login_event`. Không bảng mới.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| **Spec còn nhưng module đã gỡ** → tài liệu lệch code | TB | Ghi rõ trạng thái "gỡ" (plan này); quyết định build lại hay defer chính thức |
| Metric không định nghĩa rõ | TB | Mỗi metric có định nghĩa + nguồn + zero-data policy |

## 5. Questions for Human
- **Reports (#016) build lại hay defer chính thức Sprint 6+?** Hiện đã gỡ khỏi build.

## 6. Constitution Check (tóm tắt)
HR-10/13, AC-08/15. Sẽ áp khi build lại. Chi tiết: [`spec.md`](spec.md).
