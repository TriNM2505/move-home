<!--
=== SYNC IMPACT REPORT ===
Version Change: 1.2.0 → 1.3.0
Date: 2026-06-04
Type of bump: MINOR (thêm HR-19/20/21, AC-14/15/16, ES-07/08, D10/D11/D12/D13)

Amendment v1.3.0 (2026-06-04):
- Source: Sprint 1 complete + brand migration + 65-screen scope + team workflow.
- HR-19: THÊM (Brand identity locked - forest green primary + amber accent + Be Vietnam Pro font).
- HR-20: THÊM (Vietnamese diacritics mandatory trong tất cả UI text).
- HR-21: THÊM (DB tables không dùng PostgreSQL reserved words - app_user not user, service_order not order).
- AC-14: THÊM (Status fields VARCHAR + CHECK constraint, NOT ENUM type).
- AC-15: THÊM (Pagination pattern - server-side cho list lớn, client-side cho list nhỏ).
- AC-16: THÊM (Empty/Loading/Error states mandatory cho mỗi list page).
- ES-07: THÊM (Vietnamese commit message OK với English type prefix).
- ES-08: THÊM (Multi-AI workflow - Claude Code cho phase lớn, Codex CLI cho fixes nhỏ).
- D10: THÊM (Brand migration Stripi → Move_home, v0.9-brand-migration milestone).
- D11: THÊM (65-screen scope chia 6 sprints, Sprint 1 đã DONE 12 screens).
- D12: THÊM (SDD framework SpecKit - specs/ + .specify/ pattern).
- D13: THÊM (Multi-AI workflow strategy - Claude + Codex song song).
- Layer 1: 18 → 21 HR
- Layer 2: 13 → 16 AC
- Layer 3: 6 → 8 ES
- Decisions: 9 → 13
- Update HR-08, HR-12 wording theo marketplace pivot D9.

Templates:
  - .specify/memory/constitution.md          ✅ (this file)
  - DESIGN.md                                ✅ Synced (v1 → v2 brand migration)
  - docs/SCREEN_INVENTORY.md                 ✅ Sync với D11 (65 screens scope)
  - docs/SCREEN_TASKS.md                     ✅ Sync với team workflow

Deferred TODOs (vẫn pending từ v1.2.0):
  - TODO(DECISION_MAKER): Leader/PM
  - TODO(DEPLOY_PROVIDER): Backend cloud
  - TODO(DEMO_DATE): Final defense date
  - TODO(CLOUDINARY_CREDS)
  - TODO(CORS_PROD_ORIGIN)

---

Version Change: 1.1.0 → 1.2.0
Date: 2026-05-29
Type of bump: MINOR (thêm HR-17, HR-18, AC-13, D9; sửa HR-12 theo marketplace pivot)

Amendment v1.2.0 (2026-05-29):
- Source: CONTEXT.md v2.0 (MAJOR pivot marketplace).
- HR-12: SUA content (Driver gio tu dang ky qua 4 buoc onboarding).
- HR-17: THEM (Public vs Authenticated endpoints — Guest mode).
- HR-18: THEM (Wallet balance khong am, audit trail).
- AC-13: THEM (Money flow audit trail — wallet_transaction structure).
- D9: THEM Decision (MAJOR PIVOT v2.0 — thay duyet).
- Layer 1: 16 → 18 HR
- Layer 2: 12 → 13 AC
- Layer 3: khong doi (6 ES)
- Decisions: 8 → 9
- Thay duyet: 2026-05-29 (CONTEXT.md v2.0 da co ghi chu).

---

Version Change: 1.0.0 → 1.1.0
Date: 2026-05-29
Type of bump: MINOR (new rule AC-10 added to Layer 2)

Added:
  - AC-10: Cloudinary signed upload cho DamageReport photos (Layer 2)
    Source: team decision, Feature #8 (DamageReport)
    Note: AC-10 references AC-09 (soft-delete) — AC-09 not yet defined;
          treat as forward reference / TODO for next amendment.

History:
  - v1.0.0 (2026-05-29): Initial constitution from CONTEXT.md v1.5
    Layer 1: HR-01..HR-14 (14 rules)
    Layer 2: AC-01..AC-06  (6 rules)
    Layer 3: ES-01..ES-06  (6 rules)
  - v1.1.0 Lượt 1 (2026-05-29): Add AC-10 Cloudinary upload constraint
    Layer 2: AC-01..AC-06, AC-10 (7 rules; AC-07/08/09 gap intentional at time)
  - v1.1.0 Lượt 2 (2026-05-29): Bổ sung 5 rule bị sót ở lượt 1
    Layer 1: thêm HR-15 (idempotency IPN), HR-16 (rate limit + lockout) → 16 rules
    Layer 2: thêm AC-07 (timezone UTC+7), AC-08 (BigDecimal VND), AC-09 (soft delete) → 10 rules
  - v1.1.0 Lượt 3 (2026-05-29): Hoàn tất — thêm AC-11, AC-12, D6, D7, D8
    Layer 2: thêm AC-11 (CORS whitelist), AC-12 (Flyway migration) → 12 rules
    Project Decisions: thêm D6 (BigDecimal/VND), D7 (Cloudinary), D8 (Flyway) → 8 quyết định
    Constitution v1.1.0 chính thức đầy đủ: 16 HR + 12 AC + 6 ES = 34 rules
    External services tổng cộng: Google Maps + VNPay + Gmail SMTP + Cloudinary (4 service,
    cần quản lý 4 bộ credentials).

Templates:
  - .specify/memory/constitution.md          ✅ (this file)
  - .specify/templates/plan-template.md      ✅ No structural change needed
  - .specify/templates/spec-template.md      ✅ No structural change needed
  - .specify/templates/tasks-template.md     ⚠️ PENDING — "Tests are OPTIONAL" note conflicts with ES-05
    (mandatory ≥70% for CORE). Add a note: "For CORE features, tests are required per ES-05.")

Deferred TODOs:
  - TODO(DECISION_MAKER): Người ra quyết định cuối khi conflict yêu cầu (§3 CONTEXT Q-open)
  - TODO(DEPLOY_PROVIDER): PostgreSQL/backend cloud provider chưa chốt (Q4)
  - TODO(DEMO_DATE): Ngày demo/nộp cuối chưa xác định (milestone timeline)
  - TODO(CLOUDINARY_CREDS): 3 env vars Cloudinary cần ghi vào deploy guide và README env template
  - TODO(CORS_PROD_ORIGIN): URL FE production cho AC-11 chưa xác định (chờ deploy provider Q4)
=== END SYNC IMPACT REPORT ===
-->

# Move_home Constitution

**Hệ thống Dịch Vụ Chuyển Nhà — SWP @ FPT University**

> **Source of Truth hierarchy:** `CONTEXT.md v2.0` → Constitution v1.3.0 → Specs → Code.
> Khi có mâu thuẫn giữa Constitution và CONTEXT, CONTEXT thắng. Báo ngay cho leader nhóm.

---

## LAYER 1 — HARD RULES

> Không bao giờ vi phạm. Review/CI fail ngay khi phát hiện vi phạm.
> AI KHÔNG được submit spec/code vi phạm Layer 1 — phải tự fix trước.

---

### HR-01 — Secrets không vào git

**Rule:** API keys (Google Maps, VNPay, Gmail SMTP) và mọi credential PHẢI lưu trong biến môi
trường. Không bao giờ hardcode hay commit vào git dưới bất kỳ hình thức nào.

