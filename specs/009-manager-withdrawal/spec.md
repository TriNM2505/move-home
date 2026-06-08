# Feature Specification: Admin Withdrawal Processing

**Feature Branch:** `009-manager-withdrawal`  
**Feature Number:** #9 of 30 — CORE (driver financial outflow gatekeeping)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §Withdrawal, §Wallet & Commission, §RBAC, feature #19  
**Constitution reference:** v1.3.0 — HR-05, HR-10, HR-11, HR-13, HR-18, HR-19,
HR-20, HR-21, AC-07, AC-08, AC-09, AC-13, AC-14, AC-15, AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — legacy Manager screens 5.5-5.7 and Admin screen 6.5  
**Related specs:** Spec #001 Auth/RBAC; Spec #007 Driver Financial; Spec #028 Admin Dashboard

---

## Goals

Feature này cung cấp quy trình kiểm soát tiền ra khỏi hệ thống khi Driver yêu cầu rút thu nhập.
Theo `CONTEXT.md v2.0`, Admin là vai trò duy nhất có quyền xử lý withdrawal. Admin xem queue
`PENDING`, kiểm tra Driver, số dư hiện tại, các yêu cầu đang chờ và snapshot tài khoản ngân hàng;
sau đó chuyển khoản ngoài hệ thống và xác nhận đã chuyển, hoặc từ chối với lý do rõ ràng.

Luồng canonical không có bước `APPROVED` trung gian. Request `PENDING` không trừ và không hold
wallet balance; hệ thống chỉ dùng tổng pending để tính `available_to_withdraw`. Sau khi Admin đã
chuyển khoản thật, hành động mark processed phải lock request và ví, kiểm tra lại số dư, trừ ví,
append một transaction `WITHDRAWAL`, chuyển status sang `PROCESSED` và ghi audit trong cùng DB
transaction. Reject hoặc Driver cancel không refund vì tiền chưa từng bị trừ.

