# CLAUDE.md — AI Assistant Instructions

> **Project:** Move_home v2.0 — Marketplace dich vu chuyen nha noi thanh Ha Noi
> **Team:** 5 sinh vien FPT (1 leader + 4 dev junior)
> **Sprint demo:** Thu Ba 2026-06-02
> **Status:** Pha 1 Implementation (code generation)
>
> File nay duoc TAT CA AI assistants doc khi mo project Move_home.
> Bao gom: Claude Code (Anthropic), Codex (OpenAI), Cursor, GitHub Copilot.
> Doc file nay TRUOC khi viet bat ky code/spec moi.

---

## 1. Project Overview (read first)

Move_home la **marketplace co dieu phoi** dich vu chuyen nha noi thanh Ha Noi (12 quan). Mo hinh:
Driver tu dang ky tu do (co vehicle rieng), Manager phan cong thu cong sau khi nhan don tu khach,
cong ty thu **commission 30%** tren tong bao gia. Thanh toan **100% qua VNPay** (khong COD): khach
coc 30% khi dat, tra not 70% tai cho truoc khi Driver bam Hoan thanh. Sau COMPLETED co **escrow
2 gio** — khach khieu nai DamageReport trong 2h, het 2h thi scheduled job tu chuyen 70% vao Vi
Driver.

**4 vai tro:** Customer / Driver / Manager / Admin. Guest (khong dang nhap) xem duoc 6 trang public.
Bo vai tro Porter — Driver kiem nhiem boc xep.

**Chi tiet day du:** `docs/CONTEXT.md` v2.0.

---

## 2. Tech Stack

| Layer | Technology | Version | Ghi chu |
|-------|-----------|---------|---------|
| Backend | Spring Boot | 3.x | Standard FPT, doc nhieu |
| Language (BE) | Java | 17 LTS | Tuong thich Spring Boot 3 |
| Database | PostgreSQL (Neon Cloud) | 16 | Region Singapore, free 0.5 GB |
| Migration | Flyway | (built-in Spring Boot) | Theo Constitution AC-12 |
| Build | Maven | 3.9+ | Default IntelliJ |
| Auth | JWT (jjwt library) | 0.12.x | Theo Spec #001 AC-03 |
| Frontend | HTML + Vanilla JS + Vanilla CSS | (no framework) | Constitution AC-01 |
| Charts | Chart.js | 4.x CDN | Theo design.md §8 |
| Email | Spring Mail + Gmail SMTP | (built-in Spring Boot) | Constitution HR-11 |
| Image storage | Cloudinary (signed upload) | Java SDK 1.39+ | Constitution AC-10 |
| Payment | VNPay Sandbox | REST API | Constitution HR-04, HR-15 |
| Maps | OpenStreetMap + OSRM | public demo endpoint | CONTEXT v2.0 §4 |

---

## 3. Directory Structure

```
Move_home/
├── .specify/memory/
│   └── constitution.md          ← Project rules v1.2.0 — NEVER edit casually
├── docs/
│   ├── CONTEXT.md               ← Business model v2.0 (source of truth #1)
│   ├── CONTEXT_v1.5_archived.md ← DO NOT use as source — archived only
│   └── design.md                ← UI design system v1.0 (CSS tokens, components)
├── specs/
│   ├── 001-auth-rbac/
│   │   ├── spec.md              ← Auth + RBAC + Guest mode v2.0 (1103 lines)
│   │   └── spec_v1_archived.md  ← DO NOT use — archived only
│   └── 028-admin-dashboard/
│       └── spec.md              ← Admin Dashboard demo spec (792 lines)
├── backend/                     ← Spring Boot project (open in IntelliJ)
│   ├── src/main/java/com/movehome/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-dev.properties
│   │   └── db/migration/        ← Flyway: V1__xxx.sql ... V99__seed_demo_data.sql
│   └── pom.xml
├── frontend/                    ← HTML + Vanilla JS (open in VS Code)
│   ├── index.html               ← Landing page (Guest)
│   ├── login.html
│   ├── register.html
│   ├── admin/
│   │   └── dashboard.html       ← Admin Dashboard (Spec #028)
│   ├── driver/
│   ├── css/
│   │   ├── tokens.css           ← CSS variables (design.md §2)
│   │   ├── layout.css           ← Grid, container (design.md §3)
│   │   ├── forms.css            ← Form components (design.md §4)
│   │   ├── data-display.css     ← Card, table, KPI (design.md §5)
│   │   ├── nav.css              ← Header, sidebar (design.md §6)
│   │   └── feedback.css         ← Toast, modal (design.md §7)
│   └── js/
│       ├── auth.js              ← Token storage + refresh logic
│       ├── dashboard.js         ← Admin dashboard fetch + render
│       └── charts-config.js     ← Chart.js default config (design.md §8)
├── .env                         ← Secrets — NEVER commit (gitignored)
├── .gitignore
├── CLAUDE.md                    ← This file
└── README.md
```

---

## 4. Files to Read First (Priority Order)

Truoc khi viet hoac sua bat ky code/spec moi, AI assistant PHAI doc theo thu tu sau:

| Priority | File | Khi nao can doc |
|----------|------|-----------------|
| 1 | `docs/CONTEXT.md` | Luon luon |
| 2 | `.specify/memory/constitution.md` | Luon luon |
| 3 | `docs/design.md` | Khi lam frontend |
| 4 | `specs/001-auth-rbac/spec.md` | Khi lam Auth, RBAC, JWT, Driver onboarding |
| 5 | `specs/028-admin-dashboard/spec.md` | Khi lam Admin Dashboard |

> ⛔ `spec_v1_archived.md` va `CONTEXT_v1.5_archived.md` — chi doc khi can tham chieu
> lich su. TUYET DOI KHONG dung lam source cho code moi.

---

## 5. Coding Conventions

### Ngon ngu trong code
- **Comment trong code:** tieng Viet — vi du `// Tinh commission 30% tren total_quote (HR-12)`
- **String hien thi cho user:** tieng Viet — vi du `"Ten dang nhap hoac mat khau khong dung"`
- **Variable / method name:** tieng Anh — vi du `calculateCommission()`, `walletBalance`
- **DB column / table:** tieng Anh snake_case — vi du `wallet_transaction.balance_after`

### Naming conventions
| Loai | Convention | Vi du |
|------|-----------|-------|
| Java class / enum | PascalCase | `DriverOnboardingService`, `OrderStatus` |
| Java method / variable | camelCase | `approveDriver()`, `totalQuote` |
| DB table | snake_case singular | `order`, `damage_report`, `wallet_transaction` |
| DB column | snake_case | `created_at`, `driver_id`, `commission_rate_snapshot` |
| REST endpoint | kebab-case | `/api/admin/dashboard/pending-drivers` |
| CSS class | kebab-case | `.kpi-box`, `.btn-primary`, `.status-pill--danger` |
| FE JS function | camelCase | `renderKpiRow()`, `loadDashboard()` |

### Money — CRITICAL (Constitution AC-08)
```java
// DUNG
BigDecimal commission = totalQuote.multiply(new BigDecimal("0.30"))
                                   .setScale(0, RoundingMode.FLOOR);
// SAI — TUYET DOI KHONG
double commission = totalQuote * 0.30;
float  commission = ...;
```
- DB: `NUMERIC(15,0)` — khong bao gio float/double
- JSON: serialize as integer (vi du `12500000`, khong phai `"12500000.00"`)
- Display FE: `new Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'}).format(n)`

### Datetime — Constitution AC-07
```sql
-- DB: luon dung TIMESTAMP WITH TIME ZONE
created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
```
```java
// Server logic: UTC
Instant now = Instant.now();
// Display: convert sang Asia/Ho_Chi_Minh chi khi tra ve FE
```
- API JSON: ISO 8601 UTC — vi du `"2026-05-30T07:00:00Z"`
- FE display: `toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })`
- Peak-hour check (7-9h, 17-19h): PHAI convert sang `Asia/Ho_Chi_Minh` truoc khi so sanh

### EARS notation (khi viet spec moi)
- `WHEN <event>, THE system SHALL <action>` — happy path
- `WHILE <state>, THE system SHALL <action>` — continuous
- `WHERE <error/condition>, THE system SHALL <action>` — error/unwanted path
- `IF <condition>, THEN <action>` — optional alternative

Minimum **30% WHERE clauses** trong tong so FR cua moi spec.

---

## 6. Hard Rules — NEVER VIOLATE

> Cac rule nay luon ap dung. Khong exception, du prompt yeu cau cach khac.

### Security
- ❌ **NEVER** commit `.env` to git — API keys luon qua bien moi truong (HR-01)
- ❌ **NEVER** hardcode credentials (VNPay, Cloudinary, Gmail, JWT secret) trong code
- ❌ **NEVER** log plaintext password — chi log sau khi hash BCrypt
- ❌ **NEVER** expose PII hoac internal config qua `/api/public/*` endpoint (HR-17)
- ❌ **NEVER** skip JWT verification cho `/api/admin/*`, `/api/driver/*`, `/api/manager/*` (HR-10)
- ❌ **NEVER** trust IPN callback cua VNPay ma khong verify HMAC-SHA512 truoc (HR-04)
- ❌ **NEVER** process IPN voi `vnp_TxnRef` da xu ly — idempotency bat buoc (HR-15)

### Money & Wallet
- ❌ **NEVER** de `wallet.balance < 0` — DB CHECK constraint + service validate (HR-18)
- ❌ **NEVER** UPDATE wallet ma khong co INSERT vao `wallet_transaction` trong cung 1 transaction (AC-13)
- ❌ **NEVER** dung `double` hoac `float` cho bat ky gia tri lien quan tien (AC-08)

### Architecture
- ❌ **NEVER** dung React / Vue / Angular / Svelte cho frontend — Vanilla JS only (AC-01)
- ❌ **NEVER** sua schema DB truc tiep ma khong tao Flyway migration file (AC-12)
- ❌ **NEVER** dung `spring.jpa.hibernate.ddl-auto=update` tren moi truong shared (AC-12)
- ❌ **NEVER** edit `.specify/memory/constitution.md` ma khong bump version va update Sync Impact Report
- ❌ **NEVER** su dung vai tro Porter — da bo trong v2.0 (chi con 4 role: Customer/Driver/Manager/Admin)

