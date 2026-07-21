# Feature Specification: Customer Wallet & Withdrawal

**Feature Branch:** `021-customer-wallet-withdrawal`  
**Feature Number:** #21 of 26 — CORE (money-critical)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft — **BLOCKED** chờ leader duyệt OQ-1 (xem Source-of-Truth Resolution)  
**Sprint Target:** Sprint 4 (ví + nạp + trả đơn) → Sprint 6 (rút tiền + Admin duyệt)

**CONTEXT.md reference:** v2.0 §2 Thanh toán, §2 Huy don & Hoan tien, §2 Wallet & Commission, §3 RBAC  
**Constitution reference:** v1.4.0 — HR-01, HR-03, HR-04, HR-10, HR-13, HR-15, HR-18, HR-20, HR-21,
AC-03, AC-07, AC-08, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, ES-01, ES-02, ES-03, ES-04  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Customer 3.17; các màn `withdrawal-request`,
`withdrawal-history`, `admin/customer-withdrawals` cần bổ sung vào inventory (xem DS-07)  
**Related specs:** Spec #004 Customer Profile & Wallet (mâu thuẫn — xem Source-of-Truth Resolution);
Spec #013 Admin System Transactions (sổ cái); Spec #009 Admin Withdrawal Processing (bản tài xế —
luồng mirror); Spec #022 Order Cancellation Refund; Spec #023 Driver Incident Report

**Migration liên quan:** `V6` (transaction), `V8` (customer_wallet), `V24` (transaction finance
columns), `V39` (customer_withdrawal_request + `customer_wallet.total_withdrawn` +
`transaction.related_customer_withdrawal_id`)

---

## Goals

Đặc tả **Ví khách hàng (`customer_wallet`)** — module giữ số dư VND của Customer trong hệ thống. Ví cho
phép khách nạp tiền qua VNPay, dùng số dư để trả cọc 30% và trả nốt 70% cho đơn, nhận tiền hoàn (huỷ
đơn, tranh chấp, bồi thường sự cố) và rút số dư về tài khoản ngân hàng qua quy trình Admin duyệt thủ
công.

Spec định nghĩa REST contracts, validation, money invariants, transaction boundaries, state machine
của yêu cầu rút tiền, error matrix, RBAC, audit trail và frontend contract cho bốn màn hình:
`customer/my-wallet.html`, `customer/withdrawal-request.html`, `customer/withdrawal-history.html`
và `admin/customer-withdrawals.html`.

Ví khách là module **money-critical**: mọi thay đổi số dư phải đi kèm một bản ghi append-only trong
sổ cái `transaction` trong cùng transaction DB (AC-13), số dư không bao giờ âm ở cả tầng DB và tầng
service (HR-18), tiền dùng `BigDecimal` scale=0 VND nguyên đồng (AC-08), thời gian lưu UTC và hiển
thị `Asia/Ho_Chi_Minh` (AC-07). Bốn màn hình dùng Move_home forest green `#1B4D3E`, amber `#F5A623`,
Be Vietnam Pro, tiếng Việt có dấu (HR-19, HR-20) và đủ Loading/Empty/Error states (AC-16).

> ⚠️ **Spec này đề xuất đảo ngược một quyết định kiến trúc đã chốt.** `CONTEXT.md` v2.0 và Spec #004
> quy định Move_home **không có** ví Customer. Spec #004 FR-036 đặt ra bốn điều kiện để thay đổi điều
> đó, và spec này là điều kiện thứ ba. Trạng thái `BLOCKED` giữ nguyên cho tới khi leader duyệt OQ-1.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.4.0 → Specs → spec này → Code.
> Theo hierarchy, CONTEXT thắng. Spec này **đề xuất sửa CONTEXT**, không tự ý ghi đè. Nội dung bản vá
> ở `specs/021-customer-wallet-withdrawal/CONTEXT_PATCH_PROPOSAL.md`; trạng thái spec giữ `BLOCKED`
> cho tới khi leader duyệt.

### Quyết định kiến trúc — đảo ngược "không có ví Customer"

`CONTEXT.md` v2.0 chốt Move_home **không có ví Customer**: hoàn tiền đi qua `RefundRecord` — Manager xin
số tài khoản qua chat, chuyển khoản ngoài hệ thống, rồi bấm PROCESSED. Spec #004 codify quyết định đó
thành FR-032..FR-036 (mọi endpoint ví → 404/403). Spec này **đề xuất đảo ngược**.

**Lý do đề xuất đảo ngược:**

| Vấn đề của mô hình RefundRecord | Cách ví Customer giải quyết |
|---------------------------------|------------------------------|
| Hoàn tiền mất nhiều ngày: Manager phải chat xin STK → khách trả lời → chuyển khoản → bấm PROCESSED | Tiền vào ví **tức thì** trong cùng transaction với quyết định hoàn |
| Manager gõ nhầm STK → mất tiền, không có cơ chế đối soát | Không có bước nhập STK thủ công ở khâu hoàn |
| Khách không thấy tiền ở đâu cho tới khi ngân hàng báo | Số dư hiển thị ngay tại `my-wallet.html` |
| Mỗi lần đặt đơn khách phải qua cổng VNPay lại từ đầu | Trả cọc/70% bằng số dư — một cú bấm |
| 4 luồng hoàn tiền (huỷ đơn, tranh chấp, sự cố, lỗi công ty) đều cần cơ chế thủ công riêng | Một đích đến chung: `customer_wallet` |

