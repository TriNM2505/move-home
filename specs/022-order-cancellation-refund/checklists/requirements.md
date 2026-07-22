# Checklist chất lượng Spec — #022 Order Cancellation Refund

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical.**

## Completeness
- [x] Goals + Scope (in/out 7 mục) rõ
- [x] Data Model khớp V41 (order_cancellation_refund + photo, CHECK terminal) — đã verify với migration thật
- [x] Công thức cọc `FLOOR(total_quote × commission_rate_snapshot)`
- [x] Transaction boundaries + audit + notification

## Clarity
- [x] EARS + message tiếng Việt có dấu (HR-20)
- [x] Điều kiện kích hoạt rõ (`CONFIRMED` + `driver_id = NULL`)

## Testability
- [x] AC có cách verify
- [ ] ⚠️ ES-05 coverage chưa verify (CORE)

## Consistency
- [x] **Nền vững** — HR-14 v1.4.0 + CONTEXT §Hủy đơn đã amend 2026-06-18 **trước** khi viết spec (khác #021)
- [ ] ⚠️ **CONTEXT tự mâu thuẫn** — §Wallet "không có ví Customer" vs HR-14 "hoàn về customer_wallet" (phụ thuộc #021 OQ-1)
- [ ] ⚠️ **RefundRecord** — HR-14 nói COMPANY hủy tạo RefundRecord nhưng **không có bảng `refund_record`** (OQ-1)

## Constraints / Constitution
- [x] Money rules PASS (AC-08 FLOOR, AC-13 REFUND cùng TX, HR-18 ví không âm, AC-10 ảnh signed)
- [x] RBAC MANAGER duyệt (không Admin) — khớp CONTEXT §3
- [x] Audit HR-13

## Scope / Readiness
- [x] Rollout + guard trạng thái
- [ ] ⚠️ **Coupling #021** — đích tiền `customer_wallet` của spec #021 đang BLOCKED; nếu ví bị rollback luồng này gãy
- [ ] ⚠️ OQ-1 — luồng COMPANY hủy đơn chưa định nghĩa (không có `refund_record`)

## Kết luận
**CLEARED với điều kiện** — bản thân luồng chất lượng cao và **có nền phê duyệt (HR-14)**, khác #021.
2 việc cần leader: (1) đồng bộ CONTEXT §Wallet + số phận `customer_wallet` (gắn #021 OQ-1); (2) định
nghĩa luồng COMPANY hủy đơn / `refund_record`.
