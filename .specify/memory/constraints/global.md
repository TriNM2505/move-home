# constraints/global.md — Ràng buộc KỸ THUẬT toàn cục

**Version:** 1.0.0 | **Cập nhật:** 2026-07-24 | **Maintainer:** Tech lead (leader)
**Enforcement:** Auto (review thủ công + CI). *Chưa có linter tự động cho Java; `jacoco` đo coverage.*

> Định nghĩa "**reasonable của Move_home**" thay vì để agent dùng "reasonable" từ training data.
> Trả lời câu hỏi: **"Dùng công nghệ gì? Được/cấm thư viện nào?"**
> 📎 Canonical: constitution **AC-01** (stack), **AC-04** (query), **ES-01** (naming). Nếu lệch → constitution thắng, sync lại file này.

---

## 1. Technology Stack (bất biến trừ khi có RFC + leader duyệt)

| Layer | Công nghệ | Version |
|-------|-----------|---------|
| Backend | Spring Boot | **3.5.14** (parent) |
| Language | Java | **17 LTS** |
| Database | PostgreSQL (Neon Cloud) | 16 |
| Migration | Flyway (`flyway-core` + `flyway-database-postgresql`) | built-in Spring Boot |
| Build | Maven | 3.9+ |
| Auth | JWT `io.jsonwebtoken:jjwt` (HS256) | **0.12.6** |
| ORM | Spring Data JPA (Hibernate) | built-in |
| Frontend | HTML + Vanilla JS + Vanilla CSS (**KHÔNG framework**) | — |
| Charts | Chart.js qua **CDN** (không npm) | 4.x |
| Image | Cloudinary `cloudinary-http44` | 1.39.0 |
| Payment | VNPay Sandbox (REST) | — |
| Email | Spring Mail + Gmail SMTP | built-in |
| Maps | OpenStreetMap + OSRM (public demo) | — |
| Env | `me.paulschwarz:spring-dotenv` | 4.0.0 |

---

## 2. Naming Conventions

| Loại | Convention | Ví dụ |
|------|-----------|-------|
| Java class / enum | PascalCase | `DriverOnboardingService`, `OrderStatus` |
| Java method / variable | camelCase | `approveDriver()`, `totalQuote` |
| DB table | snake_case số ít | `service_order`, `wallet_transaction` (tránh reserved word — HR-21) |
| DB column | snake_case | `created_at`, `commission_rate_snapshot` |
| REST endpoint | kebab-case, noun plural | `/api/admin/dashboard/pending-drivers` |
| CSS class | kebab-case | `.kpi-box`, `.btn-primary`, `.status-pill--danger` |
| FE JS function | camelCase | `renderKpiRow()`, `loadDashboard()` |
| Package Java | lowercase dot | `vn.movehome.backend.service` |

- **Ngôn ngữ:** comment kỹ thuật + tên biến/method = tiếng Anh (ES-01); text hiển thị user = tiếng Việt CÓ DẤU (HR-20).

> 📎 Canonical: constitution **ES-01**, **ES-02** (REST endpoint), **HR-21** (reserved words), **HR-20**.

---

## 3. Approved Packages (danh sách trắng — từ `backend/pom.xml`)

**Spring Boot starters:** `data-jpa`, `mail`, `security`, `validation`, `web`, `websocket`.
**Runtime/tool:** `postgresql` (driver) · `spring-boot-devtools` (optional) · `org.projectlombok:lombok` (optional, annotation processor) · `me.paulschwarz:spring-dotenv` 4.0.0.
**Auth:** `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.6.
**Image:** `com.cloudinary:cloudinary-http44` 1.39.0.
**Migration:** `org.flywaydb:flyway-core` + `flyway-database-postgresql`.
**Test:** `spring-boot-starter-test`, `com.h2database:h2`, `spring-security-test`.
**Plugin build:** `spring-boot-maven-plugin`, `maven-compiler-plugin`, `org.jacoco:jacoco-maven-plugin` 0.8.13.
**Frontend:** Chart.js 4.x qua CDN — **không** dùng npm/bundler.

---

## 4. Banned Packages (danh sách đen — kèm LÝ DO)

| Cấm | Lý do |
|-----|-------|
| ORM/DB khác (MyBatis, jOOQ, raw JDBC nối chuỗi SQL) | Chỉ Spring Data JPA + JPQL/parameterized (**AC-04** — chống SQL injection) |
| React / Vue / Angular / Svelte / Thymeleaf | Vanilla JS only, BE trả JSON thuần (**AC-01**) |
| `jjwt` 0.9.x cũ · `com.auth0:java-jwt` | Chỉ dùng `jjwt` 0.12.x (**AC-03**) |
| `log4j-core` thêm trực tiếp | Dùng logback mặc định Spring — tránh CVE Log4Shell |
| PostgreSQL `ENUM type` · `hibernate-types` cho enum | Status dùng `VARCHAR + CHECK` (**AC-14**) |
| `double` / `float` cho tiền *(kiểu dữ liệu, không phải lib)* | Chỉ `BigDecimal` scale=0 (**AC-08**) |
| UI framework/bundler cho FE (npm React/webpack/vite) | Giữ Vanilla JS, không build step (**AC-01**) |

> Danh sách này **mở** — leader bổ sung khi cần.

---

## 5. Quy trình thêm package mới

1. Agent **KHÔNG** tự thêm dependency vào `pom.xml` — xem `constraints/safety.md`.
2. Đề xuất qua leader kèm justification (vì sao cần, có sẵn trong Spring không).
3. Leader duyệt → thêm vào `pom.xml` + cập nhật §3 file này.

## 6. API-First (kiến trúc — ARCH)

- Endpoint **MỚI**: mô tả OpenAPI (annotation) **trước** khi implement → review → code.
- OpenAPI auto-generate qua springdoc (không viết tay); mọi task API cập nhật spec **cùng PR**.
- Chi tiết chính sách + endpoint catalog: **`docs/api/README.md`**.
- 📎 Canonical: constitution **AC-02** (REST thuần), **HR-17** (public vs authenticated).
