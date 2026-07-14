# Feature Specification: Authentication, Authorization (RBAC) & Guest Mode

**Feature Branch:** `001-auth-rbac`
**Feature Number:** #1 of 30 — CORE (foundation for all other features)
**Created:** 2026-05-29 (rewritten from v1.0 due to MAJOR PIVOT v2.0)
**Version:** 2.0.0
**Status:** Draft
**Sprint Target:** Thu Ba 2026-06-02 demo

**CONTEXT.md reference:** v2.0 §2 Auth flow, §3 RBAC table, §4 Constraints, §5 A13/A14/A15
**Constitution reference:** v1.2.0 — HR-12, HR-16, HR-17, HR-18, AC-03, AC-07, AC-08, AC-09, AC-11, AC-12, AC-13

---

## Goals

Xay dung nen tang xac thuc va phan quyen (RBAC) cho toan bo he thong Move_home v2.0 (marketplace
co dieu phoi). Spec nay dam bao 4 vai tro chinh (Customer, Driver, Manager, Admin) cung Guest mode
duoc dinh nghia ro rang, toan bo luong dang ky / dang nhap / xac thuc email / quan ly token duoc
spec chat che, va toan bo loi bao mat (brute force, token reuse, enumeration attack) duoc xu ly
dung chuan. Day la spec nen tang — moi spec feature khac deu tham chieu RBAC va token structure
cua spec nay.

---

## Scope Summary

