# Move_home — Screen Inventory

> **Mục đích:** Catalog đầy đủ tất cả màn hình trong dự án.  
> **Audience:** Team developer + hội đồng final defense.  
> **Total screens:** 60 màn hình (5 roles × workflows + public + error pages).  
> **Last updated:** 2026-06-03  
> **Status legend:**
> - ✅ DONE — Đã implement + tested
> - 🔄 IN PROGRESS — Đang làm trong sprint hiện tại
> - 📋 PLANNED — Đã lên kế hoạch (chưa code)
> - 💡 IDEA — Concept, chưa confirm scope

---

# 1. TỔNG QUAN

## Phân bố theo role

| Role | Total screens | Status |
|------|---------------|--------|
| **Customer** | 16 | 0 done, 1 placeholder, 15 planned |
| **Driver** | 13 | 0 done, 1 placeholder, 12 planned |
| **Manager** | 9 | 0 done, 1 placeholder, 8 planned |
| **Admin** | 12 | 6 done, 6 planned |
| **Public marketing** | 6 | 0 done, 6 planned |
| **Auth (shared)** | 5 | 2 done, 3 planned |
| **Error/Utility** | 4 | 0 done, 4 planned |
| **TOTAL** | **65** | **8 done, 57 planned** |

## Phân kỳ theo Sprint

| Sprint | Focus | Screens added | Cumulative |
|--------|-------|---------------|------------|
| **Sprint 1** (DONE) | Auth + Admin MVP | +12 | 12 |
| **Sprint 2** | Customer Booking | +13 | 25 |
| **Sprint 3** | Driver Workflow | +13 | 38 |
| **Sprint 4** | Payment + Tracking | +7 | 45 |
| **Sprint 5** | Manager Workflow | +9 | 54 |
| **Sprint 6** | Public + Polish + Error | +11 | 65 |

## Định nghĩa "Màn hình"

Mỗi entry trong inventory là **1 URL route distinct** với:
- Layout riêng (header/footer/sidebar)
- Data display riêng (KPI, table, form, detail)
- User intent riêng

KHÔNG count:
- Modal popups (không phải route riêng)
- Toast notifications
- Loading states (sub-state của parent screen)

CÓ count:
- Multi-step forms (mỗi step = 1 screen)
- Empty states cho features chưa implement (placeholder URLs)
- Detail pages (vd: orders list vs order detail)

---

# 2. AUTH SCREENS (5 màn hình)

## 2.1 ✅ Đăng nhập — `/login.html`
- **Status:** DONE
- **File:** `frontend/pages/login.html`
- **Description:** Form đăng nhập với email + password
- **Roles:** All
- **Spec ref:** Spec #001 FR-016 → FR-030
- **Components:** card-elevated, text-input, btn-primary, link-primary
- **Key features:**
  - Email + password input
  - "Quên mật khẩu?" link
  - Link đến register
  - Role-based redirect sau login
  - Error handling: 401 Invalid, 403 EMAIL_NOT_VERIFIED, 423 ACCOUNT_LOCKED

## 2.2 ✅ Đăng ký — `/register.html`
- **Status:** DONE
- **File:** `frontend/pages/register.html`
- **Description:** Form đăng ký customer mới
- **Roles:** Customer (default)
- **Spec ref:** Spec #001 FR-001 → FR-015
- **Components:** card-elevated, text-input × 5, checkbox, btn-primary
- **Key features:**
  - 5 fields: họ tên, email, sđt, mật khẩu, xác nhận mật khẩu
  - Checkbox điều khoản
  - Client validation Vietnamese messages
  - Send email verification token sau register
  - Auto login + redirect customer/home

## 2.3 📋 Quên mật khẩu — `/forgot-password.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Nhập email để nhận link reset password
- **Roles:** All
- **Spec ref:** Spec #001 FR-031 (cần expand)
- **Components:** card-elevated, text-input, btn-primary
- **Key features:**
  - 1 field: email
  - Submit → backend generate reset token (1h expire) + send email
  - Success message: "Đã gửi email hướng dẫn đặt lại mật khẩu, vui lòng kiểm tra hộp thư"
  - Link back đến login

## 2.4 📋 Đặt lại mật khẩu — `/reset-password.html?token=XXX`
- **Status:** PLANNED (Sprint 2)
- **Description:** Form nhập mật khẩu mới sau khi click link reset
- **Roles:** All
- **Components:** card-elevated, text-input × 2, btn-primary
- **Key features:**
  - 2 fields: mật khẩu mới + xác nhận
  - URL chứa token reset (parse query string)
  - Backend verify token + update password hash
  - Redirect login với message "Đặt lại mật khẩu thành công"

## 2.5 📋 Xác thực email thành công — `/verify-email-success.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Landing page sau khi user click link verify email
- **Roles:** Customer, Driver
- **Components:** card-elevated, illustration, btn-primary
- **Key features:**
  - Success illustration + heading "Xác thực email thành công"
  - Body: "Tài khoản của bạn đã được kích hoạt"
  - Auto-redirect sau 5s hoặc button "Đến trang chủ"

---

# 3. CUSTOMER SCREENS (16 màn hình)

