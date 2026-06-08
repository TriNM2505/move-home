# Feature Specification: Driver Financial (Earnings & Withdrawal)

**Feature Branch:** `007-driver-financial`  
**Feature Number:** #7 of 30 — CORE (driver monetization)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 3 (earnings); Sprint 5 (withdrawal processing)

**CONTEXT.md reference:** v2.0 §2 Wallet & Commission, Withdrawal, Escrow  
**Constitution reference:** v1.3.0 — HR-05, HR-10, HR-11, HR-13, HR-18,
HR-19, HR-20, HR-21, AC-07, AC-08, AC-09, AC-12, AC-13, AC-14, AC-15,
AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Driver screens 4.11 đến 4.13  
**Related specs:** Spec #005 Driver Onboarding; Spec #006 Driver Workflow;
Admin Withdrawal Processing spec; Dispute/Damage spec

---

## Goals

Cho phép Driver minh bạch theo dõi tiền đã kiếm và chủ động yêu cầu rút tiền về tài khoản ngân
hàng. Trang earnings hiển thị số dư có thể rút, tổng earnings đã release sau escrow, tổng đã rút,
số đơn tạo thu nhập, giao dịch append-only và hai biểu đồ Chart.js theo ngày/tháng. Mọi số tiền
dùng VND nguyên đồng và có thể đối soát từ `driver_wallet` sang bảng `transaction`.

Driver tạo withdrawal request bằng amount và thông tin ngân hàng. Theo `CONTEXT.md v2.0`, request
`PENDING` không trừ hoặc hold wallet; Admin xem queue, tự chuyển khoản bên ngoài hệ thống rồi
đánh dấu `PROCESSED`. Chỉ lúc đó hệ thống lock ví, kiểm tra lại số dư, trừ tiền và append
`WITHDRAWAL` transaction trong cùng DB transaction. Nếu Admin reject hoặc Driver cancel trước xử
lý, ví không thay đổi.

Spec phải ngăn nhiều request làm tổng pending vượt số dư, xử lý race giữa cancel/process, bảo vệ
thông tin ngân hàng và audit mọi thay đổi tài chính. Mục tiêu UX là Driver hiểu rõ tiền nào đã
release, tiền nào đang chờ xử lý và SLA 1-2 ngày làm việc. Ba màn hình dùng Move_home forest
green `#1B4D3E`, amber `#F5A623`, Be Vietnam Pro, tiếng Việt có dấu và đủ Loading/Empty/Error.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Specs #005/#006 →
> spec này → inventory/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả |
|--------|----------------------|--------|
| Withdrawal reviewer | Admin, không phải Manager | Admin tự chuyển khoản ngoài hệ thống |
| States | `PENDING|PROCESSED|REJECTED|CANCELLED` | Không tạo `APPROVED|COMPLETED` |
| Wallet deduction | Chỉ khi Admin mark `PROCESSED` | Request/cancel/reject không update wallet |
| Pending reservation | Không hold DB balance | `available_to_withdraw = balance - SUM(PENDING)` |
| Concurrent requests | Lock wallet + pending requests khi tạo | Tổng pending không vượt balance |
| Cancel | Extension an toàn `PENDING → CANCELLED` | Không refund transaction vì chưa trừ tiền |
| Earnings source | `DRIVER_EARNING` transaction sau escrow | Chart theo transaction release date |
| Wallet source | `driver_wallet` từ Spec #006 | Không dùng legacy `driver_profile.total_revenue` làm source |
| Money audit | Extend append-only `transaction` với `WITHDRAWAL` | Không tạo mutable wallet transaction |
| Deposit refund | Luồng riêng khi Driver nghỉ | Không trộn vào normal withdrawal |

---

## Scope Summary

**In scope:**

1. `GET /api/driver/earnings/overview` — KPI tài chính.
2. `GET /api/driver/earnings/daily?days=30` — bar chart.
3. `GET /api/driver/earnings/monthly?months=12` — line chart.
4. `GET /api/driver/earnings/transactions` — earnings audit pagination.
5. `GET /api/driver/withdrawals/form` — available amount + saved bank info.
6. `POST /api/driver/withdrawals` — tạo request.
7. `GET /api/driver/withdrawals` — history pagination.
8. `GET /api/driver/withdrawals/{id}` — detail/timeline.
9. `POST /api/driver/withdrawals/{id}/cancel` — cancel pending request.
10. `withdrawal_request`, saved bank account và transaction extensions.
11. Chart aggregation, reconciliation và financial audit.

**Out of scope:**

