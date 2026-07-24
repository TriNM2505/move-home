# docs/api/ — API Contract (OpenAPI)

> Nguồn sự thật cho REST API. Trả lời câu hỏi **"API này hoạt động thế nào?"**
> Base URL: `http://localhost:8080` · Auth: `Authorization: Bearer <JWT>` · Format: JSON UTF-8.

---

## 1. Chính sách "API-First" (ARCH-01)

> **THE team SHALL** viết/update đặc tả API **TRƯỚC KHI** implement endpoint MỚI.

**Process bắt buộc cho endpoint MỚI:**
1. Mô tả endpoint (path, method, request/response schema) — qua annotation OpenAPI trong controller
   (hoặc bổ sung vào `openapi.yaml` nếu viết tay).
2. Review approval ≥ 1 team member.
3. **Sau** approval → implement code.

> ⚠️ **Bối cảnh Move_home (trung thực):** 28 controller **đã build xong** trước khi có policy này →
> API-First **áp dụng cho endpoint mới từ nay**; endpoint cũ được "hồi tố" dần bằng cách bổ sung
> annotation OpenAPI (không viết tay lại toàn bộ). Đây là điều chỉnh thực dụng so với bản CI-enforce
> đầy đủ của sách (Move_home hiện chưa có CI để enforce `build fail nếu code ≠ openapi.yaml`).

---

## 2. Cách sinh OpenAPI (auto-generated — chuẩn sách)

Sách khuyến nghị **auto-generate từ annotation** thay vì viết tay. Move_home dùng **springdoc-openapi**:

- Dependency: `org.springdoc:springdoc-openapi-starter-webmvc-ui` *(⏳ chờ leader duyệt thêm vào `pom.xml` — xem `constraints/safety.md §2`)*.
- Sau khi thêm:
  - JSON spec: `GET /v3/api-docs`
  - Swagger UI: `GET /swagger-ui.html`
  - Export file tĩnh (nếu cần check-in): lưu `docs/api/openapi.yaml` từ `/v3/api-docs.yaml`.
- Enrich bằng annotation trong controller: `@Operation`, `@ApiResponse`, `@Schema`, `@Parameter`.

---

## 3. Endpoint catalog (28 `@RestController` — chi tiết: spec 001–026)

| Base path | Controller | Vai trò | Spec |
|-----------|-----------|---------|------|
| `/api/auth` | AuthController | Public/Auth | 001 |
| `/api/customer/profile` | ProfileController | Customer | 004 |
| `/api/customer/wallet` | WalletController | Customer | 021 |
| (customer orders) | OrderController, CustomerQuoteController | Customer | 002/003 |
| `/api/driver/orders` | DriverOrderController, DriverOrderQueryController | Driver | 006 |
| `/api/driver` | DriverWalletController | Driver | 007 |
| `/api/driver/profile` · `/documents` · `/location` | DriverProfile/Document/Location Controller | Driver | 005/006 |
| `/api/manager/drivers` | ManagerDriverApprovalController | Manager | 008 |
| `/api/manager/driver-ratings` | ManagerDriverRatingController | Manager | 026 |
| `/api/manager/cancellation-refunds` | ManagerCancellationRefundController | Manager | 022 |
| (manager disputes) | DisputeController | Manager/Admin | 010 |
| `/api/admin/dashboard` | AdminDashboardController | Admin | 015 |
| `/api/admin` | AdminListController, AdminDetailController | Admin | 011/012 |
| `/api/admin/transactions` | AdminTransactionController | Admin | 013 |
| `/api/admin/withdrawals` · `/customer-withdrawals` | AdminWithdrawal / AdminCustomerWithdrawal | Admin | 009/021 |
| `/api/admin/settings/commission` | AdminCommissionSettingsController | Admin | 014 |
| `/api/admin/audit-logs` | AuditLogController | Admin/Manager | 025 |
| `/api/admin/users` | AdminUserAccountController | Admin | 012 |
| `/api/chat` | ChatController | Tất cả | 019 |
| `/api/notifications` | NotificationController | Tất cả | 020 |
| `/api/public/*` | PublicQuoteController | Guest | 017 |
| `/api/vnpay/*` | VnPayController | System (IPN) | 002/005/021 |

> ⚠️ **KHÔNG có:** Admin Reports (`/api/admin/reports` — gỡ, D-15); contact (`/api/public/contact` — chưa build, D-16); driver incident (chưa build, D-12).

---

## 4. Definition of Done cho mọi task tạo/sửa endpoint (cùng PR)

- [ ] OpenAPI (annotation/spec) được update **cùng PR** với code — không tách riêng.
- [ ] Request DTO có `@Valid` + Bean Validation → vi phạm trả **422** (ES-03).
- [ ] Error theo format `{ error_code, message, details }` (ES-04).
- [ ] Endpoint đánh dấu rõ **PUBLIC** (`/api/public/*`) hay **AUTHENTICATED** (HR-17); RBAC đúng (HR-10).
- [ ] Endpoint chạm tiền/auth/IPN → chạy AI Self-Check Protocol, trích HR/AC.

> 📎 Canonical: constitution **AC-02** (REST thuần), **ES-02** (noun plural), **ES-03/04** (validation/error),
> **HR-10** (RBAC), **HR-17** (public vs authenticated).
