# Feature Specification: Manager Driver Approval

**Feature Branch:** `008-manager-driver-approval`  
**Feature Number:** #8 of 30 — CORE (driver supply gatekeeping)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 5

**CONTEXT.md reference:** v2.0 §Driver Onboarding, §RBAC, feature #3 Manager duyệt Driver  
**Constitution reference:** v1.3.0 — HR-05, HR-10, HR-11, HR-13, HR-19, HR-20,
HR-21, AC-07, AC-08, AC-09, AC-10, AC-14, AC-15, AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Manager screens 5.2, 5.3, 5.4  
**Related specs:** Spec #001 Auth/RBAC; Spec #005 Driver Onboarding; Spec #006 Driver Workflow

---

## Goals

Manager là gatekeeper của nguồn cung Driver trong marketplace Move_home. Feature này cho phép
Manager xử lý hồ sơ đã hoàn tất onboarding và đang ở trạng thái `PENDING_APPROVAL`: xem danh
sách ưu tiên hồ sơ chờ lâu nhất, kiểm tra thông tin cá nhân, giấy phép lái xe, đăng ký xe, ảnh
thực tế của xe và bằng chứng đặt cọc 3.000.000 VND; sau đó đưa ra quyết định approve hoặc reject
có audit trail đầy đủ.

Quyết định approve chuyển `app_user.status` từ `PENDING_APPROVAL` sang `ACTIVE`, đánh dấu tài
liệu và xe đã được duyệt, gửi email tiếng Việt và cho phép Driver bắt đầu workflow vận hành.
Quyết định reject bắt buộc có lý do rõ ràng, chuyển trạng thái sang `REJECTED`, giữ nguyên khoản
cọc đã thanh toán và cho phép Driver sửa giấy tờ rồi nộp lại theo Spec #005. Manager có thể xem
lịch sử từ chối để nhận diện hồ sơ trùng lặp hoặc mẫu gian lận.

