# Move_home — Use Cases Catalog

> **Mục đích:** Tổng hợp toàn bộ 65 use cases của Move_home, mapped với 18 functional specs và
> 65 màn hình. Source of Truth cho hội đồng final defense.
>
> **Format:** Catalog matrix (overview) — chi tiết xem các `specs/XXX-*/spec.md`
> **Version:** 1.0
> **Last updated:** 2026-06-04

---

## Tổng quan

### Phân loại Use Cases

| Actor | Số UC | UC IDs |
|-------|------:|--------|
| Guest (visitor) | 6 | UC-001 → UC-006 |
| Customer | 16 | UC-007 → UC-022 |
| Driver | 13 | UC-023 → UC-035 |
| Manager | 9 | UC-036 → UC-044 |
| Admin | 12 | UC-045 → UC-056 |
| System (cross-cutting) | 9 | UC-057 → UC-065 |
| **TOTAL** | **65** | **UC-001 → UC-065** |

### Phân loại theo Priority

| Priority | Số UC | Ghi chú |
|----------|------:|---------|
| P1 — Critical (CORE) | 53 | Luồng chính, bảo mật, vận hành và tích hợp bắt buộc |
| P2 — High (SUPPORTIVE) | 12 | Recovery, lịch sử, báo cáo và UX hỗ trợ |
| P3 — Nice-to-have | 0 | Các P3/deferred stories không nằm trong 65 UC committed scope |
| **TOTAL** | **65** | Priority phản ánh scope hiện tại của 18 specs |

### Quy tắc Mapping

- Một UC có thể map nhiều màn hình khi đó là một workflow liên tục, ví dụ booking 6 bước.
- Một màn hình có thể phục vụ nhiều UC, ví dụ `driver/in-progress.html` cho bắt đầu và hoàn thành đơn.
- Các UC hệ thống không có route UI được ghi `(no UI)`.
- Spec `009-manager-withdrawal` quy định actor xử lý canonical là **Admin** và migrate các route
  Manager legacy sang Admin. Catalog vẫn đặt UC-040 → UC-042 trong nhóm Manager để giữ đúng
  matrix 65 UC và inventory hiện tại; cột Actor ghi rõ trạng thái legacy.
- `SCREEN_INVENTORY.md` bundle `/terms.html` + `/privacy.html` thành một screen entry và bundle
  một số workflow nhiều route khi tổng hợp scope.

---

## CATALOG MATRIX (65 Use Cases)

### Section 1 — Guest / Public (UC-001 → UC-006)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-001 | Xem trang chủ marketing và CTA đăng ký | Guest | P1 | 017 | `index.html` (`public/index.html` canonical theo spec) |
| UC-002 | Xem trang Giới thiệu Move_home | Guest | P1 | 017 | `about.html` |
| UC-003 | Xem Cách thức hoạt động | Guest | P1 | 017 | `how-it-works.html` |
| UC-004 | Xem Bảng giá và calculator ước tính | Guest | P1 | 017 | `pricing.html` |
| UC-005 | Gửi liên hệ và xem FAQ | Guest | P1 | 017 | `contact.html` |
| UC-006 | Đọc Điều khoản và Privacy | Guest | P2 | 017 | `terms.html`, `privacy.html` |

### Section 2 — Customer (UC-007 → UC-022)

#### Authentication (UC-007 → UC-011)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-007 | Đăng ký tài khoản Customer | Guest | P1 | 001 | `register.html` |
| UC-008 | Xác thực email tài khoản | Customer | P1 | 001 | `verify-email-success.html` |
| UC-009 | Đăng nhập theo vai trò Customer | Customer | P1 | 001 | `login.html` |
| UC-010 | Yêu cầu khôi phục mật khẩu | Customer | P2 | 001 | `forgot-password.html` |
| UC-011 | Đặt lại mật khẩu bằng reset token | Customer | P2 | 001 | `reset-password.html?token=XXX` |

