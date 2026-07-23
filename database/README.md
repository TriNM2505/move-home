# Database — Move_home

Tài liệu tham khảo schema cơ sở dữ liệu cho backend **Move_home** (marketplace dịch vụ chuyển nhà nội thành Hà Nội — SWP @ FPT).

> **Stack DB:** PostgreSQL 16 (Neon Cloud, region Singapore) · **Migration:** Flyway (Spring Boot tự chạy) · **Backend:** Spring Boot 3 + Java 17.

---

## ⚠️ Đọc trước: folder này là TÀI LIỆU THAM KHẢO

Khác với kiểu "chạy script tay tạo DB", ở Move_home **nguồn schema thật là các file Flyway migration** trong `backend/src/main/resources/db/migration/` (`V1__…` → `V44__…` + `V99__seed…`). Backend **tự động áp migration** khi khởi động (`spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate`).

Folder `database/` này **gộp lại schema cuối cùng** của từng bảng để **đọc/hiểu/ôn tập/báo cáo** cho nhanh (thay vì lần theo 44 migration rải rác có nhiều `ALTER`).

- ✅ Dùng để: đọc cấu trúc bảng, làm ERD, viết báo cáo, chạy thử trên **DB local rỗng của riêng bạn**.
- ⛔ **KHÔNG** chạy các file này lên Neon dùng chung — sẽ lệch/đụng với Flyway. Muốn tạo DB thật → để backend chạy Flyway (xem "Cách DB thật được tạo").

---

## File

| File | Nội dung |
|------|----------|
| `01_app_user.sql` … `32_driver_incident_photo.sql` | **32 file — mỗi bảng 1 file.** Là schema **hợp nhất (final state)**: đã gộp hết `CREATE TABLE` + các `ALTER` về sau + index + constraint + trigger. Đầu mỗi file có comment ghi **tác dụng của bảng** + migration nguồn + điều luật (HR/AC) liên quan. |
| `MoveHome_DB.sql` | **Toàn bộ schema** — ghép 32 file trên theo đúng thứ tự phụ thuộc FK, bọc trong `BEGIN … COMMIT`. Chạy 1 lần trên 1 DB rỗng là ra đủ 32 bảng. |
| `README.md` | File này. |

> Các file được **đánh số theo thứ tự phụ thuộc** (bảng cha trước bảng con) nên `MoveHome_DB.sql` = nối `01 → 32` chạy không lỗi khóa ngoại. Hàm dùng chung `update_updated_at_column()` (cho trigger `updated_at`) được tạo **một lần** ở `01_app_user.sql`.

---

## Danh mục 32 bảng (theo miền)

| Miền | Bảng (file) |
|------|-------------|
| **Auth / User (5)** | `app_user` (01), `email_verification_token` (02), `refresh_token` (03), `password_reset_token` (04), `login_event` (05) |
| **Driver (4)** | `driver_profile` (06), `driver_document` (07), `driver_location` (10), `driver_wallet` (11) |
| **Order (3)** | `service_order` (08), `order_rating` (09), + `driver_location` gắn theo đơn |
| **Ví & Tiền (6)** | `customer_wallet` (12), `withdrawal_request` (13), `customer_withdrawal_request` (14), `transaction` (19), `commission_settings` (20), `commission_settings_history` (21) |
| **Dispute (4)** | `dispute` (15), `dispute_evidence` (16), `dispute_comment` (17), `dispute_photo` (18) |
| **Chat (2)** | `conversation` (22), `chat_message` (23) |
| **Hủy đơn (2)** | `order_cancellation_refund` (26), `order_cancellation_photo` (27) |
| **Blog cộng đồng (3)** | `blog_post` (28), `blog_post_photo` (29), `blog_comment` (30) |
| **Sự cố tài xế (2)** | `driver_incident_report` (31), `driver_incident_photo` (32) |
| **Khác (2)** | `notification` (24), `audit_log` (25) |

> `transaction` (sổ cái tiền append-only) là **nguồn sự thật cho mọi luồng tiền**; `service_order` là **thực thể trung tâm**; `app_user` là bảng lõi 4 vai trò.

---

## Cách DB thật được tạo (Flyway — không chạy tay)

**Bước 1 — Cấu hình `.env`** (KHÔNG commit — theo HR-01). Backend đọc qua `application.properties`:

```dotenv
DB_URL=jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require
DB_USERNAME=<user>
DB_PASSWORD=<password>
JWT_SECRET=<chuoi-ngau-nhien-toi-thieu-32-ky-tu>
# ... VNPAY_*, CLOUDINARY_*, MAIL_* (xem application.properties)
```

**Bước 2 — Chạy backend** → Flyway tự áp toàn bộ `V1..V44` rồi seed `V99` lên DB trống:

```bash
cd backend
./mvnw spring-boot:run
```

**Bước 3 — Tài khoản demo:** do `V99__seed_demo_data.sql` tạo (admin / manager / driver / customer). Mật khẩu demo lưu dạng BCrypt trong seed — xem trực tiếp `V99__seed_demo_data.sql` để lấy đúng thông tin đăng nhập, **không hardcode** ở đây.

> ⚠️ Neon là **DB dùng chung của cả team**. Đừng tự chạy migration/script lạ lên đó. Muốn thử schema → dùng Postgres local của riêng bạn (xem dưới).

---

## Cách dùng folder này (tùy chọn — trên Postgres LOCAL của bạn)

Nếu chỉ muốn xem/nghịch schema mà không đụng Neon:

```bash
# Tao 1 DB local rong roi nap toan bo schema
createdb movehome_ref
psql -d movehome_ref -f MoveHome_DB.sql

# Hoac chi nap 1 bang de xem (luu y: bang co FK/trigger can bang cha + ham o 01 truoc)
psql -d movehome_ref -f 01_app_user.sql
```

- File `MoveHome_DB.sql` đã bọc `BEGIN … COMMIT` → lỗi giữa chừng sẽ rollback sạch.
- Các file bảng lẻ có ghi rõ **"Phụ thuộc"** ở header (bảng cha + hàm trigger) nếu cần chạy riêng.

---

## Ghi chú

- **Nguồn sự thật schema = Flyway migration `V1..V44`**, KHÔNG phải folder này. Sau khi thêm/sửa migration, cập nhật lại các file ở đây cho khớp (thủ công).
- Schema tuân thủ hiến pháp dự án: `TIMESTAMPTZ` lưu UTC (AC-07); tiền `NUMERIC(15,0)` VND nguyên đồng (AC-08); soft delete `deleted_at` (AC-09); status dùng `VARCHAR + CHECK` không dùng PostgreSQL ENUM (AC-14); tên bảng tránh reserved word — `app_user`/`service_order` (HR-21).
- Một vài "vết" lịch sử được giữ nguyên để đúng với DB thật: `service_order.status` còn lẫn cặp legacy/mới (`PENDING`~`PENDING_PAYMENT`, `ASSIGNED`~`ACCEPTED`, `DISPUTED`~`IN_DISPUTE`); index `uq_dispute_driver_deduction` lọc theo `type='DISPUTE_DEDUCTION'` — loại này đã bị bỏ ở V21 nên thực tế không khớp dòng nào (xem comment trong `19_transaction.sql`).
- Số bảng: **32 bảng ứng dụng** + `flyway_schema_history` (Flyway tự tạo) = 33 bảng vật lý trên Neon.