## 3.1 ✅ Trang chủ Khách hàng — `/customer/home.html`
- **Status:** DONE (placeholder)
- **File:** `frontend/pages/customer/home.html`
- **Description:** Landing sau login, hero + features + CTA đặt đơn
- **Components:** nav-bar, hero, promo-card × 3, category-button × 3
- **Key features:**
  - Hero "Chào mừng đến với Move_home"
  - 3 promo cards (Đặt nhanh, Tài xế xác minh, Giá minh bạch)
  - CTA "Đặt đơn ngay" → booking flow

## 3.2 📋 Đặt đơn Bước 1: Chọn xe — `/customer/booking-step1-vehicle.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Customer chọn loại xe phù hợp với khối lượng cần chuyển
- **Roles:** Customer
- **Spec ref:** Spec #002 (sắp viết) FR-100
- **Components:** vehicle-card × 3 (clickable), btn-primary "Tiếp tục"
- **Key features:**
  - 3 vehicle types:
    - Xe tải 500kg — "Phù hợp đồ ít, 1-2 người"
    - Xe tải 1 tấn — "Phù hợp gia đình nhỏ"
    - Xe tải 1.5 tấn — "Phù hợp gia đình lớn"
  - Mỗi card có: icon xe, tên, mô tả, giá khởi điểm
  - Click chọn → save state + navigate step 2
  - Progress indicator "Bước 1/6"

## 3.3 📋 Đặt đơn Bước 2: Điểm đón — `/customer/booking-step2-pickup.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Nhập điểm đón
- **Roles:** Customer
- **Components:** select-input (quận), text-input (địa chỉ), btn-primary
- **Key features:**
  - Dropdown 12 quận Hà Nội: Ba Đình, Hoàn Kiếm, Hai Bà Trưng, Đống Đa, Tây Hồ, Cầu Giấy, Thanh Xuân, Long Biên, Hà Đông, Hoàng Mai, Bắc Từ Liêm, Nam Từ Liêm
  - Text input địa chỉ chi tiết
  - Optional: số tầng + có thang máy
  - Optional: có ngõ nhỏ
  - Validation: required quận + địa chỉ
  - Progress "Bước 2/6"

## 3.4 📋 Đặt đơn Bước 3: Điểm trả — `/customer/booking-step3-dropoff.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Nhập điểm trả (giống step 2 nhưng cho destination)
- **Roles:** Customer
- **Components:** Tương tự step 2
- **Key features:**
  - Dropdown quận
  - Text input địa chỉ
  - Số tầng + thang máy + ngõ nhỏ
  - Progress "Bước 3/6"

## 3.5 📋 Đặt đơn Bước 4: Chi tiết — `/customer/booking-step4-details.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Thời gian + ghi chú + dịch vụ thêm
- **Roles:** Customer
- **Components:** datetime-picker, text-input (ghi chú), toggle-switch
- **Key features:**
  - Date + time picker (mặc định 1h sau hiện tại)
  - Số bốc xếp (0/1/2/3 porters)
  - Ghi chú thêm (textarea)
  - Toggle "Cần đóng gói thêm"
  - Progress "Bước 4/6"

## 3.6 📋 Đặt đơn Bước 5: Báo giá — `/customer/booking-step5-quote.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Hiển thị báo giá breakdown chi tiết
- **Roles:** Customer
- **Spec ref:** Spec #002 FR-110 pricing formula
- **Components:** card-elevated, breakdown-table, btn-large-rounded
- **Key features:**
  - Backend gọi OSRM để tính distance
  - Breakdown table:
    - Giá cơ bản (theo km)
    - Phụ phí giờ cao điểm (nếu 7-9h hoặc 17-19h)
    - Phụ phí ngõ nhỏ (nếu có)
    - Phụ phí tầng (nếu > 3 tầng không thang máy)
    - Phí bốc xếp (nếu chọn porter)
    - **Tổng cộng** (highlighted)
  - Button "Đặt đơn ngay" → step 6
  - Button "Quay lại" → step 4

## 3.7 📋 Đặt đơn Bước 6: Thanh toán — `/customer/booking-step6-payment.html`
- **Status:** PLANNED (Sprint 4)
- **Description:** Chọn phương thức thanh toán
- **Roles:** Customer
- **Components:** payment-method-card × 3, btn-primary
- **Key features:**
  - 3 options:
    - VNPay (Sprint 4 sandbox)
    - Tiền mặt khi giao
    - Ví Move_home (nếu có balance)
  - Select method + confirm
  - Backend tạo Order + Transaction
  - Submit → success page

## 3.8 📋 Đặt đơn thành công — `/customer/booking-success.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Confirmation page sau khi đặt đơn
- **Roles:** Customer
- **Components:** success-illustration, order-summary-card, btn-primary
- **Key features:**
  - Illustration "Đặt đơn thành công"
  - Hiển thị order code (vd: MH2026060300001)
  - Summary: pickup, dropoff, thời gian, tổng tiền
  - 2 actions:
    - "Xem chi tiết đơn" → order-detail
    - "Đặt thêm đơn" → booking-step1
  - Email confirmation gửi tới customer

