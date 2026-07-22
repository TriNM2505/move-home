# Feature Specification: Admin List Pages (Orders / Drivers / Customers / Withdrawals)

**Feature Branch:** `011-admin-list-pages`  
**Feature Number:** #11 of 30 — CORE (Admin oversight)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 1 (UI done, backend Sprint 5)

**CONTEXT.md reference:** v2.0 §19 Admin operations  
**Constitution reference:** v1.3.0 — HR-10 (RBAC ADMIN only), HR-13 (audit),
HR-19, HR-20, HR-21, AC-08, AC-14, AC-15, AC-16  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Admin screens 6.2, 6.3, 6.4, 6.5  
**Related specs:** Spec #001 Auth/RBAC; Spec #003 Customer Orders; Spec #005 Driver Onboarding;
Spec #006 Driver Workflow; Spec #008 Manager Driver Approval; Spec #009 Admin Withdrawal;
Spec #012 Admin Detail Pages; Spec #028 Admin Dashboard

---

## Goals

Admin cần bốn trang danh sách chính để giám sát toàn bộ hoạt động Move_home mà không phải truy vấn
DB hoặc đi qua dashboard tổng hợp. `orders.html` cho thấy mọi order và trạng thái vận hành;
`drivers.html` cho thấy toàn bộ vòng đời Driver, hiệu suất và số dư; `customers.html` cho thấy
Customer, mức sử dụng và trạng thái tài khoản; `withdrawals.html` cho thấy dòng tiền rút của tất
cả Driver. Mỗi trang phải hỗ trợ server-side pagination, search, status filter, sort và click một
row để mở detail thuộc Spec #012.

Admin phải tìm được record trong vài giây bằng search box debounce 300 ms, kết hợp filter và
khoảng ngày mà không làm nghẽn backend. Page size chuẩn là `10|20|50|100`; sort phải dùng
allowlist, ổn định bằng secondary key `id`, và mọi list phải có Loading/Empty/Error states.
Status enum kỹ thuật được map sang badge tiếng Việt có màu nhất quán. Tiền hiển thị VND nguyên
đồng với dấu chấm phân cách; timestamp lưu UTC và hiển thị theo `Asia/Ho_Chi_Minh`.

