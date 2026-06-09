# Feature Specification: Manager Disputes Resolution

**Feature Branch:** `010-manager-disputes`  
**Feature Number:** #10 of 30 — CORE (customer trust + driver protection)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §17 Dispute resolution flow, §18 3-outcome decision  
**Constitution reference:** v1.3.0 — HR-05, HR-10, HR-11, HR-13, HR-19, HR-20,
HR-21, AC-08, AC-14, AC-15, AC-16  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Manager screens 5.8, 5.9  
**Related specs:** Spec #001 Auth/RBAC; Spec #003 Customer Orders; Spec #006 Driver Workflow;
Spec #008 Manager Driver Approval; Spec #009 Manager Withdrawal

---

## Goals

Manager là gatekeeper bảo vệ niềm tin giữa Customer và Driver khi một order đã hoàn thành nhưng
Customer phát hiện vấn đề như đồ đạc hư hỏng, thiếu vật dụng, giao trễ, hành vi không phù hợp hoặc
một sự cố khác. Trong vòng 24 giờ sau `COMPLETED`, Customer có thể tạo dispute, nhập số tiền yêu
cầu, mô tả sự việc và tải ảnh bằng chứng. Order chuyển sang `IN_DISPUTE`, tiền liên quan được giữ,
và Manager phải review trong ba ngày làm việc.

Manager cần thấy toàn bộ bối cảnh trước khi quyết định: nội dung Customer, ảnh, phản hồi và ảnh của
Driver, thông tin order, lịch sử giao dịch, lịch sử dispute của hai bên và comment nội bộ. Manager
có thể gọi điện cho hai bên ngoài hệ thống khi cần, sau đó chọn đúng một trong ba outcome:
`REFUND_CUSTOMER` để hoàn toàn bộ hoặc một phần cho Customer; `DEDUCT_DRIVER` để trừ trách nhiệm
từ Driver và hoàn cùng số tiền cho Customer; hoặc `CLOSE_NO_FAULT` khi bằng chứng không đủ để quy
lỗi. Mọi quyết định tài chính phải dùng VND nguyên đồng, khóa dữ liệu, chạy atomic và có audit.

Sau khi quyết định commit, hệ thống tự động cập nhật ví, append money transaction, lưu người xử lý,
thời gian và lý do, rồi gửi email tiếng Việt cho cả Customer và Driver. Mục tiêu là giải quyết nhanh
và nhất quán, bảo vệ Customer mà không xử lý thiếu công bằng với Driver. Audit trail là bắt buộc vì
dispute có thể trở thành tranh chấp pháp lý, khiếu nại vận hành hoặc đầu vào cho điều tra gian lận.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec #003 → Spec #006 →
> Spec #008/#009 → spec này → `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Order status | Dùng `IN_DISPUTE` | `DISPUTED` chỉ là alias UI/legacy; không thêm vào DB CHECK |
| Dispute status | `OPEN`, `INVESTIGATING`, ba terminal outcomes | Không dùng badge text làm giá trị DB |
| Claim window | 24 giờ sau `completed_at` cho flow Customer future | Creation thuộc spec mở rộng của Spec #003, không thuộc API Manager |
| Decision authority | `MANAGER` và `ADMIN` | Role khác nhận 403; Admin dùng cùng contract và audit actor role |
| Decision outcomes | Đúng ba outcome trong spec này | Không tạo outcome ngầm như `PARTIAL`, `REJECTED`, `CANCELLED` |
| Refund amount | VND nguyên đồng, tối đa `service_order.total_quote` | Dùng `BigDecimal` scale 0 và `NUMERIC(15,0)` |
| Driver liability | Trừ `driver_wallet.balance`, sau đó `deposit_balance` | Không để wallet/deposit âm; thiếu tổng nguồn thì 422 |
| Customer refund | Credit `customer_wallet.balance` | Mỗi credit có append-only money transaction |
| Order after resolution | Giữ `IN_DISPUTE` trong scope Manager Disputes | Việc chuyển về `COMPLETED|CANCELLED` là integration decision future |
| Escrow | Open dispute giữ earning ở `HELD` | Escrow worker không release khi dispute còn hoặc đã quyết định chưa reconcile |
| Evidence delivery | Cloudinary signed URL TTL tối đa một giờ | Không expose raw private URL/public id |
| Concurrent decisions | Lock dispute trước các wallet | Chỉ một terminal decision commit; request thua nhận 409 |
| Audit | Audit decision nằm trong cùng DB transaction | Audit failure rollback toàn bộ state và money |
| Email | Outbox/async sau commit | Email lỗi không rollback decision theo HR-11 |

Các quyết định trên là contract canonical cho feature này. Nếu UI stub đang hiển thị “Khiếu nại”
hoặc “Chờ phản hồi”, frontend phải map sang status canonical. Nếu migration cũ dùng `DISPUTED`,
implementation phải migrate sang `IN_DISPUTE`, không mở rộng state machine bằng alias.

---

## Scope Summary

**In scope:**

1. `GET /api/manager/disputes/pending` — list dispute đang `OPEN|INVESTIGATING`.
2. `GET /api/manager/disputes/{id}` — chi tiết evidence, parties, order và transaction.
3. `POST /api/manager/disputes/{id}/refund-customer` — hoàn Customer full hoặc partial.
4. `POST /api/manager/disputes/{id}/deduct-driver` — trừ Driver và hoàn Customer.
5. `POST /api/manager/disputes/{id}/close-no-fault` — đóng không có lỗi rõ ràng.
6. `POST /api/manager/disputes/{id}/comment` — thêm comment nội bộ.
7. `GET /api/manager/disputes/history` — lịch sử có filter và pagination.
8. KPI queue, SLA ba ngày làm việc, filter pills và stable server-side pagination.
9. RBAC cho `MANAGER|ADMIN`, state validation và concurrent decision guard.
10. Atomic wallet update, append-only transaction và audit log cho money decision.
11. Email tiếng Việt cho Customer và Driver sau mỗi terminal decision.
12. Hai màn `disputes.html` và `dispute-detail.html` với Loading/Empty/Error states.

**Out of scope:**

1. Customer tạo dispute và upload evidence — Spec #003 extension, Sprint 5+.
2. Driver counter-claim hoặc tự sửa phản hồi sau khi submit — defer Sprint 5+.
3. Request-more-evidence workflow và deadline extension — defer.
4. Tự động phát hiện fraud, scoring hoặc liên kết dispute liên quan — defer.
5. Legal/police escalation — thực hiện thủ công ngoài hệ thống.
6. Insurance claim integration.
7. Thuật toán tự động giảm Driver rating.
8. Chuyển order `IN_DISPUTE` về `COMPLETED|CANCELLED`.
9. Customer hoặc Driver appeal một terminal decision.
10. Xóa evidence, comment, transaction hoặc audit history.

---

## User Stories

**P1 (CORE):**

**US1:** Là Manager, tôi xem danh sách dispute đang mở cùng tổng số dispute, tổng claim amount và
dispute chờ lâu nhất để ưu tiên xử lý đúng SLA.

**US2:** Là Manager, tôi mở một dispute để xem Customer claim, ảnh bằng chứng, Driver response,
chi tiết order, hồ sơ hai bên và lịch sử transaction trước khi quyết định.

