# Feature Specification: Customer Profile & Wallet

**Feature Branch:** `004-customer-profile-wallet`  
**Feature Number:** #4 of 30 — SUPPORT (account management + financial activity)  
**Created:** 2026-06-04  
**Version:** 1.0.0  
**Status:** Draft  
**Sprint Target:** Sprint 2 (profile + password); Sprint 4 (payment activity)

**CONTEXT.md reference:** v2.0 §2 Payment, §2 Wallet & Commission, §3 RBAC  
**Constitution reference:** v1.3.0 — HR-01, HR-02, HR-10, HR-11, HR-13, HR-16,
HR-18, HR-19, HR-20, HR-21, AC-03, AC-07, AC-08, AC-09, AC-10, AC-11, AC-12,
AC-13, AC-14, AC-15, AC-16, ES-03, ES-04, ES-05  
**Screen reference:** `docs/SCREEN_INVENTORY.md` — Customer screens 3.14 đến 3.17  
**Related specs:** Spec #001 Auth/RBAC; Spec #002 Booking; Spec #003 Customer Orders;
Payment spec (future)

---

## Goals

Cho phép Customer đã đăng nhập xem và quản lý thông tin cá nhân an toàn: họ tên, số điện thoại
và ảnh đại diện; email là định danh đăng nhập nên chỉ đọc. Customer có thể đổi mật khẩu bằng
cách xác minh mật khẩu hiện tại, đáp ứng password policy, thu hồi mọi refresh token và đăng nhập
lại. Mọi thay đổi bảo mật phải được audit, gửi cảnh báo email bất đồng bộ và không bao giờ để lộ
mật khẩu plaintext.

Màn hình `my-wallet.html` được giữ để đồng bộ inventory, nhưng theo `CONTEXT.md v2.0`, Move_home
không có ví Customer. Màn này phải hoạt động như "Lịch sử thanh toán": hiển thị tổng tiền Customer
đã thanh toán qua VNPay, tổng RefundRecord đã xử lý và danh sách transaction append-only. Nó
không hiển thị số dư có thể chi tiêu, không cho nạp/rút và không dùng tiền trong hệ thống để trả
đơn. Driver wallet là module khác, không thuộc spec này.

Spec định nghĩa REST contracts, validation, signed Cloudinary upload, pagination, audit trail,
privacy và Flyway migration cần thiết. Bốn màn hình phải dùng Move_home forest green `#1B4D3E`,
amber `#F5A623`, Be Vietnam Pro, tiếng Việt có dấu và đủ Loading/Empty/Error states. Money dùng
VND nguyên đồng, timestamps lưu UTC và hiển thị theo `Asia/Ho_Chi_Minh`.

---

## Source-of-Truth Resolution

> Hierarchy áp dụng: `CONTEXT.md v2.0` → Constitution v1.3.0 → Specs #001-003 →
> spec này → `SCREEN_INVENTORY.md`/UI stubs → Code.

| Chủ đề | Quyết định canonical | Hệ quả triển khai |
|--------|----------------------|-------------------|
| Customer wallet | Không tồn tại | Không tạo `customer_wallet`, balance hoặc top-up |
| `my-wallet.html` | Màn lịch sử thanh toán chỉ đọc | Đổi title/labels, bỏ balance và "Nạp tiền" |
| Thanh toán Customer | 100% qua VNPay | Đọc audit từ `transaction`; IPN thuộc Payment spec |
| Refund Customer | Chuyển khoản thủ công qua RefundRecord | Chỉ hiển thị khi refund `PROCESSED` |
| Driver wallet | Có balance/deposit theo CONTEXT | Ngoài scope, không expose cho Customer |
| Email | Không editable | Thay đổi email defer vì là identity/login |
| Avatar | Signed Cloudinary upload | Backend ký request; không lưu Base64/BLOB |
| Screen paths | `my-profile.html`, `my-profile-edit.html`, `change-password.html`, `my-wallet.html` | Dùng đúng file thực tế, không tạo aliases |
| Transaction storage | Bảng `transaction` hiện có là append-only audit | Không tạo `wallet_transaction` cho Customer |
| Profile avatar schema | `app_user.avatar_url` qua migration mới | Không sửa V1 đã chạy |

---

## Scope Summary

**In scope:**

1. `GET /api/customer/profile` — profile và account summary.
2. `GET /api/customer/profile/edit-form` — dữ liệu form hiện tại.
3. `PATCH /api/customer/profile` — cập nhật `full_name`, `phone`, `avatar_url`.
4. `POST /api/customer/avatar/signature` — tạo signed Cloudinary upload parameters.
5. `POST /api/customer/avatar/confirm` — xác nhận asset và lưu URL.
6. `POST /api/customer/change-password` — đổi mật khẩu + revoke sessions.
7. `GET /api/customer/payment-activity/summary` — tổng tiền đã thanh toán/refund.
8. `GET /api/customer/payment-activity/transactions` — lịch sử server-side pagination.
9. Audit cho profile, avatar và password.
10. Flyway migration bổ sung avatar metadata vào `app_user`.
11. Empty/Loading/Error states cho bốn màn hình.

**Out of scope:**