Feature phải bảo đảm chỉ một Manager thắng khi hai người xử lý đồng thời, mọi state transition
được kiểm tra theo HR-05 và ghi audit theo HR-13. UI phải dùng Move_home forest green
`#1B4D3E`, amber `#F5A623`, Be Vietnam Pro, tiếng Việt có dấu và đủ Empty/Loading/Error states.
SLA nghiệp vụ là hồ sơ được xử lý trong 1-3 ngày làm việc kể từ khi VNPay xác nhận đặt cọc.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec #001 → Spec #005 →
> spec này → `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Approval role | Chỉ `MANAGER` được duyệt Driver onboarding | `ADMIN` nhận HTTP 403 dù prompt cũ cho phép Admin |
| Lifecycle owner | `app_user.status` | Không tạo hoặc update `driver_profile.status` |
| Required documents | GPLX front/back, đăng ký xe front, ảnh xe front/rear/side | Không yêu cầu CCCD hoặc selfie |
| Vehicle model | Driver có nhiều xe; onboarding cần một primary vehicle | Detail chọn xe onboarding/primary để review |
| Deposit source | `driver_deposit.status='COMPLETED'`, amount 3.000.000 VND | Không chỉ tin `driver_profile.deposit_amount` |
| Reject result | `REJECTED` cho phép re-submit | Không coi `REJECTED` là terminal |
| Deposit after reject | Giữ cọc để Driver nộp lại | Không tự tạo refund khi reject |
| Decision history | Append-only `audit_log` | Không tạo bảng approval riêng trong spec này |
| Document delivery | Signed Cloudinary URL TTL tối đa 1 giờ | Không expose raw private URL/public id |
| Concurrent decision | Lock `app_user` khi approve/reject | Một decision commit; decision sau nhận 409 |

Các điểm trên là bắt buộc. Khi UI stub, migration cũ hoặc prompt implementation mâu thuẫn,
implementation SHALL theo bảng resolution này và tạo migration tương thích thay vì duplicate
nguồn dữ liệu.

---

## Scope Summary

**In scope:**

1. `GET /api/manager/drivers/pending-approval` — danh sách hồ sơ chờ duyệt.
2. `GET /api/manager/drivers/{driverId}` — chi tiết tổng hợp để review.
3. `GET /api/manager/drivers/{driverId}/documents` — signed URLs cho image viewer.
4. `POST /api/manager/drivers/{driverId}/approve` — approve transaction-safe.
5. `POST /api/manager/drivers/{driverId}/reject` — reject với lý do bắt buộc.
6. `GET /api/manager/drivers/rejected` — lịch sử từ chối có search/filter/page.
7. Validation bộ giấy tờ, primary vehicle và cọc trước decision.
8. Pessimistic lock/idempotency guard cho concurrent decisions.
9. Audit log và email tiếng Việt cho mọi decision.
10. Ba màn Manager: pending list, detail, rejected history.
11. Metrics/SLA cho queue và decision outcomes.
12. Flyway indexes/profile extensions cần thiết.

**Out of scope:**

1. Driver sửa và nộp lại giấy tờ sau reject — Spec #005 extension.
2. OCR, face matching, fraud-scoring tự động.
3. Manager tạo/sửa/xóa tài liệu hoặc thông tin xe của Driver.
4. Refund cọc khi Driver bị reject.
5. Suspend Driver đã `ACTIVE`.
6. Admin override quyết định Manager.
7. Workflow `NEEDS_REVIEW` riêng; Manager dùng note nội bộ ngoài quyết định nếu cần.
8. Driver daily workflow sau khi được approve.

---

## User Stories

**P1 (CORE):**

**US1:** Là Manager, tôi xem danh sách Driver `PENDING_APPROVAL`, tổng số hồ sơ và hồ sơ chờ
lâu nhất để ưu tiên xử lý đúng SLA.

**US2:** Là Manager, tôi mở một Driver để kiểm tra thông tin cá nhân, GPLX, đăng ký xe, ảnh xe,
thông tin phương tiện, khoản cọc và timeline onboarding.

**US3:** Là Manager, tôi approve Driver đã đủ điều kiện để tài khoản chuyển sang `ACTIVE` và
Driver có thể bắt đầu nhận công việc.

**US4:** Là Manager, tôi reject Driver với lý do tiếng Việt rõ ràng để Driver biết nội dung cần
sửa trước khi nộp lại.

**US5:** Là Manager, tôi xem và tìm kiếm lịch sử Driver bị reject để nhận diện hồ sơ trùng lặp
và theo dõi chất lượng quyết định.

**P2:**

**US6:** Là Manager, tôi xem lightbox có zoom/rotate để đọc tài liệu rõ hơn mà không rời detail.

**US7:** Là Manager, tôi nhận bản tổng hợp queue mỗi sáng khi còn hồ sơ quá SLA.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Pending Approval List (FR-001..FR-007)

**FR-001**
WHEN authenticated Manager gọi
`GET /api/manager/drivers/pending-approval?page=0&size=20&age=ALL`, THE system SHALL trả HTTP 200
với Spring Page gồm các Driver `role='DRIVER'`, `status='PENDING_APPROVAL'`,
`deleted_at IS NULL`, sort `deposit_paid_at ASC, id ASC`.

Response mỗi item SHALL có:

```json
{
  "driver_id": "6fd084a3-72ae-40bd-94ae-2fbde661a760",
  "full_name": "Nguyễn Văn Hùng",
  "email": "hung.nguyen@example.com",
  "phone": "+84912345678",
  "vehicle_type": "TRUCK_1T",
  "vehicle_label": "Xe tải 1 tấn",
  "license_plate": "30H-456.78",
  "license_class": "C",
  "deposit_amount": 3000000,
  "deposit_status": "COMPLETED",
  "submitted_at": "2026-06-04T02:30:00Z",
  "days_waiting": 2,
  "review_ready": true,
  "missing_items": []
}
```

**FR-002**
WHEN list query được thực thi, THE system SHALL tính `submitted_at` từ thời điểm cọc completed
hoặc audit event `DRIVER_DEPOSIT_COMPLETED`; `days_waiting` SHALL tính theo ngày lịch UTC và
SHALL không lấy `app_user.created_at` làm thời điểm gửi duyệt.

**FR-003**
WHEN query có `age=NEW`, THE system SHALL lọc `submitted_at >= NOW() - INTERVAL '3 days'`;
WHEN `age=OVER_SLA`, SHALL lọc `submitted_at < NOW() - INTERVAL '3 days'`; WHERE `age` ngoài
`ALL|NEW|OVER_SLA`, SHALL trả HTTP 422 code `INVALID_FILTER`.

**FR-004**
WHEN Manager load pending page, THE system SHALL trả thêm metadata:

```json
{
  "pending_total": 12,
  "review_ready_total": 10,
  "over_sla_total": 3,
  "oldest_waiting_days": 5,
  "content": [],
  "totalElements": 12,
  "totalPages": 1,
  "number": 0,
  "size": 20,
  "first": true,
  "last": true
}
```

**FR-005**
WHEN list có dữ liệu, THE frontend SHALL render table columns `Họ tên`, `Email`, `Số điện thoại`,
`Loại xe`, `Biển số`, `Ngày gửi`, `Số ngày chờ`, `Trạng thái`, `Thao tác`; rows quá ba ngày SHALL
dùng amber warning marker và rows `review_ready=false` SHALL hiển thị “Thiếu thông tin”.

**FR-006**
WHILE API đang tải, THE frontend SHALL render skeleton table; WHERE response content rỗng,
SHALL hiển thị “Không có tài xế nào đang chờ duyệt”; WHERE API lỗi, SHALL hiển thị
“Không thể tải danh sách tài xế” và button “Thử lại” theo AC-16.

**FR-007**
WHEN `totalElements > size`, THE frontend SHALL render server-side pagination với page numbers,
ellipsis, Previous/Next disabled states, selector `10|20|50|100` và text
“Hiển thị X-Y trong Z tài xế”; backend SHALL cap `size` ở 100 theo AC-15.

---

### Nhóm 2 — Driver Detail (FR-008..FR-015)

**FR-008**
WHEN Manager gọi `GET /api/manager/drivers/{driverId}`, THE system SHALL trả HTTP 200 chỉ khi
`driverId` tồn tại, role là `DRIVER`, không soft-deleted và status thuộc
`PENDING_APPROVAL|ACTIVE|REJECTED`; WHERE không tồn tại, SHALL trả HTTP 404 code
`DRIVER_NOT_FOUND`.

**FR-009**
WHEN detail được trả, THE response SHALL chứa năm section:

```json
{
  "profile": {
    "driver_id": "6fd084a3-72ae-40bd-94ae-2fbde661a760",
    "full_name": "Nguyễn Văn Hùng",
    "email": "hung.nguyen@example.com",
    "phone": "+84912345678",
    "date_of_birth": "1994-08-12",
    "address": "Hà Nội",
    "operating_districts": ["Cầu Giấy", "Nam Từ Liêm"],
    "status": "PENDING_APPROVAL",
    "email_verified": true
  },
  "documents_summary": {
    "required_count": 6,
    "submitted_count": 6,
    "missing_items": []
  },
  "vehicle": {},
  "deposit": {},
  "onboarding_timeline": [],
  "decision_eligibility": {}
}
```

**FR-010**
WHEN detail service lấy bộ giấy tờ, THE system SHALL kiểm tra đúng sáu cặp canonical:
`DRIVING_LICENSE/FRONT`, `DRIVING_LICENSE/BACK`, `VEHICLE_REGISTRATION/FRONT`,
`VEHICLE_PHOTO/FRONT`, `VEHICLE_PHOTO/REAR`, `VEHICLE_PHOTO/SIDE`; SHALL không yêu cầu CCCD
hoặc selfie.

**FR-011**
WHEN detail service lấy vehicle, THE system SHALL chọn một `driver_vehicle` onboarding primary
với status `PENDING_REVIEW`; IF có nhiều xe cùng được đánh dấu primary hoặc không có xe,
THEN `decision_eligibility.can_approve=false` và thêm reason `INVALID_PRIMARY_VEHICLE`.

**FR-012**
WHEN detail service lấy deposit, THE system SHALL trả `amount`, `status`, `paid_at`,
`vnpay_txn_ref_masked`; `vnpay_txn_ref_masked` chỉ lộ bốn ký tự cuối và approval eligibility
SHALL yêu cầu `status='COMPLETED'` cùng `amount=3000000`.

**FR-013**
WHEN detail service xây timeline, THE system SHALL map audit events
`DRIVER_REGISTERED`, `DRIVER_EMAIL_VERIFIED`, `DRIVER_DOCUMENTS_SUBMITTED`,
`DRIVER_VEHICLE_SUBMITTED`, `DRIVER_DEPOSIT_COMPLETED`, `DRIVER_APPROVED`,
`DRIVER_REJECTED` thành nhãn tiếng Việt và sort `occurred_at ASC, id ASC`.

**FR-014**
WHEN status là `PENDING_APPROVAL`, THE frontend SHALL hiển thị buttons “Duyệt hồ sơ” và
“Từ chối”; WHEN status là `ACTIVE|REJECTED`, THE frontend SHALL disable decision buttons và
hiển thị decision hiện tại, người xử lý, thời gian và lý do nếu có.

**FR-015**
WHERE bất kỳ section detail lỗi hoặc thiếu, THE frontend SHALL không suy đoán eligibility;
SHALL hiển thị section-level error “Không thể tải dữ liệu” và disable approve cho đến khi
Manager reload thành công toàn bộ eligibility.

---

### Nhóm 3 — Approve Driver (FR-016..FR-021)

**FR-016**
WHEN Manager click “Duyệt hồ sơ”, THE frontend SHALL mở confirm modal hiển thị họ tên Driver,
biển số, thông báo “Tài xế sẽ được kích hoạt và có thể tham gia vận hành”; chỉ submit một lần
sau khi Manager click “Xác nhận duyệt”.

**FR-017**
WHEN Manager gọi `POST /api/manager/drivers/{driverId}/approve` với body
`{"decision_note":"Hồ sơ đầy đủ, thông tin phương tiện khớp."}`, THE backend SHALL bắt đầu DB
transaction, lock row `app_user` bằng `SELECT ... FOR UPDATE`, verify actor role `MANAGER` và
verify target status đúng `PENDING_APPROVAL`.

**FR-018**
WHEN target đang `PENDING_APPROVAL`, THE system SHALL re-check trong transaction:

1. Email đã verify.
2. Đủ sáu document canonical, mỗi row `status='SUBMITTED'`.
3. Có đúng một primary onboarding vehicle `status='PENDING_REVIEW'`.
4. Vehicle plate không trùng active vehicle khác.
5. Có deposit `COMPLETED`, amount đúng 3.000.000 VND.

IF bất kỳ check fail, THEN SHALL rollback và trả HTTP 422 code `DRIVER_NOT_APPROVABLE` cùng
`blocking_reasons`.

**FR-019**
WHEN approval validation thành công, THE system SHALL trong cùng transaction:

```sql
UPDATE app_user
SET status = 'ACTIVE',
    rejection_reason = NULL,
    updated_at = NOW()