**US3:** Là Manager, tôi chọn `REFUND_CUSTOMER`, nhập số tiền và lý do để hệ thống tự động hoàn
toàn bộ hoặc một phần cho Customer.

**US4:** Là Manager, tôi chọn `DEDUCT_DRIVER`, nhập số tiền và lý do để hệ thống tự động trừ trách
nhiệm Driver và hoàn cùng số tiền cho Customer.

**US5:** Là Manager, tôi chọn `CLOSE_NO_FAULT` khi bằng chứng không đủ và đóng dispute mà không
phát sinh tác động tài chính.

**US6:** Là Manager, tôi thêm comment nội bộ để ghi lại quá trình xác minh và rationale trước khi
đưa ra quyết định cuối cùng.

**US7:** Là Manager, tôi xem lịch sử dispute và lọc theo outcome hoặc khoảng ngày để tra cứu.

**P2 (Deferred):**

**US8:** Là Manager, tôi yêu cầu Customer hoặc Driver cung cấp thêm evidence trước deadline.

**US9:** Là Manager, tôi xem các dispute liên quan của cùng Customer hoặc Driver để nhận diện mẫu
gian lận.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Disputes List (FR-001..FR-007)

**FR-001**  
WHEN authenticated actor role `MANAGER|ADMIN` gọi
`GET /api/manager/disputes/pending?page=0&size=20&status=ALL`, THE system SHALL trả HTTP 200 với
Spring Page chỉ gồm dispute có status `OPEN|INVESTIGATING`, sort
`created_at ASC, id ASC` để dispute cũ nhất được xử lý trước.

Mỗi item SHALL có:

```json
{
  "id": "1518729b-6289-48c7-9821-4dad3518ac25",
  "order_code": "MH202606050001",
  "customer_name": "Nguyễn Thu Hà",
  "driver_name": "Nguyễn Văn Hùng",
  "claim_amount": 2400000,
  "claim_type": "DAMAGE",
  "claim_type_label": "Hư hỏng đồ đạc",
  "status": "OPEN",
  "created_at": "2026-06-05T04:05:00Z",
  "deadline": "2026-06-10T04:05:00Z",
  "days_waiting": 2,
  "over_sla": false
}
```

**FR-002**  
WHEN pending query chạy, THE system SHALL chỉ chấp nhận `claim_type` thuộc
`DAMAGE|MISSING_ITEM|LATE_DELIVERY|INAPPROPRIATE_BEHAVIOR|OTHER`; WHERE DB row có giá trị khác,
SHALL không silently map mà SHALL phát metric data-integrity và trả label “Không xác định”.

**FR-003**  
WHEN `status=ALL`, THE system SHALL query `OPEN|INVESTIGATING`; WHEN `status=OPEN` hoặc
`status=INVESTIGATING`, SHALL lọc đúng status; WHERE status khác allowlist, SHALL trả HTTP 422
code `INVALID_DISPUTE_FILTER` và không query bằng chuỗi input thô.

**FR-004**  
WHEN pending list được trả, THE response SHALL có KPI metadata tính trên toàn bộ tập pending,
không chỉ page hiện tại:

```json
{
  "pending_total": 4,
  "open_total": 2,
  "investigating_total": 2,
  "total_claim_amount": 7200000,
  "oldest_waiting_days": 4,
  "over_sla_total": 1,
  "content": [],
  "totalElements": 4,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true
}
```

**FR-005**  
WHEN `frontend/pages/manager/disputes.html` render thành công, THE frontend SHALL hiển thị ba KPI
“Khiếu nại đang mở”, “Chờ lâu nhất”, “Tổng số tiền yêu cầu”; filter pills
“Tất cả / Mới / Đang điều tra”; và table columns `Mã đơn`, `Khách hàng`, `Tài xế`, `Loại`,
`Ngày gửi`, `Số tiền`, `Trạng thái`, `Thao tác`.

**FR-006**  
WHILE pending API đang tải, THE frontend SHALL render KPI và table skeleton; WHERE list rỗng,
SHALL hiển thị “Không có khiếu nại nào”; WHERE API lỗi, SHALL hiển thị
“Không thể tải danh sách khiếu nại” cùng button “Thử lại” theo AC-16.

**FR-007**  
WHEN `totalElements > size`, THE frontend SHALL render server-side pagination có page numbers,
ellipsis, Previous/Next disabled state, selector `10|20|50|100` và text
“Hiển thị X-Y trong Z khiếu nại”; WHERE `page < 0` hoặc `size` ngoài allowlist, backend SHALL trả
HTTP 422 theo AC-15.

---

### Nhóm 2 — Dispute Detail (FR-008..FR-016)

**FR-008**  
WHEN authorized actor gọi `GET /api/manager/disputes/{id}`, THE system SHALL trả HTTP 200 nếu
dispute tồn tại; WHERE UUID không tồn tại, SHALL trả HTTP 404 code `DISPUTE_NOT_FOUND`; WHERE row
soft-deleted hoặc inaccessible, SHALL không expose dữ liệu.

**FR-009**  
WHEN detail được serialize, THE response SHALL chứa đầy đủ các section:

```json
{
  "dispute": {
    "id": "1518729b-6289-48c7-9821-4dad3518ac25",
    "claim_type": "DAMAGE",
    "claim_amount": 2400000,
    "customer_statement": "Tủ gỗ bị trầy ở cạnh trái sau vận chuyển.",
    "status": "INVESTIGATING",
    "created_at": "2026-06-05T04:05:00Z",
    "deadline": "2026-06-10T04:05:00Z",
    "resolution_amount": null,
    "resolution_note": null
  },
  "order": {
    "order_code": "MH202606050001",
    "total_quote": 2400000,
    "completed_at": "2026-06-05T03:20:00Z",
    "route": {},
    "vehicle": {}
  },
  "customer": {
    "id": "a27d4044-c5ac-43a1-bdf7-b20fe9ad00ba",
    "name": "Nguyễn Thu Hà",
    "phone": "+84912***678",
    "total_orders": 8,
    "total_disputes": 1
  },
  "driver": {
    "id": "21076328-7604-423a-a87f-b2b799502011",
    "name": "Nguyễn Văn Hùng",
    "phone": "+84986***321",
    "rating": "4.80",
    "total_orders": 120,
    "total_disputes": 2
  },
  "evidence": {
    "photos": [],
    "driver_response": null,
    "manager_comments": []
  },
  "related_transactions": [],
  "allowed_actions": []
}
```

**FR-010**  
WHEN detail service lấy evidence, THE system SHALL group ảnh theo uploader role và evidence type,
trả signed Cloudinary URL TTL tối đa một giờ, thumbnail, content type, created time; SHALL không
trả raw `public_id`, unsigned private URL hoặc metadata chứa secret.

**FR-011**  
WHEN Driver đã phản hồi, THE detail SHALL trả `statement`, signed photo list và `submitted_at`;
WHERE Driver chưa phản hồi, SHALL trả `driver_response=null` và frontend SHALL hiển thị
“Tài xế chưa gửi phản hồi” thay vì fail toàn page.