#### Booking (UC-012 → UC-014)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-012 | Bắt đầu và hoàn tất đặt đơn chuyển nhà 6 bước | Customer | P1 | 002 | `customer/home.html`, `customer/booking-step1-vehicle.html`, `customer/booking-step2-pickup.html`, `customer/booking-step3-dropoff.html`, `customer/booking-step4-details.html`, `customer/booking-step5-quote.html`, `customer/booking-step6-payment.html`, `customer/booking-success.html` |
| UC-013 | Xem báo giá itemized và chấp nhận | Customer | P1 | 002 | `customer/booking-step5-quote.html`, `customer/booking-step6-payment.html` |
| UC-014 | Lưu, resume hoặc hủy booking draft | Customer | P1 | 002 | `customer/booking-step1-vehicle.html` → `customer/booking-step6-payment.html` |

#### Orders Management (UC-015 → UC-019)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-015 | Xem và hủy đơn đang chờ khi được phép | Customer | P1 | 003 | `customer/my-orders-pending.html` |
| UC-016 | Theo dõi đơn đang giao và vị trí Driver | Customer | P1 | 003 | `customer/my-orders-active.html` |
| UC-017 | Xem lịch sử đơn với filter và pagination | Customer | P2 | 003 | `customer/my-orders-history.html` |
| UC-018 | Xem chi tiết, giá và timeline đơn | Customer | P1 | 003 | `customer/order-detail.html?id=XXX` |
| UC-019 | Đánh giá Driver sau khi hoàn thành | Customer | P2 | 003 | `customer/order-rate.html?id=XXX` |

#### Profile + Payment Activity (UC-020 → UC-022)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-020 | Xem và sửa thông tin cá nhân, avatar | Customer | P1 | 004 | `customer/my-profile.html`, `customer/my-profile-edit.html` |
| UC-021 | Đổi mật khẩu và đăng xuất mọi session | Customer | P1 | 004 | `customer/change-password.html` |
| UC-022 | Xem tổng tiền và lịch sử thanh toán | Customer | P2 | 004 | `customer/my-wallet.html` |

> Spec `004` giữ route `my-wallet.html` để không phá inventory, nhưng behavior canonical là lịch sử
> thanh toán chỉ đọc, không phải ví nạp/rút nội bộ.

### Section 3 — Driver (UC-023 → UC-035)

#### Onboarding (UC-023 → UC-027)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-023 | Đăng ký Driver bước 1 và xác thực email | Guest | P1 | 001, 005 | `driver/register-step1.html` |
| UC-024 | Upload GPLX, đăng ký xe và ảnh xe bước 2 | Driver | P1 | 005 | `driver/register-step2.html` |
| UC-025 | Đặt cọc 3.000.000 VND qua VNPay bước 3 | Driver | P1 | 005 | `driver/register-step3-deposit.html` |
| UC-026 | Theo dõi trạng thái chờ Manager duyệt bước 4 | Driver | P1 | 005 | `driver/pending-approval.html` |
| UC-027 | Nhận email duyệt và kích hoạt tài khoản | Driver | P2 | 005 | `(email)`, `driver/home.html` |

#### Workflow hàng ngày (UC-028 → UC-033)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-028 | Xem dashboard, KPI và availability | Driver | P1 | 006 | `driver/home.html` |
| UC-029 | Xem assignment hoặc đơn có sẵn | Driver | P1 | 006 | `driver/available-orders.html` |
| UC-030 | Xem chi tiết và nhận/từ chối đơn có lock | Driver | P1 | 006 | `driver/available-orders.html`, `driver/order-detail.html?id=XXX` |
| UC-031 | Đến điểm đón, bắt đầu và cập nhật vận chuyển | Driver | P1 | 006 | `driver/in-progress.html?id=XXX` |
| UC-032 | Yêu cầu thanh toán cuối và hoàn thành đơn | Driver | P1 | 006 | `driver/in-progress.html?id=XXX` |
| UC-033 | Xem lịch sử, earnings từng đơn và hồ sơ | Driver | P2 | 006 | `driver/history.html`, `driver/profile.html` |

#### Tài chính (UC-034 → UC-035)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-034 | Xem thu nhập, KPI và charts | Driver | P1 | 007 | `driver/earnings.html` |
| UC-035 | Yêu cầu rút tiền và xem lịch sử withdrawal | Driver | P1 | 007 | `driver/withdrawal-request.html`, `driver/withdrawal-history.html` |

