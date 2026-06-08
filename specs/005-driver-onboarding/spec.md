# Feature Specification: Driver Onboarding (4-Step Flow)

**Feature Branch:** `005-driver-onboarding`  
**Feature Number:** #5 of 30 — CORE (driver supply pipeline)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 3

**CONTEXT.md reference:** v2.0 §2 Driver Onboarding, Wallet & Commission, VNPay IPN  
**Constitution reference:** v1.3.0 — HR-01, HR-02, HR-03, HR-04, HR-05, HR-10,
HR-11, HR-12, HR-13, HR-15, HR-18, HR-19, HR-20, HR-21, AC-07, AC-08, AC-09,
AC-10, AC-12, AC-13, AC-14, AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Driver screens 4.1 đến 4.4  
**Related specs:** Spec #001 Auth/RBAC; Payment spec; Manager Driver Approval spec;
Driver Workflow spec

---

## Goals

Triển khai quy trình tự đăng ký bốn bước để một ứng viên Driver trở thành đối tác `ACTIVE` đủ
điều kiện nhận đơn. Step 1 tạo tài khoản và xác thực email theo Spec #001. Step 2 thu thập giấy
phép lái xe, đăng ký xe, thông tin xe và ảnh thực tế qua Cloudinary signed upload. Step 3 yêu cầu
đặt cọc collateral cố định 3.000.000 VND qua VNPay. Step 4 hiển thị trạng thái chờ Manager duyệt;
chỉ hành động approve hợp lệ mới chuyển Driver sang `ACTIVE`.

Luồng phải hướng dẫn rõ bước hiện tại, không cho skip, lưu trạng thái authoritative trong DB và
cho phép Driver resume trên thiết bị khác. Mọi transition phải tuân thủ state machine, có audit
trail, email tiếng Việt và HTTP error cụ thể. VNPay IPN đã verify HMAC là nguồn duy nhất xác nhận
cọc; return URL hoặc frontend không được đổi trạng thái tài chính.

Mục tiêu nghiệp vụ là tạo nguồn cung Driver đã xác thực, có xe phù hợp và có collateral để giảm
rủi ro DamageReport. Mục tiêu UX là hoàn thành phần nhập liệu trong dưới 15 phút, hiển thị progress
liên tục và giải thích thời gian duyệt 1-3 ngày làm việc. Bốn màn hình dùng Move_home forest green
`#1B4D3E`, amber `#F5A623`, Be Vietnam Pro, tiếng Việt có dấu và đủ Loading/Empty/Error states.

---

## Source-of-Truth Resolution

