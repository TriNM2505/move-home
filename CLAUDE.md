# CLAUDE.md — Kiến thức dự án Move_home

> **Project:** Move_home v2.0 — Marketplace dich vu chuyen nha noi thanh Ha Noi
> **Team:** 5 sinh vien FPT (1 leader + 4 dev junior)
> **Status:** Pha 1 Implementation (code generation)
>
> File nay = **KIEN THUC** du an (kien truc, stack, cay thu muc, tham chieu). Doc de HIEU du an.
>
> **Phan vai tai lieu:**
> - **Rule HANH VI agent** (vai tro, pham vi quyen, xu ly loi, quy tac code, workflow) → **`AGENTS.md`**
> - **Luat HE THONG** (HR/AC/ES: tien, auth, IPN, schema, state machine) → **`.specify/memory/constitution.md`**
> - **Boi canh nghiep vu** (source of truth #1) → **`docs/CONTEXT.md`**
> - **File nay (CLAUDE.md)** → kien thuc du an.

---

## ⚠️ QUAN TRỌNG — Nguồn đúng hiện tại

> Các điểm sau là nguồn đúng, **ghi đè** mọi tài liệu cũ nếu lệch:
>
> 1. **BRAND = Move_home forest green** (constitution HR-19): primary **forest green `#1B4D3E`** +
>    accent **amber `#F5A623`** + font **Be Vietnam Pro** + button pill `999px` / card `16px`. KHÔNG
>    dùng Stripi purple `#533afd`/Inter. (Workflow FE chi tiet: `AGENTS.md` §3.)
> 2. **Spec hiện có 001–026.** Khi làm tính năng nào, đọc spec tương ứng trong `specs/`
>    (vd Admin Dashboard = **`specs/015-admin-dashboard/spec.md`**).
> 3. **Các con số** (schema, status, màn hình) theo
>    code/DB + migration thật. Nếu còn thấy lệch → tin code/migration + spec tương ứng.
> 4. Directory structure §3 là bản rút gọn. **Cây thư mục đầy đủ:**
>
> ```
> backend/src/main/java/vn/movehome/backend/
>   ├── controller/  (Auth, Profile, Wallet, Notification, AuditLog, Admin* ×8, ManagerDriver*, PublicQuote…)
>   ├── service/     (Auth, WalletService, CustomerProfile, DriverProfile, Admin* , ManagerDriverRating…)
>   ├── chat/        (ChatController/Service, Conversation, ChatMessage, ChatImageService, ChatRealtimePublisher)
>   ├── dispute/     (DisputeController/Service, CustomerRefundService, DisputePhoto, PenaltyEnforcementScheduler)
>   ├── driver/      (DriverOrder*, finance/DriverWallet+DriverEarningService, location/DriverLocation*)
>   ├── order/       (OrderController, CustomerOrderAction/Query, ManagerCancellationRefund*, OrderCancellation*)
>   ├── payment/     (VnPayController/Service/Signer, WalletOrderPaymentService)
>   ├── email/notification/, entity/, repository/, dto/, security/ (JwtTokenProvider), config/ (Security, WebSocket, Cloudinary)
>   └── resources/db/migration/  (V1..V44 + V99 seed)
> frontend/
>   ├── pages/       (login, register, messages.html, customer/, driver/, manager/, admin/, public/, 403|404|500)
>   ├── js/          (api, auth, chat, chat-badge, notifications-bell, dashboard, admin-*, customer-*, driver-common)
>   └── css/, assets/
> specs/001-026/     (spec.md + plan.md + tasks.md + checklists/requirements.md — đủ bộ)
> docs/              (CONTEXT, PROJECT_KNOWLEDGE_FULL, SCREEN_INVENTORY, USE_CASES_*, design-internal-reference)
> .specify/memory/constitution.md
> ```

---

## 1. Project Overview

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
| Charts | Chart.js | 4.x CDN | Theo design-internal-reference.md §8 |
| Email | Spring Mail + Gmail SMTP | (built-in Spring Boot) | Constitution HR-11 |
| Image storage | Cloudinary (signed upload) | Java SDK 1.39+ | Constitution AC-10 |
| Payment | VNPay Sandbox | REST API | Constitution HR-04, HR-15 |
| Maps | OpenStreetMap + OSRM | public demo endpoint | CONTEXT v2.0 §4 |

---

## 3. Directory Structure

```
Move_home/
├── AGENTS.md                    ← Rule hanh vi agent (persona, pham vi, workflow)
├── CLAUDE.md                    ← This file — kien thuc du an
├── DESIGN.md                    ← Brand identity Move_home (forest green + amber)
├── .github/                     ← PR template + workflows (ci, constitution-check, consistency-gate)
├── .specify/memory/
│   ├── constitution.md          ← Luat he thong HR/AC/ES — NEVER edit casually
│   └── constraints/             ← Ban thi hanh theo tang: global/business/safety.md
├── docs/
│   ├── CONTEXT.md               ← Business model v2.0 (source of truth #1)
│   ├── CONTEXT_v1.5_archived.md ← DO NOT use as source — archived only
│   ├── PROJECT_KNOWLEDGE_FULL.md← Deep dive kien truc + schema
│   ├── SCREEN_INVENTORY.md      ← Catalog man hinh
│   ├── design-internal-reference.md  ← UI design system (CSS tokens, components)
│   ├── api/                     ← API contract: API-First policy + endpoint catalog (OpenAPI)
│   └── architecture/            ← ADR (Architecture Decision Record) — "tai sao thiet ke the nay"
├── specs/
│   ├── 001-auth-rbac/ ... 026-*/     ← spec.md + plan.md + tasks.md + checklists
│   └── 028-admin-dashboard/spec.md
├── backend/                     ← Spring Boot project (open in IntelliJ)
│   ├── src/main/java/vn/movehome/backend/
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── db/migration/        ← Flyway: V1__xxx.sql ... V44__* + V99__seed_demo_data.sql
│   └── pom.xml
├── frontend/                    ← HTML + Vanilla JS (open in VS Code)
│   ├── pages/                   ← login, register, customer/, driver/, manager/, admin/, public/
│   ├── css/                     ← tokens, layout, forms, data-display, nav, feedback
│   └── js/                      ← auth, api, dashboard, admin-*, customer-*, driver-common, chat
├── graphify-out/                ← Knowledge graph (query/path/explain)
├── .env                         ← Secrets — NEVER commit (gitignored)
└── README.md
```

> **Cay thu muc backend day du:** xem khoi code o §"QUAN TRỌNG — Nguồn đúng" phia tren.

---

## 4. Common Tasks Cheat Sheet

### Tao spec moi
```
1. Tao folder: specs/XXX-feature-name/
2. Tao file: specs/XXX-feature-name/spec.md
3. Structure: Header → Goals → Scope → User Stories → FRs → Data Model
              → Error Matrix → AC Mapping → NFR → Constitution Check
              → Open Questions → Frontend Note
4. Theo EARS notation, >= 30% WHERE clauses (xem AGENTS.md §3)
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

### Build Spring Boot (chi compile — KHONG boot len Neon, xem AGENTS.md §2)
```bash
cd backend
./mvnw clean compile            # kiem cu phap
./mvnw test-compile             # sau khi doi constructor/DTO/entity
# ./mvnw spring-boot:run        # CHI leader chay tay — agent KHONG chay (tu ap migration len DB chung)
# Swagger UI: http://localhost:8080/swagger-ui.html (CHI sau khi them springdoc — xem docs/api/README.md)
```

### Chay Flyway migration thu cong
```bash
cd backend
./mvnw flyway:info              # xem status
# ./mvnw flyway:migrate         # CHI leader chay tay
```

### Kiem tra ket noi Neon PostgreSQL
```
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

## 5. Key References

| Tai lieu | Duong dan / URL |
|---------|----------------|
| Rule hanh vi agent | `AGENTS.md` |
| Constraint ky thuat (stack, naming, packages) | `.specify/memory/constraints/global.md` |
| Constraint nghiep vu (auth, tien, PII, glossary) | `.specify/memory/constraints/business.md` |
| Guardrail an toan agent | `.specify/memory/constraints/safety.md` |
| Business model day du | `docs/CONTEXT.md` v2.0 |
| Project rules (HR/AC/ES) | `.specify/memory/constitution.md` v1.4.0 |
| Deep dive kien truc + schema | `docs/PROJECT_KNOWLEDGE_FULL.md` |
| Catalog man hinh | `docs/SCREEN_INVENTORY.md` |
| ADR (quyet dinh kien truc) | `docs/architecture/README.md` |
| API contract (OpenAPI, API-First) | `docs/api/README.md` |
| Auth spec | `specs/001-auth-rbac/spec.md` v2.0 |
| Admin Dashboard spec | `specs/015-admin-dashboard/spec.md` |
| UI Design system | `docs/design-internal-reference.md` |
| Brand identity | `DESIGN.md` (root) |
| VNPay Sandbox docs | https://sandbox.vnpayment.vn/apis/ |
| Cloudinary Java SDK | https://cloudinary.com/documentation/java_integration |
| Chart.js docs | https://www.chartjs.org/docs/latest/ |
| OSRM API | https://router.project-osrm.org/ |
| Neon Console | https://console.neon.tech |
| Spring Boot docs | https://docs.spring.io/spring-boot/docs/3.x/ |

---

## 6. Team Notes (Internal)

- **4 dev junior** can guide nhieu — code phai don gian, comment day du tieng Viet.
- **Demo MOCK MODE** (cho demo): VNPay + Cloudinary co nut "Gia lap thanh cong (demo)" de test
  flow ma khong can ket noi that. Production integration la phase 2.
- **Seed data:** `V99__seed_demo_data.sql` — chay 1 lan qua Flyway.
- **Credentials demo** (chi la seed demo, KHONG phai secret that; BCrypt hash truoc khi insert):
  - Admin: `admin@movehome.vn`
  - Manager: `manager@movehome.vn`
  - Driver: `driver1@example.com`