1. Customer wallet balance, top-up, withdrawal hoặc pay-from-wallet.
2. VNPay IPN/reconciliation — Payment spec.
3. Tạo/xử lý RefundRecord — Spec #007/Manager operations.
4. Driver wallet, earning, deposit và withdrawal.
5. Thay đổi email.
6. Phone OTP verification.
7. Crop editor ảnh phía frontend; Cloudinary transformation xử lý crop.
8. Admin sửa profile Customer.

---

## User Stories

**P1:**

**US1:** Là Customer, tôi xem được họ tên, email, số điện thoại, avatar, trạng thái và ngày tham
gia để xác nhận thông tin tài khoản.

**US2:** Là Customer, tôi cập nhật họ tên, số điện thoại và avatar khi thông tin thay đổi.

**US3:** Là Customer, tôi tải ảnh đại diện JPG/PNG/WebP tối đa 5 MB lên Cloudinary an toàn.

**US4:** Là Customer, tôi đổi mật khẩu bằng mật khẩu hiện tại và bị đăng xuất khỏi mọi session
sau khi đổi thành công.

**US5:** Là Customer, tôi xem tổng tiền đã thanh toán và tổng tiền đã hoàn mà không bị hiểu nhầm
rằng hệ thống có ví Customer.

**US6:** Là Customer, tôi xem lịch sử giao dịch VNPay/refund có filter và pagination.

**P2:**

**US7:** Là Customer, tôi nhận email cảnh báo sau khi profile hoặc mật khẩu thay đổi.

**US8:** Là Customer, tôi thấy trạng thái thanh toán gần nhất cập nhật từ nguồn IPN đáng tin cậy.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven) | WHILE (state-driven) | WHERE (error/unwanted) |
> IF/THEN (optional branch). Mọi FR có ít nhất một keyword EARS và contract cụ thể.

---

### Nhóm 1 — Profile View (FR-001..FR-005)

**FR-001**
WHEN Customer `ACTIVE` gọi `GET /api/customer/profile`, THE system SHALL lấy user id từ JWT,
query `app_user` với `role='CUSTOMER'` và `deleted_at IS NULL`, trả HTTP 200:

```json
{
  "id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "full_name": "Nguyễn Văn An",
  "email": "an.nguyen@example.com",
  "phone": "+84901234567",
  "avatar_url": null,
  "email_verified": true,
  "status": "ACTIVE",
  "created_at": "2026-04-15T03:00:00Z",
  "total_orders": 12
}
```

**FR-002**
WHEN `my-profile.html` nhận profile, THE frontend SHALL hiển thị avatar, họ tên, email, số điện
thoại, ngày tham gia, tổng đơn và badge trạng thái; SHALL không hiển thị Customer wallet balance.

**FR-003**
IF `avatar_url` null hoặc ảnh load lỗi, THEN frontend SHALL hiển thị initials từ `full_name`
trong avatar forest green; SHALL không dùng logo hoặc external placeholder không kiểm soát.

**FR-004**
WHEN render account status, THE frontend SHALL map `ACTIVE` thành "Đang hoạt động" màu green và
`PENDING_VERIFY` thành "Chờ xác thực email" màu amber; technical status không hiển thị trực tiếp.

**FR-005**
WHILE profile fetch, frontend SHALL hiển thị skeleton; WHERE API lỗi, SHALL hiển thị
"Không thể tải thông tin cá nhân" + "Thử lại"; buttons "Chỉnh sửa" và "Đổi mật khẩu" SHALL trỏ
đúng `my-profile-edit.html` và `change-password.html`.

---

### Nhóm 2 — Profile Edit & Avatar (FR-006..FR-012)

**FR-006**
WHEN Customer gọi `GET /api/customer/profile/edit-form`, THE system SHALL trả `full_name`,
`phone`, `avatar_url`, `email` và `version`; email SHALL mang flag `editable=false`.

**FR-007**
WHEN Customer submit `PATCH /api/customer/profile` với body
`{"full_name":"Nguyễn Văn An","phone":"0901234567","avatar_url":"https://res.cloudinary.com/move-home/image/upload/v1/customer-avatar/a.jpg","version":3}`,
THE system SHALL normalize phone về `+84901234567`, update allowed fields và trả HTTP 200.

**FR-008**
WHEN validate profile, THE system SHALL enforce:

| Field | Constraint |
|-------|------------|
| `full_name` | Required; trim; 2-100 Unicode letters/marks/spaces; regex `^[\p{L}\p{M}]+(?:[ '\-][\p{L}\p{M}]+)*$` |
| `phone` | Required; regex `^(0|\+84)(3|5|7|8|9)[0-9]{8}$`; normalize `+84` |
| `avatar_url` | Nullable; HTTPS URL thuộc allowlist `res.cloudinary.com/<cloud_name>/...` |
| `version` | Required non-negative integer cho optimistic locking |

WHERE bất kỳ field sai, SHALL trả HTTP 422 với tất cả field errors theo ES-04.

**FR-009**
WHERE request PATCH chứa `email`, `role`, `status`, `email_verified`, `created_at` hoặc field
không allowlist, THE system SHALL trả HTTP 422 `IMMUTABLE_FIELD`; không silently ignore.

**FR-010**
WHEN Customer cần upload avatar, THE frontend SHALL validate file tối đa 5 MB và MIME
`image/jpeg|image/png|image/webp`, rồi gọi `POST /api/customer/avatar/signature` với
`{"file_name":"avatar.webp","content_type":"image/webp","size":245000}`.