WHERE id = :driver_id
  AND role = 'DRIVER'
  AND status = 'PENDING_APPROVAL';

UPDATE driver_profile
SET approved_at = NOW(),
    approved_by_manager_id = :manager_id,
    updated_at = NOW()
WHERE user_id = :driver_id;

UPDATE driver_document
SET status = 'APPROVED',
    reviewed_by = :manager_id,
    reviewed_at = NOW(),
    rejection_note = NULL,
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND status = 'SUBMITTED'
  AND deleted_at IS NULL;

UPDATE driver_vehicle
SET status = 'APPROVED',
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND is_primary = TRUE
  AND status = 'PENDING_REVIEW';
```

**FR-020**
WHEN approval transaction commit, THE system SHALL insert append-only audit event
`DRIVER_APPROVED` gồm `actor_id`, `target_driver_id`, previous/new status, decision note,
request id và timestamp; response SHALL là HTTP 200:

```json
{
  "driver_id": "6fd084a3-72ae-40bd-94ae-2fbde661a760",
  "status": "ACTIVE",
  "message": "Đã duyệt tài xế thành công",
  "approved_at": "2026-06-09T03:20:00Z"
}
```

**FR-021**
WHEN transaction đã commit, THE system SHALL enqueue async email
“Tài khoản Move_home của bạn đã được duyệt” với CTA `/driver/home.html`; WHERE email enqueue
hoặc send fail, SHALL record retry/alert nhưng SHALL không rollback approval theo HR-11.

---

### Nhóm 4 — Reject Driver (FR-022..FR-029)

**FR-022**
WHEN Manager click “Từ chối”, THE frontend SHALL mở modal có textarea “Lý do từ chối”, counter
`0/500`, helper text giải thích Driver sẽ đọc nội dung này và buttons “Quay lại”/“Xác nhận từ
chối”; confirm SHALL disabled khi trimmed length dưới 20.

**FR-023**
WHEN Manager gọi `POST /api/manager/drivers/{driverId}/reject`, THE request body SHALL là:

```json
{
  "reason": "Ảnh giấy phép lái xe bị mờ, không đọc được số bằng.",
  "decision_note": "Yêu cầu tài xế tải lại ảnh đủ sáng."
}
```

`reason` SHALL required, trim length `20..500`, có ít nhất một chữ cái và là thông điệp
user-facing có dấu tiếng Việt; `decision_note` optional, max 1000 ký tự.

**FR-024**
WHERE `reason` thiếu, quá ngắn, quá dài hoặc chỉ gồm whitespace/punctuation, THE system SHALL
trả HTTP 422 code `INVALID_REJECTION_REASON`, field error tiếng Việt và SHALL không đổi dữ liệu.

**FR-025**
WHEN reject request hợp lệ, THE backend SHALL bắt đầu transaction, lock target `app_user` bằng
`SELECT ... FOR UPDATE`, verify actor role `MANAGER`, target role `DRIVER` và target status
`PENDING_APPROVAL`; WHERE status đã đổi, SHALL rollback và trả HTTP 409 code
`DRIVER_ALREADY_DECIDED`.

**FR-026**
WHEN reject validation thành công, THE system SHALL trong cùng transaction:

```sql
UPDATE app_user
SET status = 'REJECTED',
    rejection_reason = :reason,
    updated_at = NOW()
WHERE id = :driver_id
  AND role = 'DRIVER'
  AND status = 'PENDING_APPROVAL';

