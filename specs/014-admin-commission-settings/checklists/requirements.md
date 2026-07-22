# Checklist chất lượng Spec — #014 Admin Commission Settings

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical config.**

## Completeness
- [x] Goals + Scope + backward-compat invariant rõ
- [x] Data Model (commission_settings + history) khớp V16

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)

## Testability
- [x] AC có cách verify (optimistic lock, preview)
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [x] Snapshot pattern (order cũ giữ `commission_rate_snapshot`)
- [x] Base rate canonical 20k/30k/40k (không dùng proposal legacy 15k/20k/25k)

## Constraints / Constitution
- [x] HR-10/13, AC-08 (VND), AC-14 (CHECK range)
- [x] History + audit cùng TX; optimistic version chống ghi đè

## Scope / Readiness
- [x] Đã build (config + diff + preview + history)

## Kết luận
**CLEARED** — config money-critical đã build đúng (snapshot + optimistic lock + audit). Verify ES-05.
