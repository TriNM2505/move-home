# Checklist chất lượng Spec — #025 Admin Audit Log Viewer

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (in/out) rõ
- [x] Data Model khớp V22 (append-only, 3 index) — đã verify với migration thật
- [x] Phân biệt rõ với Spec #012 (audit theo entity)

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] 4 bộ lọc + pagination rõ

## Testability
- [x] AC có cách verify; `AuditServiceTest` đã tồn tại (graph)
- [ ] ⚠️ ES-05 coverage chưa verify

## Consistency
- [ ] ⚠️ **Mâu thuẫn Spec #011** — audit-log viewer nằm Out-of-scope #9 của #011 (spec tự nhận + đề xuất sửa)
- [ ] ⚠️ **DS-01 lệch HR-13** — HR-13 yêu cầu 6 field (`actor_id`, `actor_role`, `timestamp`, `from_state`,
      `to_state`, `entity_id`); bảng thật có `actor_id/actor_email/action/entity_type/entity_id/detail`
      → `actor_role`/`from_state`/`to_state` nằm trong `detail` chứ không phải cột riêng
- [ ] ⚠️ CONTEXT §3 RBAC thiếu dòng "xem nhật ký hệ thống"

## Constraints / Constitution
- [x] Append-only bất biến (AC-09 "audit log không được xoá")
- [x] Best-effort ghi (không phá nghiệp vụ) + không log PII ra file
- [x] RBAC HR-10 (ADMIN+MANAGER)

## Scope / Readiness
- [x] Feature đã build (V22 + endpoint + FE)
- [ ] ⚠️ OQ-1 — ranh giới quyền Manager xem audit toàn cục

## Kết luận
**CLEARED với điều kiện** — mặt đọc HR-13 đã build tốt. Cần đồng bộ tài liệu: (1) sửa #011 Out-of-scope,
(2) làm rõ DS-01 (bảng chỉ map 3/6 field HR-13 trực tiếp), (3) thêm dòng CONTEXT §3 RBAC.
