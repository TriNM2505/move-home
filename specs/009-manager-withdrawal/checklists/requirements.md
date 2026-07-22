# Checklist chất lượng Spec — #009 Admin Withdrawal Processing

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical.**

## Completeness
- [x] Goals + Scope + state machine rút tiền
- [x] Money invariants (chỉ PROCESSED debit)

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Canonical states (không APPROVED/COMPLETED)

## Testability
- [x] AC có cách verify (double-debit, race)
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [ ] ⚠️ **Tên folder "manager-withdrawal" là legacy** — canonical **ADMIN** xử lý (Manager 403); nội dung spec đã đúng
- [x] Append-only `transaction` + audit

## Constraints / Constitution
- [x] HR-10 (ADMIN only), HR-18 (không âm), AC-13 (append-only)

## Scope / Readiness
- [x] Đã build (queue + process/reject)
- [ ] ⚠️ Legacy Manager stub 5.5–5.7 cần dọn

## Kết luận
**CLEARED với điều kiện** — money-critical đã build đúng (Admin + chống double-debit). Dọn legacy stub + verify ES-05.