UPDATE driver_profile
SET approved_at = NULL,
    approved_by_manager_id = NULL,
    rejected_at = NOW(),
    rejected_by_manager_id = :manager_id,
    updated_at = NOW()
WHERE user_id = :driver_id;

UPDATE driver_document
SET status = 'REJECTED',
    reviewed_by = :manager_id,
    reviewed_at = NOW(),
    rejection_note = :reason,
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND status = 'SUBMITTED'
  AND deleted_at IS NULL;

UPDATE driver_vehicle
SET status = 'REJECTED',
    updated_at = NOW()
WHERE driver_id = :driver_id
  AND is_primary = TRUE
  AND status = 'PENDING_REVIEW';
```

**FR-027**
WHEN rejection commit, THE system SHALL giữ nguyên `driver_deposit.status='COMPLETED'`,
`driver_deposit.amount=3000000` và `driver_profile.deposit_amount`; SHALL không tạo refund,
withdrawal hoặc money transaction.

**FR-028**
WHEN rejection commit, THE system SHALL insert audit event `DRIVER_REJECTED` chứa reason,
decision note, Manager id, Driver id, previous/new status và timestamp; SHALL trả HTTP 200:

```json
{
  "driver_id": "6fd084a3-72ae-40bd-94ae-2fbde661a760",
  "status": "REJECTED",
  "message": "Đã từ chối hồ sơ tài xế",
  "can_resubmit": true
}
```

**FR-029**
WHEN rejection transaction đã commit, THE system SHALL enqueue email
“Hồ sơ tài xế Move_home cần được cập nhật” chứa reason và CTA “Sửa giấy tờ và gửi lại”; WHERE
Driver chọn CTA, Spec #005 re-submit flow SHALL đưa status về `PENDING_DOCUMENTS` và không yêu
cầu đóng cọc lại.

---

### Nhóm 5 — Rejected History (FR-030..FR-034)

**FR-030**
WHEN Manager gọi
`GET /api/manager/drivers/rejected?page=0&size=20&search=&managerId=&from=&to=`, THE system SHALL
trả Spring Page Driver hiện có `status='REJECTED'` hoặc có audit event `DRIVER_REJECTED`, sort
decision time mới nhất trước.

**FR-031**
WHEN `search` có từ 2 đến 100 ký tự, THE system SHALL tìm case-insensitive theo normalized
`full_name`, `email`, `phone`, `license_plate`; SHALL dùng bound parameters và SHALL không
concatenate raw search vào SQL.

**FR-032**
WHEN filters `managerId`, `from`, `to` được cung cấp, THE system SHALL validate UUID và ISO date;
WHERE range lớn hơn 366 ngày hoặc `from > to`, SHALL trả HTTP 422 code `INVALID_DATE_RANGE`.

**FR-033**
WHEN rejected page render, THE frontend SHALL hiển thị columns `Họ tên`, `Email`, `Số điện
thoại`, `Biển số`, `Ngày từ chối`, `Người xử lý`, `Lý do`, `Thao tác`; reason SHALL truncate
hai dòng và có accessible tooltip/full detail.

**FR-034**
WHILE rejected history đang tải, rỗng hoặc lỗi, THE frontend SHALL implement Loading/Empty/Error
states; empty message SHALL là “Chưa có tài xế bị từ chối”; pagination SHALL theo AC-15 và page
size `10|20|50|100`.

---

### Nhóm 6 — Documents Viewer (FR-035..FR-037)

**FR-035**
WHEN Manager gọi `GET /api/manager/drivers/{driverId}/documents`, THE system SHALL verify role
và target Driver trước khi tạo signed Cloudinary delivery URLs TTL tối đa 3600 giây; response
SHALL không chứa Cloudinary API secret hoặc raw private public id.

**FR-036**
WHEN documents response thành công, THE system SHALL trả đủ metadata:

```json
{
  "driver_id": "6fd084a3-72ae-40bd-94ae-2fbde661a760",
  "expires_at": "2026-06-09T04:20:00Z",
  "documents": [
    {
      "document_id": "667714b3-e33f-46ce-a969-e00dfd28a79c",
      "document_type": "DRIVING_LICENSE",
      "image_role": "FRONT",
      "signed_url": "https://res.cloudinary.com/example/image/authenticated/...",
      "status": "SUBMITTED",
      "uploaded_at": "2026-06-03T08:00:00Z"
    }
  ]
}
```

**FR-037**
WHEN Manager click ảnh, THE frontend SHALL mở keyboard-accessible lightbox hỗ trợ next/previous,
zoom `50%..300%`, rotate theo bước 90 độ và đóng bằng Escape; WHERE signed URL hết hạn hoặc ảnh
lỗi, SHALL hiển thị “Không thể tải ảnh tài liệu” và button “Tạo liên kết mới”.

---

### Nhóm 7 — RBAC, State Validation & Audit (FR-038..FR-040)

**FR-038**
WHERE caller không có JWT hợp lệ, mọi endpoint SHALL trả HTTP 401 code `UNAUTHENTICATED`; WHERE
caller role không phải `MANAGER`, bao gồm `ADMIN`, `DRIVER`, `CUSTOMER`, SHALL trả HTTP 403 code
`FORBIDDEN` theo CONTEXT RBAC và HR-10.

**FR-039**
WHERE approve/reject target không còn `PENDING_APPROVAL`, THE system SHALL trả HTTP 409 code
`DRIVER_ALREADY_DECIDED` với current status; WHERE request trùng cùng `X-Idempotency-Key` và
payload/actor giống request đã thành công, SHALL replay response cũ mà không tạo audit/email
thứ hai.

**FR-040**
WHEN bất kỳ read hoặc decision endpoint hoàn tất, THE system SHALL emit structured security/
business telemetry không chứa document URL hoặc PII nhạy cảm; approve/reject SHALL luôn có
append-only audit event trong cùng transaction và audit write failure SHALL rollback decision.

---

## Non-Functional Requirements

**NFR-001 — List performance**  
Pending/rejected list SHALL hoàn tất dưới 500 ms ở p90 với page size 20 và tối thiểu 10.000
Driver records.

**NFR-002 — Detail performance**  
Driver detail SHALL hoàn tất dưới 1 giây ở p90, không phát sinh N+1 query và không tải binary
image qua backend.

**NFR-003 — Decision performance**  
Approve/reject DB transaction SHALL hoàn tất dưới 1 giây ở p90; toàn API dưới 2 giây, không chờ
SMTP.

**NFR-004 — Concurrency**  
Hai hoặc nhiều Manager quyết định cùng Driver SHALL không deadlock; đúng một transition commit,
các request còn lại nhận 409 hoặc idempotent replay.

**NFR-005 — Security & privacy**  
Signed document URL SHALL hết hạn trong tối đa một giờ; logs SHALL không chứa URL, full VNPay
reference, document metadata nhạy cảm hoặc rejection note nội bộ.

**NFR-006 — Audit durability**  
Decision và audit event SHALL atomic. Audit log SHALL append-only, lưu UTC `TIMESTAMPTZ`, actor,
target, request id, previous/new state và sanitized metadata.

**NFR-007 — UX quality**  
Ba màn SHALL responsive từ 360px, dùng brand tokens, tiếng Việt có dấu, accessible keyboard
navigation và đầy đủ Loading/Empty/Error states.

**NFR-008 — Availability**  
Nếu Cloudinary hoặc SMTP lỗi, list/detail metadata và DB decision vẫn hoạt động theo contract;
email retry async, image viewer hiển thị retry có kiểm soát.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/manager/drivers/pending-approval` | `page,size,age` | 200 Spring Page + KPIs | Manager |
| GET | `/api/manager/drivers/rejected` | `page,size,search,managerId,from,to` | 200 Spring Page | Manager |
| GET | `/api/manager/drivers/{driverId}` | Path UUID | 200 aggregate detail | Manager |
| GET | `/api/manager/drivers/{driverId}/documents` | Path UUID | 200 signed URLs | Manager |
| POST | `/api/manager/drivers/{driverId}/approve` | `{decision_note}` | 200 ACTIVE | Manager |
| POST | `/api/manager/drivers/{driverId}/reject` | `{reason,decision_note}` | 200 REJECTED | Manager |

