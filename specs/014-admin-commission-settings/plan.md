# Implementation Plan: Admin Commission Settings — Spec #014

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V16 (commission_settings + history). **Status:** As-built (SUPPORT config money).

## 1. Architectural Approach

Trang cấu hình pricing/policy (commission, peak/alley/floor rates, base_rate/km, porter fee, driver
deposit, min withdrawal). Mỗi thay đổi **money-critical**: validate chặt, hiển thị **diff**, xác nhận,
ghi `commission_settings_history` snapshot + audit **trong cùng TX**. **Optimistic locking** (version)
chống 2 Admin ghi đè. **Backward compatibility (invariant cốt lõi):** config mới chỉ áp cho thao tác
**sau** save; **order đã tạo giữ `commission_rate_snapshot`**, booking đã quote giữ snapshot, deposit/
withdrawal đã tạo giữ amount snapshot. Preview 5 sample order trước khi lưu. Email async (không rollback).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| Admin commission settings service | get/preview/patch (optimistic version) | `service/...Commission...` |
| `commission_settings` (singleton id=1) + `commission_settings_history` | Config + lịch sử append-only | `V16` |
| FE `admin/commission-settings.html` | Form + diff + preview | `frontend/pages/admin/commission-settings.html` |

## 3. Dependencies
`V16`. Đọc bởi pricing engine (#002), onboarding deposit (#005), withdrawal min (#007/#009).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| 2 Admin ghi đè config | Cao | Optimistic version → 409 |
| Đổi giá làm tính lại đơn cũ | Cao | Snapshot pattern (order giữ rate cũ) |
| Config sai gây lỗi tiền | Cao | Validate range CHECK + preview 5 sample |

## 5. Questions for Human
- Không (canonical đã rõ; snapshot boundary chốt).

## 6. Constitution Check (tóm tắt)
HR-10/13, AC-08/14. Chi tiết: [`spec.md`](spec.md).