**FR-011**
WHEN avatar metadata hợp lệ, THE backend SHALL trả signed Cloudinary params có TTL 10 phút,
folder `move_home/customer-avatar/<customer_id>`, transformation
`c_fill,g_face,w_400,h_400,q_auto,f_auto`; WHERE file sai MIME/size, SHALL trả HTTP 422.

**FR-012**
WHEN direct upload thành công, frontend SHALL gọi `POST /api/customer/avatar/confirm` với
`public_id`, `secure_url`, `version`, `signature`; backend SHALL verify signature/ownership,
update `avatar_url`, insert `PROFILE_AVATAR_UPDATED` audit và trả success message
"Cập nhật ảnh đại diện thành công".

---

### Nhóm 3 — Change Password (FR-013..FR-019)

**FR-013**
WHEN Customer submit `POST /api/customer/change-password`, request SHALL có:

```json
{
  "current_password": "OldPassword1!",
  "new_password": "NewPassword2@",
  "confirm_password": "NewPassword2@"
}
```

và backend SHALL không log request body.

**FR-014**
WHEN validate password request, THE system SHALL enforce current password required; new password
8-64 ký tự, có ít nhất một chữ hoa, chữ thường, số và ký tự đặc biệt; confirm phải match.

**FR-015**
WHERE `current_password` không BCrypt-match `app_user.password_hash`, THE system SHALL tăng
security failure counter, trả HTTP 422 `CURRENT_PASSWORD_INCORRECT` với message
"Mật khẩu hiện tại không đúng", và không tiết lộ hash.

**FR-016**
WHERE new password BCrypt-match current password, THE system SHALL trả HTTP 422
`PASSWORD_REUSE_NOT_ALLOWED`; WHERE confirm khác new password, SHALL trả HTTP 422 tại
`confirm_password`; không update DB.

**FR-017**
WHEN request hợp lệ, THE system SHALL trong một transaction hash new password bằng BCrypt cost
12, update `app_user.password_hash`, revoke toàn bộ refresh tokens của Customer, insert
`PASSWORD_CHANGED` audit event và trả HTTP 200 `{"force_relogin":true}`.

**FR-018**
WHEN frontend nhận `force_relogin=true`, THE frontend SHALL xóa `accessToken`, `refreshToken`
legacy nếu có và cached user khỏi localStorage, gọi logout cookie cleanup, rồi redirect
`/pages/login.html?reason=password_changed`.

**FR-019**
WHERE Customer vượt 3 failed change-password attempts trong rolling 1 giờ, THE system SHALL trả
HTTP 429 `RATE_LIMITED`; WHEN password đổi thành công, SHALL enqueue email cảnh báo tiếng Việt
qua async service và email lỗi không rollback transaction.

---

### Nhóm 4 — Payment Activity Summary (FR-020..FR-024)

**FR-020**
WHEN Customer gọi `GET /api/customer/payment-activity/summary`, THE system SHALL aggregate chỉ
completed financial transactions có `user_id` từ JWT và trả:

```json
{
  "wallet_supported": false,
  "available_balance": null,
  "total_paid": 4750000,
  "total_refunded": 200000,
  "net_paid": 4550000,
  "last_transaction_at": "2026-06-04T10:30:00Z"
}
```

**FR-021**
WHEN tính summary, THE system SHALL dùng:
`total_paid = ABS(SUM(amount WHERE type='ORDER_PAYMENT' AND amount < 0))`,
`total_refunded = SUM(amount WHERE type='REFUND' AND amount > 0)`,
`net_paid = total_paid - total_refunded`; money trả về integer VND scale=0.

**FR-022**
WHERE không có transaction, THE system SHALL trả các total bằng `0`, `last_transaction_at=null`,
không trả 404 và không tạo wallet row.

**FR-023**
WHEN `my-wallet.html` render, THE frontend SHALL đổi heading thành "Lịch sử thanh toán", hiển thị
cards "Tổng đã thanh toán", "Tổng đã hoàn", "Chi phí ròng"; SHALL ẩn số dư và button "Nạp tiền".

**FR-024**
WHERE UI/runtime cố gọi endpoint `/api/customer/wallet/topup` hoặc
`/api/customer/wallet`, THE backend SHALL trả HTTP 404/feature-not-supported và không tạo giao
dịch; frontend production SHALL không chứa CTA top-up.

---

### Nhóm 5 — Transaction History (FR-025..FR-031)

**FR-025**
WHEN Customer gọi
`GET /api/customer/payment-activity/transactions?page=0&size=20&type=ALL`,
THE system SHALL query append-only `transaction` rows theo `user_id` JWT, sort
`created_at DESC, id DESC`, và trả Spring Page metadata chuẩn.

**FR-026**
WHEN filter `type` là `ALL|ORDER_PAYMENT|REFUND`, THE system SHALL áp dụng filter tương ứng;
WHERE filter khác hoặc type thuộc Driver-only, SHALL trả HTTP 422 `VALIDATION_ERROR`.

**FR-027**
WHEN serialize transaction, THE system SHALL trả `id`, localized `type_label`, signed `amount`,
`related_order_id`, `order_code`, `description`, `vnpay_txn_ref_masked`, `created_at`; SHALL
không trả raw VNPay secrets hoặc metadata IPN.