1. Admin queue/process/reject UI — Admin Withdrawal Processing spec.
2. Banking API hoặc transfer tự động.
3. Deposit refund khi Driver nghỉ.
4. Tax invoices/reporting.
5. Multi-currency.
6. Manual wallet adjustment.
7. Damage deduction implementation.
8. Driver earnings trước escrow release.

---

## User Stories

**P1:**

**US1:** Là Driver `ACTIVE`, tôi xem bốn KPI tài chính để biết số dư, tổng thu nhập, tổng đã rút
và số đơn tạo earnings.

**US2:** Là Driver, tôi xem biểu đồ earnings release trong 30 ngày để nhận biết xu hướng ngắn hạn.

**US3:** Là Driver, tôi xem biểu đồ earnings release trong 12 tháng để hiểu hiệu suất dài hạn.

**US4:** Là Driver, tôi tạo yêu cầu rút tiền hợp lệ về tài khoản ngân hàng.

**US5:** Là Driver, tôi xem lịch sử withdrawal với filter và pagination.

**US6:** Là Driver, tôi mở một withdrawal để xem timeline requested/processed/rejected/cancelled.

**US7:** Là Driver, tôi cancel withdrawal `PENDING` nếu nhập sai trước khi Admin xử lý.

**P2:**

**US8:** Là Driver, tôi lưu thông tin ngân hàng để auto-fill lần sau.

**US9:** Là Driver, tôi nhận email khi withdrawal được tạo, xử lý hoặc từ chối.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Earnings Overview (FR-001..FR-006)

**FR-001**
WHEN Driver `ACTIVE` gọi `GET /api/driver/earnings/overview`, THE system SHALL trả:

```json
{
  "wallet_balance": 2500000,
  "pending_withdrawal_total": 500000,
  "available_to_withdraw": 2000000,
  "total_earnings": 25000000,
  "total_withdrawn": 22000000,
  "completed_earning_orders": 142,
  "average_earning_per_order": 176056,
  "this_month_earnings": 3500000,
  "last_month_earnings": 4200000,
  "month_over_month_change_percent": "-16.7"
}
```

**FR-002**
WHEN tính overview, THE system SHALL lấy balance/total từ `driver_wallet`, pending total từ
withdrawal `PENDING`, và earnings/order count từ append-only `DRIVER_EARNING` transactions.

**FR-003**
WHEN tính available amount, THE system SHALL dùng
`available_to_withdraw = GREATEST(wallet.balance - pending_withdrawal_total, 0)`; pending request
không trực tiếp giảm `wallet.balance`.

**FR-004**
WHEN tính average/MoM, THE system SHALL trả `average=0` nếu order count bằng 0 và
`month_over_month_change_percent=null` nếu last month bằng 0; SHALL không chia cho 0.

**FR-005**
WHEN `earnings.html` render, frontend SHALL hiển thị bốn main KPI, hai secondary stats, CTA
"Yêu cầu rút tiền", charts và transaction table; money format `Intl.NumberFormat('vi-VN')`.

**FR-006**
WHILE overview fetch, frontend SHALL render skeleton; WHERE API lỗi, SHALL hiển thị
"Không thể tải thông tin thu nhập" + retry; WHERE Driver không ACTIVE, SHALL trả/redirect 403.

---

### Nhóm 2 — Daily Earnings Chart (FR-007..FR-010)

**FR-007**
WHEN Driver gọi `GET /api/driver/earnings/daily?days=30`, THE system SHALL validate `days`
integer `1..90`, aggregate released `DRIVER_EARNING` by `Asia/Ho_Chi_Minh` day và trả đúng `days`
entries ordered ascending.

**FR-008**
WHEN một ngày không có earning, THE system SHALL vẫn trả
`{"date":"YYYY-MM-DD","order_count":0,"total_earnings":0}` bằng `generate_series`.

**FR-009**
WHEN daily data render, frontend SHALL dùng Chart.js bar chart, labels `dd/MM`, y-axis VND,
tooltip gồm order count + total earnings và CSS token forest green.

**FR-010**
WHERE `days` sai range/type, backend SHALL trả HTTP 422; WHERE chart API lỗi, frontend SHALL giữ
KPI/table và hiển thị lỗi cục bộ, không fail toàn page.

---

### Nhóm 3 — Monthly Earnings Chart (FR-011..FR-014)

**FR-011**
WHEN Driver gọi `GET /api/driver/earnings/monthly?months=12`, THE system SHALL validate months
`1..24`, aggregate released earning theo month Việt Nam và trả đúng số tháng liên tục.

**FR-012**
WHEN một tháng không có earning, THE system SHALL trả entry zero; each item SHALL gồm
`month`, `order_count`, `total_earnings`.

**FR-013**
WHEN monthly data render, frontend SHALL dùng Chart.js line chart, smooth curve, fill nhẹ,
labels `MM/yyyy`, y-axis VND và accessible summary text.

