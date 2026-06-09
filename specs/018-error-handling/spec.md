# Feature Specification: Error Handling & Recovery

**Feature Branch:** `018-error-handling`
**Feature Number:** #18 of 18 — FINAL (system reliability + UX)
**Created:** 2026-06-04
**Version:** 1.0.0
**Status:** Draft
**Sprint Target:** Sprint 6

**CONTEXT.md reference:** v2.0 §26 Error handling strategy
**Constitution reference:** v1.3.0 — HR-01 (secret redaction), HR-10 (RBAC
403), HR-13 (audit critical events), HR-19 (brand consistent error pages),
HR-20 (Vietnamese error messages), ES-04 (error format thống nhất)

---

## Goals

Move_home cần một chiến lược xử lý lỗi đồng nhất để người dùng không bị mắc kẹt
với thông báo kỹ thuật, trang trắng hoặc hành động không rõ ràng. Bốn error page
dedicated xử lý URL không tồn tại, truy cập trái quyền, lỗi máy chủ và phiên
đăng nhập hết hạn. Mỗi trang phải giữ brand Move_home, tiếng Việt đầy đủ dấu,
hiển thị mã hỗ trợ an toàn và luôn cung cấp hành động phục hồi phù hợp.

Frontend cần một fetch wrapper dùng chung để chuẩn hóa parse response, refresh
access token đúng một lần, hiển thị validation inline, xử lý conflict tại chỗ,
retry transient failures an toàn và graceful degradation theo từng section.
Backend cần `@RestControllerAdvice` chuẩn hóa mọi REST error theo ES-04, gắn
request ID, không lộ stack trace, secret hoặc dữ liệu cá nhân.

Logging phải structured, có correlation context và redaction để developer truy
vết lỗi nhanh. Security events như permission denied, token reuse và rate limit
được audit; lỗi vận hành được log/metric hóa mà không spam audit nghiệp vụ.
Retry chỉ áp dụng cho request an toàn hoặc mutation có idempotency key, tránh
tạo order, payment hoặc money transaction trùng.

Mục tiêu UX là mỗi lỗi đều có thông báo dễ hiểu và action `Thử lại`, `Đăng nhập
lại`, `Về trang chủ` hoặc `Liên hệ hỗ trợ`. Mục tiêu kỹ thuật là request ID có
thể nối client error, backend log và audit event mà không làm lộ thông tin nhạy
cảm.

---

## Source-of-Truth Resolution

| Chủ đề | Quyết định canonical | Hệ quả |
|---|---|---|
| Error body | ES-04 `{error_code,message,details}` | Không tạo `code/message_vi/message_en` song song |
| Authentication | Guest/invalid auth nhận `401` | Không đổi thành 403 |
| Authorization | Authenticated trái quyền nhận `403` | Tuân thủ HR-10 |
| Access refresh | Access token 15 phút; refresh HttpOnly cookie rotation | JS không đọc/xóa refresh token |
| Refresh reuse | Backend panic-revoke all theo Spec 001 | Redirect session expired với reason an toàn |
| API 404 | Trả structured JSON | Không mặc định redirect toàn trang |
| Page 404 | Static route fallback | Có `noindex` |
| API 500 | Section error/toast trước; full-page 500 khi page cannot function | Không redirect mọi lỗi API |
| Retry | Chỉ transient và safe/idempotent request | Không auto-retry unsafe mutation |
| Validation | HTTP 422 + `details[]` | Hiển thị inline, không redirect |
| Business conflict | HTTP 409 | Toast/modal và refresh data phù hợp |
| Error storage | Structured application logs là source chính | Không lưu raw payload/stack trace vào DB mặc định |
| Audit | Security/critical error events | Không audit mọi network/validation error |

---

## Scope Summary

**In scope:**

1. `404.html`, `403.html`, `500.html`, `session-expired.html`.
2. Global frontend `apiCall` wrapper.
3. Access-token refresh single-flight.
4. Safe retry với exponential backoff và jitter.
5. Validation, toast, section error và graceful degradation.
6. Spring `@RestControllerAdvice`.
7. ES-04 standardized error response.
8. Error-code catalog và Vietnamese messages.
9. Request/correlation ID.
10. Structured logging và sensitive-data redaction.
11. Security-event audit.
12. Error metrics và alerting contract.

**Out of scope:**

