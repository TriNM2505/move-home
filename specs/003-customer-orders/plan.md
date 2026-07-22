# Implementation Plan: Customer Orders Management — Spec #003

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V5 (+ mở rộng), V9 (order_rating), V20 (driver_location). **Status:** As-built (CORE).

## 1. Architectural Approach

Quản lý đơn sau đặt cho Customer: 3 view (pending / active / history) + trang chi tiết + timeline audit.
Bảo vệ state machine: chỉ cho hủy ở trạng thái CONTEXT cho phép (`PENDING_PAYMENT`, `CONFIRMED`),
transition sai → 409 (HR-05), ownership-only (HR-10). Active track vị trí Driver (Leaflet + OSM, polling
5s). Sau `COMPLETED` → 1 đánh giá (1–5 sao + tags + comment) trong cửa sổ (spec 003 nói 2h escrow; **lưu
ý #026 chốt lại 24h**). Timeline đọc từ `order_audit_log`, không suy từ status.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `CustomerOrderQueryService` | pending/active/history/detail | `order/CustomerOrderQueryService.java` |
| `CustomerOrderActionService` | cancel (guard trạng thái) | `order/CustomerOrderActionService.java` |
| `OrderRepository` | Query theo customer + status | `order/OrderRepository.java` |
| `order_rating` | Đánh giá (UNIQUE order_id) | `V9` |
| `driver_location` | Vị trí cho active order | `V20` |
| FE my-orders-pending/active/history, order-detail, order-rate | 5 màn | `frontend/pages/customer/*` |

## 3. Dependencies
`V5`/`V9`/`V20`. Phụ thuộc #002 (order tạo ra), #006 (driver cập nhật trạng thái), #010 (dispute), #026
(Manager xem rating). Cung cấp location cho FE.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Customer đọc đơn người khác | Cao | Ownership trong query (HR-10) |
| Hủy sai trạng thái | Cao | Guard → 409 (HR-05) |
| Rating quá hạn/trùng | TB | Window check 409 + UNIQUE order_id |
| Cửa sổ rating lệch (2h vs 24h) | TB | Đồng bộ theo #026 — D-13 |

## 5. Questions for Human
- Cửa sổ rating: spec này ghi 2h; #026 chốt 24h → cần đồng bộ CONTEXT (D-13, đã amend §7).

## 6. Constitution Check (tóm tắt)
HR-05/10 (state/ownership), AC-15/16 (pagination/states). Chi tiết: [`spec.md`](spec.md).
