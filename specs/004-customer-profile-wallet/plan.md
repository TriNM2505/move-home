# Implementation Plan: Customer Profile & Wallet — Spec #004

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V33 (avatar_url). **Status:** As-built (SUPPORT).
> ⚠️ **Xung đột đã biết:** spec này chốt **"KHÔNG có ví Customer"**; nhưng #021 đã build ví Customer
> (BLOCKED, chờ leader duyệt).

## 1. Architectural Approach

Quản lý hồ sơ Customer: xem/sửa `full_name`/`phone`/avatar (email chỉ đọc — identity). Đổi mật khẩu
(verify mật khẩu cũ → revoke mọi refresh token → đăng nhập lại), audit + email cảnh báo async, không lộ
plaintext (HR-02). Avatar qua **Cloudinary signed upload** (AC-10, không Base64/BLOB). `my-wallet.html`
theo spec gốc là **"Lịch sử thanh toán chỉ đọc"** (đọc `transaction` append-only) — **không** ví chi
tiêu được. ⚠️ Thực tế #021 đã biến nó thành ví thật → mâu thuẫn spec, chờ leader (D-11).

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| `CustomerProfileService` | profile/edit/change-password | `service/CustomerProfileService.java` |
| Avatar signed upload | Cloudinary signature + confirm | `config/CloudinaryConfig.java` + service |
| `transaction` (đọc) | Lịch sử thanh toán | `entity/Transaction.java` |
| FE my-profile, my-profile-edit, change-password, my-wallet | 4 màn | `frontend/pages/customer/*` |

## 3. Dependencies
`V33`. Phụ thuộc #001 (auth/token revoke), Cloudinary. **Mâu thuẫn với #021** (ví Customer).

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| **Mâu thuẫn "no wallet" (#004) vs ví đã build (#021)** | **Cao** | Chờ leader OQ-1 của #021 (D-11) |
| Lộ plaintext password | Cao | BCrypt, verify cũ trước đổi (HR-02) |
| Avatar unsigned lộ API key | TB | Signed upload server-side (AC-10) |

## 5. Questions for Human
- **D-11:** `my-wallet` là "lịch sử chỉ đọc" (spec #004) hay ví thật (#021 đã build)? Cần chốt.

## 6. Constitution Check (tóm tắt)
HR-02/10/18, AC-10/13. Chi tiết: [`spec.md`](spec.md). Lưu ý mâu thuẫn ví với #021.