**FR-014**
WHERE chart data rỗng bất thường hoặc malformed, frontend SHALL hiển thị
"Không thể hiển thị biểu đồ" và không dùng mock values.

---

### Nhóm 4 — Earnings Transactions List (FR-015..FR-019)

**FR-015**
WHEN Driver gọi `GET /api/driver/earnings/transactions?page=0&size=20`, THE system SHALL query
`transaction` rows owner JWT, type `DRIVER_EARNING`, sort `created_at DESC,id DESC` và trả Page.

**FR-016**
WHEN serialize earning item, THE system SHALL trả order code, positive amount, released timestamp,
masked Customer initial và description; SHALL không trả Customer phone/address.

**FR-017**
WHEN pagination render, frontend SHALL có selector `10|20|50|100`, page numbers/ellipsis,
Previous/Next và text "Hiển thị X-Y trong Z giao dịch".

**FR-018**
WHERE list rỗng, frontend SHALL hiển thị "Chưa có thu nhập đã được giải ngân"; WHILE fetch SHALL
render skeleton; WHERE lỗi SHALL hiển thị retry.

**FR-019**
WHERE request page/size sai hoặc Driver yêu cầu transaction type khác, THE system SHALL trả
HTTP 422; endpoint SHALL không expose deposit/damage/withdrawal transaction.

---

### Nhóm 5 — Withdrawal Request (FR-020..FR-028)

**FR-020**
WHEN Driver gọi `GET /api/driver/withdrawals/form`, THE system SHALL trả wallet balance,
pending total, available amount, minimum `100000`, SLA và saved bank account nullable.

**FR-021**
WHEN Driver submit `POST /api/driver/withdrawals` với `Idempotency-Key`, request SHALL có:

```json
{
  "amount": 1000000,
  "bank_code": "VCB",
  "bank_account_number": "1234567890",
  "bank_account_holder": "NGUYEN VAN A",
  "note": "Rút thu nhập tháng 6"
}
```

**FR-022**
WHEN validate request, THE system SHALL enforce amount integer VND `>=100000`, bank code thuộc
allowlist, account number regex `^[0-9]{9,15}$`, holder 2-100 uppercase Latin/Vietnamese letters
và spaces, note nullable max 500.

**FR-023**
WHEN request hợp lệ, THE system SHALL lock `driver_wallet` và current pending withdrawals,
recalculate available; IF amount `<= available_to_withdraw`, THEN insert withdrawal `PENDING`,
audit `WITHDRAWAL_REQUESTED`, return HTTP 201 và SHALL NOT update wallet/transaction.

**FR-024**
WHERE amount vượt available sau lock, THE system SHALL trả HTTP 422
`INSUFFICIENT_AVAILABLE_BALANCE`, rollback và không insert request.

**FR-025**
WHERE Driver không `ACTIVE`, wallet thiếu, hoặc account bị `SUSPENDED`, THE system SHALL trả
HTTP 403/409 tương ứng và không tạo request.

**FR-026**
WHEN request tạo thành công, THE system SHALL enqueue email "Yêu cầu rút tiền đã được gửi";
email lỗi SHALL không rollback.

**FR-027**
WHEN Driver chọn "Lưu thông tin ngân hàng", THE system SHALL upsert encrypted-at-rest
`driver_bank_account` after validation; withdrawal row luôn snapshot bank data tại request time.

**FR-028**
WHERE idempotency key retry cùng payload, THE system SHALL replay response; same key khác payload
SHALL trả HTTP 409 và không tạo duplicate pending amount.

---

### Nhóm 6 — Withdrawal History (FR-029..FR-033)

**FR-029**
WHEN Driver gọi `GET /api/driver/withdrawals?page=0&size=20&status=ALL`, THE system SHALL query
owner requests, filter `ALL|PENDING|PROCESSED|REJECTED|CANCELLED`, sort requested DESC,id DESC.

**FR-030**
WHEN serialize history item, THE system SHALL trả id, amount, bank name, masked account,
localized status, requested/processed/cancelled timestamps và rejection reason nullable.

**FR-031**
WHEN history render, frontend SHALL map `PENDING=Đang chờ`, `PROCESSED=Đã chuyển khoản`,
`REJECTED=Bị từ chối`, `CANCELLED=Đã hủy`; SHALL không hiển thị legacy `APPROVED|COMPLETED`.

**FR-032**
WHERE history rỗng, frontend SHALL hiển thị "Chưa có yêu cầu rút tiền"; WHILE fetch SHALL render
skeleton; WHERE lỗi SHALL hiển thị retry, giữ filter hiện tại.