### Common Error Format

```json
{
  "timestamp": "2026-06-09T03:20:00Z",
  "status": 409,
  "code": "DRIVER_ALREADY_DECIDED",
  "message": "Hồ sơ đã được xử lý bởi quản lý khác",
  "path": "/api/manager/drivers/6fd084a3-72ae-40bd-94ae-2fbde661a760/approve",
  "request_id": "01JY...",
  "field_errors": [],
  "details": {
    "current_status": "ACTIVE"
  }
}
```

---

## Data Model

Không tạo bảng nghiệp vụ approval mới. Feature reuse:

- `app_user` — authoritative role/status/rejection reason.
- `driver_profile` — approval metadata và profile.
- `driver_document` — six canonical image records.
- `driver_vehicle` — primary onboarding vehicle.
- `driver_deposit` — verified collateral.
- `audit_log` — append-only decision history.

### Required Profile Extensions

Migration SHALL bổ sung thời điểm reject riêng để không dùng sai `approved_at`:

```sql
ALTER TABLE driver_profile
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejected_by_manager_id UUID
        REFERENCES app_user(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_driver_profile_rejected_at
    ON driver_profile (rejected_at DESC)
    WHERE rejected_at IS NOT NULL;
```

Approval SHALL set:

```sql
UPDATE driver_profile
SET approved_at = NOW(),
    approved_by_manager_id = :manager_id,
    rejected_at = NULL,
    rejected_by_manager_id = NULL,
    updated_at = NOW()
WHERE user_id = :driver_id;
```

Rejection SHALL set:

```sql
UPDATE driver_profile
SET approved_at = NULL,
    approved_by_manager_id = NULL,
    rejected_at = NOW(),
    rejected_by_manager_id = :manager_id,
    updated_at = NOW()
WHERE user_id = :driver_id;
```

### Queue Indexes

```sql
CREATE INDEX IF NOT EXISTS idx_app_user_driver_pending_approval
    ON app_user (created_at ASC, id ASC)
    WHERE role = 'DRIVER'
      AND status = 'PENDING_APPROVAL'
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_app_user_driver_rejected
    ON app_user (updated_at DESC, id DESC)
    WHERE role = 'DRIVER'
      AND status = 'REJECTED'
      AND deleted_at IS NULL;
```

Nếu `driver_deposit.paid_at` là queue sort key canonical, migration SHALL tạo:

```sql
CREATE INDEX IF NOT EXISTS idx_driver_deposit_completed_paid
    ON driver_deposit (paid_at ASC, driver_id)
    WHERE status = 'COMPLETED';
```

### Audit Event Contract

`audit_log` canonical payload cho approve:

```json
{
  "event_type": "DRIVER_APPROVED",
  "actor_id": "manager-uuid",
  "target_type": "DRIVER",
  "target_id": "driver-uuid",
  "previous_state": "PENDING_APPROVAL",
  "new_state": "ACTIVE",
  "request_id": "01JY...",
  "metadata": {
    "decision_note": "Đã đối chiếu hồ sơ.",
    "document_count": 6,
    "vehicle_id": "vehicle-uuid",
    "deposit_id": "deposit-uuid"
  }
}
```

Audit metadata SHALL không chứa signed URLs, document public ids, VNPay full reference hoặc
Driver password/token.

---

## Query Contracts

### Pending Queue Query

```sql
SELECT u.id AS driver_id,
       u.full_name,
       u.email,
       u.phone,
       v.vehicle_type,
       v.license_plate,
       p.license_class,
       d.amount AS deposit_amount,
       d.status AS deposit_status,
       d.paid_at AS submitted_at,
       FLOOR(EXTRACT(EPOCH FROM (NOW() - d.paid_at)) / 86400) AS days_waiting
FROM app_user u
JOIN driver_profile p
  ON p.user_id = u.id
JOIN driver_vehicle v
  ON v.driver_id = u.id
 AND v.is_primary = TRUE
JOIN driver_deposit d
  ON d.driver_id = u.id
 AND d.status = 'COMPLETED'
WHERE u.role = 'DRIVER'
  AND u.status = 'PENDING_APPROVAL'
  AND u.deleted_at IS NULL
ORDER BY d.paid_at ASC, u.id ASC
LIMIT :size OFFSET :offset;
```