**Đánh đổi phải chấp nhận:** ví giữ tiền thật của khách → phát sinh nghĩa vụ pháp lý về số dư, cần
luồng rút tiền có Admin kiểm soát, và mở rộng bề mặt tấn công tài chính. Đây là lý do spec yêu cầu
security review (điều kiện (d) của Spec #004 FR-036) trước khi duyệt.

**Bốn điều kiện Spec #004 FR-036 đặt ra để thêm ví Customer:**

| # | Điều kiện | Trạng thái |
|---|-----------|-----------|
| (a) | Amendment `CONTEXT.md` | ⏳ Bản vá đã soạn — `CONTEXT_PATCH_PROPOSAL.md` PATCH 1–4 |
| (b) | Amendment Constitution (AC-13, HR-18) | ⏳ Bản vá đã soạn — PATCH 6 |
| (c) | Spec + migration riêng | ✅ Spec này + `V8`/`V39` |
| (d) | Security review | ❌ **Chưa thực hiện** — xem OQ-1 |

> **Spec #004 FR-036 ghi rõ:** *"spec này SHALL không được dùng như approval ngầm cho feature đó"*.
> Spec #021 tôn trọng điều đó: nó là **đề xuất chính thức**, không phải sự đã rồi. Nếu leader từ chối
> OQ-1, toàn bộ spec này bị huỷ và các endpoint ví phải trả 404/403 theo Spec #004 FR-032/FR-033.

### Quyết định canonical đề xuất (hiệu lực khi OQ-1 được duyệt)

| Chủ đề | Đề nghị canonical | Hệ quả với tài liệu hiện hành |
|--------|-------------------|-------------------------------|
| Customer wallet | **Tồn tại** — ví số dư chi tiêu được | Thay CONTEXT §2 Wallet "KHONG co vi cho Customer" |
| Nguồn tiền vào ví | Nạp VNPay, hoàn huỷ đơn, hoàn tranh chấp, bồi thường sự cố | 4 nguồn — xem Money Invariants |
| Nguồn tiền ra ví | Trả cọc 30%, trả nốt 70%, rút về ngân hàng | 3 nguồn |
| `RefundRecord` | **Thay thế hoàn toàn** bởi ví + `order_cancellation_refund` (Spec #022) | Gỡ khỏi CONTEXT §2 Thuật ngữ + §Huỷ đơn; làm rõ HR-14 — xem OQ-2 |
| Sổ cái | Tái dùng bảng `transaction` — không tạo `wallet_transaction` riêng cho Customer | AC-13 dùng tên `wallet_transaction` như tên khái niệm — xem DS-05 |
| Rút tiền | **Admin** duyệt thủ công, mirror luồng tài xế Spec #009 | Manager không có quyền — bổ sung 2 dòng CONTEXT §3 RBAC |
| `my-wallet.html` | Màn ví: số dư + nạp + lịch sử giao dịch | Thay Spec #004 §Goals ("lịch sử thanh toán chỉ đọc") |

---

## Scope Summary

**In scope:**

1. `GET /api/customer/wallet` — số dư và bốn chỉ số tổng hợp.
2. `GET /api/customer/wallet/transactions` — lịch sử giao dịch ví, server-side pagination.
3. `POST /api/customer/wallet/top-up/vnpay` — tạo URL VNPay nạp ví.
4. Cộng ví khi IPN nạp tiền hợp lệ (`WALLET_TOP_UP`).
5. Trả cọc 30% từ ví (`payDepositFromWallet`).
6. Trả nốt 70% từ ví (`payFinalFromWallet`).
7. Cộng ví khi hoàn tiền tranh chấp / huỷ đơn / bồi thường sự cố (`REFUND`, `DAMAGE_DEDUCTION`).
8. `POST /api/customer/wallet/withdrawals` — Customer tạo yêu cầu rút tiền.
9. `GET /api/customer/wallet/withdrawals` — lịch sử yêu cầu rút của chính mình.
10. `GET /api/admin/customer-withdrawals/pending` — hàng đợi Admin, FIFO + KPI.
11. `POST /api/admin/customer-withdrawals/{id}/process` (alias `/approve`) — xác nhận đã chuyển khoản.
12. `POST /api/admin/customer-withdrawals/{id}/reject` — từ chối kèm lý do.
13. Money invariants, transaction boundaries, audit trail, notification.
14. Loading/Empty/Error states cho bốn màn hình.

**Out of scope:**

1. Ví tài xế (`driver_wallet`), earning, cọc 3 triệu — Spec #007.
2. Rút tiền tài xế — Spec #009.
3. Ký/verify HMAC và idempotency IPN VNPay — thuộc luồng VNPay chung; spec này chỉ mô tả **hệ quả**
   lên ví sau khi IPN đã hợp lệ.
4. Tạo/duyệt yêu cầu huỷ đơn (`order_cancellation_refund`) — Spec #022; spec này chỉ mô tả bút toán
   cộng ví.
5. Quyết định bồi thường sự cố — Spec #023; spec này chỉ mô tả bút toán cộng ví.
6. Đối soát sổ cái toàn hệ thống — Spec #013.
7. Chuyển tiền giữa hai ví Customer.
8. Rút tiền tự động qua API ngân hàng (Admin chuyển khoản ngoài hệ thống).
9. Huỷ yêu cầu rút bởi Customer — trạng thái `CANCELLED` tồn tại trong DB nhưng **chưa có endpoint**
   (xem Deferred Scope).

---

## User Stories

**P1:**

**US1:** Là Customer, tôi xem được số dư ví, tổng đã nạp, tổng đã chi và tổng đã rút để biết mình
còn bao nhiêu tiền trong hệ thống.

**US2:** Là Customer, tôi nạp tiền vào ví qua VNPay để không phải thanh toán VNPay lại từ đầu ở mỗi đơn.

**US3:** Là Customer, tôi trả cọc 30% và trả nốt 70% bằng số dư ví chỉ với một cú bấm, không phải rời
khỏi hệ thống sang cổng VNPay.

**US4:** Là Customer, khi đơn bị huỷ hoặc tôi thắng tranh chấp, tôi nhận tiền hoàn vào ví ngay lập tức
thay vì chờ Manager xin số tài khoản rồi chuyển khoản thủ công.

**US5:** Là Customer, tôi tạo yêu cầu rút số dư ví về tài khoản ngân hàng của mình.

**US6:** Là Customer, tôi xem lịch sử yêu cầu rút tiền kèm trạng thái và lý do từ chối (nếu có).

**US7:** Là Admin, tôi xem hàng đợi yêu cầu rút tiền của khách theo FIFO kèm cảnh báo chặn (số dư
không đủ, thiếu số tài khoản) để biết yêu cầu nào xử lý được.

**US8:** Là Admin, sau khi chuyển khoản ngoài hệ thống, tôi nhập mã giao dịch ngân hàng và xác nhận
để hệ thống trừ ví khách và ghi sổ cái.

**P2:**

**US9:** Là Admin, tôi từ chối yêu cầu rút không hợp lệ kèm lý do; số dư khách không bị ảnh hưởng.

**US10:** Là Customer, tôi nhận notification khi yêu cầu rút được xử lý hoặc bị từ chối.

**US11:** Là Admin, tôi nhận notification khi có yêu cầu rút tiền mới của khách.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.
>
> Tổng: **72 FR**, trong đó **37 FR có mệnh đề WHERE** (51.4% ≥ 30% theo CLAUDE.md §5).

---

### Nhóm 1 — Wallet Summary (FR-001..FR-005)

**FR-001**  
WHEN Customer gọi `GET /api/customer/wallet`, THE system SHALL trả HTTP 200 với `balance`,
`totalToppedUp`, `totalSpent`, `totalWithdrawn` — tất cả là số nguyên VND (`BigDecimal` scale=0, AC-08).

**FR-002**  
WHEN Customer chưa có bản ghi `customer_wallet`, THE system SHALL tạo ví mới với cả bốn giá trị = 0
rồi trả về, thay vì trả 404.

**FR-003**  
WHILE ví tồn tại, THE system SHALL đảm bảo `balance >= 0` ở cả DB CHECK constraint và service
validation (HR-18); ví SHALL không bao giờ được phép âm dù bất kỳ luồng nào.

**FR-004**  
WHERE JWT thiếu hoặc hết hạn, THE system SHALL trả HTTP 401; WHERE role khác `CUSTOMER`, SHALL trả
HTTP 403 (HR-10); customer id SHALL luôn lấy từ JWT subject, không bao giờ từ request body/param.

**FR-005**  
WHEN trả `balance` về frontend, THE system SHALL trả số nguyên (ví dụ `500000`), và frontend SHALL
format bằng `Intl.NumberFormat('vi-VN')` với tabular numerals.

---

### Nhóm 2 — Wallet Transaction History (FR-006..FR-011)

**FR-006**  
WHEN Customer gọi `GET /api/customer/wallet/transactions?page={p}&size={s}`, THE system SHALL trả
`Page<TransactionDTO>` của **chính customer đó**, sắp xếp `createdAt` giảm dần (AC-15).

**FR-007**  
WHEN `size` không được cung cấp, THE system SHALL dùng default `20`.

**FR-008**  
WHERE `page < 0`, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Số trang không hợp lệ."

**FR-009**  
WHERE `size <= 0` hoặc `size > 100`, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Kích thước
trang phải từ 1 đến 100."

**FR-010**  
WHEN trả `TransactionDTO` chứa `vnpayTxnRef`, THE system SHALL mask chỉ còn 4 ký tự cuối dạng
`****1234`; SHALL không bao giờ trả mã giao dịch VNPay đầy đủ ra frontend.

**FR-011**  
WHILE Customer đọc lịch sử, THE system SHALL chỉ trả giao dịch có `user_id` = JWT subject; WHERE
Customer cố đọc giao dịch của người khác, SHALL không có đường dẫn nào cho phép (không có param
`userId` trên endpoint).

---

### Nhóm 3 — Nạp ví qua VNPay (FR-012..FR-018)

**FR-012**  
WHEN Customer gọi `POST /api/customer/wallet/top-up/vnpay` với `amount` hợp lệ, THE system SHALL tạo
URL thanh toán VNPay với `vnp_OrderInfo` = `"MoveHome wallet topup {compactUuid(customerId)}"` và trả
URL cho frontend redirect.

**FR-013**  
WHEN VNPay gửi IPN hợp lệ cho giao dịch nạp ví, THE system SHALL cộng `paidAmount` vào
`wallet.balance` VÀ cộng vào `wallet.totalToppedUp`, VÀ ghi một `transaction` type `WALLET_TOP_UP`
dương trong **cùng một transaction DB** (AC-13).

**FR-014**  
WHERE IPN không verify được HMAC-SHA512, THE system SHALL không thay đổi ví dưới bất kỳ hình thức nào
và trả `RspCode=97` (HR-04).

**FR-015**  
WHERE IPN trùng `vnp_TxnRef` đã xử lý thành công, THE system SHALL không cộng ví lần hai và trả
`RspCode=02` (HR-15); ví SHALL không bao giờ bị cộng hai lần cho một lần trả tiền thật.

**FR-016**  
WHERE Customer bị redirect về Return URL với `vnp_ResponseCode=00` nhưng IPN chưa tới, THE system
SHALL không cộng ví dựa trên Return URL (HR-03); Return URL chỉ để hiển thị kết quả.

**FR-017**  
WHEN nạp ví thành công, THE system SHALL ghi `balance_after` snapshot vào bản ghi `transaction` để
phục vụ đối soát (AC-13).

**FR-018**  
WHERE role khác `CUSTOMER` gọi endpoint nạp ví, THE system SHALL trả HTTP 403 (HR-10).

---

### Nhóm 4 — Thanh toán đơn bằng ví (FR-019..FR-026)

**FR-019**  
WHEN Customer trả cọc bằng ví, THE system SHALL tính `deposit` qua `OrderDepositCalculator.deposit(order)`,
trừ ví, ghi `transaction` type `ORDER_PAYMENT` với `amount` **âm** và `relatedOrderId`, rồi chuyển đơn
`PENDING_PAYMENT → CONFIRMED` qua `OrderStatusTransitionService` — tất cả trong một transaction DB.

**FR-020**  
WHERE đơn không ở trạng thái `PENDING` hoặc `PENDING_PAYMENT`, THE system SHALL trả HTTP 409
`INVALID_ORDER_STATUS` "Don hang khong o trang thai cho thanh toan." và SHALL không trừ ví (HR-05).

**FR-021**  
WHERE `wallet.balance < deposit`, THE system SHALL trả HTTP 422 `INSUFFICIENT_WALLET_BALANCE` với
message "Số dư ví không đủ. Vui lòng nạp thêm ví hoặc chọn thanh toán qua VNPay." và SHALL không trừ ví,
không đổi trạng thái đơn (HR-18).

**FR-022**  
WHEN Customer trả nốt 70% bằng ví, THE system SHALL tính `remaining` qua
`OrderDepositCalculator.finalAmount(order)`, trừ ví, ghi `ORDER_PAYMENT` âm, và set
`order.finalPaidAt = NOW()`; trạng thái đơn SHALL giữ nguyên `AWAITING_FINAL_PAYMENT` để tài xế bấm
Hoàn thành.

**FR-023**  
WHERE đơn không ở `AWAITING_FINAL_PAYMENT`, THE system SHALL trả HTTP 409 `INVALID_ORDER_STATUS`
"Don chua o buoc thanh toan not 70%."

**FR-024**  
WHERE `order.finalPaidAt != NULL`, THE system SHALL trả HTTP 409 `FINAL_ALREADY_PAID` "Don da thanh
toan not 70%." và SHALL không trừ ví lần hai (idempotency).

**FR-025**  
WHERE `order.customerId` khác JWT subject, THE system SHALL trả HTTP 403 `ORDER_OWNERSHIP_REQUIRED`
"Ban chi co the thanh toan don cua minh." (HR-10); WHERE đơn không tồn tại hoặc `deletedAt != NULL`,
SHALL trả HTTP 404 `ORDER_NOT_FOUND`.

**FR-026**  
WHEN trừ ví để trả đơn, THE system SHALL cộng số tiền vào `wallet.totalSpent` và load đơn bằng
`findByIdForUpdate` (pessimistic lock) để chống race condition khi hai request thanh toán đồng thời.

---

### Nhóm 5 — Tiền hoàn vào ví (FR-027..FR-031)

**FR-027**  
WHEN Manager duyệt yêu cầu huỷ đơn (Spec #022), THE system SHALL cộng cọc 30% vào `wallet.balance` và
ghi `transaction` type `REFUND` dương với `relatedOrderId`, trong cùng transaction của Manager
(`Propagation.MANDATORY`).

**FR-028**  
WHEN Manager quyết hoàn tiền tranh chấp (Spec #010), THE system SHALL cộng số tiền vào ví và ghi
`REFUND` dương với `relatedOrderId` + `relatedDisputeId`.

**FR-029**  
WHEN Manager quyết bồi thường sự cố tài xế (Spec #023), THE system SHALL cộng tiền bồi thường vào ví
khách và ghi `transaction` type `DAMAGE_DEDUCTION`.

**FR-030**  
WHILE cộng ví, THE system SHALL luôn ghi `balance_after` snapshot và SHALL chạy trong transaction của
caller (`Propagation.MANDATORY`) — WHERE không có transaction đang mở, SHALL throw
`IllegalTransactionStateException` thay vì cộng ví ngoài transaction.

**FR-031**  
WHERE ví của customer chưa tồn tại khi hoàn tiền, THE system SHALL gọi `insertIfMissing(customerId)`
tạo ví trước rồi mới cộng; SHALL không trả lỗi cho Manager vì lý do khách chưa từng có ví.

---

### Nhóm 6 — Customer tạo yêu cầu rút tiền (FR-032..FR-041)

**FR-032**  
WHEN Customer gọi `POST /api/customer/wallet/withdrawals` với body hợp lệ, THE system SHALL tạo bản
ghi `customer_withdrawal_request` status `PENDING` và trả HTTP 201 với `id`, `amount`, `status`,
`message` "Yêu cầu rút tiền đã được gửi.", `requestedAt`.

**FR-033**  
WHEN tính số tiền được phép rút, THE system SHALL dùng công thức
`available = wallet.balance − Σ(amount của mọi request PENDING của customer đó)`; WHERE `available < 0`,
SHALL coi như `0`.

**FR-034**  
WHERE `amount > available`, THE system SHALL trả HTTP 409 `INSUFFICIENT_AVAILABLE_BALANCE` "Số tiền rút
vượt quá số dư khả dụng." và SHALL không tạo request.

**FR-035**  
WHERE `amount` là `null`, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Vui lòng nhập số tiền cần
rút."; WHERE `amount` có phần thập phân khác 0, SHALL trả 422 "Số tiền rút phải là VND nguyên đồng.";
WHERE `amount <= 0`, SHALL trả 422 "Số tiền rút phải lớn hơn 0." (AC-08).

**FR-036**  
WHERE `bankCode` rỗng, THE system SHALL trả HTTP 422 `VALIDATION_ERROR` "Vui lòng chọn ngân hàng nhận
tiền."; WHERE `bankCode` không thuộc whitelist 8 ngân hàng (`VCB`, `BIDV`, `CTG`, `TCB`, `MB`, `ACB`,
`VPB`, `AGR`), SHALL trả 422 "Ngân hàng không được hỗ trợ."

**FR-037**  
WHERE `bankAccountNumber` rỗng, THE system SHALL trả HTTP 422 "Vui lòng nhập số tài khoản ngân hàng.";
WHERE không khớp regex `^[0-9]{8,15}$`, SHALL trả 422 "Số tài khoản không hợp lệ (phải gồm 8 đến 15
chữ số)."

**FR-038**  
WHEN tạo request, THE system SHALL snapshot `bank_name_snapshot` từ whitelist theo `bankCode`, và
snapshot `bank_account_holder` từ `user.fullName` đã normalize (NFC, gộp khoảng trắng, UPPERCASE, cắt
100 ký tự); WHERE `fullName` rỗng, SHALL dùng `"CHUA CAP NHAT"`.

**FR-039**  
WHEN đọc ví và các request PENDING để tính `available`, THE system SHALL dùng
`findByCustomerIdForUpdate` và `findPendingByCustomerIdForUpdate` (pessimistic lock) để hai request
rút đồng thời không cùng vượt số dư.

**FR-040**  
WHEN request được tạo, THE system SHALL ghi audit log dòng
`customer_withdrawal_state_audit actor_id=... actor_role=CUSTOMER timestamp=... from_state=null
to_state=PENDING entity_id=...` (HR-13).

**FR-041**  
WHEN request được tạo, THE system SHALL tạo notification `WITHDRAWAL_REQUESTED` cho **mọi Admin
ACTIVE**; WHERE việc tạo notification lỗi, SHALL log warning và **vẫn trả 201** — lỗi notification
SHALL không rollback việc tạo yêu cầu rút (tinh thần HR-11).

---

### Nhóm 7 — Lịch sử rút tiền của Customer (FR-042..FR-045)

**FR-042**  
WHEN Customer gọi `GET /api/customer/wallet/withdrawals`, THE system SHALL trả
`Page<CustomerWithdrawalItemResponse>` của chính mình, sắp xếp `requestedAt` DESC rồi `id` DESC.

**FR-043**  
WHEN trả item, THE system SHALL bao gồm `id`, `amount`, `status`, `bankName`, `maskedAccount`,
`rejectionReason`, `requestedAt`, `processedAt`.

**FR-044**  
WHEN trả `bankAccountNumber`, THE system SHALL mask thành `******{4 số cuối}`; SHALL không bao giờ trả
số tài khoản đầy đủ về frontend Customer.

**FR-045**  
WHERE `page`/`size` không hợp lệ, THE system SHALL áp dụng cùng validation của FR-008/FR-009.

---

### Nhóm 8 — Admin hàng đợi rút tiền (FR-046..FR-050)

**FR-046**  
WHEN Admin gọi `GET /api/admin/customer-withdrawals/pending`, THE system SHALL trả các request status
`PENDING` sắp xếp **FIFO** (`requestedAt` ASC, `id` ASC).

**FR-047**  
WHEN trả hàng đợi, THE system SHALL kèm KPI: `totalPending` (count), `totalPendingAmount` (sum),
`oldestWaitingDays`, `countPendingOver24h`.

**FR-048**  
WHEN trả mỗi item, THE system SHALL tính `blockingReasons` gồm: `CUSTOMER_NOT_FOUND` nếu không tìm
thấy user; `INSUFFICIENT_CURRENT_BALANCE` nếu `wallet.balance < amount`; `BANK_ACCOUNT_MISSING` nếu
thiếu số tài khoản; và `actionable = blockingReasons.isEmpty()`.

**FR-049**  
WHEN trả item cho Admin, THE system SHALL kèm `customerName`, `customerPhone`, `walletBalance`,
`waitingDays`, và số tài khoản **đã mask** `******{4 số cuối}`.

**FR-050**  
WHERE role khác `ADMIN` gọi bất kỳ endpoint `/api/admin/customer-withdrawals/**`, THE system SHALL trả
HTTP 403 (HR-10); Manager KHÔNG có quyền duyệt rút tiền khách (nhất quán CONTEXT §RBAC "Duyet
Withdrawal cua Driver: Admin Yes / Manager No").

---

### Nhóm 9 — Admin duyệt rút tiền (FR-051..FR-060)

**FR-051**  
WHEN Admin gọi `POST /api/admin/customer-withdrawals/{id}/process` với `bankTxnRef` hợp lệ, THE system
SHALL: trừ `wallet.balance`, cộng `wallet.totalWithdrawn`, ghi `transaction` type `WITHDRAWAL` với
`amount` âm + `relatedCustomerWithdrawalId` + `balanceAfter`, set request `PROCESSED` +
`processedBy` + `processedAt` + `bankTxnRef` — **tất cả trong một transaction DB** (AC-13).

**FR-052**  
WHERE request không tồn tại, THE system SHALL trả HTTP 404 `WITHDRAWAL_NOT_FOUND` "Khong tim thay yeu
cau rut tien."

**FR-053**  
WHERE request đã `PROCESSED` **và** `bankTxnRef` gửi lên trùng với `bankTxnRef` đã lưu, THE system
SHALL trả HTTP 200 với kết quả cũ (replay an toàn), SHALL không trừ ví lần hai.

**FR-054**  
WHERE request không ở `PENDING` (và không thuộc trường hợp replay FR-053), THE system SHALL trả HTTP
409 `INVALID_WITHDRAWAL_TRANSITION` "Yeu cau rut tien da duoc xu ly." (HR-05).

**FR-055**  
WHERE `bankTxnRef` đã tồn tại ở request khác, THE system SHALL trả HTTP 409 `DUPLICATE_BANK_TXN_REF`
"Ma giao dich ngan hang da duoc su dung." — chống ghi nhận hai lần cho một lần chuyển khoản thật.

**FR-056**  
WHERE `wallet.balance < amount` tại thời điểm duyệt, THE system SHALL trả HTTP 422
`INSUFFICIENT_CURRENT_BALANCE` "So du hien tai khong du." và SHALL không trừ ví (HR-18) — số dư có thể
đã giảm sau khi request được tạo.

**FR-057**  
WHERE đã tồn tại `transaction` type `WITHDRAWAL` với `relatedCustomerWithdrawalId` = id này, THE system
SHALL trả HTTP 409 `WITHDRAWAL_TRANSACTION_EXISTS` "Yeu cau rut tien da co giao dich."

**FR-058**  
WHERE `bankTxnRef` rỗng, THE system SHALL trả 422 `INVALID_BANK_TXN_REF` "Ma giao dich ngan hang bat
buoc."; WHERE độ dài ngoài `[6, 100]` hoặc không khớp `^[A-Za-z0-9._/-]+$`, SHALL trả 422 "Ma giao
dich ngan hang khong hop le."

**FR-059**  
WHEN duyệt thành công, THE system SHALL ghi `AuditLog` action `CUSTOMER_WITHDRAWAL_PROCESSED`,
`entityType` = `CUSTOMER_WITHDRAWAL_REQUEST`, detail chứa `amount`, `balance_after`, `bank_ref` và
`note=present|none` (HR-13); detail SHALL không chứa số tài khoản đầy đủ.

**FR-060**  
WHEN duyệt thành công, THE system SHALL tạo notification `WITHDRAWAL_PROCESSED` cho Customer; WHERE
`DataIntegrityViolationException` xảy ra (unique index chặn double-process), SHALL trả HTTP 409
`DUPLICATE_WITHDRAWAL_PROCESSING` "Yeu cau rut tien da duoc xu ly."

---

### Nhóm 10 — Admin từ chối rút tiền (FR-061..FR-065)

**FR-061**  
WHEN Admin gọi `POST /api/admin/customer-withdrawals/{id}/reject` với `reason` hợp lệ, THE system SHALL
set status `REJECTED` + `rejectionReason` + `processedBy` + `processedAt`, và SHALL **không** thay đổi
ví, **không** ghi transaction — từ chối không có hệ quả tiền.

**FR-062**  
WHERE `reason` rỗng, THE system SHALL trả 422 `INVALID_REJECTION_REASON` "Ly do tu choi bat buoc.";
WHERE độ dài ngoài `[10, 500]` hoặc không chứa ký tự chữ nào, SHALL trả 422 "Ly do tu choi khong hop le."

**FR-063**  
WHERE request đã `REJECTED` **và** `reason` trùng lý do đã lưu, THE system SHALL trả HTTP 200 kết quả cũ
(replay an toàn).

**FR-064**  
WHERE request không ở `PENDING` (và không thuộc FR-063), THE system SHALL trả HTTP 409
`INVALID_WITHDRAWAL_TRANSITION`.

**FR-065**  
WHEN từ chối thành công, THE system SHALL ghi `AuditLog` action `CUSTOMER_WITHDRAWAL_REJECTED` và tạo
notification `WITHDRAWAL_REJECTED` cho Customer kèm lý do.

---

### Nhóm 11 — RBAC, Audit & Data Integrity (FR-066..FR-072)

**FR-066**  
WHILE mọi endpoint `/api/customer/wallet/**` chạy, THE system SHALL enforce `@PreAuthorize("hasRole('CUSTOMER')")`
và lấy customer id từ `@AuthenticationPrincipal`; WHERE request cố truyền customer id khác, SHALL bị bỏ
qua hoàn toàn (không có param nào nhận id).

**FR-067**  
WHILE mọi endpoint `/api/admin/customer-withdrawals/**` chạy, THE system SHALL enforce
`@PreAuthorize("hasRole('ADMIN')")`.

**FR-068**  
WHILE bản ghi tồn tại trong `transaction`, THE system SHALL coi đó là sổ cái **append-only**: SHALL
không UPDATE, SHALL không DELETE; muốn đảo giao dịch phải ghi bút toán mới (AC-13).

**FR-069**  
WHERE DB CHECK `ck_customer_withdrawal_terminal_fields` bị vi phạm (ví dụ `PROCESSED` mà thiếu
`bank_txn_ref`), THE system SHALL để DB reject transaction thay vì lưu bản ghi không nhất quán.

**FR-070**  
WHILE `status = 'PENDING'`, THE system SHALL đảm bảo `processed_by`, `processed_at`, `bank_txn_ref`,
`rejection_reason` đều `NULL` theo CHECK constraint.

**FR-071**  
WHEN ghi `transaction` cho rút tiền, THE system SHALL dùng cột `related_customer_withdrawal_id` (KHÔNG
dùng `related_withdrawal_id` của tài xế) để không đụng unique index `uq_transaction_withdrawal`.

**FR-072**  
WHILE mọi thao tác tiền chạy, THE system SHALL dùng `BigDecimal` scale=0 với `RoundingMode.HALF_UP` khi
normalize và `RoundingMode.UNNECESSARY` khi validate input; SHALL không dùng `double`/`float` ở bất kỳ
tầng nào (AC-08).

---

## Non-Functional Requirements

| ID | Yêu cầu | Ngưỡng |
|----|---------|--------|
| NFR-001 | `GET /api/customer/wallet` p95 | < 300 ms |
| NFR-002 | `GET /api/customer/wallet/transactions` p95 (size=20) | < 500 ms |
| NFR-003 | `GET /api/admin/customer-withdrawals/pending` p95 | < 800 ms |
| NFR-004 | Duyệt rút tiền (process) p95 | < 1000 ms |
| NFR-005 | Pessimistic lock trên ví | Giữ < 200 ms để không tắc hàng đợi |
| NFR-006 | Pagination | Default 20, max 100 (AC-15) |
| NFR-007 | Money precision | `NUMERIC(15,0)` / `BigDecimal` scale=0 — không sai số (AC-08) |
| NFR-008 | Timestamp | `TIMESTAMPTZ` UTC, hiển thị `Asia/Ho_Chi_Minh` (AC-07) |
| NFR-009 | Empty/Loading/Error states | Bắt buộc cả 4 màn (AC-16) |
| NFR-010 | Vietnamese diacritics | 100% text user-facing (HR-20) |
| NFR-011 | Audit trail | 100% thay đổi số dư có `transaction` tương ứng (AC-13) |
| NFR-012 | Concurrency | 2 request rút đồng thời không thể vượt tổng số dư (FR-039) |

---

## API Endpoints Summary

| Method | Path | Auth | Request | Response | Ghi chú |
|--------|------|------|---------|----------|---------|
| GET | `/api/customer/wallet` | CUSTOMER | — | 200 `WalletSummaryDTO` | Auto-create ví nếu chưa có |
| GET | `/api/customer/wallet/transactions` | CUSTOMER | `page`, `size` | 200 `Page<TransactionDTO>` | Default size 20 |
| POST | `/api/customer/wallet/top-up/vnpay` | CUSTOMER | `{amount}` | 200 `{paymentUrl}` | Nằm ở `VnPayController` |
| POST | `/api/customer/wallet/withdrawals` | CUSTOMER | `CreateCustomerWithdrawalRequest` | 201 `CustomerWithdrawalRequestResponse` | |
| GET | `/api/customer/wallet/withdrawals` | CUSTOMER | `page`, `size` | 200 `Page<CustomerWithdrawalItemResponse>` | Số TK masked |
| GET | `/api/admin/customer-withdrawals/pending` | ADMIN | `page`, `size` | 200 `PendingCustomerWithdrawalPageResponse` | FIFO + KPI |
| POST | `/api/admin/customer-withdrawals/{id}/process` | ADMIN | `ProcessWithdrawalRequest` | 200 `WithdrawalActionResponse` | Alias `/approve` |
| POST | `/api/admin/customer-withdrawals/{id}/reject` | ADMIN | `RejectWithdrawalRequest` | 200 `WithdrawalActionResponse` | Không đụng tiền |

### Standard Error (ES-04)

```json
{
  "error_code": "INSUFFICIENT_AVAILABLE_BALANCE",
  "message": "Số tiền rút vượt quá số dư khả dụng.",
  "details": []
}
```

> **Convention nội bộ:** service throw `ResponseStatusException` với reason dạng
> `"ERROR_CODE|Message tiếng Việt"`. Việc map sang envelope ES-04 do `RestControllerAdvice` chung
> (Spec #018) đảm nhiệm. Spec này không đổi convention đó.

---

## Data Model

### Schema Design

Ví khách tái dùng sổ cái `transaction` (V6) đã có — **không** tạo bảng ledger riêng. Chỉ cần 2 bảng mới,
chia theo 2 giai đoạn triển khai:

| Migration | Giai đoạn | Nội dung |
|-----------|-----------|----------|
| `V6__create_transaction_table.sql` | Đã có | Sổ cái `transaction` append-only — tái dùng |
| `V8__create_customer_wallet.sql` | Sprint 4 | `customer_wallet` + trigger `updated_at` |
| `V24__admin_finance_withdrawal_transaction.sql` | Đã có | Cột finance cho `transaction` — tái dùng |
| `V39__create_customer_withdrawal_request.sql` | Sprint 6 | `customer_withdrawal_request`, `customer_wallet.total_withdrawn`, `transaction.related_customer_withdrawal_id` |

**Quyết định tái dùng `transaction`:** ví Driver (Spec #007) đã ghi vào bảng này; tách sổ cái riêng cho
Customer sẽ làm đối soát toàn hệ thống (Spec #013) phải union hai nguồn. Đánh đổi: lệch tên bảng so với
AC-13 (`wallet_transaction`) — xem DS-05.

### Table `customer_wallet` (V8 + V39)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | `gen_random_uuid()` |
| `customer_id` | `UUID` NOT NULL UNIQUE → `app_user(id)` | Một ví cho một khách |
| `balance` | `NUMERIC(15,0)` NOT NULL DEFAULT 0 | `CHECK (balance >= 0)` — HR-18 |
| `total_topped_up` | `NUMERIC(15,0)` NOT NULL DEFAULT 0 | Tổng đã nạp |
| `total_spent` | `NUMERIC(15,0)` NOT NULL DEFAULT 0 | Tổng đã chi trả đơn |
| `total_withdrawn` | `NUMERIC(15,0)` NOT NULL DEFAULT 0 | Thêm ở V39 |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | Trigger `trg_customer_wallet_updated_at` |

### Table `customer_withdrawal_request` (V39)

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | `UUID` PK | |
| `customer_id` | `UUID` NOT NULL → `app_user(id)` | |
| `amount` | `NUMERIC(15,0)` NOT NULL | `CHECK (amount > 0)` — không có min theo quyết định leader |
| `bank_code` | `VARCHAR(20)` NOT NULL | Whitelist 8 bank |
| `bank_name_snapshot` | `VARCHAR(100)` NOT NULL | Snapshot lúc gửi |
| `bank_account_number` | `VARCHAR(20)` NOT NULL | Lưu đầy đủ; mask khi trả API |
| `bank_account_holder` | `VARCHAR(100)` NOT NULL | Từ `user.fullName`, UPPERCASE |
| `note` | `VARCHAR(500)` | |
| `status` | `VARCHAR(20)` NOT NULL DEFAULT `'PENDING'` | `CHECK IN (PENDING, PROCESSED, REJECTED, CANCELLED)` — AC-14 |
| `rejection_reason` | `VARCHAR(500)` | Bắt buộc khi REJECTED |
| `processed_by` | `UUID` → `app_user(id)` | Admin xử lý |
| `bank_txn_ref` | `VARCHAR(100)` | Unique khi NOT NULL |
| `requested_at` / `processed_at` / `cancelled_at` | `TIMESTAMPTZ` | AC-07 |
| `idempotency_key` | `UUID` NOT NULL | `UNIQUE (customer_id, idempotency_key)` |
| `version` | `BIGINT` NOT NULL DEFAULT 0 | Optimistic locking |

**Indexes:** `idx_customer_withdrawal_customer_requested`, `idx_customer_withdrawal_pending_fifo`
(partial WHERE PENDING), `idx_customer_withdrawal_history_processed` (partial),
`uq_customer_withdrawal_bank_txn_ref` (partial unique).

**CHECK `ck_customer_withdrawal_terminal_fields` (NOT VALID):** ràng buộc field theo status —
`PENDING` (mọi field xử lý NULL) | `PROCESSED` (`processed_by`, `processed_at`, `bank_txn_ref` NOT NULL,
`rejection_reason` NULL) | `REJECTED` (`processed_by`, `processed_at`, `rejection_reason` NOT NULL,
`bank_txn_ref` NULL) | `CANCELLED` (`cancelled_at` NOT NULL, `bank_txn_ref` NULL).

### Table `transaction` — sổ cái dùng chung

Ví khách **tái dùng** bảng `transaction` (V6), không có bảng `wallet_transaction` riêng.

| Cột liên quan ví khách | Ghi chú |
|------------------------|---------|
| `user_id` | Customer sở hữu giao dịch |
| `type` | `WALLET_TOP_UP`, `ORDER_PAYMENT`, `REFUND`, `DAMAGE_DEDUCTION`, `WITHDRAWAL` |
| `amount` | Dương = tiền vào ví; âm = tiền ra ví |
| `balance_after` | Snapshot số dư sau giao dịch (AC-13) |
| `related_order_id` | Khi liên quan đơn |
| `related_dispute_id` | Khi liên quan tranh chấp |
| `related_customer_withdrawal_id` | **Chỉ** cho rút tiền khách (V39) |

**Unique index `uq_transaction_customer_withdrawal`:** một yêu cầu rút chỉ có tối đa một transaction
`WITHDRAWAL` — chống double-process ở tầng DB.

### `TransactionType` enum (8 giá trị)

`DEPOSIT_TOP_UP`, `DEPOSIT_REFUND`, `ORDER_PAYMENT`, `WALLET_TOP_UP`, `DRIVER_EARNING`,
`PLATFORM_FEE`, `DAMAGE_DEDUCTION`, `WITHDRAWAL`.

---

## Money Invariants

| ID | Invariant | Enforce ở đâu |
|----|-----------|---------------|
| MI-001 | `customer_wallet.balance >= 0` luôn luôn | DB CHECK + service check trước khi trừ (HR-18) |
| MI-002 | Mỗi UPDATE ví đi kèm đúng một INSERT `transaction` trong cùng TX | Service `@Transactional` (AC-13) |
| MI-003 | `Σ(transaction.amount của customer) == wallet.balance` | Sanity check đối soát (Spec #013) |
| MI-004 | Một `customer_withdrawal_request` có tối đa một `transaction` WITHDRAWAL | Unique index `uq_transaction_customer_withdrawal` |
| MI-005 | Một `bank_txn_ref` chỉ dùng cho một request | Partial unique index + check ở service |
| MI-006 | `available = balance − Σ(PENDING)` không bao giờ cho rút vượt | FR-033/FR-034 + pessimistic lock |
| MI-007 | REJECTED không đổi số dư | FR-061 |
| MI-008 | `transaction` là append-only | Không có code UPDATE/DELETE trên bảng này (AC-13) |
| MI-009 | Tiền ra ví luôn `amount < 0`; tiền vào ví luôn `amount > 0` | Convention ở service |
| MI-010 | `total_withdrawn` chỉ tăng khi PROCESSED | FR-051 |

### Luồng tiền — tổng quan

**Vào ví (amount dương):**

| Nguồn | Type | Trigger | Spec |
|-------|------|---------|------|
| Nạp ví VNPay | `WALLET_TOP_UP` | IPN hợp lệ | Spec này FR-013 |
| Hoàn cọc huỷ đơn | `REFUND` | Manager duyệt | Spec #022 |
| Hoàn tiền tranh chấp | `REFUND` | Manager quyết | Spec #010 |
| Bồi thường sự cố | `DAMAGE_DEDUCTION` | Manager quyết | Spec #023 |

**Ra ví (amount âm):**

| Đích | Type | Trigger | Spec |
|------|------|---------|------|
| Trả cọc 30% | `ORDER_PAYMENT` | Customer bấm trả bằng ví | Spec này FR-019 |
| Trả nốt 70% | `ORDER_PAYMENT` | Customer bấm trả bằng ví | Spec này FR-022 |
| Rút về ngân hàng | `WITHDRAWAL` | Admin PROCESSED | Spec này FR-051 |

---

## Transaction Boundaries

### Nạp ví (IPN)

```
BEGIN
  verify HMAC-SHA512 (HR-04)            -- fail → RspCode=97, không mở TX ghi
  check vnp_TxnRef đã xử lý (HR-15)     -- trùng → RspCode=02, không ghi
  lock wallet FOR UPDATE
  wallet.balance        += paidAmount
  wallet.totalToppedUp  += paidAmount
  INSERT transaction(WALLET_TOP_UP, +paidAmount, balance_after)
COMMIT
```

### Trả đơn bằng ví

```
BEGIN
  order = findByIdForUpdate(orderId)     -- pessimistic lock
  assert order.customerId == jwt.sub     -- 403 nếu sai (HR-10)
  assert order.status hợp lệ             -- 409 nếu sai (HR-05)
  wallet = findByCustomerIdForUpdate()
  assert wallet.balance >= amount        -- 422 nếu thiếu (HR-18)
  wallet.balance   -= amount
  wallet.totalSpent += amount
  INSERT transaction(ORDER_PAYMENT, -amount, balance_after, related_order_id)
  transition(order → CONFIRMED)  |  order.finalPaidAt = NOW()
COMMIT
```

### Tạo yêu cầu rút

```
BEGIN
  walletRepository.insertIfMissing(customerId)
  wallet    = findByCustomerIdForUpdate(customerId)
  pendings  = findPendingByCustomerIdForUpdate(customerId)   -- lock
  available = wallet.balance − Σ(pendings.amount)
  assert amount <= available                                  -- 409 nếu vượt
  INSERT customer_withdrawal_request(PENDING)
  log audit (HR-13)
COMMIT
-- notification cho Admin: try/catch, lỗi KHÔNG rollback (FR-041)
```

### Admin duyệt rút

```
BEGIN
  withdrawal = findByIdForUpdate(id)
  if PROCESSED && same bankTxnRef → return replay (không ghi)   -- FR-053
  assert status == PENDING                                       -- 409
  assert !existsByBankTxnRef(bankTxnRef)                          -- 409
  wallet = findByCustomerIdForUpdate()
  assert wallet.balance >= amount                                 -- 422 (HR-18)
  assert !existsTransaction(WITHDRAWAL, id)                       -- 409
  wallet.balance        -= amount
  wallet.totalWithdrawn += amount
  INSERT transaction(WITHDRAWAL, -amount, balance_after, related_customer_withdrawal_id)
  withdrawal → PROCESSED + processedBy + processedAt + bankTxnRef
  INSERT AuditLog(CUSTOMER_WITHDRAWAL_PROCESSED)
  INSERT Notification(WITHDRAWAL_PROCESSED)
COMMIT   -- DataIntegrityViolation → 409 DUPLICATE_WITHDRAWAL_PROCESSING
```

### Hoàn tiền vào ví (từ Manager)

```
-- Propagation.MANDATORY: BẮT BUỘC chạy trong TX của caller
walletRepository.insertIfMissing(customerId)
wallet = findByCustomerIdForUpdate(customerId)
wallet.balance += amount
INSERT transaction(REFUND | DAMAGE_DEDUCTION, +amount, balance_after, related_order_id[, related_dispute_id])
```

---

## State Machine

### `customer_withdrawal_request`

```
        POST /api/customer/wallet/withdrawals
                    │
                    ▼
                [PENDING] ──────────────────────────┐
                    │                               │
   Admin /process   │           Admin /reject       │
   (bankTxnRef)     │           (reason ≥10 ký tự)  │
                    ▼                               ▼
              [PROCESSED]                      [REJECTED]
         ví bị trừ + transaction          ví KHÔNG đổi, không transaction
              (terminal)                       (terminal)

                [CANCELLED] ← ⚠️ CHECK constraint cho phép,
                               bản 1.0.0 chưa đặc tả endpoint (DS-01)
```

| Từ | Sang | Actor | Điều kiện | Hệ quả tiền |
|----|------|-------|-----------|-------------|
| (init) | `PENDING` | CUSTOMER | `amount <= available`, bank hợp lệ | Không (chỉ giữ chỗ logic) |
| `PENDING` | `PROCESSED` | ADMIN | `bankTxnRef` hợp lệ + unique, `balance >= amount` | Trừ ví + `WITHDRAWAL` |
| `PENDING` | `REJECTED` | ADMIN | `reason` 10–500 ký tự có chữ | Không |
| `PENDING` | `CANCELLED` | — | **Chưa implement** | — |

Mọi transition ngoài bảng → HTTP 409 `INVALID_WITHDRAWAL_TRANSITION` (HR-05).

---

## Error Matrix

| HTTP | error_code | Khi nào | Message |
|------|-----------|---------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn | — |
| 403 | `FORBIDDEN` | Sai role | — |
| 403 | `ORDER_OWNERSHIP_REQUIRED` | Trả đơn của người khác | "Ban chi co the thanh toan don cua minh." |
| 404 | `ORDER_NOT_FOUND` | Đơn không tồn tại/đã xoá | "Khong tim thay don hang." |
| 404 | `WITHDRAWAL_NOT_FOUND` | Request rút không tồn tại | "Khong tim thay yeu cau rut tien." |
| 409 | `CUSTOMER_WALLET_NOT_FOUND` | Ví không tìm thấy sau insertIfMissing | "Không tìm thấy ví khách hàng." |
| 409 | `WALLET_NOT_FOUND` | Ví không thấy khi trả đơn | "Khong tim thay vi khach hang." |
| 409 | `INSUFFICIENT_AVAILABLE_BALANCE` | Rút vượt `balance − Σ PENDING` | "Số tiền rút vượt quá số dư khả dụng." |
| 409 | `INVALID_ORDER_STATUS` | Đơn sai trạng thái để trả | "Don hang khong o trang thai cho thanh toan." / "Don chua o buoc thanh toan not 70%." |
| 409 | `FINAL_ALREADY_PAID` | Đã trả 70% rồi | "Don da thanh toan not 70%." |
| 409 | `INVALID_WITHDRAWAL_TRANSITION` | Request không còn PENDING | "Yeu cau rut tien da duoc xu ly." |
| 409 | `DUPLICATE_BANK_TXN_REF` | `bankTxnRef` đã dùng | "Ma giao dich ngan hang da duoc su dung." |
| 409 | `WITHDRAWAL_TRANSACTION_EXISTS` | Đã có transaction WITHDRAWAL | "Yeu cau rut tien da co giao dich." |
| 409 | `DUPLICATE_WITHDRAWAL_PROCESSING` | Unique index chặn | "Yeu cau rut tien da duoc xu ly." |
| 422 | `INSUFFICIENT_WALLET_BALANCE` | Ví không đủ trả đơn | "Số dư ví không đủ. Vui lòng nạp thêm ví hoặc chọn thanh toán qua VNPay." |
| 422 | `INSUFFICIENT_CURRENT_BALANCE` | Ví không đủ lúc Admin duyệt | "So du hien tai khong du." |
| 422 | `INVALID_BANK_TXN_REF` | Mã giao dịch sai định dạng | "Ma giao dich ngan hang khong hop le." |
| 422 | `INVALID_REJECTION_REASON` | Lý do < 10 ký tự / không có chữ | "Ly do tu choi khong hop le." |
| 422 | `VALIDATION_ERROR` | amount/bank/page/size sai | Theo FR-035..FR-037, FR-008/009 |

---

## Frontend Screen Contract

### `customer/my-wallet.html` — "Ví của tôi"

| Thành phần | Nguồn dữ liệu | Ghi chú |
|------------|---------------|---------|
| Card số dư | `GET /api/customer/wallet` → `balance` | `tnum`, `Intl.NumberFormat('vi-VN')` |
| 3 chỉ số | `totalToppedUp`, `totalSpent`, `totalWithdrawn` | |
| Nút "Nạp tiền" | `POST /api/customer/wallet/top-up/vnpay` → redirect | |
| Bảng giao dịch | `GET /api/customer/wallet/transactions?page&size` | Pagination server-side |
| Loading | Skeleton / "Đang tải..." | AC-16 |
| Empty | "Chưa có giao dịch nào" | AC-16 |
| Error | "Không thể tải dữ liệu" + nút "Tải lại" | AC-16 |

### `customer/withdrawal-request.html` — "Yêu cầu rút tiền"

| Thành phần | Contract |
|------------|----------|
| Hiển thị số dư | `GET /api/customer/wallet` |
| Input số tiền | Number, > 0, nguyên đồng |
| Chọn ngân hàng | 8 bank whitelist |
| Số tài khoản | 8–15 chữ số |
| Submit | `POST /api/customer/wallet/withdrawals` → 201 |
| Lỗi 409/422 | Hiển thị message tiếng Việt từ backend |

### `customer/withdrawal-history.html` — "Lịch sử rút tiền"

| Thành phần | Contract |
|------------|----------|
| Bảng | `GET /api/customer/wallet/withdrawals?page&size` → `Page.content` |
| Cột | Số tiền, Ngân hàng, STK (masked), Trạng thái, Lý do từ chối, Ngày yêu cầu, Ngày xử lý |
| Badge trạng thái | `PENDING` → "Đang chờ" (warning) · `PROCESSED` → "Đã chuyển" (success) · `REJECTED` → "Bị từ chối" (danger) · `CANCELLED` → "Đã huỷ" (neutral) |

### `admin/customer-withdrawals.html` — "Rút tiền khách hàng"

| Thành phần | Contract |
|------------|----------|
| KPI | `totalPending`, `totalPendingAmount`, `oldestWaitingDays`, `countPendingOver24h` |
| Bảng FIFO | `GET /api/admin/customer-withdrawals/pending?page&size` |
| Cảnh báo | `blockingReasons` → badge; `actionable=false` → disable nút duyệt |
| Modal duyệt | `POST /{id}/process` với `bankTxnRef` + `processingNote` |
| Modal từ chối | `POST /{id}/reject` với `reason` |

---

## Security & Privacy

| Chủ đề | Quy tắc |
|--------|---------|
| Ownership | Customer id luôn từ JWT; endpoint không nhận id từ client (FR-066) |
| RBAC | `/api/customer/wallet/**` → CUSTOMER; `/api/admin/customer-withdrawals/**` → ADMIN (HR-10) |
| Manager | KHÔNG có quyền với ví/rút tiền khách |
| Số tài khoản | Lưu đầy đủ trong DB; **luôn mask** `******{4 cuối}` khi trả API (Customer và Admin) |
| VNPay TxnRef | Mask `****{4 cuối}` khi trả về Customer (FR-010) |
| Audit detail | Chứa amount/balance_after/bank_ref; **không** chứa số tài khoản đầy đủ (FR-059) |
| IPN | Verify HMAC trước mọi thay đổi ví (HR-04); idempotent theo `vnp_TxnRef` (HR-15) |
| Return URL | Không bao giờ cập nhật ví (HR-03) |
| Secrets | VNPay/DB credentials qua env, không hardcode (HR-01) |

---

## Acceptance Criteria

| ID | Tiêu chí | Cách verify |
|----|----------|-------------|
| AC-021-01 | Ví tự tạo khi Customer mới gọi `GET /api/customer/wallet` | Tài khoản mới → 200, balance 0 |
| AC-021-02 | Nạp ví qua VNPay cộng đúng balance + totalToppedUp | IPN sandbox → so DB |
| AC-021-03 | IPN trùng không cộng ví lần hai | Gửi IPN 2 lần cùng TxnRef → balance không đổi |
| AC-021-04 | Trả cọc bằng ví → đơn CONFIRMED + balance giảm đúng | E2E booking |
| AC-021-05 | Ví thiếu tiền → 422, không trừ, đơn không đổi trạng thái | Test |
| AC-021-06 | Trả 70% hai lần → lần 2 trả 409 `FINAL_ALREADY_PAID` | Test |
| AC-021-07 | Rút vượt available → 409 | Test với 1 PENDING sẵn |
| AC-021-08 | 2 request rút đồng thời không vượt tổng số dư | Concurrency test |
| AC-021-09 | Admin duyệt → balance giảm, `WITHDRAWAL` âm, totalWithdrawn tăng | DB check |
| AC-021-10 | Duyệt 2 lần cùng bankTxnRef → replay 200, không trừ 2 lần | Test |
| AC-021-11 | bankTxnRef trùng request khác → 409 | Test |
| AC-021-12 | Từ chối không đổi balance | DB check |
| AC-021-13 | Số tài khoản luôn masked trong mọi response | Grep response |
| AC-021-14 | Manager gọi endpoint admin → 403 | Test RBAC |
| AC-021-15 | `Σ(transaction.amount) == wallet.balance` | Sanity query |
| AC-021-16 | 4 màn có đủ Loading/Empty/Error | Manual |
| AC-021-17 | 100% text có dấu tiếng Việt | Manual |

---

## Edge Cases & Error Handling

1. **Số dư giảm giữa lúc tạo request và lúc Admin duyệt** (khách rút xong đi trả đơn) → duyệt trả 422
   `INSUFFICIENT_CURRENT_BALANCE`. Admin phải từ chối hoặc chờ khách nạp thêm. **Không** tự huỷ request.
2. **Khách tạo nhiều request PENDING** → `available` trừ dần theo Σ PENDING (FR-033), không thể rút
   vượt tổng.
3. **Admin chuyển khoản thật rồi nhập sai `bankTxnRef`** → nếu ref đã dùng → 409. Nếu ref mới nhưng
   tiền đã chuyển → không có cơ chế đảo; phải ghi bút toán `ADJUSTMENT` thủ công (ngoài scope).
4. **Ví chưa tồn tại khi hoàn tiền** → `insertIfMissing` tạo trước, Manager không thấy lỗi (FR-031).
5. **Notification lỗi khi tạo request rút** → log warning, request vẫn 201 (FR-041).
6. **VNPay IPN tới trước khi Return URL** → ví đã cộng, Return URL chỉ hiển thị (HR-03).
7. **Customer bị SUSPENDED còn số dư** → spec này không chặn; hành vi hiện tại là vẫn rút được nếu
   JWT còn hạn. **Cần leader quyết** (xem Open Questions OQ-3).
8. **Rút toàn bộ số dư rồi đơn đang PENDING_PAYMENT** → đơn sẽ không trả được bằng ví, khách phải nạp
   lại hoặc dùng VNPay.
9. **`amount` có phần thập phân** (`500000.50`) → 422 `VALIDATION_ERROR` (FR-035).
10. **`fullName` rỗng khi tạo request** → `bank_account_holder` = `"CHUA CAP NHAT"`; Admin thấy giá trị
    này và có thể từ chối (FR-038).

---

## Test Cases

| ID | Loại | Mô tả | Kỳ vọng |
|----|------|-------|---------|
| TC-021-01 | Unit | `getOrCreateSummary` với customer chưa có ví | Tạo ví, 4 giá trị = 0 |
| TC-021-02 | Unit | `validateWithdrawalAmount(null)` | 422 |
| TC-021-03 | Unit | `validateWithdrawalAmount(500000.5)` | 422 nguyên đồng |
| TC-021-04 | Unit | `validateWithdrawalAmount(-1)` | 422 > 0 |
| TC-021-05 | Unit | `validateBankCode("XYZ")` | 422 không hỗ trợ |
| TC-021-06 | Unit | `validateBankAccountNumber("123")` | 422 8–15 chữ số |
| TC-021-07 | Unit | `maskAccount("1234567890")` | `******7890` |
| TC-021-08 | Unit | `normalizeAccountHolder(null)` | `CHUA CAP NHAT` |
| TC-021-09 | Unit | `validateBankTxnRef("abc")` | 422 (< 6 ký tự) |
| TC-021-10 | Unit | `validateReason("ngắn")` | 422 (< 10 ký tự) |
| TC-021-11 | Integration | Tạo request khi `amount == available` | 201 |
| TC-021-12 | Integration | Tạo request khi `amount == available + 1` | 409 |
| TC-021-13 | Integration | 2 PENDING, tổng = balance, tạo thêm | 409 |
| TC-021-14 | Integration | Admin process happy path | 200, balance giảm, transaction âm |
| TC-021-15 | Integration | Admin process lần 2 cùng ref | 200 replay, balance không giảm nữa |
| TC-021-16 | Integration | Admin process request đã REJECTED | 409 |
| TC-021-17 | Integration | Admin reject happy path | 200, balance không đổi |
| TC-021-18 | Integration | Admin reject lần 2 cùng reason | 200 replay |
| TC-021-19 | Integration | Trả cọc bằng ví đủ tiền | 200, đơn CONFIRMED |
| TC-021-20 | Integration | Trả cọc bằng ví thiếu tiền | 422, đơn giữ nguyên |
| TC-021-21 | Integration | Trả cọc đơn của người khác | 403 |
| TC-021-22 | Integration | Trả 70% khi `finalPaidAt != null` | 409 |
| TC-021-23 | Integration | Customer gọi endpoint admin | 403 |
| TC-021-24 | Integration | Manager gọi endpoint admin | 403 |
| TC-021-25 | Concurrency | 2 thread cùng tạo request rút hết số dư | Chỉ 1 thành công |
| TC-021-26 | Concurrency | 2 thread cùng process 1 request | 1 thành công, 1 nhận 409 |
| TC-021-27 | Integration | `Σ(transaction.amount) == balance` sau 10 thao tác hỗn hợp | Khớp |

---

## Deferred Scope

> Các hạng mục dưới đây **nằm ngoài phạm vi bản 1.0.0** và được hoãn có chủ ý sang bản sau, kèm lý do
> và hướng xử lý. Danh sách này là đầu vào cho backlog, không phải yêu cầu bắt buộc của bản này.

| ID | Hạng mục | Rủi ro nếu bỏ qua lâu | Hướng xử lý |
|----|----------|------------------------|-------------|
| DS-01 | **Customer huỷ yêu cầu rút PENDING** — trạng thái `CANCELLED` đã có trong CHECK constraint và cột `cancelled_at` đã có, nhưng bản này không đặc tả endpoint | Khách lỡ tay tạo yêu cầu phải chờ Admin từ chối; trong lúc đó tiền bị giữ chỗ khỏi `available` (FR-033) | `DELETE /api/customer/wallet/withdrawals/{id}` — schema đã sẵn sàng, không cần migration |
| DS-02 | **Idempotency thật cho `POST /withdrawals`** — nhận `idempotency_key` từ client qua header `X-Idempotency-Key` thay vì sinh trong service | Unique `(customer_id, idempotency_key)` không chặn được double-submit khi key sinh phía server; client retry mạng chập chờn sẽ tạo 2 yêu cầu rút | Nhận từ header; giữ nguyên schema |
| DS-03 | **Idempotency cho Admin `/process` và `/reject`** — header `X-Idempotency-Key` được khai báo ở contract nhưng bản này chưa dùng để chống replay | Header không có tác dụng → gây hiểu nhầm là đã có bảo vệ. Hiện chống double-process bằng guard trạng thái + unique index (FR-053, FR-057) nên rủi ro tiền thấp | Dùng header hoặc gỡ khỏi contract |
| DS-04 | **Hợp nhất hai entity map cùng bảng `transaction`** (`Transaction` 11 cột vs `WalletTransaction` 7 cột) | Cùng loại nợ kỹ thuật với cặp `ServiceOrder`/`Order` đã biết; hai view khác nhau lên cùng bảng dễ gây lệch dữ liệu khi thêm cột | Hợp nhất — thuộc nhóm known issues cần leader duyệt riêng |
| DS-05 | **Đồng bộ tên bảng audit trail với AC-13** — AC-13 quy định `wallet_transaction`, thiết kế này dùng `transaction` | Lệch tên so với Constitution; người đọc AC-13 sẽ tìm bảng không tồn tại | Amend AC-13 ghi rõ `wallet_transaction` là tên khái niệm (PATCH 6a) hoặc rename bảng |
| DS-06 | Validate lại `ck_customer_withdrawal_terminal_fields` (đang `NOT VALID`) | Không ảnh hưởng — bảng mới, không có dữ liệu cũ. Constraint vẫn áp cho mọi bản ghi mới | Chạy `VALIDATE CONSTRAINT` khi tiện |
| DS-07 | Bổ sung 3 màn `withdrawal-request`, `withdrawal-history`, `admin/customer-withdrawals` vào `SCREEN_INVENTORY.md` | Số màn hình báo cáo thiếu 3 màn (65 → 68) | Cập nhật inventory |
| DS-08 | **Guard trạng thái tài khoản khi rút tiền** — bản này không chặn Customer `SUSPENDED` tạo yêu cầu rút nếu JWT còn hạn | Tài khoản bị khoá vì nghi vấn gian lận vẫn rút được tiền ra trong tối đa 15 phút (thời hạn access token) | Thêm guard `status == ACTIVE`. Xem OQ-3 |

---

## Open Questions

| # | Câu hỏi | Block | Ưu tiên |
|---|---------|-------|---------|
| OQ-1 | **Leader duyệt ví Customer trở thành canonical?** Yêu cầu security review (điều kiện (d) của Spec #004 FR-036) + áp bản vá `CONTEXT_PATCH_PROPOSAL.md`. Từ chối → spec huỷ, endpoint ví trả 404/403 theo #004 | **Toàn bộ spec này** | **High** |
| OQ-2 | `RefundRecord` trong CONTEXT (xin STK qua chat + chuyển khoản thủ công) đã bị thay hoàn toàn bởi ví + `order_cancellation_refund`? Có xoá khỏi CONTEXT không? | Spec #022 | High |
| OQ-3 | Customer `SUSPENDED` có được rút tiền không? | DS-08 | Medium |
| OQ-4 | Có min/max cho số tiền rút của khách không? Bản này chỉ ràng buộc `> 0` (FR-035) | — | Low |
| OQ-5 | Có phí rút tiền không? | — | Low |
| OQ-6 | Ví có hết hạn/ngủ đông sau N tháng không dùng? | — | Low |

---

## Rollout Plan

> ⛔ **Điều kiện tiên quyết:** OQ-1 phải được leader duyệt **trước** bất kỳ bước nào bên dưới. Nếu bị
> từ chối, spec này huỷ và các endpoint ví trả 404/403 theo Spec #004 FR-032/FR-033.

**Giai đoạn 0 — Duyệt (chặn tất cả):**

1. Security review ví Customer — điều kiện (d) của Spec #004 FR-036.
2. Leader duyệt OQ-1 và OQ-2 (số phận `RefundRecord`).
3. Áp bản vá `CONTEXT_PATCH_PROPOSAL.md`: CONTEXT §2 Wallet + §2 Huỷ đơn + §2 Thuật ngữ + §3 RBAC;
   Spec #004 (Goals, Scope, FR-032..FR-036, Source-of-Truth, bump 2.0.0); Constitution AC-13 + HR-18
   (bump 1.5.0).

**Giai đoạn 1 — Ví cơ bản (Sprint 4):**

4. `V8` — `customer_wallet`. Không backfill: ví tạo lazy khi khách gọi `GET /api/customer/wallet`
   lần đầu (FR-002).
5. Nạp ví qua VNPay + luồng IPN cộng ví (FR-012..FR-018).
6. Trả cọc/70% từ ví (FR-019..FR-026).
7. `my-wallet.html` — số dư + nạp + lịch sử.

**Giai đoạn 2 — Rút tiền (Sprint 6):**

8. `V39` — `customer_withdrawal_request` + `total_withdrawn` + `related_customer_withdrawal_id`.
   Cột `total_withdrawn` thêm với `DEFAULT 0` nên an toàn với ví đã tồn tại.
9. Customer tạo yêu cầu rút + lịch sử (FR-032..FR-045).
10. Admin hàng đợi + duyệt/từ chối (FR-046..FR-065).
11. `withdrawal-request.html`, `withdrawal-history.html`, `admin/customer-withdrawals.html`.

**Rủi ro cần theo dõi khi rollout:**

- Ví giữ tiền thật của khách → mọi lỗi ở đây là lỗi tiền, không phải lỗi UI. Ưu tiên test concurrency
  (TC-021-25, TC-021-26) và sanity check `Σ(transaction.amount) == balance` (AC-021-15).
- DS-02 (idempotency): phải xử lý trước khi mở cho người dùng thật, vì double-submit tạo hai yêu cầu
  rút cùng số tiền.
- DS-08 (guard SUSPENDED): xử lý trước khi có luồng khoá tài khoản vì nghi vấn gian lận.

---

## Constitution Compliance

=== CONSTITUTION CHECK REPORT ===  
Feature  : #21 Customer Wallet & Withdrawal  
Artifact : spec  
Date     : 2026-06-04

**LAYER 1 — HARD RULES**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| HR-01 Secrets không commit | PASS | VNPay/DB qua env |
| HR-02 BCrypt | N/A | |
| HR-03 IPN là nguồn cập nhật duy nhất | PASS | FR-016 |
| HR-04 Verify HMAC trước xử lý IPN | PASS | FR-014 |
| HR-05 Transition sai → 409 | PASS | FR-020, FR-054 |
| HR-06/07 DamageReport | N/A | Spec #010/#023 |
| HR-08 Driver concurrency | N/A | |
| HR-09 IPN timeout | N/A | |
| HR-10 Trái quyền → 403 | PASS | FR-004, FR-025, FR-050, FR-066/067 |
| HR-11 Email không rollback TX | PASS (tinh thần) | FR-041 áp dụng cho notification |
| HR-12 Driver onboarding | N/A | |
| HR-13 Audit log state change | PASS | FR-040, FR-059, FR-065 |
| HR-14 RefundRecord chỉ khi COMPANY huỷ | ⚠️ **EXCEPTION** | Không có bảng `RefundRecord`; hoàn tiền đi qua ví + `order_cancellation_refund` (V41). HR-14 v1.4.0 đã thêm ngoại lệ hoàn cọc nhưng vẫn giả định RefundRecord tồn tại cho COMPANY-cancel. **Cần làm rõ — OQ-2** |
| HR-15 Idempotency IPN | PASS | FR-015 |
| HR-16 Rate limit login | N/A | |
| HR-17 Public vs Authenticated | PASS | Không endpoint public nào |
| HR-18 Wallet không âm | PASS | MI-001, FR-003, FR-021, FR-056 |
| HR-19 Brand identity | PASS | Forest green + amber + Be Vietnam Pro |
| HR-20 Tiếng Việt có dấu | ⚠️ **PARTIAL** | Message user-facing ở `WalletService` có dấu; nhiều message ở `AdminCustomerWithdrawalService` **không dấu** ("Khong tim thay yeu cau rut tien.") — vi phạm HR-20, cần fix |
| HR-21 Tránh reserved words | PASS | `customer_wallet`, `customer_withdrawal_request`, `transaction` |

**Layer 1 Result:** 2 vấn đề cần xử lý — HR-14 (làm rõ), HR-20 (fix message không dấu).

**LAYER 2 — ARCHITECTURAL CONSTRAINTS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| AC-01 Stack | PASS | Spring Boot + Vanilla JS |
| AC-02 REST thuần | PASS | |
| AC-03 JWT | PASS | |
| AC-04 Không nối chuỗi SQL | PASS | JPA + named params |
| AC-05 Chat | N/A | |
| AC-06 Maps fallback | N/A | |
| AC-07 Timezone | PASS | TIMESTAMPTZ UTC |
| AC-08 BigDecimal scale=0 | PASS | FR-072 |
| AC-09 Soft delete | ⚠️ **EXCEPTION** | `customer_wallet` và `customer_withdrawal_request` **không có `deleted_at`**. Có thể chấp nhận (ví không xoá, request là audit) nhưng lệch AC-09 |
| AC-10 Cloudinary | N/A | |
| AC-11 CORS | PASS | |
| AC-12 Flyway | PASS | V8, V39 |
| AC-13 Money audit trail | ⚠️ **EXCEPTION** | Cấu trúc đúng tinh thần (UPDATE ví + INSERT sổ cái cùng TX, có `balance_after`) nhưng **bảng tên `transaction`** thay vì `wallet_transaction`, và type enum khác danh sách AC-13. Xem DS-05 |
| AC-14 VARCHAR + CHECK | PASS | `customer_withdrawal_request.status` |
| AC-15 Pagination | PASS | Default 20, max 100 |
| AC-16 Empty/Loading/Error | PASS | 4 màn |

**Layer 2 Result:** 2 exception (AC-09, AC-13) — cần leader ghi nhận.

**LAYER 3 — ENGINEERING STANDARDS**

| Rule | Kết quả | Ghi chú |
|------|---------|---------|
| ES-01 Naming | PASS | |
| ES-02 REST noun-based | PASS | Trừ alias `/approve` (verb) — chấp nhận vì backward-compat FE |
| ES-03 Bean Validation + 422 | PASS | `@Valid` + validation thủ công |
| ES-04 Error format | PARTIAL | Service dùng `"CODE|Message"`; map qua advice chung (Spec #018) |
| ES-05 Test coverage ≥70% CORE | ⚠️ **CHƯA VERIFY** | Cần chạy coverage cho `WalletService`, `AdminCustomerWithdrawalService`, `WalletOrderPaymentService` |
| ES-06/07 Commits | PASS | |
| ES-08 Multi-AI | PASS | |

=== SUMMARY ===  
Layer 1 : 19/21 PASS, 2 cần xử lý (HR-14 làm rõ, HR-20 fix message)  
Layer 2 : 14/16 PASS, 2 exception documented (AC-09, AC-13)  
Layer 3 : 6/8 PASS, ES-04 partial, ES-05 chưa verify  
Status  : **BLOCKED — chờ leader quyết OQ-1** (ví Customer có được hợp thức hoá không).
Nếu OQ-1 = có → áp bản vá CONTEXT/Spec #004, spec này chuyển `Status: Approved`.
================================