## 3.9 📋 Đơn đang chờ — `/customer/my-orders-pending.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Danh sách đơn của customer đang chờ tài xế nhận
- **Roles:** Customer
- **Components:** order-card × N, pagination-bar
- **Key features:**
  - Filter pills: "Tất cả / Đang chờ / Đã nhận đơn"
  - List orders status PENDING + ACCEPTED của customer
  - Mỗi card: order code, pickup → dropoff, thời gian, tổng tiền, status badge
  - Click card → order-detail
  - Có thể hủy đơn nếu status = PENDING

## 3.10 📋 Đơn đang giao (Real-time) — `/customer/my-orders-active.html`
- **Status:** PLANNED (Sprint 4)
- **Description:** Tracking real-time đơn đang vận chuyển
- **Roles:** Customer
- **Components:** map-view, driver-info-card, status-timeline
- **Key features:**
  - Map view (Leaflet + OpenStreetMap)
  - Marker driver location (poll mỗi 5s)
  - Driver info card: tên + ảnh + sđt + biển số + rating
  - Status timeline: PENDING → ACCEPTED → IN_PROGRESS → COMPLETED
  - Button "Gọi tài xế" (call link)
  - Button "Nhắn tin tài xế" (chat - Sprint 6 nếu có)

## 3.11 📋 Lịch sử đơn — `/customer/my-orders-history.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Lịch sử đơn đã hoàn thành/hủy
- **Roles:** Customer
- **Components:** data-table với pagination
- **Key features:**
  - Table với headers: Mã đơn, Ngày, Pickup, Dropoff, Trạng thái, Tổng tiền
  - Filter pills: "Tất cả / Hoàn thành / Đã hủy"
  - Pagination 10/20/50 per page
  - Click row → order-detail
  - Export CSV (optional Sprint 6)

## 3.12 📋 Chi tiết đơn — `/customer/order-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 2)
- **Description:** Detail page 1 đơn cụ thể
- **Roles:** Customer
- **Components:** info-section, status-timeline, action-buttons
- **Key features:**
  - Section 1: Thông tin đơn (code, ngày, status)
  - Section 2: Tài xế (nếu đã accept) — info + rating + xe
  - Section 3: Pickup/Dropoff (address + map preview)
  - Section 4: Pricing breakdown
  - Section 5: Timeline events (PENDING at HH:mm → ACCEPTED at HH:mm → ...)
  - Actions theo status:
    - PENDING: button "Hủy đơn"
    - COMPLETED: button "Đánh giá tài xế"
    - DISPUTED: button "Xem khiếu nại"

## 3.13 📋 Đánh giá tài xế — `/customer/order-rate.html?id=XXX`
- **Status:** PLANNED (Sprint 4)
- **Description:** Form đánh giá tài xế sau khi đơn COMPLETED
- **Roles:** Customer
- **Components:** star-rating, textarea, btn-primary
- **Key features:**
  - Hiển thị info driver
  - Rating 1-5 sao
  - Optional textarea: nhận xét
  - Tags rating: "Đúng giờ / Lịch sự / Xe sạch / Hỗ trợ tốt"
  - Submit → update driver_profile.average_rating

## 3.14 📋 Thông tin cá nhân — `/customer/my-profile.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Xem thông tin tài khoản
- **Roles:** Customer
- **Components:** info-display, btn-secondary
- **Key features:**
  - Avatar + tên + email + sđt
  - Ngày tham gia, tổng số đơn đã đặt
  - Button "Chỉnh sửa" → my-profile-edit
  - Button "Đổi mật khẩu" → change-password

## 3.15 📋 Chỉnh sửa thông tin — `/customer/my-profile-edit.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Form edit profile
- **Roles:** Customer
- **Components:** text-input × N, btn-primary, btn-secondary
- **Key features:**
  - Editable: họ tên, sđt, avatar (upload)
  - Readonly: email (không cho đổi)
  - Buttons: "Lưu" + "Hủy"

## 3.16 📋 Đổi mật khẩu — `/customer/change-password.html`
- **Status:** PLANNED (Sprint 2)
- **Description:** Form change password (cần old password)
- **Roles:** All authenticated
- **Components:** text-input × 3, btn-primary
- **Key features:**
  - 3 fields: mật khẩu cũ, mật khẩu mới, xác nhận mật khẩu mới
  - Backend verify old password trước khi update
  - Success → logout + redirect login

## 3.17 📋 Ví của tôi — `/customer/my-wallet.html`
- **Status:** PLANNED (Sprint 4)
- **Description:** Xem balance + lịch sử transaction
- **Roles:** Customer
- **Components:** balance-card, transaction-table, pagination
- **Key features:**
  - Card balance: số dư hiện tại
  - Table transactions:
    - Loại (ORDER_PAYMENT / REFUND / DEPOSIT_TOP_UP)
    - Số tiền (positive/negative)
    - Order code (nếu có)
    - Ngày
  - Button "Nạp tiền" (optional Sprint 4+)

---

# 4. DRIVER SCREENS (13 màn hình)

## 4.1 📋 Đăng ký Tài xế Bước 1: Thông tin — `/driver/register-step1.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Thông tin cá nhân driver
- **Components:** text-input × 5, btn-primary
- **Key features:**
  - Họ tên, email, sđt, mật khẩu, ngày sinh, giới tính
  - Backend tạo user role=DRIVER status=PENDING_DOCUMENTS

