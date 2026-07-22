# Implementation Plan: Driver Onboarding (4-Step) — Spec #005

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V4 (driver_profile), V10 (onboarding fields), V14 (driver_document), V15, V29/V31/V32
> (doc types). **Status:** As-built (CORE nguồn cung Driver).

## 1. Architectural Approach

Tự đăng ký 4 bước → `ACTIVE`: Step1 account+verify email (#001) → Step2 giấy tờ (GPLX trước/sau, đăng ký
xe, 3 ảnh xe) qua Cloudinary signed (AC-10) → Step3 cọc **3.000.000đ** qua VNPay (chỉ IPN verified xác
nhận — HR-03/04) → Step4 chờ Manager duyệt. Lifecycle owner = `app_user.status`
(`PENDING_VERIFY→PENDING_DOCUMENTS→PENDING_DEPOSIT→PENDING_APPROVAL→ACTIVE`). Không skip bước, resume
được, audit mọi transition (HR-13). Cọc ghi `transaction` `DEPOSIT_TOP_UP` + snapshot
`driver_profile.deposit_amount`.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `DriverProfileService` / onboarding service | status/step/next-action | `service/DriverProfileService.java` |
| `DriverDocumentService` | Upload giấy tờ Cloudinary signed | `.../DriverDocumentService.java` |
| `driver_document` (V14) | GPLX/đăng ký xe/ảnh xe | `V14` (+ V29/31/32 doc types) |
| Deposit VNPay | URL cọc 3tr + IPN | `payment/VnPay*` |
| FE register-step1..3-deposit, pending-approval, driver-terms | Onboarding | `frontend/pages/driver/*` |

## 3. Dependencies
`V4`/`V10`/`V14`/`V15`/`V29`/`V31`/`V32`. Phụ thuộc #001 (register/verify), Cloudinary, VNPay. Bàn giao
sang #008 (Manager duyệt).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Xác nhận cọc qua return URL (giả mạo) | Cao | Chỉ IPN verified (HR-03/04) |
| Skip bước | TB | Guard theo `app_user.status` |
| Ảnh unsigned lộ key | TB | Signed upload (AC-10) |

## 5. Questions for Human
- Re-submit khi REJECTED: chi tiết edit flow defer sang #008 (Manager Approval).

## 6. Constitution Check (tóm tắt)
HR-03/04/12/15/18, AC-08/10/13. Chi tiết: [`spec.md`](spec.md).