**Lý do:** Leak credentials gây mất quyền kiểm soát merchant VNPay và tài khoản email công ty.

**Vi phạm → hậu quả:** Compromise toàn bộ payment flow; có thể dẫn đến thiệt hại tài chính thật
dù dùng VNPay Sandbox.

---

### HR-02 — Password không lưu plaintext

**Rule:** Password của Customer và Staff PHẢI được hash bằng BCrypt trước khi lưu DB.
Không bao giờ lưu, log, hay trả về plaintext password ở bất kỳ đâu.

**Lý do:** Plaintext password trong DB là lỗ hổng bảo mật nghiêm trọng nhất, vi phạm tiêu chuẩn
tối thiểu của mọi hệ thống.

**Vi phạm → hậu quả:** Toàn bộ tài khoản người dùng bị lộ khi DB bị tấn công hoặc log bị đọc.

---

### HR-03 — IPN là nguồn cập nhật thanh toán duy nhất

**Rule:** Trạng thái thanh toán của Order CHỈ được cập nhật từ VNPay IPN callback
(server-to-server). Return URL không bao giờ cập nhật DB, chỉ dùng để hiển thị kết quả cho khách.

**Lý do:** Chống giả mạo `?vnp_ResponseCode=00` từ phía client (URL tampering).

**Vi phạm → hậu quả:** Bug tiền bạc nghiêm trọng — đơn được xác nhận CONFIRMED mà không có tiền
thật vào merchant.

---

### HR-04 — Verify HMAC-SHA512 secure hash trước khi xử lý IPN

**Rule:** Mọi IPN request PHẢI verify HMAC-SHA512 secure hash của VNPay trước khi xử lý bất kỳ
logic nào. Hash không khớp → bỏ qua toàn bộ request, không thay đổi DB, trả `RspCode=97`.

**Lý do:** Chống tấn công giả mạo IPN từ bên ngoài không có VNPay secret key.

**Vi phạm → hậu quả:** Hacker có thể xác nhận đơn hàng mà không trả tiền chỉ bằng cách gửi
POST request đến IPN endpoint.

---

### HR-05 — Transition trạng thái không hợp lệ → HTTP 409

**Rule:** Mọi attempt chuyển trạng thái Order hoặc Trip không nằm trong bảng transition hợp lệ
(CONTEXT §2) PHẢI trả HTTP 409 Conflict, không thay đổi DB dưới bất kỳ hình thức nào.

**Lý do:** State machine là xương sống nghiệp vụ; state không nhất quán gây sự cố vận hành không
thể phục hồi tự động.

**Vi phạm → hậu quả:** Đơn có thể bỏ qua bước thanh toán, hoặc Driver bấm COMPLETED khi đơn
đang PENDING_PAYMENT.

---

### HR-06 — Driver không thể COMPLETED khi đơn IN_DISPUTE

**Rule:** API "Xác nhận hoàn thành" của Driver PHẢI kiểm tra Order không có DamageReport ở trạng
thái OPEN, NEGOTIATING, hoặc CUSTOMER_AGREED. Nếu có → từ chối, trả HTTP 409.

**Lý do:** Đảm bảo tranh chấp hư hỏng không bị bỏ qua mà không có quyết định của Manager.

**Vi phạm → hậu quả:** Driver tự đóng chuyến khi khách đang tranh chấp; khách mất quyền đòi
bồi thường; compensation_amount không được ghi nhận.

---

### HR-07 — Chỉ Manager/Admin chuyển IN_DISPUTE → COMPLETED

**Rule:** Transition IN_DISPUTE → COMPLETED chỉ được thực hiện bởi tài khoản role MANAGER hoặc
ADMIN. Mọi actor khác (Driver, Porter, Customer) gọi endpoint này → HTTP 403 Forbidden.

**Lý do:** Tranh chấp phải do Manager quyết định sau khi đàm phán với khách; không thể
để Driver hay khách tự giải quyết.

**Vi phạm → hậu quả:** Driver có thể dismiss tranh chấp để tránh bồi thường 50%; khách mất tiền
mà không có ai chịu trách nhiệm.

---

### HR-08 — Driver concurrency lock

**Rule:** Khi 2 Driver cùng click "Nhận đơn" tại cùng 1 order, chỉ 1 Driver được accept. Backend
dùng database lock (pessimistic) hoặc optimistic locking với version field. Driver thua cuộc
nhận HTTP 409 + message Vietnamese.

**Lý do:** Marketplace pivot D9 - Driver tự pick order, race condition phổ biến.

**Vi phạm → hậu quả:** 2 driver nhận cùng 1 order, customer confusion, một driver làm không công.

---

### HR-09 — IPN timeout 15 phút → auto-CANCELLED bằng Scheduled Job

**Rule:** Scheduled Job PHẢI tự động chuyển các Order ở trạng thái PENDING_PAYMENT sang
CANCELLED (`cancelled_by: SYSTEM`) nếu sau 15 phút kể từ thời điểm tạo đơn không có IPN hợp
lệ được xử lý.

**Lý do:** Đơn "zombie" treo vô thời hạn gây tắc nghẽn Manager và làm rối danh sách chờ phân
công.

**Vi phạm → hậu quả:** Hàng chục đơn PENDING_PAYMENT không thể dọn dẹp; Dashboard Manager bị
nhiễu; không thể xác định đơn nào cần phân công thật.

---

### HR-10 — Truy cập trái quyền → HTTP 403 Forbidden

**Rule:** Mọi endpoint PHẢI kiểm tra role từ JWT token theo bảng RBAC (CONTEXT §3). Tài khoản
không đủ quyền → HTTP 403 Forbidden. Không trả 401 hay 404 để che giấu sự tồn tại của endpoint.

**Lý do:** RBAC là ranh giới an ninh cứng giữa 5 role có quyền hoàn toàn khác nhau.

**Vi phạm → hậu quả:** Customer có thể phân công Trip; Driver có thể xem báo cáo doanh thu
Admin; Staff có thể tạo tài khoản mới.

---

### HR-11 — Email lỗi không rollback giao dịch chính

**Rule:** Tất cả email PHẢI gửi qua Spring `@Async` với dedicated thread pool riêng. Exception từ
email service KHÔNG được propagate lên để rollback transaction DB đang chạy.

**Lý do:** Email delivery không đảm bảo 100%; không thể hủy một giao dịch tài chính chỉ vì lỗi
SMTP tạm thời.

**Vi phạm → hậu quả:** Đơn đã cọc VNPay bị rollback khi Gmail SMTP timeout → mất tiền khách,
DB mất nhất quán, không thể hoàn tác.

---

### HR-12 — Driver tự đăng ký qua 4 bước onboarding

**Rule:** Driver tự register qua flow 4 bước:

- `PENDING_VERIFY` (email verification)
- `PENDING_DOCUMENTS` (upload bằng lái + ảnh xe)
- `PENDING_DEPOSIT` (đặt cọc 3,000,000 VND)
- `PENDING_APPROVAL` (manager review)
- → `ACTIVE` (work)

Manager/Admin KHÔNG tự đăng ký - phải seed qua migration hoặc admin invitation.
Customer tự đăng ký free, không cần approval.

**Lý do:** Marketplace pivot D9 - Driver là gig worker, không phải staff. Onboarding strict để
filter out tài xế không nghiêm túc (deposit gate).

---

### HR-13 — Audit log bắt buộc cho mọi thay đổi state

**Rule:** Mọi thay đổi state của Order, Trip, RefundRecord, DamageReport PHẢI ghi audit log với
tối thiểu: `actor_id`, `actor_role`, `timestamp`, `from_state`, `to_state`, `entity_id`.