## 4.2 📋 Đăng ký Bước 2: Giấy phép + Xe — `/driver/register-step2.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Upload giấy tờ + thông tin xe
- **Components:** file-upload × 2, text-input × 3, select, btn-primary
- **Key features:**
  - Upload ảnh giấy phép lái xe
  - Upload ảnh xe (front + plate)
  - Số bằng lái + hạng (B1/B2/C/D)
  - Biển số xe
  - Loại xe (Xe tải 500kg / 1 tấn / 1.5 tấn)
  - Năm sản xuất
  - Backend update driver_profile + status=PENDING_DEPOSIT

## 4.3 📋 Đăng ký Bước 3: Đặt cọc — `/driver/register-step3-deposit.html`
- **Status:** PLANNED (Sprint 3 + 4)
- **Description:** Form đặt cọc 3 triệu VND
- **Components:** info-card, payment-method, btn-primary
- **Key features:**
  - Hiển thị info "Cọc 3,000,000 VND hoàn lại khi nghỉ"
  - Chọn payment method (VNPay sandbox Sprint 4)
  - Submit → transaction DEPOSIT_TOP_UP + status=PENDING_APPROVAL

## 4.4 📋 Chờ duyệt — `/driver/pending-approval.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Landing page sau register, chờ manager duyệt
- **Components:** card-elevated, illustration, info
- **Key features:**
  - Illustration "Đang chờ duyệt"
  - Body: "Đơn đăng ký của bạn đã được gửi đến đội ngũ quản lý. Thời gian xét duyệt: 1-2 ngày làm việc."
  - Show next steps
  - Button "Đăng xuất"

## 4.5 ✅ Trang chủ Tài xế — `/driver/home.html`
- **Status:** DONE (placeholder)
- **File:** `frontend/pages/driver/home.html`
- **Description:** Dashboard tài xế (KPI cá nhân)
- **Components:** kpi-card × 4, promo-card × 3, btn-primary
- **Key features:**
  - 4 KPI: Đơn đang chờ nhận, Thu nhập hôm nay, Tổng đơn đã giao, Rating
  - Quick actions: "Xem đơn có sẵn", "Lịch sử đơn"
  - 3 promo cards

## 4.6 📋 Đơn có sẵn — `/driver/available-orders.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Danh sách đơn PENDING phù hợp vehicle_type của driver
- **Components:** order-card × N, filter-pills, refresh-btn
- **Key features:**
  - Filter pills: "Gần đây / Phù hợp xe / Giá cao"
  - Mỗi card: pickup → dropoff, distance, tổng tiền, customer name, scheduled_at
  - Button "Xem chi tiết" → driver-order-detail
  - Auto-refresh mỗi 30s (poll)

## 4.7 📋 Chi tiết đơn (Driver view) — `/driver/order-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 3)
- **Description:** Detail page trước khi accept
- **Components:** info-section × 3, map-preview, btn-primary, btn-secondary
- **Key features:**
  - Section: Customer info (tên, sđt, rating)
  - Section: Pickup + dropoff với map preview
  - Section: Pricing breakdown
  - Section: Notes của customer
  - Buttons:
    - "Nhận đơn" (primary forest green) → status ACCEPTED
    - "Bỏ qua" (secondary)
  - Lock concurrency: 2 driver cùng click → chỉ 1 thắng

## 4.8 📋 Đang giao — `/driver/in-progress.html`
- **Status:** PLANNED (Sprint 3 + 4)
- **Description:** Workflow update status đơn đang nhận
- **Components:** map-view, status-buttons, order-info
- **Key features:**
  - Map navigation đến pickup
  - Button update status theo flow:
    - ACCEPTED → "Đã đến điểm đón" → IN_PROGRESS
    - IN_PROGRESS → "Đã hoàn thành" → COMPLETED
  - Customer info luôn visible
  - Auto-send location (Sprint 4)
  - Button "Gọi khách"

## 4.9 📋 Lịch sử nhận đơn — `/driver/history.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Lịch sử đơn đã hoàn thành/hủy
- **Components:** data-table, pagination, filter
- **Key features:**
  - Tương tự customer history nhưng từ góc nhìn driver
  - Earnings cho mỗi đơn (commission đã trừ)
  - Filter status

## 4.10 📋 Thông tin Tài xế — `/driver/profile.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Profile + xe + giấy phép
- **Components:** info-section × 3, btn-secondary
- **Key features:**
  - Section: Cá nhân (có thể edit)
  - Section: Giấy phép (readonly, cần manager re-approve nếu đổi)
  - Section: Xe (readonly)
  - Performance metrics: tổng đơn, doanh thu, rating

## 4.11 📋 Thu nhập — `/driver/earnings.html`
- **Status:** PLANNED (Sprint 3)
- **Description:** Earnings breakdown
- **Components:** kpi-card × 3, chart, transaction-table
- **Key features:**
  - KPI: Tổng thu nhập, Hoa hồng đã trả, Số đơn
  - Chart 30 ngày thu nhập
  - Table transactions DRIVER_EARNING
  - Filter by month