**FR-012**  
WHEN detail service lấy related transactions, THE system SHALL trả các payment/earning/refund/
deduction liên quan order theo `created_at ASC, id ASC`, với amount, type, status và masked
reference; SHALL không expose payment secret hoặc bank account.

**FR-013**  
WHEN detail page render, THE frontend SHALL có năm section rõ ràng: `Khiếu nại`, `Khách hàng`,
`Tài xế`, `Bằng chứng`, `Timeline`; order route và transaction history MAY nằm trong các card con
nhưng SHALL không bị bỏ qua.

**FR-014**  
WHEN Manager click ảnh evidence, THE frontend SHALL mở accessible lightbox có previous/next,
zoom, rotate, caption uploader/time và close bằng Escape; WHERE signed URL hết hạn, SHALL request
lại detail/evidence URL mà không reload toàn page.

**FR-015**  
WHILE dispute status là `OPEN|INVESTIGATING`, THE frontend SHALL hiển thị ba action
“Hoàn tiền khách hàng”, “Khấu trừ tài xế”, “Đóng không có lỗi” ở cuối trang; WHILE status terminal,
SHALL disable actions và hiển thị outcome, amount, note, actor và resolved time.

**FR-016**  
WHERE một section detail optional không tải được, THE frontend SHALL hiển thị lỗi cục bộ; WHERE
dispute, order, wallet eligibility hoặc allowed actions không tải được, SHALL disable cả ba
decision action và hiển thị “Không thể xác minh điều kiện xử lý”.

---

### Nhóm 3 — Refund Customer (FR-017..FR-023)

**FR-017**  
WHEN actor click “Hoàn tiền khách hàng”, THE frontend SHALL mở confirm modal hiển thị order total,
claim amount, Customer, input amount và textarea lý do; confirm SHALL disabled đến khi payload
hợp lệ và SHALL chống double-submit.

**FR-018**  
WHEN actor gọi `POST /api/manager/disputes/{id}/refund-customer` với `Idempotency-Key: <uuid>`,
request body SHALL là:

```json
{
  "amount": 700000,
  "note": "Ảnh và timeline xác nhận đồ đạc bị hư hỏng trong quá trình vận chuyển."
}
```

`amount` SHALL là integer VND từ `1..service_order.total_quote`; `note` SHALL trim length
`30..1000`, có ít nhất một chữ cái và là nội dung tiếng Việt user-facing.

**FR-019**  
WHEN refund request bắt đầu, THE backend SHALL mở DB transaction, lock theo thứ tự
`dispute FOR UPDATE` → `service_order FOR UPDATE` → `customer_wallet FOR UPDATE`, verify actor
role `MANAGER|ADMIN`, verify dispute status `OPEN|INVESTIGATING` và order status `IN_DISPUTE`.

**FR-020**  
WHERE refund amount lớn hơn order total, nhỏ hơn một, không phải VND nguyên đồng, note invalid,
Customer wallet thiếu hoặc dispute không gắn đúng Customer/order, THE system SHALL rollback và
trả HTTP 422 code phù hợp, không mutate dispute, wallet, transaction hoặc audit.

**FR-021**  
WHEN refund validation thành công, THE system SHALL trong cùng transaction:

```sql
UPDATE dispute
SET status = 'RESOLVED_REFUND',
    resolution_amount = :amount,
    resolution_note = :note,
    resolved_by = :actor_id,
    resolved_at = NOW(),
    version = version + 1
WHERE id = :dispute_id
  AND status IN ('OPEN', 'INVESTIGATING');

UPDATE customer_wallet
SET balance = balance + :amount,
    updated_at = NOW()
WHERE customer_id = :customer_id;

INSERT INTO wallet_transaction
    (wallet_owner_id, owner_role, type, amount, related_order_id,
     related_dispute_id, description, balance_after)
VALUES
    (:customer_id, 'CUSTOMER', 'REFUND', :amount, :order_id,
     :dispute_id, 'Hoàn tiền theo quyết định khiếu nại', :customer_balance_after);
```

The transaction SHALL giữ `service_order.status='IN_DISPUTE'` và SHALL không debit Driver.

**FR-022**  
WHEN refund transaction chạy, THE system SHALL insert append-only audit event
`DISPUTE_RESOLVED_REFUND` trong cùng transaction, gồm actor id/role, dispute id, order id,
Customer id, Driver id, previous/new status, amount, note hash, request id và timestamp; WHERE
audit insert fail, SHALL rollback toàn bộ.

**FR-023**  
WHEN refund transaction commit, THE system SHALL trả HTTP 200 với message
“Đã hoàn tiền cho khách hàng” và enqueue hai email async: Customer nhận
“Khiếu nại đã được giải quyết, hoàn tiền {amount} VND”; Driver nhận thông báo dispute đã resolved
và không bị khấu trừ; WHERE email lỗi, SHALL retry/alert nhưng không rollback.

---

### Nhóm 4 — Deduct Driver (FR-024..FR-030)

**FR-024**  
WHEN actor click “Khấu trừ tài xế”, THE frontend SHALL mở destructive confirm modal hiển thị
Driver, current balance, deposit balance, claim/order limits, input amount và textarea lý do;
modal SHALL giải thích rõ số tiền sẽ đồng thời được hoàn cho Customer.

**FR-025**  
WHEN actor gọi `POST /api/manager/disputes/{id}/deduct-driver` với `Idempotency-Key: <uuid>`,
body SHALL là `{"amount":500000,"note":"Bằng chứng xác nhận trách nhiệm thuộc về tài xế."}`;
`amount` SHALL là integer VND từ một đến
`MIN(service_order.total_quote, driver_wallet.balance + driver_wallet.deposit_balance)`;
`note` SHALL trim length `30..1000`.

**FR-026**  
WHEN deduct request bắt đầu, THE backend SHALL mở transaction và lock theo thứ tự
`dispute FOR UPDATE` → `service_order FOR UPDATE` → `driver_wallet FOR UPDATE` →
`customer_wallet FOR UPDATE`; SHALL re-check actor role, dispute status, order status, wallet
ownership và số dư tại thời điểm giữ lock.

**FR-027**  
WHEN amount hợp lệ, THE system SHALL tính bằng `BigDecimal` scale 0:

```text
wallet_part  = MIN(amount, driver_wallet.balance)
deposit_part = amount - wallet_part

new_driver_balance = driver_wallet.balance - wallet_part
new_deposit_balance = driver_wallet.deposit_balance - deposit_part
new_customer_balance = customer_wallet.balance + amount
```

IF `amount > balance + deposit_balance`, THEN SHALL trả HTTP 422
`INSUFFICIENT_DRIVER_FUNDS`; wallet/deposit SHALL không âm và SHALL không partial-commit.

**FR-028**  
WHEN deduction validation thành công, THE system SHALL trong cùng transaction update dispute sang
`RESOLVED_DEDUCT`, lưu resolution fields, debit Driver, credit Customer và append đúng hai money
transactions:

```sql
UPDATE driver_wallet
SET balance = :new_driver_balance,
    deposit_balance = :new_deposit_balance,
    updated_at = NOW()
WHERE driver_id = :driver_id;

INSERT INTO wallet_transaction
    (wallet_owner_id, owner_role, type, amount, related_order_id,
     related_dispute_id, description, balance_after)
VALUES
    (:driver_id, 'DRIVER', 'DISPUTE_DEDUCTION', -:amount, :order_id,
     :dispute_id, 'Khấu trừ theo quyết định khiếu nại', :new_driver_balance);

UPDATE customer_wallet
SET balance = balance + :amount,
    updated_at = NOW()
WHERE customer_id = :customer_id;

INSERT INTO wallet_transaction
    (wallet_owner_id, owner_role, type, amount, related_order_id,
     related_dispute_id, description, balance_after)
VALUES
    (:customer_id, 'CUSTOMER', 'REFUND', :amount, :order_id,
     :dispute_id, 'Hoàn tiền từ quyết định trách nhiệm tài xế', :new_customer_balance);
```

Driver transaction metadata SHALL lưu `wallet_part` và `deposit_part` để reconciliation.

**FR-029**  
WHEN deduction transaction chạy, THE system SHALL insert audit event
`DISPUTE_RESOLVED_DEDUCT` trong cùng transaction, giữ order `IN_DISPUTE`, và SHALL không tự động
giảm Driver rating; IF deposit sau debit thấp hơn mức vận hành bắt buộc, THEN SHALL phát event
`DRIVER_DEPOSIT_REPLENISHMENT_REQUIRED` cho workflow future.

**FR-030**  
WHEN deduction commit, THE system SHALL trả HTTP 200 message “Đã khấu trừ tài xế và hoàn tiền cho
khách hàng”, rồi enqueue email async cho Customer với amount refund và Driver với amount, lý do,
wallet/deposit split; email SHALL không chứa internal comment hoặc dữ liệu nhạy cảm.

---

### Nhóm 5 — Close No Fault (FR-031..FR-035)

**FR-031**  
WHEN actor click “Đóng không có lỗi”, THE frontend SHALL mở confirm modal giải thích không có tác
động tài chính và yêu cầu note; confirm SHALL disabled khi note trim ngắn hơn 30 ký tự.

**FR-032**  
WHEN actor gọi `POST /api/manager/disputes/{id}/close-no-fault` với
`{"note":"Không đủ bằng chứng xác định trách nhiệm của một trong hai bên."}`, THE system SHALL
validate note trim length `30..1000`, có chữ cái và không chỉ gồm whitespace/punctuation.

**FR-033**  
WHEN close-no-fault request hợp lệ, THE backend SHALL lock `dispute FOR UPDATE`, verify status
`OPEN|INVESTIGATING`, update status `CLOSED_NO_FAULT`, lưu note, actor, resolved time, increment
version và insert audit `DISPUTE_CLOSED_NO_FAULT` trong cùng transaction.

**FR-034**  
WHILE close-no-fault transaction chạy, THE system SHALL không lock/update Customer wallet,
Driver wallet, deposit hoặc append money transaction; SHALL giữ order status `IN_DISPUTE`.

**FR-035**  
WHEN close-no-fault commit, THE system SHALL trả HTTP 200 message “Đã đóng khiếu nại, không xác
định lỗi” và enqueue email tiếng Việt cho cả hai bên; WHERE email gửi lỗi, SHALL retry nhưng không
đổi terminal decision.

---

### Nhóm 6 — Comments (FR-036..FR-038)

**FR-036**  
WHEN actor gọi `POST /api/manager/disputes/{id}/comment` với
`{"comment":"Đã gọi điện cho khách hàng lúc 14:30 và xác nhận ảnh gốc."}`, THE system SHALL
validate comment trim length `1..2000`, sanitize output và insert `dispute_comment` với author id,
author role và created time.

**FR-037**  
WHILE dispute tồn tại, authorized actor MAY thêm comment trước hoặc sau terminal decision;
comment SHALL append-only, không sửa/xóa qua feature này, và SHALL không được đưa vào email cho
Customer/Driver.

**FR-038**  
WHEN comment được tạo, THE frontend SHALL append vào thread theo `created_at ASC, id ASC`, hiển
thị author, role và timestamp; WHERE submit fail, SHALL giữ draft trong textarea và hiển thị lỗi
cục bộ, không duplicate khi retry cùng idempotency key.

---

### Nhóm 7 — History + RBAC (FR-039..FR-043)

**FR-039**  
WHEN authorized actor gọi
`GET /api/manager/disputes/history?page=0&size=20&status=ALL&date_from=&date_to=`,
THE system SHALL trả Spring Page sort `created_at DESC, id DESC`, hỗ trợ status
`ALL|OPEN|INVESTIGATING|RESOLVED_REFUND|RESOLVED_DEDUCT|CLOSED_NO_FAULT`.

**FR-040**  
WHEN history có `date_from|date_to`, THE system SHALL parse ISO date theo `Asia/Ho_Chi_Minh`,
convert boundary sang UTC và filter `created_at`; WHERE range invalid, dài hơn 366 ngày hoặc
`date_from > date_to`, SHALL trả HTTP 422 `INVALID_DATE_RANGE`.

**FR-041**  
WHEN request gọi bất kỳ endpoint spec này, THE RBAC layer SHALL cho phép role `MANAGER|ADMIN`;
WHERE actor là `CUSTOMER|DRIVER` hoặc role khác, SHALL trả HTTP 403 `FORBIDDEN`; WHERE thiếu hoặc
hết hạn JWT, SHALL trả HTTP 401 theo Spec #001.

**FR-042**  
WHERE decision request gặp dispute terminal hoặc version/state đã đổi, THE system SHALL rollback
và trả HTTP 409 `DISPUTE_ALREADY_RESOLVED` cùng current status; same idempotency key + same payload
SHALL replay response cũ, còn same key + different payload SHALL trả 409
`IDEMPOTENCY_KEY_REUSED`.

**FR-043**  
WHEN bất kỳ money decision hoặc state decision chạy, THE system SHALL dùng `BigDecimal` scale 0,
bound parameters, row locks, append-only audit và request id; SHALL không log full note, phone,
signed evidence URL hoặc wallet secret.

---

## Non-Functional Requirements

**NFR-001 — Pending list performance**  
`GET /pending` SHALL hoàn tất dưới 500 ms ở p95 với 100.000 dispute records và page size 20.

**NFR-002 — Detail performance**  
`GET /{id}` SHALL hoàn tất dưới một giây ở p95 dù có nhiều JOIN; evidence URL signing MAY chạy
song song nhưng SHALL không expose URL chưa ký.

**NFR-003 — Decision latency**  
Decision API SHALL hoàn tất dưới ba giây ở p95, không chờ SMTP; email enqueue/outbox chạy sau
commit.

**NFR-004 — Evidence delivery**  
Thumbnail SHALL bắt đầu hiển thị dưới ba giây/ảnh trên mạng mục tiêu; ảnh gốc dùng lazy loading.

**NFR-005 — Audit durability**  
Mọi state decision và money decision SHALL có audit trong cùng DB transaction theo HR-13.

**NFR-006 — Atomicity**  
Refund/deduct SHALL all-or-nothing giữa dispute, wallets, money transactions và audit.

**NFR-007 — Notification isolation**  
Email lỗi SHALL không rollback transaction chính theo HR-11; outbox retry có alert.

