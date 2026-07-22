# Implementation Plan: Driver Workflow — Spec #006

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V5 (+ V25 started_at, V30 escrow, V37 arrived_at), V20 (driver_location).
> **Status:** As-built (CORE vận hành tài xế).

## 1. Architectural Approach

Vận hành hằng ngày của Driver `ACTIVE`: nhận assignment (Manager phân — không tự pick), accept/reject
trong hạn (quota 3/ngày), đã đến điểm đón → bắt đầu → yêu cầu thanh toán 70% → hoàn thành (chỉ sau IPN
final verified). State machine bảo vệ bằng **row lock** (HR-08) + ownership + transition guard (HR-05).
Location nhẹ, rate-limited, chỉ owner Customer xem (#003). Sau `COMPLETED`: **escrow 2h** — scheduled job
credit `driver_wallet` + `DRIVER_EARNING` khi hết window & không tranh chấp. Driver có thể
`IN_PROGRESS|AWAITING_FINAL_PAYMENT → IN_DISPUTE`.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `DriverOrderService` / `DriverOrderController` | assignments, accept/reject, transitions | `driver/DriverOrder*.java` |
| `driver_assignment` | Quyết định accept/reject + row lock (HR-08) | (bảng/logic) |
| `DriverEarningService` | Credit ví sau escrow | `driver/finance/DriverEarningService.java` |
| `EscrowReleaseService` | Scheduled job release 70% | `.../EscrowReleaseService.java` |
| `driver_location` (V20) | Cập nhật vị trí | `V20` |
| FE home, available-orders, order-detail, in-progress, history, earnings | 6 màn | `frontend/pages/driver/*` |

## 3. Dependencies
`V5`/`V20`/`V25`/`V30`/`V37`. Phụ thuộc #005 (Driver ACTIVE), #003 (location cho Customer), #011 (Manager
phân công), #008, payment/#008. Cung earning cho #007.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Race accept vs reassign vs timeout | Cao | Row lock (HR-08) + transition trong TX |
| Complete khi chưa trả 70% | Cao | Chỉ sau IPN final verified |
| Earning credit sai lúc | Cao | Escrow job chỉ khi hết 2h & không dispute |
| Driver COMPLETED khi IN_DISPUTE | Cao | Guard HR-06 |

## 5. Questions for Human
- Timeout trong AWAITING_FINAL_PAYMENT nếu khách im lặng (CONTEXT Q10) — chưa chốt.

## 6. Constitution Check (tóm tắt)
HR-05/06/07/08 (state/dispute/concurrency), HR-18/AC-13 (ví/escrow). Chi tiết: [`spec.md`](spec.md).