## 4.12 📋 Yêu cầu rút tiền — `/driver/withdrawal-request.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Form request withdrawal
- **Components:** input, info-card, btn-primary
- **Key features:**
  - Hiển thị available balance
  - Input số tiền muốn rút (min 100k, max balance)
  - Bank account info (BIDV / Vietcombank / etc.)
  - Submit → withdrawal_request status PENDING
  - Manager approve sau

## 4.13 📋 Lịch sử rút tiền — `/driver/withdrawal-history.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Lịch sử các request rút tiền
- **Components:** data-table, status-badge
- **Key features:**
  - Table: Số tiền, Ngày yêu cầu, Trạng thái (PENDING/APPROVED/REJECTED), Ngày xử lý
  - Status badges tiếng Việt: "Đang chờ", "Đã duyệt", "Bị từ chối"

---

---

# 5. MANAGER SCREENS (9 màn hình)

## 5.1 ✅ Trang chủ Quản lý — `/manager/home.html`
- **Status:** DONE (placeholder)
- **File:** `frontend/pages/manager/home.html`
- **Description:** Dashboard quản lý với KPI + quick actions
- **Components:** nav-bar, kpi-card × 2, action-card × 2
- **Key features:**
  - 2 KPI: "Tài xế chờ duyệt", "Yêu cầu rút tiền"
  - 2 action cards với CTA "Xem danh sách"
  - Hero "Khu vực Quản lý"

## 5.2 📋 Duyệt tài xế — `/manager/driver-approvals.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Danh sách tài xế PENDING_APPROVAL
- **Components:** data-table, pagination, filter-pills
- **Key features:**
  - Table headers: Họ tên, Email, SĐT, Loại xe, Biển số, Ngày đăng ký, Trạng thái
  - Filter: "Tất cả / Chờ duyệt / Đã duyệt / Đã từ chối"
  - Row click → manager-driver-detail
  - Highlight rows PENDING_APPROVAL (yellow row)

## 5.3 📋 Chi tiết tài xế (Manager view) — `/manager/driver-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Detail page để duyệt/từ chối driver
- **Components:** info-section × 4, image-viewer, btn-primary, btn-danger
- **Key features:**
  - Section: Cá nhân (họ tên, email, sđt, ngày sinh)
  - Section: Giấy phép (số, hạng, ảnh xem được)
  - Section: Xe (biển số, loại, năm, ảnh xe)
  - Section: Đặt cọc (3,000,000 VND đã nạp lúc nào)
  - Actions:
    - Button "Duyệt" (forest green) → status ACTIVE
    - Button "Từ chối" (danger) → modal nhập lý do → status REJECTED + rejection_reason

## 5.4 📋 Lịch sử duyệt — `/manager/driver-rejected.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Lịch sử driver bị từ chối
- **Components:** data-table, pagination
- **Key features:**
  - Table: Họ tên, Email, Ngày từ chối, Người từ chối, Lý do
  - Filter by manager (nếu có nhiều manager)
  - Read-only history

## 5.5 📋 Rút tiền chờ duyệt — `/manager/withdrawal-pending.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Danh sách yêu cầu rút tiền PENDING
- **Components:** data-table, pagination
- **Key features:**
  - Table: Tài xế, Số tiền, Ngày yêu cầu, Số tài khoản ngân hàng, Trạng thái
  - Row click → manager-withdrawal-detail

## 5.6 📋 Chi tiết yêu cầu rút tiền — `/manager/withdrawal-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Detail page xử lý 1 withdrawal request
- **Components:** info-section × 3, btn-primary, btn-danger
- **Key features:**
  - Section: Tài xế (info + balance hiện tại + lịch sử rút)
  - Section: Số tiền + thông tin ngân hàng
  - Section: Verify (Manager check balance đủ không, BIDV/VCB info đúng không)
  - Actions:
    - "Duyệt" → status APPROVED + tạo transaction WITHDRAWAL
    - "Từ chối" → modal lý do → REJECTED

## 5.7 📋 Lịch sử rút tiền — `/manager/withdrawal-history.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Lịch sử đã duyệt/từ chối
- **Components:** data-table, pagination, filter
- **Key features:**
  - Table: Tài xế, Số tiền, Trạng thái, Người duyệt, Ngày xử lý
  - Filter status + filter date range

## 5.8 📋 Đơn khiếu nại — `/manager/disputes.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Danh sách đơn status DISPUTED
- **Components:** data-table, pagination
- **Key features:**
  - Table: Mã đơn, Customer, Driver, Lý do khiếu nại, Ngày, Số tiền
  - Row click → manager-dispute-detail

## 5.9 📋 Xử lý khiếu nại — `/manager/dispute-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Detail page để xử lý dispute
- **Components:** info-section × 4, textarea, btn-primary
- **Key features:**
  - Section: Order details
  - Section: Customer claim (lý do khiếu nại)
  - Section: Driver response
  - Section: Damage photos (nếu có)
  - Actions:
    - "Refund Customer" (tạo REFUND transaction)
    - "Deduct Driver" (tạo DAMAGE_DEDUCTION transaction)
    - "Close dispute - No action" (nếu phán quyết không có lỗi)
  - Textarea notes của manager

---

# 6. ADMIN SCREENS (12 màn hình)

