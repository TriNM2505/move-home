# Implementation Plan: Driver Financial — Spec #007

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V11 (driver_wallet), V12 (withdrawal_request), V13 (indexes), V24 (transaction finance).
> **Status:** As-built (CORE tiền tài xế).

## 1. Architectural Approach

Ví tài xế minh bạch: earnings sau **escrow 2h** (`DRIVER_EARNING`), rút tiền do **Admin** duyệt (không
Manager). Request `PENDING` **không** hold ví; `available = balance − Σ(PENDING)`. Chỉ khi Admin
`PROCESSED` mới lock ví, kiểm tra lại số dư, trừ, append `WITHDRAWAL` trong cùng TX (AC-13). Reject/cancel
không đụng tiền. Ví không âm (HR-18), append-only sổ cái, chống double-debit + race cancel/process.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `DriverWallet` + `DriverWalletController` | Ví + earnings + charts | `driver/finance/DriverWallet*.java` |
| `DriverEarningService` | Credit sau escrow | `driver/finance/DriverEarningService.java` |
| `WithdrawalRequest` + repo | Tạo/list rút, FIFO pending | `.../WithdrawalRequest*.java` |
| `AdminWithdrawalService` | Xử lý (xem #009) | `service/AdminWithdrawalService.java` |
| FE earnings, withdrawal-request/history | 3 màn | `frontend/pages/driver/*` |

## 3. Dependencies
`V11`/`V12`/`V13`/`V24`. Phụ thuộc #006 (earning nguồn), #005 (cọc). Xử lý rút = #009 (Admin).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Nhiều request pending vượt số dư | Cao | `available = balance − Σpending` + lock (HR-18) |
| Double-debit rút tiền | Cao | Unique index + kiểm tra trong TX |
| Race cancel vs process | Cao | Lock request + ví |

## 5. Questions for Human
- Min/max/phí rút (CONTEXT Q7) — hiện min 100.000đ (V12 CHECK).

## 6. Constitution Check (tóm tắt)
HR-18/AC-13 (ví/audit tiền), HR-05 (state rút). Chi tiết: [`spec.md`](spec.md).