Mục tiêu là ngăn double debit, xử lý an toàn race giữa Admin và Driver, giữ bằng chứng tài chính
đầy đủ theo HR-13 và bảo vệ thông tin ngân hàng. UI phải dùng Move_home forest green
`#1B4D3E`, amber `#F5A623`, Be Vietnam Pro, tiếng Việt có dấu và đủ Loading/Empty/Error states.
SLA mục tiêu là Admin xử lý request trong một ngày làm việc; mọi external transfer phải có mã
giao dịch để reconciliation và audit.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec #001 → Spec #007 →
> spec này → `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Processor role | Chỉ `ADMIN` | `MANAGER` nhận HTTP 403 |
| Canonical states | `PENDING|PROCESSED|REJECTED|CANCELLED` | Không tạo `APPROVED|COMPLETED` |
| Transfer model | Admin chuyển khoản ngoài hệ thống trước | Không có approve-only action |
| Wallet mutation | Chỉ `PENDING → PROCESSED` mới debit | Request/reject/cancel không đổi ví |
| Pending reservation | Tính `balance - SUM(PENDING)` | Không tạo hold/refund transactions |
| Reject | `PENDING → REJECTED` với reason | Không refund vì chưa debit |
| Driver cancel | `PENDING → CANCELLED` | Race-safe với Admin process |
| Money history | Append-only `transaction` + `audit_log` | Không sửa/xóa transaction |
| Bank data | Full account chỉ Admin detail sau decrypt boundary | Queue/history luôn masked |
| UI ownership | Legacy Manager stubs phải migrate sang Admin | Không cấp quyền tài chính cho Manager |

Prompt cũ, inventory và Manager stubs mô tả `APPROVED`, `COMPLETED`, balance hold/refund hoặc
Manager authority là legacy. Implementation SHALL theo bảng resolution này.

---

## Scope Summary

**In scope:**

1. `GET /api/admin/withdrawals/pending` — FIFO queue `PENDING`.
2. `GET /api/admin/withdrawals/{withdrawalId}` — processor-safe detail.
3. `POST /api/admin/withdrawals/{withdrawalId}/process` — confirm external transfer.
4. `POST /api/admin/withdrawals/{withdrawalId}/reject` — reject với reason.
5. `GET /api/admin/withdrawals/history` — terminal history.
6. `GET /api/admin/withdrawals/{withdrawalId}/timeline` — immutable audit timeline.
7. Wallet debit và transaction append atomic khi process.
8. Concurrency lock, idempotency và unique transaction guard.
9. Audit/email cho process và reject.
10. Bank-account privacy, masking và short-lived reveal.
11. Migration legacy statuses/UI labels.
12. Admin queue/detail/history UI contracts.

**Out of scope:**

1. Tạo/cancel request phía Driver — Spec #007.
2. Automated bank API hoặc webhook.
3. Trạng thái `APPROVED` chờ chuyển.
4. Bulk process/reject.
5. Anti-fraud AI.
6. Deposit refund khi Driver nghỉ.
7. Currency conversion; chỉ VND.
8. Manager xử lý withdrawal.

---

## User Stories

**P1 (CORE):**

**US1:** Là Admin, tôi xem queue withdrawal `PENDING`, tổng số tiền và request chờ lâu nhất để
ưu tiên xử lý theo SLA.

**US2:** Là Admin, tôi mở detail để kiểm tra amount, Driver, số dư hiện tại, pending total,
snapshot ngân hàng và timeline trước khi chuyển khoản.

**US3:** Là Admin, sau khi chuyển khoản ngoài hệ thống, tôi nhập mã giao dịch và mark request
`PROCESSED` để hệ thống trừ ví đúng một lần.

**US4:** Là Admin, tôi reject request với lý do tiếng Việt rõ ràng khi thông tin không hợp lệ
hoặc số dư không còn đủ.

**US5:** Là Admin, tôi xem history có filter/status/date để audit các request đã xử lý.

**US6:** Là Admin, tôi thấy warning khi số dư, tài khoản ngân hàng hoặc lịch sử request có dấu
hiệu cần kiểm tra thủ công.

**P2:**

**US7:** Là Admin, tôi nhận cảnh báo queue quá SLA hoặc reconciliation mismatch.

**US8:** Là Admin, tôi export lịch sử đã lọc cho reconciliation — defer phase sau.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Pending Withdrawals List (FR-001..FR-007)

**FR-001**
WHEN authenticated Admin gọi
`GET /api/admin/withdrawals/pending?page=0&size=20&age=ALL`, THE system SHALL trả HTTP 200 với
Spring Page chỉ gồm `withdrawal_request.status='PENDING'`, sort
`requested_at ASC, id ASC`.

**FR-002**
WHEN queue item được trả, THE system SHALL gồm:

```json
{
  "id": "6752feee-5c2b-4bd3-8f75-2c7f2737ace7",
  "driver_id": "9ac469f5-47d8-441f-99c0-b1c6941c8fb3",
  "driver_name": "Nguyễn Văn Hùng",
  "driver_phone": "+84912345678",
  "amount": 4500000,
  "bank_code": "VCB",
  "bank_name": "Vietcombank",
  "bank_account_masked": "******7890",
  "requested_at": "2026-06-05T01:45:00Z",
  "days_waiting": 1,
  "wallet_balance": 8200000,
  "process_ready": true,
  "blocking_reasons": []
}
```

**FR-003**
WHEN queue được tính, THE system SHALL trả KPIs `pending_count`, `pending_amount`,
`oldest_waiting_days`, `over_sla_count`; money fields SHALL là integer VND scale=0 và
`pending_amount` SHALL chỉ sum status `PENDING`.

**FR-004**
WHEN query có `age=TODAY`, THE system SHALL lọc request trong ngày Asia/Ho_Chi_Minh; WHEN
`age=OVER_SLA`, SHALL lọc request cũ hơn một ngày làm việc theo policy hiện hành; WHERE age
không thuộc `ALL|TODAY|OVER_SLA`, SHALL trả HTTP 422 `INVALID_FILTER`.

**FR-005**
WHEN queue item được enrich, THE system SHALL tính `process_ready` từ actual wallet balance,
Driver account state và bank snapshot validity; SHALL không tự process hoặc reject dựa trên
warning.

**FR-006**
WHILE queue đang tải, THE frontend SHALL hiển thị skeleton; WHERE content rỗng, SHALL hiển thị
“Không có yêu cầu rút tiền nào đang chờ xử lý”; WHERE API lỗi, SHALL hiển thị
“Không thể tải yêu cầu rút tiền” và button “Thử lại”.

**FR-007**
WHEN `totalElements > size`, THE frontend SHALL dùng server-side pagination với page numbers,
ellipsis, Previous/Next, selector `10|20|50|100` và info text; backend SHALL cap size 100 theo
AC-15.

---

### Nhóm 2 — Withdrawal Detail & Verification (FR-008..FR-014)

**FR-008**
WHEN Admin gọi `GET /api/admin/withdrawals/{withdrawalId}`, THE system SHALL trả HTTP 200 cho
request tồn tại; WHERE UUID sai trả 400, WHERE không tồn tại trả 404 `WITHDRAWAL_NOT_FOUND`.

**FR-009**
WHEN detail được trả, THE response SHALL có:

```json
{
  "withdrawal": {
    "id": "6752feee-5c2b-4bd3-8f75-2c7f2737ace7",
    "amount": 4500000,
    "bank_code": "VCB",
    "bank_name": "Vietcombank",
    "bank_account_masked": "******7890",
    "bank_account_holder": "NGUYEN VAN HUNG",
    "note": null,
    "status": "PENDING",
    "requested_at": "2026-06-05T01:45:00Z",
    "processed_at": null,
    "rejection_reason": null,
    "bank_txn_ref_masked": null
  },
  "driver": {},
  "verification": {},
  "timeline": []
}
```

**FR-010**
WHEN detail xây Driver section, THE system SHALL trả `id`, `full_name`, `email`, `phone`,
`account_status`, `wallet_balance`, `pending_withdrawal_total`, `available_to_withdraw`,
`total_earnings`, `total_withdrawn`, `completed_orders_count`; SHALL không expose password/token.

**FR-011**
WHEN detail xây verification section, THE system SHALL kiểm tra request amount so với current
wallet balance, snapshot bank format, account holder/name comparison, duplicate bank transaction
reference và count request trong 24 giờ; mỗi check SHALL trả `PASS|WARNING|FAIL` cùng label.

**FR-012**
WHEN Admin yêu cầu reveal full account qua
`POST /api/admin/withdrawals/{id}/reveal-bank-account`, THE system SHALL re-authenticate Admin
hoặc yêu cầu recent auth, decrypt server-side và trả plaintext với TTL UI tối đa 60 giây; action
SHALL audit `WITHDRAWAL_BANK_ACCOUNT_REVEALED`.

**FR-013**
WHEN detail status là `PENDING`, THE frontend SHALL hiển thị “Xác nhận đã chuyển khoản” và
“Từ chối”; WHEN status là `PROCESSED|REJECTED|CANCELLED`, SHALL chỉ hiển thị read-only result.

**FR-014**
WHERE detail section lỗi, THE frontend SHALL hiển thị section-level error và disable process;
SHALL không suy đoán full account, wallet balance hoặc eligibility từ stale local data.

---

### Nhóm 3 — Process External Transfer (FR-015..FR-022)

**FR-015**
WHEN Admin đã chuyển khoản ngoài hệ thống và click “Xác nhận đã chuyển khoản”, THE frontend SHALL
mở confirm modal yêu cầu nhập `bank_txn_ref`, xác nhận amount, bank, last4 và warning rằng thao
tác sẽ trừ ví Driver.

**FR-016**
WHEN Admin gọi `POST /api/admin/withdrawals/{id}/process`, request SHALL có
`X-Idempotency-Key` UUID và body:

```json
{
  "bank_txn_ref": "VCB-20260609-84930211",
  "processing_note": "Đã đối chiếu giao dịch trên Internet Banking."
}
```

`bank_txn_ref` SHALL required, trim length `6..100`, regex `^[A-Za-z0-9._/-]+$`;
`processing_note` optional, max 500 ký tự.

**FR-017**
WHEN process request bắt đầu, THE backend SHALL mở transaction và lock theo thứ tự
`withdrawal_request FOR UPDATE` rồi `driver_wallet FOR UPDATE`; SHALL verify status `PENDING`,
actor role `ADMIN`, Driver id khác actor id và bank reference chưa tồn tại.

**FR-018**
WHEN locks đã giữ, THE system SHALL re-check `driver_wallet.balance >= withdrawal.amount`;
IF không đủ, THEN SHALL rollback và trả HTTP 422 `INSUFFICIENT_CURRENT_BALANCE`, không ghi
transaction hoặc status.

**FR-019**
WHEN validation thành công, THE system SHALL atomically:

```sql
UPDATE driver_wallet
SET balance = balance - :amount,
    total_withdrawn = total_withdrawn + :amount,
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND balance >= :amount;

