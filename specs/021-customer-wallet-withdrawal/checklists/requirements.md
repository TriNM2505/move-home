# Checklist chất lượng Spec — #021 Customer Wallet & Withdrawal

> "Unit test cho English". Ref: [`spec.md`](spec.md). **Money-critical.**

## Completeness
- [x] Goals + Scope (in/out 9 mục) rõ
- [x] Data Model đầy đủ (customer_wallet, customer_withdrawal_request, tái dùng transaction) khớp V8/V24/V39
- [x] **Money Invariants** (MI-001..010) — bắt buộc cho feature tiền, có
- [x] Transaction Boundaries chi tiết (nạp/trả đơn/tạo rút/duyệt/hoàn)
- [x] Error Matrix + AC + State machine rút tiền (PENDING/PROCESSED/REJECTED/CANCELLED)

## Clarity
- [x] EARS (72 FR, 51.4% WHERE ≥ 30%)
- [x] Message lỗi cụ thể, mã `error_code` (ES-04)
- [x] Ngưỡng NFR đo được

## Testability
- [x] AC có cách verify; có test concurrency (2 request rút đồng thời)
- [ ] ⚠️ ES-05 coverage chưa verify (CORE cần ≥70% + integration happy+error path)

## Consistency
- [ ] 🚫 **MÂU THUẪN CHỐT với CONTEXT/Spec #004** — CONTEXT v2.0 chốt "KHÔNG có ví Customer"; spec này
      đảo ngược. Đây là mâu thuẫn có kiểm soát (spec tự nhận BLOCKED), không phải drift âm thầm
- [x] Money rules khớp constitution (HR-18/AC-08/AC-13)
- [ ] ⚠️ **DS-05** — tái dùng `transaction` thay `wallet_transaction` (lệch tên khái niệm AC-13)

## Constraints / Constitution
- [x] Constitution Check đã chạy; money rules PASS (DB CHECK + service)
- [x] Append-only sổ cái (MI-008), pessimistic lock, idempotency rút tiền
- [ ] 🚫 **Điều kiện (d) security review CHƯA làm** (Spec #004 FR-036)

## Scope / Readiness
- [x] Rollout Plan (Sprint 4 ví → Sprint 6 rút)
- [ ] 🚫 **BLOCKER OQ-1** — spec ở trạng thái `BLOCKED`; **code money đã build & chạy trước khi được
      phê duyệt** → rủi ro governance cao nhất trong 8 spec
- [ ] ⚠️ OQ-2 — RefundRecord thay bằng ví, cần sửa CONTEXT §Huỷ đơn + HR-14

## Kết luận
**KHÔNG CLEARED — BLOCKED.** Chất lượng spec cao (money invariants, transaction boundaries đầy đủ),
nhưng **đảo ngược một quyết định đã chốt** và **thiếu security review (d)**. Việc code đã chạy production
không thay thế phê duyệt. Leader cần: (1) quyết OQ-1, (2) chạy security review, (3) amendment
CONTEXT/constitution — hoặc rollback endpoint ví về 404/403 theo Spec #004.
