# Tasks: Auth, RBAC & Guest Mode — Spec #001

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ deferred

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Migration app_user / email_verification_token / refresh_token | V1/V2/V3 | Data Model | ✅ |
| T-02 | Customer register + login (username) | `AuthController`/`AuthService` | Scope 1–2 | ✅ |
| T-03 | Driver register Step 1 + login (email, sau ACTIVE) | `AuthService` | Scope 3–4 | ✅ |
| T-04 | Staff login + force-change-password lần đầu (HR-12) | `AuthService` | Scope 5,13 | ✅ |
| T-05 | Email verification (token 24h, resend rate-limit) | `AuthService` | Scope 6 | ✅ |
| T-06 | JWT access 15p + refresh 7d + rotation + reuse detection (AC-03) | `JwtTokenProvider`/`RefreshTokenRepository` | Scope 7,14 | ✅ |
| T-07 | Logout server-side (revoke + clear cookie) | `AuthService` | Scope 8 | ✅ |
| T-08 | RBAC filter chain + default deny + 403 (HR-10, HR-17) | `SecurityConfig` | Scope 9 | ✅ |
| T-09 | Account lockout 5 sai/15p + rate limit login/register/resend (HR-16) | `AuthService` | Scope 10–11 | ✅ |
| T-10 | Audit event auth (HR-13) | `AuditService` | Scope 12 | ✅ |
| T-11 | Forgot/Reset Password | `PasswordResetService` (V19) | Out-of-scope #1 | ✅ (đã build sau) |
| T-12 | 2FA / social login / OAuth2 | — | Out-of-scope | ⏳ |

**Done:** T-01..T-11 ✅ (nền tảng CORE). T-11 ban đầu out-of-scope nhưng thực tế đã build (V19 + reset page).