**FR-033**
WHERE status/page/size invalid, backend SHALL trả HTTP 422 theo ES-04; history SHALL hỗ trợ hơn
1.000 requests mà không load toàn bộ vào memory.

---

### Nhóm 7 — Withdrawal Detail (FR-034..FR-038)

**FR-034**
WHEN Driver gọi `GET /api/driver/withdrawals/{id}`, THE system SHALL verify owner và trả snapshot
bank masked, amount, status, note, rejection reason, processor label và timeline.

**FR-035**
WHEN detail timeline build, THE system SHALL lấy immutable events:
`WITHDRAWAL_REQUESTED`, `WITHDRAWAL_PROCESSED`, `WITHDRAWAL_REJECTED`,
`WITHDRAWAL_CANCELLED`, ordered timestamp/id ascending.

**FR-036**
WHEN status `PROCESSED`, detail SHALL hiển thị masked bank transaction reference và processed
timestamp; WHERE `REJECTED`, SHALL hiển thị lý do tiếng Việt.

**FR-037**
WHERE withdrawal thuộc Driver khác, THE system SHALL trả HTTP 403
`WITHDRAWAL_OWNERSHIP_REQUIRED`; WHERE không tồn tại, SHALL trả 404.

**FR-038**
WHILE detail fetch, frontend SHALL render skeleton; WHERE optional timeline lỗi, SHALL vẫn render
summary và lỗi cục bộ.

---

### Nhóm 8 — Cancel Withdrawal (FR-039..FR-042)

**FR-039**
WHEN Driver owner gọi `POST /api/driver/withdrawals/{id}/cancel` với idempotency key, THE system
SHALL lock request, require status `PENDING` và transition sang `CANCELLED`.

**FR-040**
WHEN cancel hợp lệ, THE system SHALL set `cancelled_at=NOW()`, audit
`WITHDRAWAL_CANCELLED`, publish notification và return HTTP 200; SHALL NOT update wallet hoặc
insert refund transaction vì request chưa trừ tiền.

**FR-041**
WHERE Admin đang process hoặc status `PROCESSED|REJECTED|CANCELLED`, cancel SHALL trả HTTP 409
`INVALID_STATUS_TRANSITION`, không mutate.

**FR-042**
WHEN cancel commit, pending total/available amount SHALL phản ánh thay đổi ngay; email notification
async lỗi không rollback.

---

## Non-Functional Requirements

**NFR-001**
Earnings overview SHALL có P90 dưới 500 ms.

**NFR-002**
Chart queries SHALL có P90 dưới 1 giây cho 30 ngày/12 tháng.

**NFR-003**
Withdrawal request/cancel SHALL có P90 dưới 2 giây kể cả row lock.

**NFR-004**
Withdrawal history page 20 SHALL có P90 dưới 500 ms với 10.000 rows.

**NFR-005**
Mọi money/state operation SHALL có immutable audit/correlation id.

**NFR-006**
Available check, process deduction và transaction insert SHALL atomic.

**NFR-007**
Mọi money field SHALL integer VND/BigDecimal scale=0.

**NFR-008**
Email notifications SHALL async và không block API.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/driver/earnings/overview` | none | 200 KPI DTO | Active Driver |
| GET | `/api/driver/earnings/daily` | days | 200 chart array | Active Driver |
| GET | `/api/driver/earnings/monthly` | months | 200 chart array | Active Driver |
| GET | `/api/driver/earnings/transactions` | page,size | 200 Page | Active Driver |
| GET | `/api/driver/withdrawals/form` | none | 200 form DTO | Active Driver |
| POST | `/api/driver/withdrawals` | withdrawal body | 201 request | Active Driver |
| GET | `/api/driver/withdrawals` | page,size,status | 200 Page | Active Driver |
| GET | `/api/driver/withdrawals/{id}` | id | 200 detail | Owner Driver |
| POST | `/api/driver/withdrawals/{id}/cancel` | idempotency header | 200 cancelled | Owner Driver |

---

## Data Model

### Table `withdrawal_request`

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

CREATE INDEX idx_withdrawal_driver_requested
    ON withdrawal_request (driver_id, requested_at DESC, id DESC);

CREATE INDEX idx_withdrawal_pending_fifo
    ON withdrawal_request (requested_at ASC)
    WHERE status = 'PENDING';
```

### Table `driver_bank_account`

```sql
CREATE TABLE driver_bank_account (
    driver_id                 UUID         NOT NULL REFERENCES app_user(id),
    bank_code                 VARCHAR(20)  NOT NULL,
    bank_name                 VARCHAR(100) NOT NULL,
    account_number_encrypted  TEXT         NOT NULL,
    account_number_last4      CHAR(4)      NOT NULL,
    account_holder            VARCHAR(100) NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_driver_bank_account PRIMARY KEY (driver_id)
);
```