**NFR-008 — SLA tracking**  
Deadline SHALL được tính từ `created_at` theo ba ngày làm việc; UI hiển thị overdue rõ ràng.

**NFR-009 — Concurrency**  
50 request quyết định đồng thời trên một dispute SHALL chỉ tạo đúng một terminal outcome, không
deadlock và không double money.

**NFR-010 — Idempotency**  
Mọi POST decision/comment SHALL hỗ trợ `Idempotency-Key`; retry không tạo duplicate writes/email.

**NFR-011 — Availability**  
Read endpoints target 99,9% monthly availability; decision endpoint lỗi dependency SHALL fail
closed trước money mutation.

**NFR-012 — UX quality**  
Hai màn SHALL responsive ở mobile/tablet/desktop, dùng forest green `#1B4D3E`, amber `#F5A623`,
Be Vietnam Pro, tiếng Việt có dấu và đủ Loading/Empty/Error.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/manager/disputes/pending` | `page,size,status` | 200 Page + KPI | Manager, Admin |
| GET | `/api/manager/disputes/{id}` | Path UUID | 200 detail | Manager, Admin |
| POST | `/api/manager/disputes/{id}/refund-customer` | `{amount,note}` | 200 resolved | Manager, Admin |
| POST | `/api/manager/disputes/{id}/deduct-driver` | `{amount,note}` | 200 resolved | Manager, Admin |
| POST | `/api/manager/disputes/{id}/close-no-fault` | `{note}` | 200 closed | Manager, Admin |
| POST | `/api/manager/disputes/{id}/comment` | `{comment}` | 201 comment | Manager, Admin |
| GET | `/api/manager/disputes/history` | `page,size,status,date_from,date_to` | 200 Page | Manager, Admin |

### Common Error Format

```json
{
  "timestamp": "2026-06-09T03:20:00Z",
  "status": 409,
  "error_code": "DISPUTE_ALREADY_RESOLVED",
  "message": "Khiếu nại đã được xử lý.",
  "path": "/api/manager/disputes/1518729b-6289-48c7-9821-4dad3518ac25/refund-customer",
  "request_id": "01JY...",
  "details": {
    "current_status": "RESOLVED_DEDUCT"
  }
}
```

---

## Data Model

Spec này tạo `dispute`, `dispute_evidence`, `dispute_comment` và reuse `service_order`,
`driver_wallet`, `customer_wallet`, append-only `wallet_transaction`, `audit_log`.

### Canonical `dispute`

```sql
CREATE TABLE dispute (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    order_id              UUID          NOT NULL REFERENCES service_order(id),
    customer_id           UUID          NOT NULL REFERENCES app_user(id),
    driver_id             UUID          NOT NULL REFERENCES app_user(id),
    claim_type            VARCHAR(30)   NOT NULL
        CHECK (claim_type IN (
            'DAMAGE',
            'MISSING_ITEM',
            'LATE_DELIVERY',
            'INAPPROPRIATE_BEHAVIOR',
            'OTHER'
        )),
    claim_amount          NUMERIC(15,0) NOT NULL
        CHECK (claim_amount > 0),
    customer_statement    TEXT          NOT NULL,
    driver_response       TEXT,
    driver_response_at    TIMESTAMPTZ,
    status                VARCHAR(30)   NOT NULL DEFAULT 'OPEN'
        CHECK (status IN (
            'OPEN',
            'INVESTIGATING',
            'RESOLVED_REFUND',
            'RESOLVED_DEDUCT',
            'CLOSED_NO_FAULT'
        )),
    resolution_amount     NUMERIC(15,0),
    resolution_note       TEXT,
    resolved_by           UUID          REFERENCES app_user(id),
    resolved_at           TIMESTAMPTZ,
    deadline              TIMESTAMPTZ   NOT NULL,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute PRIMARY KEY (id),
    CONSTRAINT uq_dispute_order_open UNIQUE NULLS NOT DISTINCT
        (order_id, resolved_at)
);
```

### Evidence & Comments

```sql
CREATE TABLE dispute_evidence (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    dispute_id            UUID          NOT NULL REFERENCES dispute(id),
    uploader_id           UUID          NOT NULL REFERENCES app_user(id),
    uploader_role         VARCHAR(20)   NOT NULL
        CHECK (uploader_role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),
    evidence_type         VARCHAR(30)   NOT NULL
        CHECK (evidence_type IN ('PHOTO', 'DOCUMENT', 'OTHER')),
    cloudinary_public_id  TEXT          NOT NULL,
    content_type          VARCHAR(100)  NOT NULL,
    file_size_bytes       BIGINT        NOT NULL CHECK (file_size_bytes > 0),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_evidence PRIMARY KEY (id)
);

CREATE TABLE dispute_comment (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    dispute_id            UUID          NOT NULL REFERENCES dispute(id),
    author_id             UUID          NOT NULL REFERENCES app_user(id),
    author_role           VARCHAR(20)   NOT NULL
        CHECK (author_role IN ('MANAGER', 'ADMIN')),
    comment               TEXT          NOT NULL,
    idempotency_key       UUID          NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_dispute_comment PRIMARY KEY (id),
    CONSTRAINT uq_dispute_comment_idempotency UNIQUE (author_id, idempotency_key)
);
```

### Required Constraints & Indexes

```sql
ALTER TABLE dispute
    ADD CONSTRAINT ck_dispute_resolution_fields
    CHECK (
        (status IN ('OPEN', 'INVESTIGATING')
            AND resolution_amount IS NULL
            AND resolution_note IS NULL
            AND resolved_by IS NULL
            AND resolved_at IS NULL)
        OR
        (status IN ('RESOLVED_REFUND', 'RESOLVED_DEDUCT')
            AND resolution_amount IS NOT NULL
            AND resolution_amount > 0
            AND resolution_note IS NOT NULL
            AND resolved_by IS NOT NULL
            AND resolved_at IS NOT NULL)
        OR
        (status = 'CLOSED_NO_FAULT'
            AND resolution_amount IS NULL
            AND resolution_note IS NOT NULL
            AND resolved_by IS NOT NULL
            AND resolved_at IS NOT NULL)
    );

CREATE INDEX idx_dispute_pending
    ON dispute (created_at ASC, id ASC)
    WHERE status IN ('OPEN', 'INVESTIGATING');

CREATE INDEX idx_dispute_history
    ON dispute (created_at DESC, id DESC);

CREATE INDEX idx_dispute_customer_history
    ON dispute (customer_id, created_at DESC);

CREATE INDEX idx_dispute_driver_history
    ON dispute (driver_id, created_at DESC);

CREATE INDEX idx_dispute_evidence_timeline
    ON dispute_evidence (dispute_id, created_at ASC, id ASC);

CREATE INDEX idx_dispute_comment_timeline
    ON dispute_comment (dispute_id, created_at ASC, id ASC);
```

### Money Transaction Extensions

```sql
ALTER TABLE wallet_transaction
    ADD COLUMN related_dispute_id UUID REFERENCES dispute(id);

CREATE UNIQUE INDEX uq_dispute_customer_refund
    ON wallet_transaction (related_dispute_id, wallet_owner_id, type)
    WHERE type = 'REFUND' AND related_dispute_id IS NOT NULL;

