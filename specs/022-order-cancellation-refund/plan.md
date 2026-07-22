# Implementation Plan: Order Cancellation Refund — Spec #022

> **Reconstructed plan (spec-after)** — tái dựng từ code + [`spec.md`](spec.md) v1.0.0.
> **Migration:** V41. **Status:** As-built. **Nền:** HR-14 v1.4.0 + CONTEXT
> đã amend 2026-06-18 (khác #021, luồng này đã được duyệt ở cấp constitution).

## 1. Architectural Approach

Khách hủy đơn `CONFIRMED` **khi `driver_id = NULL`** (chưa tài xế) → đơn `CANCELLED` + tự mở
`order_cancellation_refund` (PENDING) kèm lý do + tối đa 3 ảnh (Cloudinary signed, AC-10). Manager duyệt
hàng đợi FIFO → **Hoàn cọc** (cộng `FLOOR(total_quote × commission_rate_snapshot)` vào `customer_wallet`
+ `transaction` REFUND, cùng TX — AC-13/HR-18) hoặc **Từ chối** (không đụng tiền). Từ `ASSIGNED` trở đi
không hủy được qua luồng này (cọc thuộc công ty). Người duyệt = **MANAGER** (không phải Admin).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `OrderCancellationRefund` + `OrderCancellationPhoto` entity | Yêu cầu hoàn + ảnh | `order/*.java` |
| `OrderCancellationRefundService` | Tạo yêu cầu khi hủy CONFIRMED, tính cọc | `order/OrderCancellationRefundService.java` |
| `OrderCancellationPhotoService` | Upload ảnh Cloudinary signed + sign URL | `order/OrderCancellationPhotoService.java` |
| `ManagerCancellationRefundService` + Controller | Hàng đợi + refund/reject | `order/ManagerCancellationRefund*.java` |
| `CustomerOrderActionService` | `PUT .../cancel` (nhánh CONFIRMED→refund) | `order/CustomerOrderActionService.java` |
| FE `manager/cancellation-refunds.html` | Hàng đợi + chi tiết + duyệt | `frontend/pages/manager/cancellation-refunds.html` |

## 3. Data Flow

```
Customer PUT orders/{id}/cancel (CONFIRMED, driver=null) ──> order=CANCELLED + INSERT order_cancellation_refund(PENDING)
Customer POST cancellation-photos ──Cloudinary signed──> order_cancellation_photo (≤3)
Manager POST refund ──lock wallet──> balance += FLOOR(30%), INSERT transaction(REFUND,+,balance_after) ──> status=REFUNDED + audit + notify
Manager POST reject ──> status=REJECTED + rejection_reason (không đụng tiền)
```

## 4. Dependencies

`V41` → service/controller → FE. **Phụ thuộc `customer_wallet` (Spec #021)** làm đích tiền hoàn —
⚠️ #021 đang BLOCKED (D-11), nên đích đến của tiền chưa được duyệt chính thức. Sổ cái #013.

## 5. Risks & Mitigations

| Rủi ro | Mức | Mitigation | Ref |
|--------|-----|-----------|-----|
| Đích tiền là `customer_wallet` (#021 BLOCKED) | **Cao (coupling)** | Chờ OQ-1 của #021; nếu ví bị rollback → luồng này gãy | #021 D-11 |
| CONTEXT tự mâu thuẫn: §Wallet "không có ví Customer" vs HR-14 "hoàn về customer_wallet" | TB | Amendment CONTEXT §Wallet | OQ-1 |
| COMPANY hủy đơn → không có bảng `refund_record`, tiền khách đi đâu chưa rõ | TB | Làm rõ luồng COMPANY cancel | OQ-1 |
| Guard `driver_id = NULL` phải chặt (không hoàn khi đã có tài xế) | TB | FR guard + test | OQ-2/DS-01 |
| Race: hủy 2 lần | Thấp | UNIQUE `(order_id)` trên bảng refund | V41 |

## 6. Questions for Human

- **OQ-1:** COMPANY hủy đơn hoàn tiền thế nào? (HR-14 nói RefundRecord nhưng **không có bảng
  `refund_record`**). Đồng bộ CONTEXT §Wallet (đang mâu thuẫn).
- **OQ-2:** Guard `driver_id = NULL` — chốt điều kiện chính xác.

## 7. Constitution Check (tóm tắt)

Money rules PASS (FLOOR AC-08, REFUND cùng TX AC-13, ví không âm HR-18, ảnh AC-10, audit HR-13). Nền
HR-14 vững. Vướng duy nhất là **coupling với #021 (ví chưa duyệt)**. Chi tiết: spec §Constitution.