**FR-028**
WHEN history có dữ liệu, frontend SHALL render columns `Ngày`, `Loại`, `Mô tả`, `Mã đơn`,
`Trạng thái`, `Số tiền`; amount dương màu green có `+`, amount âm màu red có `-`.

**FR-029**
WHEN pagination render, frontend SHALL có page numbers + ellipsis, Previous/Next, selector
`10|20|50|100`, và text "Hiển thị X-Y trong Z giao dịch"; đổi size SHALL reset page về 0.

**FR-030**
WHERE history rỗng, frontend SHALL hiển thị "Chưa có giao dịch thanh toán nào"; WHILE fetch,
SHALL render skeleton; WHERE API lỗi, SHALL hiển thị "Không thể tải lịch sử thanh toán" +
button "Tải lại".

**FR-031**
WHEN Customer click order code, frontend SHALL mở `order-detail.html?id=<related_order_id>`;
IF transaction không có related order, THEN SHALL hiển thị "Không có" và không render link.

---

### Nhóm 6 — Unsupported Customer Wallet/Top-up Boundary (FR-032..FR-036)

**FR-032**
WHERE Customer gọi `POST /api/customer/wallet/topup`, THE system SHALL trả HTTP 404
`FEATURE_NOT_SUPPORTED` với message "Move_home không hỗ trợ ví khách hàng hoặc nạp tiền".

**FR-033**
WHERE Customer gọi endpoint debit/withdraw Customer wallet, THE system SHALL trả HTTP 403
`FORBIDDEN`; no balance, transaction hoặc payment intent SHALL được tạo.

**FR-034**
WHEN Customer thanh toán order, THE system SHALL dùng VNPay flow của Payment spec và ghi
`ORDER_PAYMENT` append-only transaction sau IPN hợp lệ; SHALL không đọc/trừ Customer balance.

**FR-035**
WHEN Customer nhận refund đã xử lý, THE system SHALL ghi `REFUND` audit transaction dương để
hiển thị lịch sử; actual refund vẫn là chuyển khoản thủ công, không cộng vào wallet.

**FR-036**
IF product owner muốn thêm Customer wallet trong tương lai, THEN phải có amendment
`CONTEXT.md` + Constitution, spec/migration riêng và security review; spec này SHALL không được
dùng như approval ngầm cho feature đó.

---

### Nhóm 7 — RBAC, Ownership & Audit (FR-037..FR-039)

**FR-037**
WHERE JWT thiếu/hết hạn, THE system SHALL trả HTTP 401 `AUTHENTICATION_REQUIRED`; WHERE role
khác `CUSTOMER`, SHALL trả HTTP 403 `FORBIDDEN`; mọi endpoint lấy customer id từ JWT.

**FR-038**
WHERE request chứa customer/user id khác JWT subject, THE system SHALL ignore no field and trả
HTTP 403 `PROFILE_OWNERSHIP_REQUIRED`; Admin read-only profile thuộc Admin detail spec, không dùng
endpoint Customer.

**FR-039**
WHEN profile, avatar hoặc password thay đổi, THE system SHALL insert immutable audit event với
actor, event type, changed field names, timestamp UTC và IP; SHALL không lưu password, old/new
phone đầy đủ hoặc signed Cloudinary secret trong metadata.

---

## Non-Functional Requirements

**NFR-001**
Profile view/edit API SHALL có P90 dưới 500 ms ở 50 request/giây.

**NFR-002**
Signed avatar upload + Cloudinary transformation SHALL hoàn tất dưới 5 giây P90 trên mạng 4G.

**NFR-003**
Password change SHALL phản hồi dưới 2 giây P90; email async không tính vào latency.

**NFR-004**
Payment summary SHALL khớp transaction audit 100%; không cache quá 30 giây.

**NFR-005**
Transaction history page 20 items SHALL có P90 dưới 500 ms với 10.000 rows/Customer.

**NFR-006**
Email security alert SHALL async, retry tối đa 3 lần và không rollback profile/password update.

---

## API Endpoints Summary

| Method | Endpoint | Request | Success | Auth |
|--------|----------|---------|---------|------|
| GET | `/api/customer/profile` | none | 200 profile DTO | Customer |
| GET | `/api/customer/profile/edit-form` | none | 200 edit DTO | Customer |
| PATCH | `/api/customer/profile` | name, phone, avatar, version | 200 profile DTO | Customer |
| POST | `/api/customer/avatar/signature` | file metadata | 200 signed params | Customer |
| POST | `/api/customer/avatar/confirm` | Cloudinary asset metadata | 200 profile DTO | Customer |
| POST | `/api/customer/change-password` | current/new/confirm | 200 force relogin | Customer |
| GET | `/api/customer/payment-activity/summary` | none | 200 summary DTO | Customer |
| GET | `/api/customer/payment-activity/transactions` | page,size,type | 200 Page DTO | Customer |

### Standard Error

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ.",
  "details": [
    {"field": "phone", "message": "Số điện thoại Việt Nam không hợp lệ."}
  ],
  "timestamp": "2026-06-04T10:30:00Z",
  "path": "/api/customer/profile"
}
```

---

## Data Model

### Migration Strategy

Không sửa V1/V6 đã chạy. Migration mới chỉ bổ sung avatar/version profile và indexes phục vụ
payment activity. Không tạo `customer_wallet` hoặc `wallet_transaction` cho Customer.

### Extend `app_user`

```sql
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS avatar_public_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS profile_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_avatar_https
    CHECK (avatar_url IS NULL OR avatar_url LIKE 'https://res.cloudinary.com/%');
