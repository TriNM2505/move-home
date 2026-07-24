<!-- PR template Move_home — checklist Self-Check (governance). Xoá phần không liên quan. -->

## Tóm tắt
<!-- Thay đổi gì? Vì sao? Liên quan spec/issue nào? -->


## Loại thay đổi
- [ ] feat  - [ ] fix  - [ ] refactor  - [ ] docs  - [ ] test  - [ ] chore

---

## ✅ Self-Check (bắt buộc — xem `AGENTS.md` + `constitution.md`)

### Layer 1 — Hard Rules (critical; CI chỉ check được 1 phần, còn lại tự xác nhận)
- [ ] **HR-01** Không commit `.env` / không hardcode secret (Cloudinary/JWT/VNPay/DB) — đọc từ `.env`.
- [ ] **HR-02** Password BCrypt, không log plaintext.
- [ ] **HR-04/15** IPN VNPay verify HMAC-SHA512 + idempotency (nếu chạm payment).
- [ ] **HR-10/17** RBAC đúng (403), public chỉ `/api/public/*`, không lộ PII.
- [ ] **HR-18/AC-08/AC-13** Tiền dùng `BigDecimal`; wallet ≥ 0; UPDATE wallet kèm INSERT transaction.
- [ ] **AC-12** Schema đổi qua Flyway migration (số do leader cấp); không `ddl-auto=update`.
- [ ] **HR-19/20** Brand forest green `#1B4D3E`; UI có dấu tiếng Việt.

### API (nếu tạo/sửa endpoint — xem `docs/api/README.md`)
- [ ] OpenAPI/annotation cập nhật **cùng PR**.
- [ ] DTO có `@Valid` → 422; error theo format `{ error_code, message, details }`.

### Test & Constraints
- [ ] `mvn test` xanh (CI sẽ chạy lại — Validation Gate).
- [ ] Không thêm dependency ngoài `constraints/global.md §3` (nếu thêm → đã hỏi leader).
- [ ] Không vi phạm `constraints/safety.md` (không xóa data/migration, không boot Neon).

### Nếu chạm tiền / auth / IPN / quyền
- [ ] Đã chạy **AI Self-Check Protocol** (constitution) và trích rõ mã HR/AC trong mô tả PR.