**Lý do:** Truy vết nghiệp vụ và điều tra sự cố — đặc biệt quan trọng với luồng tiền bạc và
tranh chấp bồi thường.

**Vi phạm → hậu quả:** Không thể điều tra khi đơn bị CANCELLED sai; tranh chấp bồi thường không
có bằng chứng actor nào đã thay đổi gì và khi nào.

---

### HR-14 — RefundRecord chỉ tạo khi COMPANY hủy

**Rule:** RefundRecord PHẢI được tạo tự động khi và chỉ khi `cancelled_by = COMPANY`. CUSTOMER
cancel → không tạo RefundRecord (cọc thuộc công ty). SYSTEM cancel (timeout) → không tạo
RefundRecord (khách chưa cọc).

**Lý do:** Luồng hoàn tiền thủ công qua RefundRecord chỉ áp dụng khi lỗi phía công ty; nhầm lẫn
gây hoàn tiền không đúng đối tượng.

**Vi phạm → hậu quả:** Bug tiền bạc — khách được hoàn cọc khi không đáng, hoặc Manager phải xử
lý thủ công RefundRecord giả.

---

### HR-15 — Idempotency cho IPN VNPay (và mọi webhook bên ngoài)

**Rule:** Mọi IPN nhận được PHẢI lưu `vnp_TxnRef` + `vnp_TransactionNo` vào bảng
`payment_transaction` với UNIQUE constraint trên `vnp_TxnRef`. Nhận IPN trùng (cùng TxnRef đã
xử lý thành công) → trả `RspCode=02` "Order already confirmed", KHÔNG thay đổi DB lần 2, KHÔNG
tạo audit log mới.

**Lý do:** VNPay retry IPN nhiều lần nếu server không trả `RspCode=00` kịp thời. Không có
idempotency → đơn bị xác nhận 2 lần, RefundRecord tạo trùng.

**Vi phạm → hậu quả:** Bug tiền bạc nghiêm trọng — Customer bị tính cọc 2 lần trong DB dù chỉ
trả 1 lần thực tế; hoặc đơn chuyển CONFIRMED 2 lần gây lỗi state machine.

---

### HR-16 — Rate limit + account lockout cho login

**Rule:** Endpoint `/api/auth/login` PHẢI áp dụng CẢ HAI cơ chế:
- **(a) Rate limit theo IP:** 5 attempt/IP/15 phút. Vượt → HTTP 429 Too Many Requests.
- **(b) Account lockout:** sai password 5 lần liên tiếp cho cùng email → lock account 15 phút
  (cột `locked_until TIMESTAMPTZ`). Trong thời gian lock, dù đăng nhập đúng password vẫn trả
  HTTP 423 Locked.

Mọi POST endpoint khác áp dụng rate limit chung: 60 req/IP/phút. Vượt → HTTP 429.

**Lý do:** Chống brute force password vào tài khoản Admin/Manager; chống spam tạo đơn rác làm
đầy PENDING_PAYMENT.

**Vi phạm → hậu quả:** Tài khoản Admin bị crack bằng wordlist attack; DB ngập đơn PENDING_PAYMENT
không thể dọn dẹp; scheduled job auto-cancel quá tải.

---

### HR-17 — Public vs Authenticated endpoints tuong minh

**Rule:**
- Moi endpoint REST API PHAI duoc danh dau ro la **PUBLIC** (Guest goi duoc, khong can JWT) hoac
  **AUTHENTICATED** (yeu cau valid JWT).
- Endpoint PUBLIC PHAI dat duoi prefix `/api/public/*`. Vi du: `/api/public/quote-estimate`,
  `/api/public/pricing-config`, `/api/public/landing-info`.
- Spring Security filter chain config:
  - `/api/public/**` → `permitAll()` (bypass JWT)
  - `/api/auth/**` → `permitAll()` (login/register/verify khong can JWT san)
  - Moi path khac → `authenticated()` (default deny)
- KHONG endpoint nao khac duoc miss authentication (default deny rule).
- Endpoint PUBLIC KHONG duoc tra ve du lieu nhay cam: khong tra ve thong tin ca nhan Driver, danh
  sach don nguoi khac, internal config, PII, cookie.
- PUBLIC endpoint CHI tra ve du lieu marketing (banner, gia tham khao bang quan, thong tin chung).

**Lý do:** Mô hình v2.0 có Guest mode (6 trang public). Phải tách rõ public vs authenticated để
chống (a) lộ dữ liệu cho Guest, (b) bỏ sót auth check khi thêm endpoint mới.

**Vi phạm → hậu quả:**
- Endpoint authenticated bị mark public → lộ dữ liệu khách hàng/Driver.
- Endpoint public bị mark authenticated → Guest không xem website được, mất khách hàng tiềm năng.
- Endpoint thiếu phân loại → developer đoán → không consistent.

---

### HR-18 — Wallet balance KHONG bao gio am, audit trail bat buoc

**Rule:**
- Cot `wallet.balance` va `wallet.deposit_balance` PHAI co constraint o DB level:
  `CHECK (balance >= 0)` va `CHECK (deposit_balance >= 0)`.
- Spring service layer PHAI validate truoc khi UPDATE wallet:
  - Truoc khi tru: kiem tra `new_balance = current_balance - amount`.
  - Neu `new_balance < 0` → throw `InsufficientFundsException`, rollback transaction, KHONG cap
    nhat wallet, KHONG insert wallet_transaction.
- Moi UPDATE wallet PHAI di kem 1 INSERT `wallet_transaction` (audit trail) trong CUNG 1
  transaction DB (BEGIN ... COMMIT).
- Khi tinh boi thuong DamageReport > wallet balance: tru toi da co the (= wallet.balance), set
  `driver.status = SUSPENDED`, KHONG cho phep balance am. Driver phai nap lai coc moi tiep tuc lam.

**Lý do:** Wallet là module money-critical. Cho phép balance âm = sai số liệu doanh thu công ty,
sai báo cáo commission, liên quan pháp lý (Driver kiện nếu số dư âm).

**Vi phạm → hậu quả:**
- Driver thấy số dư âm → kiện cáo → ảnh hưởng uy tín dự án.
- Công ty báo cáo sai doanh thu (commission tính trên wallet sai).
- Audit fail nếu không có wallet_transaction tương ứng — có thể bị fraud nội bộ.

---

### HR-19 — Brand identity locked (Move_home forest green + amber)

**Rule:** Tất cả UI phải dùng Move_home brand identity:

- Primary color: `#1B4D3E` (forest green) - trust + safety
- Accent color: `#F5A623` (amber) - warmth + CTAs
- Font: Be Vietnam Pro (Google Fonts `subset=vietnamese`)
- Shape signature: pill `999px` cho buttons
- Card radius: `16px` (`rounded-xl`)

Constants được define trong `frontend/css/styles.css`. Mọi page mới phải dùng CSS variables đã
có, KHÔNG inline color khác brand.

**Lý do:** Brand consistency cho hệ thống tin cậy (chuyển nhà liên quan an toàn đồ đạc). Stripi
purple (US fintech) không phù hợp với Vietnamese moving service (feedback thầy Sprint 1 review,
brand đã migrate từ Stripi sang Move_home).

**Vi phạm → hậu quả:** UX inconsistent giữa các page, user mất tin tưởng. Brand identity bị phá
vỡ, signal tính amateur trong final defense.

---

### HR-20 — Vietnamese diacritics mandatory trong UI