```

Profile update query:

```sql
UPDATE app_user
SET full_name = :full_name,
    phone = :normalized_phone,
    avatar_url = :avatar_url,
    profile_version = profile_version + 1
WHERE id = :customer_id
  AND role = 'CUSTOMER'
  AND deleted_at IS NULL
  AND profile_version = :expected_version;
```

Update row count bằng 0 SHALL trả HTTP 409 `PROFILE_VERSION_CONFLICT`.

### Existing Append-only `transaction`

Spec dùng bảng `transaction` hiện có:

```sql
SELECT id,
       type,
       amount,
       related_order_id,
       description,
       vnpay_txn_ref,
       created_at
FROM transaction
WHERE user_id = :customer_id
  AND type = ANY(:customer_visible_types)
ORDER BY created_at DESC, id DESC
LIMIT :size OFFSET :offset;
```

`customer_visible_types` server-side allowlist là `ORDER_PAYMENT|REFUND`. Rows không update/delete;
reversal phải thêm transaction mới theo AC-13.

Summary query:

```sql
SELECT
    COALESCE(ABS(SUM(amount) FILTER (
        WHERE type = 'ORDER_PAYMENT' AND amount < 0
    )), 0)::NUMERIC(15, 0) AS total_paid,
    COALESCE(SUM(amount) FILTER (
        WHERE type = 'REFUND' AND amount > 0
    ), 0)::NUMERIC(15, 0) AS total_refunded,
    MAX(created_at) AS last_transaction_at
FROM transaction
WHERE user_id = :customer_id
  AND type IN ('ORDER_PAYMENT', 'REFUND');
```

Indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_transaction_customer_payment_activity
    ON transaction (user_id, created_at DESC, id DESC)
    WHERE type IN ('ORDER_PAYMENT', 'REFUND');
```

### Profile Audit Events

Audit store dùng bảng audit chung của Spec #001/#002 với event types:

```text
PROFILE_UPDATED
PROFILE_AVATAR_UPDATED
PASSWORD_CHANGED
PASSWORD_CHANGE_FAILED
```

Metadata `PROFILE_UPDATED` chỉ chứa:

```json
{
  "changed_fields": ["full_name", "phone"],
  "profile_version": 4
}
```

---

## State Machines

### Profile Update

```text
VIEWING
  ↓ edit
EDITING
  ├─ validation error ─────────→ EDITING
  ├─ version conflict ─────────→ RELOAD_REQUIRED
  └─ valid PATCH ──────────────→ UPDATED
```

### Avatar Upload

```text
LOCAL_SELECTED
  ↓ validate MIME/size
SIGNED
  ↓ direct Cloudinary upload
UPLOADED_UNCONFIRMED
  ↓ backend signature/ownership confirm
ACTIVE_AVATAR
```

Unconfirmed upload cleanup thuộc Cloudinary operations; asset cũ chỉ xóa sau khi DB update thành
công để tránh mất avatar khi transaction fail.

### Password Change

```text
AUTHENTICATED
  ├─ wrong current/rate limited ─→ AUTHENTICATED
  └─ valid password change ──────→ ALL_REFRESH_TOKENS_REVOKED
                                      ↓
                                  LOGIN_REQUIRED
```

### Customer Payment Activity

```text
NO_CUSTOMER_WALLET
  ├─ valid VNPay IPN payment ─→ append ORDER_PAYMENT audit row
  └─ processed manual refund ─→ append REFUND audit row

Payment activity is read-only; no balance state and no top-up transition exist.
```

---

## Error Matrix

| HTTP | `error_code` | Khi nào |
|------|--------------|---------|
| 401 | `AUTHENTICATION_REQUIRED` | JWT thiếu/hết hạn |
| 403 | `FORBIDDEN` | Role không phải Customer |
| 403 | `PROFILE_OWNERSHIP_REQUIRED` | Trái quyền |
| 404 | `PROFILE_NOT_FOUND` | Customer soft-deleted/not found |
| 404 | `FEATURE_NOT_SUPPORTED` | Customer wallet/top-up |
| 409 | `PROFILE_VERSION_CONFLICT` | Optimistic lock fail |
| 422 | `VALIDATION_ERROR` | Profile/password/filter sai |
| 422 | `CURRENT_PASSWORD_INCORRECT` | Mật khẩu hiện tại sai |
| 422 | `PASSWORD_REUSE_NOT_ALLOWED` | Mật khẩu mới trùng cũ |
| 422 | `AVATAR_SIGNATURE_INVALID` | Asset confirm không hợp lệ |
| 429 | `RATE_LIMITED` | Quá 3 password attempts/giờ |
| 502 | `CLOUDINARY_UNAVAILABLE` | Upload provider lỗi |

---

## Detailed API Contracts

### Profile Response Contract