**In scope:**
1. Customer dang ky qua `POST /api/auth/register/customer` (8 field bat buoc + 2 tuy chon)
2. Customer dang nhap bang username + password
3. Driver dang ky Step 1 qua `POST /api/auth/register/driver` (cac field co ban → PENDING_VERIFY)
4. Driver dang nhap bang email + password (chi sau khi ACTIVE)
5. Staff (Manager + Admin) dang nhap bang email + password
6. Email verification flow (token 24h, resend rate-limited) cho Customer + Driver
7. JWT: access token 15p (localStorage) + refresh token 7d (httpOnly cookie) + rotation
8. Logout server-side (revoke refresh token + clear cookie)
9. RBAC middleware: kiem tra role tu JWT, tra 403 khi trai quyen (HR-10)
10. Account lockout sau 5 lan sai password (15 phut), auto-unlock
11. Rate limit: login (5/IP/15p), register (3/IP/1h), resend (1/email/60s)
12. Audit log moi event auth quan trong (HR-13)
13. Force change password lan dau cho Staff (must_change_password=true, HR-12)
14. Token reuse detection (panic mode revoke all, AC-03)
15. Driver Step 2-4 overview (chi tiet trong Spec #002)

**Out of scope:**
1. Forgot Password / Reset Password — defer Spec rieng sau Feature #15
2. Driver Onboarding Step 2-4 chi tiet — Spec #002 (Driver Onboarding)
3. 2FA, social login (Google/Facebook), OAuth2
4. Profile management (sua email/phone/ten sau dang ky)
5. Admin tao Driver thu cong — Driver luon tu dang ky qua flow #002

---

## User Stories

**P1 (CORE — bat buoc cho Thu Ba demo):**

- **US1:** As a Customer, I can register a new account with username + password + email + phone so
  that I can place orders.
- **US2:** As a Customer, I can verify my email via a link sent to my inbox so that I can activate
  my account and log in.
- **US3:** As a Customer, I can log in with my username + password so that I can access my account.
- **US4:** As a Driver, I can register a new account (step 1 of 4) with my basic information so
  that I can begin the onboarding process toward becoming ACTIVE.
- **US5:** As a Staff member (Manager or Admin) created by Admin, I can log in with my email +
  temporary password and must change it before doing anything else, so that my account is secured
  from day one.
- **US6:** As an authenticated user, I can log out so that my refresh token is invalidated
  server-side and my cookie is cleared.
- **US7:** As an authenticated user, my access token auto-refreshes via the refresh token without
  me re-logging in for 7 days.

**P2 (Nice-to-have, khong block demo):**

- **US8:** As a Customer or Driver, if I do not receive the verification email, I can request a
  resend (rate-limited to once per 60 seconds per email address).
- **US9:** As any user, if I enter the wrong password 5 consecutive times, my account locks for 15
  minutes to prevent brute-force attacks.

---

## Functional Requirements

> **EARS notation:** WHEN (event-driven happy path) | WHILE (state-driven) |
> WHERE (unwanted / error path) | IF/THEN (optional branch).
> Moi FR phai thuoc mot trong 4 mau tren.

---

### Nhom 1 — Customer Register (FR-001..FR-010)

**FR-001**
WHEN Customer submits `POST /api/auth/register/customer` with a valid JSON payload satisfying all
rules FR-002..FR-005, THE system SHALL create a new user account and return HTTP 201 with body
`{ "user_id": "<uuid>", "message": "Tai khoan da duoc tao. Vui long xac thuc email cua ban." }`.

**FR-002**
WHEN processing a Customer registration request, THE system SHALL accept the following fields:

| Field | Type | Required | Constraint |
|-------|------|----------|------------|
| `username` | string | Yes | 3–30 ky tu; chi `[a-z0-9_]`; khong bat dau bang so hoac `_` |
| `email` | string | Yes | RFC 5322; max 255 ky tu; lowercase truoc khi luu |
| `phone` | string | Yes | Regex `^(0\|\\+84)[0-9]{9}$`; tu dong chuan hoa ve `+84xxxxxxxxx` |
| `password` | string | Yes | 8–72 ky tu; >= 1 chu hoa, >= 1 chu thuong, >= 1 so, >= 1 ky tu dac biet |
| `full_name` | string | Yes | 2–100 ky tu; khong rong sau trim |
| `date_of_birth` | string | Yes | ISO 8601 `YYYY-MM-DD`; tuoi >= 16 tinh den ngay nop don |
| `address` | string | Yes | 10–500 ky tu |
| `terms_accepted` | boolean | Yes | Phai la `true` |
| `district` | string | No | 1 trong 12 quan noi thanh Ha Noi; null neu khong cung cap |
| `notes` | string | No | Max 500 ky tu |

**FR-003**
WHERE any required field in FR-002 is missing, null, or blank after trimming, THE system SHALL
return HTTP 422 with body:
```json
{ "error_code": "VALIDATION_ERROR", "message": "Du lieu khong hop le.",
  "details": [{ "field": "<field_name>", "message": "<ly do cu the>" }] }
```
listing ALL failing fields simultaneously (no fail-fast — collect all errors in one pass).

**FR-004**
WHERE the submitted `username`, `email`, or normalized `phone` already exists in the `user` table
(`deleted_at IS NULL`), THE system SHALL return HTTP 409:
`{ "error_code": "CONFLICT", "field": "<username|email|phone>", "message": "<field> da duoc su dung." }`
for the FIRST duplicate found (check order: username → email → phone).

**FR-005**
WHERE `terms_accepted` is `false` or absent from the request body, THE system SHALL return
HTTP 422 with `details: [{ "field": "terms_accepted", "message": "Ban phai dong y dieu khoan." }]`.

**FR-006**
WHEN storing any user password, THE system SHALL hash it using `BCryptPasswordEncoder` with cost
factor 12 before persisting. Plaintext password MUST NOT appear in any DB column, log line,
response body, or audit record at any point.

**FR-007**
WHEN a Customer registration passes all validations (FR-003..FR-005), THE system SHALL persist a
new `user` row with:
- `role = CUSTOMER`, `status = PENDING_VERIFY`
- `failed_login_count = 0`, `locked_until = NULL`
- `must_change_password = false`
- `created_at = NOW() UTC` (AC-07), `deleted_at = NULL` (AC-09)

**FR-008**
WHEN a new Customer account is created (FR-007), THE system SHALL generate an email verification
token as a cryptographically random UUID v4 and insert a row into `email_verification_token`:
`{ token_id (PK UUID), user_id (FK), token (UNIQUE, index), expires_at = NOW() + 24h, created_at = NOW() }`.
Any previously unexpired tokens for the same `user_id` MUST be deleted before inserting the new one.

**FR-009**
WHEN a verification token is created (FR-008), THE system SHALL enqueue an async email via Spring
`@Async` dedicated thread pool (HR-11) containing a verification link
`GET /api/auth/verify?token=<token>`. Email delivery failure MUST NOT rollback the account
creation transaction.

**FR-010**
WHEN a Customer registration completes successfully, THE system SHALL insert an audit log row:
`{ event_type: "REGISTER", actor_id: <new_user_id>, actor_role: "CUSTOMER", ip_address: <request IP>, timestamp: NOW() UTC, metadata: { username, email } }`.

---

### Nhom 2 — Email Verification (FR-011..FR-016)

**FR-011**
WHEN a user calls `GET /api/auth/verify?token=<value>`, THE system SHALL look up the token in
`email_verification_token`. This endpoint is PUBLIC (HR-17) — no JWT required.

**FR-012**
WHERE the `token` query parameter does not match any row in `email_verification_token`,
THE system SHALL return HTTP 404:
`{ "error_code": "TOKEN_NOT_FOUND", "message": "Link xac thuc khong hop le hoac da het han." }`.

**FR-013**
WHERE the token exists but `expires_at < NOW()`, THE system SHALL delete the expired token row,
then return HTTP 410:
`{ "error_code": "TOKEN_EXPIRED", "message": "Link xac thuc da het han.", "can_resend": true }`.

**FR-014**
WHEN the token is found and `expires_at >= NOW()`, THE system SHALL, within a single DB transaction:
1. Set `user.status = ACTIVE`
2. Delete the token row from `email_verification_token`
3. Insert audit log `{ event_type: "EMAIL_VERIFIED", actor_id: user_id, timestamp: NOW() UTC }`

Then return HTTP 200: `{ "message": "Email da xac thuc. Ban co the dang nhap." }`.

**FR-015**
WHEN a user calls `POST /api/auth/resend-verification` with body `{ "email": "<email>" }`,
THE system SHALL enforce a rate limit of 1 request per email per 60 seconds.
WHERE the rate limit is exceeded → HTTP 429 `{ "error_code": "RATE_LIMITED", "retry_after_seconds": <remaining> }`.
WHEN the email belongs to a `PENDING_VERIFY` user and rate limit not exceeded: invalidate all
existing tokens for that user, generate a new token (FR-008), send verification email (FR-009),
return HTTP 200 with the generic message below.

**FR-016**
WHERE `POST /api/auth/resend-verification` is called with an email that does not exist in DB,
or belongs to a user with `status = ACTIVE`, THE system SHALL return HTTP 200 with the identical
generic response: `{ "message": "Neu email hop le va chua xac thuc, chung toi se gui lai link." }`.
KHONG duoc phan biet response giua "email khong ton tai" va "da xac thuc" — chong user enumeration.

---

### Nhom 3 — Login (FR-017..FR-023)

**FR-017**
WHEN a user submits `POST /api/auth/login` with body `{ "identifier": "<string>", "password": "<string>" }`, THE system SHALL route based on identifier format:
- Identifier DOES NOT contain `@` → lookup by `username` where `role = CUSTOMER`
- Identifier DOES contain `@` → lookup by `email` where `role IN (DRIVER, MANAGER, ADMIN)`

**FR-018**
WHILE processing any login request, THE system SHALL check the rate limit for the requesting IP
BEFORE any DB lookup. WHERE the IP has exceeded 5 login attempts in the last 15 minutes,
THE system SHALL return HTTP 429 `{ "error_code": "RATE_LIMITED", "retry_after_seconds": <remaining> }`
and abort — no DB query performed.

**FR-019**
WHERE the `identifier` resolves to no user in DB (regardless of format), THE system SHALL return
HTTP 401 `{ "error_code": "INVALID_CREDENTIALS", "message": "Ten dang nhap hoac mat khau khong dung." }`.
KHONG duoc tra "tai khoan khong ton tai" — chong enumeration.

**FR-020**
WHERE the user is found but `user.status = PENDING_VERIFY`, THE system SHALL return HTTP 403:
`{ "error_code": "EMAIL_NOT_VERIFIED", "message": "Vui long xac thuc email truoc khi dang nhap.", "can_resend_verification": true }`.

**FR-021**
WHERE the user is found, email verified, but `user.locked_until IS NOT NULL AND locked_until > NOW()`,
THE system SHALL return HTTP 423:
`{ "error_code": "ACCOUNT_LOCKED", "message": "Tai khoan bi khoa tam thoi.", "locked_until": "<ISO8601 UTC+7>", "minutes_remaining": <CEIL((locked_until - NOW()) / 60)> }`.
Password MUST NOT be verified — locked accounts short-circuit all credential checks.

**FR-022**
WHERE the user is found, not locked, but `BCrypt.verify(password, user.password_hash)` returns
false, THE system SHALL:
1. `UPDATE user SET failed_login_count = failed_login_count + 1, last_failed_login_at = NOW()`
2. IF `failed_login_count >= 5` THEN set `locked_until = NOW() + 15 minutes`,
   return HTTP 423 `{ "error_code": "ACCOUNT_LOCKED_NOW", "message": "Tai khoan bi khoa 15 phut." }`
3. ELSE return HTTP 401 `{ "error_code": "INVALID_CREDENTIALS", "message": "Ten dang nhap hoac mat khau khong dung.", "attempts_remaining": <5 - failed_login_count> }`.

**FR-023**
WHEN login succeeds (user found, not locked, password matches), THE system SHALL in a single
transaction:
1. Reset `failed_login_count = 0`, `locked_until = NULL`
2. Issue access token (FR-024) + refresh token (FR-025)
3. Set refresh cookie (FR-026)
4. Insert audit log `{ event_type: "LOGIN_SUCCESS", actor_id, actor_role, ip_address, timestamp }`
5. Return HTTP 200:
```json
{
  "access_token": "<JWT>",
  "token_type": "Bearer",
  "expires_in": 900,
  "user_info": {
    "user_id": "<uuid>",
    "role": "<CUSTOMER|DRIVER|MANAGER|ADMIN>",
    "full_name": "<string>",
    "must_change_password": <boolean>
  }
}
```

---

### Nhom 4 — JWT Token Management (FR-024..FR-030)

**FR-024**
WHEN issuing an access token, THE system SHALL create a signed JWT (HMAC-SHA256) with payload:
`{ "sub": "<user_id>", "role": "<ROLE>", "iat": <epoch>, "exp": <epoch + 900> }`.
Signing secret MUST come from env var `JWT_SECRET` (HR-01). Access token MUST NOT contain
plaintext password, phone, or email.

**FR-025**
WHEN issuing a refresh token, THE system SHALL:
1. Generate 256-bit cryptographically random bytes encoded as Base64URL
2. Compute `SHA-256(raw_token)` → store as `token_hash` in DB (NEVER store raw token in DB)
3. Insert into `refresh_token`:
   `{ token_id (PK UUID), user_id (FK), token_hash (UNIQUE), expires_at = NOW()+7d, revoked_at = NULL, created_at = NOW(), ip_created, user_agent_created }`

**FR-026**
WHEN setting the refresh token cookie, THE system SHALL use `Set-Cookie` with ALL attributes:
`HttpOnly=true; SameSite=Lax; Secure=<true prod / false local>; Path=/api/auth; Max-Age=604800`.
Refresh token MUST NOT appear in response body JSON under any circumstances.

**FR-027**
WHEN a client calls `POST /api/auth/refresh`, THE system SHALL read the refresh token
EXCLUSIVELY from the `refresh_token` cookie (not body, not Authorization header).
WHERE the cookie is absent or empty → HTTP 401 `{ "error_code": "NO_REFRESH_TOKEN" }`.

**FR-028**
WHEN a valid (non-revoked, non-expired) refresh token is received at `POST /api/auth/refresh`,
THE system SHALL perform token rotation in a single DB transaction:
1. `UPDATE refresh_token SET revoked_at = NOW() WHERE token_id = <old_id>`
2. Issue new access token (FR-024) + new refresh token (FR-025) + set new cookie (FR-026)
3. Return HTTP 200 with new access token (same format as FR-023, no user_info required).

**FR-029**
WHERE `POST /api/auth/refresh` receives a refresh token whose `revoked_at IS NOT NULL`,
THE system SHALL trigger **PANIC MODE**:
1. `UPDATE refresh_token SET revoked_at = NOW() WHERE user_id = <victim_user_id> AND revoked_at IS NULL`
2. Insert audit log `{ event_type: "SUSPICIOUS_TOKEN_REUSE", actor_id: <user_id>, ip_address, timestamp }`
3. Return HTTP 401 `{ "error_code": "TOKEN_REUSE_DETECTED", "message": "Phien lam viec het han do phat hien hoat dong bat thuong. Vui long dang nhap lai." }`.

**FR-030**
WHEN a client calls `POST /api/auth/logout` with a valid `Authorization: Bearer <access_token>`
header, THE system SHALL:
1. Hash the cookie's refresh token → lookup → `UPDATE SET revoked_at = NOW()`
2. Return `Set-Cookie: refresh_token=; Max-Age=0; Path=/api/auth` to clear cookie
3. Insert audit log `{ event_type: "LOGOUT", actor_id, timestamp }`
4. Return HTTP 200 `{ "message": "Da dang xuat thanh cong." }`

WHERE no refresh cookie is present (already logged out), THE system SHALL return HTTP 200
idempotently (no error — logout is safe to call multiple times).

---

### Nhom 5 — Account Lockout & Rate Limit (FR-031..FR-035)

**FR-031**
WHEN a failed login attempt causes `failed_login_count` to reach exactly 5, THE system SHALL
set `user.locked_until = NOW() + INTERVAL '15 minutes'` in the same UPDATE transaction that
increments the count (atomic — no race condition). No email notification is sent; lockout is
silent and auto-resolves.

**FR-032**
WHERE a login attempt is made for a user whose `locked_until > NOW()`, THE system SHALL return
HTTP 423 with `minutes_remaining = CEIL((locked_until - NOW()) / 60)` WITHOUT verifying the
submitted password — lockout short-circuits all credential checks (see FR-021).

**FR-033**
WHEN a login succeeds (correct password, account not locked), THE system SHALL reset
`user.failed_login_count = 0` AND `user.locked_until = NULL` in the same transaction as
token issuance (FR-023).

**FR-034**
WHILE the system is running, a Scheduled Job running every 5 minutes SHALL execute:
```sql
UPDATE "user"
SET failed_login_count = 0
WHERE failed_login_count > 0
  AND last_failed_login_at < NOW() - INTERVAL '30 minutes'
  AND locked_until IS NULL;
```
This prevents failed_login_count from accumulating for users who attempt a few times then stop.

**FR-035**
THE system SHALL enforce the following rate limits per the table below. WHERE any limit is
exceeded, THE system SHALL return HTTP 429:
`{ "error_code": "RATE_LIMITED", "retry_after_seconds": <int> }`.

| Endpoint | Limit | Window |
|----------|-------|--------|
| `POST /api/auth/login` | 5 attempts | per IP per 15 minutes |
| `POST /api/auth/register/*` | 3 attempts | per IP per 1 hour |
| `POST /api/auth/resend-verification` | 1 attempt | per email per 60 seconds |
| All other `POST /api/**` | 60 requests | per IP per 1 minute |

---

### Nhom 6 — Staff Force Change Password (FR-036..FR-040)

**FR-036**
WHEN Admin calls `POST /api/admin/staff` to create a Manager or Admin account (detail: Spec #015),
THE system SHALL:
1. Generate a random 12-character temporary password (mix: uppercase + lowercase + digit + special)
2. Hash with BCrypt cost 12 (FR-006), persist as `user.password_hash`
3. Set `user.must_change_password = true`, `user.status = ACTIVE`
4. Send async email (HR-11) to the new Staff email with the plaintext temporary password and
   login URL — this is the ONLY time plaintext password is transmitted (via encrypted email).

**FR-037**
WHEN a Staff user (role = MANAGER or ADMIN) logs in successfully AND `user.must_change_password = true`,
THE system SHALL issue a **restricted access token** with:
- `exp = NOW() + 5 minutes` (shorter than standard 15 minutes)
- Extra JWT claim: `"scope": "CHANGE_PASSWORD_ONLY"`

No refresh token is issued for this login. The login response includes `must_change_password: true`
in `user_info` so the frontend can redirect to the change-password screen.

**FR-038**
WHEN Staff calls `POST /api/auth/change-password-first-time` with header
`Authorization: Bearer <restricted_token>` and body `{ "new_password": "<string>" }`,
THE system SHALL:
1. Verify the token has claim `scope = CHANGE_PASSWORD_ONLY`; WHERE missing → HTTP 403
   `{ "error_code": "SCOPE_INSUFFICIENT" }`
2. Validate `new_password` meets the password policy (FR-002 password field rules)
3. Check `new_password` does NOT match the current temporary hash (FR-040)
4. Hash `new_password`, update `user.password_hash`, set `user.must_change_password = false`
5. Issue full access token (15 min, no scope restriction) + refresh token + cookie
6. Insert audit log `{ event_type: "PASSWORD_CHANGED_FIRST_TIME", actor_id, timestamp }`
7. Return HTTP 200 with full token response (FR-023 format)

**FR-039**
WHERE a Staff user holding a restricted token (scope=CHANGE_PASSWORD_ONLY) calls ANY endpoint
other than `POST /api/auth/change-password-first-time`, THE system SHALL return HTTP 403:
`{ "error_code": "PASSWORD_CHANGE_REQUIRED", "message": "Vui long doi mat khau truoc khi su dung he thong." }`.
This check MUST be enforced in the RBAC filter BEFORE the endpoint handler runs.

**FR-040**
WHERE `POST /api/auth/change-password-first-time` is called and `BCrypt.verify(new_password,
current_password_hash)` returns true (new password identical to temporary password),
THE system SHALL return HTTP 422:
`{ "error_code": "PASSWORD_SAME_AS_TEMP", "details": [{ "field": "new_password", "message": "Mat khau moi phai khac mat khau tam thoi." }] }`.
Password MUST NOT be updated in this case.

---

---

## User Stories (bo sung Luot B)

**P1 (CORE — bo sung cho Driver + Staff + Guest flows):**

- **US10:** As a Driver, I can register a new account (step 1) with my email + basic info so that
  I can begin the 4-step onboarding process.
- **US11:** As a Driver (PENDING_DOCUMENTS), I can upload my driving license, vehicle registration,
  and vehicle photos via Cloudinary so that my documents are reviewed for approval.
- **US12:** As a Driver (PENDING_DEPOSIT), I can pay the 3,000,000 VND collateral deposit via
  VNPay so that my onboarding application is submitted to Manager for review.
- **US13:** As a Manager, I can view a list of drivers pending approval and approve or reject their
  applications with a reason so that only vetted drivers become ACTIVE.
- **US14:** As an Admin, I can create a new Manager or Admin account so that the new staff member
  receives login credentials via email and must change their password on first login.
- **US15:** As a Guest (not logged in), I can access 6 public pages — landing, pricing, quote
  estimator, become-a-driver info, FAQ, and terms — without needing to register.

---

### Nhom 7 — Driver Register Step 1 (FR-041..FR-046)

**FR-041**
WHEN a Driver candidate submits `POST /api/auth/register/driver` with a valid JSON payload
satisfying all rules FR-042..FR-043, THE system SHALL create a new Driver account and return
HTTP 201: `{ "user_id": "<uuid>", "message": "Tai khoan da tao. Vui long kiem tra email de tiep tuc onboarding." }`.

**FR-042**
WHEN processing a Driver registration, THE system SHALL accept and validate the following fields:

| Field | Type | Required | Constraint |
|-------|------|----------|------------|
| `email` | string | Yes | RFC 5322; max 255 ky tu; lowercase truoc khi luu |
| `phone` | string | Yes | Regex `^(0\|\\+84)[0-9]{9}$`; chuan hoa ve `+84xxxxxxxxx` |
| `password` | string | Yes | 8–72 ky tu; >= 1 chu hoa, >= 1 chu thuong, >= 1 so, >= 1 ky tu dac biet |
| `confirm_password` | string | Yes | Phai khop chinh xac voi `password` |
| `full_name` | string | Yes | 2–100 ky tu; khong rong sau trim |
| `date_of_birth` | string | Yes | ISO 8601 `YYYY-MM-DD`; tuoi >= **18** tinh den ngay nop |
| `home_address` | string | Yes | 10–500 ky tu |
| `operating_districts` | array | Yes | Mang 1–12 phan tu; moi phan tu phai la 1 trong 12 quan noi thanh Ha Noi hop le |
| `terms_accepted` | boolean | Yes | Phai la `true` |

WHERE any field fails validation, THE system SHALL return HTTP 422 (format FR-003), listing ALL
failures simultaneously.

WHERE `confirm_password` does not exactly match `password`, THE system SHALL return HTTP 422:
`{ "details": [{ "field": "confirm_password", "message": "Mat khau xac nhan khong khop." }] }`.

WHERE `operating_districts` contains any value not in the valid 12-district list
`[Ba Dinh, Hoan Kiem, Tay Ho, Long Bien, Cau Giay, Dong Da, Hai Ba Trung, Hoang Mai, Thanh Xuan, Nam Tu Liem, Bac Tu Liem, Ha Dong]`,
THE system SHALL return HTTP 422 with details listing each invalid district value.

**FR-043**
WHERE the submitted `email` or normalized `phone` already exists in the `user` table
(`deleted_at IS NULL`), THE system SHALL return HTTP 409:
`{ "error_code": "CONFLICT", "field": "<email|phone>", "message": "<field> da duoc su dung." }`
for the FIRST duplicate found (check order: email → phone).
Note: Driver dung email lam identifier, khong co username field (khac Customer).

**FR-044**
WHEN a Driver registration passes all validations, THE system SHALL hash the password (FR-006,
BCrypt cost 12) and persist a new `user` row with:
- `role = DRIVER`, `status = PENDING_VERIFY`
- `failed_login_count = 0`, `locked_until = NULL`, `must_change_password = false`
- `created_at = NOW() UTC`, `deleted_at = NULL`
- `operating_districts` luu duoi dang array column hoac JSON column

**FR-045**
WHEN a new Driver account is created (FR-044), THE system SHALL generate an email verification
token (FR-008 logic — UUID v4, expires 24h) and send it via `@Async` email (FR-009 logic).
Driver CANNOT proceed to Step 2 until email is verified — attempting login before verify returns
403 (FR-048).

**FR-046**
WHEN a Driver registration completes, THE system SHALL insert an audit log row:
`{ event_type: "REGISTER_DRIVER", actor_id: <new_user_id>, actor_role: "DRIVER", ip_address, timestamp: NOW() UTC, metadata: { email, operating_districts } }`.

---

### Nhom 8 — Driver Email Verify & Status Transition (FR-047..FR-049)

**FR-047**
WHEN a Driver clicks the email verification link (`GET /api/auth/verify?token=<value>`) and the
token is valid and non-expired, THE system SHALL, within a single DB transaction:
1. Set `user.status = PENDING_DOCUMENTS` — KHONG la ACTIVE (khac Customer)
2. Delete the token row
3. Insert audit log `{ event_type: "EMAIL_VERIFIED", actor_id, timestamp }`

Return HTTP 200: `{ "message": "Email da xac thuc.", "next_step": "upload_documents", "redirect": "/driver/onboarding/step2" }`.

**FR-048**
WHERE a Driver attempts `POST /api/auth/login` and `user.status = PENDING_VERIFY`,
THE system SHALL return HTTP 403:
`{ "error_code": "EMAIL_NOT_VERIFIED", "message": "Vui long xac thuc email truoc khi tiep tuc.", "can_resend_verification": true }`.

**FR-049**
WHERE a Driver attempts `POST /api/auth/login` and `user.status IN (PENDING_DOCUMENTS,
PENDING_DEPOSIT, PENDING_APPROVAL)`, THE system SHALL return HTTP 403:

| status | error_code | message | next_step |
|--------|-----------|---------|-----------|
| `PENDING_DOCUMENTS` | `ONBOARDING_INCOMPLETE` | "Vui long upload giay to de tiep tuc." | `upload_documents` |
| `PENDING_DEPOSIT` | `ONBOARDING_INCOMPLETE` | "Vui long dong coc 3 trieu de hoan tat." | `pay_deposit` |
| `PENDING_APPROVAL` | `ONBOARDING_PENDING_REVIEW` | "Ho so dang cho Manager duyet. Vui long doi." | `awaiting_approval` |

The response MUST include `{ "error_code": "...", "message": "...", "current_step": "<next_step_value>" }`.

---

### Nhom 9 — Driver Upload Documents (FR-050..FR-058)

**FR-050**
WHEN a Driver (authenticated, `status = PENDING_DOCUMENTS`) calls
`POST /api/driver/me/documents`, THE system SHALL accept a `multipart/form-data` request
containing one document submission at a time (goi nhieu lan cho den khi du). WHERE the Driver's
`status != PENDING_DOCUMENTS` when this endpoint is called → HTTP 403
`{ "error_code": "INVALID_ONBOARDING_STEP", "message": "Upload chi duoc o buoc PENDING_DOCUMENTS." }`.

**FR-051**
WHEN processing a document upload, THE system SHALL accept the following document types.
All image files MUST be validated (FR-052) before Cloudinary upload.

| document_type | Required files | Metadata fields |
|---------------|---------------|-----------------|
| `GPLX` | 2 anh: `front` + `back` | Khong co metadata text |
| `VEHICLE_REGISTRATION` | 1 anh: `registration_card` | `plate_number` (string), `vehicle_type` (enum), `max_load_kg` (int) |
| `VEHICLE_PHOTOS` | 3 anh: `photo_front` + `photo_rear` + `photo_side` | Khong co metadata text |

> [to do] CONTEXT.md v2.0 liet ke 3 loai giay to (GPLX, Dang ky xe, Anh xe) — can xac nhan voi
> team co bat buoc them CCCD khong truoc khi code.

**FR-052**
WHERE any uploaded image file fails ANY of the following checks, THE system SHALL return
HTTP 422 BEFORE calling Cloudinary API (tranh ton quota):
- MIME type check via magic bytes (doc 12 bytes dau): phai la `image/jpeg`, `image/png`, hoac `image/webp`
- File size: phai `<= 1.5 MB` sau khi FE compress (backend enforce cung)
- File must not be empty (size > 0)

Response: `{ "error_code": "INVALID_FILE", "field": "<field_name>", "message": "<ly do>" }`.

**FR-053**
WHEN an image passes validation (FR-052), THE system SHALL upload it using Cloudinary **signed
upload** server-side (Java SDK) — KHONG cho phep unsigned upload tu client (AC-10). After upload,
THE system SHALL insert a row into `driver_document`:
`{ id, driver_id, document_type, image_role (front/back/...), cloudinary_public_id, cloudinary_secure_url, uploaded_at }`.
Cloudinary folder: `movehome/drivers/{driver_id}/{document_type}/{image_role}`.

**FR-054**
WHEN `document_type = VEHICLE_REGISTRATION` is submitted, THE system SHALL validate metadata:
- `plate_number`: regex `^[0-9]{2}[A-Z]{1,2}[-\s]?[0-9]{4,5}$` (bien so Viet Nam tieu chuan)
- `vehicle_type`: phai la 1 trong 4 gia tri: `XE_3_GAC`, `XE_TAI_VUA`, `XE_TAI_LON`, `XE_TO`
- `max_load_kg`: so nguyen duong phu hop voi loai xe (Xe 3 gac <= 500, Vua <= 1000, Lon <= 2000, To <= 5000)

WHERE any metadata field fails validation → HTTP 422 listing failing fields (format FR-003).

**FR-055**
WHERE a Driver in PENDING_DOCUMENTS attempts to submit `VEHICLE_REGISTRATION` a second time (a
row with `document_type = VEHICLE_REGISTRATION` for this `driver_id` already exists),
THE system SHALL return HTTP 409:
`{ "error_code": "VEHICLE_ALREADY_REGISTERED", "message": "Onboarding chi cho phep dang ky 1 xe. Lien he Manager neu can thay doi." }`.

**FR-056**
WHEN a Driver has successfully uploaded ALL required documents — specifically all 3 `document_type`
values (GPLX x2 photos, VEHICLE_REGISTRATION x1 photo + metadata, VEHICLE_PHOTOS x3 photos) —
THE system SHALL automatically:
1. Set `user.status = PENDING_DEPOSIT`
2. Insert audit log `{ event_type: "DOCUMENTS_COMPLETE", actor_id: driver_id, timestamp }`
3. Return HTTP 200: `{ "message": "Giay to da du. Vui long dong coc.", "next_step": "pay_deposit" }`.

**FR-057**
WHEN an authenticated Driver calls `GET /api/driver/me/documents`, THE system SHALL return a list
of all uploaded documents for that Driver, including:
- `document_type`, `image_role`, `uploaded_at`
- `signed_url`: Cloudinary signed URL with `expires_at = NOW() + 1 hour` (generated fresh each
  request per AC-10 — KHONG cache URL phia client qua 1 gio)

**FR-058**
WHERE a Driver calls any `/api/driver/{id}/documents` endpoint with an `id` that is NOT their own
`user_id`, THE system SHALL return HTTP 403:
`{ "error_code": "FORBIDDEN", "message": "Khong co quyen xem giay to cua tai xe khac." }`.
This check MUST be enforced in the service layer, not only at the URL level.

---

### Nhom 10 — Driver Deposit 3M qua VNPay (FR-059..FR-063)

**FR-059**
WHEN an authenticated Driver (status = `PENDING_DEPOSIT`) calls `POST /api/driver/me/deposit`,
THE system SHALL generate a VNPay payment URL for `3,000,000 VND` with a unique
`vnp_TxnRef = "DDP-{driver_id_compact_uuid}-{yyyyMMddHHmmss}-{random_hex_8}"` (actual implementation
in `VnPayPaymentService.newTxnRef()`; prefix `DDP` is also what `vnpay-return.html` checks via
`txnRef.startsWith('DDP')` to detect driver-deposit returns) and return HTTP 200:
`{ "payment_url": "<VNPay URL>", "amount": 3000000, "expires_in_minutes": 15 }`.
WHERE Driver status != PENDING_DEPOSIT when this endpoint is called → HTTP 403
`{ "error_code": "INVALID_ONBOARDING_STEP", "message": "Dong coc chi duoc o buoc PENDING_DEPOSIT." }`.

**FR-060**
WHEN a deposit payment URL is generated (FR-059), THE system SHALL insert a row into
`driver_deposit_payment`:
`{ id (PK), driver_id (FK, UNIQUE constraint — 1 driver 1 deposit record), vnp_txn_ref (UNIQUE), amount = 3000000, status = PENDING, created_at = NOW() }`.

**FR-061**
WHEN VNPay sends IPN callback to `POST /api/vnpay/ipn/driver-deposit`,
THE system SHALL:
1. Verify HMAC-SHA512 secure hash (HR-04) — hash fail → return `RspCode=97`, abort
2. Idempotency check (HR-15): lookup `vnp_TxnRef` in `driver_deposit_payment` — if already PAID → return `RspCode=02`
3. If PENDING and hash valid:
   - `UPDATE driver_deposit_payment SET status = PAID, paid_at = NOW()`
   - `UPDATE user SET status = PENDING_APPROVAL WHERE id = driver_id`
   - `UPDATE wallet SET deposit_balance = deposit_balance + 3000000` (HR-18)
   - `INSERT wallet_transaction (type=DEPOSIT_PAID, amount=+3000000, balance_after=..., ref_withdrawal_id=null)` (AC-13)
   - Insert audit log `{ event_type: "DEPOSIT_PAID", actor_id: driver_id, amount: 3000000, timestamp }`
4. Return `RspCode=00` to VNPay.
All steps 3a-3d MUST execute in a single DB transaction (HR-18).

**FR-062**
WHERE `POST /api/vnpay/ipn/driver-deposit` receives a `vnp_TxnRef` that already has
`status = PAID` in `driver_deposit_payment`, THE system SHALL return `RspCode=02`
("Order already confirmed") and perform NO DB updates — idempotency per HR-15.

**FR-063**
WHILE a `driver_deposit_payment` row has `status = PENDING` and `created_at < NOW() - 15 minutes`,
a Scheduled Job (running every 5 minutes) SHALL:
1. Mark the payment record `status = EXPIRED`
2. Send async email (HR-11) to the Driver: "Dong coc khong thanh cong. Vui long thu lai."
3. Do NOT cancel the onboarding — Driver can call `POST /api/driver/me/deposit` again to get a
   new payment URL (a new `driver_deposit_payment` row is created, old expired row kept for audit).

---

### Nhom 11 — Manager Approve / Reject Driver (FR-064..FR-068)

**FR-064**
WHEN Manager (or Admin) calls `GET /api/admin/drivers?status=PENDING_APPROVAL`,
THE system SHALL return a paginated list of Driver accounts with `status = PENDING_APPROVAL`,
each record including:
`{ driver_id, full_name, email, phone, operating_districts, submitted_at (= paid_at from driver_deposit_payment), document_count }`.
Result MUST be ordered by `submitted_at ASC` (oldest first — FIFO review queue).

**FR-065**
WHEN Manager calls `GET /api/admin/drivers/{id}`, THE system SHALL return the full Driver profile:
all `user` fields (except `password_hash`) + `operating_districts` + list of documents with fresh
Cloudinary signed URLs (expire 1h, per AC-10) + `rejection_history` (previous REJECTED records
if any). WHERE `id` does not exist or `deleted_at IS NOT NULL` → HTTP 404.

**FR-066**
WHEN Manager calls `POST /api/admin/drivers/{id}/approve` (role must be MANAGER or ADMIN),
THE system SHALL, within a single transaction:
1. Set `user.status = ACTIVE` for Driver `{id}`
2. Insert audit log `{ event_type: "DRIVER_APPROVED", actor_id: manager_id, target_driver_id: id, timestamp }`
3. Send async email (HR-11) to Driver: "Ho so da duoc duyet. Ban co the bat dau nhan don."
4. Return HTTP 200: `{ "message": "Tai xe da duoc duyet va kich hoat." }`.
WHERE Driver `status != PENDING_APPROVAL` when this endpoint is called → HTTP 409
`{ "error_code": "INVALID_STATUS_TRANSITION", "message": "Chi co the duyet Driver o trang thai PENDING_APPROVAL." }`.

**FR-067**
WHEN Manager calls `POST /api/admin/drivers/{id}/reject` with body `{ "reason": "<string min 10 chars>" }`,
THE system SHALL, within a single transaction:
1. Set `user.status = REJECTED`, store `rejection_reason` (linked to driver record)
2. Insert audit log `{ event_type: "DRIVER_REJECTED", actor_id: manager_id, target_driver_id: id, reason, timestamp }`
3. Send async email (HR-11) to Driver with rejection reason and instructions to re-upload
4. Return HTTP 200: `{ "message": "Tai xe bi tu choi.", "driver_id": "<id>" }`.
WHERE `reason` is missing or fewer than 10 characters → HTTP 422
`{ "details": [{ "field": "reason", "message": "Ly do phai co it nhat 10 ky tu." }] }`.

**FR-068**
WHERE a REJECTED Driver attempts `POST /api/auth/login` (status = REJECTED),
THE system SHALL return HTTP 403:
`{ "error_code": "ACCOUNT_REJECTED", "message": "Tai khoan bi tu choi.", "rejection_reason": "<reason>", "can_resubmit": true }`.
WHEN a REJECTED Driver calls `POST /api/driver/me/resubmit`, THE system SHALL:
1. Set `user.status = PENDING_DOCUMENTS` (skip re-deposit — coc cu van con)
2. Delete all existing `driver_document` rows for this driver (so Driver uploads fresh documents)
3. Insert audit log `{ event_type: "DRIVER_RESUBMITTED", actor_id: driver_id, timestamp }`
4. Return HTTP 200: `{ "next_step": "upload_documents" }`.

---

### Nhom 12 — Admin Tao Staff (FR-069..FR-071)

**FR-069**
WHEN Admin calls `POST /api/admin/staff` with body `{ "email": "<string>", "full_name": "<string>", "role": "<MANAGER|ADMIN>" }`,
THE system SHALL:
1. Validate `email` (RFC 5322, not already in `user` table)
2. Validate `role` is exactly `MANAGER` or `ADMIN`
3. Generate a random 16-character temporary password (at least: 2 uppercase, 2 lowercase, 2 digits, 2 special chars)
4. Hash with BCrypt cost 12 (FR-006)
5. Persist `user` row: `role = <role>, status = ACTIVE, must_change_password = true`
6. Insert audit log `{ event_type: "STAFF_CREATED", actor_id: admin_id, target_email: email, role, timestamp }`
7. Return HTTP 201: `{ "user_id": "<uuid>", "message": "Tai khoan da tao, email da gui." }`.

**FR-070**
WHEN a new Staff account is created (FR-069), THE system SHALL send async email (HR-11) to the
new Staff email address containing:
- The plaintext temporary password (ONLY transmission point — never logged or stored plaintext elsewhere)
- Login URL of the system
- Instructions: "Vui long dang nhap va doi mat khau ngay lan dau."

**FR-071**
WHERE Admin calls `POST /api/admin/staff` with `role` value of `CUSTOMER` or `DRIVER`,
THE system SHALL return HTTP 422:
`{ "error_code": "INVALID_ROLE", "message": "Endpoint nay chi tao Manager hoac Admin. Driver va Customer tu dang ky qua form cong khai." }`.

---

### Nhom 13 — Guest Public Endpoints (FR-072..FR-075)

**FR-072**
THE system SHALL expose the following 6 public API endpoints under prefix `/api/public/*`
(HR-17 — no JWT required):

| Endpoint | Method | Maps to FE page | Response |
|----------|--------|-----------------|---------|
| `GET /api/public/landing-info` | GET | `/` Landing page | `{ banner, service_summary, cta_text }` |
| `GET /api/public/pricing-config` | GET | `/pricing` Bang gia | `{ vehicle_types: [{name, price_per_km, porter_fee, max_load_kg}], surcharges: [{type, rate}] }` |
| `POST /api/public/quote-estimate` | POST | `/quote` Uoc tinh gia | Body: `{ origin_district, dest_district, vehicle_type, num_porters, floor_origin, floor_dest, has_elevator, is_alley, start_time }` → Response: `{ total_quote, breakdown }` — KHONG luu DB |
| `GET /api/public/become-driver-info` | GET | `/become-driver` | `{ requirements, commission_rate, steps_summary }` |
| `GET /api/public/faq` | GET | `/faq` | `{ faqs: [{question, answer}] }` |
| `GET /api/public/terms-and-privacy` | GET | `/terms`, `/privacy` | `{ terms_html, privacy_html }` |

**FR-073**
WHILE configuring Spring Security filter chain, THE system SHALL apply:
```
.requestMatchers("/api/public/**").permitAll()
.requestMatchers("/api/auth/**").permitAll()
.anyRequest().authenticated()
```
ensuring that `/api/public/**` routes bypass ALL JWT validation. No other prefix shares this
bypass — default deny for all other paths (HR-17).

**FR-074**
WHERE any `/api/public/*` endpoint is implemented, it MUST NOT include in its response:
- PII of any user (name, email, phone, address of specific Driver/Customer)
- Internal system configuration (DB connection, secret keys, commission rates tied to specific orders)
- Any data scoped to a specific user's account or order history

Violation of this rule at code review → treat as Layer 1 violation (HR-17 + HR-10).

**FR-075**
WHERE a Guest (unauthenticated user) calls any endpoint NOT under `/api/public/**` or
`/api/auth/**`, THE system SHALL return HTTP 401 (NOT 403):
`{ "error_code": "AUTHENTICATION_REQUIRED", "message": "Vui long dang nhap de tiep tuc." }`.
Reasoning: 401 = "chua xac thuc" (Guest khong biet endpoint can auth); 403 = "da xac thuc nhung
khong co quyen" (dung cho RBAC violations sau khi login). Quan trong de FE redirect dung man hinh.

---

---

## Data Model

> Cac bang thuoc scope Spec #001. Bang lien quan cua feature khac (Order, Trip, Wallet...)
> dinh nghia trong spec tuong ung — chi tham chieu FK o day.

### Bang `user`

```sql
CREATE TABLE "user" (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role                 VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','DRIVER','MANAGER','ADMIN')),
    status               VARCHAR(30)  NOT NULL
                         CHECK (status IN ('PENDING_VERIFY','PENDING_DOCUMENTS','PENDING_DEPOSIT',
                                           'PENDING_APPROVAL','ACTIVE','REJECTED','SUSPENDED')),

    -- Customer-only fields (NULL for Driver/Staff)
    username             VARCHAR(30)  UNIQUE,

    -- Shared identity fields
    email                VARCHAR(255) NOT NULL UNIQUE,
    phone                VARCHAR(15),                        -- chuan hoa +84xxxxxxxxx
    password_hash        VARCHAR(60)  NOT NULL,              -- BCrypt $2a$12$...
    full_name            VARCHAR(100) NOT NULL,
    date_of_birth        DATE,
    address              VARCHAR(500),

    -- Driver-only fields (NULL for Customer/Staff)
    operating_districts  TEXT[],                             -- PostgreSQL array
    rejection_reason     TEXT,

    -- Auth control
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_count   INT          NOT NULL DEFAULT 0,
    last_failed_login_at TIMESTAMPTZ,
    locked_until         TIMESTAMPTZ,

    -- Terms
    terms_accepted       BOOLEAN      NOT NULL DEFAULT FALSE,
    terms_accepted_at    TIMESTAMPTZ,

    -- Soft delete (AC-09)
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);

CREATE INDEX idx_user_email    ON "user" (email)    WHERE deleted_at IS NULL;
CREATE INDEX idx_user_username ON "user" (username) WHERE deleted_at IS NULL AND username IS NOT NULL;
CREATE INDEX idx_user_status   ON "user" (status)   WHERE deleted_at IS NULL;
```

### Bang `email_verification_token`

```sql
CREATE TABLE email_verification_token (
    token_id   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    token      UUID        NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_evtoken_user ON email_verification_token (user_id);
```

### Bang `refresh_token`

```sql
CREATE TABLE refresh_token (
    token_id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    token_hash         CHAR(64)    NOT NULL UNIQUE,  -- SHA-256 hex cua raw token
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ,                  -- NULL = con hieu luc
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_created         VARCHAR(45),
    user_agent_created TEXT
);

CREATE INDEX idx_rt_user_id    ON refresh_token (user_id);
CREATE INDEX idx_rt_token_hash ON refresh_token (token_hash);
```

### Bang `driver_document`

```sql
CREATE TABLE driver_document (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id            UUID        NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    document_type        VARCHAR(30) NOT NULL
                         CHECK (document_type IN ('GPLX','VEHICLE_REGISTRATION','VEHICLE_PHOTOS')),
    image_role           VARCHAR(20) NOT NULL,  -- front, back, registration_card, photo_front, photo_rear, photo_side
    cloudinary_public_id TEXT        NOT NULL,
    cloudinary_secure_url TEXT       NOT NULL,
    uploaded_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Vehicle metadata (chi dung cho VEHICLE_REGISTRATION)
    plate_number         VARCHAR(15),
    vehicle_type         VARCHAR(20) CHECK (vehicle_type IN ('XE_3_GAC','XE_TAI_VUA','XE_TAI_LON','XE_TO')),
    max_load_kg          INT
);

CREATE INDEX idx_driver_doc_driver ON driver_document (driver_id);
```

### Bang `driver_deposit_payment`

```sql
CREATE TABLE driver_deposit_payment (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id    UUID        NOT NULL UNIQUE REFERENCES "user"(id),  -- 1 driver 1 record
    vnp_txn_ref  VARCHAR(100) NOT NULL UNIQUE,
    amount       NUMERIC(15,0) NOT NULL DEFAULT 3000000,
    status       VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING','PAID','EXPIRED'))
                 DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    paid_at      TIMESTAMPTZ
);
```

### Bang `auth_audit_log`

```sql
CREATE TABLE auth_audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(50) NOT NULL,  -- REGISTER, LOGIN_SUCCESS, LOGOUT, EMAIL_VERIFIED,
                                       -- DRIVER_APPROVED, DRIVER_REJECTED, SUSPICIOUS_TOKEN_REUSE,
                                       -- PASSWORD_CHANGED_FIRST_TIME, STAFF_CREATED, ...
    actor_id    UUID        REFERENCES "user"(id) ON DELETE SET NULL,
    actor_role  VARCHAR(20),
    target_id   UUID,                  -- driver_id khi DRIVER_APPROVED/REJECTED
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    metadata    JSONB,                 -- extra context (username, email, reason, ...)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor    ON auth_audit_log (actor_id);
CREATE INDEX idx_audit_event    ON auth_audit_log (event_type);
CREATE INDEX idx_audit_created  ON auth_audit_log (created_at DESC);
-- KHONG co deleted_at — audit log khong bao gio xoa (HR-13)
```

---

## Error Matrix

> Tat ca error code tra ve boi Spec #001. Frontend dung `error_code` de hien thi thong bao dung.

| HTTP | error_code | Trigger FR | Mo ta ngan |
|------|-----------|-----------|------------|
| 201 | — | FR-001, FR-041, FR-069 | Tao tai khoan thanh cong |
| 200 | — | FR-014, FR-023, FR-028, FR-030, FR-038, FR-047, FR-056, FR-066, FR-068 | Thanh cong |
| 401 | `INVALID_CREDENTIALS` | FR-019, FR-022 | Username/email hoac password sai |
| 401 | `NO_REFRESH_TOKEN` | FR-027 | Cookie refresh_token khong ton tai |
| 401 | `TOKEN_REUSE_DETECTED` | FR-029 | Refresh token da bi revoke duoc dung lai |
| 401 | `AUTHENTICATION_REQUIRED` | FR-075 | Guest goi endpoint can auth |
| 403 | `EMAIL_NOT_VERIFIED` | FR-020, FR-048 | Chua verify email, kem `can_resend_verification: true` |
| 403 | `ONBOARDING_INCOMPLETE` | FR-049 | Driver chua hoan tat onboarding, kem `current_step` |
| 403 | `ONBOARDING_PENDING_REVIEW` | FR-049 | Driver dang cho Manager duyet |
| 403 | `ACCOUNT_REJECTED` | FR-068 | Driver bi tu choi, kem `rejection_reason`, `can_resubmit: true` |
| 403 | `INVALID_ONBOARDING_STEP` | FR-050, FR-059 | Goi endpoint sai buoc onboarding |
| 403 | `PASSWORD_CHANGE_REQUIRED` | FR-039 | Staff dung short-lived token goi endpoint khac |
| 403 | `SCOPE_INSUFFICIENT` | FR-038 | Token khong co scope CHANGE_PASSWORD_ONLY |
| 403 | `FORBIDDEN` | FR-058 | Driver xem tai lieu cua Driver khac |
| 404 | `TOKEN_NOT_FOUND` | FR-012 | Token verify email khong ton tai |
| 404 | — | FR-065 | Driver id khong ton tai hoac da xoa |
| 409 | `CONFLICT` | FR-004, FR-043 | Username/email/phone da ton tai |
| 409 | `VEHICLE_ALREADY_REGISTERED` | FR-055 | Driver upload vehicle thu 2 |
| 409 | `INVALID_STATUS_TRANSITION` | FR-066 | Approve Driver khong o PENDING_APPROVAL |
| 410 | `TOKEN_EXPIRED` | FR-013 | Token verify email da het han, kem `can_resend: true` |
| 422 | `VALIDATION_ERROR` | FR-003, FR-042, FR-052, FR-054, FR-067 | Input khong hop le, kem `details[]` |
| 422 | `INVALID_FILE` | FR-052 | MIME/size/empty check fail |
| 422 | `INVALID_ROLE` | FR-071 | Admin tao Staff voi role Customer/Driver |
| 422 | `PASSWORD_SAME_AS_TEMP` | FR-040 | Password moi trung password tam |
| 423 | `ACCOUNT_LOCKED` | FR-021, FR-032 | Tai khoan bi khoa, kem `minutes_remaining` |
| 423 | `ACCOUNT_LOCKED_NOW` | FR-022 | Tai khoan vua bi khoa do sai 5 lan |
| 429 | `RATE_LIMITED` | FR-018, FR-035 | Vuot rate limit, kem `retry_after_seconds` |

---

## Architectural Constraints (AC Mapping)

> Cach tung rule Constitution v1.2.0 duoc enforce trong Spec #001.

| Constitution Rule | Ap dung o dau trong Spec #001 |
|-------------------|-------------------------------|
| **HR-01** Secrets khong git | JWT_SECRET, BCrypt cost → env var. Cloudinary creds → env var (AC-10). |
| **HR-02** BCrypt no plaintext | FR-006, FR-036, FR-044, FR-069. Password_hash NEVER in log/response/audit. |
| **HR-04** Verify HMAC IPN | FR-061 buoc 1 — verify truoc moi logic. |
| **HR-10** Trai quyen → 403 | FR-058, FR-039, FR-050, FR-059, FR-066 — role check truoc handler. |
| **HR-11** Email loi khong rollback | FR-009, FR-045, FR-063, FR-066, FR-067, FR-069 — tat ca qua @Async. |
| **HR-12** Quy trinh tao tai khoan | FR-041 (Driver tu dang ky), FR-069 (Admin tao Staff), FR-071 (guard). |
| **HR-13** Audit log state change | auth_audit_log insert o FR-010, FR-014, FR-046, FR-061, FR-066, FR-067, FR-068. |
| **HR-15** Idempotency IPN | FR-062 — UNIQUE constraint tren vnp_txn_ref + RspCode=02 khi trung. |
| **HR-16** Rate limit + lockout | FR-018 (5/IP/15p login), FR-031/032 (lockout 5 sai/15p), FR-035 (bang rate limit). |
| **HR-17** Public vs Auth endpoints | FR-072/073 — 6 /api/public/* la permitAll(), moi path khac authenticated(). |
| **HR-18** Wallet khong am | FR-061 — deposit_balance += 3M trong transaction + wallet_transaction INSERT. |
| **AC-03** JWT rotation | FR-024 (access 15p), FR-025/026 (refresh 7d httpOnly cookie), FR-028 (rotation), FR-029 (panic). |
| **AC-07** Timezone UTC | Moi TIMESTAMPTZ column luu UTC. Peak-hour check (FR-072 quote) convert sang Asia/Ho_Chi_Minh. |
| **AC-09** Soft delete | Bang `user`: `deleted_at` nullable, moi query filter `deleted_at IS NULL`. |
| **AC-10** Cloudinary signed | FR-053 — backend ky request, client khong bao gio upload truc tiep. Signed URL expire 1h (FR-057, FR-065). |
| **AC-11** CORS whitelist | Spring Security config: `localhost:5500`, `127.0.0.1:5500` (dev); prod URL khi chot deploy. |
| **AC-12** Flyway migration | Moi bang tren phai co file `V{n}__....sql` trong `db/migration/`. |
| **AC-13** wallet_transaction | FR-061 — INSERT wallet_transaction kem UPDATE wallet trong cung transaction. |

---

## Non-Functional Requirements (NFR)

| # | Yeu cau | Nguong | Do o nhu the nao |
|---|---------|--------|-----------------|
| NFR-01 | Latency login happy path | P95 < 500ms | Spring Actuator metrics + log |
| NFR-02 | Latency register | P95 < 800ms | Bao gom BCrypt hash ~300ms cost 12 |
| NFR-03 | Cloudinary upload timeout | <= 10 giay | Spring RestTemplate timeout config |
| NFR-04 | Email async khong block | <= 5ms block time main thread | Spring @Async thread pool rieng |
| NFR-05 | Audit log retention | Giu >= 90 ngay | Khong xoa auth_audit_log < 90 ngay |
| NFR-06 | Refresh token cleanup | Revoked + expired token > 30 ngay → scheduled job xoa | Giam bloat bang refresh_token |
| NFR-07 | BCrypt cost | Cost = 12 (~300ms tren server CPU thong thuong) | Khong tang len 13+ (ha UX login), khong giam xuong 10 (bao mat kem) |

---

## Constitution Compliance Check (Self-Check cho Spec nay)

```
=== CONSTITUTION CHECK REPORT ===
Feature  : "#1 Auth/RBAC + Guest Mode v2.0"
Artifact : spec
Date     : 2026-05-29

--- LAYER 1 ---
HR-01  Secrets khong commit git                        [ PASS ] — env var pattern (JWT_SECRET, CLOUDINARY_*)
HR-02  Password BCrypt, khong plaintext               [ PASS ] — FR-006, BCrypt cost 12, khong log
HR-03  IPN la nguon cap nhat duy nhat                 [ PASS ] — FR-061, return URL chi hien thi
HR-04  Verify HMAC-SHA512 truoc khi xu ly IPN         [ PASS ] — FR-061 buoc 1
HR-05  Transition khong hop le → 409                  [ N/A  ] — Order SM khong trong scope nay
HR-06  Driver blocked confirm khi IN_DISPUTE          [ N/A  ]
HR-07  Chi Manager/Admin dong IN_DISPUTE→COMPLETED    [ N/A  ]
HR-08  Chi assign Staff FREE, trong transaction       [ N/A  ]
HR-09  IPN timeout 15ph → auto-CANCELLED              [ PASS ] — FR-063 scheduled job cho deposit
HR-10  Trai quyen → 403                               [ PASS ] — FR-039, FR-050, FR-058, FR-059, FR-066
HR-11  Email loi khong rollback TX                    [ PASS ] — FR-009, FR-045, FR-063, FR-066 → @Async
HR-12  Quy trinh tao tai khoan theo role              [ PASS ] — FR-041 Driver, FR-069 Staff, FR-071 guard
HR-13  Audit log moi state change                     [ PASS ] — auth_audit_log insert o 8+ FRs
HR-14  RefundRecord chi khi COMPANY huy               [ N/A  ]
HR-15  Idempotency IPN                                [ PASS ] — FR-062, UNIQUE vnp_txn_ref
HR-16  Rate limit login + lockout                     [ PASS ] — FR-018, FR-031..FR-035
HR-17  Public vs Auth endpoints tuong minh            [ PASS ] — FR-072, FR-073, FR-075
HR-18  Wallet balance khong am, audit trail           [ PASS ] — FR-061 (deposit += 3M + wallet_transaction)

Layer 1 Result: [ ALL PASS / N/A → CLEARED ]

--- LAYER 2 ---
AC-01  Stack Spring Boot + HTML tinh + PG             [ PASS ] — khong co framework ngoai scope
AC-02  REST thuan, khong GraphQL/RPC                  [ PASS ] — moi endpoint la REST
AC-03  JWT 15p + 7d refresh + rotation + DB store     [ PASS ] — FR-024..FR-029
AC-04  Khong noi chuoi SQL thu cong                   [ PASS ] — JPA/JPQL, parameterized queries
AC-05  Chat STOMP + fallback polling                  [ N/A  ] — khong trong scope nay
AC-06  Maps API co fallback bang quan                 [ N/A  ] — quote-estimate dung quan→quan fallback (Spec #006)
AC-07  Timezone UTC store + Asia/Ho_Chi_Minh display  [ PASS ] — TIMESTAMPTZ, convert khi compare gio
AC-08  Tien te BigDecimal scale=0                     [ PASS ] — deposit 3,000,000 NUMERIC(15,0)
AC-09  Soft delete (deleted_at) cho user              [ PASS ] — bang user co deleted_at
AC-10  Cloudinary signed upload; expire URL 1h        [ PASS ] — FR-053, FR-057, FR-065
AC-11  CORS whitelist tuong minh                      [ PASS ] — application-{profile}.properties
AC-12  Flyway migration; ddl-auto=validate            [ PASS ] — tat ca bang → V{n}__xxx.sql
AC-13  Money flow audit trail (wallet_transaction)    [ PASS ] — FR-061: INSERT wallet_transaction atomic

Layer 2 Result: [ ALL PASS / N/A → CLEARED ]

=== SUMMARY ===
Layer 1 : [18/18 PASS or N/A]
Layer 2 : [13/13 PASS or N/A]
Layer 3 : [Apply at code review]
Status  : CLEARED TO PROCEED TO IMPLEMENTATION
================================
```

---

## Open Questions

| # | Cau hoi | Block gi | Uu tien | Trang thai |
|---|---------|---------|---------|-----------|
| OQ-1 | Driver onboarding co yeu cau them CCCD (CMND) nhu la loai giay to thu 4 khong? CONTEXT.md v2.0 liet ke 3 loai (GPLX, Dang ky xe, Anh xe) nhung prompt Spec yeu cau 4. | FR-051 — so document_type | High | Mo — can xac nhan voi team/thay truoc sprint |
| OQ-2 | Password toi thieu Staff (Manager/Admin): 8 ky tu nhu Customer hay cao hon (vi du 12 ky tu)? | FR-036, FR-069 | Low | Mo — default: ap dung chinh sach 8 ky tu chung (FR-002) |
| OQ-3 | Khi Driver bi SUSPENDED (do DamageReport tru het coc), co the re-login khong? Flow nap lai coc 3 trieu nhu the nao — co endpoint rieng hay dung lai /api/driver/me/deposit? | FR-059, Spec #018 Wallet | Medium | Mo — tam defer sang Spec #018 |
| OQ-4 | Rate limit ngu sau bao nhieu lan? Hien tai FR-035 dung in-memory (mat khi restart). Neu can survive restart → phai dung Redis. Cloud provider nao se dung? | FR-035, Infra | Low | Mo — default in-memory du cho demo; note thay doi khi deploy prod |
| OQ-5 | Public endpoint GET /api/public/landing-info tra ve nhung field gi chinh xac? Content do ai quan ly (hardcode hay Admin edit qua CMS)? | FR-072 | Low | Mo — tam hardcode trong application.yml; CMS la Phase 2 |

---

## Frontend Implementation Note

> Huong dan cho FE Vanilla JS. Khong phai spec — chi la guidance de code dung.

**Luu token:**
- `access_token` → `localStorage.setItem('access_token', token)` — JS co the doc
- `refresh_token` → KHONG luu trong JS; browser tu luu qua `Set-Cookie: HttpOnly`

**Gui request authenticated:**
```javascript
const res = await fetch('/api/orders', {
  headers: { 'Authorization': `Bearer ${localStorage.getItem('access_token')}` },
  credentials: 'include'   // bat buoc de browser gui cookie refresh_token
});
```

**Auto-refresh khi access token het han:**
```javascript
if (res.status === 401) {
  const refresh = await fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'include'  // gui cookie
  });
  if (refresh.ok) {
    const { access_token } = await refresh.json();
    localStorage.setItem('access_token', access_token);
    // retry request goc
  } else {
    // refresh that bai (het han, bi revoke) → redirect /login
    localStorage.removeItem('access_token');
    window.location.href = '/login';
  }
}
```

**Xu ly must_change_password:**
```javascript
if (loginResponse.user_info.must_change_password) {
  window.location.href = '/change-password-first-time';
  // Page nay chi goi POST /api/auth/change-password-first-time
  // Moi endpoint khac → 403 PASSWORD_CHANGE_REQUIRED
}
```

**Logout:**
```javascript
await fetch('/api/auth/logout', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${localStorage.getItem('access_token')}` },
  credentials: 'include'
});
localStorage.removeItem('access_token');
window.location.href = '/login';
```

**Guest mode:**
- Cac trang public (`/`, `/pricing`, `/quote`, `/become-driver`, `/faq`, `/terms`) goi
  `/api/public/*` — KHONG can Authorization header, KHONG can credentials: 'include'
- Khi Guest bam nut can login → hien modal "Dang nhap / Dang ky" (khong redirect trang)
- Phan biet 401 vs 403: 401 → redirect `/login`; 403 → hien thong bao "Khong co quyen"
