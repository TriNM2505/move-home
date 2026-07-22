# Tasks: Driver Financial — Spec #007

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration driver_wallet + withdrawal_request + indexes | V11/V12/V13 | Data Model | ✅ |
| T-02 | Trang earnings: available/released/withdrawn + 2 chart | `DriverWalletController` | Scope | ✅ |
| T-03 | Credit earnings sau escrow (DRIVER_EARNING) | `DriverEarningService` | Earnings | ✅ |
| T-04 | Tạo withdrawal request (available=balance−Σpending, lock) | withdrawal service | Scope | ✅ |
| T-05 | Cancel PENDING→CANCELLED (race-safe, không refund) | service | State | ✅ |
| T-06 | FE earnings, withdrawal-request/history | `frontend/pages/driver/*` | Screen | ✅ |
| T-07 | Xử lý PROCESSED/REJECTED (Admin) | #009 | — | ✅ (ở #009) |
| T-08 | Min/max/phí rút | — | CONTEXT Q7 | ⏳ |

**Done:** T-01..T-07 ✅ (min 100k đã có ở V12). T-08 (max/phí) chưa chốt.