INSERT INTO transaction
    (user_id, type, amount, related_withdrawal_id, description, balance_after)
VALUES
    (:driver_id, 'WITHDRAWAL', -:amount, :withdrawal_id,
     'Rút tiền về tài khoản ngân hàng', :balance_after);

UPDATE withdrawal_request
SET status = 'PROCESSED',
    processed_by = :admin_id,
    bank_txn_ref = :bank_txn_ref,
    processed_at = NOW(),
    version = version + 1
WHERE id = :withdrawal_id
  AND status = 'PENDING';
```

**FR-020**
WHEN process transaction chạy, THE system SHALL insert append-only audit
`WITHDRAWAL_PROCESSED` trong cùng transaction với Admin id, Driver id, withdrawal id, amount,
bank code, account last4, reference, previous/new state và request id; audit failure SHALL
rollback toàn bộ debit.

**FR-021**
WHEN process commit, THE system SHALL trả HTTP 200:

```json
{
  "withdrawal_id": "6752feee-5c2b-4bd3-8f75-2c7f2737ace7",
  "status": "PROCESSED",
  "amount": 4500000,
  "balance_after": 3700000,
  "message": "Đã ghi nhận chuyển khoản thành công"
}
```

và enqueue email tiếng Việt; email failure SHALL không rollback theo HR-11.

**FR-022**
WHERE same idempotency key, actor và payload được retry sau success, THE system SHALL replay
response cũ; WHERE request đã terminal hoặc reference trùng request khác, SHALL trả HTTP 409 và
SHALL không double debit.

---

### Nhóm 4 — Reject Withdrawal (FR-023..FR-029)

**FR-023**
WHEN Admin click “Từ chối”, THE frontend SHALL mở modal với textarea reason, counter `0/500`,
helper text Driver sẽ đọc nội dung và confirm disabled khi trimmed reason dưới 10 ký tự.

**FR-024**
WHEN Admin gọi `POST /api/admin/withdrawals/{id}/reject`, request SHALL có idempotency key và
body `{"reason":"Thông tin chủ tài khoản ngân hàng không khớp hồ sơ tài xế."}`; reason SHALL
required, length `10..500`, có ít nhất một chữ cái và là thông điệp user-facing.

**FR-025**
WHEN reject bắt đầu, THE system SHALL lock `withdrawal_request FOR UPDATE`, verify actor
`ADMIN` và current status `PENDING`; WHERE request đã `PROCESSED|REJECTED|CANCELLED`, SHALL trả
HTTP 409 `INVALID_WITHDRAWAL_TRANSITION`.

**FR-026**
WHEN reject validation thành công, THE system SHALL atomically update:

```sql
UPDATE withdrawal_request
SET status = 'REJECTED',
    rejection_reason = :reason,
    processed_by = :admin_id,
    processed_at = NOW(),
    version = version + 1
