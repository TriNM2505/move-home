# Tasks: Driver Workflow — Spec #006

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Home KPI + current assignment | `DriverOrderController` | Scope 1 | ✅ |
| T-02 | Assignments chờ phản hồi + accept/reject (quota 3/ngày, row lock HR-08) | `DriverOrderService` | Scope 2–3 | ✅ |
| T-03 | Đã đến điểm đón (arrived_at V37) + bắt đầu → IN_PROGRESS | service | State machine | ✅ |
| T-04 | Yêu cầu thanh toán 70% → AWAITING_FINAL_PAYMENT | service | State machine | ✅ |
| T-05 | Hoàn thành: chỉ sau IPN final verified → COMPLETED | service + `VnPay*` | State machine | ✅ |
| T-06 | Guard không COMPLETED khi IN_DISPUTE (HR-06) | service | HR-06 | ✅ |
| T-07 | Cập nhật vị trí (rate-limited, owner xem) | `driver_location` (V20) | Location | ✅ |
| T-08 | Escrow 2h → credit driver_wallet + DRIVER_EARNING | `EscrowReleaseService` (V30) | Earnings | ✅ |
| T-09 | Report dispute (IN_PROGRESS/AWAITING → IN_DISPUTE) | service | Dispute | ✅ |
| T-10 | FE home, available-orders, order-detail, in-progress, history, earnings | `frontend/pages/driver/*` | Screen | ✅ |
| T-11 | Timeout AWAITING_FINAL_PAYMENT nếu khách im lặng | — | CONTEXT Q10 | ⏳ |

**Done:** T-01..T-10 ✅ (CORE vận hành). T-11 (timeout) chưa chốt.