### Extend Append-only `transaction`

```sql
ALTER TABLE transaction DROP CONSTRAINT IF EXISTS transaction_type_check;

ALTER TABLE transaction
    ADD CONSTRAINT ck_transaction_type
    CHECK (type IN (
        'DEPOSIT_TOP_UP',
        'DEPOSIT_REFUND',
        'ORDER_PAYMENT',
        'DRIVER_EARNING',
        'PLATFORM_FEE',
        'DAMAGE_DEDUCTION',
        'REFUND',
        'WITHDRAWAL'
    ));

ALTER TABLE transaction
    ADD COLUMN IF NOT EXISTS related_withdrawal_id UUID
        REFERENCES withdrawal_request(id),
    ADD COLUMN IF NOT EXISTS balance_after NUMERIC(15,0);

CREATE UNIQUE INDEX uq_transaction_withdrawal
    ON transaction (related_withdrawal_id)
    WHERE type = 'WITHDRAWAL';
```

### Create Request Transaction

```sql
BEGIN;

SELECT driver_id, balance
FROM driver_wallet
WHERE driver_id = :driver_id
FOR UPDATE;

SELECT COALESCE(SUM(amount), 0) AS pending_total
FROM withdrawal_request
WHERE driver_id = :driver_id
  AND status = 'PENDING';

INSERT INTO withdrawal_request
    (driver_id, amount, bank_code, bank_name_snapshot, bank_account_number,
     bank_account_holder, note, idempotency_key)
VALUES
    (:driver_id, :amount, :bank_code, :bank_name, :account_number,
     :account_holder, :note, :idempotency_key);

COMMIT;
```

Service SHALL verify `amount <= balance - pending_total` before insert. No wallet mutation occurs.

### Admin Process Transaction Boundary

Admin Processing spec SHALL use:

```sql
BEGIN;

SELECT id, driver_id, amount, status
FROM withdrawal_request
WHERE id = :withdrawal_id
FOR UPDATE;

SELECT driver_id, balance
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

COMMIT;
```

Admin SHALL chuyển khoản ngoài hệ thống trước khi mark processed. Mọi row count phải bằng 1.

---

## State Machine

```text
PENDING
  ├─ Driver cancel trước xử lý ─────────→ CANCELLED
  ├─ Admin chuyển khoản + mark done ────→ PROCESSED
  └─ Admin reject kèm reason ───────────→ REJECTED

Terminal: PROCESSED, REJECTED, CANCELLED
```

Only `PENDING → PROCESSED` updates wallet and inserts `WITHDRAWAL` transaction.
Invalid transitions SHALL return HTTP 409 per HR-05.

---

## Error Matrix

| HTTP | `error_code` | Khi nào |
|------|--------------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn |
| 403 | `FORBIDDEN` | Không phải Active Driver |
| 403 | `WITHDRAWAL_OWNERSHIP_REQUIRED` | Request Driver khác |
| 404 | `WITHDRAWAL_NOT_FOUND` | ID không tồn tại |
| 409 | `INVALID_STATUS_TRANSITION` | Cancel/process sai state |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same key, payload khác |
| 422 | `INSUFFICIENT_AVAILABLE_BALANCE` | Amount vượt available |
| 422 | `VALIDATION_ERROR` | Input/filter/range sai |
| 429 | `RATE_LIMITED` | Vượt request rate |

---

## Chart Aggregation Queries

### Daily

```sql
WITH days AS (
    SELECT generate_series(
        (:today_vn::date - (:days - 1) * INTERVAL '1 day')::date,
        :today_vn::date,
        INTERVAL '1 day'
    )::date AS day
),
earnings AS (
    SELECT (t.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,
           COUNT(*) AS order_count,
           SUM(t.amount) AS total_earnings
    FROM transaction t
    WHERE t.user_id = :driver_id
      AND t.type = 'DRIVER_EARNING'
      AND t.created_at >= :from_utc
    GROUP BY 1
)
SELECT d.day,
       COALESCE(e.order_count, 0) AS order_count,
       COALESCE(e.total_earnings, 0) AS total_earnings
FROM days d
LEFT JOIN earnings e ON e.day = d.day
ORDER BY d.day ASC;
```

### Monthly