WHERE id = :withdrawal_id
  AND status = 'PENDING';
```

và insert audit `WITHDRAWAL_REJECTED`.

**FR-027**
WHEN reject commit, THE system SHALL NOT update `driver_wallet`, SHALL NOT insert
`WITHDRAWAL_REFUND` và SHALL NOT insert money transaction vì pending request chưa trừ hoặc hold
balance.

**FR-028**
WHEN reject commit, THE system SHALL trả HTTP 200 với status `REJECTED`, message
“Đã từ chối yêu cầu rút tiền” và enqueue email chứa sanitized reason; internal Admin note SHALL
không gửi cho Driver.

**FR-029**
WHERE reason thiếu hoặc invalid, THE system SHALL trả HTTP 422 `INVALID_REJECTION_REASON`;
WHERE audit insert fail, SHALL rollback rejection; WHERE email fail sau commit, SHALL retry
async mà không rollback.

---

### Nhóm 5 — Timeline, History & Reconciliation (FR-030..FR-036)

**FR-030**
WHEN Admin gọi `GET /api/admin/withdrawals/{id}/timeline`, THE system SHALL derive events từ
append-only audit, sort timestamp/id ascending và map
`WITHDRAWAL_REQUESTED|PROCESSED|REJECTED|CANCELLED` sang nhãn tiếng Việt.

**FR-031**
WHEN Admin gọi
`GET /api/admin/withdrawals/history?page=0&size=20&status=ALL&driverId=&from=&to=`, THE system
SHALL filter `ALL|PROCESSED|REJECTED|CANCELLED`, optional Driver/date range và sort
`processed_at DESC NULLS LAST, id DESC`.

**FR-032**
WHEN history item được trả, THE system SHALL gồm id, Driver name, amount, bank name, masked
account, status, requested/processed timestamps, processor label và masked bank reference;
SHALL không trả full account.

**FR-033**
WHERE date range invalid, lớn hơn 366 ngày hoặc Driver UUID sai, THE system SHALL trả HTTP 422
với field error; WHERE page size trên 100, SHALL cap 100.

**FR-034**
WHILE history đang tải/rỗng/lỗi, THE frontend SHALL implement Loading/Empty/Error states; empty
message SHALL là “Không có lịch sử rút tiền”; pagination SHALL theo AC-15.

**FR-035**
WHEN daily reconciliation chạy, THE system SHALL verify mỗi `PROCESSED` withdrawal có đúng một
negative `WITHDRAWAL` transaction cùng amount/Driver và wallet totals không âm; mismatch SHALL
emit alert, SHALL không auto-edit money.

**FR-036**
WHEN Admin mở processed detail, THE system SHALL hiển thị reconciliation status
`MATCHED|MISMATCH|PENDING_CHECK`; WHERE mismatch, SHALL disable destructive remediation và hướng
dẫn xử lý qua runbook/audit.

---

### Nhóm 6 — Privacy, Notifications & UI Migration (FR-037..FR-041)

**FR-037**
WHEN queue/history trả bank account, THE system SHALL chỉ trả last4; WHEN full account được
reveal, SHALL không cache frontend, không log và tự ẩn sau tối đa 60 giây.

**FR-038**
WHEN process/reject/cancel event commit, THE system SHALL enqueue notification/email tiếng Việt
với amount format VND, status label và support channel; SHALL không gửi full account hoặc
internal note.

**FR-039**
WHEN triển khai spec này, legacy screens
`manager/withdrawal-pending.html`, `manager/withdrawal-detail.html`,
`manager/withdrawal-history.html` SHALL được migrate/redirect sang Admin routes; Manager nav
SHALL không hiển thị withdrawal processing.

**FR-040**
WHEN UI render status, THE frontend SHALL map `PENDING=Đang chờ xử lý`,
`PROCESSED=Đã chuyển khoản`, `REJECTED=Bị từ chối`, `CANCELLED=Đã hủy`; SHALL không hiển thị
legacy `APPROVED|COMPLETED`.

**FR-041**
WHEN frontend render process action, THE system SHALL dùng Move_home brand tokens, confirm modal,
Vietnamese diacritics và accessible labels; semantic danger chỉ dùng cho reject, không inline
brand colors.

---

### Nhóm 7 — RBAC, Concurrency & Validation (FR-042..FR-044)

**FR-042**
WHERE caller không có JWT hợp lệ, mọi endpoint SHALL trả HTTP 401; WHERE caller role không phải
`ADMIN`, bao gồm `MANAGER`, `DRIVER`, `CUSTOMER`, SHALL trả HTTP 403 theo CONTEXT RBAC và HR-10.

**FR-043**
WHEN Driver cancel và Admin process/reject cùng lúc, THE system SHALL serialize bằng lock trên
withdrawal row; đúng một terminal transition commit, request thua nhận HTTP 409 và không có
double money effect.

**FR-044**
WHEN bất kỳ money operation chạy, THE system SHALL dùng `BigDecimal`/`NUMERIC(15,0)`, transaction
atomic, guarded row counts, append-only audit và common error format; WHERE invariant fail,
SHALL rollback và emit security/financial alert.

---

## Non-Functional Requirements

**NFR-001 — Queue performance**  
Pending/history page size 20 SHALL hoàn tất dưới 500 ms ở p90 với 10.000 withdrawals.

**NFR-002 — Detail performance**  
Detail/timeline SHALL hoàn tất dưới 1 giây ở p90, không N+1 query.

**NFR-003 — Money transaction**  
Process/reject SHALL hoàn tất dưới 2 giây ở p90, không chờ SMTP; process transaction giữ lock
ngắn hơn 1 giây ở p90.

**NFR-004 — Atomicity**  
Wallet debit, transaction append, withdrawal transition và audit SHALL all-or-nothing.

**NFR-005 — Idempotency**  
Retry process/reject SHALL không tạo duplicate debit, transaction, audit hoặc email outbox.

**NFR-006 — Privacy**  
Bank account full value chỉ tồn tại ngắn hạn sau authorized decrypt; queue/history/log luôn masked.

**NFR-007 — Audit durability**  
Mọi terminal transition và bank reveal SHALL có append-only audit UTC.

**NFR-008 — UX quality**  
Admin screens SHALL responsive, brand-consistent, tiếng Việt có dấu và đủ Loading/Empty/Error.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/admin/withdrawals/pending` | `page,size,age` | 200 Page + KPIs | Admin |
| GET | `/api/admin/withdrawals/history` | `page,size,status,driverId,from,to` | 200 Page | Admin |
| GET | `/api/admin/withdrawals/{id}` | Path UUID | 200 detail | Admin |
| GET | `/api/admin/withdrawals/{id}/timeline` | Path UUID | 200 events | Admin |
| POST | `/api/admin/withdrawals/{id}/process` | `{bank_txn_ref,processing_note}` | 200 processed | Admin |
| POST | `/api/admin/withdrawals/{id}/reject` | `{reason}` | 200 rejected | Admin |

