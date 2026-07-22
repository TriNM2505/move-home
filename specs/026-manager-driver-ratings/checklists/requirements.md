# Checklist chất lượng Spec — #026 Manager Driver Ratings

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (in/out) rõ
- [x] Data Model khớp V9/V40 (order_rating, default 5.00)
- [x] Phân biệt rõ 3 luồng đọc rating (#026 Manager / #012 Admin detail / #016 Reports)
- [x] **Implementation Notes** ghi rõ bẫy PostgreSQL `lower(bytea)`

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Chính sách 5 sao + 24h nêu rõ + đánh đổi

## Testability
- [x] AC có cách verify; **phải test trên PostgreSQL thật** (H2 không bắt bẫy null-bind) — có repro test
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [ ] ⚠️ **CONTEXT lệch (DS-02)** — §2 nói rating trong escrow 2h; thực tế **24h** + **default 5 sao**
      (leader 2026-06-17) **chưa vào CONTEXT** (chỉ ở comment code/migration V40)
- [x] Khớp V9/V40 thật
- [x] Không đụng Spec #024 (blog rating tách biệt hoàn toàn)

## Constraints / Constitution
- [x] AC-04 — sort cố định server, không nhận từ client (chống injection tên cột)
- [x] RBAC MANAGER (HR-10) — Manager đọc cả comment, khác Admin
- [x] Đánh giá bất biến (Manager không ẩn/xoá — đúng ý đồ)

## Scope / Readiness
- [x] Feature đã build (V9/V40 + endpoint + FE + repro test)
- [ ] ⚠️ DS-02 — amend CONTEXT cho khớp chính sách 24h + default 5 sao

## Kết luận
**CLEARED với điều kiện** — feature đã build, chất lượng tốt (có xử lý bẫy PostgreSQL + repro test).
Việc còn lại thuần tài liệu: amend CONTEXT §2/§7 cho khớp 2 quyết định leader 2026-06-17 (rating 24h,
default 5 sao).
