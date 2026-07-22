# Implementation Plan: Customer Wallet & Withdrawal — Spec #021

> **Reconstructed plan (spec-after)** — tái dựng từ code + [`spec.md`](spec.md) v1.0.0.
> **Migration:** V6, V8, V24, V39.
> **Status:** ⚠️ **Code đã build nhưng spec vẫn `BLOCKED` chờ leader duyệt OQ-1** (xem §6).

## 1. Architectural Approach

Ví khách (`customer_wallet`) giữ số dư VND: nạp VNPay → `WALLET_TOP_UP`; trả cọc 30%/nốt 70% bằng ví →
`ORDER_PAYMENT` âm; nhận hoàn (huỷ đơn/tranh chấp/sự cố) → `REFUND`/`DAMAGE_DEDUCTION`; rút về ngân hàng
qua **Admin duyệt thủ công** (mirror luồng tài xế Spec #009) → `WITHDRAWAL` âm. **Money-critical:** mọi
đổi số dư đi kèm 1 `transaction` append-only trong cùng TX (AC-13), `balance>=0` DB+service (HR-18),
`BigDecimal` scale=0 (AC-08), pessimistic lock chống race.

**Tái dùng sổ cái `transaction` (V6)** thay vì tạo `wallet_transaction` riêng → lệch tên khái niệm
AC-13 (DS-05) nhưng giúp đối soát Spec #013 không phải union 2 nguồn.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `CustomerWallet` + `CustomerWithdrawalRequest` entity | Ví + yêu cầu rút | `customer/finance/*.java` |
| `WalletService` / customer wallet service | Summary, top-up hệ quả, trả đơn, hoàn tiền (MANDATORY) | `service/WalletService.java` |
| `WalletOrderPaymentService` | Trả cọc/nốt 70% bằng ví | `payment/WalletOrderPaymentService.java` |
| `AdminCustomerWithdrawalService` + Controller | Hàng đợi FIFO + KPI + process/reject | `service/...`, `controller/AdminCustomerWithdrawalController.java` |
| `CustomerWithdrawalRequestRepository` | FIFO/pending/for-update queries | `customer/finance/*Repository.java` |
| VNPay top-up | Tạo URL nạp ví + IPN cộng ví | `payment/VnPayController.java`, `VnPayPaymentService.java` |
| FE 4 màn | `my-wallet`, `withdrawal-request`, `withdrawal-history`, `admin/customer-withdrawals` | `frontend/pages/customer/*`, `admin/customer-withdrawals.html` |

## 3. Data Flow (rút tiền)

```
Customer POST withdrawals ──lock wallet+pendings──> available=balance−Σpending ──> INSERT request(PENDING) ──> notify Admin
Admin process(bankTxnRef) ──lock wallet──> balance-=amount, totalWithdrawn+=amount, INSERT transaction(WITHDRAWAL,-,balance_after) ──> PROCESSED + audit + notify
```

## 4. Dependencies

`V6` (transaction) + `V8` (customer_wallet, Sprint 4) → `V24` (finance columns) → `V39`
(customer_withdrawal_request + total_withdrawn + related_customer_withdrawal_id, Sprint 6). Phụ thuộc
Spec #020 (notification), #018 (error envelope). Feed sổ cái #013; nhận tiền hoàn từ #022/#010/#023.

## 5. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| **Feature money đã build nhưng chưa được duyệt chính thức** | **Cao (governance)** | Chờ OQ-1 security review + amendment CONTEXT/constitution | OQ-1 |
| Race 2 request rút cùng vượt số dư | Cao | Pessimistic lock ví + pendings (FR-039) | MI-006 |
| Double-process rút tiền | Cao | Unique index `uq_transaction_customer_withdrawal` + bank_txn_ref unique | MI-004/005 |
| Lệch tên sổ cái AC-13 (`transaction` vs `wallet_transaction`) | TB | Làm rõ AC-13 dùng tên khái niệm | DS-05 |
| `RefundRecord` (CONTEXT) bị thay bằng ví — mâu thuẫn tài liệu | TB | Amendment CONTEXT §Huỷ đơn + HR-14 | OQ-2 |

## 6. Questions for Human (BLOCKER)

- **OQ-1 (BLOCKER):** Duyệt đảo ngược "không có ví Customer"? Cần **(d) security review** — 3/4 điều
  kiện Spec #004 FR-036 đã có (a/b bản vá `CONTEXT_PATCH_PROPOSAL.md`, c spec+V8/V39); **(d) chưa làm**.
  Nếu leader từ chối → mọi endpoint ví phải trả 404/403 theo Spec #004.
- **OQ-2:** `RefundRecord` thay hoàn toàn bằng ví + `order_cancellation_refund`? Sửa CONTEXT + HR-14.

## 7. Constitution Check (tóm tắt)

Money rules tuân thủ tốt (HR-18/AC-08/AC-13 enforce ở DB+service). Governance: spec BLOCKED, cần
amendment CONTEXT/constitution trước khi coi là canonical. Chi tiết: spec §Constitution Compliance.
