# Checklist chất lượng Spec — #003 Customer Orders

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + canonical status mapping rõ
- [x] 3 view + detail + timeline + rating

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Canonical status (stub PENDING/ACCEPTED/DISPUTED → alias)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [x] Đầu vào `PENDING_PAYMENT` (khớp #002)
- [ ] ⚠️ **Cửa sổ rating 2h (spec này) vs 24h (#026)** — cần đồng bộ CONTEXT (D-13)

## Constraints / Constitution
- [x] HR-05 (state 409), HR-10 (ownership), AC-15/16
- [x] Timeline từ `order_audit_log` (không suy từ status)

## Scope / Readiness
- [x] Đã build (5 màn + location + rating)
- [x] Realtime WebSocket defer (polling baseline)

## Kết luận
**CLEARED với điều kiện** — đã build. Cần đồng bộ cửa sổ rating theo #026 (D-13) + verify ES-05.
