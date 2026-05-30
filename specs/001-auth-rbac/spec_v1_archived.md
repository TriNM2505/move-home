# Feature Specification: Authentication & Authorization (RBAC)

**Feature Branch**: `001-auth-rbac`

**Feature Number**: #1 of 21 — CORE (foundation for all other features)

**Created**: 2026-05-29

**Last Amended**: 2026-05-29 (post-Open-Questions review — OQ-3 forced password change + OQ-5 httpOnly cookie)

**Status**: Draft

**Input**: User description: "Authentication and Authorization (RBAC) for Move_home — 5 roles
(Customer/Driver/Porter/Manager/Admin). Customer registers with 8 required fields + 2 optional.
Customer logs in with username; Staff with email. Email verification required (token 24h).
JWT access 15min + refresh 7d with rotation. Account lockout after 5 failed login (15min).
Rate limit per IP. Audit log all auth events. Full compliance with constitution v1.1.0."

**CONTEXT.md reference**: v1.5 §2 (Auth), §3 (RBAC table), §4 (Constraints), §5 (A21)

---

## Out of Scope

The following items are explicitly NOT part of this spec. Each has a designated future spec:

1. **Forgot Password / Reset Password** — DEFERRED — confirmed by leader. Customer reset via
   email link will be specced separately after Feature #9. Staff reset: still TBD pending
   OQ-2 resolution.
2. **Forgot Password for Staff** — DEFERRED — confirmed, blocked by OQ-3 decision (resolved:
   forced password change IS mandatory). Staff forgot-password flow will be designed after
   Feature #15 (Staff Account Management) is spec'd.
