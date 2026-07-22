# Implementation Plan: Error Handling & Recovery — Spec #018

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Status:** As-built (FINAL — độ tin cậy hệ thống). Hạ tầng lỗi dùng chung mọi feature.

## 1. Architectural Approach

Chiến lược lỗi đồng nhất: **4 error page** (404, 403, 500, session-expired) giữ brand + tiếng Việt + mã
hỗ trợ an toàn + action phục hồi. **Backend `@RestControllerAdvice`** chuẩn hoá mọi REST error theo
**ES-04** `{error_code, message, details, request_id, timestamp}`, gắn **request ID**, không lộ stack/
secret/PII (HR-01). **FE fetch wrapper** (`api.js`): parse chuẩn, **single-flight refresh** access token
đúng 1 lần, validation inline, retry an toàn (chỉ GET/idempotent — tránh tạo order/payment trùng),
graceful degradation theo section. 401 (guest/invalid) vs 403 (trái quyền, HR-10). Security events (403,
token reuse, rate limit) audit; lỗi vận hành log/metric (không spam audit).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `GlobalExceptionHandler` (`@RestControllerAdvice`) | Chuẩn hoá ES-04 + request_id + redact | `.../GlobalExceptionHandler.java` |
| `ErrorResponse` | Envelope ES-04 | `.../ErrorResponse.java` |
| FE `api.js` | Fetch wrapper, single-flight refresh, retry | `frontend/js/api.js` |
| FE 403/404/500/session-expired | 4 trang lỗi | `frontend/pages/*.html` |

## 3. Dependencies
Dùng chung mọi spec (mọi endpoint trả ES-04). Phụ thuộc #001 (refresh/panic-revoke), audit (#025).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Retry tạo order/payment trùng | Cao | Chỉ retry GET/idempotent + idempotency key |
| Lộ stack/secret/PII qua error | Cao | Redact allowlist + không stack ra ngoài (HR-01) |
| Refresh race nhiều tab | TB | Single-flight refresh trong `api.js` |
| 401 vs 403 lẫn lộn | TB | Guest→401, trái quyền→403 (HR-10) |

## 5. Questions for Human
- Không (ES-04 + strategy đã chốt). Lưu ý DS chat #019 clamp page/size vs endpoint tiền 422 (nhất quán validate).

## 6. Constitution Check (tóm tắt)
HR-01/10/13, ES-04. Chi tiết: [`spec.md`](spec.md).
