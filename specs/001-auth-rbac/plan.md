# Implementation Plan: Auth, RBAC & Guest Mode — Spec #001

> Plan tái dựng từ code as-built + [`spec.md`](spec.md) v2.0.0.
> **Migration:** V1, V2, V3 (+ V23 locked status, V27 suspension, V33 avatar). **Status:** As-built (CORE nền tảng).

## 1. Architectural Approach

Nền tảng xác thực/phân quyền cho toàn hệ thống: 4 vai trò (Customer/Driver/Manager/Admin) + Guest.
JWT access 15p (localStorage) + refresh 7d (httpOnly cookie) + **rotation + reuse detection** (panic
revoke-all, AC-03). RBAC qua Spring Security filter (default deny; `/api/public/**` + `/api/auth/**`
permitAll — HR-17). Chống brute force: rate limit (login 5/IP/15p) + account lockout 5 sai → 15p (HR-16).
Email verification token 24h. Staff force-change-password lần đầu (HR-12). Audit mọi event auth (HR-13).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AuthController` | register/login/verify/refresh/logout | `controller/AuthController.java` |
| `AuthService` | Logic đăng ký, đăng nhập, lockout, token rotation | `service/AuthService.java` |
| `JwtTokenProvider` | Sinh/verify JWT (HS256) | `security/JwtTokenProvider.java` |
| `SecurityConfig` | Filter chain, RBAC, CORS whitelist (AC-11) | `config/SecurityConfig.java` |
| `User`/`UserRole`/`UserStatus` | Entity + enum-as-String | `entity/*.java` |
| `EmailVerificationToken` + repo | Token 24h | `entity/*`, `repository/*` |
| `RefreshTokenRepository` | Rotation + reuse detection | `repository/RefreshTokenRepository.java` |

## 3. Dependencies

Migration V1–V3 (user, email token, refresh token) + V23/V27/V33. **Foundation** — mọi spec khác tham
chiếu RBAC + token structure của spec này. Phụ thuộc EmailService (#async), Cloudinary (avatar sau).

## 4. Risks & Mitigations

| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Token reuse / theft | Cao | Rotation + panic revoke-all (AC-03) |
| Brute force Admin | Cao | Rate limit IP + account lockout (HR-16) |
| User enumeration | TB | Cùng message cho email/password sai |
| Lộ endpoint qua status code | TB | 403 (không 401/404) cho trái quyền (HR-10) |

## 5. Questions for Human
- Forgot/Reset Password (Out-of-scope #1) — spec riêng sau #15 (đã có `forgot-password.html`/`reset-password.html` + V19 → thực tế đã build một phần).

## 6. Constitution Check (tóm tắt)
CORE nền tảng: HR-10/16/17, AC-03/11/12 là trọng tâm; PASS theo spec §Constitution. Chi tiết: [`spec.md`](spec.md).