> Hierarchy: `CONTEXT.md v2.0` → Constitution v1.3.0 → Spec #001 → spec này →
> `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả |
|--------|----------------------|--------|
| Lifecycle owner | `app_user.status` | Không duplicate status trong `driver_profile` |
| Canonical states | `PENDING_VERIFY → PENDING_DOCUMENTS → PENDING_DEPOSIT → PENDING_APPROVAL → ACTIVE` | Không tạo `PENDING_VEHICLE` |
| Step count | 4 business steps: account/verify, documents+vehicle, deposit, pending approval | UI hiện ghi 1/3 phải đổi thành 1/4, 2/4, 3/4, 4/4 |
| Required docs | GPLX front/back, đăng ký xe, ba ảnh xe | CCCD/selfie chưa được CONTEXT duyệt, ngoài scope |
| Vehicles | Driver có thể có nhiều xe, onboarding yêu cầu ít nhất một xe | `driver_vehicle` dùng PK riêng, không PK driver_id |
| Deposit confirmation | Chỉ VNPay IPN verified | Không có Driver-facing `/deposit/confirm` đổi DB |
| Deposit audit | `driver_deposit` + append-only `transaction` type `DEPOSIT_TOP_UP` | Không dùng PostgreSQL ENUM |
| Collateral snapshot | Đồng bộ `driver_profile.deposit_amount=3.000.000` khi IPN thành công | Driver wallet chi tiết thuộc financial spec |
| Approval | Manager flow ngoài scope | Spec này chỉ consume transition/event `DRIVER_APPROVED` |
| Rejected re-submit | CONTEXT cho phép, implementation chi tiết defer Manager Approval spec | Status hiển thị được nhưng không định nghĩa full edit flow |

---

## Scope Summary

**In scope:**

1. Reference `POST /api/auth/register/driver` và email verification từ Spec #001.
2. `GET /api/driver/onboarding/status` — current state, step và next action.
3. `GET /api/driver/onboarding/documents/required` — checklist authoritative.
4. Signed Cloudinary upload cho tài liệu và ảnh xe.
5. `POST /api/driver/onboarding/documents/submit` — xác nhận bộ GPLX.
6. `POST /api/driver/onboarding/vehicles` — tạo xe onboarding đầu tiên.
7. `GET /api/driver/onboarding/deposit` — thông tin collateral.
8. `POST /api/driver/onboarding/deposit/initiate` — tạo VNPay URL.
9. `POST /api/vnpay/ipn/driver-deposit` — verified/idempotent IPN.
10. Pending approval screen polling 30 giây.
11. Email và audit cho mọi transition.
12. Flyway migrations cho documents, vehicles, deposits và profile extensions.

**Out of scope:**

1. Form/list Manager approve/reject — Manager Driver Approval spec.
2. VNPay shared gateway internals ngoài contract IPN.
3. Driver workflow sau `ACTIVE`.
4. Full re-submission/edit-after-reject flow.
5. Deposit refund/withdrawal/replenishment.
6. CCCD, selfie và background check.
7. OCR/AI document verification.
8. Đăng ký xe thứ hai sau onboarding.

---

## User Stories

**P1 (CORE):**

**US1:** Là ứng viên Driver, tôi tạo tài khoản với email, số điện thoại, thông tin cá nhân và
mật khẩu để bắt đầu onboarding.

**US2:** Là Driver đã xác thực email, tôi tải GPLX mặt trước/sau và thông tin GPLX để chứng minh
quyền điều khiển xe.

**US3:** Là Driver, tôi nộp đăng ký xe, biển số, loại xe và ba ảnh thực tế để Manager xác minh.

**US4:** Là Driver đã hoàn tất hồ sơ, tôi đặt cọc 3.000.000 VND qua VNPay để gửi hồ sơ duyệt.

**US5:** Là Driver đã đặt cọc, tôi thấy màn hình chờ duyệt, timeline và thời gian dự kiến.

**US6:** Là Driver được Manager approve, tôi nhận email và được chuyển đến trang chủ Driver.

**P2:**

**US7:** Là Driver đang onboarding, tôi resume đúng bước từ DB trên thiết bị khác.

**US8:** Là Driver bỏ dở hơn bảy ngày, tôi nhận email nhắc tiếp tục onboarding.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Step 1 Registration & Resume (FR-001..FR-005)

**FR-001**
WHEN ứng viên submit `POST /api/auth/register/driver`, THE system SHALL áp dụng Spec #001
FR-041..FR-046 và tạo `app_user` với `role='DRIVER'`, `status='PENDING_VERIFY'`; spec này SHALL
không duplicate password/email validation.

**FR-002**
WHEN Driver account được tạo, THE system SHALL trong cùng transaction tạo một `driver_profile`
row với `user_id`, `deposit_amount=0`, timestamps UTC và các field onboarding nullable; SHALL
insert audit event `DRIVER_ONBOARDING_STARTED`.

**FR-003**
WHEN Driver xác thực email bằng token hợp lệ, THE system SHALL áp dụng Spec #001 FR-047,
transition `PENDING_VERIFY → PENDING_DOCUMENTS`, insert audit `DRIVER_EMAIL_VERIFIED` và redirect
`/driver/register-step2.html`.

**FR-004**
WHEN authenticated Driver gọi `GET /api/driver/onboarding/status`, THE system SHALL trả:

```json
{
  "status": "PENDING_DOCUMENTS",
  "current_step": 2,
  "total_steps": 4,
  "next_action": "SUBMIT_DOCUMENTS_AND_VEHICLE",
  "redirect": "/driver/register-step2.html",
  "completed_steps": ["ACCOUNT", "EMAIL_VERIFIED"]
}
```

**FR-005**
WHERE role không phải Driver, SHALL trả HTTP 403; WHERE Driver status không phải onboarding state,
SHALL map `ACTIVE` đến `/driver/home.html`, `REJECTED` đến pending screen có reason và
`SUSPENDED` đến support screen; frontend SHALL không suy đoán step từ localStorage.

---

### Nhóm 2 — Step 2 Documents Upload (FR-006..FR-015)

**FR-006**
WHEN Driver `PENDING_DOCUMENTS` gọi `GET /api/driver/onboarding/documents/required`, THE system
SHALL trả checklist:

```json
{
  "required": [
    {"document_type":"DRIVING_LICENSE","image_roles":["FRONT","BACK"]},
    {"document_type":"VEHICLE_REGISTRATION","image_roles":["FRONT"]},
    {"document_type":"VEHICLE_PHOTO","image_roles":["FRONT","REAR","SIDE"]}
  ]
}
```

**FR-007**
WHEN frontend chọn ảnh, THE frontend SHALL compress cạnh dài tối đa 1280px, JPEG quality 0.8 và
target dưới 1 MB; backend SHALL enforce magic-byte MIME `image/jpeg|image/png|image/webp`,
size `1..1572864` bytes trước khi ký/upload.

**FR-008**
WHEN file metadata hợp lệ, Driver SHALL gọi `POST /api/driver/onboarding/uploads/signature` với
`document_type`, `image_role`, `content_type`, `size`; backend SHALL trả signed Cloudinary params
TTL 10 phút cho folder `movehome/drivers/<driver_id>/<document_type>/<image_role>`.

**FR-009**
WHEN Cloudinary upload thành công, Driver SHALL gọi
`POST /api/driver/onboarding/uploads/confirm`; backend SHALL verify signature, public-id prefix,
HTTPS URL và asset metadata, rồi upsert draft `driver_document` row cho đúng type/role.

**FR-010**
WHEN Driver submit GPLX bằng `POST /api/driver/onboarding/documents/submit`, body SHALL gồm:

```json
{
  "license_number": "790123456789",
  "license_class": "B2",
  "license_expiry_date": "2030-06-04",
  "front_document_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "back_document_id": "6bf6e878-52b0-4fb8-a9cb-8af517594e89"
}
```

**FR-011**
WHEN validate GPLX, THE system SHALL enforce license number 8-20 uppercase alphanumeric,
`license_class IN ('B1','B2','C','D')`, expiry ISO date và lớn hơn current date ít nhất 90 ngày;
WHERE sai, SHALL trả HTTP 422 với tất cả field errors.

**FR-012**
WHERE `license_number` đã thuộc Driver khác còn hiệu lực, THE system SHALL trả HTTP 409
`LICENSE_NUMBER_ALREADY_USED`; SHALL không tiết lộ Driver owner.

**FR-013**
WHEN GPLX hợp lệ, THE system SHALL update `driver_profile.license_number/license_class`,
mark hai document rows `SUBMITTED`, insert audit `DRIVER_LICENSE_SUBMITTED` và giữ
`app_user.status='PENDING_DOCUMENTS'` cho đến khi vehicle hoàn tất.

**FR-014**
WHERE Driver submit CCCD/selfie hoặc document type ngoài checklist, THE system SHALL trả HTTP 422
`UNSUPPORTED_DOCUMENT_TYPE`; feature không được tự thêm yêu cầu chưa được CONTEXT duyệt.

**FR-015**
WHILE upload/submit đang chạy, frontend SHALL hiển thị progress từng file; WHERE Cloudinary/API
lỗi, SHALL giữ các file đã confirm, hiển thị "Không thể tải ảnh lên" và cho retry file lỗi.

---

### Nhóm 3 — Step 2 Vehicle Information (FR-016..FR-022)

**FR-016**
WHEN Driver `PENDING_DOCUMENTS` submit `POST /api/driver/onboarding/vehicles`, body SHALL có:

```json
{
  "license_plate": "30E-56789",
  "vehicle_type": "TRUCK_1T",
  "manufacture_year": 2021,
  "color": "Trắng",
  "registration_document_id": "7cf6e878-52b0-4fb8-a9cb-8af517594e89",
  "photo_ids": {
    "front": "8df6e878-52b0-4fb8-a9cb-8af517594e89",
    "rear": "9ef6e878-52b0-4fb8-a9cb-8af517594e89",
    "side": "10f6e878-52b0-4fb8-a9cb-8af517594e89"
  }
}
```

**FR-017**
WHEN validate vehicle, THE system SHALL enforce plate regex
`^[0-9]{2}[A-Z]{1,2}-[0-9]{4,5}$`, type
`TRUCK_500KG|TRUCK_1T|TRUCK_15T`, year `2010..current_year`, color 1-30 Unicode chars và đủ
registration/front/rear/side assets thuộc Driver.

**FR-018**
WHERE normalized plate đã tồn tại trên vehicle không soft-deleted, THE system SHALL trả HTTP 409
`LICENSE_PLATE_ALREADY_USED`; normalization SHALL uppercase và loại spaces trước compare.

**FR-019**
WHEN vehicle payload hợp lệ và GPLX đã submit, THE system SHALL trong một transaction insert
`driver_vehicle`, mark related documents `SUBMITTED`, transition
`app_user.status PENDING_DOCUMENTS → PENDING_DEPOSIT`, insert audit
`DRIVER_DOCUMENTS_COMPLETE` và trả HTTP 201 với next step deposit.

**FR-020**
WHERE GPLX chưa đủ hoặc bất kỳ vehicle image chưa confirmed, THE system SHALL trả HTTP 409
`ONBOARDING_DOCUMENTS_INCOMPLETE`, liệt kê `missing_items`; SHALL không transition status.

**FR-021**
WHERE Driver submit vehicle khi status khác `PENDING_DOCUMENTS`, THE system SHALL trả HTTP 409
`INVALID_ONBOARDING_STEP`; duplicate retry cùng idempotency key SHALL replay response cũ.

**FR-022**
WHEN frontend hoàn tất Step 2, THE frontend SHALL hiển thị summary read-only GPLX/xe, message
"Hồ sơ đã được lưu" và redirect `/driver/register-step3-deposit.html`.

---

### Nhóm 4 — Step 3 Deposit Payment (FR-023..FR-031)

**FR-023**
WHEN Driver `PENDING_DEPOSIT` gọi `GET /api/driver/onboarding/deposit`, THE system SHALL trả
`amount=3000000`, currency `VND`, refundable policy, `latest_attempt`, và payment method duy nhất
`VNPAY`; amount SHALL không nhận từ client.

**FR-024**
WHEN Driver gọi `POST /api/driver/onboarding/deposit/initiate` với `Idempotency-Key: <uuid>`,
THE system SHALL create `driver_deposit` status `PENDING`, amount 3.000.000 VND, unique
`vnpay_txn_ref`, expiry 15 phút và trả HTTP 201:

```json
{
  "deposit_id": "11f6e878-52b0-4fb8-a9cb-8af517594e89",
  "amount": 3000000,
  "payment_url": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
  "expires_at": "2026-06-04T11:15:00Z"
}
```

**FR-025**
WHERE Driver status khác `PENDING_DEPOSIT`, THE initiate endpoint SHALL trả HTTP 409
`INVALID_ONBOARDING_STEP`; WHERE đã có completed deposit, SHALL trả HTTP 409
`DEPOSIT_ALREADY_PAID`.

**FR-026**
WHEN VNPay gọi public `POST /api/vnpay/ipn/driver-deposit`, THE system SHALL verify HMAC-SHA512
trước mọi lookup/mutation; WHERE hash sai, SHALL trả VNPay `RspCode=97` và không đổi DB.

**FR-027**
WHEN IPN hash hợp lệ và `vnp_TxnRef` map tới deposit `PENDING`, THE system SHALL verify amount
3.000.000 VND và success response code; trong một transaction lock deposit/Driver, set deposit
`COMPLETED`, set `paid_at`, update `driver_profile.deposit_amount=3000000`, transition
`PENDING_DEPOSIT → PENDING_APPROVAL`, append `transaction(DEPOSIT_TOP_UP,+3000000)` và audit.

**FR-028**
WHERE IPN hợp lệ được gửi lại cho deposit `COMPLETED`, THE system SHALL trả idempotent success/
already-confirmed code và không duplicate transaction, deposit amount hoặc audit event.

**FR-029**
WHERE IPN amount/reference/status không khớp, THE system SHALL trả appropriate VNPay error,
không transition Driver và insert security audit `DRIVER_DEPOSIT_IPN_REJECTED`.

**FR-030**
WHILE deposit `PENDING` quá `expires_at`, scheduled job mỗi 5 phút SHALL transition sang
`EXPIRED`, giữ audit row và cho Driver initiate attempt mới; frontend return URL SHALL chỉ
hiển thị kết quả dự kiến, không update DB.

**FR-031**
WHEN deposit hoàn tất, THE system SHALL enqueue email "Đặt cọc thành công, hồ sơ đang chờ duyệt";
frontend polling status SHALL redirect `/driver/pending-approval.html`; email lỗi SHALL không
rollback IPN transaction.

---

### Nhóm 5 — Step 4 Pending Approval (FR-032..FR-036)

**FR-032**
WHEN Driver `PENDING_APPROVAL` mở `/driver/pending-approval.html`, THE frontend SHALL hiển thị
badge "Đang chờ duyệt", timeline 4/4, thời gian dự kiến "1-3 ngày làm việc", support contact và
button "Đăng xuất".

**FR-033**
WHILE pending approval page visible, frontend SHALL gọi `GET /api/driver/onboarding/status`
mỗi 30 giây, tối đa 2 call/phút; SHALL dừng polling khi tab hidden hoặc status đổi.

**FR-034**
WHEN Manager Approval spec transition `PENDING_APPROVAL → ACTIVE`, THE status API SHALL trả
`status='ACTIVE'`, `redirect='/driver/home.html'`; frontend SHALL hiển thị welcome message rồi
redirect sau 3 giây.

**FR-035**
WHEN Manager transition `PENDING_APPROVAL → REJECTED`, status API SHALL trả localized rejection
reason và `next_action='CONTACT_SUPPORT_OR_RESUBMIT'`; frontend SHALL không hiển thị nút nhận đơn.

**FR-036**
WHERE status endpoint lỗi, frontend SHALL giữ screen hiện tại, hiển thị
"Không thể cập nhật trạng thái hồ sơ" + "Thử lại"; SHALL không giả định Driver đã ACTIVE.

---

### Nhóm 6 — Email Notifications & Reminder (FR-037..FR-040)

**FR-037**
WHEN account registration hoàn tất, THE system SHALL gửi verification email theo Spec #001,
tiếng Việt có link TTL 24 giờ và không block register response.

**FR-038**
WHEN Step 2 transition sang `PENDING_DEPOSIT`, THE system SHALL enqueue email
"Hồ sơ giấy tờ đã được lưu. Vui lòng hoàn tất đặt cọc 3.000.000 VND".

**FR-039**
WHEN deposit IPN transition sang `PENDING_APPROVAL` hoặc Manager transition sang `ACTIVE`,
THE system SHALL enqueue template tương ứng "Đặt cọc thành công" hoặc
"Tài khoản tài xế đã được duyệt"; email failure SHALL retry và không rollback.

**FR-040**
WHILE Driver ở `PENDING_DOCUMENTS|PENDING_DEPOSIT` và `updated_at < NOW()-INTERVAL '7 days'`,
daily reminder job SHALL gửi tối đa một email mỗi 7 ngày, ghi `last_reminder_at` và không gửi cho
`PENDING_APPROVAL|ACTIVE|REJECTED|SUSPENDED`.

---

### Nhóm 7 — RBAC, State Validation & Audit (FR-041..FR-044)

**FR-041**
WHERE JWT thiếu/hết hạn, onboarding endpoints authenticated SHALL trả HTTP 401; WHERE role khác
`DRIVER`, SHALL trả HTTP 403; driver id SHALL luôn lấy từ JWT, không từ body.

**FR-042**
WHERE Driver gọi endpoint không đúng current state hoặc attempt skip step, THE system SHALL trả
HTTP 409 `INVALID_ONBOARDING_STEP`, gồm `current_status`, `expected_action`, và không mutate.

**FR-043**
WHILE Driver status chưa `ACTIVE`, access `/driver/home.html` hoặc Driver workflow API SHALL bị
chặn và redirect/response chỉ đúng onboarding next action; `PENDING_APPROVAL` không được nhận đơn.

**FR-044**
WHEN bất kỳ onboarding state/document/vehicle/deposit thay đổi, THE system SHALL ghi immutable
audit với actor, target Driver, from/to state, event type, timestamp UTC và metadata không chứa
secret, raw document URL, HMAC hoặc password.

---

## Non-Functional Requirements

**NFR-001**
Step 1 register API SHALL có P90 dưới 2 giây, không tính email async.

**NFR-002**
Mỗi image upload/confirm SHALL hoàn tất dưới 10 giây P90 trên mạng 4G.

**NFR-003**
Deposit initiate SHALL trả VNPay URL dưới 3 giây P90.

**NFR-004**
Status check API SHALL có P90 dưới 300 ms và chịu 2 polls/phút/pending Driver.

**NFR-005**
Mọi email SHALL async, retry tối đa 3 lần và không block transaction chính.

**NFR-006**
Mọi image SHALL ở Cloudinary signed upload; không Base64/BLOB/local file.

**NFR-007**
Reminder job SHALL idempotent, không gửi quá một reminder/Driver/7 ngày.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| POST | `/api/auth/register/driver` | Spec #001 payload | 201 user id | Public |
| GET | `/api/driver/onboarding/status` | none | 200 status DTO | Driver |
| GET | `/api/driver/onboarding/documents/required` | none | 200 checklist | Driver |
| POST | `/api/driver/onboarding/uploads/signature` | file metadata | 200 signed params | Driver |
| POST | `/api/driver/onboarding/uploads/confirm` | asset metadata | 200 document draft | Driver |
| POST | `/api/driver/onboarding/documents/submit` | GPLX metadata | 200 submitted | Driver |
| POST | `/api/driver/onboarding/vehicles` | vehicle + asset ids | 201 vehicle | Driver |
| GET | `/api/driver/onboarding/deposit` | none | 200 deposit info | Driver |
| POST | `/api/driver/onboarding/deposit/initiate` | idempotency header | 201 payment URL | Driver |
| POST | `/api/vnpay/ipn/driver-deposit` | VNPay query/body | VNPay RspCode | Public verified |

---

## Data Model

### Table 1 — `driver_profile` (existing, extended)

Lifecycle status không nằm trong table này; authoritative status là `app_user.status`.

```sql
ALTER TABLE driver_profile
    ADD COLUMN IF NOT EXISTS license_expiry_date DATE,
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS profile_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE driver_profile
    ADD CONSTRAINT ck_driver_profile_license_class
    CHECK (license_class IS NULL OR license_class IN ('B1', 'B2', 'C', 'D'));