Bank reveal là security sub-action của detail và không tính vào sáu workflow endpoints chính.

### Common Error Format

```json
{
  "timestamp": "2026-06-09T03:20:00Z",
  "status": 409,
  "code": "INVALID_WITHDRAWAL_TRANSITION",
  "message": "Yêu cầu rút tiền đã được xử lý",
  "path": "/api/admin/withdrawals/6752feee-5c2b-4bd3-8f75-2c7f2737ace7/process",
  "request_id": "01JY...",
  "field_errors": [],
  "details": {"current_status":"CANCELLED"}
}
```

---

## Data Model

Spec này reuse `withdrawal_request`, `driver_wallet`, append-only `transaction`, `audit_log` và
`driver_bank_account` từ Spec #007.

### Canonical `withdrawal_request`

```sql
CREATE TABLE withdrawal_request (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    driver_id             UUID          NOT NULL REFERENCES app_user(id),
    amount                NUMERIC(15,0) NOT NULL CHECK (amount >= 100000),
    bank_code             VARCHAR(20)   NOT NULL,
    bank_name_snapshot    VARCHAR(100)  NOT NULL,
    bank_account_number   VARCHAR(20)   NOT NULL,
    bank_account_holder   VARCHAR(100)  NOT NULL,
    note                  VARCHAR(500),
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED', 'REJECTED', 'CANCELLED')),
    rejection_reason      VARCHAR(500),
    processed_by          UUID          REFERENCES app_user(id),
    bank_txn_ref          VARCHAR(100),
    requested_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    processed_at          TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    idempotency_key       UUID          NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_withdrawal_request PRIMARY KEY (id),
    CONSTRAINT uq_withdrawal_idempotency UNIQUE (driver_id, idempotency_key)
);
```

### Required Constraints & Indexes

```sql
ALTER TABLE withdrawal_request
    ADD CONSTRAINT ck_withdrawal_terminal_fields
    CHECK (
        (status = 'PENDING'
            AND processed_by IS NULL
            AND processed_at IS NULL
            AND bank_txn_ref IS NULL
            AND rejection_reason IS NULL)
        OR
        (status = 'PROCESSED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND bank_txn_ref IS NOT NULL
            AND rejection_reason IS NULL)
        OR
        (status = 'REJECTED'
            AND processed_by IS NOT NULL
            AND processed_at IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND bank_txn_ref IS NULL)
        OR
        (status = 'CANCELLED'
            AND cancelled_at IS NOT NULL
            AND bank_txn_ref IS NULL)
    );

CREATE UNIQUE INDEX uq_withdrawal_bank_txn_ref
    ON withdrawal_request (bank_txn_ref)
    WHERE bank_txn_ref IS NOT NULL;

CREATE INDEX idx_withdrawal_pending_fifo
    ON withdrawal_request (requested_at ASC, id ASC)
    WHERE status = 'PENDING';

CREATE INDEX idx_withdrawal_history_processed
    ON withdrawal_request (processed_at DESC, id DESC)
    WHERE status IN ('PROCESSED', 'REJECTED', 'CANCELLED');
```

### Unique Money Guard

```sql
CREATE UNIQUE INDEX uq_transaction_withdrawal
    ON transaction (related_withdrawal_id)
    WHERE type = 'WITHDRAWAL';
```

Mọi status dùng `VARCHAR + CHECK`, không PostgreSQL ENUM theo AC-14.

---