1. Sentry integration.
2. Error-monitoring Admin dashboard.
3. A/B testing error pages.
4. Multi-language error messages.
5. Predictive error prevention.
6. Automated email alerts implementation.
7. User-submitted custom error reports.
8. Persisting full stack traces or raw request payloads in database.

---

## User Stories

**P1:**

- **US1:** Là user mở URL không tồn tại, tôi thấy trang 404 thân thiện với
  hành động quay về.
- **US2:** Là user không đủ quyền, tôi thấy trang 403 giải thích rõ và link về
  home đúng vai trò.
- **US3:** Là user gặp lỗi máy chủ, tôi thấy mã hỗ trợ, `Thử lại` và
  `Liên hệ hỗ trợ`.
- **US4:** Là user có phiên hết hạn, hệ thống thử refresh an toàn trước khi yêu
  cầu đăng nhập lại.
- **US5:** Là user submit form sai, tôi thấy lỗi tiếng Việt ngay dưới field.
- **US6:** Là user mất mạng giữa thao tác, tôi thấy trạng thái retry hoặc hành
  động phục hồi rõ ràng.

**P2:**

- **US7:** Là Admin, tôi xem error logs aggregated → defer Sprint 6+.
- **US8:** Là user, tôi gửi custom error report → defer Sprint 6+.

---

## Functional Requirements

> EARS notation: WHEN | WHILE | WHERE | IF/THEN

### Nhóm 1 — 404 Not Found (FR-001..FR-005)

**FR-001 — Page-route 404**

WHEN a browser navigation targets a public/static route that does not exist,
THE deployment server SHALL return HTTP `404` and render `/404.html` without
changing the address to a false `200` route.

**FR-002 — API-resource 404**

WHEN a REST resource or API route is not found,
THE backend SHALL return HTTP `404` with ES-04 JSON:

```json
{
  "error_code": "RESOURCE_NOT_FOUND",
  "message": "Không tìm thấy dữ liệu yêu cầu.",
  "details": [],
  "request_id": "uuid",
  "timestamp": "2026-06-04T10:30:00Z"
}
```

The frontend SHALL normally render a local not-found state rather than
redirecting the whole application.

**FR-003 — 404 page content**

WHEN `/404.html` renders,
THE page SHALL show:

- Large forest-green `404`.
- Heading `Không tìm thấy trang`.
- Explanation that the page may not exist or has moved.
- Primary action `Về trang chủ`.
- Secondary action `Liên hệ hỗ trợ`.
- Optional safe previous-page action.

**FR-004 — Role-aware home**

WHEN an authenticated user selects `Về trang chủ`,
THE frontend SHALL navigate to the canonical home for the locally known role.

IF role is missing, invalid or untrusted,
THEN the action SHALL navigate to the public landing page.

**FR-005 — 404 audit and SEO**

WHEN page-route 404 occurs,
THE SYSTEM SHALL set `<meta name="robots" content="noindex,nofollow">` and MAY
audit `PAGE_404` with sanitized attempted path, referer origin and actor ID.

Audit SHALL throttle to at most five events per IP hash per minute and SHALL
not store query-string secrets.

---

### Nhóm 2 — 403 Forbidden (FR-006..FR-011)

**FR-006 — Backend forbidden response**

WHEN an authenticated actor attempts an operation without required permission,
THE backend SHALL return HTTP `403` with `error_code="PERMISSION_DENIED"` and a
Vietnamese message.

It SHALL not return `401` or hide authorization failure as `404`.

**FR-007 — 403 presentation choice**

WHEN a full-page protected route fails authorization,
THE frontend SHALL render or navigate to `/403.html`.

WHEN an API action inside an otherwise valid page returns `403`,
THE frontend SHALL show a local forbidden message and SHALL not always discard
the current page.

**FR-008 — 403 page content**

WHEN `/403.html` renders,
THE page SHALL show:

- Large forest-green `403`.
- Heading `Truy cập bị từ chối`.
- Explanation `Bạn không có quyền truy cập trang này`.
- Current role label only if safely available.
- Primary action `Về trang chủ của tôi`.
- Secondary action `Đăng xuất`.

**FR-009 — Role-aware forbidden recovery**

WHEN the user selects role home from 403,
THE frontend SHALL map canonical roles to Customer, Driver, Manager or Admin
home.

IF a Driver is onboarding rather than `ACTIVE`,
THEN the frontend SHALL follow the server-provided safe next-step route instead
of Driver home.

