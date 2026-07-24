# ADR-005: Auth JWT HS256 stateless + refresh token rotation

- **Status:** Accepted
- **Nguồn:** PROJECT_KNOWLEDGE §1.4 · constitution **AC-03**, **HR-16**

## Context
Backend cần authentication cho 4 role (RBAC). Muốn stateless để không phụ thuộc shared session
store, và sẵn sàng cho mobile app tương lai (nếu có).

## Decision
- **JWT HS256** (symmetric secret từ `.env`). **KHÔNG RS256** (overkill cho 1 service).
- Access token **15 phút**; refresh token **7 ngày**, lưu DB (hash SHA-256).
- **Refresh token rotation + reuse detection:** mỗi lần refresh → cấp mới access+refresh, revoke cũ;
  nếu token đã revoke bị dùng lại → PANIC, revoke toàn bộ token của user.
- Logout = xóa refresh token khỏi DB (server-side invalidation).
- Login security: rate-limit 5/IP/15ph (429) + lockout 5 sai → khóa 15ph (423).

## Alternatives considered
| Option | Verdict |
|--------|---------|
| **JWT HS256** | ✅ Chọn — stateless, đơn giản, mobile-ready |
| Session + Redis | ❌ Cần thêm infra Redis |
| JWT RS256 | ❌ Public/private key overkill cho 1 service |
| OAuth 2.0 provider | ❌ Phức tạp setup |

## Consequences (Trade-off)
- ➕ Stateless, scale ngang không cần shared store; blast radius nhỏ (access token 15ph).
- ➖ Revocation khó với access token → bù bằng TTL ngắn + refresh rotation + DB store refresh.
- ➖ Secret HS256 phải bảo vệ tuyệt đối (`.env`, HR-01) — lộ = giả mạo được mọi token.
