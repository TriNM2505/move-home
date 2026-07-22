# Implementation Plan: Admin Withdrawal Processing — Spec #009

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V12 (withdrawal_request), V24 (transaction finance + terminal CHECK). **Status:** As-built (CORE).
> ⚠️ **Tên folder `009-manager-withdrawal` là legacy** — canonical: **ADMIN** xử lý (Manager 403).

## 1. Architectural Approach

Kiểm soát tiền **ra** hệ thống (rút tiền tài xế). **Chỉ ADMIN** xử lý (Manager 403 — legacy stub Manager
phải migrate). Không có bước `APPROVED` trung gian: `PENDING` → Admin chuyển khoản **ngoài hệ thống** →
mark `PROCESSED` (lock request+ví, kiểm tra lại số dư, trừ ví, append `WITHDRAWAL` + `bankTxnRef` + audit,
cùng TX). Reject/cancel không refund (chưa trừ). `available = balance − Σ(PENDING)`. Chống double-debit
(unique index + bank_txn_ref unique), race Admin×Driver. Bank data masked ở queue/history.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AdminWithdrawalService` | queue FIFO + KPI + process/reject | `service/AdminWithdrawalService.java` |
| `withdrawal_request` + repo | State machine PENDING/PROCESSED/REJECTED/CANCELLED | `.../WithdrawalRequestRepository.java` |
| `transaction` (WITHDRAWAL) | Append-only + balance_after | `V24` |
| FE admin/withdrawals | Màn xử lý | `frontend/pages/admin/withdrawals.html` |

## 3. Dependencies
`V12`/`V24`. Phụ thuộc #007 (ví/earning tài xế), #001 (RBAC Admin). Là bản **mirror** của #021 (rút tiền khách).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Double-debit | Cao | Unique `uq_transaction_withdrawal` + `bank_txn_ref` unique |
| Race Admin process vs Driver cancel | Cao | Lock request + ví |
| Manager có quyền tài chính | TB | Chỉ ADMIN (HR-10) |

## 5. Questions for Human
- Không có (canonical đã rõ; legacy Manager stub cần dọn).

## 6. Constitution Check (tóm tắt)
HR-10/18, AC-13 (append-only). Chi tiết: [`spec.md`](spec.md).