```sql
WITH months AS (
    SELECT generate_series(
        date_trunc('month', :current_month::date) - (:months - 1) * INTERVAL '1 month',
        date_trunc('month', :current_month::date),
        INTERVAL '1 month'
    )::date AS month
),
earnings AS (
    SELECT date_trunc('month', t.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS month,
           COUNT(*) AS order_count,
           SUM(t.amount) AS total_earnings
    FROM transaction t
    WHERE t.user_id = :driver_id
      AND t.type = 'DRIVER_EARNING'
      AND t.created_at >= :from_utc
    GROUP BY 1
)
SELECT m.month,
       COALESCE(e.order_count, 0) AS order_count,
       COALESCE(e.total_earnings, 0) AS total_earnings
FROM months m
LEFT JOIN earnings e ON e.month = m.month
ORDER BY m.month ASC;
```

---

## Acceptance Criteria

**AC1**
Overview KPI khớp `driver_wallet`, pending requests và append-only transactions đến từng VND.

**AC2**
Daily/monthly APIs trả đủ ngày/tháng liên tục, missing periods bằng zero và Chart.js render đúng.

**AC3**
Earning transaction list chỉ expose `DRIVER_EARNING` của Driver owner.

**AC4**
Withdrawal hợp lệ tạo một `PENDING` request nhưng không thay đổi wallet/transaction.

**AC5**
Nhiều request đồng thời không làm tổng pending vượt wallet balance.

**AC6**
Driver cancel `PENDING` thành công, không tạo refund transaction hoặc thay đổi wallet.

**AC7**
Cancel race với Admin process chỉ một transition commit.

**AC8**
Admin process hợp lệ trừ wallet và append `WITHDRAWAL` đúng một lần trong cùng transaction.

**AC9**
History/detail mask account number, enforce ownership và map status canonical.

**AC10**
Ba màn hình có brand Move_home, tiếng Việt và đủ Loading/Empty/Error.

---

## Edge Cases & Error Handling

| ID | Tình huống | Expected Behavior |
|----|------------|-------------------|
| EC-01 | Amount dưới 100.000 | 422 |
| EC-02 | Amount vượt available do pending requests | 422 |
| EC-03 | Hai concurrent requests cùng dùng available cuối | Lock cho một success |
| EC-04 | Network fail sau request commit | Idempotent retry trả request cũ |
| EC-05 | Account number có ký tự chữ | 422 |
| EC-06 | Bank code không allowlist | 422 |
| EC-07 | Driver suspended trước create | Không tạo request |
| EC-08 | Driver suspended sau pending | Admin policy quyết định process/reject |
| EC-09 | Cancel cùng lúc Admin process | Một terminal transition, no double money |
| EC-10 | Bank transfer ngoài hệ thống fail | Admin không mark processed; request PENDING/reject |
| EC-11 | Wallet balance giảm bởi damage trước process | Process rollback/reject vì insufficient balance |
| EC-12 | Chart period không earning | Zero-filled chart |
| EC-13 | Duplicate process callback/action | Unique withdrawal transaction ngăn double debit |
| EC-14 | Email lỗi | State/money transaction vẫn commit |
| EC-15 | Saved bank info bị sửa sau request | Request snapshot không đổi |

---

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TC-01 | Overview wallet 2,5M + pending 0,5M | Available 2M |
| TC-02 | Daily chart 30 ngày, 2 ngày earning | 30 entries, 28 zero |
| TC-03 | Request 1M với available 2M | PENDING, wallet unchanged |
| TC-04 | Request 2,1M với available 2M | 422 |
| TC-05 | Hai requests 1,5M trên balance 2M | Một success |
| TC-06 | Cancel pending | CANCELLED, wallet unchanged |
| TC-07 | Cancel/process race | One terminal state |
| TC-08 | Admin process valid 1M | Wallet -1M, one WITHDRAWAL transaction |
| TC-09 | Process khi wallet thiếu | Rollback, no transaction |
| TC-10 | Driver A reads Driver B withdrawal | 403 |

### Required Automated Test Layers

1. Unit tests cho available calculation, validation, chart zero-fill và status mapping.
2. PostgreSQL/Testcontainers concurrency tests cho request/cancel/process locks.
3. Integration tests cho RBAC, ownership, pagination, idempotency và wallet audit.
4. Contract tests cho Admin Processing boundary.
5. Frontend tests cho charts, filters, masks và Empty/Loading/Error.
6. Financial reconciliation/security tests.
7. CORE coverage tối thiểu 70%.

---

## Detailed API Contracts

### Withdrawal Create Success

```json
{
  "id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "amount": 1000000,
  "status": "PENDING",
  "status_label": "Đang chờ xử lý",
  "bank_name": "Vietcombank",
  "bank_account_masked": "******7890",
  "requested_at": "2026-06-04T10:30:00Z",
  "message": "Yêu cầu rút tiền đã được gửi."
}
```

### Withdrawal Detail Timeline

