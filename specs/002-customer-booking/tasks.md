# Tasks: Customer Booking Flow — Spec #002

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳ deferred

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration booking fields (draft/vehicle/surcharge/address) | V7 | Data Model | ✅ |
| T-02 | CRUD booking_draft (create/patch/get/delete owner-only) | booking service | Scope 1–4 | ✅ |
| T-03 | Quote Step 5: công thức base+peak+alley+floor+porter + snapshot | pricing + `OrderDepositCalculator` | Scope 5 | ✅ |
| T-04 | OSRM distance + fallback quận→quận (AC-06) | OSRM client | Pricing | ✅ |
| T-05 | Confirm → tạo `service_order` PENDING_PAYMENT (bất biến giá) | `OrderController` | Scope 6 | ✅ |
| T-06 | URL VNPay cọc 30%; chỉ IPN → CONFIRMED (HR-03/04) | `VnPay*` | Thanh toán | ✅ |
| T-07 | FE booking-step1..6 + booking-success (localStorage handoff) | `frontend/pages/customer/booking-*` | Screen | ✅ |
| T-08 | Sửa đơn hạn chế (fields không ảnh hưởng giá) | order service | CONTEXT §Sửa đơn | ✅ |

**Done:** T-01..T-08 ✅ (CORE doanh thu).
