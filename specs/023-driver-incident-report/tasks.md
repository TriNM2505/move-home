# Tasks: Driver Incident Report — Spec #023

> ⚠️ **CHƯA HIỆN THỰC** trên nhánh này (không V44, không code). Ref: [`spec.md`](spec.md),
> [`plan.md`](plan.md). Đây là task **cần làm**, không phải as-built. 🚫 blocked · ⏳ chưa làm · ✅ done

| ID | Task | Spec ref | TT |
|----|------|----------|----|
| T-00 | **Chốt OQ-1 + amendment CONTEXT §State Machine + cấp số `V44`** | OQ-1 / DS-02 | 🚫 blocked |
| T-01 | Migration `driver_incident_report` + `driver_incident_photo` (`V44` — chờ số) | Data Model | ⏳ |
| T-02 | Entity + repository | Data Model | ⏳ |
| T-03 | `POST driver/orders/{id}/incident` + ảnh ≤3 (Cloudinary signed) | Scope 1–2 | ⏳ |
| T-04 | Manager hàng đợi + cờ `overdue` + chi tiết (signed URL) | Scope 3–4 | ⏳ |
| T-05 | Manager confirm → bán đơn pool (`CONFIRMED`, driver=null) + cửa sổ 15' | Scope 5 | ⏳ |
| T-06 | Scheduler quét cửa sổ 15' quá hạn | Scope 6 | ⏳ |
| T-07 | Hook `resolveReassigned` trong `acceptOrder` (tự đóng khi tài xế mới nhận) | Scope 7 | ⏳ |
| T-08 | Manager compensate: khách +FLOOR(30%)+200k, tài xế −200k (cọc→ví→SUSPENDED), đơn CANCELLED | Scope 6, 8 | ⏳ |
| T-09 | Money invariants + transaction boundaries + audit + notification | Scope 9 | ⏳ |
| T-10 | FE `manager/driver-incidents.html` + nút ở `driver/in-progress.html` | Scope 10 | ⏳ |

**Định nghĩa Done:** T-00 phải xong trước (blocker chính sách + migration). Toàn bộ đang ⏳/🚫 — feature
**chưa build**. Lưu ý giao cắt với Spec #019 DS-02 (hội thoại C↔D khi đơn đổi tài xế).