```json
{
  "id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "amount": 1000000,
  "status": "PROCESSED",
  "bank_account_masked": "******7890",
  "timeline": [
    {"event":"WITHDRAWAL_REQUESTED","label":"Đã gửi yêu cầu","at":"2026-06-04T10:30:00Z"},
    {"event":"WITHDRAWAL_PROCESSED","label":"Đã chuyển khoản","at":"2026-06-05T03:00:00Z"}
  ]
}
```

---

## Transaction & Concurrency Boundaries

Lock order SHALL luôn là `withdrawal_request` (nếu có) → `driver_wallet`. Create request chỉ lock
wallet; Admin process và cancel lock request trước. Same lock order tránh deadlock.

Available-to-withdraw là reservation logic, không phải wallet hold. Pending amount được tính trong
create transaction dưới wallet lock, nên requests của cùng Driver serialize. Admin process SHALL
recheck actual balance vì DamageReport có thể giảm wallet sau khi request.

---

## Admin Processing Boundary

Driver-facing spec không cung cấp endpoint process/reject. Admin Withdrawal Processing spec SHALL
consume cùng state machine với contracts tối thiểu:

| Admin action | Required current state | Result | Wallet effect |
|--------------|------------------------|--------|---------------|
| View queue/detail | `PENDING` | unchanged | none |
| Reject with reason | `PENDING` | `REJECTED` | none |
| Confirm external transfer | `PENDING` | `PROCESSED` | debit + audit in same TX |

Admin không được mark `PROCESSED` trước khi có external bank transfer reference. Nếu transfer
được thực hiện nhưng DB transaction fail, Admin phải retry cùng idempotency key; unique
`related_withdrawal_id` ngăn debit lần hai.

Reject contract SHALL require reason 10-500 characters, set `processed_by`/`processed_at`, append
audit and email Driver. Reject không tạo money transaction vì wallet chưa bị trừ.

### Processor-safe Detail DTO

Admin detail có thể xem full encrypted account sau authorization/decryption boundary; Driver DTO
chỉ masked. Audit event SHALL lưu last4, bank code và amount, không lưu full account.

---

## Bank Validation Contract

Initial allowlist:

| Code | Tên hiển thị |
|------|--------------|
| `VCB` | Vietcombank |
| `BIDV` | BIDV |
| `CTG` | VietinBank |
| `TCB` | Techcombank |
| `MB` | MB Bank |
| `ACB` | ACB |
| `VPB` | VPBank |
| `TPB` | TPBank |
| `STB` | Sacombank |
| `VIB` | VIB |

Bank allowlist SHALL là server configuration/versioned reference, không chỉ hardcode frontend.
Request snapshot giữ bank name tại thời điểm tạo để lịch sử không đổi nếu label cấu hình đổi.

Account holder normalization:

1. Unicode NFC;
2. trim/collapse spaces;
3. uppercase theo locale-neutral;
4. chấp nhận Vietnamese letters và spaces;
5. từ chối digits, punctuation và control characters.

Account number SHALL lưu encrypted ciphertext trong saved account; withdrawal snapshot có thể
được mã hóa riêng hoặc field-level encryption. Plaintext chỉ tồn tại trong memory ngắn hạn để
Admin thực hiện transfer.

---

## Status & Timeline Contract

| Status | Label Driver | Allowed Driver action | Terminal |
|--------|--------------|-----------------------|----------|
| `PENDING` | Đang chờ xử lý | View, Cancel | No |
| `PROCESSED` | Đã chuyển khoản | View | Yes |
| `REJECTED` | Bị từ chối | View | Yes |
| `CANCELLED` | Đã hủy | View | Yes |

Timeline events derive từ immutable audit, không suy diễn chỉ từ current state:

```json
[
  {
    "event_type": "WITHDRAWAL_REQUESTED",
    "label": "Đã gửi yêu cầu",
    "occurred_at": "2026-06-04T10:30:00Z",
    "actor_label": "Bạn"
  },
  {
    "event_type": "WITHDRAWAL_PROCESSED",
    "label": "Đã chuyển khoản",
    "occurred_at": "2026-06-05T03:00:00Z",
    "actor_label": "Move_home"
  }
]
```

Internal Admin id/note không được trả cho Driver trừ rejection reason đã duyệt để hiển thị.

---

## Migration & Rollout Plan

1. Deploy `driver_wallet` từ Spec #006 và reconcile legacy Driver balances.
2. Tạo `withdrawal_request`, `driver_bank_account`, transaction type/column extensions.
3. Backfill no withdrawal rows; seed/demo withdrawals phải map sang canonical statuses.
4. Deploy read-only overview/charts/earnings history trước.
5. Deploy withdrawal form/create/cancel dưới feature flag.
6. Deploy Admin queue/reject/process sau concurrency và external-transfer runbook tests.
7. Chuyển UI legacy `APPROVED` thành `PENDING` hoặc `PROCESSED` đúng dữ liệu.
8. Bật reconciliation/alerts trước khi xử lý withdrawal thật.

