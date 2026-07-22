# Tasks: Customer Wallet & Withdrawal — Spec #021

> Code as-built; **spec vẫn BLOCKED (OQ-1)**.
> Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳ deferred · 🚫 blocked

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration `customer_wallet` + trigger | `V8` | Data Model | ✅ |
| T-02 | Migration `customer_withdrawal_request` + total_withdrawn + related col + CHECK terminal | `V39` | Data Model | ✅ |
| T-03 | Wallet summary auto-create + balance>=0 | wallet service | FR-001..005 | ✅ |
| T-04 | Lịch sử giao dịch ví (mask vnpayTxnRef, pagination) | controller | FR-006..011 | ✅ |
| T-05 | Nạp ví VNPay + IPN cộng ví (WALLET_TOP_UP, balance_after) | `VnPay*` | FR-012..018 | ✅ |
| T-06 | Trả cọc 30% / nốt 70% bằng ví (lock, ORDER_PAYMENT âm) | `WalletOrderPaymentService` | FR-019..026 | ✅ |
| T-07 | Hoàn tiền vào ví (MANDATORY, REFUND/DAMAGE_DEDUCTION) | wallet service | FR-027..031 | ✅ |
| T-08 | Customer tạo yêu cầu rút (available=balance−Σpending, lock, whitelist 8 bank, snapshot) | withdrawal service | FR-032..041 | ✅ |
| T-09 | Lịch sử rút của Customer (mask số TK) | controller | FR-042..045 | ✅ |
| T-10 | Admin hàng đợi FIFO + KPI + blockingReasons | `AdminCustomerWithdrawalService` | FR-046..050 | ✅ |
| T-11 | Admin process (trừ ví, WITHDRAWAL, audit, notify, replay-safe) | service | FR-051..060 | ✅ |
| T-12 | Admin reject (không đụng tiền, audit, notify) | service | FR-061..065 | ✅ |
| T-13 | RBAC + append-only + BigDecimal + related_customer_withdrawal_id | toàn module | FR-066..072 | ✅ |
| T-14 | FE 4 màn (my-wallet, withdrawal-request/history, admin/customer-withdrawals) | `frontend/pages/**` | Frontend Contract | ✅ |
| T-15 | **Security review ví Customer (điều kiện (d) Spec #004 FR-036)** | — | OQ-1 | 🚫 blocked |
| T-16 | Amendment CONTEXT + constitution (đảo ngược "no customer wallet") | `CONTEXT_PATCH_PROPOSAL.md` | OQ-1/OQ-2 | 🚫 blocked |
| T-17 | Endpoint Customer huỷ yêu cầu rút (status CANCELLED có sẵn, chưa có endpoint) | — | Scope out #9 | ⏳ |

**Done (code):** T-01..T-14 ✅. **NHƯNG chưa được duyệt chính thức** — T-15/T-16 (🚫) phải xong để spec
rời trạng thái BLOCKED. Đây là **feature money đã chạy trước khi có phê duyệt** — cần leader xử lý.