### Decision Lock Query

```sql
SELECT id, role, status, deleted_at
FROM app_user
WHERE id = :driver_id
FOR UPDATE;
```

Lock order SHALL luôn là `app_user` → `driver_profile` → `driver_document` →
`driver_vehicle` → `driver_deposit` → `audit_log` để giảm deadlock.

### Approval Eligibility Query

```sql
SELECT COUNT(*) FILTER (
           WHERE document_type = 'DRIVING_LICENSE' AND image_role IN ('FRONT', 'BACK')
       ) AS license_images,
       COUNT(*) FILTER (
           WHERE document_type = 'VEHICLE_REGISTRATION' AND image_role = 'FRONT'
       ) AS registration_images,
       COUNT(*) FILTER (
           WHERE document_type = 'VEHICLE_PHOTO' AND image_role IN ('FRONT', 'REAR', 'SIDE')
       ) AS vehicle_images
FROM driver_document
WHERE driver_id = :driver_id
  AND status = 'SUBMITTED'
  AND deleted_at IS NULL;
```

Expected counts là `license_images=2`, `registration_images=1`, `vehicle_images=3`. Unique
constraints từ Spec #005 SHALL ngăn duplicate active document role.

---

## Decision Transaction Boundaries

Approve/reject SHALL run at minimum `READ COMMITTED` với explicit row lock. Pseudocode:

```text
BEGIN
  verify Manager role from authoritative app_user
  lock target app_user FOR UPDATE
  verify target role/status/deleted_at
  check idempotency key
  load and validate documents, primary vehicle, deposit
  update app_user + profile + docs + vehicle
  insert audit event
  persist idempotency response
COMMIT
enqueue email
```

WHERE audit insert, profile update hoặc target guarded update affects zero unexpected rows,
THE transaction SHALL rollback and return an internal consistency error. Email enqueue occurs
after commit via outbox/event; it SHALL not hold DB lock.

### Concurrent Approval Example

Manager A và Manager B cùng approve:

1. A lock target row.
2. B waits on same row.
3. A validates, updates `ACTIVE`, inserts audit, commits.
4. B obtains lock, sees `ACTIVE`, rolls back.
5. B receives HTTP 409 `DRIVER_ALREADY_DECIDED`.
6. Exactly one `DRIVER_APPROVED` audit and one email outbox row exist.

### Approve vs Reject Race

Approve và reject cùng lúc SHALL follow identical locking. First committed decision wins.
Second request SHALL receive current status and generic message; response SHALL not disclose
other Manager's internal decision note.

---

## State Machine

```text
PENDING_APPROVAL
  |-- Manager APPROVE --> ACTIVE
  |-- Manager REJECT + reason --> REJECTED

REJECTED
  |-- Driver chọn sửa hồ sơ, Spec #005 --> PENDING_DOCUMENTS
  |-- Driver nộp lại đủ giấy tờ, cọc cũ còn hiệu lực --> PENDING_APPROVAL

ACTIVE
  |-- Driver Workflow, Spec #006
  |-- Suspend flow ngoài scope --> SUSPENDED
```

Rules:

1. Chỉ `PENDING_APPROVAL → ACTIVE` hoặc `PENDING_APPROVAL → REJECTED` thuộc spec này.
2. Mọi transition khác qua approve/reject endpoint trả HTTP 409 theo HR-05.
3. `REJECTED` không terminal; Driver có thể re-submit.
4. Reject không thay đổi deposit.
5. Admin không được thực hiện Manager decision.

---

## Decision Eligibility Checklist

Trước khi enable approve, UI hiển thị checklist read-only:

| Hạng mục | Pass condition | Blocking code |
|----------|----------------|---------------|
| Tài khoản | Role Driver, email verified, status pending | `INVALID_ACCOUNT_STATE` |
| GPLX mặt trước | Submitted, active row | `MISSING_LICENSE_FRONT` |
| GPLX mặt sau | Submitted, active row | `MISSING_LICENSE_BACK` |
| Đăng ký xe | Submitted, active row | `MISSING_VEHICLE_REGISTRATION` |
| Ảnh xe trước | Submitted, active row | `MISSING_VEHICLE_PHOTO_FRONT` |
| Ảnh xe sau | Submitted, active row | `MISSING_VEHICLE_PHOTO_REAR` |
| Ảnh xe hông | Submitted, active row | `MISSING_VEHICLE_PHOTO_SIDE` |
| Phương tiện | One primary pending vehicle, plate unique | `INVALID_PRIMARY_VEHICLE` |
| Cọc | Completed exactly 3.000.000 VND | `INVALID_DEPOSIT` |

Manager vẫn có thể reject khi thiếu checklist item. Manager không thể approve cho đến khi mọi
blocking condition pass.

---

## Error Matrix

| Scenario | HTTP | Code | Vietnamese message |
|----------|------|------|--------------------|
| Không có JWT | 401 | `UNAUTHENTICATED` | Phiên đăng nhập không hợp lệ |
| Token hết hạn | 401 | `TOKEN_EXPIRED` | Phiên đăng nhập đã hết hạn |
| Không phải Manager | 403 | `FORBIDDEN` | Bạn không có quyền thực hiện thao tác này |
| Driver không tồn tại | 404 | `DRIVER_NOT_FOUND` | Không tìm thấy tài xế |
| UUID sai format | 400 | `INVALID_PATH_PARAMETER` | Mã tài xế không hợp lệ |
| Already approved/rejected | 409 | `DRIVER_ALREADY_DECIDED` | Hồ sơ đã được xử lý |
| Missing document/deposit | 422 | `DRIVER_NOT_APPROVABLE` | Hồ sơ chưa đủ điều kiện duyệt |
| Rejection reason invalid | 422 | `INVALID_REJECTION_REASON` | Lý do từ chối phải từ 20 đến 500 ký tự |
| Invalid filter/date | 422 | `INVALID_FILTER` | Bộ lọc không hợp lệ |
| Signed URL failed | 503 | `DOCUMENT_PROVIDER_UNAVAILABLE` | Không thể tải ảnh tài liệu |
| Audit write failed | 500 | `AUDIT_WRITE_FAILED` | Không thể hoàn tất quyết định |

