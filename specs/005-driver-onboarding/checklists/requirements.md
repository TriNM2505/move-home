# Checklist chất lượng Spec — #005 Driver Onboarding

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + state machine 4 bước rõ
- [x] Data Model (driver_profile/driver_document) khớp V4/V10/V14

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Canonical states (không tạo PENDING_VEHICLE)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [x] Lifecycle owner = `app_user.status` (không duplicate ở driver_profile)
- [x] Cọc chỉ xác nhận qua IPN verified

## Constraints / Constitution
- [x] HR-03/04 (IPN), HR-12 (onboarding), HR-18/AC-13 (cọc), AC-10 (ảnh signed)

## Scope / Readiness
- [x] Đã build (4 bước + VNPay cọc)
- [x] Re-submit flow chi tiết defer #008

## Kết luận
**CLEARED** — CORE onboarding đã build đúng state machine + IPN. Verify ES-05.
