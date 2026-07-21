# Bản vá đề xuất — Hợp thức hoá Ví khách hàng

> **Trạng thái:** ĐỀ XUẤT — chưa áp dụng. Chờ leader duyệt từng mục.  
> **Ngày soạn:** 2026-06-04  
> **Liên quan:** `specs/021-customer-wallet-withdrawal/spec.md` OQ-1, OQ-2  
> **Lý do:** Code đã có ví Customer đầy đủ (nạp / số dư / trả đơn / rút tiền) nhưng `CONTEXT.md v2.0`
> và Spec #004 đều ghi rõ **không có ví Customer**. Spec #004 FR-036 yêu cầu 4 điều kiện trước khi
> thêm: amendment CONTEXT + amendment Constitution + spec/migration riêng + security review.
> File này soạn sẵn nội dung cho 2 điều kiện đầu.

---

## Quyết định cần leader chốt trước

**OQ-1: Ví Customer có được công nhận là kiến trúc chính thức không?**

- **Nếu CÓ** → áp các bản vá bên dưới, spec #021 chuyển `Approved`.
- **Nếu KHÔNG** → phải gỡ bỏ ví khỏi code: xoá `WalletOrderPaymentService`, endpoint top-up, endpoint
  withdrawals, chuyển hoàn tiền về `RefundRecord` thủ công. **Đây là việc rất lớn**, đụng V8/V39/V41,
  dispute, incident, cancellation refund. Không khuyến nghị ở giai đoạn này.

> **Khuyến nghị của tôi:** chọn CÓ. Ví đã ăn sâu vào 4 luồng (huỷ đơn, tranh chấp, sự cố, thanh toán),
> gỡ ra rủi ro hơn nhiều so với việc cập nhật tài liệu. Nhưng đây là quyết định của bạn.

---

## PATCH 1 — `docs/CONTEXT.md` §2 Thuật ngữ domain

**Vị trí:** bảng "Thuat ngu domain", dòng `RefundRecord` (~dòng 89)

**Hiện tại:**

```
| **RefundRecord** | Khi cong ty huy don (loi cong ty): tao RefundRecord PENDING → Manager chat xin STK khach → chuyen khoan thu cong → danh dau PROCESSED. KHONG co vi noi bo cho Customer. |
```

**Đề xuất:**

```
| **RefundRecord** | ⚠️ LOI THOI 2026-07 — KHONG con dung. Hoan tien nay di qua **Vi khach hang** (customer_wallet): huy don → order_cancellation_refund (V41) → Manager duyet → cong vi; tranh chap/su co → cong vi truc tiep. Xem Spec #021, #022. |
| **Customer Wallet (Vi khach hang)** | So du VND cua khach trong he thong (bang `customer_wallet`, V8). Nguon tien vao: nap qua VNPay, hoan coc huy don, hoan tranh chap, boi thuong su co. Tien ra: tra coc 30%, tra not 70%, rut ve ngan hang (Admin duyet). Vi KHONG bao gio am (HR-18). Chi tiet: Spec #021. |
```

---

## PATCH 2 — `docs/CONTEXT.md` §2 Wallet & Commission

**Vị trí:** đầu mục "Wallet & Commission (v2.0 — chi tiet)" (~dòng 418–422)

**Hiện tại:**

```
**Tong quan:** He thong co 2 luong tien tach biet:
- **Vi cong ty (dashboard):** noi tat ca tien chay vao tu khach.
- **Vi Driver (wallet):** noi nhan 70% sau escrow 2h.

KHONG co vi cho Customer (RefundRecord chuyen khoan thu cong).
```

**Đề xuất:**

