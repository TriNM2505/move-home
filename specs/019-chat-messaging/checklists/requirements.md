# Checklist chất lượng Spec — #019 Chat Messaging

> "Unit test cho English" — kiểm chất lượng **yêu cầu**, không kiểm implementation.
> Chạy trước khi coi spec là nguồn tin cậy. Ref: [`spec.md`](spec.md).

## Completeness
- [x] Có Goals + Scope (in/out) rõ ràng
- [x] Actors & quyền (Permission Matrix 5 vai trò)
- [x] Data Model đầy đủ (2 bảng + index) khớp migration V36/V38
- [x] Error Matrix (13 mã lỗi + HTTP)
- [x] Acceptance Criteria (21 AC) + Test Cases (22 TC)
- [x] **Out of Scope tường minh** (9 mục) + Deferred Scope (11 mục)

## Clarity
- [x] FR dùng EARS (52 FR, 50% WHERE ≥ 30%)
- [x] Không "magic requirement" — có ngưỡng NFR đo được (p95)
- [x] Message lỗi cụ thể, tiếng Việt có dấu (HR-20)

## Testability
- [x] Mỗi AC có "cách verify"
- [x] Test Cases phủ unit/integration/concurrency
- [ ] ⚠️ ES-05 coverage **chưa verify** con số thực (SHELL — chỉ cần integration happy path)

## Consistency
- [x] Khớp CONTEXT (lệch 3 cấp **có chủ ý**, đã ghi CLAUDE.md §4 + banner CONTEXT)
- [x] Khớp constitution refs (AC-05/AC-14/AC-15/AC-16...)
- [x] Data Model khớp DB thật (đã verify V36/V38 migration thật)

## Constraints / Constitution
- [x] Đã chạy Constitution Check (Layer 1/2/3)
- [ ] ⚠️ **HR-16 gap** — chưa áp rate limit `POST /messages` (DS-11)
- [ ] ⚠️ **AC-09 / AC-10 partial** — chưa có cleanup message/ảnh (DS-06/DS-09)
- [x] AC-11 origin whitelist (không `"*"`)

## Scope / Readiness
- [x] Có Rollout Plan + thứ tự migration
- [x] Open Questions liệt kê (6 OQ)
- [ ] 🚫 **OQ-1 / DS-02 chưa giải quyết** — hội thoại C↔D khi đơn đổi tài xế (bug chức năng + quyền riêng tư); **phải xong trước Spec #023 prod**

## Kết luận
**CLEARED với điều kiện** — spec đủ chất lượng làm nguồn; 3 việc phải theo dõi: HR-16 rate limit,
cleanup (AC-09/10), và **DS-02/OQ-1 (blocker giao với Spec #023)**.
