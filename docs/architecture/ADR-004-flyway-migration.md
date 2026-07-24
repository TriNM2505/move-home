# ADR-004: Schema qua Flyway, ddl-auto=validate

- **Status:** Accepted
- **Nguồn:** constitution Decision **D8** · PROJECT_KNOWLEDGE §1.10 · **AC-12**

## Context
5 người viết code đồng thời trên **1 DB Neon dùng chung**. Nếu mỗi người tự sửa schema local → DB
mỗi người một kiểu → integration buổi demo sập. Cần cơ chế versioned, reproducible, an toàn với
data thật.

## Decision
- Mọi thay đổi schema (CREATE/ALTER/ADD COLUMN/INDEX/CONSTRAINT) viết thành `V{n}__{desc}.sql` tại
  `src/main/resources/db/migration/`.
- `spring.jpa.hibernate.ddl-auto=**validate**` mọi environment (local + shared). `spring.flyway.enabled=true`.
- **KHÔNG** `ddl-auto=update`/`create` ở bất kỳ đâu.
- Số migration do **leader cấp** — không tự đoán (tránh trùng số khi nhiều người cùng làm).

## Alternatives considered
| Approach | Verdict |
|----------|---------|
| **Flyway versioned** | ✅ Chọn — versioned, rollback, team-friendly |
| Hibernate `ddl-auto=update` | ❌ Không reproducible, không detect rename column, conflict team |
| Hibernate `create-drop` | ❌ Mất data mỗi restart |
| Liquibase (XML/YAML) | ❌ Verbose hơn SQL thuần |

## Consequences (Trade-off)
- ➕ Schema versioned, reproducible cho 5 người, `flyway_schema_history` track everything.
- ➕ `validate` bắt lỗi entity ≠ DB ngay lúc startup.
- ➖ Phải viết SQL migration tay (không auto từ entity).
- ➖ Vì DB shared: **agent KHÔNG được boot app lên Neon** (tự áp migration) — chỉ leader chạy tay (safety.md §1).