```
**Tong quan:** He thong co 3 luong tien tach biet:
- **Vi cong ty (dashboard):** noi tat ca tien chay vao tu khach.
- **Vi Driver (wallet):** noi nhan 70% sau escrow 2h.
- **Vi Customer (customer_wallet):** so du cua khach — nap qua VNPay, dung tra don, nhan tien hoan,
  rut ve ngan hang qua Admin duyet.

> ⚠️ **CAP NHAT 2026-07 (doc truoc):** Ban goc v2.0 ghi "KHONG co vi cho Customer". Quyet dinh nay
> da DAO NGUOC: Vi khach hang da duoc trien khai day du (migration V8 + V39). Ly do: hoan tien qua vi
> nhanh hon nhieu so voi luong RefundRecord thu cong (Manager xin STK qua chat → chuyen khoan → bam
> PROCESSED), va cho phep khach tra don bang so du co san. Day la **CO CHU Y**, khong phai bug.
> Chi tiet: Spec #021 Customer Wallet & Withdrawal.
```

---

## PATCH 3 — `docs/CONTEXT.md` §2 Huỷ đơn & Hoàn tiền

**Vị trí:** mục "Huy don & Hoan tien (khong co vi noi bo)" (~dòng 247–262)

**Đề xuất đổi tiêu đề:**

```
### Huy don & Hoan tien  (hoan ve Vi khach hang — cap nhat 2026-07)
```

**Sửa gạch đầu dòng "Cong ty huy / loi cong ty"** — bỏ luồng xin STK thủ công:

```
- **Cong ty huy / loi cong ty** (Manager xac dinh, bat ky trang thai nao tu CONFIRMED): → `CANCELLED`
  (cancelled_by: COMPANY) → hoan **coc 30%** ve **customer_wallet** (transaction REFUND, AC-13;
  vi khong am, HR-18). Khach xem so du + lich su tai `customer/my-wallet.html`, co the rut ve ngan
  hang qua `customer/withdrawal-request.html` (Admin duyet — Spec #021).
  - KHONG con luong "Manager chat xin STK → chuyen khoan thu cong → bam Da hoan tien".
  - KHONG co VNPay refund tu dong.
```

**Bỏ dòng cuối:**

```
- KHONG co vi noi bo, KHONG co VNPay refund tu dong.     ← XOA (nua dau da sai)
```

---

## PATCH 4 — `docs/CONTEXT.md` §3 RBAC

**Vị trí:** bảng RBAC (~dòng 636–651)

**Đề xuất thêm 2 dòng:**

```
| Vi khach hang + rut tien | No | No | No | Yes | No |
| Duyet Withdrawal cua Khach | Yes | No | No | No | No |
```

---

## PATCH 5 — `specs/004-customer-profile-wallet/spec.md`

Spec #004 là nơi mâu thuẫn nặng nhất (FR-032..FR-036 cấm thẳng). Hai lựa chọn:

### Lựa chọn A — Cắt phần ví khỏi #004, trỏ sang #021 *(khuyến nghị)*

1. **Đổi tiêu đề:** `Customer Profile & Wallet` → `Customer Profile & Payment Activity`
2. **§Goals đoạn 2:** thay bằng —
   ```
   Màn hình `my-wallet.html` là màn Ví khách hàng (số dư + nạp + lịch sử giao dịch), được đặc tả ở
   Spec #021 Customer Wallet & Withdrawal. Spec này chỉ phụ trách profile, avatar và đổi mật khẩu.
   ```
3. **§Source-of-Truth:** sửa 3 dòng —
   ```
   | Customer wallet | Tồn tại — Spec #021 | Bảng customer_wallet (V8, V39) |
   | `my-wallet.html` | Màn ví có số dư — Spec #021 | Không thuộc spec này |
   | Transaction storage | Bảng `transaction` dùng chung | Ledger cho cả ví khách — Spec #013/#021 |
   ```
4. **§Scope Out of scope #1:** đổi thành —
   ```
   1. Ví khách hàng (số dư, nạp, rút, trả đơn bằng ví) — Spec #021.
   ```
