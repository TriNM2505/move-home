# Checklist chất lượng Spec — #017 Public Marketing

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (6 trang + calculator + contact) rõ
- [x] Out-of-scope (blog #024, analytics)

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Calculator = ước tính (không phải quote authoritative)

## Testability
- [x] AC có cách verify (phần đã build)
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [x] Base rate 20k/30k/40k (không legacy); gỡ claim GPS realtime
- [x] Public API chỉ `/api/public/contact` (HR-17)

## Constraints / Constitution
- [x] HR-17 (public endpoint), HR-19/20 (brand/tiếng Việt), AC-16
- [ ] ⚠️ HR-16 rate limit contact — thuộc phần chưa build

## Scope / Readiness — ⚠️ MỘT PHẦN
- [x] 6 trang public + calculator + estimate **đã build**
- [ ] 🚫 **`/api/public/contact` + `contact_submission` CHƯA build** (D-16) — FE form không có BE
- [ ] 🚫 Cần **cấp số migration** `contact_submission` nếu build

## Kết luận
**CLEARED một phần** — trang marketing + calculator đã build; **contact form BE còn thiếu** (D-16).
Leader quyết build contact (cấp số migration) hay ẩn form để tài liệu không lệch.
