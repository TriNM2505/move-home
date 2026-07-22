# Implementation Plan: Admin Detail Pages — Spec #012

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V22 (audit), V27 (suspension). **Status:** As-built (CORE oversight detail).

## 1. Architectural Approach

3 trang chi tiết Admin (order/driver/customer) — oversight rộng: lifecycle đầy đủ, dòng tiền, 6 document
canonical (signed Cloudinary URL TTL ≤1h), history, dispute, audit. Chỉ **ADMIN** (HR-10). Read-only mặc
định + **suspend/reactivate** Customer/Driver (confirm + lý do + row lock + **revoke token** + audit
atomic — HR-13). Suspension **không phá state machine** đơn đang chạy (escalation thủ công). Privacy:
Customer analytics chỉ district (không exact address), redact metadata theo allowlist. Endpoint audit
**theo entity** `GET /api/admin/{entityType}/{id}/audit-log` (bổ trợ #025 audit toàn cục).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `AdminDetailService` | Tổng hợp order/driver/customer detail | `service/AdminDetailService.java` |
| `AdminUserAccountService` + Controller | suspend/reactivate + revoke token | `service/AdminUserAccountService.java` |
| `AdminOrderDetailService` | Order detail (pricing/payment/dispute/timeline) | `service/AdminOrderDetailService.java` |
| Audit-log-theo-entity | `GET /api/admin/{entityType}/{id}/audit-log` | `controller/AdminDetailController.java` |
| FE order-detail/driver-detail/customer-detail | 3 màn | `frontend/pages/admin/*` |

## 3. Dependencies
`V22`/`V27`. Phụ thuộc #011 (navigate từ list), toàn bộ domain specs (dữ liệu). Bổ trợ #025 (audit toàn cục).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Suspend phá đơn đang chạy | Cao | Không auto-cancel active order; escalation thủ công |
| Lộ PII (địa chỉ chính xác, token) | Cao | District-only + redact allowlist + không trả secret |
| Suspend đồng thời | TB | Row lock + audit atomic |

## 5. Questions for Human
- Force-cancel + export PDF: disabled, defer Sprint 6+.

## 6. Constitution Check (tóm tắt)
HR-05/10/11/13, AC-14/15/16. Chi tiết: [`spec.md`](spec.md).