```json
{
  "id": "5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "full_name": "Nguyễn Văn An",
  "email": "an.nguyen@example.com",
  "phone": "+84901234567",
  "phone_display": "0901 234 567",
  "avatar_url": "https://res.cloudinary.com/move-home/image/upload/v4/customer-avatar/a.webp",
  "email_verified": true,
  "status": "ACTIVE",
  "status_label": "Đang hoạt động",
  "created_at": "2026-04-15T03:00:00Z",
  "total_orders": 12,
  "profile_version": 4
}
```

DTO SHALL không trả `password_hash`, `must_change_password`, `failed_login_count`, `locked_until`,
`deleted_at`, raw audit metadata hoặc refresh token. `phone_display` chỉ là convenience field;
database luôn lưu normalized `+84`.

### Profile Update Success

```json
{
  "profile": {
    "full_name": "Nguyễn Văn An",
    "phone": "+84901234567",
    "avatar_url": null,
    "profile_version": 4
  },
  "message": "Cập nhật thông tin thành công."
}
```

Backend SHALL trim strings trước validate và compare changed values trước update. Nếu payload
hợp lệ nhưng không field nào đổi, trả HTTP 200 với profile hiện tại và không tạo audit event giả.

### Avatar Signature Response

```json
{
  "cloud_name": "move-home",
  "api_key": "public-api-key",
  "timestamp": 1780540200,
  "expires_at": "2026-06-04T10:40:00Z",
  "folder": "move_home/customer-avatar/5af5e878-52b0-4fb8-a9cb-8af517594e89",
  "transformation": "c_fill,g_face,w_400,h_400,q_auto,f_auto",
  "signature": "signed-value"
}
```

Response SHALL không chứa Cloudinary API secret. Signature chỉ hợp lệ với đúng folder,
transformation và TTL. Confirm endpoint SHALL reject public id nằm ngoài folder Customer.

### Avatar Confirm Response

```json
{
  "avatar_url": "https://res.cloudinary.com/move-home/image/upload/v4/customer-avatar/a.webp",
  "avatar_public_id": "move_home/customer-avatar/5af5e878-52b0-4fb8-a9cb-8af517594e89/a",
  "profile_version": 5,
  "message": "Cập nhật ảnh đại diện thành công."
}
```

### Change Password Success

```json
{
  "message": "Mật khẩu đã được thay đổi. Vui lòng đăng nhập lại.",
  "force_relogin": true,
  "sessions_revoked": true
}
```

Response SHALL không trả access/refresh token mới. Cookie refresh token phải được clear bằng
`HttpOnly`, `Secure` production, `SameSite` theo Spec #001 và đúng path/domain hiện tại.

### Payment Activity Page Item

```json
{
  "id": "3e4cf39c-1932-44e6-8898-d03dad5c09b4",
  "type": "ORDER_PAYMENT",
  "type_label": "Thanh toán đơn",
  "amount": -1500000,
  "related_order_id": "18000ad0-ee49-4084-870a-e59cc170092a",
  "order_code": "MH2026060400001",
  "description": "Thanh toán cọc đơn MH2026060400001",
  "vnpay_txn_ref_masked": "VNP***8321",
  "created_at": "2026-06-04T10:30:00Z"
}
```

Transaction status không tồn tại trong V6 append-only schema; frontend SHALL không hiển thị
badge "Đang giao" như UI stub. Một row chỉ biểu thị money event đã được ghi nhận.

---

## Transaction & Concurrency Boundaries

### Profile Update Transaction

```text
BEGIN
  load Customer by JWT subject
  validate role/status/deleted_at
  validate allowlisted fields
  optimistic UPDATE WHERE profile_version=:expected
  INSERT audit_log PROFILE_UPDATED when changed_fields non-empty
COMMIT
```

Nếu optimistic update row count bằng 0, transaction SHALL rollback và trả 409. Client reload form
để tránh ghi đè thay đổi từ tab/session khác.

### Avatar Confirm Transaction

```text
verify Cloudinary response signature outside DB transaction
verify secure_url + public_id allowlist
BEGIN
  lock/load Customer profile version
  update avatar_url + avatar_public_id + profile_version
  insert audit_log PROFILE_AVATAR_UPDATED
  insert outbox event OLD_AVATAR_CLEANUP_REQUESTED when old public_id exists
COMMIT
```

Cloudinary cleanup consumer chỉ xóa old asset sau commit. Nếu cleanup lỗi, retry async; không
rollback avatar mới.

### Password Change Transaction

```text
BCrypt verify current password outside write transaction
validate new password policy + non-reuse
BEGIN
  SELECT app_user FOR UPDATE
  re-verify account active and password hash unchanged
  UPDATE password_hash + password_changed_at
  revoke all refresh_token rows for user
  INSERT audit_log PASSWORD_CHANGED
  INSERT outbox SECURITY_EMAIL_REQUESTED
COMMIT
clear refresh cookie and return force_relogin=true
```

Re-check hash bên trong lock ngăn hai password-change requests đồng thời dùng cùng current
password đều thành công. Request thua SHALL trả 409 `PASSWORD_CHANGED_CONCURRENTLY`.

### Payment Activity Read Boundary

Summary và history là read-only. Endpoint SHALL chạy transaction `READ_ONLY`, không tạo default
wallet, không update aggregate và không sửa transaction. Summary có thể cache tối đa 30 giây theo
key Customer id; event `ORDER_PAYMENT|REFUND` SHALL invalidate cache.

---

## Validation & Normalization Matrix