Frontend SHALL map expected codes to inline/modal messages and SHALL not display raw stack
trace, SQL error or provider response.

---

## Frontend Screen Contract

### `frontend/pages/manager/driver-approvals.html`

Required UI:

1. Page title “Tài xế chờ duyệt”.
2. KPI cards: hồ sơ chờ duyệt, đủ điều kiện, quá SLA.
3. Age filter pills: “Tất cả”, “Mới”, “Đợi quá 3 ngày”.
4. Server-side table và pagination.
5. Row click/button “Xem hồ sơ”.
6. Empty/Loading/Error states.
7. Link “Xem lịch sử từ chối”.

Legacy filter “Đã duyệt” SHALL không ở pending endpoint; approved management thuộc future
Manager Driver Management feature.

### `frontend/pages/manager/driver-detail.html?id={driverId}`

Required sections:

1. Thông tin cá nhân.
2. Checklist/ảnh giấy tờ.
3. Thông tin và ảnh phương tiện.
4. Bằng chứng đặt cọc.
5. Timeline onboarding/decision.
6. Decision eligibility checklist.
7. Approve/reject actions.

Buttons SHALL use brand primary for approve and semantic danger for reject. Inline colors ngoài
brand tokens không được dùng.

### `frontend/pages/manager/driver-rejected.html`

Required UI:

1. Search input.
2. Filters theo Manager và date range.
3. Rejected history table.
4. Read-only detail link.
5. Pagination.
6. Empty/Loading/Error states.

---

## Security & Privacy

1. Manager role được verify server-side từ JWT subject và authoritative DB role.
2. Admin không được approve/reject dù là cấp quản trị cao hơn theo CONTEXT RBAC.
3. Document URLs signed, short-lived và không log.
4. Detail response chỉ trả PII cần cho review.
5. Phone/email không xuất hiện trong metrics labels.
6. Reject reason được sanitize để ngăn stored XSS nhưng vẫn giữ tiếng Việt.
7. Search dùng parameter binding.
8. Decision endpoints yêu cầu CSRF protection nếu dùng cookie auth; JWT header flow vẫn kiểm
   tra CORS allowlist.
9. Audit append-only, Manager không thể edit/delete decision history.
10. Download document optional SHALL preserve authorization and signed expiry.

---

## Acceptance Criteria

**AC1 — Pending FIFO queue**  
GIVEN ba Driver pending có `submitted_at` khác nhau, WHEN Manager mở queue, THEN list sort oldest
first, KPIs đúng và page size 20.

**AC2 — Complete detail**  
GIVEN Driver có đủ six canonical documents, one primary vehicle và completed deposit, WHEN
Manager mở detail, THEN năm section, timeline và eligibility `can_approve=true` hiển thị đúng.

**AC3 — Successful approve**  
GIVEN Driver hợp lệ `PENDING_APPROVAL`, WHEN Manager approve, THEN `app_user.status='ACTIVE'`,
profile/docs/vehicle cập nhật atomic, một audit event và một email outbox được tạo.

**AC4 — Approval blocked on missing evidence**  
GIVEN Driver thiếu `VEHICLE_PHOTO/REAR`, WHEN Manager approve, THEN API trả 422 với blocking
reason, không state change, không audit decision và không email.

**AC5 — Successful reject**  
GIVEN reason hợp lệ, WHEN Manager reject, THEN status `REJECTED`, reason lưu, deposit giữ nguyên,
audit/email tạo và response `can_resubmit=true`.

**AC6 — Rejection validation**  
GIVEN reason dưới 20 ký tự, WHEN submit reject, THEN API trả 422 field error tiếng Việt và Driver
vẫn `PENDING_APPROVAL`.

**AC7 — Concurrent decision**  
GIVEN hai Manager approve/reject cùng Driver, WHEN requests chạy đồng thời, THEN đúng một
decision commit, request còn lại 409, không duplicate audit/email.

**AC8 — RBAC**  
GIVEN Admin, Driver hoặc Customer token, WHEN gọi bất kỳ endpoint spec này, THEN API trả 403;
Manager token hợp lệ truy cập được.

**AC9 — Rejected history**  
GIVEN rejected records, WHEN Manager search theo họ tên/email/phone/plate và filter date, THEN
page trả đúng records, sort newest rejection first và không có SQL injection.

**AC10 — UI quality**  
GIVEN API loading, empty hoặc fail, WHEN mở ba screens, THEN mỗi screen hiển thị đúng state,
tiếng Việt có dấu, brand tokens và keyboard navigation hoạt động.

---

## Edge Cases & Error Handling

### EC-01 — Hai Manager approve đồng thời

Expected: row lock serialize requests; một approve thành công, request còn lại 409; exactly one
audit/email.

### EC-02 — Một Manager approve, một Manager reject đồng thời

Expected: first commit wins; second nhận current status; không overwrite reason hoặc decision.

### EC-03 — Double-click approve

Expected: frontend disable button; backend idempotency key replay same 200 response; không
duplicate side effects.

### EC-04 — Manager session hết hạn trong confirm modal

Expected: API 401; modal đóng, redirect session-expired/login; không state change.

### EC-05 — Admin cố approve

Expected: HTTP 403 theo CONTEXT; không coi Admin là implicit Manager.

### EC-06 — Driver thiếu document sau khi detail đã load

Expected: approve endpoint re-check và trả 422; stale UI eligibility không được tin.

### EC-07 — Deposit bị đổi sang `REFUNDED` trước approve

Expected: approve blocked 422 `INVALID_DEPOSIT`; reject vẫn có thể thực hiện với reason.

### EC-08 — Cloudinary URL hỏng/hết hạn

Expected: metadata vẫn load; viewer hiển thị retry/new signed link; approve disabled nếu Manager
chưa thể review evidence theo team policy.

### EC-09 — Email gửi thất bại sau decision

Expected: decision giữ nguyên, outbox retry, alert nếu exhausted; Manager nhận success kèm
non-blocking notification status.

### EC-10 — Audit insert thất bại

Expected: transaction rollback toàn bộ decision; API 500; không email.

### EC-11 — Driver soft-deleted khi đang pending

Expected: list/detail loại bỏ record; decision endpoint 404/409, không activate.

### EC-12 — Vehicle plate trùng Driver active khác