3. **Admin creates Staff accounts (Feature #15)** — full flow (form, fields) in the Staff
   Account Management spec. This spec covers ONLY the Auth impact: new Staff account is
   created with `status=ACTIVE`, `must_change_password=true`, and a temporary password sent
   by email (see FR-031, FR-032, FR-033).
4. **Profile management** — editing email, phone, full_name, avatar after registration is a
   separate SHELL feature.
5. **Two-factor authentication, social login (Google/Facebook), OAuth2** — out of scope for the
   6-week sprint.

---

## User Scenarios & Testing

### User Story 1 — Customer Self-Registration (Priority: P1)

A new customer visits the website and creates their own account to be able to place moving orders.
This is the primary entry point for all customer revenue.

**Why this priority**: No registration = no customers = no orders. Foundational to the entire
business flow.

**Independent Test**: A new user can fill the registration form, receive a verification email,
click the link, and then log in successfully — all without any manual admin action.

**Acceptance Scenarios**:

1. **Given** a visitor on the registration page with all 8 required fields correctly filled,
   **When** they submit the form,
   **Then** the system creates the account with status `PENDING_VERIFY`, sends a verification
   email within 30 seconds, and returns HTTP 201 with `{ user_id, message }`.

2. **Given** a visitor submitting registration with an already-used username,
   **When** the form is submitted,
   **Then** the system returns HTTP 409 with `{ error_code: "USERNAME_TAKEN", field: "username" }`
   and does NOT create any account.

3. **Given** a visitor submitting registration with `date_of_birth` making them 15 years old,
   **When** the form is submitted,
   **Then** the system returns HTTP 422 with `{ error_code: "VALIDATION_ERROR", details:
   [{ field: "date_of_birth", message: "Must be at least 16 years old" }] }`.

4. **Given** a visitor submitting with `terms_accepted = false`,
   **When** the form is submitted,
   **Then** the system returns HTTP 422 listing `terms_accepted` as a required true field.

5. **Given** a visitor submitting with phone `0912345678` (Vietnamese local format),
   **When** the form is submitted and the account is created,
   **Then** the stored phone in DB is `+84912345678` (normalized).

---

### User Story 2 — Email Verification (Priority: P1)

After registration, the customer must verify their email before they can log in and use the
system. This prevents fake accounts and ensures reachable contact info.

**Why this priority**: An unverified account cannot place orders. Core business protection.

**Independent Test**: A customer with status `PENDING_VERIFY` who clicks the verification link
in their email gets their account set to `ACTIVE` and can then log in.

**Acceptance Scenarios**:

1. **Given** a customer with a valid, unexpired verification token,
   **When** they click the verification link (`GET /api/auth/verify?token=xxx`),
   **Then** their account status changes to `ACTIVE`, the token is deleted, and they are
   redirected to the login page.

2. **Given** a customer attempting to log in while status is `PENDING_VERIFY`,
   **When** they submit valid credentials,
   **Then** the system returns HTTP 403 with `{ error_code: "EMAIL_NOT_VERIFIED",
   can_resend_verification: true }`.

3. **Given** a customer with an expired verification token (older than 24 hours),
   **When** they click the verification link,
   **Then** the system returns HTTP 410 with `{ error_code: "TOKEN_EXPIRED",
   message: "Token đã hết hạn. Vui lòng yêu cầu gửi lại email xác nhận." }`.

4. **Given** a customer who already verified their email trying to use the same token again,
   **When** they click the link a second time,
   **Then** the system returns HTTP 404 with `{ error_code: "TOKEN_NOT_FOUND" }`.

5. **Given** a customer requesting resend verification email,
   **When** they POST their email within 60 seconds of the last resend,
   **Then** the system returns HTTP 429 with `{ error_code: "RATE_LIMIT_EXCEEDED",
   retry_after_seconds: N }`.

---

### User Story 3 — Login & JWT Issuance (Priority: P1)

Registered and verified users (all 5 roles) can log in and receive tokens to access protected
features.

**Why this priority**: All other features require an authenticated session. Login is the gate.

**Independent Test**: A verified customer can log in with username + password and receive a
working access token that allows them to call a protected endpoint.

**Acceptance Scenarios**:

1. **Given** a verified Customer with correct username and password,
   **When** they POST to `/api/auth/login`,
   **Then** the system returns HTTP 200 with `{ access_token, refresh_token, expires_in: 900,
   user: { id, role: "CUSTOMER", username, full_name } }`.

2. **Given** a Staff member (Driver) with correct email and password,
   **When** they POST to `/api/auth/login`,
   **Then** the system returns HTTP 200 with `{ access_token, refresh_token, expires_in: 900,
   user: { id, role: "DRIVER", email, full_name } }`.

3. **Given** a user submitting wrong password 5 times consecutively,
   **When** the 5th wrong attempt is submitted,
   **Then** the account is locked for 15 minutes and returns HTTP 423 with
   `{ error_code: "ACCOUNT_LOCKED", locked_until: "<ISO timestamp>", minutes_remaining: 15 }`.

4. **Given** a locked account where the user now provides the correct password,
   **When** they try to log in before `locked_until` expires,
   **Then** the system still returns HTTP 423 (correct password does not bypass lock).

5. **Given** the same IP address making 6 login attempts in 15 minutes,
   **When** the 6th attempt is made,
   **Then** the system returns HTTP 429 with `{ error_code: "TOO_MANY_REQUESTS",
   retry_after_seconds: N }` regardless of credential correctness.

---

### User Story 4 — Token Refresh & Secure Rotation (Priority: P2)

Logged-in users maintain their session by refreshing tokens without re-entering credentials.
Stolen token reuse is detected and triggers a security lockdown.

**Why this priority**: Without refresh, users are logged out every 15 minutes — unusable UX.
Token reuse detection is a key security control.

**Independent Test**: A user can call `/api/auth/refresh` with a valid refresh token and receive
a new pair of tokens. The old refresh token is invalidated. Using the old token again triggers
a security alert.

**Acceptance Scenarios**:

1. **Given** a user with a valid, non-revoked refresh token,
   **When** they POST it to `/api/auth/refresh`,
   **Then** the system returns a new `access_token` + new `refresh_token`; the old refresh
   token is marked revoked and cannot be used again.

2. **Given** an attacker who obtained a refresh token that has already been rotated (used once),
   **When** they POST the old refresh token to `/api/auth/refresh`,
   **Then** the system revokes ALL active refresh tokens for that user (panic mode), returns
   HTTP 401, and logs a `SUSPICIOUS_TOKEN_REUSE` audit event.

3. **Given** a user with an expired refresh token (older than 7 days),
   **When** they attempt to refresh,
   **Then** the system returns HTTP 401 with `{ error_code: "REFRESH_TOKEN_EXPIRED" }`.

---

### User Story 5 — Logout (Priority: P2)

Users can explicitly terminate their session, invalidating the refresh token server-side so
it cannot be reused even if captured.

**Acceptance Scenarios**:

1. **Given** a logged-in user who sends their refresh token,
   **When** they POST to `/api/auth/logout`,
   **Then** the refresh token is marked revoked in the DB, and the system returns HTTP 204.

2. **Given** a logged-out user attempting to use the revoked refresh token,
   **When** they POST to `/api/auth/refresh`,
   **Then** the system returns HTTP 401 `REFRESH_TOKEN_REVOKED`.

---

### User Story 6 — RBAC Access Control (Priority: P1)

Every protected endpoint enforces role-based access. Users without the required role receive
403, not a redirect or silent failure.

**Independent Test**: A Customer token used against a Manager-only endpoint returns HTTP 403.
A Manager token used against a Driver-only endpoint returns HTTP 403.

**Acceptance Scenarios**:

1. **Given** a Customer's access token,
   **When** they call `POST /api/trips/{id}/assign` (Manager-only),
   **Then** the system returns HTTP 403 `{ error_code: "FORBIDDEN", required_role: "MANAGER" }`.

2. **Given** an expired access token (older than 15 minutes),
   **When** any protected endpoint is called,
   **Then** the system returns HTTP 401 `{ error_code: "TOKEN_EXPIRED" }`.

3. **Given** a request with no Authorization header,
   **When** a protected endpoint is called,
   **Then** the system returns HTTP 401 `{ error_code: "UNAUTHORIZED" }`.

---

### Edge Cases

- What happens when a Customer tries to log in using their email instead of username?
  → System treats the identifier as a username lookup (no `@` detection needed — identifier
  containing `@` is routed to Staff path; without `@` → Customer path). If a Customer
  accidentally types their email → no match found → generic HTTP 401.
- What if the verification email is never delivered (SMTP failure)?
  → Email failure is silent per HR-11 (async, no rollback). Account remains `PENDING_VERIFY`.
  Customer must use the resend endpoint. Admin can manually verify in future spec (Feature #15).
- What happens if the same phone number is submitted in 3 different formats?
  → All three formats (`0912...`, `+84912...`, `84912...`) normalize to `+84912...` before
  the UNIQUE check. They are treated as the same number → HTTP 409 `PHONE_TAKEN`.
- Can Admin log in as a Customer?
  → No. Admin role has no `username`, only `email`. Admin uses the Staff login path.
- Can two users have the same `full_name`?
  → Yes, `full_name` is not unique. Only `username`, `email`, and `phone` are unique.
- What if a user registers, never verifies, and the token expires?
  → Account remains `PENDING_VERIFY` indefinitely (soft-delete per AC-09 only on explicit
  Admin action). Customer must request a new verification email via resend endpoint.

---

## Requirements

### Functional Requirements (EARS notation)

**Registration:**

- **FR-001** WHEN a visitor submits `POST /api/auth/register` with all 8 required fields
  valid, THE system SHALL create a user account with `role=CUSTOMER`, `status=PENDING_VERIFY`,
  send a verification email asynchronously, and return HTTP 201.

- **FR-002** WHERE `username` contains characters outside `[a-z0-9_.]` or does not start with
  a letter, or is shorter than 4 or longer than 20 characters, THE system SHALL return HTTP 422
  listing the specific violation for the `username` field.

- **FR-003** WHERE `username` or `email` or `phone` (after normalization) already exists in the
  system, THE system SHALL return HTTP 409 with `{ error_code: "FIELD_TAKEN", field: "<name>" }`
  and NOT create any partial record.

- **FR-004** WHERE `date_of_birth` results in an age less than 16 years as of the registration
  date, THE system SHALL return HTTP 422 with a clear message indicating the minimum age
  requirement.

- **FR-005** WHERE `terms_accepted` is not `true`, THE system SHALL return HTTP 422 and refuse
  registration.

- **FR-006** WHEN a valid phone number is submitted in any of the three accepted formats
  (`0xxxxxxxxx`, `+84xxxxxxxxx`, `84xxxxxxxxx`), THE system SHALL normalize and store it as
  `+84xxxxxxxxx` before uniqueness check.

- **FR-007** WHEN a registration succeeds, THE system SHALL hash the password using BCrypt with
  cost factor 12 and store only the hash — never the plaintext password.

- **FR-008** WHERE registration is attempted from an IP that has already submitted 3 registration
  requests in the last hour, THE system SHALL return HTTP 429 and reject the request.

**Email Verification:**

- **FR-009** WHEN a registration succeeds, THE system SHALL create an email verification token
  (UUID v4) with expiry of 24 hours from creation time (stored in UTC per AC-07) and dispatch
  it asynchronously via Gmail SMTP.

- **FR-010** WHEN a visitor calls `GET /api/auth/verify?token=xxx` with a valid, non-expired
  token, THE system SHALL set `user.status = ACTIVE`, delete the token record, log
  `EMAIL_VERIFIED`, and return HTTP 200.

- **FR-011** WHERE the verification token has expired (older than 24 hours), THE system SHALL
  return HTTP 410 and NOT update user status.

- **FR-012** WHERE the verification token does not exist in the database (used, deleted, or
  invalid), THE system SHALL return HTTP 404 and NOT reveal whether the user account exists.

- **FR-013** WHEN `POST /api/auth/resend-verification` is called for an email that belongs to
  a `PENDING_VERIFY` account and the rate limit is not exceeded, THE system SHALL create a new
  token (invalidating any existing token for that user), send the email, and return HTTP 200
  with a generic success message regardless of whether the email actually exists.

- **FR-014** WHERE `POST /api/auth/resend-verification` is called within 60 seconds of the last
  resend for the same email, THE system SHALL return HTTP 429 with `retry_after_seconds`.

**Login:**

- **FR-015** WHEN `POST /api/auth/login` is submitted with `identifier` not containing `@`,
  THE system SHALL treat it as a Customer login and look up by `username`. WHEN `identifier`
  contains `@`, THE system SHALL treat it as a Staff login and look up by `email`.

- **FR-016** WHERE the user is not found OR the password does not match, THE system SHALL return
  HTTP 401 with a generic message `"Username hoặc password không đúng"` — NOT revealing which
  field is incorrect (anti-enumeration).

- **FR-017** WHERE `user.status = PENDING_VERIFY`, THE system SHALL return HTTP 403 with
  `{ error_code: "EMAIL_NOT_VERIFIED", can_resend_verification: true }`.

- **FR-018** WHERE `user.locked_until > NOW()` (UTC), THE system SHALL return HTTP 423 with
  `{ error_code: "ACCOUNT_LOCKED", locked_until: "<ISO 8601>", minutes_remaining: N }`.

- **FR-019** WHEN login succeeds, THE system SHALL reset `failed_login_count = 0`,
  issue an access token (15-minute expiry) and a refresh token (7-day expiry), persist the
  refresh token in the `refresh_token` table (storing only the hash), return the
  `access_token` in the JSON response body, and set the `refresh_token` in an httpOnly cookie
  (see FR-036 for cookie attributes). The refresh token MUST NOT appear in the JSON body.

- **FR-020** WHEN a login attempt fails due to wrong password for an existing account, THE system
  SHALL increment `user.failed_login_count`. WHILE `failed_login_count` reaches 5, THE system
  SHALL set `user.locked_until = NOW() + 15 minutes` and log a `LOCKOUT` audit event.

- **FR-021** WHERE the IP address has made 5 or more failed login attempts in the last 15 minutes,
  THE system SHALL return HTTP 429 before checking credentials.

**Token Management:**

- **FR-022** WHEN `POST /api/auth/refresh` is called and the browser sends a valid, non-revoked,
  non-expired refresh token via the httpOnly cookie `refresh_token`, THE system SHALL:
  (a) set `revoked_at = NOW()` on the old token, (b) create and persist a new refresh token,
  (c) return the new `access_token` in the JSON response body and set the new `refresh_token`
  in a new httpOnly cookie (per FR-036), (d) return HTTP 200.

- **FR-023** WHERE a refresh token that has already been revoked is presented to
  `POST /api/auth/refresh`, THE system SHALL revoke ALL active refresh tokens for that user,
  log `SUSPICIOUS_TOKEN_REUSE`, and return HTTP 401 `{ error_code: "TOKEN_REUSE_DETECTED" }`.

- **FR-024** WHERE a refresh token has expired (`expires_at < NOW()`), THE system SHALL return
  HTTP 401 `{ error_code: "REFRESH_TOKEN_EXPIRED" }`.

- **FR-025** WHEN `POST /api/auth/logout` is called with a valid Authorization header and the
  refresh token delivered via httpOnly cookie `refresh_token`, THE system SHALL set
  `revoked_at = NOW()` on that refresh token, log `LOGOUT`, clear the cookie by responding
  with `Set-Cookie: refresh_token=; Max-Age=0; Path=/api/auth/; HttpOnly`, and return HTTP 204.

**RBAC / Access Control:**

- **FR-026** WHILE a request to any protected endpoint is made without a valid Bearer token in
  the Authorization header, THE system SHALL return HTTP 401 `{ error_code: "UNAUTHORIZED" }`.

- **FR-027** WHERE a valid token belongs to a role that does not have permission to access the
  requested endpoint (per RBAC table in CONTEXT §3), THE system SHALL return HTTP 403
  `{ error_code: "FORBIDDEN" }`.

- **FR-028** WHERE an access token has expired, THE system SHALL return HTTP 401
  `{ error_code: "TOKEN_EXPIRED" }` — NOT HTTP 403.

**Audit Logging:**

- **FR-029** THE system SHALL append a record to `audit_log_auth` for every auth event:
  `REGISTER`, `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOCKOUT`, `EMAIL_VERIFIED`,
  `VERIFICATION_RESENT`, `TOKEN_REFRESHED`, `LOGOUT`, `SUSPICIOUS_TOKEN_REUSE`,
  `ADMIN_CREATED_STAFF`, `PASSWORD_CHANGED_FIRST_TIME`.
  Each record MUST include: `event_type`, `user_id` (nullable on failed lookups),
  `ip_address`, `user_agent`, `created_at` (UTC), and optional `extra_info` (JSON).

- **FR-030** THE system SHALL write audit log records asynchronously so that audit log INSERT
  latency does NOT add to the user-facing response time of any auth endpoint.

**First-Time Password Change (OQ-3 — mandatory for Staff):**

- **FR-031** WHEN Admin creates a Staff account via `POST /api/admin/staff`, THE system SHALL
  set `must_change_password = true` and `status = ACTIVE` on the new user record, generate a
  temporary password, send it to the Staff's email asynchronously, and log
  `ADMIN_CREATED_STAFF`. Staff accounts created this way bypass email verification.

- **FR-032** WHEN a Staff user with `must_change_password = true` provides correct credentials
  at `POST /api/auth/login`, THE system SHALL return HTTP 200 with a short-lived token
  (5-minute expiry, scope restricted to `POST /api/auth/change-password-first-time` only)
  and the flag `must_change_password: true` in the response body. No full access token or
  refresh cookie is issued at this step.

- **FR-033** WHEN Staff calls `POST /api/auth/change-password-first-time` with the short-lived
  token in the Authorization header and a valid `new_password` (meeting password policy in
  FR-002), THE system SHALL update `password_hash`, set `must_change_password = false`, issue
  a full access token (15 min) + set refresh token httpOnly cookie (7 days), log
  `PASSWORD_CHANGED_FIRST_TIME`, and return HTTP 200 with the full token response.

- **FR-034** WHERE a Staff user with `must_change_password = true` uses the short-lived token
  to call any endpoint OTHER THAN `POST /api/auth/change-password-first-time`, THE system
  SHALL return HTTP 403 with `{ error_code: "MUST_CHANGE_PASSWORD",
  message: "Phải đổi password trước khi sử dụng hệ thống" }`.

- **FR-035** WHERE the `new_password` submitted to
  `POST /api/auth/change-password-first-time` is identical to the current temporary password,
  THE system SHALL return HTTP 422 with
  `{ error_code: "VALIDATION_ERROR", details: [{ field: "new_password",
  message: "Password mới phải khác password tạm" }] }`.

**Refresh Token Cookie (OQ-5 — httpOnly cookie):**

- **FR-036** WHEN the system sets a refresh token cookie (on login or token rotation), THE
  system SHALL use ALL of the following attributes: `HttpOnly=true`, `SameSite=Lax`,
  `Secure=true` (production environment), `Secure=false` (local dev), `Path=/api/auth/`,
  `Max-Age=604800` (7 days in seconds).

### Key Entities

- **User**: Central entity. One record per human. Discriminated by `role` (CUSTOMER / DRIVER /
  PORTER / MANAGER / ADMIN). Customer has `username`; Staff has no `username` (NULL).
  Both have `email`. Soft-deleted via `deleted_at` (AC-09).

- **EmailVerification**: Ephemeral token linking a `user_id` to a UUID verification code with
  a 24-hour expiry. Deleted on successful verification.

- **RefreshToken**: Persistent server-side record of issued refresh tokens. Stores only the
  token hash (never plaintext). Supports rotation (old → `revoked_at`, new → INSERT).
  Supports panic-mode revocation of all tokens for a user.

- **AuditLogAuth**: Immutable append-only log of all auth events. Never soft-deleted.
  Queryable by Admin for security investigations.

---

## Data Model

```
┌─────────────────────────────────────────────────────────────────────┐
│ user                                                                │
├──────────────────────────────┬──────────────────────────────────────┤
│ id                           │ BIGINT PK AUTO_INCREMENT             │
│ role                         │ ENUM(CUSTOMER,DRIVER,PORTER,         │
│                              │      MANAGER,ADMIN) NOT NULL         │
│ status                       │ ENUM(PENDING_VERIFY,ACTIVE,          │
│                              │      SUSPENDED) NOT NULL DEFAULT     │
│                              │      'PENDING_VERIFY'                │
│ username                     │ VARCHAR(20) UNIQUE NULL              │
│                              │ (Customer only; Staff = NULL)        │
│ email                        │ VARCHAR(255) UNIQUE NOT NULL         │
│ phone                        │ VARCHAR(15) UNIQUE NULL              │
│                              │ (normalized: +84xxxxxxxxx)           │
│ password_hash                │ VARCHAR(60) NOT NULL (BCrypt output) │
│ full_name                    │ VARCHAR(100) NOT NULL                │
│ date_of_birth                │ DATE NULL                            │
│ gender                       │ ENUM(MALE,FEMALE,OTHER,              │
│                              │      PREFER_NOT_TO_SAY) NULL         │
│ avatar_cloudinary_public_id  │ VARCHAR(255) NULL                    │
│ failed_login_count           │ INT NOT NULL DEFAULT 0               │
│ locked_until                 │ TIMESTAMPTZ NULL                     │
│ terms_accepted_at            │ TIMESTAMPTZ NULL (Customer only)     │
│ must_change_password         │ BOOLEAN NOT NULL DEFAULT false       │
│                              │ true khi Admin tạo Staff account;    │
│                              │ set false sau khi Staff đổi pw lần   │
│                              │ đầu (FR-033). Luôn false cho Customer│
│ created_at                   │ TIMESTAMPTZ NOT NULL                 │
│ updated_at                   │ TIMESTAMPTZ NOT NULL                 │
│ deleted_at                   │ TIMESTAMPTZ NULL (soft delete AC-09) │
└──────────────────────────────┴──────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ email_verification                                                  │
├──────────────────────────────┬──────────────────────────────────────┤
│ token                        │ UUID PK                              │
│ user_id                      │ BIGINT FK → user.id                  │
│ expires_at                   │ TIMESTAMPTZ NOT NULL (created +24h)  │
│ created_at                   │ TIMESTAMPTZ NOT NULL                 │
└──────────────────────────────┴──────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ refresh_token                                                       │
├──────────────────────────────┬──────────────────────────────────────┤
│ id                           │ UUID PK                              │
│ user_id                      │ BIGINT FK → user.id                  │
│ token_hash                   │ VARCHAR(64) NOT NULL (SHA-256 hash)  │
│ expires_at                   │ TIMESTAMPTZ NOT NULL (issued +7d)    │
│ revoked_at                   │ TIMESTAMPTZ NULL                     │
│ created_at                   │ TIMESTAMPTZ NOT NULL                 │
│ last_used_at                 │ TIMESTAMPTZ NULL                     │
└──────────────────────────────┴──────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ audit_log_auth                                                      │
├──────────────────────────────┬──────────────────────────────────────┤
│ id                           │ BIGINT PK AUTO_INCREMENT             │
│ event_type                   │ ENUM(REGISTER, LOGIN_SUCCESS,        │
│                              │      LOGIN_FAILED, LOCKOUT,          │
│                              │      EMAIL_VERIFIED,                 │
│                              │      VERIFICATION_RESENT,            │
│                              │      TOKEN_REFRESHED, LOGOUT,        │
│                              │      SUSPICIOUS_TOKEN_REUSE,         │
│                              │      ADMIN_CREATED_STAFF,            │
│                              │      PASSWORD_CHANGED_FIRST_TIME)    │
│ user_id                      │ BIGINT FK NULL (NULL if user unknown)│
│ ip_address                   │ VARCHAR(45) NOT NULL (IPv4/IPv6)     │
│ user_agent                   │ TEXT NULL                            │
│ extra_info                   │ JSONB NULL                           │
│ created_at                   │ TIMESTAMPTZ NOT NULL                 │
└──────────────────────────────┴──────────────────────────────────────┘

Indexes:
  user(username) UNIQUE WHERE deleted_at IS NULL
  user(email)    UNIQUE WHERE deleted_at IS NULL
  user(phone)    UNIQUE WHERE deleted_at IS NULL
  email_verification(user_id)
  refresh_token(user_id, revoked_at) -- for panic-mode revocation
  refresh_token(token_hash)          -- for lookup on refresh call
  audit_log_auth(user_id, created_at DESC)
  audit_log_auth(event_type, created_at DESC)
```

---

## Error Handling Matrix

| Scenario | HTTP Code | `error_code` | Notes |
|----------|-----------|--------------|-------|
| Registration — validation error (field format) | 422 | `VALIDATION_ERROR` | `details[]` lists each field + message |
| Registration — username/email/phone already taken | 409 | `FIELD_TAKEN` | `field` names the conflicting field |
| Registration — rate limit exceeded (3/IP/1h) | 429 | `TOO_MANY_REQUESTS` | `retry_after_seconds` included |
| Verify token — not found or already used | 404 | `TOKEN_NOT_FOUND` | Generic: do not leak user existence |
| Verify token — expired (>24h) | 410 | `TOKEN_EXPIRED` | Prompt to resend |
| Resend verification — rate limit (1/email/60s) | 429 | `TOO_MANY_REQUESTS` | `retry_after_seconds` included |
| Login — user not found OR wrong password | 401 | `INVALID_CREDENTIALS` | Same message for both (anti-enumeration) |
| Login — account not email-verified | 403 | `EMAIL_NOT_VERIFIED` | `can_resend_verification: true` |
| Login — account locked | 423 | `ACCOUNT_LOCKED` | `locked_until` + `minutes_remaining` |
| Login — IP rate limit exceeded (5/IP/15min) | 429 | `TOO_MANY_REQUESTS` | `retry_after_seconds` |
| Refresh — token not found in DB | 401 | `INVALID_REFRESH_TOKEN` | |
| Refresh — token already revoked (reuse attempt) | 401 | `TOKEN_REUSE_DETECTED` | All user tokens revoked (panic mode) |
| Refresh — token expired | 401 | `REFRESH_TOKEN_EXPIRED` | |
| Any protected endpoint — no/bad Authorization | 401 | `UNAUTHORIZED` | |
| Any protected endpoint — access token expired | 401 | `TOKEN_EXPIRED` | |
| Any protected endpoint — insufficient role | 403 | `FORBIDDEN` | |
| First-time login (must_change_password=true) — full endpoint accessed with short-lived token | 403 | `MUST_CHANGE_PASSWORD` | Short-lived token only allows `/api/auth/change-password-first-time` |
| Change password first time — new password same as temp | 422 | `VALIDATION_ERROR` | `field: new_password` |
| Change password first time — short-lived token expired (>5min) | 401 | `TOKEN_EXPIRED` | Staff must log in again to get a new short-lived token |

---

## Non-Functional Requirements

- **NFR-01**: Password hashing (BCrypt cost 12) MUST complete in under 500ms on the production
  server. If the server cannot meet this within cost 12, cost factor is adjustable but must be
  documented as a deviation from this spec.

- **NFR-02**: The `/api/auth/login` endpoint p95 response time MUST be under 300ms (excluding
  BCrypt time, which is measured separately).

- **NFR-03**: Audit log writes MUST be asynchronous and MUST NOT add latency to any
  user-facing response. A 200ms spike in audit log INSERT latency must be invisible to the
  caller.

- **NFR-04**: All timestamps stored and returned MUST be in UTC internally and convertible to
  `Asia/Ho_Chi_Minh` for display (AC-07). All `TIMESTAMPTZ` columns enforce this automatically
  at the DB level.

- **NFR-05**: The email verification and credential emails MUST be dispatched asynchronously
  per HR-11. Email failure MUST NOT roll back the registration transaction.

- **NFR-06**: Rate limiting state for IP-based limits MAY be stored in-memory
  (`ConcurrentHashMap`). A server restart resets counters — this is an acceptable trade-off
  for the 6-week project scope.

- **NFR-07**: The refresh token MUST NEVER appear in the JSON response body, server logs, or
  any endpoint response other than as an httpOnly cookie set via `Set-Cookie` header.
  The access token MAY appear in the JSON response body (localStorage on client). This
  separation ensures that XSS attacks can steal the access token (15-min window) but CANNOT
  steal the long-lived refresh token.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: A new customer can complete the full registration + email verification + first
  login flow in under 3 minutes without any assistance.

- **SC-002**: 100% of protected endpoints return HTTP 403 (not 200, 404, or 500) when accessed
  with a token that lacks the required role.

- **SC-003**: A brute-force attack attempting 100 password guesses on the same account within
  5 minutes is blocked at attempt 5 with no more than 1 successful bcrypt comparison per
  attempt (no bypass possible via race condition).

- **SC-004**: Token reuse (using a rotated-away refresh token) triggers a `SUSPICIOUS_TOKEN_REUSE`
  audit event and invalidates all active sessions for that user within the same request cycle.

- **SC-005**: 100% of auth events (all 9 event types in FR-029) produce an audit log record
  that can be queried by Admin, with no gaps even under concurrent load.

- **SC-006**: A Staff account created by Admin (via Feature #15 mechanism) can log in with
  email + password immediately, without going through email verification (Staff accounts start
  as `ACTIVE`, not `PENDING_VERIFY`).

---

## Assumptions

- Staff accounts created by Admin bypass the email verification flow and start with
  `status = ACTIVE`. The Admin is responsible for communicating credentials to the Staff member.
- The `refresh_token` stored in the DB is the SHA-256 hash of the actual token sent to the
  client. The client always holds the raw token; the server only stores the hash. This prevents
  DB compromise from immediately yielding usable tokens.
- The `username` uniqueness check is case-insensitive at the application level (e.g., `Alice`
  and `alice` are treated as the same username) even though the stored value preserves original
  casing.
- Phone normalization assumes Vietnamese phone numbers only (10-digit starting with 0, or
  +84/84 prefix). International formats are rejected with HTTP 422.
- The single Manager account is created by Admin during system setup (not covered here).
- Rate limiting uses in-memory counters. On server restart, all counters reset. This is
  acceptable for the 6-week demo scope (A9 equivalent for rate limits).
- `failed_login_count` counter resets 30 minutes after the last failed attempt even without
  a successful login, to avoid permanent lockout from incidental errors.

---

## Acceptance Criteria

**Happy Path:**

1. **Given** a visitor fills all 8 required fields with valid data, **When** they submit
   registration, **Then** HTTP 201 is returned, account exists with `status=PENDING_VERIFY`,
   and an email is queued for delivery.

2. **Given** a `PENDING_VERIFY` customer clicks a valid 12-hour-old verification link,
   **When** the GET request is processed, **Then** `status` becomes `ACTIVE` and the token
   record is deleted.

3. **Given** an ACTIVE Customer with correct username/password, **When** they login, **Then**
   HTTP 200 with `access_token` (JWT, exp 900s) and `refresh_token` (opaque, exp 7d) is returned.

4. **Given** a valid refresh token, **When** `/api/auth/refresh` is called, **Then** a new
   token pair is returned and the old refresh token is marked revoked.

5. **Given** a logged-in user sends their refresh token to `/api/auth/logout`, **When** the
   request is processed, **Then** HTTP 204 and the token is marked revoked.

**Error & Edge Cases:**

6. **Given** a Customer access token, **When** `POST /api/trips/{id}/assign` (MANAGER role
   required) is called, **Then** HTTP 403 with `error_code: FORBIDDEN` is returned.

7. **Given** an access token older than 15 minutes, **When** any protected endpoint is called,
   **Then** HTTP 401 with `error_code: TOKEN_EXPIRED` is returned.

8. **Given** a username that already exists, **When** a new registration is submitted, **Then**
   HTTP 409 with `error_code: FIELD_TAKEN, field: username` is returned and no account is created.

9. **Given** a `date_of_birth` that makes the user 15 years old, **When** registration is
   submitted, **Then** HTTP 422 listing `date_of_birth` validation failure is returned.

10. **Given** wrong password submitted 4 times, **When** the 5th wrong attempt is made, **Then**
    `locked_until` is set to NOW()+15min, HTTP 423 is returned, `LOCKOUT` audit event is logged.

11. **Given** account locked, **When** correct password is submitted before lock expires, **Then**
    HTTP 423 is returned (lock is not bypassed by correct credentials).

12. **Given** an IP that sent 5 login attempts in 14 minutes, **When** the 6th attempt is made,
    **Then** HTTP 429 is returned before credentials are checked.

13. **Given** a rotated (used-once) refresh token is presented again, **When** `/api/auth/refresh`
    is called, **Then** ALL tokens for that user are revoked, HTTP 401 `TOKEN_REUSE_DETECTED`
    returned, and `SUSPICIOUS_TOKEN_REUSE` logged.

14. **Given** a verification token older than 24 hours, **When** the link is clicked, **Then**
    HTTP 410 is returned and the account stays `PENDING_VERIFY`.

15. **Given** resend verification called twice within 60 seconds for the same email, **When**
    the second call is made, **Then** HTTP 429 with `retry_after_seconds` is returned and no
    new email is sent.

16. **Given** Admin just created Staff X with temporary password "Temp@123", **When** X logs in
    with the temporary password, **Then** the system returns HTTP 200 with a 5-minute access
    token and `must_change_password: true` in the body — no refresh cookie is set.

17. **Given** Staff X has `must_change_password = true` and holds the 5-minute short-lived
    token, **When** X calls `GET /api/orders` (a normal protected endpoint), **Then** the
    system returns HTTP 403 with `error_code: MUST_CHANGE_PASSWORD`.

---

## Constitution Compliance Mapping

| Constitution Rule | How This Spec Ensures Compliance |
|------------------|----------------------------------|
| **HR-01** — Secrets not in git | Spec mandates env vars for `JWT_SECRET`, `GMAIL_APP_PASSWORD`. No credentials appear in spec or code. Covered in NFR assumptions. |
| **HR-02** — BCrypt password hashing | FR-007 explicitly mandates BCrypt cost 12. FR-016/FR-019 require hash comparison, never plaintext. NFR-01 sets performance constraint. |
| **HR-10** — RBAC → HTTP 403 | FR-026, FR-027, FR-028 define RBAC enforcement. Error Handling Matrix maps every access violation to HTTP 403. AC-07 in User Story 6. |
| **HR-12** — Staff cannot self-register | FR-001 specifies `POST /api/auth/register` creates CUSTOMER only. Out of Scope item #3 explicitly defers Staff creation to Feature #15. |
| **HR-13** — Audit log all state changes | FR-029, FR-030 mandate all 9 auth event types are logged with actor, timestamp, IP, user-agent. |
| **HR-16** — Rate limit + lockout | FR-008 (register rate limit), FR-014 (resend rate limit), FR-020 (lockout trigger), FR-021 (IP rate limit login), SC-003 (brute force criterion). |
| **AC-03** — JWT 15min/7d/rotation | FR-019 (token issuance), FR-022 (rotation), FR-023 (panic revoke), FR-025 (logout). D-AUTH-6 in business decisions. |
| **AC-07** — Timezone UTC storage | Data Model uses `TIMESTAMPTZ` for all time columns. NFR-04 explicit. All JWT `exp`/`iat` in epoch UTC. |
| **AC-09** — Soft delete | `user` table has `deleted_at TIMESTAMPTZ NULL`. Data Model shows partial index `WHERE deleted_at IS NULL` on unique constraints. `audit_log_auth` is never soft-deleted. |
| **AC-11** — CORS whitelist | CORS configuration is a cross-cutting concern enforced by Spring Security config. Spec calls out that all auth endpoints are under `/api/auth/` path. Codex must apply AC-11 when generating the security config. |
| **AC-12** — Flyway migration | Data Model section defines 4 tables to be created via Flyway `V1__` migrations. `ddl-auto=validate` applies. |
| **OWASP A07** — Identification & Authentication Failures | Mitigated by: httpOnly cookie for refresh token (XSS cannot steal it), rotation invalidates old tokens, panic-mode revocation on token reuse, account lockout after 5 failures, rate limit per IP. FR-019, FR-022, FR-023, FR-036, NFR-07. |

---

## Open Questions

1. **Forgot Password for Customer** (Q13-partial): DEFERRED — confirmed by leader. Customer
   reset via email link will be specced separately. Timeline: after Feature #9 (Cancel Order).
   Not a blocker for any CORE feature in this sprint.

2. **Forgot Password for Staff** (Q13-full): DEFERRED — confirmed, blocked by OQ-3 decision
   (now resolved: forced password change is mandatory). Staff forgot-password flow will be
   designed after Feature #15 (Staff Account Management). Options remain open: Admin resets
   via panel vs. Staff has dedicated reset page.

3. **Forced password change on first Staff login** (Q14): RESOLVED — YES, mandatory.
   Implemented via `must_change_password` flag + short-lived 5-minute token. See FR-031..035,
   AC-16, AC-17, and updated Data Model (`user.must_change_password` column).

4. **Rate limit persistence**: CONFIRMED — keep in-memory for 6-week sprint. A server restart
   resets all counters — acceptable trade-off. Backlog phase 2: upgrade to Bucket4j + Redis
   if production deployment requires persistence.

5. **Refresh token storage in client**: RESOLVED — hybrid storage adopted.
   - Access token: `localStorage` (15-min window, acceptable XSS risk for short-lived token).
   - Refresh token: httpOnly cookie (7 days, XSS-protected — JS cannot read it).
   Frontend cannot access refresh token via JavaScript. Browser sends cookie automatically
   when calling `/api/auth/refresh` and `/api/auth/logout`. See FR-019, FR-022, FR-025,
   FR-036, NFR-07, and Frontend Implementation Note section.

---

## Frontend Implementation Note (for Vanilla JS team)

> This section is non-normative guidance for the frontend developers. Backend spec is
> authoritative; this note summarizes implications for Vanilla JS implementation.

**Access token:**
- Store in `localStorage` as key `move_home_access_token`.
- Send on every API call via `Authorization: Bearer <token>` header.
- On receiving HTTP 401 `TOKEN_EXPIRED`, attempt one silent refresh before redirecting
  to login.

**Refresh token:**
- **KHÔNG TRUY CẬP được từ JavaScript.** The browser manages the httpOnly cookie entirely.
- Browser automatically sends the `refresh_token` cookie when calling `/api/auth/refresh`
  or `/api/auth/logout` (same-origin or correct CORS origin — see AC-11).
- Frontend does NOT need to read, store, or send the refresh token manually.

**Login flow:**
1. POST `/api/auth/login` with `{ identifier, password }`.
2. Read `access_token` from the JSON response body → save to `localStorage`.
3. The browser automatically stores the `refresh_token` httpOnly cookie from `Set-Cookie`.
4. If response includes `must_change_password: true` → redirect to change-password page.

**Token refresh flow:**
1. POST `/api/auth/refresh` — no body or Authorization header needed.
2. Browser automatically sends the `refresh_token` cookie.
3. Read the new `access_token` from the JSON response body → update `localStorage`.

**Logout flow:**
1. POST `/api/auth/logout` with `Authorization: Bearer <access_token>` header.
2. Browser automatically sends the `refresh_token` cookie.
3. Server clears the cookie via `Set-Cookie: refresh_token=; Max-Age=0`.
4. Frontend clears `move_home_access_token` from `localStorage`.
5. Redirect to login page.