**FR-010 — Logout from forbidden page**

WHEN the user selects `Đăng xuất`,
THE frontend SHALL call the logout endpoint with credentials included, clear
the access token and nonessential local session state, then navigate to login.

It SHALL not rely on `localStorage.clear()` to revoke the HttpOnly refresh
token.

**FR-011 — Permission-denied audit**

WHEN a backend authorization check denies access,
THE SYSTEM SHALL audit `PERMISSION_DENIED` with actor ID, role, sanitized
endpoint, method, required authority and request ID.

The audit SHALL exclude request payload, JWT and personal data.

---

### Nhóm 3 — 500 Server Error (FR-012..FR-017)

**FR-012 — Unexpected exception handler**

WHEN an uncaught backend exception reaches the REST boundary,
THE global handler SHALL log it and return HTTP `500` with
`error_code="INTERNAL_ERROR"`, safe Vietnamese message, request ID and
timestamp.

**FR-013 — No sensitive leakage**

WHERE a 500 response is serialized,
THE backend SHALL NOT return stack trace, SQL, hostnames, environment values,
secret keys, token values, raw exception messages or request payload.

**FR-014 — Frontend 500 recovery**

WHEN a recoverable section API returns `500`,
THE frontend SHALL show a section-level error with `Thử lại` and request ID.

IF the page cannot render its primary purpose,
THEN the frontend MAY navigate to `/500.html?request_id={safe-id}`.

**FR-015 — 500 page content**

WHEN `/500.html` renders,
THE page SHALL show:

- Large forest-green or semantic-danger `500`.
- Heading `Lỗi máy chủ`.
- Safe explanation.
- Copyable request ID when present.
- Primary action `Thử lại`.
- Secondary action `Liên hệ hỗ trợ`.

**FR-016 — Safe retry navigation**

WHEN the user selects `Thử lại` on the 500 page,
THE frontend SHALL retry the safe previous navigation once or navigate to a
known safe home.

It SHALL not replay a previous POST/payment/money mutation automatically.

**FR-017 — Backend exception logging**

WHEN an unexpected exception occurs,
THE backend SHALL write one structured ERROR log with request ID, actor ID if
known, endpoint, method, status, duration, safe error type and full stack trace
in protected server logs.

---

### Nhóm 4 — Session Expired (FR-018..FR-023)

**FR-018 — Eligible 401 refresh**

WHEN an authenticated API request returns a refresh-eligible `401`,
THE frontend SHALL perform one single-flight
`POST /api/auth/refresh` with `credentials: include`.

Login invalid-credential responses and explicit public-auth errors SHALL not
trigger refresh.

**FR-019 — Refresh success**

WHEN refresh succeeds,
THE frontend SHALL store the new access token and replay each waiting request
at most once.

Unsafe mutation SHALL be replayed only if it carries a valid idempotency key.

**FR-020 — Refresh failure**

WHERE refresh returns `NO_REFRESH_TOKEN`, `TOKEN_REUSE_DETECTED`, expired token
or another terminal `401`,
THE frontend SHALL clear access-token/session display state and navigate once
to `/session-expired.html`.

Concurrent failing requests SHALL not create redirect or refresh loops.

**FR-021 — Session-expired page**

WHEN `/session-expired.html` renders,
THE page SHALL show a clock illustration, heading
`Phiên đăng nhập hết hạn`, security explanation, primary action
`Đăng nhập lại` and secondary action `Về trang chủ`.

**FR-022 — Session cleanup**

WHEN session expiry is confirmed,
THE frontend SHALL remove access token and user-specific cached data.

The backend SHALL revoke refresh tokens according to Spec 001 where applicable,
especially token-reuse detection.

**FR-023 — Session security audit**

WHEN refresh reuse or another security-significant session termination occurs,
THE backend SHALL audit the canonical security event with user ID if known,
request ID and safe reason.

Ordinary access-token expiry SHALL not create excessive audit spam.

---

### Nhóm 5 — Frontend Error Handler (FR-024..FR-031)

**FR-024 — Shared API wrapper**

WHEN frontend code calls an API,
THE application SHALL use a shared wrapper that adds authorization, credentials,
request ID, timeout/cancellation and standardized response parsing.

**FR-025 — ES-04 parsing**

WHEN a non-success JSON response is received,
THE wrapper SHALL parse `error_code`, `message`, `details`, `request_id` and
`timestamp`.

