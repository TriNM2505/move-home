# ADR-001: Marketplace pivot (v1.5 → v2.0)

- **Status:** Accepted (2026-05-29, thầy duyệt)
- **Nguồn:** constitution Decision **D9** · CONTEXT.md v2.0
- **Superseded:** mô hình công ty nội bộ v1.5 (archived)

## Context
Bản v1.5 mô hình **công ty nội bộ:** 5 vai trò, Driver là nhân viên, có Porter riêng, công ty sở
hữu xe, thanh toán cọc 30% + 70% COD, hoàn tiền thủ công. Thầy yêu cầu pivot sang mô hình
marketplace để sát thực tế thị trường (Grab-like nhưng có điều phối).

## Decision
Chuyển sang **marketplace có điều phối:**
- **4 vai trò** (bỏ Porter — Driver kiêm bốc xếp): Customer / Driver / Manager / Admin.
- Driver **tự đăng ký** (onboarding 4 bước + cọc 3 triệu collateral), sở hữu xe riêng.
- Manager **phân công thủ công** (không auto-assign kiểu Grab).
- Công ty thu **commission 30%** trên total_quote.
- Thanh toán **100% VNPay** (cọc 30% + trả 70% tại chỗ, KHÔNG COD).
- Có **Ví Driver nội bộ** + **escrow 2 giờ** trước khi nhả 70%.
- Thêm **Guest mode** (6 trang public).
- Maps: Google Maps → **OpenStreetMap + OSRM**.

## Alternatives considered
1. **Giữ công ty nội bộ (v1.5):** đơn giản hơn, nhưng không đáp ứng yêu cầu thầy + kém thực tế.
2. **Marketplace mở kiểu Grab (driver tự nhận đơn):** bỏ vai trò điều phối → mất tính "quản lý chất lượng" mà đề án muốn thể hiện.

## Consequences (Trade-off)
- ➕ Sát thực tế, có luồng tiền phong phú (escrow, wallet, commission, dispute) → điểm nghiệp vụ cao.
- ➖ Phức tạp hơn nhiều: thêm onboarding, wallet, dispute, 3 luồng IPN VNPay → rủi ro timeline.
- ➖ Sinh drift lịch sử: tài liệu v1.5 archived, một số spec/code cũ cần đồng bộ lại.
- **Không revert.** Quyết định đã được thầy xác nhận 2026-05-29.
