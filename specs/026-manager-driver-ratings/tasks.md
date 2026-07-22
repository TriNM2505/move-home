# Tasks: Manager Driver Ratings — Spec #026

> As-built. Ref: [`spec.md`](spec.md), [`plan.md`](plan.md).
> ✅ done · ⏳ deferred

| ID | Task | File | Spec ref | TT |
|----|------|------|----------|----|
| T-01 | Tái dùng `order_rating` (V9) + default 5.00 sao (V40) | `V9`, `V40` | Canonical | ✅ |
| T-02 | `ManagerDriverRatingService` + query 3 filter (driverId/stars/keyword) | service | Scope 1–2 | ✅ |
| T-03 | **Fix bẫy `lower(bytea)`**: convert String+lowercase trong Java trước bind | service | Impl Notes FR-012/013 | ✅ |
| T-04 | `GET /api/manager/driver-ratings` + pagination + sort cố định (AC-04) | controller | Scope 1 | ✅ |
| T-05 | RBAC MANAGER (đọc cả comment) | controller | Scope 3, HR-10 | ✅ |
| T-06 | FE `manager/driver-ratings.html` (L/E/E states) | `frontend/pages/manager/driver-ratings.html` | Scope 6 | ✅ |
| T-07 | Test trên PostgreSQL thật (H2 không bắt được bẫy null-bind) | test | Impl Notes | ✅ (đã có repro test) |
| T-08 | Amend CONTEXT §2 (rating 24h ≠ 2h) + §7 (default 5 sao) | `docs/CONTEXT.md` | DS-02 | ⏳ |
| T-09 | Tags rating ("Đúng giờ/Lịch sự/Xe sạch") | — | DS-08 | ⏳ |

**Done (code):** T-01..T-07 ✅. TODO tài liệu: T-08 (amend CONTEXT cho khớp chính sách 24h + 5 sao).