## Money Invariants

1. `driver_wallet.balance >= 0` luôn đúng.
2. `PENDING` không mutate wallet và không có `WITHDRAWAL` transaction.
3. `REJECTED`/`CANCELLED` không mutate wallet và không có refund transaction.
4. `PROCESSED` có đúng một negative `WITHDRAWAL` transaction.
5. Transaction amount bằng `-withdrawal_request.amount`.
6. Transaction `balance_after` bằng wallet balance ngay sau debit.
7. `driver_wallet.total_withdrawn` tăng đúng amount khi processed.
8. Một `bank_txn_ref` chỉ gắn một withdrawal.
9. Một withdrawal chỉ có một terminal transition.
10. Audit failure rollback money operation.

---

## Transaction Boundaries

### Process Transaction

```sql
BEGIN;

SELECT id, driver_id, amount, status
FROM withdrawal_request
WHERE id = :withdrawal_id
FOR UPDATE;

SELECT driver_id, balance, total_withdrawn
FROM driver_wallet
WHERE driver_id = :driver_id
FOR UPDATE;

UPDATE driver_wallet
SET balance = balance - :amount,
    total_withdrawn = total_withdrawn + :amount,
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND balance >= :amount;

INSERT INTO transaction
    (user_id, type, amount, related_withdrawal_id, description, balance_after)
VALUES
    (:driver_id, 'WITHDRAWAL', -:amount, :withdrawal_id,
     'Rút tiền về tài khoản ngân hàng', :balance_after);

UPDATE withdrawal_request
SET status = 'PROCESSED',
    processed_by = :admin_id,
    bank_txn_ref = :bank_txn_ref,
    processed_at = NOW(),
    version = version + 1
WHERE id = :withdrawal_id
  AND status = 'PENDING';

INSERT INTO audit_log (...);

COMMIT;
```

Every guarded update/insert SHALL affect exactly one row. Otherwise rollback.

### Reject Transaction

```sql
BEGIN;

SELECT id, driver_id, amount, status
FROM withdrawal_request
WHERE id = :withdrawal_id
FOR UPDATE;

UPDATE withdrawal_request
SET status = 'REJECTED',
    rejection_reason = :reason,
    processed_by = :admin_id,
    processed_at = NOW(),
    version = version + 1
WHERE id = :withdrawal_id
  AND status = 'PENDING';

INSERT INTO audit_log (...);

COMMIT;
```

Reject SHALL not lock/update wallet because there is no money effect.

### Lock Order

Lock order SHALL luôn là:

```text
withdrawal_request → driver_wallet → transaction → audit_log
```

Driver cancel từ Spec #007 cũng lock withdrawal first. Same order ngăn deadlock trong races.

---

## State Machine

```text
PENDING
  |-- Driver cancel trước xử lý ----------------> CANCELLED
  |-- Admin chuyển khoản ngoài + process ------> PROCESSED
  |-- Admin reject + reason --------------------> REJECTED

Terminal: PROCESSED, REJECTED, CANCELLED
```

Rules:

1. Spec này chỉ thực hiện `PENDING → PROCESSED|REJECTED`.
2. Không có `APPROVED` hoặc `COMPLETED`.
3. Chỉ `PENDING → PROCESSED` có money effect.
4. Invalid transition trả HTTP 409 theo HR-05.
5. Terminal records read-only.

---

## External Transfer Runbook

Admin SHALL thực hiện theo thứ tự:

1. Mở pending detail và verify Driver/request/bank.
2. Reveal account qua recent-auth action.
3. Chuyển khoản ngoài hệ thống đúng amount.
4. Lấy bank transaction reference.
5. Gọi process endpoint với idempotency key.
6. Xác nhận response `PROCESSED` và reconciliation `MATCHED`.

Nếu external transfer thành công nhưng API/DB lỗi, Admin SHALL retry cùng idempotency key và
bank reference. Admin SHALL không chuyển khoản lần hai. Unique bank reference và unique
withdrawal transaction bảo vệ retry.

Nếu transfer chưa thực hiện hoặc fail, Admin SHALL giữ `PENDING` hoặc reject với reason. Không
được mark processed trước transfer.

---

## Verification Checklist

| Check | Result | Blocking |
|-------|--------|----------|
| Request còn PENDING | PASS/FAIL | Yes |
| Driver tồn tại | PASS/FAIL | Yes |
| Wallet balance đủ tại thời điểm process | PASS/FAIL | Yes |
| Account number format hợp lệ | PASS/WARNING | Manual |
| Account holder khớp Driver | PASS/WARNING | Manual |
| Bank code trong allowlist | PASS/FAIL | Yes |
| Không trùng bank reference | PASS/FAIL | Yes |
| Không có withdrawal transaction trước đó | PASS/FAIL | Yes |
| Request frequency 24h | PASS/WARNING | Manual |
| Driver status suspended | PASS/WARNING | Policy/manual |

Warnings không tự block trừ khi policy được chốt. Admin phải xác nhận thủ công.

---

## Error Matrix

