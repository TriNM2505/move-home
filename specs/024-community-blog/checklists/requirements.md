# Checklist chất lượng Spec — #024 Community Blog

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope + 3 pha (A/B/C) rõ
- [x] Chống spam + PII + soft delete + Cloudinary được nêu

## Clarity
- [x] EARS + tiếng Việt có dấu (HR-20)
- [x] Ngoại lệ ảnh `type=upload` (public) giải thích rõ

## Testability
- [ ] Chưa có code → chưa test được

## Consistency
- [ ] 🚫 **MÂU THUẪN Spec #017** — blog nằm trong Out-of-scope của #017 (feature mới #31 chưa được duyệt)
- [ ] 🚫 **CONTEXT không nhắc blog** — feature hoàn toàn mới, cần amend §7 nếu duyệt

## Constraints / Constitution
- [x] Nêu HR-16 (rate limit), HR-17 (không lộ PII public), AC-09 (soft delete), AC-10 (Cloudinary)

## Scope / Readiness — 🚫 CHƯA SẴN SÀNG
- [ ] 🚫 **Status = Draft/BLOCKED** chờ OQ-1
- [ ] 🚫 **Không có `V42`/`V43`** (max = V41) — cần leader cấp số
- [ ] 🚫 **Không có code Java blog** (grep rỗng 2026-06-24) — chỉ FE stub `blog-detail.html`
- [ ] ⚠️ Memory cũ "blog Pha A xong (V42)" **lỗi thời** cho nhánh `upload/spec`

## Kết luận
**KHÔNG CLEARED — BLOCKED & chưa hiện thực.** Cần leader: (1) quyết OQ-1 (blog có vào scope không, gỡ
mâu thuẫn #017), (2) nếu có → amend CONTEXT §7 + cấp số `V42`/`V43` + build; nếu không → huỷ spec + xoá
FE stub để tránh drift.
