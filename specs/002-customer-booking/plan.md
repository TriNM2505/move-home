# Implementation Plan: Customer Booking Flow — Spec #002

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V5 (service_order) + V7 (booking fields). **Status:** As-built (CORE doanh thu).

## 1. Architectural Approach

Đặt đơn 6 bước có URL riêng (chọn xe → điểm đón → điểm trả → chi tiết → báo giá → xác nhận). Lưu
`booking_draft` (bảng riêng, không thêm `DRAFT` vào `service_order`) — resume/refresh không mất dữ liệu,
chỉ owner đọc/sửa. Báo giá **deterministic** (base + peak + alley + floor + porter) từ pricing snapshot
(`commission_settings`) + khoảng cách OSRM (fallback bảng quận→quận, AC-06). Kết thúc = `service_order`
**bất biến giá** ở `PENDING_PAYMENT` + URL VNPay cọc 30%. Chỉ IPN hợp lệ → `CONFIRMED` (HR-03/04).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| Booking draft service | CRUD draft, quote, confirm | `order/*` (booking) |
| `OrderDepositCalculator` | Tính cọc 30% / nốt 70% | `order/OrderDepositCalculator.java` |
| Pricing (snapshot commission_settings) | Công thức giá + snapshot rate | `commission_settings` + service |
| OSRM routing client | Khoảng cách + fallback quận | `location/OSRM*` |
| `OrderController` / service_order | Tạo order PENDING_PAYMENT | `order/OrderController.java` |
| VNPay | URL cọc + IPN | `payment/VnPay*` |
| FE booking-step1..6 + success | Wizard localStorage handoff | `frontend/pages/customer/booking-*` |

## 3. Dependencies
`V5` + `V7` (draft/vehicle/surcharge/address). Phụ thuộc #001 (auth), pricing config (#014), VNPay,
OSRM. Bàn giao sang payment/#004.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| OSRM lỗi/hết quota buổi demo | Cao | Fallback bảng quận→quận, nhãn "ước tính" (AC-06) |
| Giá không deterministic | Cao | Snapshot `commission_rate_snapshot` vào order |
| Giả mạo `?vnp_ResponseCode=00` | Cao | Chỉ IPN đổi DB (HR-03), verify hash (HR-04) |
| State machine sai | TB | Transition guard → 409 (HR-05) |

## 5. Questions for Human
- Đồng bộ giá: CONTEXT thắng các mức fixed trong stub (đã chốt trong Source-of-Truth).

## 6. Constitution Check (tóm tắt)
HR-03/04/05/15 (payment/state), AC-06/07/08 (maps/tiền/tz). Chi tiết: [`spec.md`](spec.md).
