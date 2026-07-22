# Checklist chất lượng Spec — #023 Driver Incident Report

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (in/out) rõ
- [x] So sánh rõ với DamageReport (ai báo, khi nào, ai chịu)
- [x] Công thức tiền + thứ tự trừ tài xế (cọc → ví → SUSPENDED)
- [x] State machine kép (sự cố × đơn)

## Clarity
- [x] EARS + message tiếng Việt có dấu (HR-20)
- [x] Chính sách 15'/200k/công ty-chịu-cọc nêu rõ + lý do

## Testability
- [ ] Chưa có code → chưa test được (feature chưa build)

## Consistency
- [ ] 🚫 **Khoảng trống tài liệu cấp trên** — CONTEXT **không có** transition sự cố + chính sách 200k;
      spec là nơi đầu tiên đặc tả → **phải amend CONTEXT trước** (OQ-1/DS-02)
- [x] Thứ tự trừ tiền khớp DamageReport (cọc→ví→SUSPENDED)

## Constraints / Constitution
- [x] Money rules dự kiến đúng (AC-08/AC-13/HR-18)
- [ ] ⚠️ Đích tiền `customer_wallet` phụ thuộc #021 (BLOCKED)

## Scope / Readiness — 🚫 CHƯA SẴN SÀNG
- [ ] 🚫 **Migration `V44` chưa tồn tại** (max = V41) — cần leader cấp số
- [ ] 🚫 **Không có code Java incident** (grep rỗng 2026-06-24) — feature chưa build
- [ ] 🚫 **OQ-1 blocker** — spec tự cấm triển khai trước khi chốt chính sách + amend CONTEXT
- [ ] ⚠️ Giao cắt Spec #019 DS-02 (hội thoại C↔D khi đơn đổi tài xế) phải xử lý cùng

## Kết luận
**KHÔNG CLEARED — spec tốt nhưng CHƯA hiện thực & BLOCKED.** Đây là spec-first (khác 019–022 đã build).
Trước khi làm: (1) chốt OQ-1 + amend CONTEXT §State Machine, (2) leader cấp số `V44`, (3) giải quyết
giao cắt #019 DS-02 và phụ thuộc #021.