| Scenario | HTTP | Code | Message |
|----------|------|------|---------|
| Không có JWT | 401 | `AUTHENTICATION_REQUIRED` | Phiên đăng nhập không hợp lệ |
| Không phải Admin | 403 | `FORBIDDEN` | Bạn không có quyền xử lý rút tiền |
| Request không tồn tại | 404 | `WITHDRAWAL_NOT_FOUND` | Không tìm thấy yêu cầu rút tiền |
| Status terminal | 409 | `INVALID_WITHDRAWAL_TRANSITION` | Yêu cầu đã được xử lý |
| Duplicate bank reference | 409 | `DUPLICATE_BANK_TXN_REF` | Mã giao dịch đã được sử dụng |
| Wallet không đủ | 422 | `INSUFFICIENT_CURRENT_BALANCE` | Số dư hiện tại không đủ |
| Reason invalid | 422 | `INVALID_REJECTION_REASON` | Lý do từ chối không hợp lệ |
| Reference invalid | 422 | `INVALID_BANK_TXN_REF` | Mã giao dịch ngân hàng không hợp lệ |
| Audit failure | 500 | `AUDIT_WRITE_FAILED` | Không thể hoàn tất giao dịch |
| Decrypt provider failure | 503 | `BANK_DATA_UNAVAILABLE` | Không thể tải thông tin ngân hàng |

---

## Frontend Screen Contract

Canonical routes SHALL thuộc Admin:

| Legacy screen | Canonical target |
|---------------|------------------|
| `manager/withdrawal-pending.html` | `admin/withdrawal-pending.html` |
| `manager/withdrawal-detail.html` | `admin/withdrawal-detail.html` |
| `manager/withdrawal-history.html` | `admin/withdrawal-history.html` |
| `admin/withdrawals.html` placeholder | Pending queue entry point |

### Pending Screen

Required: three KPI cards, age filter, FIFO table, masked account, pagination, link history,
Loading/Empty/Error states.

### Detail Screen

Required sections: withdrawal, Driver/wallet, bank verify, timeline/reconciliation. `PENDING`
shows process/reject actions. Terminal status is read-only.

### History Screen

Required: status/date/Driver filters, table, masked bank fields, detail links and pagination.
Export CSV remains deferred.

---

## Security & Privacy

1. Chỉ Admin được process/reject/reveal.
2. Manager nhận 403 và không thấy withdrawal nav.
3. Full account encrypted at rest và chỉ decrypt sau recent authentication.
4. Queue/history/email/log/audit dùng last4, không full account.
5. Bank reference được masked trong Driver-facing response.
6. Logs không chứa rejection reason, full account hoặc plaintext decrypt.
7. Process/reject cần idempotency key.
8. Search/filter dùng bound parameters.
9. Transaction/audit append-only.
10. Financial endpoints rate-limited và có request id.

---

## Acceptance Criteria

**AC1 — FIFO queue**  
GIVEN nhiều requests pending, WHEN Admin mở queue, THEN oldest first, KPIs và masked accounts
đúng, pagination hoạt động.

**AC2 — Process success**  
GIVEN pending request 1.000.000 VND và wallet đủ, WHEN Admin process với unique bank reference,
THEN wallet giảm đúng 1.000.000, total withdrawn tăng, một transaction/audit được tạo và status
`PROCESSED`.

**AC3 — Reject success**  
GIVEN pending request, WHEN Admin reject với reason hợp lệ, THEN status `REJECTED`, audit/email
tạo và wallet/transactions không đổi.

**AC4 — No legacy states**  
GIVEN bất kỳ workflow action, WHEN response/UI render, THEN không tạo/hiển thị
`APPROVED|COMPLETED`.

**AC5 — Insufficient balance**  
GIVEN damage deduction làm wallet thấp hơn request, WHEN Admin process, THEN 422, rollback toàn
bộ và request vẫn pending.

**AC6 — Concurrent process**  
GIVEN 50 Admin process requests đồng thời, WHEN chạy, THEN đúng một debit/transaction/audit,
các request khác replay hoặc 409.

**AC7 — Cancel race**  
GIVEN Driver cancel và Admin process cùng lúc, WHEN chạy, THEN đúng một terminal state và money
effect phù hợp winner.

**AC8 — RBAC/privacy**  
GIVEN Manager/Driver/Customer token, WHEN gọi Admin endpoints, THEN 403; queue/history chỉ trả
masked account.

**AC9 — Reconciliation**  
GIVEN processed records, WHEN reconciliation chạy, THEN matching rows pass và mismatch tạo alert
không auto-edit.

**AC10 — UI quality**  
GIVEN loading/empty/error scenarios, WHEN mở screens, THEN states tiếng Việt, brand tokens và
accessible actions hiển thị đúng.

---

## Edge Cases & Error Handling

### EC-01 — Hai Admin process cùng request

Expected: row lock serialize; một process commit, request còn lại idempotent replay hoặc 409.

### EC-02 — Admin process và reject đồng thời

Expected: first terminal transition wins; second 409; không overwrite result.

### EC-03 — Driver cancel và Admin process đồng thời

Expected: first lock/commit wins; nếu cancel thắng không debit; nếu process thắng debit once.

### EC-04 — External transfer thành công nhưng API timeout

Expected: Admin retry cùng key/reference; no second transfer/debit.

### EC-05 — Bank reference trùng request khác

Expected: unique constraint/409; không debit.

### EC-06 — Wallet giảm do DamageReport trước process

Expected: re-check fail 422; request pending để Admin reject/manual review.

