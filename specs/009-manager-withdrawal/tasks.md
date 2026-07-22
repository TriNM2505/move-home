# Tasks: Admin Withdrawal Processing — Spec #009

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ⚠️ Folder tên "manager-withdrawal" là **legacy** — canonical ADMIN. ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration transaction finance + terminal CHECK | V24 | Data Model | ✅ |
| T-02 | Queue PENDING FIFO + KPI + info Driver/ví/bank masked | `AdminWithdrawalService` | Goals | ✅ |
| T-03 | Process: lock, kiểm số dư, trừ ví, WITHDRAWAL + bankTxnRef + audit (1 TX) | service | Goals | ✅ |
| T-04 | Reject (lý do, không refund) | service | Goals | ✅ |
| T-05 | Chống double-debit (unique index + bank_txn_ref unique) | DB + service | Goals | ✅ |
| T-06 | Race Admin process vs Driver cancel | service | Goals | ✅ |
| T-07 | Chỉ ADMIN (Manager 403) | controller | Canonical | ✅ |
| T-08 | FE admin/withdrawals | `frontend/pages/admin/withdrawals.html` | Screen | ✅ |
| T-09 | Dọn legacy Manager stub 5.5–5.7 | — | Source-of-Truth | ⏳ |

**Done:** T-01..T-08 ✅ (CORE). T-09 (dọn stub Manager legacy) tồn.
