# constraints/safety.md — Guardrail AN TOÀN của Agent ("last line of defense")

**Version:** 1.0.0 | **Cập nhật:** 2026-07-24 | **Maintainer:** Tech lead (leader)
**Enforcement:** **Human approval gate (nghiêm nhất)** — không cho merge nếu vi phạm.

> Ngăn agent làm hành động **không thể hoàn tác** khi thiếu context hoặc bị "confused".
> Trả lời câu hỏi: **"Agent KHÔNG được phép làm gì / phải hỏi trước khi làm gì?"**
> 📎 Canonical liên quan: constitution **HR-01** (secrets), **AC-12** (Flyway migration).

---

## 1. Data Safety (⛔ blocking — cần leader confirm)

**KHÔNG được:**
- `DROP TABLE` / `TRUNCATE` trong migration file.
- `DELETE FROM ...` **không có** `WHERE` (= thảm họa mất data).
- **Xóa migration cũ** đã có trong repo.
- Đổi kiểu (type) của cột đang có data mà chưa hỏi (migration risk).
- Chạy `mvn spring-boot:run` / boot app lên **Neon** — tự áp migration lên **DB dùng chung của team**. **Chỉ compile / test-compile.**

**PHẢI:**
- Trước mọi thay đổi schema: nhắc leader "Đã backup/checkpoint chưa?" + có rollback plan.
- Migration mới `V{n}__*.sql`: **leader cấp số**, tuyệt đối không tự đoán.

---

## 2. Code Safety (⛔ hỏi trước)

**KHÔNG tự ý (phải hỏi leader trước):**
- `git add` / `commit` / `push` / `merge` / `checkout` (đổi nhánh) / `branch` (tạo nhánh).
- Thêm dependency vào `pom.xml` — xem `constraints/global.md` §5 (quy trình duyệt).
- Sửa `SecurityConfig`, cấu hình JWT / RBAC / CORS.
- Sửa `application.properties` (phần credentials/URL), `.env`, `.env.example`.
- Sửa file hiến pháp: `AGENTS.md`, `CLAUDE.md`, `.specify/**` (gồm constitution + constraints), `spec.md`,
  `docs/CONTEXT.md`, `docs/PROJECT_KNOWLEDGE_FULL.md`.
- Sửa **>3 file cùng lúc** — tóm tắt kế hoạch trước, chờ duyệt.
- Đụng vào **known issues** (vd dọn 2 entity `ServiceOrder`/`Order`; nới cột status).

---

## 3. Production & Secret Safety

- **KHÔNG** hardcode credentials (Cloudinary key, JWT secret, DB password, VNPay hash) — đọc qua
  `@Value` / `@ConfigurationProperties` từ `.env` (**HR-01**).
- **KHÔNG** log dữ liệu nhạy cảm (password, secret, PII chưa mask — xem `business.md` §6).
- **KHÔNG** bypass auth middleware "cho nhanh".
- **KHÔNG** sinh màu Stripi purple `#533afd` (lỗi thời) — luôn brand thật `#1B4D3E` + `#F5A623` (HR-19).

---

## 4. Khi KHÔNG chắc chắn — quy tắc mặc định

- **Dừng lại và báo cáo, KHÔNG assume.** "Tôi không chắc về constraint X. Anh muốn làm thế nào?"
- Phát hiện bug/mâu thuẫn/thiếu info → đưa **options ưu-nhược** cho leader, không tự quyết.
- **GIỮ SCOPE:** chỉ sửa đúng thứ được yêu cầu; chỗ khác cần sửa → ghi TODO, đừng tự đụng.
- Better to ask and be slow than assume and be wrong.

---

## 5. Verify sau khi code (self-check compliance)

Sau khi viết/sửa code, tự kiểm và báo ✅ PASS / ❌ FAIL từng dòng:

```
## Global (constraints/global.md)
- [ ] Không thêm library ngoài approved list?
- [ ] Naming conventions đúng?
## Business (constraints/business.md)
- [ ] Auth đúng thuật toán (BCrypt 12 / HS256)? Tiền dùng BigDecimal?
- [ ] PII không xuất hiện raw trong logs?
## Safety (file này)
- [ ] Không DELETE thiếu WHERE, không DROP/TRUNCATE?
- [ ] Không hardcode credentials? Không tự chạy git/boot Neon?
```
Có ❌ FAIL → **fix trước khi submit.** Chạm tiền/auth/IPN/quyền → chạy thêm AI Self-Check Protocol trong constitution.
