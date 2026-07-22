# Checklist chất lượng Spec — #018 Error Handling & Recovery

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (4 error page + advice + fetch wrapper) rõ
- [x] ES-04 envelope + request_id

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [x] 401 (guest/invalid) vs 403 (trái quyền) — HR-10
- [x] ES-04 `{error_code,message,details,request_id,timestamp}` (không code/message_vi song song)
- [ ] ⚠️ Nhất quán validate page/size: chat clamp (#019) vs endpoint tiền 422 (#019 DS-05)

## Constraints / Constitution
- [x] HR-01 (không lộ secret/stack/PII), HR-10, HR-13 (audit security), **ES-04**

## Scope / Readiness
- [x] Đã build (4 error page + advice + api.js single-flight refresh)
- [x] Retry chỉ GET/idempotent (chống order/payment trùng)

## Kết luận
**CLEARED** — hạ tầng lỗi FINAL đã build đúng (ES-04 + single-flight refresh + retry an toàn). Việc tồn:
thống nhất chính sách validate page/size toàn dự án (#019 DS-05). Verify ES-05.