### Section 4 — Manager (UC-036 → UC-044)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-036 | Xem dashboard Manager và tác vụ cần xử lý | Manager | P2 | 008, 010 | `manager/home.html` |
| UC-037 | Xem queue Driver PENDING_APPROVAL | Manager | P1 | 008 | `manager/driver-approvals.html` |
| UC-038 | Xem chi tiết, approve hoặc reject Driver | Manager | P1 | 008 | `manager/driver-detail.html?id=XXX` |
| UC-039 | Xem lịch sử Driver bị reject | Manager | P2 | 008 | `manager/driver-rejected.html` |
| UC-040 | Xem queue withdrawal chờ xử lý | Manager route legacy / Admin canonical | P1 | 009 | `manager/withdrawal-pending.html` → `admin/withdrawal-pending.html` |
| UC-041 | Approve/reject và mark withdrawal processed | Manager route legacy / Admin canonical | P1 | 009 | `manager/withdrawal-detail.html?id=XXX` → `admin/withdrawal-detail.html?id=XXX` |
| UC-042 | Xem lịch sử withdrawal đã xử lý | Manager route legacy / Admin canonical | P1 | 009 | `manager/withdrawal-history.html` → `admin/withdrawal-history.html` |
| UC-043 | Xem và xử lý khiếu nại với 3 outcomes | Manager | P1 | 010 | `manager/disputes.html`, `manager/dispute-detail.html?id=XXX` |
| UC-044 | Xem lịch sử khiếu nại theo outcome/ngày | Manager | P2 | 010 | `manager/disputes.html` (history filter), `manager/dispute-detail.html?id=XXX` |

### Section 5 — Admin (UC-045 → UC-056)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-045 | Xem Admin Dashboard với KPI, charts và tables | Admin | P1 | 015 | `admin/dashboard.html` |
| UC-046 | Xem và tìm kiếm danh sách Orders | Admin | P1 | 011 | `admin/orders.html` |
| UC-047 | Xem và tìm kiếm danh sách Drivers | Admin | P1 | 011 | `admin/drivers.html` |
| UC-048 | Xem và tìm kiếm danh sách Customers | Admin | P1 | 011 | `admin/customers.html` |
| UC-049 | Xem và lọc danh sách Withdrawals | Admin | P1 | 011, 009 | `admin/withdrawals.html` |
| UC-050 | Xem chi tiết Order, payment và audit | Admin | P1 | 012 | `admin/order-detail.html?id=XXX` |
| UC-051 | Xem chi tiết Driver, documents và performance | Admin | P1 | 012 | `admin/driver-detail.html?id=XXX` |
| UC-052 | Xem chi tiết Customer và lịch sử | Admin | P1 | 012 | `admin/customer-detail.html?id=XXX` |
| UC-053 | Xem System Transactions và reconciliation | Admin | P1 | 013 | `admin/transactions.html` |
| UC-054 | Cấu hình commission và pricing settings | Admin | P1 | 014 | `admin/commission-settings.html`, `admin/settings.html` |
| UC-055 | Xem Analytics Reports | Admin | P2 | 016 | `admin/reports.html` |
| UC-056 | Suspend hoặc reactivate Driver/Customer | Admin | P1 | 012 | `admin/driver-detail.html?id=XXX`, `admin/customer-detail.html?id=XXX` |

### Section 6 — System / Cross-cutting (UC-057 → UC-065)

| UC-ID | Use Case Name | Actor | Priority | Spec | Screen |
|-------|---------------|-------|----------|------|--------|
| UC-057 | Hiển thị trang 404 Not Found | System | P1 | 018 | `404.html` |
| UC-058 | Hiển thị trang 403 Forbidden theo role | System | P1 | 018 | `403.html` |
| UC-059 | Hiển thị trang 500 với request ID và retry | System | P1 | 018 | `500.html` |
| UC-060 | Refresh token hoặc xử lý Session Expired | System | P1 | 001, 018 | `session-expired.html`, `login.html` |
| UC-061 | Validate lỗi form, network recovery và error handling | System | P1 | 018 | `(cross-screen)` |
| UC-062 | Ghi Audit Log cho security và state changes | System | P1 | 001, 003, 006, 008, 009, 010, 012, 013, 014, 015 | `(no UI)` |
| UC-063 | Gửi email notifications bất đồng bộ | System | P1 | 001, 002, 003, 004, 005, 007, 008, 009 | `(email / no UI)` |
| UC-064 | Tính khoảng cách OSRM và xử lý fallback | System | P1 | 002 | `(no UI)` |
| UC-065 | Xử lý VNPay và Cloudinary integrations | System | P1 | 002, 004, 005, 010 | `(no UI)` |

