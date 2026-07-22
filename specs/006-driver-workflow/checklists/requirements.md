# Checklist chất lượng Spec — #006 Driver Workflow

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + state machine đầy đủ
- [x] Escrow 2h + earnings release

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Canonical transitions (không tạo status ACCEPTED)

## Testability
- [x] AC có cách verify; có race test (accept/reassign/timeout)
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [x] Assignment ở `driver_assignment`, đơn `ASSIGNED` (không status ACCEPTED)
- [x] Complete chỉ sau IPN final verified

## Constraints / Constitution
- [x] HR-05/06/07/08 (state/dispute/HR-08 concurrency), HR-18/AC-13 (ví/escrow)

## Scope / Readiness
- [x] Đã build (6 màn + escrow job)
- [ ] ⚠️ Timeout AWAITING_FINAL_PAYMENT (CONTEXT Q10) chưa chốt

## Kết luận
**CLEARED với điều kiện** — CORE đã build đúng state machine + row lock + escrow. Cần chốt timeout Q10 + verify ES-05.