5. **Xoá Nhóm 6 (FR-032..FR-036)** — toàn bộ nhóm "Unsupported Customer Wallet/Top-up Boundary" đã sai
   thực tế. Đánh số lại FR hoặc để trống có ghi chú.
6. **Bump version:** 1.0.0 → **2.0.0** (MAJOR — đảo ngược quyết định kiến trúc)

### Lựa chọn B — Giữ #004 nguyên, chỉ thêm ghi chú lệch

Thêm banner đầu file:

```
> ⚠️ LOI THOI 2026-07: Muc Out-of-scope #1 va FR-032..FR-036 ("khong ho tro vi Customer") KHONG con
> dung. Vi khach hang da duoc trien khai — xem Spec #021. Cac FR do giu lai chi de tham chieu lich su.
```

Rẻ hơn nhưng để lại FR sai trong tài liệu — hội đồng đọc #004 vẫn thấy "404 FEATURE_NOT_SUPPORTED".

---

## PATCH 6 — `.specify/memory/constitution.md`

⚠️ **Sửa constitution phải bump version + cập nhật Sync Impact Report** (CLAUDE.md §6). Đề xuất
**v1.4.0 → v1.5.0** (MINOR — mở rộng rule hiện có).

### 6a. AC-13 — tên bảng audit trail

**Hiện tại:** quy định audit trail money ở bảng **`wallet_transaction`**.  
**Thực tế:** ví Driver và ví Customer đều ghi vào bảng **`transaction`** (V6/V24/V39).

**Đề xuất thêm vào AC-13:**

```
**Ghi chu trien khai (v1.5.0):** Ten bang thuc te la `transaction` (V6), dung chung cho ca vi Driver
va vi Customer. Cau truc tuan thu tinh than AC-13 (amount, balance_after, ref_*, created_at, append-only,
INSERT cung TX voi UPDATE vi). Ten `wallet_transaction` trong rule nay la ten khai niem, khong phai
ten bang bat buoc. Type enum thuc te: DEPOSIT_TOP_UP, DEPOSIT_REFUND, ORDER_PAYMENT, WALLET_TOP_UP,
DRIVER_EARNING, PLATFORM_FEE, DAMAGE_DEDUCTION, WITHDRAWAL.
```

### 6b. HR-18 — mở rộng cho ví Customer

**Hiện tại:** nói về `wallet.balance` và `wallet.deposit_balance` (ví Driver).

**Đề xuất thêm:**

```
**Mo rong (v1.5.0):** Rule nay ap dung cho CA `customer_wallet.balance`. DB CHECK (balance >= 0) da co
tu V8. Service phai validate truoc khi tru (WalletOrderPaymentService, AdminCustomerWithdrawalService).
```

### 6c. HR-14 — làm rõ RefundRecord (OQ-2)

**Vấn đề:** HR-14 vẫn giả định bảng `RefundRecord` tồn tại cho COMPANY-cancel, nhưng **không có bảng
đó trong DB**. Hoàn tiền COMPANY-cancel thực tế đi đâu? Cần leader xác nhận trước khi soạn patch.

---

## Tóm tắt việc cần bạn quyết

| # | Quyết định | Ảnh hưởng |
|---|-----------|-----------|
| 1 | OQ-1: Ví Customer chính thức? | Chặn toàn bộ patch |
| 2 | Patch 1–4 CONTEXT: áp không? | CONTEXT v2.0 → v2.1 |
| 3 | Patch 5: chọn A (cắt gọn #004, bump 2.0.0) hay B (chỉ ghi chú)? | Spec #004 |
| 4 | Patch 6: bump constitution 1.4.0 → 1.5.0? | Constitution + Sync Impact Report |
| 5 | OQ-2: COMPANY-cancel hoàn tiền đi đâu? Có bảng RefundRecord không? | HR-14 + Spec #022 |

**Tôi không tự áp bất kỳ patch nào.** Bạn OK cái nào, nói cái đó.
