# Checklist chất lượng Spec — #020 Notifications

> "Unit test cho English". Ref: [`spec.md`](spec.md).

## Completeness
- [x] Goals + Scope (in/out) rõ
- [x] Data Model 1 bảng khớp V18 (đã verify migration thật)
- [x] Danh mục type đầy đủ (24 hằng số + 5 hardcode ghi rõ)
- [x] Error Matrix + AC (14) + Test Cases (13)
- [x] Out of Scope (9) + Deferred Scope (12)

## Clarity
- [x] EARS (32 FR, 31.2% WHERE ≥ 30% — thấp nhất nhưng đúng bản chất hạ tầng đơn giản)
- [x] NFR có ngưỡng đo được
- [x] Message tiếng Việt có dấu (HR-20)

## Testability
- [x] Mỗi AC có cách verify; TC phủ unit/integration
- [ ] ⚠️ ES-05 coverage chưa verify (đã có `NotificationServiceTest`)

## Consistency
- [x] Không mâu thuẫn CONTEXT (CONTEXT chỉ nói email; spec này lấp chỗ trống in-app)
- [x] Khớp V18 thật; ghi rõ **2 đường tạo notification không nhất quán** (DS-10)

## Constraints / Constitution
- [x] Constitution Check đã chạy; Layer 1 ALL PASS
- [ ] ⚠️ **AC-14 partial** — `type` VARCHAR(50) **không CHECK** → 5 type hardcode, typo không phát hiện (DS-01)
- [ ] ⚠️ **AC-09** — không `deleted_at`/cleanup (coi N/A nhưng ghi nhận DS-09)

## Scope / Readiness
- [x] Rollout Plan + thứ tự (notification lên trước #021–#024)
- [x] Open Questions (7 OQ)
- [ ] ⚠️ **OQ-3/DS-07** — `REQUIRES_NEW` sinh thông báo "ma" khi nghiệp vụ rollback (nặng nhất ở feature tiền)
- [ ] ⚠️ **OQ-2/DS-02** — badge sai khi >5 chưa đọc
- [ ] ⚠️ **OQ-4/DS-08** — thiếu index → full scan khi bảng lớn (Neon free tier)

## Kết luận
**CLEARED** — hạ tầng đơn giản, tuân thủ tốt. 3 việc thật cần theo dõi: thông báo "ma" (DS-07),
badge sai (DS-02), thiếu index (DS-08). Không block.