```

### Table 2 — `driver_document`

```sql
CREATE TABLE driver_document (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    driver_id             UUID         NOT NULL REFERENCES app_user(id),
    document_type         VARCHAR(30)  NOT NULL
        CHECK (document_type IN ('DRIVING_LICENSE', 'VEHICLE_REGISTRATION', 'VEHICLE_PHOTO')),
    image_role            VARCHAR(20)  NOT NULL
        CHECK (image_role IN ('FRONT', 'BACK', 'REAR', 'SIDE')),
    cloudinary_public_id  VARCHAR(500) NOT NULL,
    cloudinary_secure_url VARCHAR(1000) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    reviewed_by           UUID         REFERENCES app_user(id),
    reviewed_at           TIMESTAMPTZ,
    rejection_note        VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,

    CONSTRAINT pk_driver_document PRIMARY KEY (id)
);

CREATE INDEX idx_driver_document_driver_status
    ON driver_document (driver_id, status)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_driver_document_role_active
    ON driver_document (driver_id, document_type, image_role)
    WHERE deleted_at IS NULL;
```

### Table 3 — `driver_vehicle`

```sql
CREATE TABLE driver_vehicle (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    driver_id         UUID        NOT NULL REFERENCES app_user(id),
    license_plate     VARCHAR(20) NOT NULL,
    vehicle_type      VARCHAR(20) NOT NULL
        CHECK (vehicle_type IN ('TRUCK_500KG', 'TRUCK_1T', 'TRUCK_15T')),
    manufacture_year  INTEGER     NOT NULL CHECK (manufacture_year >= 2010),
    color             VARCHAR(30) NOT NULL,
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'INACTIVE')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ,

    CONSTRAINT pk_driver_vehicle PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_driver_vehicle_plate_active
    ON driver_vehicle (UPPER(REPLACE(license_plate, ' ', '')))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uq_driver_vehicle_primary
    ON driver_vehicle (driver_id)
    WHERE is_primary = TRUE AND deleted_at IS NULL;