Mục tiêu UX là giúp Admin nhanh chóng tìm, lọc và monitor entity trên toàn hệ thống, đồng thời giữ
privacy và RBAC. Refresh thủ công luôn có; auto-refresh 30 giây là tùy chọn và tạm dừng khi tab
ẩn hoặc Admin đang tương tác. List access được audit có throttle để hỗ trợ điều tra mà không tạo
log spam. Export CSV, bulk actions và chỉnh sửa từ list được defer sang phase sau.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → domain specs → spec này →
> `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Admin authority | Chỉ `ADMIN` | Manager/Driver/Customer nhận HTTP 403 |
| Order states | `PENDING_PAYMENT|CONFIRMED|ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT|COMPLETED|CANCELLED|IN_DISPUTE` | `DRAFT|PENDING|ACCEPTED|DISPUTED` trong prompt/stub là alias hoặc không canonical |
| Driver states | `PENDING_VERIFY|PENDING_DOCUMENTS|PENDING_DEPOSIT|PENDING_APPROVAL|ACTIVE|REJECTED|SUSPENDED` | Không tạo `PENDING_VEHICLE` |
| Customer states | `PENDING_VERIFY|ACTIVE|SUSPENDED` | Chỉ query `role='CUSTOMER'` |
| Withdrawal states | `PENDING|PROCESSED|REJECTED|CANCELLED` | Không tạo/hiển thị `APPROVED|COMPLETED` |
| Pagination | Server-side cho cả bốn list | Legacy client-side Driver/Customer phải migrate |
| Search semantics | Case/accent-insensitive khi khả thi, trim, max 100 ký tự | Dùng bound parameters và index phù hợp |
| Bank search | Driver name, bank reference, exact account hash hoặc last4 | Không `ILIKE` plaintext/full encrypted account |
| Detail ownership | Spec #012 | List chỉ navigate, không mutate entity |
| List audit | `ADMIN_LIST_ACCESSED`, throttle 60 giây | Không ghi một audit cho mỗi poll 30 giây |
| Auto-refresh | Polling 30 giây, opt-in | Không WebSocket trong scope |
| Soft delete | Loại `deleted_at IS NOT NULL` mặc định | Không expose deleted entity qua list |

UI stubs hiện dùng order aliases `PENDING`, `ACCEPTED`, `DISPUTED`, client-side pagination cho
Driver/Customer và withdrawal placeholder. Implementation SHALL migrate về contract canonical
trên, không mở rộng DB CHECK bằng alias.

---

## Scope Summary

**In scope:**

1. `GET /api/admin/orders` — paginated list tất cả order.
2. `GET /api/admin/drivers` — paginated list tất cả Driver.
3. `GET /api/admin/customers` — paginated list tất cả Customer.
4. `GET /api/admin/withdrawals` — paginated list tất cả withdrawal.
5. Search theo field allowlist của từng entity.
6. Filter pills theo canonical status và date range nơi áp dụng.
7. Server-side sort theo column allowlist, stable secondary sort.
8. Server-side pagination `10|20|50|100` theo AC-15.
9. Click row/action “Xem chi tiết” sang route Spec #012.
10. Manual refresh và auto-refresh polling 30 giây opt-in.
11. Admin-only RBAC và throttled list-access audit.
12. Search/status/date indexes và query performance contract.
13. Vietnamese badges, VND/date format và Loading/Empty/Error states.
14. Migrate bốn Sprint 1 UI stubs sang backend contracts.

**Out of scope:**

1. Detail pages — Spec #012.
2. Edit, delete, suspend hoặc state mutation từ list.
3. Manager withdrawal processing — Spec #009 xác định Admin-only.
4. Bulk operations.
5. Export CSV/Excel — defer Sprint 6+.
6. Advanced multi-status/geographic filters.
7. Saved views hoặc shared filters.
8. Real-time WebSocket/SSE.
9. Full audit-log viewer — **đã tách sang Spec #025 Admin Audit Log Viewer** (đã build, V22). Ghi chú 2026-06-24.
10. Search engine ngoài PostgreSQL.

---

## User Stories

**P1 (CORE):**

**US1:** Là Admin, tôi xem tất cả order với pagination và status filter để monitor hoạt động
kinh doanh.

**US2:** Là Admin, tôi tìm order theo mã đơn, tên Customer hoặc tên Driver để xử lý nhanh một
trường hợp cụ thể.

**US3:** Là Admin, tôi xem tất cả Driver với filter trạng thái để theo dõi onboarding, hoạt động,
từ chối và đình chỉ.

**US4:** Là Admin, tôi tìm Driver theo tên, số điện thoại, email hoặc biển số xe.

**US5:** Là Admin, tôi xem tất cả Customer với filter trạng thái để monitor tài khoản và mức sử
dụng.

**US6:** Là Admin, tôi tìm Customer theo tên, số điện thoại hoặc email.

**US7:** Là Admin, tôi xem tất cả withdrawal với filter trạng thái để theo dõi dòng tiền ra.

**US8:** Là Admin, tôi click một row để mở đúng detail page thuộc Spec #012.

**P2:**

**US9:** Là Admin, tôi export danh sách đã lọc sang CSV — defer Sprint 6+.

**US10:** Là Admin, tôi bật auto-refresh 30 giây để monitor list mà không refresh thủ công.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Orders List (FR-001..FR-009)

**FR-001**  
WHEN authenticated Admin gọi
`GET /api/admin/orders?page=0&size=20&status=ALL&search=&date_from=&date_to=&sort=created_at,desc`,
THE system SHALL trả HTTP 200 với Spring `Page<OrderListItemDTO>`, chỉ gồm
`service_order.deleted_at IS NULL`.

Response SHALL có metadata:

```json
{
  "content": [],
  "totalElements": 0,
  "totalPages": 0,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true
}
```

**FR-002**  
WHEN order item được serialize, THE response SHALL gồm:

```json
{
  "id": "6bf5e878-52b0-4fb8-a9cb-8af517594e89",
  "order_code": "MH202606050001",
  "customer_name": "Nguyễn Thu Hà",
  "driver_name": "Nguyễn Văn Hùng",
  "vehicle_type": "TRUCK_1T",
  "pickup_district": "Ba Đình",
  "dropoff_district": "Cầu Giấy",
  "total_quote": 2400000,
  "status": "IN_PROGRESS",
  "created_at": "2026-06-05T02:15:00Z",
  "scheduled_at": "2026-06-05T03:00:00Z"
}
```

WHERE order chưa có Driver, `driver_name` SHALL là null và frontend SHALL hiển thị “Chưa phân
công”.

**FR-003**  
WHEN `status=ALL`, THE system SHALL không thêm status predicate; WHEN status thuộc
`PENDING_PAYMENT|CONFIRMED|ASSIGNED|IN_PROGRESS|AWAITING_FINAL_PAYMENT|COMPLETED|CANCELLED|IN_DISPUTE`,
SHALL lọc đúng giá trị; WHERE status khác allowlist, SHALL trả HTTP 422 `INVALID_STATUS_FILTER`.

**FR-004**  
WHEN `search` có giá trị sau trim, THE system SHALL match case-insensitive trên `order_code`,
Customer `full_name` hoặc Driver `full_name`; WHERE search rỗng, SHALL trả mọi order theo các
filter còn lại; search SHALL không match address hoặc internal notes.

**FR-005**  
WHEN `date_from|date_to` được truyền, THE system SHALL parse ISO date theo
`Asia/Ho_Chi_Minh`, convert thành UTC boundaries và filter trên `service_order.created_at`;
WHERE range invalid, lớn hơn 366 ngày hoặc `date_from > date_to`, SHALL trả HTTP 422
`INVALID_DATE_RANGE`.

**FR-006**  
WHEN `sort` được truyền, THE system SHALL chỉ cho phép
`created_at|total_quote|status|scheduled_at` với direction `asc|desc`, thêm secondary sort
`id` cùng direction; WHERE sort không hợp lệ, SHALL trả HTTP 422 `INVALID_SORT`; default SHALL
là `created_at,desc`.

**FR-007**  
WHEN orders page render, THE frontend SHALL hiển thị search box, date range, canonical status
filters, table columns `Mã đơn`, `Khách hàng`, `Tài xế`, `Tuyến`, `Tổng tiền`, `Trạng thái`,
`Ngày tạo`, `Hành động`; amount SHALL format VND và timestamp theo Việt Nam.

**FR-008**  
WHEN frontend render order status, THE system SHALL map:

| Status | Nhãn | Badge |
|--------|------|-------|
| `PENDING_PAYMENT` | Chờ thanh toán | amber |
| `CONFIRMED` | Đã xác nhận | blue |
| `ASSIGNED` | Đã phân công | blue |
| `IN_PROGRESS` | Đang giao | forest green |
| `AWAITING_FINAL_PAYMENT` | Chờ thanh toán cuối | amber |
| `COMPLETED` | Hoàn thành | green |
| `CANCELLED` | Đã hủy | red |
| `IN_DISPUTE` | Khiếu nại | orange |

Frontend SHALL không gửi alias `PENDING|ACCEPTED|DISPUTED`.

**FR-009**  
WHEN Admin click row hoặc “Xem chi tiết”, THE frontend SHALL navigate
`/admin/order-detail.html?id={orderId}`; WHILE API loading SHALL render skeleton; WHERE content
rỗng SHALL hiển thị “Không có đơn hàng nào”; WHERE API lỗi SHALL hiển thị “Không thể tải danh
sách đơn hàng” và “Thử lại”.

---

### Nhóm 2 — Drivers List (FR-010..FR-017)

**FR-010**  
WHEN Admin gọi
`GET /api/admin/drivers?page=0&size=20&status=ALL&search=&sort=created_at,desc`,
THE system SHALL trả Spring Page chỉ gồm `app_user.role='DRIVER'` và `deleted_at IS NULL`.

**FR-011**  
WHEN Driver item được serialize, THE response SHALL gồm:

```json
{
  "user_id": "9ac469f5-47d8-441f-99c0-b1c6941c8fb3",
  "full_name": "Nguyễn Văn Hùng",
  "email": "hung.nguyen@example.com",
  "phone": "+84912345678",
  "vehicle_type": "TRUCK_1T",
  "license_plate": "30H-456.78",
  "status": "ACTIVE",
  "average_rating": "4.80",
  "total_completed_orders": 120,
  "total_earnings": 85000000,
  "current_balance": 8200000,
  "created_at": "2026-05-20T02:15:00Z",
  "last_active_at": "2026-06-09T03:00:00Z"
}
```

Money SHALL là integer VND; rating SHALL serialize decimal string hoặc fixed precision number,
không Float-derived.

**FR-012**  
WHEN status filter thuộc
`ALL|ACTIVE|PENDING_VERIFY|PENDING_DOCUMENTS|PENDING_DEPOSIT|PENDING_APPROVAL|REJECTED|SUSPENDED`,
THE system SHALL filter tương ứng; WHERE filter là legacy `PENDING_VEHICLE` hoặc giá trị khác,
SHALL trả HTTP 422 và không map thành state mới.

**FR-013**  
WHEN Driver search có giá trị, THE system SHALL match case-insensitive trên `full_name`, `phone`,
`email` hoặc primary active vehicle `license_plate`; SHALL normalize phone và license plate
trước search nhưng SHALL không log raw search term.

**FR-014**  
WHEN Driver sort được truyền, THE system SHALL chỉ cho phép
`created_at|total_earnings|average_rating|total_completed_orders|last_active_at`; default
`created_at,desc`; sort SHALL stable bằng `app_user.id`.

**FR-015**  
WHEN drivers page render, THE frontend SHALL hiển thị search, status filters, table columns
`Họ tên`, `Email`, `SĐT`, `Xe/Biển số`, `Đơn hoàn thành`, `Thu nhập`, `Số dư`, `Đánh giá`,
`Trạng thái`, `Hành động`; row `PENDING_APPROVAL` SHALL có warning highlight không làm giảm
contrast.

**FR-016**  
WHEN Driver status render, THE frontend SHALL dùng nhãn:
`PENDING_VERIFY=Chờ xác thực`, `PENDING_DOCUMENTS=Chờ hồ sơ`,
`PENDING_DEPOSIT=Chờ đặt cọc`, `PENDING_APPROVAL=Chờ duyệt`, `ACTIVE=Đang hoạt động`,
`REJECTED=Đã từ chối`, `SUSPENDED=Bị đình chỉ`; badge SHALL dùng semantic color nhất quán.

**FR-017**  
WHEN Admin click Driver row/action, THE frontend SHALL navigate
`/admin/driver-detail.html?id={userId}`; WHILE loading SHALL render skeleton; WHERE content rỗng
SHALL hiển thị “Không có tài xế nào”; WHERE API lỗi SHALL hiển thị “Không thể tải danh sách tài
xế” và button retry.

---

### Nhóm 3 — Customers List (FR-018..FR-024)

**FR-018**  
WHEN Admin gọi
`GET /api/admin/customers?page=0&size=20&status=ALL&search=&sort=created_at,desc`,
THE system SHALL trả Spring Page chỉ gồm `app_user.role='CUSTOMER'` và `deleted_at IS NULL`.

**FR-019**  
WHEN Customer item được serialize, THE response SHALL gồm:

```json
{
  "user_id": "a27d4044-c5ac-43a1-bdf7-b20fe9ad00ba",
  "full_name": "Nguyễn Thu Hà",
  "email": "ha.nguyen@example.com",
  "phone": "+84986321456",
  "status": "ACTIVE",
  "total_orders": 8,
  "total_spent": 12500000,
  "wallet_balance": 700000,
  "created_at": "2026-05-18T08:30:00Z",
  "last_active_at": "2026-06-09T01:20:00Z",
  "email_verified": true
}
```

**FR-020**  
WHEN Customer status filter thuộc `ALL|ACTIVE|PENDING_VERIFY|SUSPENDED`, THE system SHALL lọc
tương ứng; WHERE filter khác allowlist, SHALL trả HTTP 422 `INVALID_STATUS_FILTER`.

**FR-021**  
WHEN Customer search có giá trị, THE system SHALL match case-insensitive trên `full_name`,
normalized `phone` hoặc lowercase `email`; SHALL không search address, password metadata hoặc
deleted users.

**FR-022**  
WHEN Customer sort được truyền, THE system SHALL chỉ cho phép
`created_at|total_orders|total_spent|last_active_at`; default `created_at,desc`; aggregate sort
SHALL không nhân chéo totals do JOIN.

**FR-023**  
WHEN customers page render, THE frontend SHALL hiển thị search, filters, columns `Họ tên`,
`Email`, `SĐT`, `Email xác thực`, `Số đơn`, `Tổng chi tiêu`, `Số dư ví`, `Ngày tham gia`,
`Trạng thái`, `Hành động`; email verification SHALL có text accessible ngoài icon.

**FR-024**  
WHEN Admin click Customer row/action, THE frontend SHALL navigate
`/admin/customer-detail.html?id={userId}`; WHILE loading SHALL render skeleton; WHERE content
rỗng SHALL hiển thị “Không có khách hàng nào”; WHERE API lỗi SHALL hiển thị
“Không thể tải danh sách khách hàng” và button retry.

---

### Nhóm 4 — Withdrawals List (FR-025..FR-031)

**FR-025**  
WHEN Admin gọi
`GET /api/admin/withdrawals?page=0&size=20&status=ALL&search=&date_from=&date_to=&sort=requested_at,desc`,
THE system SHALL trả Spring Page tất cả `withdrawal_request` visible theo Spec #009, không giới
hạn một Driver.

**FR-026**  
WHEN withdrawal item được serialize, THE response SHALL gồm:

```json
{
  "id": "6752feee-5c2b-4bd3-8f75-2c7f2737ace7",
  "driver_id": "9ac469f5-47d8-441f-99c0-b1c6941c8fb3",
  "driver_name": "Nguyễn Văn Hùng",
  "amount": 4500000,
  "bank_name": "Vietcombank",
  "bank_account_masked": "******7890",
  "status": "PROCESSED",
  "requested_at": "2026-06-05T01:45:00Z",
  "processed_at": "2026-06-05T04:10:00Z",
  "processor_name": "Trần Minh Anh",
  "bank_txn_ref_masked": "VCB-***021"
}
```

SHALL không trả full bank account hoặc unmasked bank reference.

**FR-027**  
WHEN status filter thuộc `ALL|PENDING|PROCESSED|REJECTED|CANCELLED`, THE system SHALL lọc đúng
canonical status; WHERE filter là `APPROVED|COMPLETED` hoặc giá trị khác, SHALL trả HTTP 422 và
frontend SHALL không hiển thị legacy badge.

**FR-028**  
WHEN withdrawal search có giá trị, THE system SHALL match Driver name hoặc bank transaction
reference; IF input là bốn chữ số, THEN MAY match account last4; IF input là full normalized
account number, THEN SHALL match deterministic secure hash/exact lookup; SHALL không dùng
`ILIKE` trên plaintext hoặc decrypt hàng loạt.

**FR-029**  
WHEN withdrawal date range được truyền, THE system SHALL filter trên `requested_at` theo UTC
boundaries từ ngày Việt Nam; WHERE range invalid hoặc lớn hơn 366 ngày, SHALL trả HTTP 422.

**FR-030**  
WHEN withdrawal sort được truyền, THE system SHALL chỉ cho phép
`requested_at|processed_at|amount|status`; default `requested_at,desc`; response SHALL dùng stable
secondary sort `id`.

**FR-031**  
WHEN Admin click withdrawal row/action, THE frontend SHALL navigate
`/admin/withdrawal-detail.html?id={withdrawalId}`; WHILE loading SHALL render skeleton; WHERE
content rỗng SHALL hiển thị “Không có yêu cầu rút tiền nào”; WHERE API lỗi SHALL hiển thị
“Không thể tải danh sách rút tiền” và retry.

---

### Nhóm 5 — Search Implementation (FR-032..FR-035)

**FR-032**  
WHEN Admin nhập search, THE frontend SHALL debounce 300 ms sau lần gõ cuối, reset `page=0`,
update query string và gửi một request mới; WHERE input tiếp tục thay đổi, SHALL cancel timer và
request cũ bằng `AbortController`.

**FR-033**  
WHEN backend nhận `search`, THE system SHALL trim, Unicode-normalize, giới hạn tối đa 100 ký tự
và dùng bound parameters; WHERE search vượt giới hạn hoặc chứa control characters không hợp lệ,
SHALL trả HTTP 422 `INVALID_SEARCH_TERM`; special SQL wildcard SHALL không gây injection.

**FR-034**  
WHEN search chạy trên tên/email/code/plate, THE system SHALL dùng PostgreSQL indexed strategy
phù hợp như normalized columns và `pg_trgm` GIN cho substring `ILIKE`; WHERE search rỗng, SHALL
không thêm search predicate để planner dùng status/date/sort index.

**FR-035**  
WHEN frontend render kết quả search, THE frontend MAY highlight matched text bằng escaped DOM
text; WHERE highlight không thể xác định an toàn, SHALL hiển thị text bình thường và SHALL không
dùng raw `innerHTML` từ API.

---

### Nhóm 6 — Auto-refresh + Audit (FR-036..FR-039)

**FR-036**  
WHEN Admin bật toggle “Tự động làm mới”, THE frontend SHALL fetch current list/query mỗi 30 giây
và hiển thị thời điểm cập nhật gần nhất; WHEN toggle tắt, SHALL clear interval; default SHALL là
tắt sau mỗi page load.

**FR-037**  
WHILE tab bị ẩn, request trước còn chạy, Admin đang scroll/mousedown, search input focused hoặc
action menu mở, THE frontend SHALL pause auto-refresh; WHEN tương tác kết thúc, SHALL chờ interval
kế tiếp, không refresh ngay và không reset scroll/page.

**FR-038**  
WHEN Admin list request thành công, THE system SHALL append audit event
`ADMIN_LIST_ACCESSED` gồm `admin_id`, `list_type`, normalized filter summary, result count,
request id và timestamp; audit SHALL throttle tối đa một event mỗi Admin/list/filter hash/60 giây
để polling không tạo spam.

**FR-039**  
WHEN row action menu mở, THE frontend SHALL hiển thị “Xem chi tiết” hoạt động; “Xem audit log”,
“Đình chỉ tài xế” và “Đình chỉ khách hàng” SHALL disabled/hidden với nhãn “Sắp ra mắt” vì thuộc
Spec #012 hoặc future; list SHALL không gọi mutation API.

---

### Nhóm 7 — RBAC + Performance (FR-040..FR-042)

**FR-040**  
WHEN bất kỳ endpoint spec này được gọi, THE system SHALL yêu cầu JWT hợp lệ và authoritative role
`ADMIN`; WHERE caller là `MANAGER|DRIVER|CUSTOMER`, SHALL trả HTTP 403 `FORBIDDEN`; WHERE thiếu
hoặc hết hạn JWT, SHALL trả HTTP 401 theo Spec #001.

**FR-041**  
WHEN pagination được xử lý, THE system SHALL chỉ chấp nhận `page >= 0` và
`size IN (10,20,50,100)`; default `page=0,size=20`; WHERE invalid, SHALL trả HTTP 422; every
query SHALL execute count query phù hợp và stable sort theo AC-15.

**FR-042**  
WHEN list query chạy, THE system SHALL select DTO projection, tránh N+1, dùng allowlisted sort,
bound filters và indexes; WHERE sort bị bỏ trống, orders/drivers/customers default
`created_at DESC, id DESC`, withdrawals default `requested_at DESC, id DESC`.

---

## Non-Functional Requirements

**NFR-001 — Standard list latency**  
Mỗi list page size 20 SHALL hoàn tất dưới 500 ms ở p95 với 100.000 primary records.

**NFR-002 — Search latency**  
Search SHALL hoàn tất dưới 800 ms ở p95 với 100.000 records và index đã warm.

**NFR-003 — Combined filters**  
Search + status + date range SHALL hoàn tất dưới một giây ở p95.

**NFR-004 — Sort performance**  
Sort theo allowlisted indexed columns SHALL hoàn tất dưới 500 ms ở p95 cho page size 20.

**NFR-005 — Pagination navigation**  
DB data query target dưới 300 ms ở p95; count query SHALL được đo riêng.

**NFR-006 — Auto-refresh UX**  
Background refresh SHALL không freeze UI, reset scroll hoặc flash toàn table.

**NFR-007 — UI states**  
Bốn trang SHALL có Loading/Empty/Error states đầy đủ theo AC-16.

**NFR-008 — Status consistency**  
Badge labels/colors SHALL nhất quán với domain specs và accessible contrast.

**NFR-009 — Privacy**  
Logs/audit SHALL không chứa raw search term nếu có phone/email/account/reference nhạy cảm.

**NFR-010 — Accessibility**  
Search, filters, sort, pagination, rows và action menus SHALL dùng được bằng keyboard/screen reader.

**NFR-011 — Responsive design**  
Bốn trang SHALL responsive mobile/tablet/desktop; wide tables SHALL scroll ngang có header rõ.

**NFR-012 — Observability**  
Mỗi endpoint SHALL emit duration, result count, error code và slow-query metric không chứa PII.

---

## API Endpoints Summary

| Method | Endpoint | Query parameters | Success | Auth |
|--------|----------|------------------|---------|------|
| GET | `/api/admin/orders` | `page,size,status,search,date_from,date_to,sort` | 200 `Page<OrderListItemDTO>` | Admin |
| GET | `/api/admin/drivers` | `page,size,status,search,sort` | 200 `Page<DriverListItemDTO>` | Admin |
| GET | `/api/admin/customers` | `page,size,status,search,sort` | 200 `Page<CustomerListItemDTO>` | Admin |
| GET | `/api/admin/withdrawals` | `page,size,status,search,date_from,date_to,sort` | 200 `Page<WithdrawalListItemDTO>` | Admin |

### Common Error Format

```json
{
  "timestamp": "2026-06-09T03:20:00Z",
  "status": 422,
  "error_code": "INVALID_STATUS_FILTER",
  "message": "Bộ lọc trạng thái không hợp lệ.",
  "path": "/api/admin/orders",
  "request_id": "01JY...",
  "details": [
    {
      "field": "status",
      "message": "Trạng thái lọc không được hỗ trợ."
    }
  ]
}
```

---

## Data Model

Spec này reuse `service_order`, `app_user`, `driver_profile`, `driver_vehicle`, `driver_wallet`,
`customer_wallet`, `withdrawal_request` và `audit_log`. Không tạo entity nghiệp vụ mới.

### Search Normalization

Implementation SHOULD dùng generated/maintained normalized columns để tránh gọi function trên
mọi row trong query:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS search_text TEXT;

ALTER TABLE driver_vehicle
    ADD COLUMN IF NOT EXISTS license_plate_normalized VARCHAR(20);

ALTER TABLE withdrawal_request
    ADD COLUMN IF NOT EXISTS bank_account_last4 CHAR(4),
    ADD COLUMN IF NOT EXISTS bank_account_lookup_hash CHAR(64);
```

