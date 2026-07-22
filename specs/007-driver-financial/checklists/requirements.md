# Checklist chất lượng Spec — #007 Driver Financial

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical.**

## Completeness
- [x] Goals + Scope + Money invariants (available=balance−Σpending)
- [x] Data Model (driver_wallet/withdrawal_request) khớp V11/V12

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Canonical states (không APPROVED/COMPLETED)

## Testability
- [x] AC có cách verify (race cancel/process)
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [x] Rút do **Admin** (không Manager) — mirror #009
- [x] Ví nguồn `driver_wallet` (#006), không dùng `driver_profile.total_revenue`

## Constraints / Constitution
- [x] HR-18 (ví không âm), AC-13 (append-only), HR-05 (state)

## Scope / Readiness
- [x] Đã build (earnings + withdrawal + charts)
- [ ] ⚠️ Min/max/phí rút (Q7) — min 100k có, còn lại chưa chốt

## Kết luận
**CLEARED với điều kiện** — money-critical đã build đúng invariant. Chốt min/max/phí (Q7) + verify ES-05.