**Rule:** TẤT CẢ text user-facing PHẢI có dấu tiếng Việt đầy đủ. KHÔNG được dùng
"tieng Viet khong dau" trong:

- Page title (vd: "Đặt đơn" NOT "Dat don")
- Button labels (vd: "Đăng ký" NOT "Dang ky")
- Form labels + placeholders
- Status badges (vd: "Đang chờ", "Hoàn thành", "Đã hủy")
- Error messages
- Empty state messages
- Email templates
- Notifications

Exception: ID, log, technical strings (status enum value như `PENDING`, `COMPLETED`) có thể dùng
English nhưng display luôn phải map sang tiếng Việt.

**Lý do:** Người dùng Việt Nam, không-có-dấu = unprofessional + khó đọc + thiếu tôn trọng người
dùng. Tiếng Việt là core identity của dự án.

**Vi phạm → hậu quả:** UX kém chất lượng, mất điểm trong final defense, khách hàng feel "không
thuộc về" sản phẩm.

---

### HR-21 — DB tables không dùng PostgreSQL reserved words

**Rule:** Mọi table mới phải tránh PostgreSQL reserved words:

- ❌ KHÔNG dùng: `user`, `order`, `group`, `table`, `select`, `where`, `from`
- ✅ DÙNG: `app_user`, `service_order`, `user_group`, ...

Khi `@Entity` Java map với reserved word, phải dùng `@Entity(name="...")` để JPQL không conflict
với `ORDER BY` / `SELECT`.

**Lý do:** Reserved words gây SQL syntax error khó debug, đặc biệt với JPA/Hibernate generate
query động. Tránh từ đầu rẻ hơn fix sau.

**Vi phạm → hậu quả:** Migration fail random, JPQL query crash production, mất hours debug
"tại sao SELECT từ order không chạy".

---

## LAYER 2 — ARCHITECTURAL CONSTRAINTS

> Cần approved exception bằng văn bản (comment trong PR + leader nhóm đồng ý) để bypass.
> AI PHẢI hỏi human trước khi vi phạm bất kỳ rule nào trong Layer này.

---

### AC-01 — Tech stack cố định

**Rule:** Backend: Spring Boot, `@RestController` trả JSON thuần. Frontend: HTML tĩnh + Vanilla JS
thuần — không dùng React, Angular, Vue, Svelte, Thymeleaf, hay bất kỳ UI framework nào. Database:
PostgreSQL. Không biến backend thành server-render HTML.

**Lý do:** Yêu cầu bắt buộc của thầy hướng dẫn; đã chốt trong CONTEXT §4.

---

### AC-02 — REST thuần, không GraphQL hay RPC

