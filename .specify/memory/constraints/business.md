# constraints/business.md — Ràng buộc NGHIỆP VỤ

**Version:** 1.0.0 | **Cập nhật:** 2026-07-24 | **Maintainer:** Product/Business + Dev
**Enforcement:** Code review + CI (logic nghiệp vụ — cần con người verify, không auto 100%).

> Những rule "hiển nhiên với người nhưng AI không biết trừ khi được nói" (anti-pattern *Implicit
> Assumption* §5.2.9). Trả lời câu hỏi: **"Nghiệp vụ hoạt động ra sao?"**
> ⚠️ Giá trị dưới đây là **của Move_home** (BCrypt/HS256), **KHÔNG** phải values mẫu của sách (argon2id/RS256).

---

## 1. Authentication & Authorization

- **Password hash:** BCrypt **cost 12**. KHÔNG dùng argon2id/md5/sha1/sha256. Không bao giờ log plaintext.
- **JWT:** thuật toán **HS256** (KHÔNG RS256) · access token **15 phút** · refresh token **7 ngày**,
  lưu DB (hash), **rotation + reuse-detection** (dùng lại token cũ → revoke toàn bộ token của user).
- **Login security:** rate-limit **5 lần/IP/15 phút** (vượt → 429) + account lockout **5 sai liên tiếp**
  → khóa 15 phút (`locked_until`), đang khóa dù đúng password vẫn trả **423**.
- **RBAC:** kiểm role từ JWT theo bảng CONTEXT §3; trái quyền → **403** (không trả 401/404 để giấu endpoint).
- **Public endpoint:** chỉ dưới `/api/public/*` (bypass JWT); không lộ PII/dữ liệu người khác.

> 📎 Canonical: constitution **HR-02** (password), **AC-03** (JWT), **HR-16** (rate limit/lockout),
> **HR-10** (RBAC 403), **HR-17** (public vs authenticated).

---

## 2. Tiền & Ví (money-critical)

- Mọi field tiền: `java.math.BigDecimal` **scale=0**; DB `NUMERIC(15,0)`. KHÔNG `double`/`float`.
- JSON serialize tiền = integer (`12500000`, không `"12500000.00"`).
- `wallet.balance` / `deposit_balance` **KHÔNG bao giờ âm** (DB CHECK + service validate).
- Mọi UPDATE wallet PHẢI đi kèm 1 INSERT `transaction` trong **cùng 1 DB transaction** (audit trail).
- Chia bồi thường 50/50: company `CEILING`, driver `FLOOR` (tổng = gốc).

> 📎 Canonical: constitution **AC-08** (BigDecimal), **HR-18** (wallet ≥ 0), **AC-13** (audit trail).

---

## 3. Datetime

- DB: luôn `TIMESTAMP WITH TIME ZONE`, lưu **UTC**. API JSON: ISO 8601 UTC.
- Server logic: `Instant` (UTC). Display FE: convert `Asia/Ho_Chi_Minh` khi trả về.
- Peak-hour (7–9h, 17–19h): PHẢI convert `Asia/Ho_Chi_Minh` **trước** khi so sánh (không so UTC hour trực tiếp).
- Property bắt buộc: `spring.jackson.time-zone=Asia/Ho_Chi_Minh`.

> 📎 Canonical: constitution **AC-07**.

---

## 4. Data Management

- **Soft delete:** entity nghiệp vụ dùng cột `deleted_at TIMESTAMPTZ NULL` + `@SQLDelete`/`@SQLRestriction`;
  query mặc định filter `deleted_at IS NULL`. KHÔNG `DELETE FROM`.
  Hard delete chỉ cho: `refresh_token` (logout/expire), `chat_message` > 90 ngày. Audit log KHÔNG xóa.
- **Migration:** mọi thay đổi schema qua Flyway `V{n}__*.sql`; `ddl-auto=validate` mọi environment.
- **Status field:** `VARCHAR + CHECK`, Java `String` — KHÔNG PostgreSQL ENUM.

> 📎 Canonical: constitution **AC-09** (soft delete), **AC-12** (Flyway), **AC-14** (status VARCHAR+CHECK).

---

## 5. API Rules

- **Pagination:** list >50 records → server-side `Pageable`/`Page<T>` (default size 10, max 100);
  list nhỏ → `List<T>` + client-side paginate.
- **Validation:** request body DTO `@Valid` + Jakarta Bean Validation; vi phạm → **422** kèm danh sách field.
- **Error format thống nhất:** `{ "error_code": "...", "message": "...", "details": [...] }` (không plain string/HTML/stack trace).
- **Empty/Loading/Error states:** mỗi list page/data-driven page PHẢI có đủ 3 trạng thái (tiếng Việt).

> 📎 Canonical: constitution **AC-15** (pagination), **ES-03** (validation 422), **ES-04** (error format), **AC-16** (states).

---

## 6. PII & Logging (masking bắt buộc)

Khi log/hiển thị nội bộ, **mask** dữ liệu cá nhân:

| Loại | Cách mask | Ví dụ |
|------|-----------|-------|
| Số điện thoại | ẩn 3 số giữa | `0912***456` |
| Email | ẩn phần local | `use***@domain.com` |
| **KHÔNG BAO GIỜ log** | password, thẻ thanh toán, số CCCD/CMND, JWT secret, VNPay hash | — |

> 📎 Canonical: constitution **HR-02** (không log plaintext password), **HR-17** (không lộ PII qua public), **HR-01** (secrets).
> *Bổ sung mới của file này: format mask cụ thể cho phone/email — chưa có trong constitution.*

---

## 7. Domain Glossary (thuật ngữ dễ hiểu sai — nguồn đầy đủ: `docs/CONTEXT.md §2`)

| Thuật ngữ | Nghĩa chuẩn trong Move_home |
|-----------|------------------------------|
| **Order / ServiceOrder** | Đơn chuyển nhà. Bảng DB tên `service_order` (KHÔNG `order` — reserved word). Entity dùng thật = `order.ServiceOrder`. |
| **Trip** | Lần thực thi 1 Order, sinh khi Manager phân công (1 Order + 1 Driver + 1 Vehicle). |
| **Deposit (cọc)** | 30% total_quote khách trả khi đặt = chính là commission công ty giữ. |
| **Final Payment** | 70% khách trả qua VNPay tại chỗ trước khi Driver bấm Hoàn thành (KHÔNG COD). |
| **Commission** | 30% × total_quote (snapshot vào `commission_rate_snapshot` khi tạo đơn). |
| **Escrow** | 2 giờ sau COMPLETED; hết 2h không khiếu nại → 70% vào ví Driver. |
| **Wallet** | Ví Driver (bảng `driver_wallet`); `balance` ≥ 0. Ví Customer (`customer_wallet`) đang BLOCKED chờ leader (#021). |
| **Driver Deposit** | Cọc 3 triệu collateral cho DamageReport (trừ trước → hết thì trừ ví → hết thì SUSPENDED). |
| **DamageReport / dispute** | Khiếu nại hư hỏng. Bảng DB canonical = **`dispute`** (không `damage_report`); status `OPEN/INVESTIGATING/RESOLVED_*/CLOSED_NO_FAULT` (spec #010). |
| **RefundRecord** | Chỉ tạo khi COMPANY hủy (lỗi công ty). Khác `order_cancellation_refund` (khách hủy sớm khi chưa có tài xế). |

> ⚠️ Lưu ý drift đã biết: bộ status Order code có 11 giá trị (V21) vs CONTEXT mô tả 8 — tin code/migration khi lệch.
