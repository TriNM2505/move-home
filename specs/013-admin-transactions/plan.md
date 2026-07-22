# Implementation Plan: Admin System Transactions — Spec #013

> Ref: [`spec.md`](spec.md) v1.0.0.
> **Migration:** V6 + V24 (transaction finance). **Status:** As-built (CORE oversight tài chính).

## 1. Architectural Approach

Trang giám sát tài chính toàn cục: danh sách giao dịch **bất biến** (append-only `transaction` — sổ cái
canonical), filter kết hợp (type/role/status/date/amount/keyword), KPI theo kỳ (inflow/outflow xác nhận,
platform fee, pending withdrawal), biểu đồ theo loại, chi tiết liên kết (truy về order/withdrawal/dispute/
payment), **báo cáo reconciliation** kiểm từng invariant (không giả định inflow−outflow=balance). Không
sửa/xoá/void. Tiền VND nguyên đồng (AC-08). Chỉ ADMIN (HR-10). Mask ref nhạy cảm. Hiệu năng tới ~1M rows.

> `wallet_transaction` trong mô tả legacy = **projection đọc** từ `transaction`, không tạo bảng ledger 2.

## 2. Components (as-built)

| Thành phần | Trách nhiệm | File |
|------------|-------------|------|
| Admin transaction service | list/filter/summary/by-type/reconciliation | `service/...Transaction...` |
| `transaction` (append-only) | Sổ cái canonical (10 loại) | `entity/Transaction.java` |
| FE `admin/transactions.html` + `admin-transactions.js` | Danh sách + KPI + chart | `frontend/pages/admin/transactions.html` |

## 3. Dependencies
`V6`/`V24`. Nguồn từ #004/#006/#007/#009/#010/#021 (mọi luồng tiền). Là **sổ cái dùng chung**.

## 4. Risks & Mitigations
| Rủi ro | Mức | Mitigation |
|--------|-----|-----------|
| Đối soát sai do 2 sổ cái | Cao | Chỉ 1 bảng `transaction`; wallet_transaction = projection |
| Sửa/xoá giao dịch | Cao | Append-only (AC-13), không endpoint mutate |
| Chậm khi 1M rows | TB | Index type/created_at/user; pagination |

## 5. Questions for Human
- Không (canonical ledger đã rõ).

## 6. Constitution Check (tóm tắt)
HR-10/13, AC-08/13. Chi tiết: [`spec.md`](spec.md).