Migration legacy statuses:

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

Migration SHALL không tự đánh dấu processed nếu thiếu bank reference. Rows ambiguous phải được
Admin rà soát thủ công.

---

## Performance & Caching

Overview/charts là read-heavy và MAY cache tối đa 60 giây theo Driver id. Events
`DRIVER_EARNING`, `WITHDRAWAL_REQUESTED/CANCELLED/PROCESSED/REJECTED` SHALL invalidate cache.

History/detail không cache bank information. Chart endpoints SHALL limit ranges server-side và
dùng indexes:

```sql
CREATE INDEX idx_transaction_driver_earning_created
    ON transaction (user_id, created_at DESC)
    WHERE type = 'DRIVER_EARNING';

CREATE INDEX idx_withdrawal_driver_status_requested
    ON withdrawal_request (driver_id, status, requested_at DESC);
```

Chart.js SHALL destroy existing chart instance trước re-render để tránh memory leak khi filter
tháng/range thay đổi.

---

## Reconciliation & Operations

Daily reconciliation:

```sql
SELECT w.driver_id,
       w.balance,
       COALESCE(SUM(t.amount), 0) AS audit_net
FROM driver_wallet w
LEFT JOIN transaction t ON t.user_id = w.driver_id
GROUP BY w.driver_id, w.balance;
```

Audit net cần phân loại deposit balance và opening balance khi triển khai; mismatch tạo alert,
không auto-edit. Withdrawal reconciliation SHALL verify mỗi `PROCESSED` row có đúng một negative
`WITHDRAWAL` transaction và bank reference.

SLA metrics:

| Metric | Type | Labels |
|--------|------|--------|
| `driver_financial_overview_duration_seconds` | Histogram | `outcome` |
| `driver_withdrawal_request_total` | Counter | `outcome` |
| `driver_withdrawal_transition_total` | Counter | `from`, `to`, `outcome` |
| `driver_withdrawal_pending_age_seconds` | Histogram | none |
| `driver_wallet_reconciliation_mismatch_total` | Counter | none |

Alert khi oldest pending >2 business days, double-debit attempt, wallet insufficient at process,
reconciliation mismatch hoặc ownership violation spike.

---

## Frontend Screen Contract

| Screen | Canonical behavior |
|--------|--------------------|
| `earnings.html` | Four KPI + daily/monthly Chart.js + earnings transactions |
| `withdrawal-request.html` | Available amount, form, saved bank info, SLA |
| `withdrawal-history.html` | Filter/page/history/detail timeline |

Frontend SHALL loại bỏ legacy `APPROVED/COMPLETED` labels, không giảm displayed wallet ngay khi
request, và luôn lấy available amount mới trước submit.

---

## Privacy & Security

1. Bank account number encrypted at rest; history/detail chỉ trả last four digits.
2. Logs/audit không chứa full account number, Customer PII hoặc encryption key.
3. Driver only reads own wallet/withdrawals; Admin processing thuộc endpoint/spec riêng.
4. Bank transaction reference masked với Driver, full value chỉ Admin/audit.
5. Financial rows append-only hoặc terminal-state controlled; không hard-delete.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Invalid withdrawal transition trả 409 |
| HR-10 | Driver RBAC + ownership |
| HR-11 | Email async |
| HR-13 | Mọi withdrawal/money state có audit |
| HR-18 | Wallet không âm; debit + transaction same TX |
| HR-19/20 | Brand + tiếng Việt |
| HR-21 | Tên bảng an toàn |
| AC-07/08 | UTC/timezone + VND BigDecimal |
| AC-09 | User-facing request không hard-delete |
| AC-12/13/14 | Flyway, money audit, VARCHAR CHECK |
| AC-15/16 | Pagination + UI states |
| ES-03/04/05 | Validation, errors, CORE tests |

---

## Out of Scope (Deferred)

1. Admin withdrawal processing UI và bank transfer integration.
2. Deposit refund/replenishment.
3. Tax, multi-currency và automatic adjustment.
4. Damage deduction implementation.

---

## Open Questions

1. Chốt maximum withdrawal mỗi lần/ngày và fee; minimum 100.000 theo inventory.
2. Chốt pending request có tự expire hay không.
3. Chốt Driver suspended có được Admin process pending withdrawal không.
4. Chốt danh sách/mã ngân hàng authoritative và encryption provider.
5. Chốt reconciliation opening-balance strategy cho seed/legacy wallets.
