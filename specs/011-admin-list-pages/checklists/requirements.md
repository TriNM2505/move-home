# Checklist chất lượng Spec — #011 Admin List Pages

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + canonical status mapping (4 entity)
- [x] Pagination/search/filter/sort rõ

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [x] Canonical status (alias PENDING/ACCEPTED/DISPUTED gỡ)
- [x] Out-of-scope #9 audit-viewer → đã tách #025 (D-14, sửa 2026-06-24)

## Constraints / Constitution
- [x] HR-10 (ADMIN), HR-13 (audit throttle), **AC-04 (allowlist sort + bound params)**, AC-15/16

## Scope / Readiness
- [x] Đã build (4 màn)
- [x] Export/bulk defer Sprint 6+

## Kết luận
**CLEARED** — oversight list đã build đúng (allowlist sort chống injection + audit throttle). Verify ES-05.