**Rule:** Tất cả API data fetching PHẢI là REST endpoint dưới `/api/...`. Không dùng GraphQL,
gRPC, SOAP. WebSocket STOMP chỉ dùng cho Chat realtime (Feature #20), không dùng cho data
fetching thông thường.

**Lý do:** Nhất quán với stack; giảm learning curve cho nhóm trong 6 tuần.

---

### AC-03 — JWT với refresh token rotation

**Rule:** Authentication dùng Spring Security + JWT. Access token: 15 phút. Refresh token: 7
ngày, lưu DB (bảng `refresh_token`). Mỗi lần dùng refresh token → cấp mới access + refresh;
token cũ bị invalidate ngay. Logout → xóa refresh token khỏi DB (server-side invalidation).

**Lý do:** Cân bằng security (access token ngắn, giảm rủi ro steal) và UX (refresh token dài,
không bắt đăng nhập lại liên tục); đã chốt trong CONTEXT §4 (A21).

---

### AC-04 — Truy vấn DB qua ORM/JdbcTemplate, không nối chuỗi SQL

**Rule:** Mọi truy vấn PHẢI dùng Spring Data JPA, JPQL, Criteria API, hoặc JdbcTemplate với named
parameters. KHÔNG bao giờ nối chuỗi SQL thủ công từ input người dùng, dù là trong test hay
helper method.

**Lý do:** Chống SQL injection — OWASP Top 10 #1; một query không dùng parameterized có thể xóa
toàn bộ dữ liệu.

---

### AC-05 — Chat dùng WebSocket STOMP + SockJS, có fallback polling

**Rule:** Chat realtime (Feature #20) dùng WebSocket STOMP + SockJS (Spring built-in), in-memory
broker. Tin nhắn vừa đẩy qua WebSocket vừa lưu DB ngay lập tức. Nếu tuần 5 không kịp → hạ xuống
polling 30 giây. Chat KHÔNG được làm chậm delivery CORE features (#1-#9).

**Lý do:** Chat là SHELL feature có rủi ro thời gian cao nhất; phải có phương án dự phòng để bảo
vệ timeline.

---

### AC-06 — Maps API phải có fallback bảng quận→quận

**Rule:** Mọi chỗ gọi Google Maps Distance Matrix API PHẢI implement fallback: khi API lỗi hoặc
hết quota → dùng bảng khoảng cách quận→quận nội thành Hà Nội lưu trong DB/config. Báo giá dùng
fallback PHẢI hiển thị nhãn "ước tính" rõ ràng cho khách.

**Lý do:** API có thể hết quota trong buổi demo; không có fallback → toàn bộ chức năng đặt đơn
và báo giá dừng hoàn toàn.

---

### AC-07 — Timezone: lưu UTC, hiển thị Asia/Ho_Chi_Minh

**Rule:** Mọi `TIMESTAMP` trong PostgreSQL PHẢI dùng kiểu `TIMESTAMP WITH TIME ZONE`, lưu UTC.
Backend nhận/trả ISO 8601 với offset đầy đủ (ví dụ `2026-05-29T08:30:00+07:00`).

Logic so sánh khung giờ cao điểm (CONTEXT §2: 7:00–9:00 và 17:00–19:00) PHẢI convert sang
`Asia/Ho_Chi_Minh` trước khi so sánh — không so sánh trực tiếp với UTC hour.

Scheduled job dùng UTC nội bộ, log cả 2 timezone để debug.

Application property bắt buộc: `spring.jackson.time-zone=Asia/Ho_Chi_Minh`.

**Lý do:** JVM trên cloud thường chạy UTC; Việt Nam UTC+7. Không quy định → tính giờ cao điểm
sai 7 tiếng, surcharge bị áp nhầm hoặc bỏ sót cho toàn bộ đơn trong ngày.

---

### AC-08 — Tiền tệ dùng BigDecimal scale=0, đơn vị VND nguyên đồng

**Rule:** Mọi field tiền (`deposit_amount`, `total_quote`, `compensation_amount`, `base_price`,
`surcharge_amount`, `refund_amount`, ...) PHẢI dùng `java.math.BigDecimal` với scale=0.

PostgreSQL column: `NUMERIC(15, 0) NOT NULL`.

KHÔNG dùng `Double`, `Float`, `double`, `float` ở bất kỳ đâu liên quan đến tiền — kể cả
trong DTO, service, hay test fixture.

Khi chia bồi thường 50/50: `company_share` làm tròn lên (`RoundingMode.CEILING`),
`driver_share` làm tròn xuống (`RoundingMode.FLOOR`). Tổng 2 phần = `compensation_amount` gốc.

**Lý do:** `float`/`double` không chính xác cho tiền tệ (IEEE 754 rounding error). `BigDecimal`
scale=0 đơn giản và đủ vì VND không có xu.

---

### AC-09 — Soft delete cho mọi entity tham chiếu lịch sử

**Rule:** Các entity sau PHẢI dùng soft delete với cột
`deleted_at TIMESTAMP WITH TIME ZONE NULL DEFAULT NULL`:
`Order`, `Trip`, `Vehicle`, `Driver` (user), `Porter` (user), `Customer` (user),
`DamageReport`, `RefundRecord`.

Query mặc định từ repository PHẢI filter `deleted_at IS NULL` — implement bằng JPA
`@SQLDelete(sql = "UPDATE ... SET deleted_at = NOW() WHERE id = ?")` + `@Where(clause =
"deleted_at IS NULL")`.

Truy vấn Admin/audit có quyền đọc cả soft-deleted record (endpoint riêng, bỏ filter).

KHÔNG `DELETE FROM` các entity trên — chỉ `UPDATE SET deleted_at = NOW()`.

**Entity được phép hard delete:** `refresh_token` (khi logout/expire), `chat_message` > 90 ngày
(cleanup job tùy chọn). Audit log KHÔNG được xóa dưới bất kỳ hình thức nào.

**Lý do:** `Order` tham chiếu `Vehicle`/`Driver`; hard-delete → lỗi FK hoặc mất lịch sử đơn
hàng — không thể tra cứu sau demo, mất điểm báo cáo.

---

### AC-10 — Upload ảnh DamageReport qua Cloudinary signed upload

**Rule:** Ảnh đính kèm DamageReport (Feature #8) PHẢI lưu trên Cloudinary. Không lưu ảnh trong
PostgreSQL (kể cả Base64 hay BLOB), không lưu trên local file system. Toàn bộ upload PHẢI là
**signed upload server-side** — backend ký request rồi gọi Cloudinary API; frontend không bao giờ
upload trực tiếp bằng unsigned request (tránh lộ API key trên client).

**Cấu hình:**
- SDK: Cloudinary Java SDK v1.x.
- Credentials qua 3 biến môi trường: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`,
  `CLOUDINARY_API_SECRET` — tuân thủ HR-01 (không commit git).

**Schema DB — bảng `damage_report_photo`:**

| Column | Type | Ghi chú |
|--------|------|---------|
| `id` | PK | |
| `damage_report_id` | FK | |
| `cloudinary_public_id` | TEXT NOT NULL | vd `movehome/damage/rpt123/ph456` |
| `cloudinary_secure_url` | TEXT NOT NULL | URL HTTPS từ Cloudinary |
| `uploaded_at` | TIMESTAMPTZ | |
| `uploaded_by_user_id` | FK | |

Không lưu Base64, không lưu BLOB.

**Folder structure trên Cloudinary:** `movehome/damage/{report_id}/{photo_id}` — để xóa hàng
loạt theo report khi cần.

**Giới hạn và validation (backend enforce):**

1. Tối đa **3 ảnh/DamageReport**. INSERT thứ 4 → HTTP 422.
2. Validate **MIME type qua magic number** (đọc byte đầu file) trước khi upload lên Cloudinary.
   Chấp nhận: `image/jpeg`, `image/png`, `image/webp`. Loại khác → HTTP 422, không gọi Cloudinary
   (tránh tốn quota free tier).
3. Kích thước backend validate: **≤ 1.5 MB/ảnh** sau khi client resize. Vượt → HTTP 422.

**Frontend compress trước khi gửi (Vanilla JS):**
- Max dimension: 1280px (cạnh dài).
- Quality: JPEG 0.8.
- Mục tiêu sau resize: ≤ 1 MB/ảnh (backend chặn cứng ở 1.5 MB).

**Truy cập ảnh — signed URL có expire:**
- `GET /api/damage-reports/{id}/photos/{photoId}` trả về **signed URL mới tạo** từ Cloudinary SDK
  với `expires_at = now() + 1 hour`. Frontend dùng URL này làm `<img src>`.
- URL hết hạn → load fail; client gọi lại API để lấy URL mới. Không cache URL phía client quá
  1 giờ.
- Endpoint PHẢI kiểm tra RBAC trước khi tạo signed URL: chỉ Customer tạo report + Manager +
  Admin có quyền xem. Tài khoản khác → HTTP 403 (HR-10).

**Cleanup bắt buộc:**
- Khi DamageReport bị xóa (xem TODO AC-09 cho soft-delete policy), Cloudinary asset PHẢI bị xóa
  bằng `cloudinary.uploader().destroy(public_id)`. Implement qua JPA `@PreRemove` hoặc
  Spring event listener. Không để ảnh rác trên Cloudinary free tier (25 GB/tháng).

**Lý do:** Cloudinary là kiến trúc đúng cho file storage — tách file ra khỏi DB, có CDN toàn
cầu cho UX nhanh, signed URL bảo vệ privacy. Free tier 25 GB đủ cho đồ án. Tuân thủ
production-ready pattern để chấm điểm cao.

**Vi phạm → hậu quả:**
- Upload unsigned từ frontend → lộ `CLOUDINARY_API_KEY` trên browser DevTools → attacker upload
  ảnh tùy ý vào bucket công ty.
- Không expire URL → bất kỳ ai có link đều xem được ảnh tranh chấp của khách hàng khác vĩnh
  viễn (GDPR/privacy risk).
- Không cleanup → hết free tier quota giữa sprint → toàn bộ upload DamageReport dừng.

---

### AC-11 — CORS whitelist tường minh

**Rule:** Spring Security CORS PHẢI khai báo whitelist tường minh các origin được phép. KHÔNG
dùng `allowedOrigins("*")` ở bất kỳ environment nào, kể cả local dev.

Cấu hình qua `application-{profile}.properties`:
- **Local dev:** `http://localhost:5500`, `http://127.0.0.1:5500`, `http://localhost:3000`
- **Production:** URL FE thực tế (TODO: xác định khi chốt deploy provider — xem TODO AC-11 trong
  Sync Impact Report)

`allowCredentials=true` chỉ với origin trong whitelist (CORS spec không cho phép `*` +
`allowCredentials=true` cùng lúc — Spring sẽ throw exception lúc startup).

Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
Allowed headers: `Authorization, Content-Type, X-Requested-With`.

**Lý do:** HTML tĩnh + Vanilla JS chạy khác origin với Spring backend (FE thường chạy
Live Server `localhost:5500`, BE chạy `localhost:8080`). CORS sai → toàn bộ API call từ FE
thất bại — demo sập ngay khi mở trình duyệt.

**Vi phạm → hậu quả:**
- Dùng `allowedOrigins("*")` kết hợp `allowCredentials=true` → Spring throw
  `IllegalArgumentException` lúc startup, server không khởi động được.
- Quên khai báo origin FE → browser block mọi API call, demo fail hoàn toàn dù backend chạy
  bình thường.

---

### AC-12 — Schema migration qua Flyway, `ddl-auto=validate`

**Rule:** Mọi thay đổi schema DB (CREATE TABLE, ALTER TABLE, ADD COLUMN, CREATE INDEX,
ADD CONSTRAINT) PHẢI được viết thành file Flyway migration tại
`src/main/resources/db/migration/` với tên `V{n}__{description}.sql`
(ví dụ: `V1__create_user_table.sql`, `V7__add_locked_until_to_user.sql`).

Cấu hình Spring bắt buộc cho mọi environment shared (staging, production):
```
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

KHÔNG dùng `ddl-auto=update` hay `ddl-auto=create` ở bất kỳ environment shared nào.
Local dev cũng dùng `validate` để đồng bộ với team.

5 người nhóm PHẢI sync schema qua migration files trong git. KHÔNG sync bằng cách "sửa local
DB rồi commit code tương ứng mà không có migration".

**Lý do:** 5 người viết code đồng thời → mỗi người tự sửa schema local → DB của mỗi người một
kiểu → integration ngay buổi demo sập. Hibernate `ddl-auto=update` không detect được rename
column, không reversible, không an toàn với data thực.

**Vi phạm → hậu quả:**
- Member A thêm cột `locked_until` vào bảng `user` nhưng không tạo migration → Member B pull
  code, chạy → `JPA validation failed: Schema-validation: missing column` → toàn bộ app không
  khởi động.
- Dùng `ddl-auto=update` trên staging → Hibernate rename column sai → data production bị mất
  hoặc corrupt.

---

### AC-13 — Money flow audit trail (wallet_transaction structure)

**Rule:** Moi giao dich lien quan tien (Driver wallet, deposit, withdrawal, damage compensation)
PHAI duoc ghi vao bang `wallet_transaction` voi cau truc bat buoc:

| Column | Type | Ghi chu |
|--------|------|---------|
| `id` | PK | |
| `wallet_id` | FK to wallet | |
| `type` | ENUM | `EARNING`, `DEPOSIT_PAID`, `DAMAGE_DEDUCT`, `WITHDRAWAL`, `DEPOSIT_REFUND`, `ADJUSTMENT` |
| `amount` | BigDecimal scale=0 (theo AC-08) | Duong neu tien vao, am neu tien ra |
| `balance_after` | BigDecimal | Snapshot wallet balance sau giao dich |
| `ref_order_id` | FK, nullable | Neu lien quan don |
| `ref_damage_id` | FK, nullable | Neu lien quan DamageReport |
| `ref_withdrawal_id` | FK, nullable | Neu lien quan Withdrawal |
| `note` | TEXT, optional | |
| `created_at` | TIMESTAMPTZ (theo AC-07) | |

- INSERT vao `wallet_transaction` PHAI di kem voi UPDATE wallet trong cung transaction (theo HR-18).
- KHONG DELETE hay UPDATE rows trong `wallet_transaction` (audit log bat bien). Neu can revert
  giao dich → them giao dich `ADJUSTMENT` moi.
- Cot `balance_after` cho phep kiem tra integrity: tong (amount lich su) phai = balance hien tai
  (sanity check).

**Lý do:** Audit trail tiền là yêu cầu cơ bản của hệ thống tài chính. Cho phép khách hàng/Driver
tra cứu lịch sử, Admin debug sai số, Manager báo cáo chính xác, tuân thủ quy định pháp lý về
tính minh bạch tài chính.

**Vi phạm → hậu quả:** Không thấy được nguồn gốc tiền — Driver khiếu nại "tôi có X tiền sao giờ
chỉ còn Y" không trả lời được.

---

### AC-14 — Status fields VARCHAR + CHECK constraint, NOT ENUM type

**Rule:** Mọi column lưu enum status PHẢI dùng:

- PostgreSQL: `VARCHAR(20) NOT NULL DEFAULT '...' CHECK (status IN (...))`
- Java: `String` (NOT enum `@Enumerated`)

Ví dụ ĐÚNG:
```sql
status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
```

Ví dụ SAI:
```sql
status order_status_enum NOT NULL  -- KHÔNG dùng CREATE TYPE enum
```

**Lý do:** PostgreSQL ENUM type khó migrate (cần ALTER TYPE phức tạp), khó add value mới, không
tương thích với Java String mapping đơn giản. VARCHAR + CHECK linh hoạt hơn, dễ migrate.

---

### AC-15 — Pagination pattern

**Rule:** List endpoints phải có pagination theo 2 patterns:

Server-side pagination (cho list lớn >50 records, vd: orders):

- Spring Data `Pageable` + `Page<T>`
- Default page size 10, max 100
- Response include: `content`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last`

Client-side pagination (cho list nhỏ <50 records, vd: drivers, customers):

- Backend trả `List<T>`
- Frontend dùng `clientSidePaginate(items, page, size)` từ `admin-common.js`

Frontend pagination UI mandatory:

- Page number buttons với ellipsis logic
- Previous/Next với disabled states
- Page size selector (10/20/50/100)
- Info text "Hiển thị X-Y trong Z [entityLabel]"

**Lý do:** UX scalable, không crash khi data lớn. Standard pattern cho admin tables.

---

### AC-16 — Empty/Loading/Error states mandatory

**Rule:** Mỗi list page hoặc data-driven page PHẢI implement 3 states:

Empty state: data = 0 records

- Empty illustration hoặc icon
- Vietnamese message: "Không có [entity] để hiển thị"
- Optional CTA action

Loading state: trong khi fetch

- Skeleton hoặc text "Đang tải..."
- Non-blocking UI

Error state: API fail

- Error message Vietnamese: "Không thể tải dữ liệu"
- Button "Tải lại" hoặc "Thử lại"

**Lý do:** Production-grade UX. User không bị stuck khi network slow hoặc data empty. Signal
product polish trong final defense.

---

## LAYER 3 — ENGINEERING STANDARDS

> Override được nếu có lý do ghi rõ trong PR description.
> AI submit kèm ghi chú lý do khi override bất kỳ rule nào.

---

### ES-01 — Naming conventions

**Rule:** Code viết bằng tiếng Anh. Class/enum: PascalCase. Method/variable: camelCase. DB
table/column: snake_case số ít (`order`, `damage_report`, `refund_record`). Package: lowercase
dot-separated (`com.movehome.service`).

**Lý do:** Nhất quán giúp Codex sinh code đúng convention ngay lần đầu, giảm diff noise khi
review.

---

### ES-02 — REST endpoint naming

**Rule:** Endpoint theo noun-based plural: `/api/orders`, `/api/trips`, `/api/damage-reports`.
Không dùng verb trong URL (`/api/getOrder`, `/api/createTrip`). HTTP method đúng nghĩa: GET đọc,
POST tạo, PUT thay toàn bộ, PATCH thay một phần, DELETE xóa.

**Lý do:** RESTful convention — dễ đoán, dễ test, dễ document, dễ sinh client code từ spec.

---

### ES-03 — Bean Validation + HTTP 422 cho input không hợp lệ

**Rule:** Mọi request body DTO PHẢI annotate `@Valid` + Jakarta Bean Validation annotations
(`@NotNull`, `@Size`, `@Min`, v.v.). Vi phạm validation → HTTP 422 Unprocessable Entity kèm
danh sách field vi phạm theo format ES-04.

**Lý do:** Validate tại API boundary — không để dữ liệu rác vào service layer và gây lỗi khó
debug ở tầng sâu hơn.

---

### ES-04 — Error response format thống nhất

**Rule:** Mọi error response PHẢI theo format JSON:
`{ "error_code": "STRING", "message": "...", "details": [...] }`.
Không trả plain string, HTML error page, hay stack trace ra ngoài cho REST endpoint.

**Lý do:** Frontend Vanilla JS cần parse error nhất quán; Codex sinh code FE dễ hơn khi format
error cố định từ đầu.

---

### ES-05 — Test coverage tối thiểu cho CORE features

**Rule:** CORE features (#1-#9) PHẢI đạt tối thiểu 70% line coverage và bắt buộc có integration
test cho luồng chính (happy path + ít nhất 1 error path). SHELL features (#10-#20) phải có ít
nhất integration test cho happy path.

**Lý do:** CORE features xử lý tiền bạc và state machine — bug không được phát hiện ở test gây
sự cố vận hành và mất điểm demo.

---

### ES-06 — Conventional Commits

**Rule:** Mọi commit PHẢI theo format Conventional Commits: `feat:`, `fix:`, `docs:`,
`refactor:`, `test:`, `chore:`. Subject line tối đa 72 ký tự. Breaking changes ghi
`BREAKING CHANGE:` trong body commit.

**Lý do:** Git history dễ đọc khi review; hỗ trợ tạo changelog tự động nếu cần cho báo cáo
cuối kỳ.

---

### ES-07 — Vietnamese commit message OK

**Rule:** Commit message theo Conventional Commits format nhưng description có thể là Vietnamese:

- Type: English (`feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `design`)
- Scope: English/kebab-case (`auth`, `frontend`, `customer`, `driver`)
- Description: Vietnamese OK

Ví dụ:

- `feat(frontend): chuyển brand từ Stripi sang Move_home`
- `fix(driver/home): thêm nav menu cho consistency`
- `docs: cập nhật SCREEN_INVENTORY với 65 màn hình`

KHÔNG dùng dấu `"` trong description khi commit qua PowerShell (gây parse error). Body message
OK dùng tiếng Việt có dấu.

**Lý do:** Team Vietnamese, viết tiếng Việt dễ hiểu hơn. Type prefix English chuẩn Conventional
Commits cho changelog tool.

---

### ES-08 — Multi-AI workflow strategy

**Rule:** Project dùng cả Claude Code + Codex CLI. Phân chia rõ:

Claude Code (Anthropic):

- Phase lớn (Sprint deliverables, multi-file refactor)
- Spec writing (per SDD framework)
- Architecture decisions

Codex CLI (OpenAI GPT-5.5):

- Phase nhỏ (single file fixes, stub generation)
- Pattern matching (làm 13 màn theo template)
- Quick verification

Document AI usage trong commit message body:

- `Generated by: Claude Code v2.1.159`
- `Generated by: Codex CLI v0.137.0 (GPT-5.5)`

**Lý do:** Tránh phụ thuộc 1 AI provider, tận dụng strengths của từng tool, quota management hợp
lý.

---

## AI Self-Check Protocol

> Trước khi submit bất kỳ spec hoặc code nào, AI (Claude/Codex) PHẢI chạy checklist này
> và in báo cáo đầy đủ.
>
> - **Layer 1 FAIL** → AI tự fix, KHÔNG submit cho đến khi tất cả PASS.
> - **Layer 2 EXCEPTION** → Document lý do, hỏi human trước khi proceed.
> - **Layer 3 OVERRIDE** → Submit kèm ghi chú lý do trong PR description.

```
=== CONSTITUTION CHECK REPORT ===
Feature  : [tên feature, ví dụ: "#7 VNPay IPN"]
Artifact : [spec | code | plan]
Date     : [YYYY-MM-DD]

--- LAYER 1: HARD RULES (FAIL → tự fix, không submit) -----------------
HR-01  Secrets không commit git                        [ PASS / FAIL ]
HR-02  Password dùng BCrypt, không plaintext           [ PASS / FAIL ]
HR-03  IPN là nguồn cập nhật thanh toán duy nhất       [ PASS / FAIL / N/A ]
HR-04  Verify HMAC-SHA512 trước khi xử lý IPN          [ PASS / FAIL / N/A ]
HR-05  Transition không hợp lệ → HTTP 409              [ PASS / FAIL ]
HR-06  Driver blocked confirm khi IN_DISPUTE            [ PASS / FAIL / N/A ]
HR-07  Chỉ Manager/Admin đóng IN_DISPUTE→COMPLETED      [ PASS / FAIL / N/A ]
HR-08  Driver concurrency lock                          [ PASS / FAIL / N/A ]
HR-09  IPN timeout 15ph → auto-CANCELLED Scheduled Job  [ PASS / FAIL / N/A ]
HR-10  Trái quyền → HTTP 403                           [ PASS / FAIL ]
HR-11  Email lỗi không rollback TX (@Async)             [ PASS / FAIL / N/A ]
HR-12  Driver tự đăng ký qua 4 bước onboarding         [ PASS / FAIL / N/A ]
HR-13  Audit log mọi state change                      [ PASS / FAIL ]
HR-14  RefundRecord chỉ khi COMPANY hủy                [ PASS / FAIL / N/A ]
HR-15  Idempotency IPN (UNIQUE vnp_TxnRef)             [ PASS / FAIL / N/A ]
HR-16  Rate limit login (5/IP/15ph) + lockout (5 sai) [ PASS / FAIL / N/A ]
HR-17  Public vs Authenticated endpoints tuong minh    [ PASS / FAIL / N/A ]
HR-18  Wallet balance KHONG am, audit trail bat buoc   [ PASS / FAIL / N/A ]
HR-19  Move_home brand identity locked                  [ PASS / FAIL / N/A ]
HR-20  UI có đầy đủ dấu tiếng Việt                     [ PASS / FAIL / N/A ]
HR-21  DB tables tránh PostgreSQL reserved words       [ PASS / FAIL / N/A ]

Layer 1 Result: [ ALL PASS → proceed | FAIL → fix first, do not submit ]

--- LAYER 2: ARCHITECTURAL CONSTRAINTS (EXCEPTION → hỏi human) ---------
AC-01  Stack: Spring Boot + HTML tĩnh + Vanilla JS + PG        [ PASS / EXCEPTION: ... ]
AC-02  REST thuần, không GraphQL/RPC                            [ PASS / EXCEPTION: ... ]
AC-03  JWT 15ph access + 7d refresh + rotation + DB store       [ PASS / EXCEPTION: ... / N/A ]
AC-04  Không nối chuỗi SQL thủ công                            [ PASS / EXCEPTION: ... ]
AC-05  Chat: STOMP+SockJS + lưu DB + fallback polling           [ PASS / EXCEPTION: ... / N/A ]
AC-06  Maps API có fallback bảng quận, label "ước tính"         [ PASS / EXCEPTION: ... / N/A ]
AC-07  Timezone UTC store + Asia/Ho_Chi_Minh display + compare  [ PASS / EXCEPTION: ... / N/A ]
AC-08  Tiền tệ BigDecimal scale=0, NUMERIC(15,0), không Float   [ PASS / EXCEPTION: ... / N/A ]
AC-09  Soft delete (deleted_at) cho entity lịch sử              [ PASS / EXCEPTION: ... / N/A ]
AC-10  Cloudinary signed upload; expire URL 1h; RBAC; cleanup   [ PASS / EXCEPTION: ... / N/A ]
AC-11  CORS whitelist tường minh, không allowedOrigins("*")     [ PASS / EXCEPTION: ... ]
AC-12  Flyway migration; ddl-auto=validate mọi environment       [ PASS / EXCEPTION: ... ]
AC-13  Money flow audit trail (wallet_transaction)               [ PASS / EXCEPTION / N/A ]
AC-14  Status dùng VARCHAR + CHECK, không PostgreSQL ENUM         [ PASS / EXCEPTION: ... / N/A ]
AC-15  Pagination theo server-side/client-side pattern            [ PASS / EXCEPTION: ... / N/A ]
AC-16  Empty/Loading/Error states cho data-driven page            [ PASS / EXCEPTION: ... / N/A ]

Layer 2 Result: [ ALL PASS → proceed | EXCEPTION noted → ask human ]

--- LAYER 3: ENGINEERING STANDARDS (OVERRIDE → ghi chú lý do) ----------
ES-01  Naming: PascalCase/camelCase/snake_case/tiếng Anh  [ PASS / OVERRIDE: ... ]
ES-02  REST endpoint: noun plural, HTTP method đúng        [ PASS / OVERRIDE: ... ]
ES-03  Bean Validation @Valid + HTTP 422 danh sách field   [ PASS / OVERRIDE: ... ]
ES-04  Error format { error_code, message, details }       [ PASS / OVERRIDE: ... ]
ES-05  Test ≥70% line cov cho CORE + integration test      [ PASS / OVERRIDE: ... ]
ES-06  Conventional Commits (feat/fix/docs/refactor/test)  [ PASS / OVERRIDE: ... ]
ES-07  Vietnamese commit với English type prefix          [ PASS / OVERRIDE: ... ]
ES-08  Multi-AI workflow strategy                         [ PASS / OVERRIDE: ... ]

Layer 3 Result: [ ALL PASS / OVERRIDE(s) noted in PR description ]

=== SUMMARY ===
Layer 1 : [__/21 PASS]
Layer 2 : [__/16 PASS, __ exception(s) documented]
Layer 3 : [__/8  PASS, __ override(s) noted]
Status  : [ CLEARED TO SUBMIT | BLOCKED — fix Layer 1 first ]
================================
```

---

## Project-Specific Decisions

> Các quyết định chiến lược đã chốt trong Pha 0 (CONTEXT.md v1.5 — Tổng kết).
> Không thay đổi mà không có team discussion và cập nhật CONTEXT.

| ID | Quyết định | Hệ quả kỹ thuật |
|----|-----------|----------------|
| D1 | **Một công ty duy nhất, không marketplace.** Hệ thống phục vụ 1 công ty sở hữu toàn bộ xe và nhân viên. | DB không cần multi-tenant. Không có bảng `vendor`/`merchant`. Mọi Vehicle/Staff thuộc công ty duy nhất. |
| D2 | **Cọc 30% VNPay + 70% COD. Không ví nội bộ. Hoàn tiền thủ công qua RefundRecord.** | Không có bảng `wallet`. Không tích hợp VNPay Refund API. RefundRecord ghi nhận quyết định hoàn tiền; Manager chuyển khoản ngoài hệ thống. |
| D3 | **Conflict check theo STATUS (FREE/BUSY), không theo khung giờ.** Driver/Porter có `availability_status`; BUSY khi Trip ASSIGNED, FREE khi Trip COMPLETED/CANCELLED. | Không cần bảng lịch khung giờ. Một Driver chỉ nhận 1 chuyến tại một thời điểm — đơn giản, đủ cho demo. |
| D4 | **GPS realtime → thay bằng cập nhật trạng thái thủ công.** Driver bấm "Bắt đầu" và "Hoàn thành" trên app. | Không tích hợp GPS SDK. COD tin tưởng Driver bấm confirm sau khi thu tiền thật (Assumption A10). |
| D5 | **Maps API chỉ dùng Distance Matrix; autocomplete và bản đồ tương tác là out of scope.** | Form nhập địa chỉ là text input thủ công. Không embed Google Maps interactive. Không có Places Autocomplete. |
| D6 | **Tiền tệ: BigDecimal scale=0, VND nguyên đồng, không xu.** Bồi thường chia 50/50: company ceil, driver floor. | Không dùng `Double`/`Float`. Column `NUMERIC(15,0)`. Tuân thủ AC-08. |
| D7 | **Ảnh upload: Cloudinary signed upload, bảng riêng `damage_report_photo`, signed URL expire 1h, FE compress trước upload.** | Không lưu Base64/BLOB trong DB. Cascade delete sang Cloudinary khi xóa report. Tuân thủ AC-10. |
| D8 | **Migration qua Flyway, `ddl-auto=validate` mọi environment.** 5 người nhóm sync schema qua migration files trong git. | Không dùng Hibernate auto-update. Schema versioned, có thể rollback. Tuân thủ AC-12. |
| D9 | **MAJOR PIVOT v2.0 (Marketplace):** Du an chuyen tu cong ty noi bo (5 vai tro, Driver nhan vien, Porter rieng) sang marketplace co dieu phoi (4 vai tro, Driver tu dang ky, Driver kiem Porter, commission 30%, Wallet noi bo, escrow 2h). | Lay tu CONTEXT.md v2.0. Quyet dinh duoc thay duyet 2026-05-29. Khong revert nua. |
| D10 | Brand migration Stripi → Move_home (forest green + amber). Sau feedback thầy Sprint 1, đổi brand từ Stripi (purple `#533afd`) sang Move_home (forest green `#1B4D3E` + amber `#F5A623`). Font Be Vietnam Pro native Vietnamese diacritics. | DESIGN.md migrated từ Stripi sang Uber-inspired format. Tag `v0.9-brand-migration` (2026-06-03). Tất cả page Sprint 1+ phải dùng forest green brand. |
| D11 | Scope 65 màn hình chia 6 sprints. Theo SCREEN_INVENTORY.md: Sprint 1 done 12, Sprint 2 +13 (booking), Sprint 3 +13 (driver), Sprint 4 +7 (payment), Sprint 5 +9 (manager + admin details), Sprint 6 +11 (public + error). Yêu cầu thầy: 50-70 màn hình. | Effort estimate ~282 giờ frontend. Chia đều cho 5 members ~13 màn/người mỗi sprint scope. Tag `v1.0-screen-flow-demo` (2026-06-04) với 66 màn stub. |
| D12 | SDD framework với SpecKit. Project dùng `.specify/` + `specs/` pattern. Constitution = source of truth pháp lý. Mỗi feature có `spec.md` riêng với FR/NFR/Acceptance Criteria. | Folder `specs/001-auth-rbac`, `specs/028-admin-dashboard` đã có. Sprint 2-6 sẽ thêm `specs/002-006` cho remaining features. |
| D13 | Multi-AI workflow strategy: Claude Code + Codex CLI song song. Phân chia theo size: Claude Code cho phase lớn (sprint deliverables, spec writing), Codex CLI cho phase nhỏ (file fixes, stub generation). | Plus account ChatGPT cho Codex quota cao. Document AI tool trong commit message body. ES-08 đã codify rule này. |

---

## Governance

Constitution này là tài liệu pháp lý của dự án, có quyền ưu tiên cao hơn mọi convention
mặc định của framework hay thói quen cá nhân.

**Thứ tự ưu tiên khi có conflict:**
`CONTEXT.md v2.0` → `Constitution v1.3.0` → Feature Specs → Code Implementation

**Amendment procedure:**
1. Propose thay đổi trong PR description với label `constitution-amendment`.
2. TODO(DECISION_MAKER): Leader/PM nhóm phải approve trước khi merge.
3. Tăng version theo semantic versioning:
   - MAJOR: xóa rule hoặc redefine rule cũ theo hướng khác.
   - MINOR: thêm rule/section mới hoặc mở rộng đáng kể một rule.
   - PATCH: clarification, wording, typo fix.
4. Cập nhật `Last Amended` = ngày merge.

**Compliance review:**
- Mỗi spec hoặc PR PHẢI chạy AI Self-Check Protocol và đính kèm report.
- CORE feature spec phải pass 100% Layer 1 trước khi được review bởi team.
- Layer 2 exceptions phải được ghi nhận rõ ràng trong PR và leader nhóm đồng ý.
- Layer 3 overrides phải có lý do kỹ thuật cụ thể trong PR description.

---

**Version**: 1.3.0 | **Ratified**: 2026-05-29 | **Last Amended**: 2026-06-04 (post-sprint-1)
