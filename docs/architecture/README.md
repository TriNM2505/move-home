# docs/architecture/ — Architecture Decision Records (ADR)

> Nơi trả lời câu hỏi **"Tại sao hệ thống được thiết kế thế này?"** — điều mà code một mình không
> bao giờ trả lời được. Mỗi ADR ghi: **Context → Decision → Alternatives → Consequences (Trade-off)**.
>
> **Naming:** `ADR-{NNN}-{ten-kebab}.md`, số **zero-padded** (001, 002...). Status: `Accepted` /
> `Superseded by ADR-XXX` / `Proposed`.
> **Nguồn gốc:** các quyết định này đã tồn tại ở `constitution.md` (bảng Decisions **D1–D13**) +
> `docs/PROJECT_KNOWLEDGE_FULL.md` **Part 1** (§1.1–1.10). ADR ở đây **đóng gói chuẩn form + trỏ** về đó (DRY).

---

## Index — ADR đã ratified

| ADR | Quyết định | Nguồn gốc | Rule liên quan |
|-----|-----------|-----------|----------------|
| [ADR-001](ADR-001-marketplace-pivot.md) | Marketplace pivot v2.0 (4 vai trò, driver tự đăng ký, commission 30%, escrow) | D9 | — |
| [ADR-002](ADR-002-vanilla-js-frontend.md) | Frontend Vanilla JS (không framework) | §1.3 | AC-01 |
| [ADR-003](ADR-003-money-bigdecimal.md) | Tiền dùng BigDecimal scale=0, VND nguyên đồng | D6, §1.6 | AC-08, HR-18 |
| [ADR-004](ADR-004-flyway-migration.md) | Schema qua Flyway, `ddl-auto=validate` | D8, §1.10 | AC-12 |
| [ADR-005](ADR-005-jwt-stateless-auth.md) | Auth JWT HS256 stateless + refresh rotation | §1.4 | AC-03, HR-16 |
| [ADR-006](ADR-006-status-varchar-check.md) | Status field VARCHAR + CHECK (không PostgreSQL ENUM) | §1.7 | AC-14 |

## Quyết định khác (rationale đầy đủ trong PROJECT_KNOWLEDGE Part 1 — chưa tách file riêng)

| Decision | Nguồn |
|----------|-------|
| Spring Boot 3.5.14 + Java 17 LTS | §1.1 |
| Neon Cloud PostgreSQL (shared, Singapore) | §1.2 |
| BCrypt cost 12 | §1.5 |
| Snapshot commission rate per order | §1.6, D-01 |
| Soft delete (`deleted_at`) cho entity nghiệp vụ | §1.8, AC-09 |
| UUID v4 PK (`gen_random_uuid()`) | §1.9 |

---

## Quy trình thêm ADR mới
1. Copy form 1 ADR có sẵn → `ADR-{NNN+1}-{ten}.md`.
2. Điền đủ 4 phần (Context/Decision/Alternatives/Consequences).
3. Thêm dòng vào Index trên.
4. Nếu quyết định **sửa constitution** → theo quy trình amendment (Sync Impact Report) — đây là tầng RFC.