## 6.1 ✅ Dashboard — `/admin/dashboard.html`
- **Status:** DONE
- **File:** `frontend/pages/admin/dashboard.html`
- **Description:** Dashboard tổng quan với KPI + charts
- **Spec ref:** Spec #028 đầy đủ
- **Components:** kpi-card × 4, line-chart (revenue), donut-chart (status), top-list, recent-table
- **Key features:**
  - 4 KPI cards (Tổng khách hàng, Tài xế hoạt động, Đơn tháng này, Doanh thu)
  - Revenue chart 30 days (dual line: doanh thu + hoa hồng)
  - Top 5 tài xế với rating
  - Status distribution donut
  - Recent orders table (10 mới nhất)

## 6.2 ✅ Đơn hàng — `/admin/orders.html`
- **Status:** DONE
- **File:** `frontend/pages/admin/orders.html`
- **Description:** Danh sách tất cả đơn hàng với pagination + filter
- **Components:** filter-pills, data-table, pagination
- **Key features:**
  - Filter: 7 statuses
  - Pagination 10/20/50/100
  - Server-side pagination
  - Row click → admin-order-detail (Sprint 5)

## 6.3 ✅ Tài xế — `/admin/drivers.html`
- **Status:** DONE
- **File:** `frontend/pages/admin/drivers.html`
- **Description:** Danh sách tài xế với highlight PENDING_APPROVAL
- **Components:** filter-pills, data-table, pagination
- **Key features:**
  - Filter: 4 statuses
  - Client-side pagination
  - Rating "★ 4.7/5"
  - PENDING row highlight vàng

## 6.4 ✅ Khách hàng — `/admin/customers.html`
- **Status:** DONE
- **File:** `frontend/pages/admin/customers.html`
- **Description:** Danh sách khách hàng
- **Components:** filter-pills, data-table, pagination
- **Key features:**
  - Filter: 3 statuses
  - Email verification ✅/❌
  - Số đơn đã đặt

## 6.5 ✅ Rút tiền — `/admin/withdrawals.html` (placeholder)
- **Status:** DONE (placeholder Sprint 5)
- **File:** `frontend/pages/admin/withdrawals.html`
- **Description:** Empty state cho Sprint 5
- **Key features:** Empty state với illustration

## 6.6 ✅ Cấu hình — `/admin/settings.html` (placeholder)
- **Status:** DONE (placeholder Sprint 5+)
- **File:** `frontend/pages/admin/settings.html`
- **Description:** Empty state cho Sprint 5+
- **Key features:** Empty state với illustration

## 6.7 📋 Chi tiết đơn (Admin view) — `/admin/order-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Detail page 1 đơn từ góc nhìn admin
- **Components:** info-section × 5, timeline, btn-secondary
- **Key features:**
  - Tất cả info: customer, driver, pickup/dropoff, pricing, timeline
  - Transactions liên quan (ORDER_PAYMENT, PLATFORM_FEE, DRIVER_EARNING)
  - Status timeline events
  - Actions: "Cancel order" (force admin action), "View customer/driver profile"

## 6.8 📋 Chi tiết tài xế (Admin view) — `/admin/driver-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Full profile + analytics của 1 tài xế
- **Components:** info-section × 4, chart, table
- **Key features:**
  - Profile + giấy phép + xe
  - Performance: tổng đơn, doanh thu, rating
  - Chart earnings 30/90 days
  - Lịch sử đơn của driver (paginated)
  - Lịch sử transactions
  - Actions: "Suspend" / "Re-approve"

## 6.9 📋 Chi tiết khách hàng (Admin view) — `/admin/customer-detail.html?id=XXX`
- **Status:** PLANNED (Sprint 5)
- **Description:** Profile + history của 1 khách
- **Components:** info-section × 3, table
- **Key features:**
  - Profile (email, sđt, ngày tham gia)
  - Tổng số đơn + tổng chi tiêu
  - Lịch sử orders (paginated)
  - Lịch sử transactions
  - Actions: "Suspend" (nếu spam/abuse)

## 6.10 📋 Giao dịch hệ thống — `/admin/transactions.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Lịch sử transaction toàn hệ thống
- **Components:** data-table, pagination, filter
- **Key features:**
  - Filter by type (7 types) + filter by user role
  - Table: User, Order, Type, Amount, Date
  - Export CSV (Sprint 6)
  - Financial reconciliation reference

## 6.11 📋 Cấu hình Commission — `/admin/commission-settings.html`
- **Status:** PLANNED (Sprint 5)
- **Description:** Form cấu hình tỷ lệ commission + surcharges
- **Components:** form, btn-primary, info-card
- **Key features:**
  - Commission rate (default 0.3000 = 30%)
  - Peak hour surcharge rate (default 0.30)
  - Peak hours config (default 07:00-09:00, 17:00-19:00)
  - Alley surcharge fixed amount (default 200,000 VND)
  - Floor surcharge per floor (default 50,000 VND)
  - Porter fee (default 300,000 VND)
  - Save → backend update settings (chỉ ảnh hưởng đơn mới, snapshot pattern)