| Input | Normalize | Valid example | Invalid example | Error |
|-------|-----------|---------------|-----------------|-------|
| `full_name` | Unicode NFC + trim/collapse spaces | `Nguyễn Văn An` | `<script>` | 422 |
| `phone` | `0xxxxxxxxx` → `+84xxxxxxxxx` | `0901234567` | `012345` | 422 |
| `avatar_url` | No mutation | Cloudinary HTTPS allowlist | HTTP/arbitrary host | 422 |
| avatar MIME | Browser + provider verify | `image/webp` | `image/svg+xml` | 422 |
| avatar size | Integer bytes | `5242880` max | `5242881` | 422 |
| current password | No trim | Exact entered value | Blank | 422 |
| new password | No trim | `NewPassword2@` | `password` | 422 |
| page | Integer | `0` | `-1` | 422 |
| size | Allowlist | `10|20|50|100` | `1000` | 422 |
| payment type | Allowlist | `ALL|ORDER_PAYMENT|REFUND` | `DRIVER_EARNING` | 422 |

Phone uniqueness check SHALL compare normalized value. Unicode name validation SHALL run after NFC
normalization để cùng một tên không có nhiều representations khó audit.

---

## Migration & Rollout Plan

1. Tạo migration bổ sung nullable avatar fields và `profile_version DEFAULT 0`.
2. Backfill `profile_version=0` cho rows cũ trước khi enforce `NOT NULL`.
3. Tạo partial index payment activity bằng `CREATE INDEX` trong migration.
4. Deploy backend read endpoints trước; giữ UI stubs chưa gọi API.
5. Deploy profile edit/change-password, theo dõi audit và error rate.
6. Thay nội dung `my-wallet.html` thành payment activity, loại bỏ mock balance/top-up.
7. Chạy data integrity query và UI acceptance tests.
8. Chỉ bật payment activity khi V6 transaction data và ownership mapping đã được xác minh.

Rollout SHALL không sửa hoặc xóa lịch sử `transaction`. Rollback application không được rollback
money audit rows; schema additive có thể giữ lại an toàn.

Data integrity queries:

```sql
SELECT t.user_id, COUNT(*)
FROM transaction t
LEFT JOIN app_user u ON u.id = t.user_id
WHERE t.type IN ('ORDER_PAYMENT', 'REFUND')
  AND u.id IS NULL
GROUP BY t.user_id;
```

Query trên phải trả zero rows. Kiểm tra unexpected Customer-visible type:

```sql
SELECT DISTINCT type
FROM transaction
WHERE user_id = :customer_id
  AND type NOT IN ('ORDER_PAYMENT', 'REFUND');
```

Rows có thể tồn tại cho audit nội bộ nhưng Customer DTO SHALL không expose.

---

## Acceptance Criteria

**AC1**
Customer xem profile thấy đúng dữ liệu của mình, không có password hash hoặc wallet balance.

**AC2**
Customer chỉ sửa được full name, phone, avatar; gửi email/role/status trả 422 và không mutate.

**AC3**
Avatar JPG/PNG/WebP dưới 5 MB upload signed thành công, crop 400x400 và lưu HTTPS Cloudinary URL.

**AC4**
Avatar quá 5 MB, MIME sai hoặc signature giả bị từ chối; avatar cũ vẫn còn.

**AC5**
Đổi mật khẩu đúng sẽ BCrypt cost 12, revoke mọi refresh token, audit và force login lại.

**AC6**
Sai mật khẩu hiện tại, password reuse hoặc vượt rate limit không thay đổi hash/token.

**AC7**
Payment summary/history chỉ phản ánh `ORDER_PAYMENT|REFUND` của Customer hiện tại và paginate đúng.

**AC8**
Production không hiển thị balance/top-up; gọi Customer wallet/top-up endpoint không tạo dữ liệu.

**AC9**
Bốn màn hình có brand Move_home, tiếng Việt có dấu và đủ Loading/Empty/Error states.

---

## Edge Cases & Error Handling

| ID | Tình huống | Expected Behavior |
|----|------------|-------------------|
| EC-01 | Hai tab update profile cùng version | Một success, một 409 conflict |
| EC-02 | Họ tên chứa số/script | 422; không render stored XSS |
| EC-03 | Phone `0901 234 567` có spaces | Frontend có thể normalize display; backend input strict/422 |
| EC-04 | Phone đã thuộc user khác | 409 `PHONE_ALREADY_USED` |
| EC-05 | Avatar đúng extension nhưng MIME giả | 422 sau content validation/provider reject |
| EC-06 | Cloudinary upload xong nhưng confirm fail | DB giữ avatar cũ; cleanup orphan sau |
| EC-07 | Email alert timeout | Profile/password update vẫn commit |
| EC-08 | Đổi password làm request refresh đồng thời | Revoked token không được rotate lại |
| EC-09 | Customer không có transaction | Summary zero; history empty state |
| EC-10 | Transaction Driver-only gắn nhầm Customer | Không hiển thị do type allowlist |
| EC-11 | Refund amount lớn hơn total paid | Hiển thị audit thực tế; alert integrity, không tạo balance |
| EC-12 | UI stub còn button "Nạp tiền" | Production test fail; CTA phải bị loại bỏ |

---

## Test Cases