CREATE UNIQUE INDEX uq_dispute_driver_deduction
    ON wallet_transaction (related_dispute_id, wallet_owner_id, type)
    WHERE type = 'DISPUTE_DEDUCTION' AND related_dispute_id IS NOT NULL;
```

Mọi status dùng `VARCHAR + CHECK`, không PostgreSQL ENUM theo AC-14. Mọi migration chạy bằng
Flyway và `ddl-auto=validate`.

---

## Money Invariants

1. `customer_wallet.balance >= 0` luôn đúng.
2. `driver_wallet.balance >= 0` luôn đúng.
3. `driver_wallet.deposit_balance >= 0` luôn đúng.
4. `RESOLVED_REFUND` có đúng một positive Customer `REFUND` transaction.
5. `RESOLVED_REFUND` không có Driver `DISPUTE_DEDUCTION`.
6. `RESOLVED_DEDUCT` có đúng một negative Driver `DISPUTE_DEDUCTION`.
7. `RESOLVED_DEDUCT` có đúng một positive Customer `REFUND` cùng absolute amount.
8. `CLOSED_NO_FAULT` không có money transaction liên quan dispute.
9. Resolution amount không vượt `service_order.total_quote`.
10. Deduction amount không vượt Driver balance cộng deposit tại thời điểm lock.
11. Customer credit, Driver debit, dispute transition và audit cùng transaction.
12. Money transaction append-only; không update/delete để sửa lịch sử.
13. Retry cùng idempotency key không tạo transaction hoặc email thứ hai.
14. Audit failure rollback money operation.

---

## Transaction Boundaries

### Refund Customer Transaction

```sql
BEGIN;

SELECT id, order_id, customer_id, driver_id, status, claim_amount
FROM dispute
WHERE id = :dispute_id
FOR UPDATE;

SELECT id, total_quote, status
FROM service_order
WHERE id = :order_id
FOR UPDATE;

SELECT customer_id, balance
FROM customer_wallet
WHERE customer_id = :customer_id
FOR UPDATE;

UPDATE customer_wallet
SET balance = balance + :amount,
    updated_at = NOW()
WHERE customer_id = :customer_id;

INSERT INTO wallet_transaction (...);

UPDATE dispute
SET status = 'RESOLVED_REFUND',
    resolution_amount = :amount,
    resolution_note = :note,
    resolved_by = :actor_id,
    resolved_at = NOW(),
    version = version + 1
WHERE id = :dispute_id
  AND status IN ('OPEN', 'INVESTIGATING');

INSERT INTO audit_log (...);

COMMIT;
```

Every guarded update/insert SHALL affect exactly one row. Otherwise rollback.

### Deduct Driver Transaction

```sql
BEGIN;

SELECT id, order_id, customer_id, driver_id, status
FROM dispute
WHERE id = :dispute_id
FOR UPDATE;

SELECT id, total_quote, status
FROM service_order
WHERE id = :order_id
FOR UPDATE;

SELECT driver_id, balance, deposit_balance
FROM driver_wallet
WHERE driver_id = :driver_id
FOR UPDATE;

SELECT customer_id, balance
FROM customer_wallet
WHERE customer_id = :customer_id
FOR UPDATE;

UPDATE driver_wallet SET ...;
INSERT INTO wallet_transaction (... 'DISPUTE_DEDUCTION' ...);
UPDATE customer_wallet SET ...;
INSERT INTO wallet_transaction (... 'REFUND' ...);
UPDATE dispute SET ... status = 'RESOLVED_DEDUCT' ...;
INSERT INTO audit_log (...);

COMMIT;
```

### Close No Fault Transaction

```sql
BEGIN;

SELECT id, status
FROM dispute
WHERE id = :dispute_id
FOR UPDATE;

UPDATE dispute
SET status = 'CLOSED_NO_FAULT',
    resolution_note = :note,
    resolved_by = :actor_id,
    resolved_at = NOW(),
    version = version + 1
WHERE id = :dispute_id
  AND status IN ('OPEN', 'INVESTIGATING');

INSERT INTO audit_log (...);

COMMIT;
```

### Lock Order

Lock order SHALL luôn là:

```text
dispute → service_order → driver_wallet → customer_wallet → wallet_transaction → audit_log
```

Refund bỏ qua Driver wallet nhưng không đổi thứ tự tương đối. Close-no-fault chỉ lock dispute.
Không transaction nào được lock wallet trước dispute.

---

## State Machine

```text
Dispute Status Flow:

OPEN
  |-- Manager/Admin bắt đầu xác minh ----------> INVESTIGATING
  |-- Hoàn tiền Customer ----------------------> RESOLVED_REFUND [terminal]
  |-- Khấu trừ Driver + hoàn Customer ---------> RESOLVED_DEDUCT [terminal]
  |-- Đóng không có lỗi -----------------------> CLOSED_NO_FAULT [terminal]

INVESTIGATING
  |-- Hoàn tiền Customer ----------------------> RESOLVED_REFUND [terminal]
  |-- Khấu trừ Driver + hoàn Customer ---------> RESOLVED_DEDUCT [terminal]
  |-- Đóng không có lỗi -----------------------> CLOSED_NO_FAULT [terminal]