```

Vehicle image/registration assets link qua `driver_document`; không duplicate JSON URLs.

### Table 4 — `driver_deposit`

```sql
CREATE TABLE driver_deposit (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    driver_id        UUID          NOT NULL REFERENCES app_user(id),
    amount           NUMERIC(15,0) NOT NULL DEFAULT 3000000
        CHECK (amount = 3000000),
    vnpay_txn_ref    VARCHAR(100)  NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'EXPIRED', 'REFUNDED')),
    idempotency_key  UUID          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ   NOT NULL,
    paid_at          TIMESTAMPTZ,
    refunded_at      TIMESTAMPTZ,

    CONSTRAINT pk_driver_deposit PRIMARY KEY (id),
    CONSTRAINT uq_driver_deposit_vnpay_ref UNIQUE (vnpay_txn_ref),
    CONSTRAINT uq_driver_deposit_idempotency UNIQUE (driver_id, idempotency_key)
);

CREATE UNIQUE INDEX uq_driver_deposit_completed
    ON driver_deposit (driver_id)
    WHERE status = 'COMPLETED';

CREATE INDEX idx_driver_deposit_pending_expiry
    ON driver_deposit (expires_at)
    WHERE status = 'PENDING';
```

### Deposit IPN Transaction

```sql
BEGIN;

