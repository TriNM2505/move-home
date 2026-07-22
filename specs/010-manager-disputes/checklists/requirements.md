# Checklist chất lượng Spec — #010 Manager Disputes

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical.**

## Completeness
- [x] Goals + Scope + 3 outcome rõ
- [x] Data Model (dispute + evidence/comment/photo) khớp V16/V34/V35/V37
- [x] Thứ tự trừ tài xế + escrow HELD

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] `IN_DISPUTE` canonical (DISPUTED = alias)

## Testability
- [x] AC có cách verify (concurrent decision)
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [ ] ⚠️ **Khác CONTEXT §DamageReport** (D-03): status OPEN/INVESTIGATING/RESOLVED_*/CLOSED; cửa sổ 24h ≠ 2h
- [ ] ⚠️ Hoàn khách → `customer_wallet` phụ thuộc **#021 (BLOCKED, D-11)**

## Constraints / Constitution
- [x] HR-05/06/07 (state/dispute), HR-18 (ví/cọc không âm), AC-08/13 (tiền/audit)
- [x] Lock dispute trước ví; audit atomic (rollback nếu fail)

## Scope / Readiness
- [x] Đã build (2 màn + scheduler penalty)
- [ ] ⚠️ Đồng bộ CONTEXT (D-03/D-13); chuyển trạng thái sau resolve để mở

## Kết luận
**CLEARED với điều kiện** — CORE niềm tin đã build đúng (3 outcome + lock + atomic). Vướng: đồng bộ CONTEXT
(D-03) + phụ thuộc ví #021 (D-11) + verify ES-05.
