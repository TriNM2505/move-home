# Checklist chất lượng Spec — #004 Customer Profile & Wallet

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope rõ (profile + password + payment history)
- [x] Data Model (avatar_url V33) + đọc transaction append-only

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [ ] 🚫 **MÂU THUẪN CỐT LÕI với #021** — spec này chốt "KHÔNG có ví Customer" (`my-wallet` = lịch sử chỉ
      đọc); #021 đã build **ví thật**. Cần leader chốt hướng nào (D-11)
- [x] Email không editable (identity)

## Constraints / Constitution
- [x] HR-02 (không plaintext), AC-10 (avatar signed upload), AC-13 (transaction đọc)

## Scope / Readiness
- [x] Profile/password/avatar đã build
- [ ] 🚫 `my-wallet` bản chất chưa nhất quán (#004 vs #021)

## Kết luận
**KHÔNG CLEARED trọn vẹn** — profile/password OK, nhưng **`my-wallet` mâu thuẫn với ví #021 đã build**.
Đây là 1 mặt của blocker D-11; cần leader quyết cùng OQ-1 của #021.