### EC-07 — Audit insert fail

Expected: rollback wallet, transaction và withdrawal state.

### EC-08 — Email fail sau commit

Expected: money/state giữ nguyên, outbox retry và alert.

### EC-09 — Manager cố xử lý

Expected: HTTP 403; không reveal bank data.

### EC-10 — Admin session hết hạn khi account đang reveal

Expected: plaintext tự ẩn, action mới yêu cầu login/re-auth.

### EC-11 — Invalid/HTML rejection reason

Expected: validation/sanitization; không stored XSS.

### EC-12 — Driver suspended khi pending

Expected: detail warning; Admin policy/manual decision, không auto-process.

### EC-13 — Process row count bằng zero

Expected: rollback và alert invariant failure.

### EC-14 — Transaction insert duplicate

Expected: unique guard rollback; no double debit.

### EC-15 — Legacy APPROVED/COMPLETED rows

Expected: migration maps unambiguously; ambiguous records require manual review.

### EC-16 — Bank decrypt provider unavailable

Expected: detail metadata load, process disabled, 503 reveal error.

---

## Test Cases

### TC-001 — Pending Queue

**Type:** Integration  
**Given:** Three pending requests with different timestamps.  
**When:** Admin loads queue.  
**Then:** FIFO order, KPIs correct, account masked.

### TC-002 — Process Happy Path

**Type:** Integration  
**Given:** Pending 1M, wallet balance 3M.  
**When:** Admin processes with unique reference.  
**Then:** Wallet 2M, total withdrawn +1M, one withdrawal transaction/audit, processed status.

### TC-003 — Reject Happy Path

**Type:** Integration  
**Given:** Pending request.  
**When:** Admin rejects with valid reason.  
**Then:** Rejected, wallet unchanged, no money transaction, one audit/email.

### TC-004 — Insufficient Balance

**Type:** Integration  
**Given:** Pending 2M, current wallet 1M.  
**When:** Admin processes.  
**Then:** 422, no writes, pending remains.

### TC-005 — Concurrent Process

**Type:** Concurrency  
**Given:** 50 process calls for same request.  
**When:** Calls start simultaneously.  
**Then:** One debit/transaction/audit; no deadlock.

### TC-006 — Cancel vs Process

**Type:** Concurrency  
**Given:** Driver cancel and Admin process start together.  
**When:** Both execute.  
**Then:** One terminal state; wallet matches winner.

### TC-007 — Duplicate Bank Reference

**Type:** Constraint  
**Given:** Reference used by processed request.  
**When:** Admin processes another request with same reference.  
**Then:** 409 and no debit.

### TC-008 — Audit Failure Rollback

**Type:** Fault Injection  
**Given:** Audit insert fails.  
**When:** Admin processes.  
**Then:** Wallet/request/transaction unchanged.

### TC-009 — RBAC Matrix

**Type:** Security  
**Given:** Admin, Manager, Driver, Customer tokens.  
**When:** Each calls six endpoints.  
**Then:** Only Admin succeeds.

### TC-010 — Bank Reveal Privacy

**Type:** Security  
**Given:** Recent-auth Admin.  
**When:** Reveal account.  
**Then:** Plaintext shown max 60 seconds, audit exists, logs remain masked.

### TC-011 — Reconciliation Mismatch

**Type:** Operations  
**Given:** Processed withdrawal without transaction fixture.  
**When:** Reconciliation runs.  
**Then:** Alert emitted, no auto-edit.

### TC-012 — Retry After Timeout

**Type:** Idempotency  
**Given:** First process committed but response lost.  
**When:** Retry same key/payload.  
**Then:** Same response replayed, no second debit.

---

## Legacy Migration

Legacy rows SHALL migrate conservatively:

```sql
UPDATE withdrawal_request
SET status = 'PROCESSED'
WHERE status IN ('APPROVED', 'COMPLETED')
  AND bank_txn_ref IS NOT NULL;

UPDATE withdrawal_request
SET status = 'PENDING'
WHERE status = 'APPROVED'
  AND bank_txn_ref IS NULL;
```

Rows `COMPLETED` thiếu bank reference hoặc có money mismatch SHALL không auto-map; Admin phải
review bằng reconciliation runbook. Sau backfill, migration SHALL replace status CHECK với
canonical values.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Chỉ pending → processed/rejected; invalid transition 409 |
| HR-10 | Chỉ Admin theo CONTEXT RBAC |
| HR-11 | Email async không rollback money |
| HR-13 | Mọi money transition có audit atomic |
| HR-18 | Wallet không âm; debit guarded |
| HR-19/20 | Brand + tiếng Việt có dấu |
| HR-21 | Tên bảng an toàn |
| AC-07/08 | UTC + VND integer scale=0 |
| AC-09 | Không hard-delete financial history |
| AC-13 | Append-only money transaction |
| AC-14 | Status `VARCHAR + CHECK` |
| AC-15/16 | Pagination + UI states |
| ES-03/04/05 | Validation, errors, CORE tests |

---

## Out of Scope (Deferred)

1. Automatic bank transfer/API/webhook.
2. Bulk processing.
3. Fraud AI/scoring.
4. Deposit refund.
5. Currency conversion.
6. CSV export.
7. Manager withdrawal authority.