---

## Spec Coverage Matrix

| Spec | Functional Scope | Mapped UC IDs |
|------|------------------|---------------|
| 001 — Auth & RBAC | Đăng ký, xác thực, login, token/session | UC-007 → UC-011, UC-023, UC-060, UC-062, UC-063 |
| 002 — Customer Booking | Booking draft, quote, confirm, payment | UC-012 → UC-014, UC-063 → UC-065 |
| 003 — Customer Orders | Pending, active, history, detail, rating | UC-015 → UC-019, UC-062, UC-063 |
| 004 — Customer Profile & Wallet | Profile, password, payment activity | UC-020 → UC-022, UC-063, UC-065 |
| 005 — Driver Onboarding | Driver register, documents, deposit, approval wait | UC-023 → UC-027, UC-063, UC-065 |
| 006 — Driver Workflow | Dashboard, assignment, delivery, completion | UC-028 → UC-033, UC-062 |
| 007 — Driver Financial | Earnings và withdrawal request/history | UC-034 → UC-035, UC-063 |
| 008 — Manager Driver Approval | Approval queue, detail, approve/reject, history | UC-036 → UC-039, UC-062, UC-063 |
| 009 — Withdrawal Processing | Admin-canonical withdrawal queue/detail/history | UC-040 → UC-042, UC-049, UC-062, UC-063 |
| 010 — Manager Disputes | Dispute list, detail, outcomes, history | UC-036, UC-043 → UC-044, UC-062, UC-065 |
| 011 — Admin List Pages | Orders, Drivers, Customers, Withdrawals lists | UC-046 → UC-049 |
| 012 — Admin Detail Pages | Order, Driver, Customer detail; user suspension | UC-050 → UC-052, UC-056, UC-062 |
| 013 — Admin Transactions | Transactions và reconciliation | UC-053, UC-062 |
| 014 — Commission Settings | Commission, pricing config và history | UC-054, UC-062 |
| 015 — Admin Dashboard | KPI, charts và operational tables | UC-045, UC-062 |
| 016 — Admin Reports | Financial, operations, Driver, Customer reports | UC-055 |
| 017 — Public Marketing | Sáu public marketing pages | UC-001 → UC-006 |
| 018 — Error Handling | Error pages, form errors, network/session recovery | UC-057 → UC-061 |

---

## Use Case ID Conventions

```text
Format: UC-XXX
  XXX = 3-digit sequential 001-065

Actor codes:
  G = Guest (visitor)
  C = Customer
  D = Driver
  M = Manager
  A = Admin
  S = System

Priority:
  P1 = Critical (must have)
  P2 = High (should have)
  P3 = Nice-to-have (could have, defer)
```

## Cross-references

- **18 Specs:** Chi tiết User Stories, FR/NFR và Acceptance Criteria xem trong
  `specs/001-auth-rbac/spec.md` → `specs/018-error-handling/spec.md`.
- **65 Screens:** UI catalog, route và trạng thái triển khai xem trong `docs/SCREEN_INVENTORY.md`.
- **Feature Tree:** Phân cấp scope xem trong `docs/diagrams/feature-tree.drawio`.
- **Source hierarchy:** `CONTEXT.md` → Constitution → Specs → Code.

## Document Maintenance

| Field | Value |
|-------|-------|
| Updated when | Có UC mới, thay đổi ownership/actor, spec hoặc screen route |
| Owner | TriNM2505 (Leader) |
| Format version | 1.0 — catalog matrix |
| Verification rule | Luôn giữ đúng 65 UC IDs liên tục và reference đủ 18 specs |