SELECT id, driver_id, amount, status
FROM driver_deposit
WHERE vnpay_txn_ref = :vnp_txn_ref
FOR UPDATE;

UPDATE driver_deposit
SET status = 'COMPLETED',
    paid_at = NOW()
WHERE id = :deposit_id
  AND status = 'PENDING';

UPDATE driver_profile
SET deposit_amount = 3000000,
    deposit_paid_at = NOW(),
    updated_at = NOW()
WHERE user_id = :driver_id;

UPDATE app_user
SET status = 'PENDING_APPROVAL',
    updated_at = NOW()
WHERE id = :driver_id
  AND role = 'DRIVER'
  AND status = 'PENDING_DEPOSIT';

INSERT INTO transaction
    (user_id, type, amount, description, vnpay_txn_ref)
VALUES
    (:driver_id, 'DEPOSIT_TOP_UP', 3000000,
     'Đặt cọc đăng ký tài xế', :vnp_txn_ref);

COMMIT;
```

Service SHALL verify every update row count bằng 1; nếu không, rollback và trả IPN error.

---

## State Machines

### Canonical Driver Account Lifecycle

```text
NEW
  ↓ register (Spec #001)
PENDING_VERIFY
  ↓ verified email
PENDING_DOCUMENTS
  ↓ complete GPLX + vehicle registration + three vehicle photos
PENDING_DEPOSIT
  ↓ verified successful VNPay IPN
PENDING_APPROVAL
  ├─ Manager approve ─────────→ ACTIVE
  └─ Manager reject ──────────→ REJECTED

ACTIVE ── Admin/financial enforcement ──→ SUSPENDED
```

Không có state `PENDING_VEHICLE`. Vehicle completion là condition trong `PENDING_DOCUMENTS`.
Mọi transition ngoài bảng SHALL trả HTTP 409 theo HR-05.

### Document Status

```text
DRAFT → SUBMITTED → APPROVED
                  └→ REJECTED
```

### Deposit Status

```text
PENDING
  ├─ verified success IPN → COMPLETED
  ├─ provider failure ────→ FAILED
  └─ timeout 15 phút ─────→ EXPIRED

COMPLETED ── financial offboarding spec ──→ REFUNDED
```

---

## Error Matrix

| HTTP | `error_code` | Khi nào |
|------|--------------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn |
| 403 | `FORBIDDEN` | Role không phải Driver |
| 404 | `ONBOARDING_PROFILE_NOT_FOUND` | Driver/profile không tồn tại |
| 409 | `INVALID_ONBOARDING_STEP` | Skip/sai current state |
| 409 | `LICENSE_NUMBER_ALREADY_USED` | GPLX trùng |
| 409 | `LICENSE_PLATE_ALREADY_USED` | Biển số trùng |
| 409 | `ONBOARDING_DOCUMENTS_INCOMPLETE` | Thiếu asset |
| 409 | `DEPOSIT_ALREADY_PAID` | Cọc đã completed |
| 422 | `VALIDATION_ERROR` | Metadata sai |
| 422 | `INVALID_FILE` | MIME/size/magic byte sai |
| 422 | `UNSUPPORTED_DOCUMENT_TYPE` | CCCD/selfie/type ngoài scope |
| 429 | `RATE_LIMITED` | Vượt upload/initiate/status rate |
| 502 | `CLOUDINARY_UNAVAILABLE` | Provider upload lỗi |

VNPay IPN lỗi dùng `RspCode` contract của gateway thay vì HTTP Customer-facing error.

---

## Detailed API Contracts

### Onboarding Status Response

```json
{
  "driver_id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "status": "PENDING_DEPOSIT",
  "status_label": "Chờ đặt cọc",
  "current_step": 3,
  "total_steps": 4,
  "next_action": "PAY_DEPOSIT",
  "redirect": "/driver/register-step3-deposit.html",
  "completed_steps": [
    "ACCOUNT",
    "EMAIL_VERIFIED",
    "DOCUMENTS_AND_VEHICLE"
  ],
  "missing_items": [],
  "last_updated_at": "2026-06-04T10:30:00Z"
}
```

Response SHALL không trả document number, raw URLs, deposit transaction reference hoặc Manager
internal notes. `redirect` được server map từ status, không nhận từ query/client.

### Upload Signature Request/Response

```json
{
  "document_type": "DRIVING_LICENSE",
  "image_role": "FRONT",
  "content_type": "image/webp",
  "size": 845221
}
```

```json
{
  "cloud_name": "move-home",
  "api_key": "public-api-key",
  "timestamp": 1780540200,
  "expires_at": "2026-06-04T10:40:00Z",
  "folder": "movehome/drivers/5af5e878-52b0-4fb8-a9cb-8af517594e89/DRIVING_LICENSE/FRONT",
  "signature": "signed-value"
}
```

API secret SHALL không xuất hiện trong response. Signature SHALL bind folder/type/role/timestamp.

### Upload Confirm Request/Response

```json
{
  "document_type": "DRIVING_LICENSE",
  "image_role": "FRONT",
  "cloudinary_public_id": "movehome/drivers/5af5/DRIVING_LICENSE/FRONT/a1",
  "cloudinary_secure_url": "https://res.cloudinary.com/move-home/image/upload/v1/a1.webp",
  "bytes": 845221,
  "format": "webp",
  "version": 1,
  "signature": "cloudinary-response-signature"
}
```

```json
{
  "document_id": "6bf6e878-52b0-4fb8-a9cb-8af517594e89",
  "document_type": "DRIVING_LICENSE",
  "image_role": "FRONT",
  "status": "DRAFT",
  "message": "Ảnh đã được tải lên."
}
```

### Vehicle Success Response

```json
{
  "vehicle_id": "7cf6e878-52b0-4fb8-a9cb-8af517594e89",
  "license_plate": "30E-56789",
  "vehicle_type": "TRUCK_1T",
  "status": "PENDING_REVIEW",
  "driver_status": "PENDING_DEPOSIT",
  "next_step": "PAY_DEPOSIT"
}
```

### Deposit IPN Contract

IPN adapter SHALL parse canonical VNPay fields, sort parameters theo gateway rule và verify
HMAC-SHA512 bằng secret env. Domain service chỉ nhận verified command:

```json
{
  "txn_ref": "DRVDEP-20260604-000001",
  "amount": 3000000,
  "response_code": "00",
  "transaction_no": "14581234",
  "pay_date": "20260604103000"
}
```

Return URL endpoint chỉ trả display DTO:

```json
{
  "message": "Move_home đang xác nhận giao dịch đặt cọc.",
  "authoritative_status": "PENDING",
  "poll_url": "/api/driver/onboarding/status"
}
```

---

## Transition & Access Matrix

| Current `app_user.status` | Allowed Driver action | Success state | Invalid action result |
|---------------------------|-----------------------|---------------|-----------------------|
| `PENDING_VERIFY` | Verify email/resend | `PENDING_DOCUMENTS` | Other onboarding API 409/403 |
| `PENDING_DOCUMENTS` | Upload/confirm docs, submit GPLX/vehicle | `PENDING_DEPOSIT` when complete | Deposit initiate 409 |
| `PENDING_DEPOSIT` | View/initiate deposit | `PENDING_APPROVAL` only via IPN | Document/vehicle submit 409 |
| `PENDING_APPROVAL` | View/poll status, logout | `ACTIVE|REJECTED` by Manager spec | Deposit/document submit 409 |
| `ACTIVE` | Enter Driver workflow | unchanged | Onboarding mutation 409 |
| `REJECTED` | View reason/support | defer resubmit spec | Driver workflow blocked |
| `SUSPENDED` | View support/financial requirement | defer financial spec | Driver workflow blocked |

All state transitions SHALL lock `app_user` row or use optimistic versioning. Audit insert and
state update execute trong cùng transaction.

---

## Validation Matrix

| Field | Normalize | Constraint | Error |
|-------|-----------|------------|-------|
| `license_number` | Uppercase, remove outer spaces | 8-20 alphanumeric | 422 |
| `license_class` | Uppercase | `B1|B2|C|D` | 422 |
| `license_expiry_date` | ISO date | `>= today + 90 days` | 422 |
| `license_plate` | Uppercase, remove spaces | regex `^[0-9]{2}[A-Z]{1,2}-[0-9]{4,5}$` | 422/409 |
| `vehicle_type` | No mutation | `TRUCK_500KG|TRUCK_1T|TRUCK_15T` | 422 |
| `manufacture_year` | Integer | `2010..current year` | 422 |
| `color` | Unicode NFC + trim | 1-30 chars | 422 |
| image MIME | Magic bytes | JPEG/PNG/WebP | 422 |
| image size | Integer bytes | 1-1.572.864 | 422 |
| deposit amount | Server constant | exactly 3.000.000 VND | client value ignored/rejected |

Year upper bound is application validation because PostgreSQL CHECK constraints cannot safely use
volatile current time. Database enforces lower bound; service enforces current-year maximum.

---

## Transaction & Concurrency Boundaries

### Step 2 Completion

```text
BEGIN
  lock app_user Driver
  validate status=PENDING_DOCUMENTS
  validate submitted GPLX front/back
  validate registration + front/rear/side vehicle assets
  validate unique license number + normalized plate
  insert driver_vehicle
  mark six driver_document rows SUBMITTED
  update app_user status=PENDING_DEPOSIT
  insert onboarding audit event
  insert outbox email event
COMMIT
```

Cloudinary uploads occur trước DB completion transaction. Orphan draft assets older than 24 hours
SHALL được cleanup async; cleanup lỗi không rollback completed onboarding step.

### Deposit Initiate

```text
BEGIN
  lock Driver
  validate status=PENDING_DEPOSIT
  check no completed deposit
  resolve/replay idempotency key
  insert driver_deposit(PENDING, amount=3000000, expires_at=now+15m)
COMMIT
generate signed VNPay URL
```

Nếu VNPay URL generation fail sau insert, mark attempt `FAILED` bằng compensating transaction;
Driver có thể retry với idempotency key mới.

### Deposit IPN

```text
verify HMAC outside domain transaction
BEGIN
  lock driver_deposit by txn_ref
  lock app_user Driver
  validate PENDING + amount + response code + Driver state
  complete deposit
  update collateral snapshot
  transition Driver to PENDING_APPROVAL
  append money transaction
  append onboarding audit
  append email outbox event
COMMIT
```

Unique `vnpay_txn_ref`, completed-deposit partial index và row locks bảo vệ exactly-once.

---

## Migration & Rollout Plan

1. Tạo additive Flyway migration cho profile extensions và ba bảng mới.
2. Backfill vehicle legacy fields từ `driver_profile` sang `driver_vehicle` cho seed Drivers.
3. Backfill completed deposits từ `driver_profile.deposit_amount=3000000` và V6 transaction.
4. Validate không có duplicate normalized license plate/license number.
5. Deploy status/read APIs và audit trước mutation endpoints.
6. Deploy signed upload + Step 2 transaction.
7. Deploy deposit initiate/IPN với VNPay sandbox.
8. Thay progress UI 1/3 thành 1/4 và bật feature flag cho test Drivers.
9. Chạy reconciliation, concurrency và security tests trước rollout toàn bộ.

Backfill vehicle example:

```sql
INSERT INTO driver_vehicle
    (driver_id, license_plate, vehicle_type, manufacture_year, color, is_primary, status)
SELECT dp.user_id,
       dp.vehicle_plate,
       CASE
           WHEN dp.vehicle_capacity_kg <= 500 THEN 'TRUCK_500KG'
           WHEN dp.vehicle_capacity_kg <= 1000 THEN 'TRUCK_1T'
           ELSE 'TRUCK_15T'
       END,
       2010,
       'Chưa cập nhật',
       TRUE,
       'APPROVED'
FROM driver_profile dp
WHERE dp.vehicle_plate IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM driver_vehicle dv WHERE dv.driver_id = dp.user_id
  );