WHERE JSON is malformed or content type is unexpected,
THE wrapper SHALL create a safe `MALFORMED_ERROR_RESPONSE` client error without
displaying raw response content.

**FR-026 — Safe retry policy**

WHEN a network error, timeout, HTTP `502`, `503` or `504` occurs,
THE wrapper MAY retry up to three total attempts using exponential backoff with
jitter.

Automatic retry SHALL be allowed only for:

- `GET`, `HEAD` and `OPTIONS`.
- Idempotent mutation with explicit idempotency key.

**FR-027 — Retry timing and cancellation**

WHILE retrying,
THE frontend SHALL show `Mất kết nối, đang thử lại...`, honor
`Retry-After`, support `AbortController` and use approximately `2s`, `4s`,
`8s` maximum delays with jitter.

Navigation away SHALL cancel remaining retries.

**FR-028 — Validation errors**

WHEN HTTP `422 VALIDATION_ERROR` is received,
THE frontend SHALL map `details[]` to inline field errors, set accessible
invalid state, focus the first invalid field and preserve user input.

It SHALL not redirect.

**FR-029 — Conflict and rate-limit errors**

WHEN HTTP `409` is received,
THE frontend SHALL show the server message and offer reload/recovery appropriate
to the business conflict.

WHEN HTTP `429` is received,
THE frontend SHALL show retry-after information and disable repeated action
until allowed.

**FR-030 — Toast behavior**

WHEN a recoverable global message is needed,
THE frontend SHALL show an accessible top-right toast with type, Vietnamese
message and optional action.

Toasts SHALL auto-dismiss after about five seconds, pause on hover/focus and
show at most three simultaneously.

**FR-031 — Graceful degradation**

WHERE a noncritical section fails,
THE frontend SHALL preserve successful sections and show a local error state.

Lists and data-driven sections SHALL retain mandatory Loading, Empty and Error
states under AC-16.

---

### Nhóm 6 — Backend Error Response Format (FR-032..FR-037)

**FR-032 — Standard error envelope**

WHEN any REST error is returned,
THE backend SHALL use:

```json
{
  "error_code": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ.",
  "details": [
    {"field": "email", "message": "Email không hợp lệ."}
  ],
  "request_id": "abc-123-def",
  "timestamp": "2026-06-04T10:30:00Z"
}
```

`message_en` and alternate top-level `code` SHALL not be introduced.

**FR-033 — Validation handler**

WHEN `MethodArgumentNotValidException`, constraint violation or supported
binding error occurs,
THE global advice SHALL return HTTP `422 VALIDATION_ERROR` with all safe field
errors in deterministic order.

**FR-034 — Auth and authorization handlers**

WHEN authentication is missing/invalid,
THE security handler SHALL return HTTP `401` with canonical auth error code.

WHEN authenticated access is denied,
THE security handler SHALL return HTTP `403 PERMISSION_DENIED`.

Both SHALL use the same ES-04 envelope.

**FR-035 — Domain handlers**

WHEN `ResourceNotFoundException`, duplicate/conflict exception, invalid state
transition or rate-limit exception occurs,
THE global advice SHALL map respectively to canonical `404`, `409` or `429`
codes without converting expected business errors into `500`.

**FR-036 — Request ID propagation**

WHEN any HTTP request arrives,
THE SYSTEM SHALL accept a valid safe request ID header or generate a UUID,
place it in logging context, return it in response header and include it in
error bodies.

Invalid/untrusted request ID values SHALL be replaced.

**FR-037 — Advice boundaries**

WHERE an exception is handled,
THE global advice SHALL preserve domain-specific safe `error_code`, message and
details where defined.

The catch-all handler SHALL be the final fallback and SHALL not swallow
security, validation or business mappings.

---

### Nhóm 7 — Logging + Monitoring (FR-038..FR-042)

**FR-038 — Structured logging**

WHEN backend requests complete or fail,
THE SYSTEM SHALL emit structured JSON logs with timestamp, level, service,
request ID, actor ID/role if known, endpoint template, method, status, duration
and safe error type/code.

**FR-039 — Log levels**

WHEN selecting a log level,
THE SYSTEM SHALL use DEBUG for development diagnostics, INFO for normal
milestones, WARN for recoverable/security-relevant conditions and ERROR for
unexpected failures requiring investigation.

Production SHALL not enable TRACE/DEBUG globally.