## 6.12 📋 Báo cáo Analytics — `/admin/reports.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Advanced analytics + reports
- **Components:** chart × N, date-range-picker, export-btn
- **Key features:**
  - Date range picker
  - Charts:
    - Revenue by month
    - Orders by district (heatmap)
    - Driver utilization
    - Customer retention
  - Export PDF report (Sprint 6+ optional)

---

# 7. PUBLIC MARKETING SCREENS (6 màn hình)

## 7.1 📋 Trang chủ Marketing — `/index.html` (public)
- **Status:** PLANNED (Sprint 6)
- **Description:** Public landing page với hero + features
- **Roles:** Visitor (chưa đăng nhập)
- **Components:** hero-band-light, request-form-card, promo-card × 6, footer
- **Key features:**
  - Hero "Chuyển nhà dễ dàng, an toàn"
  - Booking form preview (nhập pickup → CTA "Đăng ký để đặt đơn")
  - Section "Cách thức hoạt động" (4 steps)
  - Section "Tại sao chọn Move_home" (6 features)
  - Section "Khách hàng nói gì" (testimonials)
  - Section "Tải app" (app download pills)
  - Footer với links

⚠️ Note: Cần phân biệt với `/customer/home.html` (sau khi login). Public homepage này thay thế redirect logic của `frontend/index.html` cho visitor chưa login.

## 7.2 📋 Về chúng tôi — `/about.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Story + mission + team
- **Components:** content-section × 4
- **Key features:**
  - Giới thiệu Move_home
  - Mission + vision
  - Team (mock photos)
  - Milestones timeline

## 7.3 📋 Cách thức hoạt động — `/how-it-works.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Step-by-step user guide
- **Components:** step-card × 4, video-embed
- **Key features:**
  - 4 steps cho Customer:
    1. Đăng ký tài khoản
    2. Chọn loại xe + điểm đón/trả
    3. Đợi tài xế nhận đơn
    4. Đánh giá sau khi hoàn thành
  - Tương tự cho Driver (toggle)

## 7.4 📋 Bảng giá — `/pricing.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Pricing reference cho visitor
- **Components:** pricing-tier × 3, comparison-table
- **Key features:**
  - 3 tiers vehicle với giá khởi điểm
  - Comparison table: Move_home vs đối thủ
  - Note: "Giá có thể thay đổi theo giờ cao điểm, ngõ nhỏ, tầng cao"
  - CTA "Đăng ký để xem báo giá chính xác"

## 7.5 📋 Liên hệ + FAQ — `/contact.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Contact form + FAQ accordion
- **Components:** contact-form, faq-row × N
- **Key features:**
  - Form gửi email contact
  - FAQ accordion (10-15 câu hỏi thường gặp)
  - Footer info: địa chỉ, sđt hotline, email

## 7.6 📋 Điều khoản + Privacy — `/terms.html` + `/privacy.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Legal pages
- **Components:** prose-content
- **Key features:**
  - Long-form text legal content
  - Sections + headings rõ ràng

⚠️ Tính là 1 entry vì content tương tự (có thể tách 2 routes).

---

# 8. ERROR + UTILITY SCREENS (4 màn hình)

## 8.1 📋 404 Not Found — `/404.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Page when URL không tồn tại
- **Components:** illustration, btn-primary
- **Key features:**
  - Illustration 404
  - Text "Không tìm thấy trang"
  - Button "Về trang chủ"

## 8.2 📋 500 Server Error — `/500.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Backend lỗi unexpected
- **Components:** illustration, info, btn-primary
- **Key features:**
  - "Lỗi hệ thống, vui lòng thử lại"
  - Mã lỗi cho support reference
  - Button "Thử lại" + "Liên hệ hỗ trợ"

## 8.3 📋 403 Forbidden — `/403.html`
- **Status:** PLANNED (Sprint 6)
- **Description:** Khi user access page không đủ quyền
- **Components:** illustration, info
- **Key features:**
  - "Bạn không có quyền truy cập trang này"
  - Show current role + link back home theo role

## 8.4 📋 Session Expired — `/session-expired.html`
- **Status:** PLANNED (Sprint 4 — link với JWT refresh)
- **Description:** Auto-redirect khi token hết hạn + reuse detection
- **Components:** illustration, btn-primary
- **Key features:**
  - "Phiên đăng nhập hết hạn"
  - Button "Đăng nhập lại"
  - Auto-clear localStorage

---

# 9. TRACKING TỪNG SPRINT

## Sprint 1 (DONE — v0.8 + v0.9)

**12 screens delivered:**
- ✅ login, register
- ✅ index (redirect)
- ✅ customer/home, driver/home, manager/home
- ✅ admin/dashboard, orders, drivers, customers, withdrawals, settings

## Sprint 2 — Customer Booking (PLANNED)

**+13 screens:**
- 📋 forgot-password, reset-password, verify-email-success (3 auth)
- 📋 booking-step1-vehicle → step5-quote (5 booking)
- 📋 booking-success
- 📋 my-orders-pending, my-orders-history (2 list)
- 📋 order-detail (customer view)
- 📋 my-profile, my-profile-edit, change-password (3 profile)

**Total cumulative: 25**

## Sprint 3 — Driver Workflow (PLANNED)

