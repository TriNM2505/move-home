# Tasks: Error Handling & Recovery — Spec #018

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md). ✅ done · ⏳

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | 4 error page (404/403/500/session-expired) brand + tiếng Việt + action phục hồi | `frontend/pages/*.html` | Goals | ✅ |
| T-02 | `@RestControllerAdvice` chuẩn hoá ES-04 + request_id + redact | `GlobalExceptionHandler` | Goals | ✅ |
| T-03 | FE fetch wrapper `api.js` (single-flight refresh, retry an toàn) | `frontend/js/api.js` | Goals | ✅ |
| T-04 | 401 (guest/invalid) vs 403 (trái quyền, HR-10) | advice | Goals | ✅ |
| T-05 | Retry chỉ GET/idempotent (tránh order/payment trùng) | `api.js` | Goals | ✅ |
| T-06 | Audit security events (403, token reuse, rate limit) | `AuditService` | Goals | ✅ |
| T-07 | Không lộ stack/secret/PII (HR-01) + structured logging + correlation | advice | Goals | ✅ |
| T-08 | Thống nhất validate page/size toàn dự án (chat clamp vs endpoint tiền 422) | — | #019 DS-05 | ⏳ |

**Done:** T-01..T-07 ✅ (FINAL — hạ tầng lỗi dùng chung). T-08 (nhất quán validate) tồn.
