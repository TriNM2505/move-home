# Checklist chất lượng Spec — #002 Customer Booking

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + Source-of-Truth resolution rõ
- [x] Data Model (booking_draft + mở rộng service_order) khớp V7
- [x] Công thức giá + fallback OSRM (AC-06)

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Pricing **deterministic** (cùng input + snapshot = cùng breakdown)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify (CORE cần ≥70% + happy+error path)

## Consistency
- [x] CONTEXT thắng các mức fixed trong stub (đã chốt Source-of-Truth)
- [x] Kết thúc tại `PENDING_PAYMENT` (khớp #003)

## Constraints / Constitution
- [x] HR-03/04 (IPN nguồn tiền), HR-05 (state 409), AC-06/07/08
- [x] Snapshot `commission_rate_snapshot` vào order

## Scope / Readiness
- [x] Đã build (V7 + wizard 6 bước + VNPay)
- [x] Out of scope rõ (bàn giao payment)

## Kết luận
**CLEARED** — luồng CORE doanh thu đã build, pricing deterministic, IPN đúng chuẩn. Chỉ cần verify ES-05 coverage.