**+13 screens:**
- 📋 driver-register-step1, step2, step3-deposit (3 onboarding)
- 📋 driver/pending-approval
- 📋 driver/available-orders, order-detail, in-progress, history (4 workflow)
- 📋 driver/profile, earnings (2 profile)
- 📋 driver/withdrawal-request, withdrawal-history (2 financial — Sprint 5 actually)
- 📋 (-1 dispose to Sprint 5)

**Total cumulative: 38**

## Sprint 4 — Payment + Tracking (PLANNED)

**+7 screens:**
- 📋 booking-step6-payment
- 📋 my-orders-active (real-time tracking)
- 📋 order-rate
- 📋 my-wallet
- 📋 session-expired (link với JWT refresh)
- 📋 (driver in-progress updated với location tracking)
- 📋 2 more details/sub-screens

**Total cumulative: 45**

## Sprint 5 — Manager Workflow (PLANNED)

**+9 screens:**
- 📋 manager/driver-approvals, driver-detail, driver-rejected (3)
- 📋 manager/withdrawal-pending, withdrawal-detail, withdrawal-history (3)
- 📋 manager/disputes, dispute-detail (2)
- 📋 admin/order-detail, driver-detail, customer-detail, transactions, commission-settings (5 admin detail — count 1 as bundle)

**Total cumulative: 54**

## Sprint 6 — Polish + Public + Errors (PLANNED)

**+11 screens:**
- 📋 Public: index, about, how-it-works, pricing, contact, terms (6)
- 📋 Errors: 404, 500, 403 (3)
- 📋 Admin reports (1)
- 📋 Polish + email service real (1 extra)

**Total cumulative: 65**

---

# 10. NOTES & DECISIONS

## 10.1 Tách "step" thành màn hình riêng

**Decision:** Multi-step forms (booking, driver register) đếm mỗi step = 1 screen.

**Rationale:**
- Mỗi step có URL riêng (back/forward browser work)
- State save in localStorage hoặc backend draft
- UX clearer cho user — biết đang ở step nào
- SEO friendly nếu có (mobile thì share link step được)

## 10.2 Modal vs Screen

**Modal KHÔNG đếm là screen** — chỉ là sub-UI của parent screen. Vd:
- Confirm dialog xóa đơn = modal, không là screen
- "Nhập lý do từ chối" trong driver-detail = modal

**Exception:** Nếu modal có logic phức tạp (form 5+ fields, multi-step trong modal) → có thể convert sang screen riêng để dễ dev/test.

## 10.3 Customer/Driver/Manager landing đã có placeholder

3 file `customer/home.html`, `driver/home.html`, `manager/home.html` đã build trong Sprint 1 nhưng chỉ là **landing placeholders** với CTA dẫn đến features chưa implement.

**Sprint 2-5 sẽ:**
- Update content thực (KPI thực, data thực)
- Add real action links (thay vì href="#")

→ Vẫn count là DONE.

## 10.4 Responsive — Mobile vs Desktop

Tất cả screens cần responsive 3 breakpoints (mobile <600px / tablet 600-1119 / desktop ≥1120). KHÔNG count "mobile version" + "desktop version" là 2 screens — chỉ 1.

## 10.5 i18n — Tiếng Việt only

Sprint 1-6 chỉ Vietnamese. Sprint 7+ (sau capstone) có thể thêm English. KHÔNG count i18n version là screen riêng.

## 10.6 Email templates

Email templates (verify email, reset password, order confirmation) KHÔNG count là screen vì:
- Render qua email client, không phải web
- Backend Spring Mail template
- Đếm riêng nếu cần là "email screens"

→ ~5 email templates Sprint 6+ (chưa count vào 65).

---

# 11. ESTIMATION TỔNG

## Effort estimate (theo developer experience)

Mỗi screen mất:
- **Simple** (empty state, landing placeholder): ~1-2 giờ
- **Medium** (list with table + pagination): ~3-5 giờ
- **Complex** (multi-step form, real-time tracking): ~6-12 giờ

Phân bố 65 screens:
- Simple: ~15 screens × 1.5h = 22.5h
- Medium: ~35 screens × 4h = 140h
- Complex: ~15 screens × 8h = 120h

**Total: ~282 giờ frontend work** (5-6 tuần với 1 dev fulltime)

→ Team 5 người, chia 2-3 frontend dev, có thể hoàn thành trong Sprint timeline.

## Quality bar

Mỗi screen cần:
- ✅ HTML + CSS theo DESIGN.md (forest green + amber, Be Vietnam Pro)
- ✅ Vietnamese diacritics đầy đủ
- ✅ Responsive 3 breakpoints
- ✅ Loading + error + empty states
- ✅ Accessibility (aria labels, keyboard nav)
- ✅ Backend integration (API call + error handling)

---

# 12. FILE END

**Generated:** 2026-06-03  
**Author:** TriNM2505 (leader)  
**Review with team:** TBD  
**Reference for:** Spec writers, Frontend devs, QA team, Final defense

> Khi thầy hỏi "Em có bao nhiêu màn hình?" → Show file này. Khi hỏi "Mỗi sprint làm gì?" → Show section 9 (per-sprint tracking). Khi hỏi "Sao em design như vậy?" → Show section 10 (decisions + rationale).