**FR-040 — Sensitive-data redaction**

WHERE logs, audit events or metrics are emitted,
THE SYSTEM SHALL redact passwords, JWTs, refresh cookies, HMAC/signatures,
authorization headers, bank accounts, document numbers and sensitive payload
fields.

Redaction SHALL occur before serialization.

**FR-041 — Audit versus operational logs**

WHEN permission denial, token reuse, suspicious rate limiting or critical
financial/state failure occurs,
THE SYSTEM SHALL write the appropriate immutable audit event.

Routine validation, user cancellation and transient network errors SHALL use
operational logs/metrics and SHALL not spam business audit.

**FR-042 — Monitoring and retention**

WHEN errors are observed in production,
THE SYSTEM SHALL expose metrics for HTTP status, error code, endpoint,
latency, retry and refresh outcome; protected logs SHALL rotate and follow the
approved retention policy.

Sentry and automated Admin email alerts SHALL remain deferred.

---

## Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-001 | Error pages render under `1s` |
| NFR-002 | Retry maximum three total attempts with backoff/jitter |
| NFR-003 | Vietnamese user-facing messages mandatory |
| NFR-004 | Sensitive data never leaks in response/log/audit |
| NFR-005 | Every response carries a traceable request ID |
| NFR-006 | Security-significant errors are audited |
| NFR-007 | Error pages use `noindex,nofollow` |
| NFR-008 | Error pages follow Move_home brand |
| NFR-009 | Global handler overhead below `20ms` excluding logging I/O |
| NFR-010 | Error UI keyboard/screen-reader accessible |
| NFR-011 | Refresh and retry logic cannot loop indefinitely |
| NFR-012 | Logs remain available according to approved retention policy |

---

## API Endpoints Summary

No new business endpoint is introduced.

Existing endpoints SHALL adopt the common error envelope, request ID and
handler strategy.

Authentication refresh/logout endpoints remain owned by Spec 001:

| Method | Existing endpoint | Error-handling use |
|---|---|---|
| POST | `/api/auth/refresh` | Single-flight access-token recovery |
| POST | `/api/auth/logout` | Revoke refresh session before exit |

---

## Data Model

No `error_log` database table is required by default.

Structured protected application logs are the authoritative source for stack
traces and operational failures. Audit tables remain authoritative for
security/business audit events.

An optional future error-event projection MAY store only redacted metadata:

```sql
CREATE TABLE error_event (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id UUID NOT NULL,
  actor_id UUID REFERENCES app_user(id),
  event_type VARCHAR(50) NOT NULL,
  endpoint_template VARCHAR(200),
  method VARCHAR(10),
  status_code INT NOT NULL,
  error_code VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_error_event_created
  ON error_event(created_at DESC);
```

This optional projection SHALL NOT store stack traces, raw payload, JWT,
cookie, raw IP or user-agent strings.

---

## State Machine

Error handling is a cross-cutting concern and creates no business state machine.

Session recovery has this control flow:

```text
API request
  ├─ success → render
  └─ eligible 401 → one single-flight refresh
       ├─ refresh success → replay once
       └─ refresh terminal failure → session-expired page
```

Retry control flow:

```text
safe/idempotent request transient failure
  → retry attempt 2
  → retry attempt 3
  → local error/recovery action
```

Business state transitions remain owned by their domain specs.

---

## Error Code Catalog

| Error code | HTTP | Vietnamese message/default handling |
|---|---:|---|
| `VALIDATION_ERROR` | 422 | Dữ liệu không hợp lệ; inline fields |
| `AUTHENTICATION_REQUIRED` | 401 | Vui lòng đăng nhập để tiếp tục |
| `INVALID_CREDENTIALS` | 401 | Email hoặc mật khẩu không đúng |
| `NO_REFRESH_TOKEN` | 401 | Phiên đăng nhập đã hết hạn |
| `TOKEN_REUSE_DETECTED` | 401 | Phiên bị kết thúc vì hoạt động bất thường |
| `PERMISSION_DENIED` | 403 | Bạn không có quyền truy cập |
| `EMAIL_NOT_VERIFIED` | 403 | Vui lòng xác thực email |
| `ONBOARDING_INCOMPLETE` | 403 | Vui lòng hoàn tất đăng ký tài xế |
| `RESOURCE_NOT_FOUND` | 404 | Không tìm thấy dữ liệu yêu cầu |
| `DUPLICATE_RESOURCE` | 409 | Dữ liệu đã tồn tại |
| `INVALID_STATUS_TRANSITION` | 409 | Trạng thái không hợp lệ |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Yêu cầu lặp không hợp lệ |
| `RATE_LIMITED` | 429 | Quá nhiều yêu cầu, vui lòng thử lại sau |
| `INTERNAL_ERROR` | 500 | Đã có lỗi xảy ra, vui lòng thử lại |
| `SERVICE_UNAVAILABLE` | 503 | Dịch vụ tạm thời không khả dụng |
| `REPORT_QUERY_TIMEOUT` | 503 | Không thể tải báo cáo lúc này |