---

## 7. Workflow Patterns

### Khi viet / sua spec
1. Doc spec hien tai + Sync Impact Report header de biet version
2. Viet theo EARS notation (WHEN / WHILE / WHERE / IF)
3. Sau khi sua, update version number (MAJOR / MINOR / PATCH)
4. In tom tat cuoi cung: "Confirm X, Y, Z. Tong so dong: N."
5. User verify bang Ctrl+F — so luong phai khop

### Khi generate code (Codex / Claude Code)
1. Doc spec lien quan + tat ca HR/AC duoc tham chieu
2. Generate code khop spec CHINH XAC — KHONG them feature ngoai scope
3. Comment tham chieu FR-XXX, HR-XX, AC-XX
4. In danh sach file da tao / sua sau khi xong

### Khi user bao "verify that bai" (so luong khong khop)
- KHONG gia dinh user sai
- Kiem tra file tu DB (khong tu bo nho)
- Goi y user: `Ctrl+Shift+P → Revert File` truoc (file co the chua reload trong IDE)

### Khi prompt phuc tap / dai
- Chia thanh nhieu Luot (A/B/C hoac 1/2/3)
- In ro: "LUOT NAY viet X, Y. LUOT SAU se them A, B."
- User verify giua cac luot truoc khi tiep theo

---

## 8. Common Tasks Cheat Sheet

### Tao spec moi
```
1. Tao folder: specs/XXX-feature-name/
2. Tao file: specs/XXX-feature-name/spec.md
3. Structure: Header → Goals → Scope → User Stories → FRs → Data Model
              → Error Matrix → AC Mapping → NFR → Constitution Check
              → Open Questions → Frontend Note
4. Theo EARS notation, >= 30% WHERE clauses
5. Reference spec #001 cho cac pattern Auth/JWT
```

### Them Constitution rule moi
```
1. Edit .specify/memory/constitution.md
2. Them HR-XX / AC-XX vao dung Layer
3. Them vao Self-Check Protocol checklist
4. Cap nhat SUMMARY count (Layer 1: N HR, Layer 2: N AC)
5. Cap nhat Sync Impact Report header
6. Bump version: MINOR neu them rule moi, PATCH neu chi clarify
```

### Build va run Spring Boot
```bash
cd backend
./mvnw clean install -DskipTests
./mvnw spring-boot:run --spring.profiles.active=dev
# App chay tren http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Chay Flyway migration thu cong
```bash
cd backend
./mvnw flyway:migrate
# Xem status: ./mvnw flyway:info
```

### Kiem tra ket noi Neon PostgreSQL
```bash
# Trong IntelliJ: Database → + → Data Source → PostgreSQL
# URL: jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require
# Hoac test nhanh qua Neon Console: https://console.neon.tech
```

### Verify spec EARS ratio
```
1. Mo spec.md
2. Ctrl+F count "**FR-" → tong so FR
3. Ctrl+F count "^WHERE" → so UNWANTED pattern
4. Ti le UNWANTED / total FR phai >= 30%
```

---

## 9. Key References

| Tai lieu | Duong dan / URL |
|---------|----------------|
| Business model day du | `docs/CONTEXT.md` v2.0 |
| Project rules | `.specify/memory/constitution.md` v1.2.0 |
| Auth spec | `specs/001-auth-rbac/spec.md` v2.0 |
| Admin Dashboard spec | `specs/028-admin-dashboard/spec.md` v1.0 |
| UI Design system | `docs/design.md` v1.0 |
| VNPay Sandbox docs | https://sandbox.vnpayment.vn/apis/ |
| Cloudinary Java SDK | https://cloudinary.com/documentation/java_integration |
| Chart.js docs | https://www.chartjs.org/docs/latest/ |
| OSRM API | https://router.project-osrm.org/ |
| Neon Console | https://console.neon.tech |
| Spring Boot docs | https://docs.spring.io/spring-boot/docs/3.x/ |

---

## 10. Team Notes (Internal)

- **4 dev junior** can guide nhieu — code phai don gian, comment day du tieng Viet
- **Demo Thu Ba 2026-06-02** target: 4 man hinh chinh:
  - `/register` — Customer Register form
  - `/login` — Login (Customer + Driver + Staff)
  - `/driver/onboarding` — Driver Onboarding Step 1-2 (Step 3-4 mock)
  - `/admin/dashboard` — Admin Dashboard (Spec #028)
- **Demo MOCK MODE** (cho demo ngay): VNPay + Cloudinary co nut "Gia lap thanh cong (demo)" de
  test flow ma khong can ket noi that. Production integration la phase 2.
- **Seed data:** `V99__seed_demo_data.sql` — chay 1 lan qua Flyway, tao 150 orders + 60 users.
- **Credentials demo** (BCrypt hash truoc khi insert):
  - Admin: `admin@movehome.vn` / `Admin@123456`
  - Manager: `manager@movehome.vn` / `Manager@123456`
  - Driver: `driver1@example.com` / `Driver@123456`