| ID | Test | Expected |
|----|------|----------|
| TC-01 | GET profile bằng Customer A | 200 dữ liệu A, email read-only |
| TC-02 | PATCH name/phone hợp lệ version 3 | 200, version 4, audit changed fields |
| TC-03 | PATCH chứa email + role | 422 immutable fields, DB không đổi |
| TC-04 | Upload WebP 4,9 MB | Signed upload + confirm success |
| TC-05 | Upload PNG 5,1 MB | 422 trước Cloudinary |
| TC-06 | Change password đúng | Hash mới BCrypt, tokens revoked, relogin |
| TC-07 | Change password sai 4 lần/giờ | Lần 4 trả 429 |
| TC-08 | Summary: payments -4.750.000, refund +200.000 | total_paid 4.750.000, net_paid 4.550.000 |
| TC-09 | History size 20 với 1.001 rows | 51 pages, stable pagination |
| TC-10 | POST Customer wallet top-up | 404 feature unsupported, không insert |

### Required Automated Test Layers

1. Unit tests cho validation, normalization, password policy và summary calculation.
2. Repository/Testcontainers tests cho optimistic update, payment query và partial index.
3. Integration tests cho RBAC, signed avatar confirm, token revocation và pagination.
4. Contract tests cho profile/payment DTO và ES-04 errors.
5. Frontend tests cho action visibility, Loading/Empty/Error và removal top-up UI.
6. Security tests bảo đảm logs/audit không chứa password, signed secret hoặc full PII.

---

## Frontend Screen Contract

| Screen | Canonical behavior |
|--------|--------------------|
| `my-profile.html` | Read-only account/profile summary, không có số dư |
| `my-profile-edit.html` | Name/phone/avatar edit; email disabled |
| `change-password.html` | Three password fields; success force relogin |
| `my-wallet.html` | Rename UI thành lịch sử thanh toán; không balance/top-up |

Mỗi screen SHALL dùng CSS variables từ `frontend/css/styles.css`, không inline màu khác brand,
format VND bằng `Intl.NumberFormat('vi-VN')`, format UTC sang `Asia/Ho_Chi_Minh`, có keyboard
focus/accessible labels và không trust localStorage làm source of truth.

---

## Privacy, Security & Observability

1. Profile DTO không trả `password_hash`, refresh tokens, lock counters hoặc audit IP.
2. Logs không chứa password, signed Cloudinary secret, phone đầy đủ hoặc VNPay raw callback.
3. Avatar URL chỉ HTTPS Cloudinary allowlist; frontend escape mọi user-provided value.
4. Password change metric chỉ label outcome/error code, không label user/email.
5. Alert khi password failure rate tăng, avatar confirm signature fail hoặc payment summary lệch.
6. Payment transaction append-only; correction là transaction mới, không update/delete.

Metrics tối thiểu:

| Metric | Type | Labels |
|--------|------|--------|
| `customer_profile_update_total` | Counter | `outcome` |
| `customer_avatar_upload_total` | Counter | `content_type`, `outcome` |
| `customer_password_change_total` | Counter | `outcome` |
| `customer_payment_activity_duration_seconds` | Histogram | `endpoint`, `outcome` |
| `customer_wallet_unsupported_call_total` | Counter | `endpoint` |

---

## Constitution Compliance

| Rule | Áp dụng |
|------|---------|
| HR-01 | Cloudinary credentials/signing secret chỉ ở env |
| HR-02 | BCrypt cost 12; không plaintext |
| HR-10 | Customer RBAC + ownership |
| HR-11 | Security email async không rollback |
| HR-13 | Profile/avatar/password thay đổi có audit |
| HR-16 | Change-password rate limit |
| HR-18 | Không tạo Customer wallet; Driver wallet invariant không bị ảnh hưởng |
| HR-19 | Forest green + amber + Be Vietnam Pro |
| HR-20 | Toàn bộ UI/error/email tiếng Việt có dấu |
| HR-21 | Dùng `app_user`; không tạo reserved-word table mới |
| AC-07 | UTC storage, Asia/Ho_Chi_Minh display |
| AC-08 | Money NUMERIC/BigDecimal scale=0 |
| AC-10 | Signed Cloudinary upload |
| AC-12 | Schema change qua Flyway |
| AC-13 | Financial transaction append-only audit |
| AC-14 | Status/string field VARCHAR + CHECK |
| AC-15 | Payment history server-side pagination |
| AC-16 | Empty/Loading/Error cho data-driven pages |
| ES-03 | Bean Validation + HTTP 422 |
| ES-04 | Error response thống nhất |
| ES-05 | Automated tests theo blast radius tài chính/bảo mật |

---

## Out of Scope (Deferred)

1. Customer wallet/top-up/pay-from-wallet: bị loại theo `CONTEXT.md v2.0`.
2. Payment IPN/reconciliation và RefundRecord processing: Payment/financial specs.
3. Driver wallet/deposit/withdrawal: Driver Financial spec.
4. Email change, phone OTP, avatar crop editor và Admin edit Customer.

---

## Open Questions

1. Chốt Cloudinary cloud name/folder retention và orphan cleanup interval.
2. Chốt phone uniqueness policy khi user cũ đã soft-delete.
3. Chốt route rename `my-wallet.html` thành `payment-activity.html`; hiện giữ path để không phá inventory.
4. Chốt retention pháp lý cho Customer-visible transaction history và masking VNPay reference.
