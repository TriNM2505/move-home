# ADR-006: Status field VARCHAR + CHECK (không PostgreSQL ENUM)

- **Status:** Accepted
- **Nguồn:** PROJECT_KNOWLEDGE §1.7 · constitution **AC-14**

## Context
Nhiều bảng có field status (order, driver, dispute, withdrawal...). Cần enum-like nhưng dễ migrate
vì status set thay đổi theo sprint (vd order status mở rộng từ 8 → 11 giá trị qua V21).

## Decision
- PostgreSQL: `VARCHAR(n) NOT NULL DEFAULT '...' CHECK (status IN (...))`.
- Java: `String` (hoặc `@Enumerated(EnumType.STRING)`) — **KHÔNG** `CREATE TYPE ... AS ENUM`.

## Alternatives considered
| Aspect | VARCHAR + CHECK | PostgreSQL ENUM |
|--------|-----------------|-----------------|
| Thêm value | `DROP CHECK, ADD CHECK` | `ALTER TYPE ADD VALUE` |
| Xóa value | Dễ | **Rất khó** (recreate type) |
| Seed cast | Không cần | `::enum_type` bắt buộc |

## Consequences (Trade-off)
- ➕ Linh hoạt thêm/xóa status; seed không cần cast; JPA mapping đơn giản.
- ➖ CHECK constraint phải sửa qua migration mỗi lần đổi status set (đã xảy ra: V21 mở rộng order status).
- ➖ Không có type-safety mức DB như ENUM thật (bù bằng CHECK + enum Java).

## Notes — Lesson learned
> Order status hiện **drift**: code/migration có 11 giá trị (lẫn cặp legacy+mới: `ASSIGNED`≈`ACCEPTED`,
> `DISPUTED`≈`IN_DISPUTE`, `PENDING`≈`PENDING_PAYMENT`) trong khi CONTEXT mô tả 8. Cần dọn về 1 bộ
> — xem known issues. Khi lệch: **tin code/migration**.