```

Rules:

1. Spec này không định nghĩa endpoint riêng `OPEN → INVESTIGATING`; implementation MAY chuyển khi
   Manager bắt đầu review trong detail load/action future, nhưng phải audit.
2. Ba terminal state là read-only.
3. Invalid transition trả HTTP 409 theo HR-05.
4. Customer/Driver không được resolve dispute.
5. `service_order.status` giữ `IN_DISPUTE` trong toàn bộ lifecycle của spec này.
6. `DISPUTED` chỉ là legacy/UI alias.

---

## Decision Eligibility Checklist

| Check | Refund Customer | Deduct Driver | Close No Fault |
|-------|-----------------|---------------|----------------|
| Actor role Manager/Admin | Blocking | Blocking | Blocking |
| Dispute `OPEN|INVESTIGATING` | Blocking | Blocking | Blocking |
| Order `IN_DISPUTE` | Blocking | Blocking | Blocking |
| Customer/Driver/order relationship valid | Blocking | Blocking | Blocking |
| Amount integer VND, `1..order.total_quote` | Blocking | Blocking | N/A |
| Customer wallet exists | Blocking | Blocking | N/A |
| Driver wallet exists | N/A | Blocking | N/A |
| Driver funds sufficient | N/A | Blocking | N/A |
| Note `30..1000` | Blocking | Blocking | Blocking |
| Evidence reviewed | Manual warning | Manual warning | Manual warning |
| SLA exceeded | Warning | Warning | Warning |

Warnings không tự block decision, nhưng frontend SHALL hiển thị rõ để actor xác nhận.

---

## Error Matrix

| Scenario | HTTP | `error_code` | Message |
|----------|------|--------------|---------|
| Không có JWT | 401 | `AUTHENTICATION_REQUIRED` | Phiên đăng nhập không hợp lệ |
| Role trái quyền | 403 | `FORBIDDEN` | Bạn không có quyền xử lý khiếu nại |
| Dispute không tồn tại | 404 | `DISPUTE_NOT_FOUND` | Không tìm thấy khiếu nại |
| Order không tồn tại | 404 | `ORDER_NOT_FOUND` | Không tìm thấy đơn hàng |
| Terminal decision | 409 | `DISPUTE_ALREADY_RESOLVED` | Khiếu nại đã được xử lý |
| Order không `IN_DISPUTE` | 409 | `INVALID_ORDER_DISPUTE_STATE` | Trạng thái đơn không hợp lệ |
| Idempotency key đổi payload | 409 | `IDEMPOTENCY_KEY_REUSED` | Khóa yêu cầu đã được sử dụng |
| Concurrent lock timeout | 409 | `DISPUTE_DECISION_IN_PROGRESS` | Khiếu nại đang được xử lý |
| Amount invalid | 422 | `INVALID_RESOLUTION_AMOUNT` | Số tiền xử lý không hợp lệ |
| Amount vượt order total | 422 | `AMOUNT_EXCEEDS_ORDER_TOTAL` | Số tiền vượt tổng giá trị đơn |
| Driver funds thiếu | 422 | `INSUFFICIENT_DRIVER_FUNDS` | Số dư và tiền cọc tài xế không đủ |
| Note invalid | 422 | `INVALID_RESOLUTION_NOTE` | Lý do xử lý không hợp lệ |
| Filter invalid | 422 | `INVALID_DISPUTE_FILTER` | Bộ lọc không hợp lệ |
| Date range invalid | 422 | `INVALID_DATE_RANGE` | Khoảng ngày không hợp lệ |
| Evidence URL provider lỗi | 503 | `EVIDENCE_UNAVAILABLE` | Không thể tải bằng chứng |
| Audit insert lỗi | 500 | `AUDIT_WRITE_FAILED` | Không thể hoàn tất quyết định |

---

## Frontend Screen Contract

### `frontend/pages/manager/disputes.html`

Required:

1. Page title “Đơn khiếu nại” và subtitle giải thích queue.
2. Ba KPI: pending count, oldest waiting, total claim amount.
3. Filter pills “Tất cả / Mới / Đang điều tra”.
4. FIFO table với amount VND, SLA warning và “Xem” action.
5. Server-side pagination.
6. Link hoặc section history có outcome filters.
7. Loading/Empty/Error states.
8. Row click và action keyboard accessible.

Legacy stub đang ghi “Sắp xếp theo ngày mới nhất” SHALL đổi thành “Ưu tiên khiếu nại chờ lâu nhất”.

### `frontend/pages/manager/dispute-detail.html?id={disputeId}`

Required:

1. Header có order code, status badge, created time, deadline và SLA warning.
2. Claim card có type, amount, Customer statement.
3. Customer và Driver cards có contact masked, performance summary.
4. Order card có route, vehicle, total và completed time.
5. Evidence gallery/lightbox và Driver response.
6. Related transaction timeline.
7. Internal Manager/Admin comment thread.
8. Ba decision cards/buttons.
9. Decision modal có amount/note validation tương ứng.
10. Terminal outcome read-only.
11. Section-level và page-level error handling.

Button “Không xử lý” trong legacy stub SHALL đổi thành “Đóng không có lỗi” để khớp canonical
outcome `CLOSED_NO_FAULT`.

---

## Security & Privacy

1. Chỉ `MANAGER|ADMIN` được gọi bảy endpoint.
2. Customer/Driver không đọc internal comments hoặc decision eligibility.
3. Phone trong list/detail mặc định masked; full contact chỉ dùng action được audit nếu future.
4. Evidence dùng signed URL TTL tối đa một giờ.
5. API/log/audit không chứa signed URL, raw Cloudinary id hoặc payment secret.
6. Resolution note được sanitize khi render để chống stored XSS.
7. Search/filter dùng bound parameters và server allowlist.
8. Money endpoints yêu cầu idempotency key và request id.
9. Wallet transaction và audit append-only.
10. Terminal dispute không sửa/xóa.
11. Amount dùng BigDecimal/NUMERIC, không Float/Double.
12. Rate-limit decision endpoint theo actor và dispute.

---

## Acceptance Criteria

**AC1 — Pending queue FIFO**  
GIVEN nhiều dispute `OPEN|INVESTIGATING`, WHEN Manager mở list, THEN dispute cũ nhất đứng trước,
KPI toàn tập đúng, filter và pagination hoạt động.

**AC2 — Detail đầy đủ**  
GIVEN dispute hợp lệ, WHEN Manager mở detail, THEN năm section, signed photos, Driver response,
order, parties, transactions, comments và allowed actions hiển thị đúng.

**AC3 — Refund Customer success**  
GIVEN open dispute và amount hợp lệ, WHEN Manager refund, THEN dispute `RESOLVED_REFUND`,
Customer wallet tăng đúng amount, đúng một refund transaction/audit và Driver wallet không đổi.

**AC4 — Deduct Driver success**  
GIVEN Driver đủ balance/deposit, WHEN Manager deduct, THEN Driver giảm đúng amount, Customer tăng
cùng amount, hai money transactions và một audit được tạo atomic.

**AC5 — Close no fault success**  
GIVEN dispute open, WHEN Manager đóng với note hợp lệ, THEN status `CLOSED_NO_FAULT`, audit/email
được tạo và không có wallet/transaction thay đổi.

**AC6 — Invalid state guard**  
GIVEN dispute terminal, WHEN actor gọi bất kỳ decision lần nữa, THEN HTTP 409 và không có write.

**AC7 — Concurrent decision**  
GIVEN 50 refund/deduct/close request đồng thời, WHEN chạy, THEN đúng một terminal decision commit,
không double money, không deadlock.

**AC8 — Insufficient Driver funds**  
GIVEN amount vượt balance cộng deposit, WHEN deduct, THEN HTTP 422 và rollback toàn bộ.

**AC9 — RBAC and privacy**  
GIVEN Manager, Admin, Customer và Driver token, WHEN gọi endpoints, THEN chỉ Manager/Admin thành
công; evidence URL signed và comments nội bộ không lộ.

**AC10 — Audit and email isolation**  
GIVEN audit insert fail, WHEN decision chạy, THEN rollback; GIVEN email fail sau commit, THEN
decision giữ nguyên và outbox retry.

**AC11 — UI quality**  
GIVEN loading, empty, error và terminal scenarios, WHEN mở hai màn, THEN UI tiếng Việt có dấu,
brand đúng, responsive và accessible.

**AC12 — Canonical state**  
GIVEN mọi response/migration/UI mapping, WHEN xử lý order dispute, THEN DB chỉ dùng
`IN_DISPUTE`, không tạo status `DISPUTED`.

---

## Edge Cases & Error Handling

### EC-01 — Claim amount lớn hơn order total

Expected: creation flow future hoặc decision validation từ chối; Manager không thể refund/deduct
vượt `service_order.total_quote`.

### EC-02 — Nhiều dispute cùng một order

Expected: chỉ tối đa một dispute chưa resolve; unique/business guard trả 409 cho open duplicate.
History MAY có nhiều terminal dispute nếu policy future cho phép.

### EC-03 — Driver balance đủ nhưng deposit dữ liệu lỗi

Expected: debit balance phần hợp lệ chỉ khi toàn bộ wallet row vượt integrity checks; nếu deposit
âm/null bất hợp lệ thì rollback và alert, không cố sửa tự động.

### EC-04 — Driver không đủ balance + deposit

Expected: HTTP 422 `INSUFFICIENT_DRIVER_FUNDS`, không partial debit, không Customer credit.

### EC-05 — Customer và Driver cùng id

Expected: data-integrity violation; decision disabled, API 422/500 controlled và alert; không
chuyển tiền giữa cùng owner.

### EC-06 — Ảnh lớn hơn 5 MB hoặc sai content type

Expected: upload flow future từ chối trước signed upload/finalize; detail không render file không
hợp lệ.

### EC-07 — Signed photo URL hết hạn khi lightbox đang mở

Expected: frontend request URL mới, giữ vị trí ảnh; không expose raw URL.

### EC-08 — Hai Manager quyết định đồng thời

Expected: row lock serialize; một request commit, request còn lại 409.

### EC-09 — Manager refund và Admin deduct đồng thời

Expected: first terminal transition wins; second 409; outcome đầu không bị overwrite.

### EC-10 — Double-click cùng decision

Expected: cùng idempotency key replay cùng response; một audit, một set money transactions.

### EC-11 — Dispute quá deadline ba ngày

Expected: vẫn cho authorized actor quyết định, hiển thị overdue warning, ghi SLA metric; không
auto-close.

### EC-12 — Order không còn `IN_DISPUTE`

Expected: decision trả 409, không mutate; reconciliation alert nếu dispute vẫn open.

### EC-13 — Customer wallet chưa tồn tại

Expected: money decision fail closed với controlled error; không tự tạo wallet trong decision
transaction nếu wallet provisioning contract chưa được chốt.

### EC-14 — Driver wallet bị thay đổi bởi withdrawal trước deduct

Expected: lock/re-check số dư mới; nếu thiếu thì 422, nếu đủ thì debit theo số dư hiện tại.

### EC-15 — Escrow worker chạy cùng decision

Expected: escrow thấy order `IN_DISPUTE` và không release; lock order/wallet theo compatible order.

### EC-16 — Audit insert thất bại

Expected: rollback dispute, wallets và money transactions.

### EC-17 — Email fail sau commit

Expected: terminal decision và money giữ nguyên; outbox retry và alert.

### EC-18 — Note chứa HTML/script

Expected: validation/sanitization chống stored XSS; raw note không log.

### EC-19 — Comment được submit sau terminal decision

Expected: được phép append để document follow-up; không thay đổi outcome.

### EC-20 — Browser mất mạng sau successful decision

Expected: retry cùng idempotency key replay response; detail load hiển thị terminal outcome.

---

## Test Cases

### TC-001 — Pending Queue FIFO

**Type:** Integration  
**Given:** Ba dispute pending với timestamps và amounts khác nhau.  
**When:** Manager tải page đầu.  
**Then:** Oldest-first, KPI/filter/page metadata đúng.

### TC-002 — Detail Evidence Contract

**Type:** Integration/Security  
**Given:** Dispute có Customer photos, Driver response và comments.  
**When:** Manager tải detail.  
**Then:** Năm section đúng, URLs signed, phone masked, không lộ public id.

### TC-003 — Refund Customer Happy Path

**Type:** Integration  
**Given:** Open dispute, order total 2.400.000, Customer wallet tồn tại.  
**When:** Manager refund 700.000.  
**Then:** Customer wallet +700.000, one refund/audit, terminal refund, Driver unchanged.

### TC-004 — Deduct Driver From Balance

**Type:** Integration  
**Given:** Driver balance 1.000.000, deposit 3.000.000.  
**When:** Manager deduct 500.000.  
**Then:** Balance còn 500.000, deposit giữ nguyên, Customer +500.000, two transactions.

### TC-005 — Deduct Driver Across Balance And Deposit

**Type:** Integration  
**Given:** Driver balance 200.000, deposit 3.000.000.  
**When:** Manager deduct 500.000.  
**Then:** Balance 0, deposit 2.700.000, metadata split đúng, Customer +500.000.

### TC-006 — Insufficient Driver Funds

**Type:** Integration  
**Given:** Driver total available 400.000.  
**When:** Manager deduct 500.000.  
**Then:** HTTP 422, dispute/wallet/transactions/audit không đổi.

### TC-007 — Close No Fault

**Type:** Integration  
**Given:** Open dispute.  
**When:** Manager close với note hợp lệ.  
**Then:** `CLOSED_NO_FAULT`, one audit, zero money writes, emails queued.

### TC-008 — Concurrent Decisions

**Type:** PostgreSQL Concurrency  
**Given:** 50 mixed refund/deduct/close calls cho cùng dispute.  
**When:** Calls bắt đầu đồng thời.  
**Then:** One terminal outcome, money khớp winner, no deadlock.

### TC-009 — Idempotent Retry

**Type:** Integration  
**Given:** Decision đã commit nhưng response bị mất.  
**When:** Retry cùng key/payload.  
**Then:** Replay cùng response, không duplicate money/audit/email.

### TC-010 — Audit Failure Rollback

**Type:** Fault Injection  
**Given:** Audit insert bị ép fail.  
**When:** Refund hoặc deduct chạy.  
**Then:** Dispute, wallets và transactions không đổi.

### TC-011 — Email Failure Does Not Roll Back

**Type:** Fault Injection  
**Given:** SMTP/outbox consumer fail sau commit.  
**When:** Decision thành công.  
**Then:** State/money giữ nguyên, retry record và alert tồn tại.

### TC-012 — RBAC Matrix

**Type:** Security  
**Given:** Manager, Admin, Customer, Driver và anonymous.  
**When:** Mỗi actor gọi bảy endpoint.  
**Then:** Manager/Admin được phép; Customer/Driver 403; anonymous 401.

### TC-013 — Validation Matrix

**Type:** Contract  
**Given:** Amount zero/decimal/quá total, note ngắn/dài/HTML, invalid filter/date.  
**When:** Gọi endpoints tương ứng.  
**Then:** HTTP 422 format thống nhất, zero writes.

### TC-014 — Signed URL Expiry

**Type:** Security/Frontend  
**Given:** Evidence signed URL hết hạn.  
**When:** Manager mở lightbox.  
**Then:** URL mới được lấy, raw Cloudinary id không lộ.

### TC-015 — Reconciliation Mismatch

**Type:** Operations  
**Given:** Fixture terminal dispute thiếu transaction.  
**When:** Reconciliation chạy.  
**Then:** Alert phát ra, không auto-edit money.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Invalid dispute/order transition trả 409 |
| HR-10 | Chỉ Manager/Admin; role khác 403 |
| HR-11 | Email async sau commit, lỗi không rollback |
| HR-13 | Mọi decision/comment state change có audit phù hợp |
| HR-19 | Hai màn dùng Move_home forest green + amber |
| HR-20 | UI/email tiếng Việt có đầy đủ dấu |
| HR-21 | Dùng `dispute`, `dispute_comment`, không reserved table names |
| AC-08 | VND BigDecimal scale 0, NUMERIC(15,0) |
| AC-14 | Status VARCHAR + CHECK, không PostgreSQL ENUM |
| AC-15 | Pending/history dùng server-side pagination |
| AC-16 | Loading/Empty/Error mandatory |
