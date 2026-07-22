# Implementation Plan: Manager Disputes Resolution — Spec #010

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V16 (dispute + evidence + comment), V34 (pending_deduct), V35 (dispute_photo), V37
> (DRIVER_MISMATCH). **Status:** As-built (CORE niềm tin).

## 1. Architectural Approach

Customer tạo dispute trong **24h** sau `COMPLETED` (claim_type + số tiền + ảnh) → đơn `IN_DISPUTE`, tiền
giữ (escrow HELD). Manager/Admin xem toàn cảnh (nội dung 2 bên, ảnh signed URL, lịch sử, comment nội bộ)
→ chọn **1 trong 3 outcome**: `RESOLVED_REFUND` (hoàn khách), `RESOLVED_DEDUCT` (trừ tài xế + hoàn khách),
`CLOSED_NO_FAULT`. Trừ tài xế: **ví → cọc** (HR-18, thứ tự leader chốt V34: ví-trước; thiếu → shortfall +
deadline → khoá + trừ cọc). Hoàn khách → `customer_wallet` + `transaction`. Lock dispute trước ví, atomic
+ audit cùng TX (rollback nếu audit fail).

> ⚠️ Mô hình `dispute` thật **khác CONTEXT §DamageReport** và cửa sổ **24h ≠ 2h escrow** (đã sync CONTEXT).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `DisputeService` + `DisputeController` | resolve 3 outcome, lock, atomic | `dispute/Dispute*.java` |
| `dispute`/`dispute_evidence`/`dispute_comment`/`dispute_photo` | Schema | V16/V35 |
| `PenaltyEnforcementScheduler` | Quét shortfall quá hạn → khoá + trừ cọc | `dispute/PenaltyEnforcementScheduler.java` (V34) |
| `CustomerRefundService` | Hoàn khách (REFUND) | `dispute/CustomerRefundService.java` |
| FE disputes, dispute-detail | 2 màn (+ deep-link chat) | `frontend/pages/manager/*` |

## 3. Dependencies
`V16`/`V34`/`V35`/`V37`. Phụ thuộc #003 (Customer tạo), #006 (escrow HELD), **#021 `customer_wallet`**
(hoàn khách — BLOCKED, D-11), #007 (ví tài xế).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Quyết định đồng thời | Cao | Lock dispute trước ví → 1 commit, còn lại 409 |
| Ví/cọc âm khi trừ tài xế | Cao | Trừ tối đa, thiếu → shortfall + SUSPENDED (HR-18) |
| Hoàn khách phụ thuộc ví #021 (BLOCKED) | Cao | Chờ #021 OQ-1 (D-11) |
| Cửa sổ 24h vs CONTEXT 2h | TB | Đồng bộ CONTEXT (D-03/D-13) |

## 5. Questions for Human
- Chuyển `IN_DISPUTE → COMPLETED/CANCELLED` sau resolve: "integration decision future" (spec ghi để mở).

## 6. Constitution Check (tóm tắt)
HR-05/06/07/10/13/18, AC-08/13. Chi tiết: [`spec.md`](spec.md).