```

Backfill defaults phải được Manager rà soát; không dùng manufacture year giả cho production mà
không gắn cờ data-quality.

---

## Acceptance Criteria

**AC1**
Driver register theo Spec #001 tạo `PENDING_VERIFY` + profile, password BCrypt và verification email.

**AC2**
Email verify hợp lệ chuyển đúng sang `PENDING_DOCUMENTS`; invalid/expired token không transition.

**AC3**
Step 2 chỉ hoàn tất khi có GPLX front/back, đăng ký xe và ba ảnh xe hợp lệ.

**AC4**
GPLX hết hạn hoặc còn dưới 90 ngày, duplicate license/plate và file quá 1,5 MB bị từ chối.

**AC5**
Hoàn tất documents+vehicle chuyển đúng một lần sang `PENDING_DEPOSIT`, có audit.

**AC6**
Deposit initiate luôn dùng 3.000.000 VND server-side và URL hết hạn sau 15 phút.

**AC7**
Chỉ IPN HMAC hợp lệ, amount/reference đúng mới chuyển `PENDING_APPROVAL` và ghi tiền đúng một lần.

**AC8**
Duplicate IPN không duplicate deposit, `transaction` hoặc transition.

**AC9**
Pending approval polling nhận `ACTIVE` thì redirect Driver home; Driver chưa ACTIVE bị chặn.

**AC10**
Mọi transition có audit và email lỗi không rollback.

**AC11**
Bốn màn hình có progress 1/4-4/4, brand Move_home, tiếng Việt và Loading/Empty/Error.

---

## Edge Cases & Error Handling

| ID | Tình huống | Expected Behavior |
|----|------------|-------------------|
| EC-01 | Driver verify email hai lần | Lần hai idempotent/no duplicate transition |
| EC-02 | Upload SVG đổi extension JPG | Magic-byte reject 422 |
| EC-03 | Cloudinary upload xong, confirm fail | Draft không ghi; orphan cleanup async |
| EC-04 | Hai tab submit vehicle | Một success; request thua 409/idempotent replay |
| EC-05 | Manufacture year tương lai | 422 |
| EC-06 | GPLX còn đúng 89 ngày | 422 |
| EC-07 | Plate khác spaces/case nhưng cùng xe | Unique normalized plate chặn |
| EC-08 | Driver sửa payload amount deposit | Backend bỏ qua/422; luôn 3M |
| EC-09 | VNPay return URL giả success | Chỉ hiển thị pending; DB không đổi |
| EC-10 | IPN hash sai | RspCode 97; không lookup/mutate |
| EC-11 | Hai IPN success đồng thời | Row lock + unique transaction cho một commit |
| EC-12 | Deposit timeout rồi IPN success muộn | Payment spec reconciliation; không auto transition |
| EC-13 | Manager approve trong lúc status polling | Poll kế tiếp redirect an toàn |
| EC-14 | Reminder job chạy hai instance | Lock/idempotency chỉ một email |
| EC-15 | Driver gọi home ở PENDING_APPROVAL | 403/redirect pending approval |

---

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TC-01 | Register Driver hợp lệ | PENDING_VERIFY, profile, audit, email queued |
| TC-02 | Verify email token hợp lệ | PENDING_DOCUMENTS, redirect Step 2 |
| TC-03 | Submit GPLX expiry +89 ngày | 422, status không đổi |
| TC-04 | Upload đủ six image roles + valid vehicle | PENDING_DEPOSIT |
| TC-05 | Submit normalized duplicate plate | 409 |
| TC-06 | Initiate deposit hai lần cùng key | Cùng deposit/payment response |
| TC-07 | Valid VNPay IPN 3M | Completed deposit, PENDING_APPROVAL, one transaction |
| TC-08 | Duplicate valid IPN | Không duplicate money/audit |
| TC-09 | Invalid HMAC/amount | Không transition |
| TC-10 | Status changes to ACTIVE during polling | Welcome + redirect home |
| TC-11 | Non-Driver calls onboarding | 403 |
| TC-12 | Incomplete Driver >7 days | Một reminder, no duplicate |

### Required Automated Test Layers

1. Unit tests cho state guard, document checklist, vehicle validation và deposit amount.
2. Testcontainers/PostgreSQL tests cho unique normalized plate, partial indexes và IPN row lock.
3. Integration tests cho register reference, resume, upload confirm, vehicle submit và IPN.
4. Contract tests cho Cloudinary/VNPay adapter và ES-04 errors.
5. Frontend tests cho progress, resume, polling và Loading/Empty/Error.
6. Security tests cho HMAC, signed upload, ownership và log redaction.
7. CORE coverage tối thiểu 70% theo ES-05.

---

## Frontend Screen Contract

| Screen | Canonical behavior |
|--------|--------------------|
| `register-step1.html` | Account info + progress 1/4; submit Spec #001 |
| `register-step2.html` | GPLX + registration + three vehicle photos + progress 2/4 |
| `register-step3-deposit.html` | Fixed 3M VNPay deposit + progress 3/4 |
| `pending-approval.html` | Timeline/polling/status/support + progress 4/4 |

Frontend SHALL không lưu onboarding state authoritative trong localStorage, không hardcode
approval, không update deposit từ return URL và không hiển thị CCCD/selfie như required.

---

## Privacy, Security & Observability

1. Document URLs là signed/authorized; không expose public raw assets.
2. Logs không chứa HMAC, password, full document numbers hoặc raw Cloudinary signature.
3. GPLX/vehicle assets chỉ Driver owner, Manager và Admin được xem.
4. Deposit amount/ref/status audit append-only; correction qua financial spec.
5. Metrics không label email, phone, plate hoặc document number.

| Metric | Type | Labels |
|--------|------|--------|
| `driver_onboarding_transition_total` | Counter | `from`, `to`, `outcome` |
| `driver_document_upload_total` | Counter | `document_type`, `outcome` |
| `driver_deposit_initiate_total` | Counter | `outcome` |
| `driver_deposit_ipn_total` | Counter | `rsp_code`, `outcome` |
| `driver_onboarding_status_duration_seconds` | Histogram | `status`, `outcome` |
| `driver_onboarding_reminder_total` | Counter | `status`, `outcome` |

Alerts: IPN HMAC failures spike; pending deposits timeout rate >20%; upload failure >10%;
`PENDING_APPROVAL` oldest age >3 business days; invalid transition spike.

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-01 | VNPay/Cloudinary/Gmail secrets chỉ env |
| HR-02 | Register password BCrypt theo Spec #001 |
| HR-03/04 | IPN verified là nguồn deposit duy nhất |
| HR-05 | Invalid onboarding transition trả 409 |
| HR-10 | Driver ownership + Manager/Admin read boundary |
| HR-11 | Email async không rollback |
| HR-12 | Driver tự đăng ký bốn bước |
| HR-13 | Mọi state change có audit |
| HR-15 | Deposit IPN/idempotency |
| HR-18 | Collateral không âm, money audit |
| HR-19/20 | Brand + tiếng Việt có dấu |
| HR-21 | Tên bảng tránh reserved words |
| AC-08 | Deposit NUMERIC/BigDecimal scale=0 |
| AC-10 | Cloudinary signed upload |
| AC-12 | Schema qua Flyway |
| AC-13 | Deposit có append-only transaction |
| AC-14 | Status VARCHAR + CHECK |
| AC-16 | Loading/Empty/Error |
| ES-03/04/05 | Validation, errors, CORE tests |

---

## Out of Scope (Deferred)

1. Manager approval UI/rejection/resubmit implementation.
2. Driver workflow sau ACTIVE.
3. Deposit refund, replenishment và Driver wallet operations.
4. CCCD/selfie/OCR/background verification.
5. Xe thứ hai và chỉnh sửa xe sau onboarding.

---

## Open Questions

1. Chốt Manager Approval spec number và SLA chính thức 1-2 hay 1-3 ngày làm việc.
2. Chốt có bổ sung CCCD/selfie sau khi amendment `CONTEXT.md`.
3. Chốt vehicle license class compatibility matrix với ba vehicle types.
4. Chốt xử lý IPN success đến muộn sau deposit `EXPIRED`.
5. Chốt migration từ `driver_profile.vehicle_*` legacy sang `driver_vehicle`.