`app_user.search_text` SHALL được cập nhật từ normalized `full_name`, lowercase email và
normalized phone trong application/service hoặc trigger được test. Lookup hash SHALL dùng
server-side keyed hash/HMAC với secret từ environment; không dùng raw SHA-256 cho account number.

### Orders Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_service_order_admin_created
    ON service_order (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_order_admin_status_created
    ON service_order (status, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_order_admin_scheduled
    ON service_order (scheduled_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_service_order_code_trgm
    ON service_order USING gin (order_code gin_trgm_ops)
    WHERE deleted_at IS NULL;
```

### User & Vehicle Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_app_user_admin_role_status_created
    ON app_user (role, status, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_app_user_search_text_trgm
    ON app_user USING gin (search_text gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_driver_vehicle_plate_normalized
    ON driver_vehicle (license_plate_normalized, driver_id)
    WHERE status IN ('PENDING_REVIEW', 'APPROVED');
```

### Withdrawal Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_withdrawal_admin_requested
    ON withdrawal_request (requested_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_withdrawal_admin_status_requested
    ON withdrawal_request (status, requested_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_withdrawal_bank_txn_ref_trgm
    ON withdrawal_request USING gin (bank_txn_ref gin_trgm_ops)
    WHERE bank_txn_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_withdrawal_account_last4
    ON withdrawal_request (bank_account_last4);

CREATE INDEX IF NOT EXISTS idx_withdrawal_account_lookup_hash
    ON withdrawal_request (bank_account_lookup_hash);
```

Mọi migration dùng Flyway; status giữ `VARCHAR + CHECK`, không PostgreSQL ENUM theo AC-14.

---

## Query Contracts

### Orders Projection

```sql
SELECT so.id,
       so.order_code,
       customer.full_name AS customer_name,
       driver.full_name AS driver_name,
       so.vehicle_type,
       so.pickup_district,
       so.dropoff_district,
       so.total_quote,
       so.status,
       so.created_at,
       so.scheduled_at
FROM service_order so
JOIN app_user customer
  ON customer.id = so.customer_id
LEFT JOIN app_user driver
  ON driver.id = so.driver_id
WHERE so.deleted_at IS NULL
  AND (:status_is_all OR so.status = :status)
  AND (:search_is_empty
       OR so.order_code ILIKE :search_pattern
       OR customer.search_text ILIKE :search_pattern
       OR driver.search_text ILIKE :search_pattern)
ORDER BY so.created_at DESC, so.id DESC
LIMIT :size OFFSET :offset;
```

`:status` và sort clause SHALL build từ server allowlist. Search pattern SHALL bind parameter.

### Driver Aggregates

Driver totals SHOULD được pre-aggregated hoặc query bằng isolated subqueries/materialized reporting
view để tránh join multiplication giữa orders, vehicles và transactions.

```sql
SELECT u.id,
       u.full_name,
       u.email,
       u.phone,
       u.status,
       v.vehicle_type,
       v.license_plate,
       p.average_rating,
       p.total_completed_orders,
       w.total_earnings,
       w.balance AS current_balance,
       u.created_at,
       p.last_active_at
FROM app_user u
LEFT JOIN driver_profile p ON p.user_id = u.id
LEFT JOIN driver_wallet w ON w.driver_id = u.id
LEFT JOIN driver_vehicle v
  ON v.driver_id = u.id
 AND v.is_primary = TRUE
WHERE u.role = 'DRIVER'
  AND u.deleted_at IS NULL;
```

### Stable Pagination Rules

1. Mọi sort có secondary `id`.
2. Filter/search change reset page về zero.
3. Page out of range trả empty content và valid metadata; frontend MAY về last valid page.
4. Count query dùng cùng predicates nhưng bỏ JOIN không cần thiết.
5. API không trả toàn bộ rows để frontend tự phân trang.

---

## State Machine

Spec này không tạo hoặc chuyển state mới; chỉ display canonical states từ domain owners:

```text
Order:
PENDING_PAYMENT → CONFIRMED → ASSIGNED → IN_PROGRESS
→ AWAITING_FINAL_PAYMENT → COMPLETED
Any allowed branch → CANCELLED or IN_DISPUTE

Driver:
PENDING_VERIFY → PENDING_DOCUMENTS → PENDING_DEPOSIT
→ PENDING_APPROVAL → ACTIVE
PENDING_APPROVAL → REJECTED
ACTIVE → SUSPENDED

Customer:
PENDING_VERIFY → ACTIVE → SUSPENDED

Withdrawal:
PENDING → PROCESSED | REJECTED | CANCELLED
```

Rules:

1. List pages read-only.
2. Aliases không được persist hoặc gửi làm filter.
3. Mutation action thuộc domain/detail specs.
4. Unknown status SHALL hiển thị neutral “Không xác định” và emit data-integrity metric.

---

## Filter & Sort Matrix

| List | Status filters | Search fields | Date field | Sort fields |
|------|----------------|---------------|------------|-------------|
| Orders | 8 canonical + ALL | code, Customer name, Driver name | `created_at` | created, total, status, scheduled |
| Drivers | 7 canonical + ALL | name, phone, email, plate | none | created, earnings, rating, completed, active |
| Customers | 3 canonical + ALL | name, phone, email | none | created, orders, spent, active |
| Withdrawals | 4 canonical + ALL | Driver name, ref, account exact/last4 | `requested_at` | requested, processed, amount, status |

Backend SHALL reject fields ngoài matrix; frontend SHALL serialize filters trong URL để
reload/back/forward giữ state.

---

## Error Matrix

| Scenario | HTTP | `error_code` | Message |
|----------|------|--------------|---------|
| Không có JWT | 401 | `AUTHENTICATION_REQUIRED` | Phiên đăng nhập không hợp lệ |
| Không phải Admin | 403 | `FORBIDDEN` | Bạn không có quyền xem danh sách này |
| Status invalid | 422 | `INVALID_STATUS_FILTER` | Bộ lọc trạng thái không hợp lệ |
| Search invalid | 422 | `INVALID_SEARCH_TERM` | Từ khóa tìm kiếm không hợp lệ |
| Date range invalid | 422 | `INVALID_DATE_RANGE` | Khoảng ngày không hợp lệ |
| Sort invalid | 422 | `INVALID_SORT` | Cách sắp xếp không hợp lệ |
| Page/size invalid | 422 | `INVALID_PAGINATION` | Tham số phân trang không hợp lệ |
| Query timeout | 503 | `LIST_QUERY_TIMEOUT` | Không thể tải dữ liệu lúc này |
| Audit write fail | 500 | `AUDIT_WRITE_FAILED` | Không thể ghi nhận truy cập |
| Unexpected error | 500 | `INTERNAL_ERROR` | Không thể tải dữ liệu |

List access audit failure SHOULD fail closed theo yêu cầu audit của feature này; polling retry
SHALL dùng backoff để không tạo vòng lỗi.

---

## Frontend Screen Contract

### Shared List Layout

Mỗi trang SHALL có:

1. Page title và mô tả ngắn.
2. Search box có clear button.
3. Filter pills canonical.
4. Optional date range cho Orders/Withdrawals.
5. Sort control/header.
6. Manual refresh và auto-refresh toggle.
7. Result count và last-updated label.
8. Responsive data table.
9. Row click và three-dot action.
10. Server-side pagination.
11. Loading/Empty/Error states.

### `frontend/pages/admin/orders.html`

Legacy filter buttons `PENDING|ACCEPTED|DISPUTED` SHALL đổi sang canonical. Table SHALL gộp
pickup/dropoff thành tuyến dễ đọc, thêm search/date/action và navigate detail.

### `frontend/pages/admin/drivers.html`

Legacy client-side pagination SHALL đổi server-side. Trang SHALL thêm search, đầy đủ onboarding
statuses, current balance và detail navigation. `PENDING_APPROVAL` giữ warning highlight.

### `frontend/pages/admin/customers.html`

Legacy client-side pagination SHALL đổi server-side. Trang SHALL thêm search, total spent,
wallet balance và detail navigation; verification icon cần accessible label.

### `frontend/pages/admin/withdrawals.html`

Placeholder “Tính năng đang phát triển” SHALL được thay bằng list contract này. Full bank account
không xuất hiện; status labels theo Spec #009.

---

## Security & Privacy

1. Chỉ Admin gọi được bốn endpoint.
2. Search/filter/sort dùng allowlist và bound parameters.
3. Raw search term có PII không ghi vào logs/audit.
4. Phone/email chỉ hiển thị cho Admin authenticated.
5. Withdrawal account luôn masked trong response.
6. Full bank account search dùng exact keyed hash hoặc last4, không decrypt scan.
7. Soft-deleted users/orders bị loại mặc định.
8. Row action không có mutation trong scope.
9. Audit metadata không chứa result rows hoặc PII.
10. Frontend escape mọi API text, không raw `innerHTML`.
11. Auto-refresh dừng sau logout/token expiry.
12. Rate limit list/search để chống enumeration và expensive-query abuse.

---

## Observability

| Metric | Type | Labels |
|--------|------|--------|
| `admin_list_request_total` | Counter | `list_type`, `result` |
| `admin_list_duration_seconds` | Histogram | `list_type`, `has_search`, `has_filter` |
| `admin_list_result_count` | Histogram | `list_type` |
| `admin_list_query_timeout_total` | Counter | `list_type` |
| `admin_list_auto_refresh_total` | Counter | `list_type`, `result` |
| `admin_list_invalid_filter_total` | Counter | `list_type`, `field` |

Alerts: p95 trên target 15 phút; query timeout spike; non-Admin access spike; audit failure;
unknown canonical status; sequential scans trên bảng lớn sau rollout.

---

## Acceptance Criteria

**AC1 — Four list pages**  
GIVEN Admin token và dữ liệu, WHEN mở bốn pages, THEN mỗi list render page đầu dưới một giây,
đúng columns và result count.

**AC2 — Search debounce**  
GIVEN Admin gõ liên tục năm ký tự, WHEN chưa dừng 300 ms, THEN không gọi API mỗi keystroke; sau
khi dừng chỉ request mới nhất được render.

**AC3 — Status filters**  
GIVEN canonical statuses, WHEN chọn từng filter pill, THEN API trả đúng records và URL giữ filter;
legacy aliases bị từ chối.

**AC4 — Pagination**  
GIVEN hơn 100 records, WHEN dùng next/previous/page/size, THEN metadata và rows đúng, stable,
không duplicate hoặc bỏ record do timestamp bằng nhau.

**AC5 — Detail navigation**  
GIVEN một row bất kỳ, WHEN click row hoặc “Xem chi tiết”, THEN navigate đúng detail route/id của
Spec #012.

**AC6 — Status badges**  
GIVEN records ở mọi canonical status, WHEN render, THEN nhãn tiếng Việt, màu semantic và
accessible text đúng.

**AC7 — Empty/Loading/Error**  
GIVEN DB rỗng, network chậm hoặc API fail, WHEN mở list, THEN hiển thị đúng message tiếng Việt,
skeleton và retry theo AC-16.

**AC8 — Auto-refresh**  
GIVEN toggle bật, WHEN trang idle/visible, THEN refresh mỗi 30 giây; khi tương tác/tab hidden thì
pause, không reset page/scroll và không tạo audit spam.

**AC9 — RBAC and privacy**  
GIVEN Admin/Manager/Driver/Customer token, WHEN gọi bốn endpoints, THEN chỉ Admin thành công;
withdrawal response luôn masked.

**AC10 — Search performance and safety**  
GIVEN 100.000 records và search/filter hợp lệ hoặc SQL payload, WHEN gọi API, THEN query dùng
bound parameters/index, hợp lệ dưới target và payload không gây injection.

**AC11 — Canonical states**  
GIVEN legacy UI stubs, WHEN implementation hoàn tất, THEN không gửi/hiển thị order alias hoặc
withdrawal legacy states.

**AC12 — Vietnamese UX**  
GIVEN bốn pages ở mọi state, WHEN render, THEN toàn bộ user-facing text có dấu tiếng Việt, money
VND và date/time Việt Nam.

---

## Edge Cases & Error Handling

### EC-01 — Search chứa SQL/wildcard payload

Expected: bound parameter và escaped wildcard policy; không injection hoặc SQL error leakage.

### EC-02 — Search chỉ có whitespace

Expected: trim thành empty, trả list theo filters còn lại, reset page zero.

### EC-03 — Search có dấu/không dấu

Expected: normalized strategy trả kết quả nhất quán theo implementation đã chọn; không sai Unicode.

### EC-04 — Database không có record

Expected: HTTP 200 empty Page; đúng empty message cho từng list.

### EC-05 — Filter combination không có kết quả

Expected: giữ search/filter controls, hiển thị empty filtered state và action “Xóa bộ lọc”.

### EC-06 — Date range from lớn hơn to

Expected: HTTP 422 field errors; frontend giữ input và không render stale result như thành công.

### EC-07 — Date range qua DST/timezone boundary

Expected: convert từ Asia/Ho_Chi_Minh sang UTC chính xác, không bỏ record cuối ngày.

### EC-08 — Page out of range sau filter

Expected: response empty valid metadata; frontend reset về page zero hoặc last valid page một lần.

### EC-09 — User navigate away khi fetch

Expected: AbortController cancel request; response cũ không mutate DOM trang mới.

### EC-10 — Response cũ về sau response mới

Expected: request sequence guard bỏ response cũ; filters hiện tại không bị overwrite.

### EC-11 — Network chậm quá 10 giây

Expected: timeout/cancel, error state và retry; auto-refresh dùng backoff.

### EC-12 — Auto-refresh khi search focused

Expected: pause refresh đến interval kế tiếp sau blur, không xóa text đang nhập.

### EC-13 — Auto-refresh khi action menu mở

Expected: pause để menu/row không biến mất giữa thao tác.

### EC-14 — Entity đổi trạng thái giữa hai page

Expected: stable query tại từng request; refresh phản ánh state mới, không mutation từ list.

### EC-15 — Order chưa có Driver

Expected: `driver_name=null`, hiển thị “Chưa phân công”, search Driver name không match.

### EC-16 — Driver có nhiều vehicle

Expected: list dùng primary active/onboarding vehicle canonical; không duplicate Driver row.

### EC-17 — Aggregate JOIN nhân chéo

Expected: totals tính từ isolated aggregates; tests phát hiện total orders/spent/earnings sai.

### EC-18 — Withdrawal bank reference null

Expected: masked reference null/“Chưa có”; search reference không fail.

### EC-19 — Full bank account search

Expected: exact keyed-hash lookup; response vẫn masked, logs/audit không chứa account.

### EC-20 — Audit throttle với polling

Expected: nhiều poll trong 60 giây cùng filter chỉ tạo tối đa một audit event.

---

## Test Cases

### TC-001 — Orders List Contract

**Type:** Integration  
**Given:** Orders ở tám canonical statuses.  
**When:** Admin gọi orders list/filter/search/date/sort.  
**Then:** Page, DTO, status results và stable order đúng; aliases 422.

### TC-002 — Drivers Search And Aggregates

**Type:** Integration  
**Given:** Drivers có nhiều orders/vehicles/transactions.  
**When:** Search name/phone/email/plate và sort aggregates.  
**Then:** Một row mỗi Driver, totals không nhân chéo, primary vehicle đúng.

### TC-003 — Customers List Contract

**Type:** Integration  
**Given:** Active, pending verify, suspended và deleted Customer.  
**When:** Admin filter/search/sort.  
**Then:** Đúng role/status, deleted excluded, totals và verification đúng.

### TC-004 — Withdrawals Privacy

**Type:** Security/Integration  
**Given:** Withdrawals ở bốn canonical statuses.  
**When:** Admin list/search Driver/ref/last4/full account.  
**Then:** Match đúng, response luôn masked, legacy status 422.

### TC-005 — Pagination Stability

**Type:** Integration  
**Given:** 250 records có nhiều timestamp giống nhau.  
**When:** Navigate mọi page và đổi size.  
**Then:** Không duplicate/missing, secondary id sort đúng.

### TC-006 — Debounce And Stale Response

**Type:** Frontend  
**Given:** Admin gõ nhanh và network trả response đảo thứ tự.  
**When:** Search chạy.  
**Then:** Chỉ request mới nhất render; page reset zero.

### TC-007 — Auto-refresh Pause

**Type:** Frontend  
**Given:** Toggle bật.  
**When:** Tab hidden, input focused, scrolling hoặc menu mở.  
**Then:** Poll pause; resume interval sau tương tác; scroll/page giữ nguyên.

### TC-008 — RBAC Matrix

**Type:** Security  
**Given:** Admin, Manager, Driver, Customer và anonymous.  
**When:** Mỗi actor gọi bốn endpoints.  
**Then:** Admin 200; authenticated non-Admin 403; anonymous 401.

### TC-009 — Search Injection And Validation

**Type:** Security/Contract  
**Given:** SQL payload, control chars, search dài, invalid sort/date/page.  
**When:** Gọi APIs.  
**Then:** Bound query hoặc 422 structured error; không SQL/stack leakage.

### TC-010 — Loading Empty Error States

**Type:** Frontend  
**Given:** Slow API, empty content và 503.  
**When:** Mở từng list.  
**Then:** Skeleton, entity-specific empty text, retry và filters giữ nguyên.

### TC-011 — Audit Throttle

**Type:** Integration  
**Given:** Auto-refresh gọi cùng list/filter bốn lần trong 60 giây.  
**When:** Requests thành công.  
**Then:** Tối đa một `ADMIN_LIST_ACCESSED`; filter khác tạo audit riêng.

### TC-012 — Performance With 100k Rows

**Type:** Performance  
**Given:** 100.000 rows/entity và indexes.  
**When:** Chạy list/search/filter/sort workload.  
**Then:** p95 đạt NFR, query plan không sequential scan không chủ đích.

---

## Required Automated Test Layers

1. Unit tests cho status/sort/search allowlists và normalization.
2. Integration tests cho bốn Page DTO queries và RBAC.
3. PostgreSQL query-plan/performance tests cho indexes.
4. Security tests cho SQL injection, PII log redaction và bank lookup.
5. Frontend tests cho debounce, cancellation, filters, pagination và polling.
6. Accessibility tests cho keyboard/table/action menu.
7. Contract tests với Specs #003/#005/#006/#009/#012.
8. CORE coverage tối thiểu 70% theo ES-05.

---

## Migration & Rollout Plan

1. Xác nhận canonical statuses từ domain specs.
2. Tạo normalized search columns và indexes bằng Flyway.
3. Backfill `app_user.search_text`, plate normalized và withdrawal lookup fields.
4. Chạy `ANALYZE` và benchmark 100.000-row fixtures.
5. Deploy four read APIs sau feature flag.
6. Migrate Orders aliases và add search/date/sort.
7. Migrate Driver/Customer từ client-side sang server-side pagination.
8. Thay Withdrawal placeholder bằng list.
9. Bật detail links khi Spec #012 routes sẵn sàng.
10. Bật auto-refresh/audit throttle và monitor slow queries.

Legacy filter query strings SHALL map ở frontend redirect một lần hoặc bị từ chối rõ ràng; backend
không persist alias.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-10 | Chỉ Admin; role khác 403 |
| HR-13 | List access audit có throttle, append-only |
| HR-19 | Bốn pages dùng Move_home forest green/amber/Be Vietnam Pro |
| HR-20 | Mọi user-facing text có dấu tiếng Việt |
| HR-21 | Reuse `app_user`, `service_order`, không reserved names |
| AC-08 | Money DTO là VND integer/BigDecimal scale 0 |
| AC-14 | Display canonical VARCHAR+CHECK statuses, không ENUM mới |
| AC-15 | Cả bốn list dùng server-side pagination |
| AC-16 | Loading/Empty/Error mandatory |
| ES-03 | Query validation trả HTTP 422 |
| ES-04 | Common structured error format |
| ES-05 | CORE có integration/security/performance tests |

