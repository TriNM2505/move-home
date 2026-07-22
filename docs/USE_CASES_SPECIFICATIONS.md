# Move_home — Use Case Specifications (15 Critical UCs)

> **Mục đích:** Chi tiết spec cho 15 use cases critical nhất của Move_home.
> Source of Truth cho QA test plan và final defense Q&A.
>
> **Format:** Use Case Specification Table (FPT SWP standard)
> **Total UCs detailed:** 15 / 73 (overview 26 spec xem `docs/USE_CASES_CATALOG.md`)
> **Version:** 1.0
> **Last updated:** 2026-06-09

---

> ℹ️ **Phạm vi:** tài liệu này chi tiết 15/65 UC critical của **18 spec đầu (001–018)**. Tính năng
> **019–026** (chat, notifications, ví khách, hoàn cọc hủy đơn, incident, blog[BLOCKED], audit log,
> ratings) dùng danh sách UC tạm UC-066→UC-073 ở `docs/USE_CASES_CATALOG.md` (Section 7); sẽ viết chi
> tiết UC ở pha sau nếu cần cho defense.

---

## Table of Contents

1. [UC-007: Đăng ký tài khoản Customer](#uc-007)
2. [UC-009: Đăng nhập 4 vai trò](#uc-009)
3. [UC-012: Đặt đơn chuyển nhà 6 bước](#uc-012)
4. [UC-016: Theo dõi đơn đang giao real-time](#uc-016)
5. [UC-019: Đánh giá tài xế](#uc-019)
6. [UC-024: Upload giấy tờ Driver](#uc-024)
7. [UC-025: Đặt cọc 3.000.000 VND](#uc-025)
8. [UC-030: Nhận hoặc từ chối đơn có lock](#uc-030)
9. [UC-032: Hoàn thành đơn và tính earnings](#uc-032)
10. [UC-035: Yêu cầu rút tiền Driver](#uc-035)
11. [UC-037: Manager duyệt tài xế](#uc-037)
12. [UC-040: Xử lý withdrawal](#uc-040)
13. [UC-043: Xử lý khiếu nại với 3 outcomes](#uc-043)
14. [UC-045: Admin Dashboard với KPI](#uc-045)
15. [UC-054: Cấu hình Commission Settings](#uc-054)

---

## Quy ước chung

| Quy ước | Mô tả |
|---------|-------|
| Trạng thái lỗi | REST API dùng `{ "error_code", "message", "details" }` theo ES-04 |
| Validation | Input sai trả HTTP 422 với lỗi tiếng Việt theo field |
| Phân quyền | Thiếu JWT trả 401; có JWT nhưng sai role trả 403 theo HR-10 |
| State transition | Transition không hợp lệ trả HTTP 409 và không mutate theo HR-05 |
| Audit | State và money mutation phải ghi audit trong cùng transaction theo HR-13 |
| Email | Email chạy async, lỗi email không rollback giao dịch chính theo HR-11 |
| Tiền tệ | VND nguyên đồng, `BigDecimal`/`NUMERIC(15,0)`, không dùng float theo AC-08 |
| Thời gian | Lưu UTC, hiển thị `Asia/Ho_Chi_Minh` theo AC-07 |
| UI | Có Loading/Empty/Error states và tiếng Việt đầy đủ dấu theo AC-16, HR-20 |

---

<a id="uc-007"></a>
### UC-007: Đăng ký tài khoản Customer

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-007 |
| **Use Case Name** | Đăng ký tài khoản Customer |
| **Actor** | Primary: Guest; Secondary: Move_home System, Gmail SMTP |
| **Spec Reference** | `specs/001-auth-rbac/spec.md` — US1, FR-001 → FR-010 |
| **Priority** | P1 — Critical |
| **Preconditions** | - Guest chưa đăng nhập.<br>- Email, username và số điện thoại chưa thuộc tài khoản active khác.<br>- Guest có thể truy cập public route `register.html`. |
| **Trigger** | Guest nhập thông tin và nhấn **Đăng ký**. |
| **Main Flow (Happy Path)** | 1. Hệ thống hiển thị form gồm username, họ tên, email, số điện thoại, mật khẩu, xác nhận mật khẩu và đồng ý điều khoản.<br>2. Guest nhập dữ liệu và submit form.<br>3. Frontend validate định dạng, độ dài, password strength và confirm password.<br>4. Backend chuẩn hóa email lowercase và số điện thoại về định dạng canonical.<br>5. Backend kiểm tra uniqueness theo thứ tự username, email, phone.<br>6. Backend hash mật khẩu bằng BCrypt và tạo Customer ở trạng thái `PENDING_VERIFY`.<br>7. Backend tạo email verification token ngẫu nhiên, TTL 24 giờ.<br>8. Backend ghi audit event `REGISTER` với actor và request metadata an toàn.<br>9. Backend commit transaction và enqueue email xác thực bất đồng bộ.<br>10. Frontend hiển thị thông báo yêu cầu Customer kiểm tra email. |
| **Alternative Flows** | **A1 — Dữ liệu không hợp lệ:** Frontend/backend trả lỗi field; backend trả HTTP 422 và không tạo user.<br>**A2 — Username/email/phone đã tồn tại:** Backend trả HTTP 409 `CONFLICT` cho field trùng đầu tiên.<br>**A3 — Chưa đồng ý điều khoản:** Backend trả HTTP 422 cho `terms_accepted`.<br>**A4 — Email service lỗi:** Tài khoản vẫn được tạo; email được retry async, không rollback.<br>**A5 — Rate limit đăng ký:** Hệ thống trả HTTP 429 và `retry_after_seconds`. |
| **Postconditions** | - Customer tồn tại với role `CUSTOMER`, status `PENDING_VERIFY` và password hash BCrypt.<br>- Verification token hợp lệ được lưu.<br>- Audit đăng ký tồn tại.<br>- Email xác thực được enqueue. |
| **Business Rules** | - Password không được lưu/log plaintext — HR-02.<br>- Email lỗi không rollback transaction — HR-11.<br>- Auth endpoints public nhưng không lộ dữ liệu nhạy cảm — HR-17.<br>- Login/register áp dụng rate limit — HR-16.<br>- UI và email dùng tiếng Việt đầy đủ dấu — HR-20. |
| **Non-Functional** | - Performance: register API hoàn tất nhanh, không chờ SMTP; email block main thread tối đa khoảng 5 ms.<br>- Security: BCrypt cost phù hợp; token ngẫu nhiên; không log password/token.<br>- Reliability: transaction tạo user, token và audit phải atomic.<br>- UX: hiển thị tất cả field errors rõ ràng và giữ dữ liệu không nhạy cảm đã nhập. |
| **Related UCs** | - Include: UC-008 Xác thực email.<br>- Related: UC-009 Đăng nhập; UC-010 Khôi phục mật khẩu; UC-063 Email notifications. |

---

<a id="uc-009"></a>
### UC-009: Đăng nhập 4 vai trò

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-009 |
| **Use Case Name** | Đăng nhập và điều hướng theo 4 vai trò |
| **Actor** | Primary: Customer, Driver, Manager, Admin; Secondary: Move_home System |
| **Spec Reference** | `specs/001-auth-rbac/spec.md` — US3, US5, FR-017 → FR-035 |
| **Priority** | P1 — Critical |
| **Preconditions** | - User đã có tài khoản.<br>- Email đã xác thực đối với Customer/Driver.<br>- Driver phải `ACTIVE` để vào workflow; Staff được tạo bởi Admin.<br>- Tài khoản không bị suspended hoặc deleted. |
| **Trigger** | User nhập identifier/password và nhấn **Đăng nhập**. |
| **Main Flow (Happy Path)** | 1. Frontend submit identifier và password qua HTTPS.<br>2. Backend áp dụng rate limit theo IP: tối đa 5 attempt trong 15 phút.<br>3. Backend tìm Customer theo username hoặc Driver/Manager/Admin theo email.<br>4. Backend kiểm tra trạng thái tài khoản và `locked_until` trước khi verify password.<br>5. Backend verify password bằng BCrypt.<br>6. Backend reset `failed_login_count` và `locked_until` trong transaction.<br>7. Backend phát access token 15 phút và refresh token rotation 7 ngày.<br>8. Backend lưu refresh token server-side, đặt secure cookie và ghi audit login.<br>9. Backend trả role cùng allowed redirect.<br>10. Frontend điều hướng đến home/dashboard đúng vai trò. |
| **Alternative Flows** | **A1 — Sai credentials:** Tăng failed count atomic; trả HTTP 401 và số lần còn lại.<br>**A2 — Sai 5 lần:** Set lock 15 phút và trả HTTP 423 `ACCOUNT_LOCKED_NOW`.<br>**A3 — Tài khoản đang lock:** Trả HTTP 423 ngay, không verify password.<br>**A4 — Chưa xác thực email:** Trả HTTP 403 `EMAIL_NOT_VERIFIED` với khả năng resend.<br>**A5 — Driver chưa ACTIVE:** Trả HTTP 403 và `current_step` onboarding.<br>**A6 — Staff phải đổi mật khẩu lần đầu:** Chỉ cấp token scope `CHANGE_PASSWORD_ONLY`. |
| **Postconditions** | - Session hợp lệ được tạo và refresh token được lưu/rotate.<br>- Failed login counters được reset khi login thành công.<br>- Audit login thành công hoặc security event được ghi.<br>- User vào đúng route theo role. |
| **Business Rules** | - Password BCrypt, không plaintext — HR-02.<br>- Sai role/không đủ quyền trả 403 — HR-10.<br>- Login rate limit và account lockout bắt buộc — HR-16.<br>- JWT secret lấy từ environment — HR-01.<br>- Refresh reuse detection phải vô hiệu session liên quan. |
| **Non-Functional** | - Performance: login thông thường phản hồi dưới 1 giây ở p90.<br>- Security: cookie HttpOnly/Secure/SameSite; không phân biệt user không tồn tại và sai password.<br>- Concurrency: tăng failed count và reset lock atomic.<br>- UX: thông báo lỗi tiếng Việt actionable nhưng không hỗ trợ user enumeration. |
| **Related UCs** | - Related: UC-007 Đăng ký; UC-008 Xác thực email; UC-010/UC-011 Reset password; UC-060 Session Expired. |

---

<a id="uc-012"></a>
### UC-012: Đặt đơn chuyển nhà 6 bước

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-012 |
| **Use Case Name** | Bắt đầu và hoàn tất đặt đơn chuyển nhà 6 bước |
| **Actor** | Primary: Customer; Secondary: Move_home System, OSRM, VNPay, Gmail SMTP |
| **Spec Reference** | `specs/002-customer-booking/spec.md` — US1 → US7, FR-001 → FR-042 |
| **Priority** | P1 — Core Booking |
| **Preconditions** | - Customer đã đăng nhập, email verified và status `ACTIVE`.<br>- Pricing configuration và vehicle types khả dụng.<br>- Customer chưa confirm draft này trước đó. |
| **Trigger** | Customer nhấn **Đặt đơn ngay** từ Customer home. |
| **Main Flow (Happy Path)** | 1. Step 1: Customer chọn loại xe; hệ thống tạo booking draft thuộc Customer.<br>2. Step 2: Customer nhập quận, địa chỉ, tầng, thang máy và ngõ nhỏ tại điểm đón.<br>3. Step 3: Customer nhập thông tin điểm trả theo cùng validation.<br>4. Step 4: Customer chọn lịch hẹn, porter, dịch vụ thêm và ghi chú.<br>5. Backend lưu từng bước, current step và ownership để Customer resume an toàn.<br>6. Step 5: Backend gọi OSRM, áp dụng pricing snapshot và trả báo giá itemized.<br>7. Customer xem base fare, peak, alley, floor, porter, distance, duration và tổng tiền.<br>8. Step 6: Customer xác nhận booking với Idempotency-Key.<br>9. Backend lock draft, validate lại toàn bộ, tạo order code và order `PENDING_PAYMENT` trong transaction.<br>10. Payment module tạo VNPay URL cọc; hệ thống enqueue email và hiển thị booking success. |
| **Alternative Flows** | **A1 — Field sai ở bất kỳ bước:** Trả HTTP 422 với tất cả lỗi; không partial update.<br>**A2 — OSRM lỗi:** Dùng district fallback, gắn nhãn “ước tính” theo AC-06.<br>**A3 — Draft hết hạn 24 giờ:** Trả HTTP 410 `DRAFT_EXPIRED` và yêu cầu tạo draft mới.<br>**A4 — Double-click confirm:** Idempotency replay cùng response; không tạo order trùng.<br>**A5 — Draft bị sửa đồng thời:** Row lock/version bảo vệ; request stale nhận conflict.<br>**A6 — VNPay intent tạm lỗi:** Giữ order `PENDING_PAYMENT`, hiển thị trạng thái retry rõ ràng. |
| **Postconditions** | - Draft được đánh dấu confirmed/không còn dùng để tạo order lần hai.<br>- Một order duy nhất ở `PENDING_PAYMENT` với pricing snapshot bất biến.<br>- VNPay deposit intent/URL được tạo hoặc trạng thái retry được ghi.<br>- Audit/order event và email confirmation được enqueue. |
| **Business Rules** | - Chỉ VNPay IPN hợp lệ cập nhật payment state — HR-03, HR-04.<br>- Invalid state transition trả 409 — HR-05.<br>- Order payment timeout áp dụng scheduled cleanup/cancel — HR-09.<br>- Audit mọi state change — HR-13.<br>- Money dùng VND nguyên đồng — AC-08.<br>- OSRM phải có fallback — AC-06. |
| **Non-Functional** | - Performance: quote API mục tiêu p90 dưới 3 giây; confirm transaction ngắn và có lock.<br>- Security: Customer chỉ đọc/sửa draft của chính mình; route yêu cầu JWT.<br>- Reliability: confirm atomic, idempotent và không duplicate order code.<br>- UX: progress 1/6 → 6/6, giữ draft qua refresh, hiển thị breakdown dễ hiểu. |
| **Related UCs** | - Include: UC-013 Xem báo giá và chấp nhận.<br>- Related: UC-014 Resume/hủy draft; UC-015 Đơn đang chờ; UC-064 OSRM; UC-065 VNPay integration. |

---

<a id="uc-016"></a>
### UC-016: Theo dõi đơn đang giao real-time

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-016 |
| **Use Case Name** | Theo dõi đơn đang giao và vị trí Driver |
| **Actor** | Primary: Customer; Secondary: Driver, Move_home System |
| **Spec Reference** | `specs/003-customer-orders/spec.md` — US2, US8, FR-007 → FR-013 |
| **Priority** | P1 — Critical |
| **Preconditions** | - Customer đã đăng nhập và sở hữu order.<br>- Order đang ở trạng thái active có Driver.<br>- Driver có thể gửi location update trong workflow. |
| **Trigger** | Customer mở `my-orders-active.html` hoặc chọn một order active. |
| **Main Flow (Happy Path)** | 1. Frontend gọi API danh sách active orders của Customer.<br>2. Customer chọn order cần theo dõi.<br>3. Backend kiểm tra ownership và trả order summary, Driver summary, timeline.<br>4. Frontend render tuyến đường, Driver card và status timeline.<br>5. Frontend gọi location endpoint theo chu kỳ polling 5 giây.<br>6. Backend trả vị trí Driver gần nhất cùng thời điểm cập nhật.<br>7. Frontend cập nhật marker và tính/hiển thị ETA khi location còn mới.<br>8. Customer có thể dùng hành động gọi Driver để phối hợp.<br>9. Khi state thay đổi, timeline và allowed actions được refresh.<br>10. Polling dừng khi order terminal hoặc Customer rời trang. |
| **Alternative Flows** | **A1 — Chưa có location:** Hiển thị Driver info/timeline và thông báo chưa có vị trí.<br>**A2 — Location stale:** Không trình bày như real-time; hiển thị thời điểm cập nhật cuối.<br>**A3 — Network fail:** Giữ dữ liệu gần nhất, hiển thị retry và tiếp tục polling có backoff.<br>**A4 — Order không thuộc Customer:** Trả HTTP 403 `ORDER_OWNERSHIP_REQUIRED`.<br>**A5 — Order vừa terminal:** Chuyển UI sang trạng thái hoàn thành/hủy và dừng polling. |
| **Postconditions** | - Customer thấy trạng thái và location mới nhất được phép xem.<br>- Không có mutation order từ màn tracking.<br>- Polling được dừng đúng lúc, không tạo request vô hạn. |
| **Business Rules** | - Customer chỉ xem order của mình — HR-10.<br>- State hiển thị phải theo canonical state machine — HR-05.<br>- Location chỉ được Driver owner cập nhật.<br>- Baseline real-time là polling 5 giây; WebSocket không bắt buộc. |
| **Non-Functional** | - Performance: polling response nhẹ; không trả history location không giới hạn.<br>- Security: không lộ vị trí Driver ngoài active order thuộc Customer.<br>- Availability: network lỗi có recovery, không làm hỏng toàn page.<br>- UX: marker, stale indicator, timeline và call action rõ ràng. |
| **Related UCs** | - Related: UC-015 Đơn đang chờ; UC-018 Chi tiết đơn; UC-031 Driver vận chuyển; UC-063 Notifications. |

---

<a id="uc-019"></a>
### UC-019: Đánh giá tài xế

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-019 |
| **Use Case Name** | Đánh giá Driver sau khi order hoàn thành |
| **Actor** | Primary: Customer; Secondary: Driver, Move_home System |
| **Spec Reference** | `specs/003-customer-orders/spec.md` — US6, FR-033 → FR-039 |
| **Priority** | P1 — Customer Quality Feedback |
| **Preconditions** | - Customer đã đăng nhập và sở hữu order.<br>- Order ở trạng thái `COMPLETED` và có Driver.<br>- Chưa có rating cho order.<br>- Chưa quá 2 giờ từ `completed_at`. |
| **Trigger** | Customer nhấn **Đánh giá tài xế** từ order detail/history. |
| **Main Flow (Happy Path)** | 1. Frontend mở form rating cho order cụ thể.<br>2. Backend kiểm tra ownership và rating eligibility.<br>3. Frontend hiển thị Driver summary, 1-5 sao, tags và comment optional.<br>4. Customer chọn số sao và có thể chọn tags/nhập nhận xét.<br>5. Frontend validate rating bắt buộc và giới hạn comment.<br>6. Backend lock/kiểm tra lại order và uniqueness rating.<br>7. Backend tạo một `order_rating` gắn order, Customer và Driver.<br>8. Backend cập nhật aggregate rating Driver theo contract.<br>9. Backend ghi audit/event rating.<br>10. Frontend hiển thị success state và rating mới. |
| **Alternative Flows** | **A1 — Quá thời hạn 2 giờ:** Trả HTTP 409 `RATING_WINDOW_EXPIRED`.<br>**A2 — Đã đánh giá:** Trả HTTP 409 và không tạo rating thứ hai.<br>**A3 — Order chưa hoàn thành/không có Driver:** Trả HTTP 409 `RATING_NOT_ALLOWED`.<br>**A4 — Order thuộc Customer khác:** Trả HTTP 403.<br>**A5 — Rating/comment sai:** Trả HTTP 422 với field errors. |
| **Postconditions** | - Có đúng một rating bất biến cho order.<br>- Aggregate rating Driver phản ánh rating mới.<br>- Audit/event được ghi và UI hiển thị kết quả. |
| **Business Rules** | - Một Customer chỉ rating một lần cho completed order của mình.<br>- Rating chỉ trong window 2 giờ.<br>- Ownership vi phạm trả 403 — HR-10.<br>- Audit mutation bắt buộc — HR-13. |
| **Non-Functional** | - Performance: submit rating dưới 1 giây ở p90.<br>- Concurrency: unique constraint ngăn double-submit.<br>- Security: sanitize comment/tags, không cho rating order người khác.<br>- UX: star control accessible, validation tiếng Việt, success state rõ ràng. |
| **Related UCs** | - Include: UC-018 Xem chi tiết đơn.<br>- Related: UC-016 Tracking; UC-032 Hoàn thành đơn; UC-033 Driver history/profile. |

---

<a id="uc-024"></a>
### UC-024: Upload giấy tờ Driver

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-024 |
| **Use Case Name** | Upload GPLX, đăng ký xe và ảnh xe bước 2 onboarding |
| **Actor** | Primary: Driver; Secondary: Move_home System, Cloudinary |
| **Spec Reference** | `specs/005-driver-onboarding/spec.md` — US2, US3, FR-006 → FR-022 |
| **Priority** | P1 — Critical Onboarding |
| **Preconditions** | - Driver đã đăng ký và xác thực email.<br>- Driver status là `PENDING_DOCUMENTS`.<br>- Driver có JWT hợp lệ.<br>- Cloudinary signed upload service khả dụng. |
| **Trigger** | Driver mở bước 2 và chọn upload giấy tờ/ảnh xe. |
| **Main Flow (Happy Path)** | 1. Frontend tải onboarding status và checklist bắt buộc.<br>2. Driver chọn GPLX mặt trước/sau, đăng ký xe và ba ảnh xe.<br>3. Frontend kiểm tra content type, kích thước và vai trò từng ảnh.<br>4. Frontend yêu cầu signed upload parameters từ backend.<br>5. Backend xác thực Driver/status và trả Cloudinary signed params có TTL ngắn.<br>6. Frontend upload trực tiếp từng file lên Cloudinary và hiển thị progress.<br>7. Sau mỗi upload, frontend gọi confirm endpoint với public ID/metadata.<br>8. Driver nhập license number, license class, plate, vehicle type và year.<br>9. Backend validate uniqueness license/normalized plate và checklist hoàn chỉnh.<br>10. Backend lock Driver, lưu documents/vehicle, chuyển sang `PENDING_DEPOSIT` và ghi audit. |
| **Alternative Flows** | **A1 — File sai type/kích thước:** Trả HTTP 422 trước khi gọi Cloudinary.<br>**A2 — Cloudinary upload lỗi:** Hiển thị retry theo file; không hoàn tất step.<br>**A3 — License number hoặc plate trùng:** Trả HTTP 409 conflict.<br>**A4 — Checklist thiếu:** Trả HTTP 409, giữ status `PENDING_DOCUMENTS`.<br>**A5 — Sai onboarding state/role:** Trả HTTP 409 hoặc 403, không mutate.<br>**A6 — Upload xong nhưng confirm fail:** Asset orphan được cleanup async sau TTL. |
| **Postconditions** | - Document và vehicle metadata hợp lệ được lưu.<br>- Ảnh nằm trên Cloudinary, không lưu BLOB/Base64 trong DB.<br>- Driver chuyển sang `PENDING_DEPOSIT` khi checklist đầy đủ.<br>- Audit onboarding step completion được ghi. |
| **Business Rules** | - Driver onboarding 4 bước bắt buộc — HR-12.<br>- Signed Cloudinary upload, URL đọc expire 1 giờ — AC-10.<br>- Transition sai trả 409 — HR-05.<br>- Audit state transition — HR-13.<br>- Secrets Cloudinary chỉ qua environment — HR-01. |
| **Non-Functional** | - Performance: signed params nhanh; upload timeout mục tiêu không quá 10 giây/file.<br>- Security: validate MIME/size trước upload; không log signature/document number đầy đủ.<br>- Reliability: completion transaction atomic; orphan cleanup async.<br>- UX: progress từng file, preview, retry và checklist rõ ràng. |
| **Related UCs** | - Related: UC-023 Driver đăng ký; UC-025 Đặt cọc; UC-026 Chờ duyệt; UC-065 Cloudinary integration. |

---

<a id="uc-025"></a>
### UC-025: Đặt cọc 3.000.000 VND

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-025 |
| **Use Case Name** | Đặt cọc Driver 3.000.000 VND qua VNPay |
| **Actor** | Primary: Driver; Secondary: Move_home System, VNPay |
| **Spec Reference** | `specs/005-driver-onboarding/spec.md` — US4, FR-023 → FR-031 |
| **Priority** | P1 — Critical Payment |
| **Preconditions** | - Driver status `PENDING_DEPOSIT` và hồ sơ bước 2 hoàn tất.<br>- Chưa có completed deposit cho Driver.<br>- VNPay config/secret khả dụng qua environment. |
| **Trigger** | Driver nhấn **Đặt cọc 3.000.000 VND**. |
| **Main Flow (Happy Path)** | 1. Frontend hiển thị amount cố định và điều kiện đặt cọc.<br>2. Driver xác nhận thanh toán.<br>3. Backend lock Driver, kiểm tra status và chưa có completed deposit.<br>4. Backend tạo deposit attempt với unique transaction reference.<br>5. Backend ký VNPay request và trả payment URL, amount, expiry 15 phút.<br>6. Frontend redirect Driver sang VNPay.<br>7. Driver hoàn tất thanh toán tại VNPay.<br>8. VNPay gọi IPN server-to-server.<br>9. Backend verify HMAC-SHA512 trước lookup/mutation, lock deposit và Driver, kiểm tra amount/reference/status.<br>10. Backend complete deposit exactly-once, chuyển Driver sang `PENDING_APPROVAL`, ghi transaction/audit và trả `RspCode=00`. |
| **Alternative Flows** | **A1 — Driver sai onboarding step:** Initiate trả HTTP 409 `INVALID_ONBOARDING_STEP`.<br>**A2 — Deposit đã hoàn tất:** Trả HTTP 409 `DEPOSIT_ALREADY_PAID`.<br>**A3 — HMAC IPN sai:** Trả `RspCode=97`, không thay đổi DB.<br>**A4 — Amount/reference/status không khớp:** Trả gateway error, không complete deposit.<br>**A5 — IPN trùng hoặc đồng thời:** Unique ref + row lock cho đúng một commit.<br>**A6 — Return URL giả success:** Chỉ hiển thị pending; DB không đổi nếu chưa có IPN. |
| **Postconditions** | - Có đúng một completed deposit 3.000.000 VND.<br>- Driver status là `PENDING_APPROVAL`.<br>- Money transaction và audit trail tồn tại.<br>- Driver có thể vào màn chờ duyệt. |
| **Business Rules** | - IPN là nguồn cập nhật payment duy nhất — HR-03.<br>- Verify HMAC trước xử lý — HR-04.<br>- Webhook/IPN phải idempotent — HR-15.<br>- Invalid transition trả 409 — HR-05.<br>- Money VND nguyên đồng — AC-08.<br>- Deposit workflow thuộc onboarding HR-12. |
| **Non-Functional** | - Performance: initiate trả URL dưới 3 giây p90; IPN xử lý ngắn và deterministic.<br>- Security: secret chỉ env; không log HMAC/full gateway payload nhạy cảm.<br>- Concurrency: hai IPN success đồng thời chỉ một commit.<br>- Reliability: DB transaction gồm deposit, state, money audit; email lỗi không rollback. |
| **Related UCs** | - Related: UC-024 Upload giấy tờ; UC-026 Chờ duyệt; UC-037 Manager duyệt Driver; UC-065 VNPay integration. |

---

<a id="uc-030"></a>
### UC-030: Nhận hoặc từ chối đơn có lock

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-030 |
| **Use Case Name** | Xem chi tiết và nhận/từ chối assignment có concurrency lock |
| **Actor** | Primary: Driver; Secondary: Manager, Move_home System |
| **Spec Reference** | `specs/006-driver-workflow/spec.md` — US2 → US4, FR-014 → FR-021 |
| **Priority** | P1 — Concurrency Critical |
| **Preconditions** | - Driver `ACTIVE`, đã đăng nhập và là owner của assignment.<br>- Assignment còn `PENDING`, chưa hết deadline.<br>- Order ở state cho phép accept.<br>- Driver không có order active gây conflict. |
| **Trigger** | Driver nhấn **Nhận đơn** hoặc **Từ chối** từ assignment detail. |
| **Main Flow (Happy Path)** | 1. Frontend hiển thị assignment, route, schedule, pricing và deadline.<br>2. Driver nhấn **Nhận đơn** và gửi Idempotency-Key.<br>3. Backend bắt đầu transaction và lock `driver_assignment` rồi `service_order` bằng `SELECT ... FOR UPDATE`.<br>4. Backend verify assignment owner, deadline, assignment state và order state.<br>5. Backend kiểm tra Driver không có order active conflicting.<br>6. Backend chuyển assignment sang accepted và cập nhật order theo canonical transition.<br>7. Backend cập nhật Driver availability/busy state nếu contract yêu cầu.<br>8. Backend ghi audit và publish notification event.<br>9. Backend commit và trả allowed next actions.<br>10. Frontend điều hướng đến order/in-progress workflow. |
| **Alternative Flows** | **A1 — Assignment đã được reassign/accept:** Request thua lock trả HTTP 409 `ASSIGNMENT_NO_LONGER_AVAILABLE`.<br>**A2 — Driver từ chối:** Backend lock rows, validate reason/quota, chuyển rejected và notify Manager.<br>**A3 — Vượt reject quota:** Trả HTTP 409 `DAILY_REJECT_QUOTA_EXCEEDED`.<br>**A4 — Order/assignment sai state:** Trả HTTP 409 và zero mutation.<br>**A5 — Không phải owner:** Trả HTTP 403.<br>**A6 — Retry cùng Idempotency-Key:** Replay response cũ, không duplicate audit. |
| **Postconditions** | - Chính xác một outcome accept/reject được commit.<br>- Order/assignment/Driver state nhất quán.<br>- Audit và notification event được ghi một lần.<br>- Driver nhận next action hợp lệ. |
| **Business Rules** | - Concurrency lock bắt buộc — HR-08.<br>- Invalid transition trả 409 — HR-05.<br>- Ownership/RBAC sai trả 403 — HR-10.<br>- Audit mọi state change — HR-13.<br>- Lock order nhất quán: assignment → order → profile/wallet. |
| **Non-Functional** | - Performance: accept/reject API p90 dưới 1 giây kể cả lock acquisition.<br>- Concurrency: 100 stale accept/reassign requests chỉ một outcome, zero deadlock.<br>- Reliability: Idempotency-Key ngăn double-click mutation.<br>- UX: disable button khi submit và hiển thị conflict tiếng Việt rõ ràng. |
| **Related UCs** | - Extend: UC-031 Đến pickup/bắt đầu vận chuyển.<br>- Related: UC-029 Browse assignment; UC-032 Hoàn thành đơn; UC-062 Audit Log. |

---

<a id="uc-032"></a>
### UC-032: Hoàn thành đơn và tính earnings

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-032 |
| **Use Case Name** | Yêu cầu thanh toán cuối, hoàn thành order và tính Driver earnings |
| **Actor** | Primary: Driver; Secondary: Customer, Move_home System, VNPay |
| **Spec Reference** | `specs/006-driver-workflow/spec.md` — US6, US7, FR-027 → FR-034 |
| **Priority** | P1 — Money/State Critical |
| **Preconditions** | - Driver là owner của order và order đã `IN_PROGRESS`.<br>- Không có open dispute/DamageReport chặn complete.<br>- Final payment có thể được yêu cầu và xác nhận bởi verified IPN. |
| **Trigger** | Driver nhấn **Yêu cầu thanh toán** rồi nhấn **Hoàn thành** sau khi final payment verified. |
| **Main Flow (Happy Path)** | 1. Driver nhấn yêu cầu Customer thanh toán 70% còn lại.<br>2. Backend lock order, chuyển `IN_PROGRESS → AWAITING_FINAL_PAYMENT`, ghi audit và tạo payment boundary event.<br>3. Customer thanh toán qua VNPay.<br>4. Payment module xác nhận final payment chỉ từ verified IPN.<br>5. Driver nhấn **Hoàn thành** với Idempotency-Key.<br>6. Backend lock order và verify owner, state, final payment trusted flag và không có dispute.<br>7. Backend chuyển order sang `COMPLETED`, set `completed_at` và đưa Driver về `ONLINE`.<br>8. Backend snapshot commission và tính `driver_earning = total_quote - commission_amount`.<br>9. Backend tạo earning pending escrow, `escrow_release_at = completed_at + 2h`, ghi audit và trả preview.<br>10. Scheduled job sau 2 giờ lock order/wallet, nếu không dispute thì credit wallet và append `DRIVER_EARNING` transaction đúng một lần. |
| **Alternative Flows** | **A1 — Final payment chưa verified:** Complete trả HTTP 409, order giữ `AWAITING_FINAL_PAYMENT`.<br>**A2 — Order IN_DISPUTE/open DamageReport:** Driver bị chặn complete theo HR-06; trả HTTP 409.<br>**A3 — Sai owner:** Trả HTTP 403.<br>**A4 — Double-click/retry:** Idempotency replay, không duplicate completion/earning.<br>**A5 — Dispute mở trong escrow:** Worker không release earning cho đến resolution.<br>**A6 — Worker chạy đồng thời:** Lock + unique transaction cho một wallet credit. |
| **Postconditions** | - Order `COMPLETED`, Driver `ONLINE` và audit timeline đầy đủ.<br>- Earning snapshot tồn tại ở trạng thái pending/released đúng thời điểm.<br>- Wallet chỉ được credit sau escrow khi đủ điều kiện.<br>- Không có double credit. |
| **Business Rules** | - Final payment state chỉ từ verified IPN — HR-03, HR-04.<br>- Driver không complete order IN_DISPUTE — HR-06.<br>- Chỉ Manager/Admin đóng dispute — HR-07.<br>- Invalid transition trả 409 — HR-05.<br>- Wallet không âm và money audit append-only — HR-18, AC-13.<br>- Money dùng VND nguyên đồng — AC-08. |
| **Non-Functional** | - Performance: complete API p90 dưới 1 giây kể cả lock.<br>- Atomicity: order state, earning snapshot và audit commit cùng transaction.<br>- Concurrency: escrow worker/idempotent complete không double credit.<br>- Observability: alert khi complete rejection hoặc escrow overdue tăng bất thường. |
| **Related UCs** | - Extend: UC-031 Đến pickup/bắt đầu.<br>- Related: UC-016 Customer tracking; UC-019 Rating; UC-034 Earnings; UC-043 Dispute. |

---

<a id="uc-035"></a>
### UC-035: Yêu cầu rút tiền Driver

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-035 |
| **Use Case Name** | Tạo yêu cầu rút tiền và xem lịch sử withdrawal |
| **Actor** | Primary: Driver; Secondary: Admin, Move_home System, Gmail SMTP |
| **Spec Reference** | `specs/007-driver-financial/spec.md` — US4 → US7, FR-020 → FR-042 |
| **Priority** | P1 — Financial Critical |
| **Preconditions** | - Driver `ACTIVE`, có wallet và JWT hợp lệ.<br>- Driver có số tiền khả dụng sau khi trừ tổng pending withdrawals.<br>- Thông tin ngân hàng hợp lệ. |
| **Trigger** | Driver nhập amount/thông tin ngân hàng và nhấn **Gửi yêu cầu rút tiền**. |
| **Main Flow (Happy Path)** | 1. Frontend gọi withdrawal form API để lấy balance, pending total, available amount và bank info đã lưu.<br>2. Driver nhập amount và xác nhận tài khoản ngân hàng.<br>3. Frontend validate minimum, integer VND và required fields.<br>4. Frontend gửi request với Idempotency-Key.<br>5. Backend lock `driver_wallet` và đọc current pending withdrawals.<br>6. Backend tính lại `available_to_withdraw = balance - pending_total` dưới lock.<br>7. Backend validate amount không vượt available và Driver đủ điều kiện.<br>8. Backend tạo withdrawal `PENDING`, snapshot bank data và lưu saved bank account nếu chọn.<br>9. Backend ghi audit/outbox notification rồi commit; wallet chưa bị trừ.<br>10. Frontend hiển thị request/timeline và cho phép Driver xem history hoặc cancel khi còn pending. |
| **Alternative Flows** | **A1 — Amount vượt available sau lock:** Trả HTTP 422, không tạo request.<br>**A2 — Hai request dùng số dư cuối:** Wallet lock serialize; chỉ request hợp lệ commit.<br>**A3 — Idempotency-Key trùng:** Trả HTTP 409/replay, không duplicate pending amount.<br>**A4 — Driver suspended/sai trạng thái:** Trả HTTP 403/409.<br>**A5 — Bank data sai:** Trả HTTP 422 theo field.<br>**A6 — Driver cancel pending:** Lock withdrawal, chuyển `CANCELLED`; wallet không đổi. |
| **Postconditions** | - Withdrawal `PENDING` tồn tại với bank snapshot masked/encrypted phù hợp.<br>- Wallet balance chưa bị trừ; pending amount làm giảm available logic.<br>- Audit và email/outbox được tạo.<br>- Request hiển thị trong history. |
| **Business Rules** | - Wallet chỉ trừ khi Admin mark `PROCESSED`, không trừ lúc request.<br>- Wallet không âm và money trail bắt buộc — HR-18, AC-13.<br>- Invalid transition trả 409 — HR-05.<br>- Audit withdrawal state — HR-13.<br>- Email async — HR-11.<br>- Driver chỉ xem withdrawal của mình — HR-10. |
| **Non-Functional** | - Performance: request/cancel p90 dưới 2 giây kể cả row lock.<br>- Concurrency: requests cùng Driver serialize dưới wallet lock.<br>- Security: account number encrypted; UI/log chỉ hiển thị masked value.<br>- Reliability: unique idempotency key và deterministic lock order. |
| **Related UCs** | - Related: UC-034 Earnings; UC-040 Admin xử lý withdrawal; UC-042 Withdrawal history; UC-063 Email notifications. |

---

<a id="uc-037"></a>
### UC-037: Manager duyệt tài xế

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-037 |
| **Use Case Name** | Manager xem queue và duyệt hồ sơ Driver |
| **Actor** | Primary: Manager; Secondary: Driver, Move_home System, Cloudinary, Gmail SMTP |
| **Spec Reference** | `specs/008-manager-driver-approval/spec.md` — US1 → US5, FR-001 → FR-034 |
| **Priority** | P1 — Operational Critical |
| **Preconditions** | - Manager có JWT hợp lệ và role `MANAGER`.<br>- Driver đã đặt cọc, status `PENDING_APPROVAL`.<br>- Hồ sơ, vehicle, documents và deposit metadata tồn tại. |
| **Trigger** | Manager mở approval queue và chọn một Driver để quyết định. |
| **Main Flow (Happy Path)** | 1. Hệ thống hiển thị queue `PENDING_APPROVAL`, KPI và SLA age.<br>2. Manager chọn Driver cần duyệt.<br>3. Backend trả aggregate detail: profile, GPLX, vehicle, ảnh, deposit, timeline và eligibility.<br>4. Frontend tạo fresh signed URLs để Manager xem/zoom/rotate documents.<br>5. Manager kiểm tra checklist và nhấn **Duyệt**.<br>6. Backend bắt đầu transaction, lock `app_user` Driver và verify actor role/status.<br>7. Backend kiểm tra eligibility: verified email, đủ documents, primary vehicle hợp lệ, deposit completed.<br>8. Backend chuyển Driver sang `ACTIVE`, cập nhật approved metadata và documents/vehicle state.<br>9. Backend ghi audit `DRIVER_APPROVED`, commit và enqueue email sau commit.<br>10. Queue refresh; Driver nhận email và có thể vào Driver home. |
| **Alternative Flows** | **A1 — Hồ sơ chưa đủ/không approvable:** Trả HTTP 422 `DRIVER_NOT_APPROVABLE` với blocking reasons.<br>**A2 — Manager reject:** Yêu cầu reason tiếng Việt hợp lệ; chuyển `REJECTED`, giữ deposit, ghi audit/email.<br>**A3 — Hai Manager quyết định đồng thời:** Row lock cho một commit; request sau trả HTTP 409.<br>**A4 — Driver không còn pending:** Trả HTTP 409 `DRIVER_DECISION_CONFLICT`.<br>**A5 — Admin/Driver/Customer gọi endpoint:** Trả HTTP 403; chỉ Manager được duyệt.<br>**A6 — Cloudinary/email lỗi:** Viewer cho retry; email lỗi không rollback decision. |
| **Postconditions** | - Driver chuyển `ACTIVE` khi approve hoặc `REJECTED` khi reject.<br>- Deposit không bị hoàn tự động khi reject.<br>- Audit decision bền vững và email được enqueue.<br>- Queue/history phản ánh outcome mới. |
| **Business Rules** | - Chỉ Manager duyệt onboarding theo spec 008; role khác trả 403 — HR-10.<br>- Concurrent decision lock, một outcome commit.<br>- Invalid state trả 409 — HR-05.<br>- Audit decision atomic — HR-13.<br>- Email async — HR-11.<br>- Documents dùng signed URL — AC-10. |
| **Non-Functional** | - Performance: queue dưới 500 ms p90; decision DB dưới 1 giây p90, API dưới 2 giây.<br>- Concurrency: nhiều Manager không deadlock, đúng một transition.<br>- Privacy: không log signed URL/PII nhạy cảm.<br>- UX: eligibility/blocking reasons và Loading/Empty/Error states rõ ràng. |
| **Related UCs** | - Include: UC-038 Xem detail/decide Driver.<br>- Related: UC-024 Upload giấy tờ; UC-025 Đặt cọc; UC-027 Kích hoạt; UC-063 Email. |

---

<a id="uc-040"></a>
### UC-040: Xử lý withdrawal

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-040 |
| **Use Case Name** | Xem queue và xử lý yêu cầu rút tiền |
| **Actor** | Primary canonical: Admin; Legacy route actor: Manager bị từ chối; Secondary: Driver, Move_home System, Gmail SMTP |
| **Spec Reference** | `specs/009-manager-withdrawal/spec.md` — Admin Withdrawal Processing |
| **Priority** | P1 — Financial Critical |
| **Preconditions** | - Admin có JWT hợp lệ; Manager không có quyền xử lý theo spec canonical.<br>- Withdrawal status `PENDING`.<br>- Admin đã thực hiện/kiểm tra chuyển khoản ngoài hệ thống trước khi mark processed.<br>- Wallet và bank snapshot có thể verify. |
| **Trigger** | Admin mở pending queue, chọn request và nhấn **Process** hoặc **Reject**. |
| **Main Flow (Happy Path)** | 1. Admin xem queue FIFO, pending total và oldest age.<br>2. Admin mở detail gồm Driver, amount, available balance, masked bank, timeline và warnings.<br>3. Admin verify request/bank và thực hiện chuyển khoản ngoài hệ thống.<br>4. Admin nhập unique bank transaction reference và xác nhận process.<br>5. Backend bắt đầu transaction và lock `withdrawal_request` trước `driver_wallet`.<br>6. Backend re-check status `PENDING`, balance, duplicate transaction và bank reference.<br>7. Backend debit wallet đúng amount, tăng total withdrawn và append một `WITHDRAWAL` transaction.<br>8. Backend chuyển request sang `PROCESSED`, lưu processor/reference/time.<br>9. Backend ghi audit/outbox trong cùng transaction và commit.<br>10. UI chuyển request sang terminal read-only; Driver nhận email. |
| **Alternative Flows** | **A1 — Admin reject:** Validate reason, lock request, chuyển `REJECTED`, không update wallet/money transaction.<br>**A2 — Manager cố process/reveal bank:** Trả HTTP 403; không lộ bank data.<br>**A3 — Số dư không đủ khi process:** Trả HTTP 422; request giữ pending để review/reject.<br>**A4 — Hai Admin process đồng thời:** Lock serialize; một debit commit, request sau replay/409.<br>**A5 — Driver cancel cùng lúc:** First lock/commit wins; không double debit.<br>**A6 — External transfer/API timeout:** Retry cùng idempotency/bank ref không tạo debit thứ hai.<br>**A7 — Audit insert fail:** Rollback wallet, transaction và withdrawal state. |
| **Postconditions** | - Khi processed: wallet giảm đúng một lần, transaction/audit tồn tại, request terminal `PROCESSED`.<br>- Khi rejected: wallet không đổi, request `REJECTED`, audit/email tồn tại.<br>- Không có duplicate debit hoặc duplicate bank reference. |
| **Business Rules** | - Canonical processor là Admin; Manager nhận 403 — HR-10 và spec 009.<br>- Chỉ `PENDING → PROCESSED|REJECTED`; transition sai trả 409 — HR-05.<br>- Wallet không âm, append-only money trail — HR-18, AC-13.<br>- Audit money state atomic — HR-13.<br>- Email lỗi không rollback — HR-11.<br>- Lock order: withdrawal → wallet → transaction → audit. |
| **Non-Functional** | - Performance: detail/process đáp ứng SLA vận hành; transaction giữ lock ngắn.<br>- Concurrency: concurrent process/reject/cancel chỉ một terminal outcome.<br>- Security: bank reveal Admin-only, account masked, không log plaintext/rejection reason.<br>- Reliability: unique money guard và bank transaction reference hỗ trợ retry an toàn. |
| **Related UCs** | - Related: UC-035 Driver yêu cầu rút tiền; UC-042 Lịch sử withdrawal; UC-049 Admin withdrawals list; UC-053 Transactions. |

---

<a id="uc-043"></a>
### UC-043: Xử lý khiếu nại với 3 outcomes

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-043 |
| **Use Case Name** | Xem và xử lý dispute bằng Refund Customer, Deduct Driver hoặc Close No Fault |
| **Actor** | Primary: Manager/Admin; Secondary: Customer, Driver, Move_home System, Gmail SMTP |
| **Spec Reference** | `specs/010-manager-disputes/spec.md` — US1 → US7, FR-001 → FR-043 |
| **Priority** | P1 — Money/Resolution Critical |
| **Preconditions** | - Actor có role `MANAGER` hoặc `ADMIN` và JWT hợp lệ.<br>- Dispute status `OPEN` hoặc `INVESTIGATING`.<br>- Order ở `IN_DISPUTE` và relationship Customer/Driver/order hợp lệ.<br>- Evidence/detail đã được tải để ra quyết định. |
| **Trigger** | Manager/Admin mở dispute detail và chọn một trong ba outcome. |
| **Main Flow (Happy Path)** | 1. Actor xem pending queue, claim amount và SLA.<br>2. Actor mở detail gồm claim, evidence, Driver response, order, parties, comments và timeline.<br>3. Actor thêm comment nội bộ/kiểm tra eligibility.<br>4. Actor chọn chính xác một outcome: `REFUND_CUSTOMER`, `DEDUCT_DRIVER` hoặc `CLOSE_NO_FAULT`.<br>5. Actor nhập amount nếu outcome có tiền và note 30-1000 ký tự.<br>6. Backend bắt đầu transaction, lock dispute rồi order và các wallet cần thiết theo thứ tự canonical.<br>7. Backend re-check state, role, relationship, amount và funds dưới lock.<br>8. Backend cập nhật terminal outcome; nếu có tiền thì append đúng money transactions tương ứng.<br>9. Backend ghi audit atomic, commit và enqueue notifications/email sau commit.<br>10. Detail chuyển read-only và hiển thị outcome, amount, actor, note, resolved time. |
| **Alternative Flows** | **A1 — Refund Customer:** Credit Customer, tạo đúng một positive `REFUND`, không debit Driver.<br>**A2 — Deduct Driver:** Debit Driver balance/deposit và credit Customer cùng absolute amount; rollback nếu funds thiếu.<br>**A3 — Close No Fault:** Chỉ đóng dispute/audit, không lock/update wallet và không tạo money transaction.<br>**A4 — Dispute đã terminal:** Trả HTTP 409 `DISPUTE_ALREADY_RESOLVED`, zero writes.<br>**A5 — Hai actor quyết định đồng thời:** First terminal outcome wins; request sau 409, không double money.<br>**A6 — Amount/note sai:** Trả HTTP 422, không partial write.<br>**A7 — Audit fail:** Rollback outcome và mọi money changes. |
| **Postconditions** | - Dispute có đúng một terminal outcome canonical.<br>- Money transactions khớp outcome và audit trail đầy đủ.<br>- Order vẫn được quản lý theo dispute resolution policy.<br>- Customer/Driver nhận notification sau commit. |
| **Business Rules** | - Đúng ba outcomes, không tạo outcome ngầm.<br>- Invalid/terminal transition trả 409 — HR-05.<br>- Driver không tự đóng dispute — HR-06, HR-07.<br>- Actor sai role trả 403 — HR-10.<br>- Audit và money changes atomic — HR-13, HR-18, AC-13.<br>- Money VND nguyên đồng — AC-08. |
| **Non-Functional** | - Performance: decision API phản hồi trong SLA dưới lock; evidence tải có retry.<br>- Concurrency: 50 decisions đồng thời chỉ một terminal outcome, không double money/deadlock.<br>- Security: signed evidence URL; sanitize note; không log phone/full note/raw URL.<br>- Reliability: idempotency replay khi mất mạng sau successful decision. |
| **Related UCs** | - Related: UC-018 Order detail; UC-032 Complete/escrow; UC-044 Dispute history; UC-053 Transactions; UC-062 Audit. |

---

<a id="uc-045"></a>
### UC-045: Admin Dashboard với KPI

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-045 |
| **Use Case Name** | Xem Admin Dashboard với KPI, charts và operational tables |
| **Actor** | Primary: Admin; Secondary: Move_home System |
| **Spec Reference** | `specs/015-admin-dashboard/spec.md` — US1 → US4, FR-001 → FR-018 |
| **Priority** | P1 — Demo/Operational Critical |
| **Preconditions** | - Admin đã đăng nhập với JWT hợp lệ.<br>- Dashboard APIs và database khả dụng.<br>- Seed/production data có canonical statuses. |
| **Trigger** | Admin đăng nhập hoặc điều hướng đến `admin/dashboard.html`. |
| **Main Flow (Happy Path)** | 1. Frontend auth guard kiểm tra Admin session.<br>2. Backend xác thực role `ADMIN` và ghi audit `DASHBOARD_VIEW`.<br>3. Frontend song song gọi KPI, orders-by-day, revenue-by-month, pending drivers và recent orders APIs.<br>4. KPI API trả sáu metrics trong một response: orders, commission revenue, completion rate, active Drivers, pending approvals, disputes.<br>5. Chart APIs trả đủ date/month series kể cả ngày/tháng có zero data.<br>6. Table APIs trả top pending Drivers và recent orders.<br>7. Frontend render sáu KPI cards với variant phù hợp.<br>8. Frontend render bar chart 30 ngày và line chart 12 tháng bằng Chart.js.<br>9. Frontend render hai operational tables và action links.<br>10. Admin dùng dashboard để điều hướng đến queue/list cần xử lý. |
| **Alternative Flows** | **A1 — Không có JWT:** Redirect login với `redirect_to`.<br>**A2 — Sai role:** Trả HTTP 403 và điều hướng về home đúng role.<br>**A3 — Một API section lỗi:** Hiển thị section-level error/retry, không làm hỏng toàn page.<br>**A4 — Data rỗng:** Hiển thị zero KPI/empty table và full date series.<br>**A5 — Chart.js/CDN lỗi:** Giữ KPI/tables và hiển thị chart fallback message. |
| **Postconditions** | - Admin nhìn thấy snapshot hoạt động mới nhất.<br>- Dashboard view audit được ghi.<br>- Không có mutation business data từ thao tác xem dashboard. |
| **Business Rules** | - Chỉ Admin truy cập — HR-10.<br>- Dashboard view có audit theo spec — HR-13.<br>- Revenue dùng commission snapshot, VND nguyên đồng — AC-08.<br>- Time buckets theo timezone contract — AC-07.<br>- UI dùng brand và tiếng Việt — HR-19, HR-20. |
| **Non-Functional** | - Performance: KPI endpoint p95 dưới 200 ms; chart endpoints mục tiêu dưới 300 ms.<br>- Security: không expose PII không cần thiết; auth guard ở backend, không chỉ frontend.<br>- Availability: tải song song, section failure isolation.<br>- UX: desktop/tablet readable, Loading/Empty/Error cho mỗi section. |
| **Related UCs** | - Related: UC-037 Driver approval; UC-043 Disputes; UC-046 → UC-049 Admin lists; UC-055 Reports. |

---

<a id="uc-054"></a>
### UC-054: Cấu hình Commission Settings

| Field | Value |
|-------|-------|
| **Use Case ID** | UC-054 |
| **Use Case Name** | Xem và cập nhật commission, pricing và surcharge settings |
| **Actor** | Primary: Admin; Secondary: Move_home System, Gmail SMTP |
| **Spec Reference** | `specs/014-admin-commission-settings/spec.md` — US1 → US7, FR-001 → FR-034 |
| **Priority** | P1 — Financial Configuration Critical |
| **Preconditions** | - Admin có JWT hợp lệ.<br>- Có một current commission settings row/version.<br>- Admin hiểu thay đổi chỉ áp dụng cho order mới qua snapshot.<br>- Không có dirty form chưa xác nhận khi rời trang. |
| **Trigger** | Admin mở Commission Settings, sửa giá trị và nhấn **Lưu thay đổi**. |
| **Main Flow (Happy Path)** | 1. Frontend gọi current settings API và render version/current values.<br>2. Admin chỉnh commission, peak ranges/rates, alley rate, floor tiers, base rate per km hoặc porter fee.<br>3. Frontend validate format/bounds và đánh dấu dirty fields.<br>4. Frontend gọi preview API để tính tác động trên sample orders mà không persist.<br>5. Frontend hiển thị confirmation modal với diff từ giá trị cũ sang mới.<br>6. Admin xác nhận save; frontend gửi partial PATCH cùng current version.<br>7. Backend xác thực Admin role, strict validation và optimistic version.<br>8. Backend lock settings row, increment version, update settings và insert immutable history.<br>9. Backend insert critical audit `SETTINGS_UPDATED` và outbox email trong cùng transaction.<br>10. Backend commit, trả version mới; frontend hiển thị success và refresh history. |
| **Alternative Flows** | **A1 — Rate/range/tier invalid:** Trả HTTP 422 với field errors; không persist.<br>**A2 — Hai Admin update cùng version:** Request stale trả HTTP 409 `SETTINGS_VERSION_CONFLICT`, không history/audit/outbox mới.<br>**A3 — Admin cancel confirmation:** Đóng modal, không thay đổi server.<br>**A4 — Audit/history/outbox insert fail:** Rollback toàn bộ settings update.<br>**A5 — Non-Admin truy cập:** Trả HTTP 403 và security audit.<br>**A6 — Rời trang khi dirty:** Frontend cảnh báo trước khi mất thay đổi.<br>**A7 — Email notification lỗi sau commit:** Retry async, settings không rollback. |
| **Postconditions** | - Current settings có version mới và giá trị hợp lệ.<br>- Immutable history, audit và outbox event commit cùng update.<br>- Order mới dùng snapshot mới; order cũ không bị repricing.<br>- Admin khác có thể thấy diff/history. |
| **Business Rules** | - Chỉ Admin cập nhật — HR-10.<br>- Money config update phải audit immutable — HR-13.<br>- Optimistic locking ngăn lost update.<br>- Order pricing dùng snapshot tại thời điểm tạo.<br>- Commission rate trong `0.0500..0.5000`; rates/ranges/tier phải theo validation spec.<br>- VND integer và precision rate deterministic — AC-08. |
| **Non-Functional** | - Performance: current/history/preview phản hồi nhanh; update transaction ngắn.<br>- Concurrency: optimistic lock cho một winner, stale writer nhận 409.<br>- Security: critical audit không throttle; correlation ID liên kết PATCH/history/audit/outbox.<br>- UX: preview impact, highlighted diff, accessible confirmation và dirty-form protection. |
| **Related UCs** | - Related: UC-012 Booking quote; UC-032 Earnings calculation; UC-045 Dashboard revenue; UC-053 Transactions; UC-055 Reports. |

---

## Traceability Summary

| UC | Primary Spec | Main Constitution Rules | Critical Test Focus |
|----|--------------|-------------------------|---------------------|
| UC-007 | 001 Auth & RBAC | HR-02, HR-11, HR-16, HR-17 | Validation, uniqueness, BCrypt, async email |
| UC-009 | 001 Auth & RBAC | HR-01, HR-02, HR-10, HR-16 | Rate limit, lockout, token rotation, role redirect |
| UC-012 | 002 Customer Booking | HR-03, HR-04, HR-05, HR-13 | Six-step persistence, quote, idempotent confirm |
| UC-016 | 003 Customer Orders | HR-05, HR-10 | Ownership, polling, stale location, recovery |
| UC-019 | 003 Customer Orders | HR-10, HR-13 | Eligibility, 2-hour window, one rating |
| UC-024 | 005 Driver Onboarding | HR-05, HR-12, HR-13 | Signed upload, checklist, duplicate documents |
| UC-025 | 005 Driver Onboarding | HR-03, HR-04, HR-15 | Verified IPN, duplicate IPN, exact deposit |
| UC-030 | 006 Driver Workflow | HR-05, HR-08, HR-13 | Concurrent accept/reassign, idempotency |
| UC-032 | 006 Driver Workflow | HR-03, HR-05, HR-06, HR-18 | Final IPN, dispute guard, escrow release |
| UC-035 | 007 Driver Financial | HR-05, HR-13, HR-18 | Available calculation, concurrent requests |
| UC-037 | 008 Manager Approval | HR-05, HR-10, HR-13 | Eligibility, concurrent decisions, RBAC |
| UC-040 | 009 Withdrawal Processing | HR-05, HR-10, HR-13, HR-18 | Admin-only, debit once, cancel/process race |
| UC-043 | 010 Manager Disputes | HR-05, HR-07, HR-13, HR-18 | Three outcomes, atomic money, concurrent decisions |
| UC-045 | 015 Admin Dashboard | HR-10, HR-13, HR-19 | KPI accuracy, RBAC, partial loading |
| UC-054 | 014 Commission Settings | HR-10, HR-13, AC-08 | Strict validation, optimistic lock, snapshot integrity |

## QA Exit Criteria

| Category | Exit Criteria |
|----------|---------------|
| Happy paths | Mỗi UC có integration test cho main flow và postconditions |
| Alternative paths | Mỗi UC có ít nhất hai error/alternative test cases |
| RBAC | Mọi protected UC test thiếu JWT, đúng role và sai role |
| State machine | Mọi mutation test valid transition và invalid transition HTTP 409 |
| Concurrency | UC-025, UC-030, UC-032, UC-035, UC-037, UC-040, UC-043, UC-054 có concurrent test |
| Idempotency | Payment, confirm, assignment, withdrawal và dispute retry không duplicate writes |
| Money integrity | Wallet/transaction/audit reconcile; không partial money write hoặc số dư âm |
| Audit | State/money/config mutation có audit đúng actor, from/to state và timestamp |
| Async notifications | Email failure không rollback transaction chính |
| UI quality | Loading/Empty/Error, Vietnamese diacritics, keyboard/accessibility và responsive states |

## Critical Verification Matrix

### Authentication and Booking

| Test ID | UC | Scenario | Expected Result |
|---------|----|----------|-----------------|
| CV-001 | UC-007 | Đăng ký bằng payload hợp lệ | Customer `PENDING_VERIFY`, token/audit tạo, email enqueue |
| CV-002 | UC-007 | Email đã tồn tại | HTTP 409, không tạo user/token/audit đăng ký mới |
| CV-003 | UC-007 | SMTP lỗi sau commit | Customer vẫn tồn tại, email được retry async |
| CV-004 | UC-009 | Login đúng Customer | Access/refresh token hợp lệ, redirect Customer home |
| CV-005 | UC-009 | Login đúng Driver chưa ACTIVE | HTTP 403 kèm onboarding current step |
| CV-006 | UC-009 | Sai password 5 lần | Lock 15 phút atomic, lần tiếp theo không verify password |
| CV-007 | UC-009 | Vượt rate limit IP | HTTP 429 kèm retry time |
| CV-008 | UC-012 | Hoàn tất đủ 6 bước | Một order `PENDING_PAYMENT`, pricing snapshot và VNPay intent |
| CV-009 | UC-012 | Confirm double-click | Một order duy nhất, response được replay |
| CV-010 | UC-012 | OSRM unavailable | Dùng district fallback và hiển thị nhãn “ước tính” |
| CV-011 | UC-012 | Draft quá hạn | HTTP 410, không confirm hoặc tạo order |

### Customer Orders

| Test ID | UC | Scenario | Expected Result |
|---------|----|----------|-----------------|
| CV-012 | UC-016 | Active order có location mới | Marker, ETA và timeline được cập nhật |
| CV-013 | UC-016 | Location stale | Hiển thị last-updated/stale, không gọi là real-time |
| CV-014 | UC-016 | Customer xem order người khác | HTTP 403, không trả location/Driver detail |
| CV-015 | UC-016 | Network gián đoạn | Giữ dữ liệu cuối và retry có backoff |
| CV-016 | UC-019 | Rating hợp lệ trong 2 giờ | Một rating, aggregate Driver cập nhật |
| CV-017 | UC-019 | Rating lần hai | HTTP 409, không duplicate |
| CV-018 | UC-019 | Rating sau 2 giờ | HTTP 409 `RATING_WINDOW_EXPIRED` |
| CV-019 | UC-019 | Rating order chưa completed | HTTP 409, zero write |

### Driver Onboarding and Workflow

| Test ID | UC | Scenario | Expected Result |
|---------|----|----------|-----------------|
| CV-020 | UC-024 | Upload đủ checklist hợp lệ | Documents/vehicle lưu, Driver `PENDING_DEPOSIT` |
| CV-021 | UC-024 | File quá 5 MB hoặc MIME sai | HTTP 422 trước Cloudinary |
| CV-022 | UC-024 | License/plate trùng | HTTP 409, Driver giữ `PENDING_DOCUMENTS` |
| CV-023 | UC-024 | Cloudinary thành công, confirm lỗi | Không complete step; asset orphan cleanup async |
| CV-024 | UC-025 | IPN hợp lệ amount 3M | Deposit completed, Driver `PENDING_APPROVAL` |
| CV-025 | UC-025 | IPN HMAC sai | `RspCode=97`, zero mutation |
| CV-026 | UC-025 | Hai IPN success đồng thời | Một completed deposit/transaction/audit |
| CV-027 | UC-025 | Return URL giả success | DB không đổi khi chưa có verified IPN |
| CV-028 | UC-030 | Driver accept hợp lệ | Assignment/order state commit, audit một lần |
| CV-029 | UC-030 | Accept và reassign đồng thời | Một outcome, request thua HTTP 409 |
| CV-030 | UC-030 | Retry cùng idempotency key | Replay response, không duplicate audit |
| CV-031 | UC-030 | Driver từ chối vượt quota | HTTP 409, assignment không đổi |
| CV-032 | UC-032 | Complete sau verified final payment | Order completed, earning pending escrow |
| CV-033 | UC-032 | Complete trước final payment | HTTP 409, không tạo earning |
| CV-034 | UC-032 | Complete khi IN_DISPUTE | HTTP 409 theo HR-06 |
| CV-035 | UC-032 | Escrow worker chạy hai lần | Wallet credit/transaction đúng một lần |

### Driver and Admin Financial Operations

| Test ID | UC | Scenario | Expected Result |
|---------|----|----------|-----------------|
| CV-036 | UC-035 | Withdrawal request hợp lệ | Request `PENDING`, wallet chưa bị trừ |
| CV-037 | UC-035 | Hai request tranh available cuối | Một request hợp lệ commit dưới wallet lock |
| CV-038 | UC-035 | Amount vượt available | HTTP 422, không tạo request |
| CV-039 | UC-035 | Driver cancel pending | Request `CANCELLED`, wallet không đổi |
| CV-040 | UC-040 | Admin process hợp lệ | Wallet debit một lần, transaction/audit, `PROCESSED` |
| CV-041 | UC-040 | Admin reject hợp lệ | `REJECTED`, wallet không đổi, audit/email |
| CV-042 | UC-040 | Manager cố xử lý | HTTP 403, không reveal bank data |
| CV-043 | UC-040 | Process đồng thời | Một debit, request sau replay/409 |
| CV-044 | UC-040 | Audit insert thất bại | Rollback wallet, transaction và status |

### Approval, Disputes and Administration

| Test ID | UC | Scenario | Expected Result |
|---------|----|----------|-----------------|
| CV-045 | UC-037 | Manager approve hồ sơ đủ | Driver `ACTIVE`, audit/email, queue refresh |
| CV-046 | UC-037 | Hồ sơ thiếu document | HTTP 422 với blocking reasons |
| CV-047 | UC-037 | Hai Manager quyết định | Một outcome commit, request sau 409 |
| CV-048 | UC-037 | Admin gọi approval endpoint | HTTP 403 theo spec 008 |
| CV-049 | UC-043 | Refund Customer | Một positive refund, không Driver deduction |
| CV-050 | UC-043 | Deduct Driver đủ funds | Driver debit và Customer credit cùng amount |
| CV-051 | UC-043 | Deduct Driver thiếu funds | HTTP 422, rollback toàn bộ |
| CV-052 | UC-043 | Close No Fault | Terminal outcome, audit, zero money writes |
| CV-053 | UC-043 | Hai quyết định đồng thời | Một terminal outcome, không double money |
| CV-054 | UC-045 | Admin tải dashboard | Sáu KPI, hai charts, hai tables đúng dữ liệu |
| CV-055 | UC-045 | Non-Admin tải dashboard | HTTP 403 và redirect đúng role |
| CV-056 | UC-045 | Một chart API lỗi | KPI/tables vẫn render, chart có retry state |
| CV-057 | UC-054 | Update settings hợp lệ | Version tăng, history/audit/outbox atomic |
| CV-058 | UC-054 | Settings validation sai | HTTP 422, không persist |
| CV-059 | UC-054 | Hai Admin update cùng version | Một winner, stale request HTTP 409 |
| CV-060 | UC-054 | Audit insert fail | Rollback settings/history/outbox |

## Final Defense Q&A Anchors

| Chủ đề hội đồng hỏi | Câu trả lời ngắn gọn có thể chứng minh |
|---------------------|----------------------------------------|
| Vì sao payment return URL không cập nhật DB? | Return URL do client kiểm soát; chỉ verified VNPay IPN được mutate payment theo HR-03/HR-04 |
| Làm sao chống nhận đơn đồng thời? | UC-030 lock assignment rồi order bằng `SELECT ... FOR UPDATE`; request thua nhận HTTP 409 |
| Làm sao chống double debit withdrawal? | Lock withdrawal → wallet, unique related withdrawal transaction và idempotency/bank reference |
| Driver có tự hoàn thành dispute không? | Không; HR-06 chặn Driver complete khi dispute, HR-07 chỉ Manager/Admin resolve |
| Earnings được cộng lúc nào? | UC-032 snapshot khi complete, credit wallet sau escrow 2 giờ nếu không có dispute |
| Vì sao pending withdrawal không trừ wallet? | Pending chỉ reserve logic; wallet chỉ debit atomic khi Admin mark `PROCESSED` |
| Manager hay Admin xử lý withdrawal? | Spec 009 canonical là Admin; Manager route legacy nhận HTTP 403 |
| Vì sao pricing không đổi order cũ? | Mỗi order lưu pricing/commission snapshot tại thời điểm tạo |
| Cloudinary được bảo vệ thế nào? | Signed upload, validate MIME/size, signed read URL TTL 1 giờ, không lưu BLOB/Base64 |
| Audit fail thì sao? | Với critical state/money/config mutation, audit nằm cùng transaction nên toàn bộ rollback |
| Email fail thì sao? | Email async sau commit và retry; HR-11 cấm rollback transaction chính vì SMTP lỗi |
| Tiền có dùng `double` không? | Không; dùng BigDecimal/NUMERIC VND nguyên đồng theo AC-08 |
| UI xử lý API chậm/lỗi thế nào? | Mỗi data-driven page có Loading/Empty/Error và retry theo AC-16 |
| Làm sao chống lost update settings? | UC-054 optimistic version; stale Admin nhận HTTP 409 |
| 15 UC này được chọn vì sao? | Chúng bao phủ auth, core booking, real-time, onboarding, state machine, concurrency và money flows |