Expected: approval blocked 422 `DUPLICATE_LICENSE_PLATE`; Manager có thể reject.

### EC-13 — Reason chứa HTML/script

Expected: validation/sanitization lưu plain text an toàn; email/UI escape; không stored XSS.

### EC-14 — Search input chứa wildcard/SQL payload

Expected: bound parameter query; không SQL injection; response bình thường hoặc empty.

### EC-15 — Driver đã reject rồi re-submit

Expected: new pending cycle xuất hiện lại queue; old `DRIVER_REJECTED` audit vẫn giữ; cọc không
thu lần hai.

### EC-16 — Multiple submitted document versions

Expected: chỉ active/non-deleted latest canonical row được review; uniqueness violation làm
approve blocked và tạo operational alert.

### EC-17 — Browser mất mạng sau successful decision

Expected: retry cùng idempotency key nhận same result; reload detail cho thấy current status.

### EC-18 — Queue page out of range

Expected: HTTP 200 với empty content và valid page metadata hoặc redirect frontend về last valid
page; không 500.

---

## Test Cases

### TC-001 — Pending Queue FIFO

**Type:** Integration  
**Given:** Driver A paid 5 days ago, B 1 day ago, C 3 days ago.  
**When:** Manager calls pending endpoint.  
**Then:** Order A, C, B; `oldest_waiting_days=5`; `over_sla_total=1`.

### TC-002 — Detail Canonical Documents

**Type:** Integration  
**Given:** Driver has exactly GPLX front/back, registration front, vehicle front/rear/side.  
**When:** Manager calls detail.  
**Then:** `required_count=6`, `submitted_count=6`, no missing items, no CCCD/selfie required.

### TC-003 — Approve Happy Path

**Type:** Integration  
**Given:** Valid pending Driver, primary vehicle and completed 3M deposit.  
**When:** Manager approves.  
**Then:** HTTP 200, Driver active, docs/vehicle approved, profile approval metadata set, one
audit and one email outbox.

### TC-004 — Approve Missing Deposit

**Type:** Integration  
**Given:** Deposit status `FAILED`.  
**When:** Manager approves.  
**Then:** HTTP 422 `DRIVER_NOT_APPROVABLE`, blocking `INVALID_DEPOSIT`, no writes.

### TC-005 — Reject Happy Path

**Type:** Integration  
**Given:** Pending Driver and valid Vietnamese reason.  
**When:** Manager rejects.  
**Then:** HTTP 200, Driver rejected, reason/audit/email saved, deposit still completed and 3M.

### TC-006 — Reject Short Reason

**Type:** Validation  
**Given:** Reason “Ảnh mờ”.  
**When:** Manager rejects.  
**Then:** HTTP 422 `INVALID_REJECTION_REASON`; all state unchanged.

### TC-007 — Concurrent Approvals

**Type:** Concurrency  
**Given:** 50 Manager requests approve same Driver using distinct idempotency keys.  
**When:** Requests start simultaneously.  
**Then:** Exactly one 200, 49 responses 409, one audit event, one email outbox, no deadlock.

### TC-008 — Approve/Reject Race

**Type:** Concurrency  
**Given:** One approve and one reject request start simultaneously.  
**When:** Both execute.  
**Then:** One wins, one 409; final data consistently matches winner.

### TC-009 — RBAC Matrix

**Type:** Security  
**Given:** Tokens for Guest/Customer/Driver/Admin/Manager.  
**When:** Each calls all six endpoints.  
**Then:** Only Manager succeeds; no token 401; all other authenticated roles 403.

### TC-010 — Signed URL Expiry

**Type:** Security/Integration  
**Given:** Manager requests document URLs.  
**When:** URL used before and after TTL.  
**Then:** Before works, after fails; refresh endpoint issues new URL after RBAC check.

### TC-011 — Audit Failure Rollback

**Type:** Fault Injection  
**Given:** Audit insert forced to fail.  
**When:** Manager approves.  
**Then:** HTTP 500, Driver remains pending, docs/vehicle unchanged, no email.

### TC-012 — Email Failure Does Not Roll Back

**Type:** Fault Injection  
**Given:** SMTP unavailable after commit.  
**When:** Manager rejects.  
**Then:** Rejection persists, outbox retry scheduled, alert emitted.

### TC-013 — Rejected Search Injection Safety

**Type:** Security  
**Given:** Search `%' OR 1=1 --`.  
**When:** Manager calls rejected endpoint.  
**Then:** Parameterized query executes safely; no unauthorized rows or SQL error leakage.

### TC-014 — Re-submit Cycle

**Type:** End-to-End  
**Given:** Driver rejected with completed deposit.  
**When:** Driver re-submits via Spec #005 and Manager approves.  
**Then:** Driver active, deposit charged only once, rejection and approval audit both retained.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-05 | Chỉ transition pending → active/rejected; invalid transition 409 |
| HR-10 | Chỉ Manager; Admin/Driver/Customer 403 theo CONTEXT |
| HR-11 | Email async, không rollback decision |
| HR-13 | Decision + audit atomic; history append-only |
| HR-19 | Move_home forest green/amber/Be Vietnam Pro |
| HR-20 | Mọi user-facing text/email có dấu tiếng Việt |
| HR-21 | Reuse tên bảng không reserved word |
| AC-07 | Timestamps UTC `TIMESTAMPTZ` |
| AC-08 | Deposit VND `NUMERIC(15,0)` |
| AC-09 | Không hard-delete Driver/history |
| AC-10 | Signed document URLs có RBAC và TTL |
| AC-14 | Status dùng `VARCHAR + CHECK`, không PostgreSQL ENUM |
| AC-15 | Pending/rejected server-side pagination |
| AC-16 | Loading/Empty/Error bắt buộc |
| ES-03 | Request validation qua Bean Validation |
| ES-04 | Common structured error format |
| ES-05 | CORE feature có integration/concurrency/security tests |

---

## Out of Scope (Deferred)

1. Driver re-upload/resubmit implementation chi tiết.
2. Automated OCR/fraud scoring.
3. Manager note collaboration hoặc `NEEDS_REVIEW` state.
4. Active Driver suspension/reinstatement.
5. Admin override.
6. Deposit refund/withdrawal.
7. Full approved Driver management list.