Domain specs MAY add stable codes while preserving the common envelope.

---

## Frontend Recovery Matrix

| Condition | Default UI behavior | Automatic retry |
|---|---|---|
| Page route 404 | Full 404 page | No |
| API resource 404 | Local not-found state | No |
| Full-route 403 | Full 403 page | No |
| Action 403 | Local message/toast | No |
| Eligible 401 | Refresh single-flight, replay once | Once |
| Terminal refresh 401 | Session-expired page | No |
| 409 | Conflict message + reload action | No |
| 422 | Inline field errors | No |
| 429 | Retry-after message/disable action | Only explicit/user |
| 500 | Section error or full 500 page | No unsafe replay |
| 502/503/504/network | Local retry state | Safe/idempotent only |

---

## Exception Mapping Matrix

Backend SHALL map known exception families deterministically so every API
consumer receives the same HTTP status and canonical `error_code`.

| Source exception or condition | HTTP | Canonical error code | Frontend behavior |
|---|---:|---|---|
| `MethodArgumentNotValidException` | 422 | `VALIDATION_ERROR` | Show inline field errors |
| `ConstraintViolationException` | 422 | `VALIDATION_ERROR` | Show query/filter validation |
| Missing or invalid authentication | 401 | `AUTHENTICATION_REQUIRED` | Start eligible authentication recovery |
| Invalid login credentials | 401 | `INVALID_CREDENTIALS` | Keep form and show inline error |
| Refresh cookie missing or expired | 401 | `NO_REFRESH_TOKEN` | Navigate to session-expired page |
| Refresh token reuse detected | 401 | `TOKEN_REUSE_DETECTED` | End session and navigate to session-expired page |
| `AccessDeniedException` | 403 | `PERMISSION_DENIED` | Show local denial or full 403 page |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` | Show local not-found state or 404 page |
| Duplicate resource exception | 409 | `DUPLICATE_RESOURCE` | Preserve form and show conflict |
| Invalid domain transition | 409 | `INVALID_STATUS_TRANSITION` | Prompt entity reload |
| Idempotency-key conflict | 409 | `IDEMPOTENCY_KEY_REUSED` | Do not replay automatically |
| Rate-limit exception | 429 | `RATE_LIMITED` | Respect `Retry-After` |
| Timeout or temporary upstream outage | 503 | `SERVICE_UNAVAILABLE` | Retry safe requests only |
| Unexpected `Exception` | 500 | `INTERNAL_ERROR` | Show fallback with request ID |

Specific handlers SHALL be evaluated before the catch-all handler. Backend
SHALL NOT convert authentication failures to 403, permission failures to 401,
or any failure to HTTP 200 with an error payload.

---

## Request ID, Correlation, and Redaction Contract

1. WHEN a request enters the system, THE system SHALL accept `X-Request-ID`
   only when it is a valid UUID; otherwise it SHALL generate a UUID v4.
2. WHEN a response is returned, THE system SHALL include the same request ID
   in the `X-Request-ID` header and any error envelope.
3. WHEN backend logs a request, THE system SHALL add request ID, safe user ID,
   role, method, normalized endpoint and status to logging context.
4. WHEN a request completes, THE system SHALL clear thread-local logging
   context so correlation data cannot leak into another request.
5. WHEN an async job, email or outbox event originates from a request, THE
   system SHALL propagate a correlation ID across that boundary.
6. WHEN frontend records a client error, THE system SHALL include the request
   ID but SHALL NOT record access tokens, cookies, secrets or full form data.
7. WHEN request data is logged, THE system SHALL use an allowlist of safe
   fields instead of storing the complete payload by default.
8. WHEN field names match password, token, authorization, cookie, secret, OTP,
   bank account or sensitive identity data, THE system SHALL redact values.
9. WHEN sensitive fields exist inside nested objects or arrays, THE system
   SHALL apply redaction recursively before serialization.
10. WHERE production metrics are emitted, THE system SHALL NOT use request ID,
    raw URL or user ID as metric labels because they create high cardinality.
11. IF a client supplies a duplicated or abusive request ID, THEN backend SHALL
    still isolate the requests and SHALL NOT treat request ID as a credential.
12. IF support investigates an incident, THEN request ID SHALL be the primary
    lookup key; support SHALL NOT request passwords or tokens from users.

---

## Error Page Routing and Deployment Contract

1. WHERE static hosting or Nginx serves page routes, THE system SHALL map an
   unknown page route to `404.html` while preserving HTTP status 404.
2. WHERE a request path begins with `/api/`, THE system SHALL always return the
   ES-04 JSON envelope and SHALL NOT return an HTML error page.
3. WHERE Spring Boot cannot find an API handler, THE system SHALL return
   `RESOURCE_NOT_FOUND` with HTTP 404.
4. WHEN a 500 occurs during page navigation, frontend MAY navigate to
   `500.html`; WHEN a section can recover locally, it SHALL prefer local UI.
5. WHEN `500.html` loads, THE page SHALL render its message, request ID and
   recovery actions without depending on the failing API.
6. WHEN an error page receives query parameters, THE page SHALL read only an
   allowlist such as `request_id` and normalized reason code, escaped on render.
7. WHEN a user selects “Thử lại”, THE system SHALL return to the previous URL
   only if it is same-origin; otherwise it SHALL use the role-based home.
8. WHEN a user selects “Về trang chủ của tôi”, THE system SHALL use the known
   authenticated role or fall back to the public landing page.
9. WHEN CSP, font or illustration assets fail, THE error page SHALL still show
   readable HTML and working recovery actions.
10. WHERE a CDN or reverse proxy caches error pages, THE system SHALL NOT cache
    responses containing user-specific data or another request's request ID.
11. WHEN a crawler visits an error page, THE system SHALL provide
    `noindex, nofollow` so invalid URLs are not indexed.
12. WHEN a deployment health check calls a technical endpoint, THE system SHALL
    return its real status and SHALL NOT redirect it to a branded error page.

---

## Acceptance Criteria

**AC-01 — Error pages**

GIVEN page-route 404, forbidden route, fatal page error and terminal session
expiry,
WHEN each occurs,
THEN the correct branded page renders with Vietnamese recovery actions.

**AC-02 — Access-token refresh**

GIVEN an expired access token and valid refresh cookie,
WHEN multiple API calls return eligible 401 concurrently,
THEN exactly one refresh occurs and waiting requests replay at most once.

**AC-03 — Global backend handling**

GIVEN each mapped exception and an uncaught exception,
WHEN it reaches the REST boundary,
THEN the correct HTTP/error code is returned and catch-all handles only the
unexpected case.

**AC-04 — ES-04 consistency**

GIVEN errors across all modules,
WHEN responses are inspected,
THEN they use `error_code`, `message`, `details`, `request_id`, `timestamp`
without alternate envelope fields.

**AC-05 — Vietnamese messages**

GIVEN common error conditions,
WHEN shown to users,
THEN messages are safe, actionable Vietnamese with complete diacritics.

**AC-06 — Retry safety**

GIVEN transient GET and POST failures,
WHEN wrapper retries,
THEN GET/idempotent POST may retry within limits and unsafe POST never retries
automatically.

**AC-07 — Redaction**

GIVEN requests containing secrets and personal data,
WHEN errors are returned/logged/audited,
THEN sensitive values do not appear.

**AC-08 — Security audit**

GIVEN permission denial and token reuse,
WHEN events occur,
THEN immutable audit entries contain safe actor/event/request context.

**AC-09 — Request tracing**

GIVEN an unexpected error,
WHEN a user shares request ID,
THEN the ID correlates response, protected backend log and applicable audit.

**AC-10 — SEO and accessibility**

GIVEN four error pages,
WHEN inspected,
THEN they are `noindex,nofollow`, keyboard accessible and brand consistent.

---

## Edge Cases & Error Handling

| ID | Edge case | Required behavior |
|---|---|---|
| EC-01 | Browser offline mid-request | Safe retry then offline message |
| EC-02 | JWT expires during long form | Refresh; replay only with idempotency |
| EC-03 | Multiple concurrent 401s | One refresh single-flight |
| EC-04 | Refresh itself returns 401 | No recursive refresh; session expired |
| EC-05 | Token reuse detected | Revoke-all backend; one safe redirect |
| EC-06 | Network timeout over configured limit | Abort and safe retry policy |
| EC-07 | Server returns malformed JSON | Safe client error, no raw body display |
| EC-08 | HTTP 503 with Retry-After | Honor header for safe retry |
| EC-09 | CORS/browser-blocked request | Generic network guidance, log safe context |
| EC-10 | localStorage unavailable | In-memory fallback then login recovery |
| EC-11 | Three toasts already visible | Queue/coalesce without hiding critical error |
| EC-12 | Validation field not present in DOM | Show form summary safely |
| EC-13 | Request ID header malicious/too long | Replace with generated UUID |
| EC-14 | 404 URL contains token/query secret | Do not audit raw query |
| EC-15 | Logging sink unavailable | Request behavior remains safe; metric/console fallback |

---

## Test Cases

| ID | Test | Expected result |
|---|---|---|
| TC-01 | Navigate unknown route and simulate asset failure | HTTP 404 branded noindex page remains accessible |
| TC-02 | Non-Admin opens Admin route/action | 403 page/local error and audit |
| TC-03 | Throw unexpected backend exception | Safe 500 envelope; protected correlated stack log |
| TC-04 | Concurrent expired-token requests | One refresh; each replay once |
| TC-05 | Refresh token reuse | Revoke-all, terminal session page, audit |
| TC-06 | Validation exception with multiple fields | Deterministic 422 details and inline errors |
| TC-07 | Transient GET then recovery | Backoff retry succeeds within limit |
| TC-08 | Unsafe POST transient failure | No automatic retry/duplicate mutation |
| TC-09 | Secret-bearing failed request | No secret in response/log/audit |
| TC-10 | Malformed JSON/503/CORS/offline chaos | Graceful safe recovery states |

---

## Required Automated Test Layers

1. Unit tests for exception-to-code mappings and message catalog.
2. Unit tests for retry eligibility, backoff and cancellation.
3. Integration tests for `@RestControllerAdvice` and Spring Security handlers.
4. Auth integration tests for refresh rotation, concurrency and reuse.
5. Security tests for redaction and response leakage.
6. Frontend tests for pages, inline errors, toast limits and section failures.
7. Chaos tests for network timeout, malformed response and unavailable service.
8. Static/accessibility/SEO checks for four error pages.

---

## Security and Observability

1. Authorization header, cookies and secrets SHALL never enter logs.
2. Endpoint templates SHALL be logged instead of sensitive raw URLs.
3. Metrics labels SHALL avoid user ID, request ID and high-cardinality values.
4. Stack traces SHALL remain in protected backend logs only.
5. Client console logs SHALL not expose tokens or response bodies.
6. Permission and token-reuse spikes SHOULD trigger security alerts.
7. HTTP 500/503 rate and latency SHOULD trigger operational alerts.
8. Log retention/access SHALL follow the approved operations policy.

---

## Constitution Compliance

| Rule | Compliance |
|---|---|
| HR-01 | Secrets and tokens redacted |
| HR-05 | Invalid domain transitions remain HTTP 409 |
| HR-10 | Authenticated permission denial remains HTTP 403 |
| HR-13 | Security/critical events audited |
| HR-19 | Error pages follow Move_home brand |
| HR-20 | User-facing errors use Vietnamese diacritics |
| AC-03 | Refresh rotation/reuse follows Spec 001 |
| AC-16 | Loading/Empty/Error and graceful degradation |
| ES-03 | Validation maps to HTTP 422 |
| ES-04 | One standardized error envelope |

---

## Definition of Done

1. Four error pages satisfy content, brand, SEO and accessibility contracts.
2. Exactly 42 EARS FR and eight User Stories are covered.
3. Shared frontend wrapper handles refresh, retry and errors safely.
4. Global backend advice maps expected and unexpected exceptions correctly.
5. ES-04 envelope is consistent across modules.
6. Request ID correlation and structured logging pass tests.
7. Sensitive-data redaction tests pass.
8. Security-event audit tests pass.
9. Chaos/recovery tests pass without duplicate mutations.
10. Spec catalog 001-018 is complete.
