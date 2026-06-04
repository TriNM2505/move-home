# Move_home — Complete Knowledge Base (Deep Dive)

> **Mục đích:** Tài liệu deep dive cho AI assistant onboarding. Bổ sung cho `PROJECT_KNOWLEDGE.md` (overview).
> **Đối tượng:** AI mới hoặc team member mới muốn hiểu sâu kiến trúc + decisions.
> **Last updated:** 02/06/2026

---

# PART 1 — ARCHITECTURE DECISIONS & RATIONALE

## 1.1 Vì sao chọn Spring Boot 3.5.14 + Java 17?

### Decision
**Spring Boot 3.5.14** (latest stable tại thời điểm bắt đầu project), **Java 17 LTS**.

### Alternatives đã consider
| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **Spring Boot 3.5.14 + Java 17** | LTS, mature ecosystem, team biết Java | Verbose hơn Express | ✅ CHỌN |
| Spring Boot 3.5 + Java 21 | Modern features (records, pattern matching) | Team chưa quen 21 | ❌ |
| Node.js + Express | Faster dev, JS familiar | Team yếu JS, không phù hợp curriculum | ❌ |
| Spring Boot 4.0.x SNAPSHOT | Newest | Unstable, đã gặp Maven issue | ❌ AVOID |

### Rationale
1. **FPT curriculum**: SWP course focus Java enterprise → Spring Boot natural choice
2. **Java 17 LTS**: support đến 2029, stable hơn Java 21+
3. **Spring Boot 3.5**: official Java 17 baseline (3.x require Java 17 min)
4. **Team strength**: 5 member học Java trong các môn trước

### Trade-offs accepted
- Verbose hơn Node.js (boilerplate code)
- Startup chậm hơn Express (8-10s vs 1-2s)
- → Acceptable vì stability + enterprise patterns quan trọng hơn dev speed

---

## 1.2 Vì sao Neon PostgreSQL (cloud) thay vì self-hosted?

### Decision
**Neon Cloud (Singapore region)** free tier.

### Alternatives
| Option | Cost | Setup | Verdict |
|--------|------|-------|---------|
| **Neon Cloud free** | $0 | 5 phút signup | ✅ CHỌN |
| PostgreSQL local (Docker) | $0 | 30-60 phút mỗi member | ❌ |
| AWS RDS | $20+/month | Complex IAM | ❌ Too pricey |
| Supabase | $0 free + $25 pro | Free tier limited | ❌ |
| Railway | $5/month | Simple | ❌ Tốn tiền |

### Rationale
1. **Shared DB cho team 5 người**: localhost không share được
2. **No setup overhead**: teammate chỉ cần `.env` là connect được
3. **Free tier đủ MVP**: 0.5GB storage, không pay
4. **Backup tự động**: Neon manage
5. **Branch DB feature**: future có thể tạo dev/staging branches

### Trade-offs accepted
- **Auto-suspend sau 5 phút idle** → mỗi connect đầu mất 10-30s wake up
- **Latency từ VN tới Singapore**: ~50-80ms (acceptable cho dev)
- **0.5GB limit**: đủ cho seed data 30 orders + tương lai 1000 orders

### Mitigation
- Code có retry logic cho first connection (Hikari pool config)
- Team document trong ENVIRONMENT_SETUP.md về wake-up behavior

---

## 1.3 Vì sao Vanilla JS thay vì React?

### Decision
**Vanilla JS** trong Sprint 1, để ngỏ migrate React ở Sprint 4-6 nếu cần.

### Alternatives
| Option | Time to MVP | Learn curve | Verdict |
|--------|-------------|-------------|---------|
| **Vanilla JS** | 1 ngày/page | Low | ✅ CHỌN cho Sprint 1 |
| React + Vite | 3 ngày setup + 2 ngày/page | High | 🔄 Sprint 4-6 |
| Vue 3 | 2 ngày setup + 1.5 ngày/page | Medium | ❌ |
| Next.js | 1 tuần setup | Very High | ❌ Overkill |

### Rationale Sprint 1
1. **Speed**: Vanilla JS không cần build step, refresh ngay
2. **Team learning**: hiểu DOM/fetch trước khi học React abstraction
3. **Bundle size**: 0 KB framework (chỉ Chart.js CDN ~80KB)
4. **Debug đơn giản**: không source map, không transpile
5. **No npm**: tránh node_modules hell với team chưa quen

### Khi nào migrate React?
Khi UX cần:
- Real-time updates (driver tracking) — Sprint 4
- Complex forms (booking wizard) — Sprint 2 nếu cần
- State management phức tạp — Sprint 4
- Component reuse cao — Sprint 5-6

### Migration path
1. Setup Vite + React trong subfolder `frontend-react/`
2. Migrate 1 page làm proof-of-concept
3. Nếu OK → migrate dần các pages khác
4. Giữ admin pages Vanilla (đủ cho admin use)

---

## 1.4 Vì sao JWT thay vì Session?

### Decision
**JWT HS256** với access (15 min) + refresh (7 day).

### Alternatives
| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| **JWT HS256** | Stateless, scale tốt | Revocation khó | ✅ CHỌN |
| Session + Redis | Easy revoke, smaller | Cần Redis infra | ❌ Tốn infra |
| JWT RS256 | Public/private key, microservices | Overkill 1 service | ❌ |
| OAuth 2.0 + provider | Outsource auth | Phức tạp setup | ❌ |

### Rationale
1. **Stateless backend**: scale horizontal không cần shared session store
2. **Mobile-ready**: JWT work tốt với mobile app (Sprint 4 nếu có app)
3. **Constitution AC-03**: stateless mandatory

### Mitigation cho revocation problem
- **HR-19**: refresh token rotation + reuse detection
- DB lưu refresh token hash, có thể revoke
- Access token short (15 min) → blast radius nhỏ nếu leak

### Implementation
```java
// JwtTokenProvider.java
private final String secret;        // 64 chars từ env JWT_SECRET
private final long accessExpiryMs;  // 15 * 60 * 1000
private final long refreshExpiryMs; // 7 * 24 * 60 * 60 * 1000

public String generateAccessToken(User user) {
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("role", user.getRole().name())
        .claim("email", user.getEmail())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessExpiryMs))
        .signWith(getSigningKey())
        .compact();
}
```

---

## 1.5 Vì sao BCrypt cost 12?

### Decision
**BCrypt cost factor 12** cho password hashing.

### Rationale (theo OWASP 2024)
- Cost 10 (default Spring): ~100ms/hash trên modern CPU → đủ chậm vs brute force
- Cost 12: ~400ms/hash → 4x slower, better security
- Cost 14: ~1.6s/hash → quá chậm cho login UX

### Constitution HR-02
BCrypt cost ≥ 12 mandatory.

### Implementation
```java
// Application config
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

### Performance note
- Login slow ~400ms (perceptible nhưng acceptable)
- Register slow ~400ms (1 lần, OK)
- KHÔNG ảnh hưởng các API khác (chỉ encode/match password)

---

## 1.6 Vì sao Snapshot Pattern cho Commission?

### Problem
Admin có thể đổi commission rate (vd: 30% → 25%) cho đơn mới. **Đơn cũ phải giữ rate cũ** để financial integrity.

### Decision
**Snapshot `commission_rate_snapshot` vào từng order tại thời điểm tạo.**

### Schema
```sql
CREATE TABLE service_order (
    ...
    total_quote NUMERIC(15,0) NOT NULL,
    commission_rate_snapshot NUMERIC(5,4) NOT NULL DEFAULT 0.3000,
    ...
);
```

### Logic
```java
// OrderBookingService.createOrder()
BigDecimal currentRate = settingsService.getCurrentCommissionRate(); // 0.3000
order.setCommissionRateSnapshot(currentRate);
order.setTotalQuote(calculatePrice(...));
orderRepository.save(order);
```

### Calculation in dashboard
```sql
SELECT SUM(total_quote * commission_rate_snapshot) AS total_commission
FROM service_order
WHERE status = 'COMPLETED' AND completed_at >= :since;
```

→ Khi admin đổi rate, đơn cũ vẫn tính 30%, đơn mới tính 25%.

### Constitution DR-01
Snapshot pattern mandatory cho mọi rate có thể thay đổi (commission, peak surcharge, alley surcharge).

---

## 1.7 Vì sao VARCHAR + CHECK thay vì PostgreSQL ENUM?

### Decision
Status fields dùng **VARCHAR + CHECK constraint** thay vì PostgreSQL `ENUM` type.

### Comparison
| Aspect | VARCHAR + CHECK | PostgreSQL ENUM |
|--------|-----------------|-----------------|
| Add new value | `ALTER TABLE ... DROP CHECK, ADD CHECK` | `ALTER TYPE ... ADD VALUE` |
| Remove value | Easy | **CỰC KHÓ** (cần recreate type) |
| Casting in seed | Không cần | `::enum_type` mandatory |
| Hibernate map | `@Enumerated(EnumType.STRING)` | Same |
| Performance | Same | Same |

### Rationale
1. **Flexibility**: thêm/xóa status value dễ
2. **Seed data**: không cần `::transaction_type` cast (đã gặp bug)
3. **JPA mapping đơn giản hơn**

### Implementation example
```sql
CREATE TABLE service_order (
    ...
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING', 'ACCEPTED', 'IN_PROGRESS',
            'COMPLETED', 'CANCELLED', 'DISPUTED'
        )),
    ...
);
```

```java
@Entity
public class Order {
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private OrderStatus status;
}
```

---

## 1.8 Vì sao soft delete?

### Decision
**Soft delete** mandatory cho tất cả entity user-facing (User, Order, DriverProfile, Transaction).

### Implementation
```java
@Entity
@Table(name = "service_order")
@SQLDelete(sql = "UPDATE service_order SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Order {
    @Column(name = "deleted_at")
    private Instant deletedAt;
    // ...
}
```

### Rationale
1. **Audit trail**: biết đơn nào đã xóa, ai xóa, lúc nào
2. **GDPR compliance**: có thể "soft restore" trong 30 ngày
3. **Financial regulation**: VN luật yêu cầu giữ transaction history 5-10 năm
4. **Bug recovery**: nếu xóa nhầm, query DB recover được

### Constitution AC-09
Soft delete mandatory cho tất cả business entities.

### Exceptions (hard delete OK)
- `email_verification_token` — expire 24h, không cần history
- `refresh_token` — revoke = hard delete OK
- Audit log itself — không thể delete

---

## 1.9 Vì sao UUID PK thay vì BIGSERIAL?

### Decision
**UUID v4 với `gen_random_uuid()`** cho mọi primary key.

### Comparison
| Aspect | UUID v4 | BIGSERIAL |
|--------|---------|-----------|
| Size | 16 bytes | 8 bytes |
| Generate client | Yes | No (DB only) |
| Predictable | No | Yes |
| Multi-DB merge | Easy | Conflict |
| Index size | Bigger | Smaller |

### Rationale
1. **Security**: order_id `4b09440b-d266-4742-87e8-2386e061d579` KHÔNG đoán được (vs sequential `42`)
2. **Distributed friendly**: nếu sau scale ra nhiều DB instance, UUID không conflict
3. **API friendly**: external system không thể guess pattern
4. **No race condition**: client có thể generate UUID trước khi insert (offline-first)

### Performance trade-off accepted
- Index B-tree lớn hơn ~2x
- Sort theo UUID không make sense (random)
- → Acceptable vì security + flexibility quan trọng hơn

---

## 1.10 Vì sao Flyway thay vì Hibernate auto-create?

### Decision
**Flyway migration** mandatory. Hibernate `ddl-auto=validate` only.

### Comparison
| Approach | Pros | Cons | Verdict |
|----------|------|------|---------|
| **Flyway versioned** | Versioned, rollback, team-friendly | Manual SQL | ✅ CHỌN |
| Hibernate `update` | Auto, fast | Không reproducible, conflict team | ❌ |
| Hibernate `create-drop` | Test only | Mất data mỗi restart | ❌ |
| Liquibase | XML/YAML format | Verbose | ❌ |

### Rationale
1. **Team reproducibility**: 5 member cùng schema
2. **Production safe**: deploy biết migration nào sẽ apply
3. **History**: `flyway_schema_history` table track everything
4. **Rollback**: có thể undo migration (nếu setup undo scripts)

### Constitution AC-11
Flyway mandatory. Hibernate `ddl-auto=validate` only (verify entity match DB).

### Workflow
1. Dev viết migration `V{N}__description.sql`
2. Commit lên Git
3. Restart app → Flyway tự apply
4. Production: deploy → Flyway tự run
# PART 2 — DATABASE SCHEMA DETAIL

## 2.1 Schema Overview

6 bảng + `flyway_schema_history`:
app_user (1)
├── 1-1 → driver_profile (only DRIVER role)
├── 1-N → email_verification_token
├── 1-N → refresh_token
├── 1-N → service_order (as customer)
├── 1-N → service_order (as driver) — nullable
└── 1-N → transaction
service_order (1) → 1-N transaction

## 2.2 Table: `app_user`

22 columns. Core user table cho tất cả 4 roles.

```sql
CREATE TABLE app_user (
    -- Identity
    id                    UUID            NOT NULL DEFAULT gen_random_uuid(),
    email                 VARCHAR(255)    NOT NULL,
    username              VARCHAR(100)    NOT NULL,
    password_hash         VARCHAR(255)    NOT NULL,  -- BCrypt cost 12
    
    -- Profile
    full_name             VARCHAR(255)    NOT NULL,
    phone                 VARCHAR(20),
    avatar_url            VARCHAR(500),
    date_of_birth         DATE,
    gender                VARCHAR(10)
                          CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    
    -- RBAC
    role                  VARCHAR(20)     NOT NULL
                          CHECK (role IN ('CUSTOMER', 'DRIVER', 'MANAGER', 'ADMIN')),
    status                VARCHAR(30)     NOT NULL DEFAULT 'PENDING_VERIFY'
                          CHECK (status IN (
                              'ACTIVE', 
                              'PENDING_VERIFY', 
                              'PENDING_DOCUMENTS', 
                              'PENDING_DEPOSIT', 
                              'PENDING_APPROVAL',
                              'SUSPENDED', 
                              'REJECTED'
                          )),
    
    -- Email verification
    email_verified        BOOLEAN         NOT NULL DEFAULT FALSE,
    
    -- Driver-specific (only for DRIVER role, NULL for others)
    operating_districts   TEXT[]          -- PostgreSQL ARRAY type
                                          -- vd: ['Ba Dinh', 'Hoan Kiem']
    
    -- Account security (HR-16)
    failed_login_attempts INTEGER         NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    
    -- Terms (Constitution requires explicit accept)
    terms_accepted        BOOLEAN         NOT NULL DEFAULT FALSE,
    terms_accepted_at     TIMESTAMPTZ,
    
    -- Audit timestamps (AC-07)
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,    -- soft delete (AC-09)
    
    -- Constraints
    PRIMARY KEY (id),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_username UNIQUE (username)
);

-- Indexes
CREATE INDEX idx_user_role_status ON app_user(role, status);
CREATE INDEX idx_user_created_at ON app_user(created_at DESC);
CREATE INDEX idx_user_email_lower ON app_user(LOWER(email));  -- case-insensitive
```

### Important fields
- `email`: unique, lowercase by convention
- `username`: unique, can differ from email
- `operating_districts`: PostgreSQL ARRAY, chỉ dùng cho DRIVER (NULL cho others)
- `failed_login_attempts`: reset về 0 sau login OK, lock account khi >= 5
- `locked_until`: NULL nếu không lock, ngược lại là timestamp unlock

## 2.3 Table: `email_verification_token`

```sql
CREATE TABLE email_verification_token (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID            NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    
    -- Token storage (Constitution HR-19)
    token_hash  VARCHAR(64)     NOT NULL,  -- SHA-256 hash của UUID plaintext
    
    -- Lifecycle
    expires_at  TIMESTAMPTZ     NOT NULL,  -- typically NOW() + 24h
    used_at     TIMESTAMPTZ,                -- NULL nếu chưa dùng
    
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (id)
);

CREATE INDEX idx_evt_user_id ON email_verification_token(user_id);
CREATE INDEX idx_evt_token_hash ON email_verification_token(token_hash);
CREATE INDEX idx_evt_expires_at ON email_verification_token(expires_at);
```

### Logic
1. User register → tạo token plaintext UUID
2. Hash SHA-256 → lưu vào `token_hash`
3. Email send token plaintext (mock log console hiện tại)
4. User click link với plaintext token
5. Backend hash token nhận được, compare với `token_hash` trong DB
6. Match + chưa hết hạn + chưa used → set `used_at = NOW()` + update user `email_verified = TRUE`

## 2.4 Table: `refresh_token`

```sql
CREATE TABLE refresh_token (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    
    -- Token (HR-19)
    token_hash      VARCHAR(64)     NOT NULL,   -- SHA-256
    
    -- Lifecycle
    expires_at      TIMESTAMPTZ     NOT NULL,    -- NOW() + 7 days
    revoked_at      TIMESTAMPTZ,                 -- NULL nếu active
    
    -- Rotation tracking (HR-19)
    rotated_from_id UUID            REFERENCES refresh_token(id),
    rotated_to_id   UUID            REFERENCES refresh_token(id),
    
    -- Device tracking (future)
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(45),    -- IPv6 max
    
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (id)
);

CREATE INDEX idx_rt_user_id ON refresh_token(user_id);
CREATE INDEX idx_rt_token_hash ON refresh_token(token_hash);
CREATE INDEX idx_rt_expires_at ON refresh_token(expires_at);
```

### Logic (HR-19 — Refresh Token Rotation + Reuse Detection)
1. Login → tạo refresh_token_1, return plaintext token
2. Client store plaintext token
3. Khi access token expire → client send refresh_token_1 plaintext
4. Backend:
   - Hash token, find in DB
   - Nếu `revoked_at IS NOT NULL` → **PANIC**: someone replayed old token
   - Revoke ALL refresh tokens của user (kick out all devices)
   - Return 401
5. Nếu chưa revoke + chưa expire:
   - Revoke refresh_token_1 (`revoked_at = NOW()`)
   - Tạo refresh_token_2, link `rotated_from_id = refresh_token_1.id`
   - Return new access + new refresh

→ Token rotation chống reuse attack.

## 2.5 Table: `driver_profile`

```sql
CREATE TABLE driver_profile (
    user_id                       UUID            NOT NULL PRIMARY KEY 
                                                  REFERENCES app_user(id) ON DELETE CASCADE,
    
    -- KYC info
    license_number                VARCHAR(50)     NOT NULL,
    license_image_url             VARCHAR(500),
    license_class                 VARCHAR(10),    -- B1, B2, C, D
    
    -- Vehicle info
    vehicle_plate                 VARCHAR(20)     NOT NULL,
    vehicle_type                  VARCHAR(50)     NOT NULL,  
                                                  -- 'Xe tai 500kg', 'Xe tai 1 tan', etc.
    vehicle_year                  INTEGER,
    vehicle_image_url             VARCHAR(500),
    
    -- Financial
    deposit_amount                NUMERIC(15, 0)  NOT NULL DEFAULT 0,
    deposit_paid_at               TIMESTAMPTZ,
    
    -- Approval (Manager workflow Sprint 5)
    approved_at                   TIMESTAMPTZ,
    approved_by_manager_id        UUID            REFERENCES app_user(id),
    rejected_at                   TIMESTAMPTZ,
    rejection_reason              TEXT,
    
    -- Performance metrics (denormalized for dashboard)
    total_orders_completed        INTEGER         NOT NULL DEFAULT 0,
    total_revenue                 NUMERIC(15, 0)  NOT NULL DEFAULT 0,
    average_rating                NUMERIC(3, 2)   DEFAULT 0.00,  -- 1.00 → 5.00
    
    -- Audit
    created_at                    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                    TIMESTAMPTZ
);

CREATE INDEX idx_dp_approved ON driver_profile(approved_at) WHERE approved_at IS NOT NULL;
CREATE INDEX idx_dp_total_revenue ON driver_profile(total_revenue DESC);
```

### Important
- `user_id` là PK + FK → 1-1 relationship với `app_user`
- `deposit_amount`: tiền cọc driver, default 3,000,000 VND (3 triệu) cho activate account
- `average_rating`: tính trung bình từ Customer feedback (Sprint 4)
- Performance metrics denormalize để tăng tốc dashboard query

## 2.6 Table: `service_order`

```sql
CREATE TABLE service_order (
    -- Identity
    id                          UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code                  VARCHAR(20)     NOT NULL UNIQUE,  
                                                -- vd: 'MH202606010001'
    
    -- Parties
    customer_id                 UUID            NOT NULL REFERENCES app_user(id),
    driver_id                   UUID            REFERENCES app_user(id),  
                                                -- NULL khi PENDING
    
    -- Locations (Hanoi inner districts)
    pickup_address              VARCHAR(500)    NOT NULL,
    pickup_district             VARCHAR(100),   -- 'Ba Dinh', 'Hoan Kiem', ...
    pickup_latitude             NUMERIC(9, 6),  -- nullable, OSRM optional
    pickup_longitude            NUMERIC(9, 6),
    
    dropoff_address             VARCHAR(500)    NOT NULL,
    dropoff_district            VARCHAR(100),
    dropoff_latitude            NUMERIC(9, 6),
    dropoff_longitude           NUMERIC(9, 6),
    
    -- Scheduling (AC-07)
    scheduled_at                TIMESTAMPTZ     NOT NULL,
    
    -- Status (6 statuses)
    status                      VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN (
                                    'PENDING',
                                    'ACCEPTED', 
                                    'IN_PROGRESS', 
                                    'COMPLETED', 
                                    'CANCELLED', 
                                    'DISPUTED'
                                )),
    
    -- Pricing (AC-08, FR-006 commission)
    total_quote                 NUMERIC(15, 0)  NOT NULL,  
                                                -- VND nguyên đồng
    commission_rate_snapshot    NUMERIC(5, 4)   NOT NULL DEFAULT 0.3000,  
                                                -- 30%
    
    -- Distance (OSRM or fallback)
    distance_km                 NUMERIC(6, 2),
    estimated_duration_minutes  INTEGER,
    
    -- Event timestamps
    accepted_at                 TIMESTAMPTZ,    -- when driver accept
    started_at                  TIMESTAMPTZ,    -- when IN_PROGRESS
    completed_at                TIMESTAMPTZ,    -- when COMPLETED (trigger escrow 2h)
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason         TEXT,
    
    -- Customer notes
    notes                       TEXT,           -- 'Chung cu lau 5, khong co thang may'
    
    -- Audit
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                  TIMESTAMPTZ
);

CREATE INDEX idx_order_code ON service_order(order_code);
CREATE INDEX idx_order_customer ON service_order(customer_id, created_at DESC);
CREATE INDEX idx_order_driver ON service_order(driver_id, created_at DESC);
CREATE INDEX idx_order_status_created ON service_order(status, created_at DESC);
CREATE INDEX idx_order_completed_at ON service_order(completed_at DESC) 
    WHERE status = 'COMPLETED';
```

### Field reminder
- `order_code`: human-readable, generated by service `'MH' + yyyyMMdd + sequence`
- `total_quote`: total customer pays (base + surcharges, no commission split visible)
- `commission_rate_snapshot`: snapshot rate cho FR-006
- `commission_amount` = `total_quote × commission_rate_snapshot` (compute at query)
- `driver_earning` = `total_quote × (1 - commission_rate_snapshot)`

### Status transitions (state machine)
PENDING ─[driver accept]→ ACCEPTED ─[start trip]→ IN_PROGRESS ─[done]→ COMPLETED
│                                                                       │
└─[cancel before pickup]→ CANCELLED ←─────────────────────[dispute]→ DISPUTED

## 2.7 Table: `transaction`

Financial ledger — append only.

```sql
CREATE TABLE transaction (
    id          UUID            NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Relations
    user_id     UUID            NOT NULL REFERENCES app_user(id),
    order_id    UUID            REFERENCES service_order(id),  -- nullable for non-order tx
    
    -- Type (7 types)
    type        VARCHAR(30)     NOT NULL
                CHECK (type IN (
                    'DEPOSIT_TOP_UP',      -- Driver nạp tiền cọc
                    'DEPOSIT_REFUND',       -- Hoàn cọc khi nghỉ
                    'ORDER_PAYMENT',        -- Customer trả tiền đơn
                    'DRIVER_EARNING',       -- Driver nhận 70% sau escrow
                    'PLATFORM_FEE',         -- Move_home nhận 30% commission
                    'DAMAGE_DEDUCTION',     -- Trừ tiền nếu damage
                    'REFUND'                -- Hoàn tiền customer
                )),
    
    -- Amount (always positive, type indicates direction)
    amount      NUMERIC(15, 0)  NOT NULL,
    
    -- Metadata
    description TEXT,
    reference   VARCHAR(100),    -- VNPay txn ref, etc.
    
    -- Audit (no delete — append only)
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_user ON transaction(user_id, created_at DESC);
CREATE INDEX idx_tx_order ON transaction(order_id);
CREATE INDEX idx_tx_type_created ON transaction(type, created_at DESC);
```

### Transaction lifecycle khi COMPLETED order
1. **Customer thanh toán** → `ORDER_PAYMENT`, user_id = customer, amount = total_quote, positive
2. **Move_home thu commission** → `PLATFORM_FEE`, user_id = admin, amount = total_quote × 0.30, positive
3. **Driver nhận tiền** (sau escrow 2h) → `DRIVER_EARNING`, user_id = driver, amount = total_quote × 0.70, positive

→ Tổng: 100% transparent, balance per user computable từ `SUM(amount) GROUP BY user_id, type`.

## 2.8 ER Diagram (text)
┌─────────────────┐
│    app_user     │
│ id (PK)         │◀────┐
│ email           │     │
│ role            │     │
│ status          │     │
└─────────────────┘     │
│ 1             │ N (driver)
│               │
│ 1-1           │
▼               │
┌─────────────────┐     │
│ driver_profile  │     │
│ user_id (PK,FK) │     │
│ license_number  │     │
│ vehicle_plate   │     │
│ approved_at     │     │
└─────────────────┘     │
│
┌───────────────┘
│ N (customer)
▼
┌─────────────────┐
│ service_order   │
│ id (PK)         │◀────┐
│ customer_id (FK)│     │
│ driver_id (FK)  │     │
│ status          │     │
│ total_quote     │     │
│ commission_rate │     │
└─────────────────┘     │
│ N
┌───────────────┘
│
▼
┌─────────────────┐
│   transaction   │
│ id (PK)         │
│ user_id (FK)    │
│ order_id (FK)   │
│ type            │
│ amount          │
└─────────────────┘
┌─────────────────────────┐    ┌─────────────────┐
│ email_verification_token│    │ refresh_token   │
│ id (PK)                 │    │ id (PK)         │
│ user_id (FK)            │    │ user_id (FK)    │
│ token_hash              │    │ token_hash      │
│ expires_at              │    │ expires_at      │
└─────────────────────────┘    │ revoked_at      │
│ rotated_from_id │
└─────────────────┘

## 2.9 Seed Data (V99__seed_demo_data.sql)

604 dòng SQL, tạo:

### Users (17 total)
- 1 ADMIN: `admin@movehome.vn` (ACTIVE)
- 2 MANAGER: `manager1@movehome.vn`, `manager2@movehome.vn` (ACTIVE)
- 5 DRIVER ACTIVE: `driver1-5@movehome.vn`
  - Nguyen Van Minh — 12 orders, 16.8M revenue, 4.5 rating
  - Tran Thanh Hung — 8 orders, 11.2M, 4.8
  - Le Quang Duc — 15 orders, 21M, 4.3
  - Pham Thi Lan — 5 orders, 7M, 4.9
  - Hoang Van Nam — 20 orders, 28M, 4.7
- 1 DRIVER PENDING_APPROVAL: `driver_pending@movehome.vn` (Vo Thanh Tung)
- 10 CUSTOMER (mix 8 ACTIVE, 2 PENDING_VERIFY)
- ⚠️ All passwords: BCrypt hash của `Admin@2026` (cost 12)

### Driver profiles (6)
Mapping 1-1 với 6 driver users. Có:
- licenseNumber: 'B2-HN-XXXXXX'
- vehiclePlate: '30A-XXXXX'
- vehicleType: rotate giữa 'Xe tai 500kg', 'Xe tai 1 tan', 'Xe tai 1.5 tan'
- depositAmount: 3,000,000 VND (đã pay)
- approvedAt: NOW() - 29 days (cho 5 active)

### Orders (30)
- **20 COMPLETED** (revenue history):
  - completed_at: spread trong 30 ngày qua (1 đơn/ngày average)
  - total_quote: random 800,000 - 5,000,000 VND
  - commission_rate_snapshot: 0.3000 (30%)
  - Districts: random từ Ba Dinh, Hoan Kiem, Hai Ba Trung, Dong Da, Tay Ho, Cau Giay, Thanh Xuan
- **5 IN_PROGRESS** (đang vận chuyển)
- **3 PENDING** (chưa driver accept)
- **2 CANCELLED** (đã hủy)

### Transactions (75)
Cho mỗi COMPLETED order (20 orders × 3 tx = 60):
- ORDER_PAYMENT (customer)
- PLATFORM_FEE (admin, 30%)
- DRIVER_EARNING (driver, 70%)

Plus 6 DEPOSIT_TOP_UP (mỗi driver 3M VND).

Tổng: 60 + 6 = ~75 transactions.

---

# PART 3 — API ENDPOINTS CATALOG

## 3.1 Overview

**Base URL:** `http://localhost:8080`
**Auth:** Bearer JWT trong header `Authorization: Bearer <token>`
**Format:** JSON request/response, UTF-8
**Error codes:** Standard HTTP + custom in body

12 endpoints chia 3 nhóm:
- Auth (4) — public hoặc authenticated
- Admin Dashboard (6) — require ROLE_ADMIN
- Admin Lists (3) — require ROLE_ADMIN

## 3.2 Auth Endpoints

### POST /api/auth/register/customer
**Public.** Register customer mới.

Request:
```json
{
  "fullName": "Nguyen Van A",
  "email": "test@example.com",
  "phone": "+84901234567",
  "password": "Test@1234",
  "passwordConfirm": "Test@1234",
  "termsAccepted": true
}
```

Validation:
- email: format valid, unique
- phone: optional, regex `^\+84[0-9]{9}$`
- password: min 8 chars, contain uppercase + digit + special
- passwordConfirm: match password
- termsAccepted: must be true

Response 201:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "abc123...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "test@example.com",
    "fullName": "Nguyen Van A",
    "role": "CUSTOMER",
    "status": "PENDING_VERIFY"
  }
}
```

Errors:
- 400 — Validation fail (field errors)
- 409 — Email already exists (EMAIL_EXISTS)

Side effects:
- Tạo user với `status = PENDING_VERIFY`, `email_verified = FALSE`
- Tạo `email_verification_token` với 24h expire
- Log token plaintext ra console (mock email)

### POST /api/auth/login
**Public.** Login với email + password.

Request:
```json
{
  "email": "admin@movehome.vn",
  "password": "Admin@2026"
}
```

Response 200:
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "xyz789...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "email": "admin@movehome.vn",
    "fullName": "Admin He Thong",
    "role": "ADMIN",
    "status": "ACTIVE",
    "mustChangePassword": false
  }
}
```

Errors:
- 401 INVALID_CREDENTIALS — sai email/password
- 403 EMAIL_NOT_VERIFIED — status = PENDING_VERIFY
- 403 ACCOUNT_SUSPENDED — status = SUSPENDED
- 423 ACCOUNT_LOCKED — failed >= 5 attempts

Side effects:
- Tăng `failed_login_attempts` nếu fail
- Reset = 0 nếu success
- Update `last_login_at`

### POST /api/auth/verify-email
**Public.** Verify email với token.

Request:
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000"
}
```

Response 200:
```json
{
  "message": "Email verified successfully",
  "userId": "uuid"
}
```

Errors:
- 400 INVALID_TOKEN
- 410 TOKEN_EXPIRED
- 410 TOKEN_ALREADY_USED

Side effects:
- Update user: `email_verified = TRUE`, `status = ACTIVE`
- Update token: `used_at = NOW()`

### POST /api/auth/refresh
**Public** (nhưng cần refresh token).

Request:
```json
{
  "refreshToken": "xyz789..."
}
```

Response 200 — giống login response.

Errors:
- 401 INVALID_REFRESH_TOKEN
- 401 TOKEN_REUSED (HR-19 panic: revoke all user tokens)
- 401 TOKEN_EXPIRED

Side effects:
- Revoke old refresh token
- Tạo new refresh token (rotation)
- Track `rotated_from_id` chain

## 3.3 Admin Dashboard Endpoints

Tất cả endpoints cần `Authorization: Bearer <admin_token>` + `@PreAuthorize("hasRole('ADMIN')")`.

### GET /api/admin/dashboard/overview
Combined response — gọi 1 lần, trả 5 sections.

Response 200:
```json
{
  "kpi": {
    "totalCustomers": 10,
    "activeDrivers": 5,
    "pendingDriverApprovals": 1,
    "totalOrdersToday": 8,
    "totalOrdersThisMonth": 30,
    "totalRevenueThisMonth": 50000000,
    "totalCommissionThisMonth": 15000000,
    "pendingOrders": 3,
    "completedOrders": 20,
    "inDisputeOrders": 0
  },
  "revenueChart": {
    "points": [
      { "date": "2026-05-03", "revenue": 2500000, "commission": 750000, "orderCount": 1 },
      ... (30 entries)
    ]
  },
  "topDrivers": {
    "topDrivers": [
      {
        "driverId": "uuid",
        "fullName": "Hoang Van Nam",
        "totalOrders": 20,
        "totalRevenue": 28000000,
        "averageRating": 4.70
      },
      ... (5 entries)
    ]
  },
  "recentOrders": {
    "orders": [
      {
        "orderId": "uuid",
        "orderCode": "SEED-P-003",
        "customerName": "Pham Van Tuan",
        "driverName": null,
        "status": "PENDING",
        "totalQuote": 2100000,
        "createdAt": "2026-06-01T03:06:43.694760Z"
      },
      ... (10 entries)
    ]
  },
  "statusDistribution": {
    "distribution": {
      "PENDING": 3,
      "ACCEPTED": 0,
      "IN_PROGRESS": 5,
      "COMPLETED": 20,
      "CANCELLED": 2,
      "DISPUTED": 0
    }
  }
}
```

### GET /api/admin/dashboard/kpi
Chỉ trả KPI section.

### GET /api/admin/dashboard/revenue-chart
Chỉ trả revenue chart 30 days.

### GET /api/admin/dashboard/top-drivers
Top 5 drivers by revenue.

### GET /api/admin/dashboard/recent-orders
10 orders mới nhất.

### GET /api/admin/dashboard/status-distribution
Status distribution count.

## 3.4 Admin List Endpoints

### GET /api/admin/dashboard/orders
Query params:
- `status` (optional): PENDING, COMPLETED, etc.
- `page` (default 0)
- `size` (default 50)

Response: `Page<OrderListItem>`

```json
{
  "content": [
    {
      "id": "uuid",
      "orderCode": "SEED-P-003",
      "customerName": "Pham Van Tuan",
      "driverName": null,
      "status": "PENDING",
      "pickupDistrict": "Dong Da",
      "dropoffDistrict": "Dong Da",
      "totalQuote": 2100000,
      "commissionRateSnapshot": 0.3000,
      "scheduledAt": "2026-06-02T03:11:43.694760Z",
      "createdAt": "2026-06-01T03:06:43.694760Z",
      "completedAt": null
    },
    ... (30 items)
  ],
  "totalElements": 30,
  "totalPages": 1
}
```

### GET /api/admin/dashboard/drivers
Query params:
- `status` (optional): ACTIVE, PENDING_APPROVAL, SUSPENDED

Response: `List<DriverListItem>`

```json
[
  {
    "userId": "uuid",
    "fullName": "Vo Thanh Tung",
    "email": "driver_pending@movehome.vn",
    "phone": "+84901001006",
    "status": "PENDING_APPROVAL",
    "licenseNumber": "B2-HN-678901",
    "vehiclePlate": "30D-67890",
    "vehicleType": "Xe tai 500kg",
    "depositAmount": 3000000,
    "totalOrdersCompleted": 0,
    "totalRevenue": 0,
    "averageRating": 0.00,
    "createdAt": "2026-05-29T03:11:43Z",
    "approvedAt": null
  },
  ... (6 items)
]
```

### GET /api/admin/dashboard/customers
Query params:
- `status` (optional): ACTIVE, PENDING_VERIFY, SUSPENDED

Response: `List<CustomerListItem>`

```json
[
  {
    "id": "uuid",
    "fullName": "Nguyen Minh Tri",
    "email": "test@gmail.com",
    "phone": "+84962397517",
    "status": "PENDING_VERIFY",
    "emailVerified": false,
    "totalOrdersPlaced": 0,
    "createdAt": "2026-06-01T10:11:38Z"
  },
  ... (11 items)
]
```

---markdown---

# PART 4 — CODE PATTERNS & EXAMPLES

## 4.1 Entity Pattern

Mọi entity tuân thủ pattern này. Ví dụ `Order.java`:

```java
package vn.movehome.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Don hang chuyen nha — thuc the trung tam cua he thong Move_home.
 * Bang ten "service_order" (KHONG dung "order" — reserved word PostgreSQL).
 * Constitution AC-09: soft delete qua deleted_at.
 * AC-07: tat ca timestamp la TIMESTAMP WITH TIME ZONE, luu UTC.
 * AC-08: totalQuote, commissionRateSnapshot dung BigDecimal (khong float/double).
 */
@Entity(name = "ServiceOrder")  // JPQL name avoid conflict with ORDER BY
@Table(
    name = "service_order",
    indexes = {
        @Index(name = "idx_order_code", columnList = "order_code", unique = true),
        @Index(name = "idx_order_customer", columnList = "customer_id, created_at"),
        @Index(name = "idx_order_driver", columnList = "driver_id, created_at"),
        @Index(name = "idx_order_status", columnList = "status, created_at")
    }
)
@SQLDelete(sql = "UPDATE service_order SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_code", nullable = false, unique = true, length = 20)
    private String orderCode;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "pickup_address", nullable = false, length = 500)
    private String pickupAddress;

    @Column(name = "pickup_district", length = 100)
    private String pickupDistrict;

    @Column(name = "dropoff_address", nullable = false, length = 500)
    private String dropoffAddress;

    @Column(name = "dropoff_district", length = 100)
    private String dropoffDistrict;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // VND nguyen dong, BigDecimal scale=0 (AC-08)
    @Column(name = "total_quote", precision = 15, scale = 0, nullable = false)
    private BigDecimal totalQuote;

    // Snapshot ty le commission TAI THOI DIEM TAO DON (FR-006)
    // Default 30% = 0.3000
    @Column(name = "commission_rate_snapshot", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal commissionRateSnapshot = new BigDecimal("0.3000");

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

### Key patterns
- `@Entity(name = "...")` — JPQL name khác `@Table(name = "...")`
- `@SQLDelete` + `@SQLRestriction` — soft delete
- `@Builder.Default` — set giá trị mặc định cho field có `@Builder`
- `@CreationTimestamp` / `@UpdateTimestamp` — Hibernate tự manage
- `BigDecimal` precision/scale match DB
- Lombok `@Getter @Setter @Builder` (KHÔNG `@Data`)

## 4.2 Repository Pattern

```java
package vn.movehome.backend.repository;

import vn.movehome.backend.entity.Order;
import vn.movehome.backend.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Method name query (Spring Data tự sinh SQL)
    long countByStatus(OrderStatus status);
    
    long countByStatusAndCompletedAtGreaterThanEqual(OrderStatus status, Instant since);
    
    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
    
    // Custom JPQL query
    @Query("""
        SELECT SUM(o.totalQuote)
        FROM ServiceOrder o
        WHERE o.status = 'COMPLETED'
          AND o.completedAt >= :since
    """)
    BigDecimal sumRevenueSince(@Param("since") Instant since);
    
    @Query("""
        SELECT SUM(o.totalQuote * o.commissionRateSnapshot)
        FROM ServiceOrder o
        WHERE o.status = 'COMPLETED'
          AND o.completedAt >= :since
    """)
    BigDecimal sumCommissionSince(@Param("since") Instant since);
    
    // Dashboard recent orders
    @Query("""
        SELECT o
        FROM ServiceOrder o
        ORDER BY o.createdAt DESC
    """)
    List<Order> findRecent(Pageable pageable);
}
```

### Key patterns
- `extends JpaRepository<EntityType, IdType>` — get CRUD miễn phí
- Method name query — Spring Data parse: `findBy...`, `countBy...`, `existsBy...`
- `@Query` JPQL — dùng entity name `ServiceOrder` (KHÔNG `service_order`)
- `@Param` để bind variables
- Trả `Optional<T>` cho find single, `List<T>` cho find many, `Page<T>` cho pagination

## 4.3 Service Pattern

```java
package vn.movehome.backend.service;

import vn.movehome.backend.dto.admin.KpiResponse;
import vn.movehome.backend.entity.UserRole;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.repository.UserRepository;
import vn.movehome.backend.repository.OrderRepository;
import vn.movehome.backend.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Service tinh toan KPI va du lieu cho Admin Dashboard.
 * Tat ca query read-only de toi uu performance.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final DriverProfileRepository driverProfileRepository;

    private static final ZoneId VN_TIMEZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Lay KPI tong quan cho dashboard.
     * AC-07: tinh "today" theo Asia/Ho_Chi_Minh, KHONG UTC.
     */
    public KpiResponse getKpi() {
        log.info("Loading KPI dashboard");

        // Compute timezone-aware boundaries
        Instant startOfToday = LocalDate.now(VN_TIMEZONE)
            .atStartOfDay(VN_TIMEZONE)
            .toInstant();
        
        Instant startOfMonth = LocalDate.now(VN_TIMEZONE)
            .withDayOfMonth(1)
            .atStartOfDay(VN_TIMEZONE)
            .toInstant();

        long totalCustomers = userRepository.countByRoleAndStatus(
            UserRole.CUSTOMER, UserStatus.ACTIVE);
        
        long activeDrivers = userRepository.countByRoleAndStatus(
            UserRole.DRIVER, UserStatus.ACTIVE);
        
        long pendingApprovals = userRepository.countByRoleAndStatus(
            UserRole.DRIVER, UserStatus.PENDING_APPROVAL);
        
        long ordersToday = orderRepository.countByCreatedAtGreaterThanEqual(startOfToday);
        long ordersThisMonth = orderRepository.countByCreatedAtGreaterThanEqual(startOfMonth);

        BigDecimal revenueThisMonth = orderRepository.sumRevenueSince(startOfMonth);
        BigDecimal commissionThisMonth = orderRepository.sumCommissionSince(startOfMonth);

        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);
        long completedOrders = orderRepository.countByStatus(OrderStatus.COMPLETED);
        long inDisputeOrders = orderRepository.countByStatus(OrderStatus.DISPUTED);

        return KpiResponse.builder()
            .totalCustomers(totalCustomers)
            .activeDrivers(activeDrivers)
            .pendingDriverApprovals(pendingApprovals)
            .totalOrdersToday(ordersToday)
            .totalOrdersThisMonth(ordersThisMonth)
            .totalRevenueThisMonth(revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO)
            .totalCommissionThisMonth(commissionThisMonth != null ? commissionThisMonth : BigDecimal.ZERO)
            .pendingOrders(pendingOrders)
            .completedOrders(completedOrders)
            .inDisputeOrders(inDisputeOrders)
            .build();
    }
    
    // ... other methods
}
```

### Key patterns
- `@Service` + `@Transactional(readOnly = true)` class level
- `@RequiredArgsConstructor` từ Lombok → auto constructor injection cho `final` fields
- `@Slf4j` → có `log` field tự động
- Timezone-aware: dùng `ZoneId.of("Asia/Ho_Chi_Minh")` cho business logic
- `Instant` cho UTC storage
- Null-safe: `null != null ? ... : BigDecimal.ZERO`
- Logging: `log.info()`, `log.debug()`, `log.error()` (KHÔNG `System.out`)

## 4.4 Controller Pattern

```java
package vn.movehome.backend.controller;

import vn.movehome.backend.dto.admin.*;
import vn.movehome.backend.entity.OrderStatus;
import vn.movehome.backend.entity.UserStatus;
import vn.movehome.backend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller cho Admin Dashboard endpoints.
 * Tat ca endpoints chi cho ADMIN role (HR-10 RBAC).
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/overview")
    public ResponseEntity<DashboardOverviewResponse> getOverview() {
        log.info("GET /api/admin/dashboard/overview");
        return ResponseEntity.ok(dashboardService.getOverview());
    }

    @GetMapping("/kpi")
    public ResponseEntity<KpiResponse> getKpi() {
        return ResponseEntity.ok(dashboardService.getKpi());
    }

    @GetMapping("/orders")
    public ResponseEntity<Page<OrderListItem>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("GET /orders status={} page={} size={}", status, page, size);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(dashboardService.getAllOrders(status, pageable));
    }

    @GetMapping("/drivers")
    public ResponseEntity<List<DriverListItem>> listDrivers(
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(dashboardService.getAllDrivers(status));
    }

    @GetMapping("/customers")
    public ResponseEntity<List<CustomerListItem>> listCustomers(
            @RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(dashboardService.getAllCustomers(status));
    }
}
```

### Key patterns
- `@RestController` (NOT `@Controller`) — auto-serialize response as JSON
- `@RequestMapping("/api/...")` class level cho common path
- `@PreAuthorize("hasRole('ADMIN')")` class level → tất cả method enforce
- `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping`
- `@RequestParam(required = false)` cho optional query params
- `@RequestParam(defaultValue = "0")` cho default
- `ResponseEntity.ok(...)` cho 200 OK
- `ResponseEntity.status(HttpStatus.CREATED).body(...)` cho 201

## 4.5 DTO Pattern (Records)

Java 17 `record` cho immutable DTOs. Cleaner than `class`.

```java
package vn.movehome.backend.dto.admin;

import java.math.BigDecimal;

/**
 * KPI overview cho Admin Dashboard.
 * FR-001 -> FR-005: cac so lieu tong quan hien thi tren cards.
 */
public record KpiResponse(
    long totalCustomers,
    long activeDrivers,
    long pendingDriverApprovals,
    long totalOrdersToday,
    long totalOrdersThisMonth,
    BigDecimal totalRevenueThisMonth,
    BigDecimal totalCommissionThisMonth,
    long pendingOrders,
    long completedOrders,
    long inDisputeOrders
) {
    // Builder pattern via static method (Lombok không work tốt với records)
    public static KpiResponseBuilder builder() {
        return new KpiResponseBuilder();
    }
    
    public static class KpiResponseBuilder {
        private long totalCustomers;
        // ... fields
        
        public KpiResponseBuilder totalCustomers(long val) {
            this.totalCustomers = val;
            return this;
        }
        // ... other setters
        
        public KpiResponse build() {
            return new KpiResponse(totalCustomers, activeDrivers, /* ... */);
        }
    }
}
```

### Key patterns
- `record` instead of class cho DTO
- Field accessors tự động: `kpiResponse.totalCustomers()` (KHÔNG `getTotalCustomers()`)
- Immutable — không có setter
- Constructor canonical tự động
- Equals/hashCode/toString tự động

## 4.6 Exception Handling Pattern

Global handler cho tất cả exception.

```java
package vn.movehome.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — convert exception thanh JSON response.
 * Centralize error handling, KHONG xu ly trong controller.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError err : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(err.getField(), err.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(Map.of(
            "error", Map.of(
                "code", "VALIDATION_FAILED",
                "message", "Du lieu khong hop le",
                "fields", fieldErrors
            )
        ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "error", Map.of(
                "code", "INVALID_CREDENTIALS",
                "message", "Email hoac mat khau khong dung"
            )
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "error", Map.of(
                "code", "ACCESS_DENIED",
                "message", "Khong co quyen truy cap"
            )
        ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of(
            "error", Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage()
            )
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "error", Map.of(
                "code", "INTERNAL_ERROR",
                "message", "Loi he thong"
            )
        ));
    }
}
```

### Custom business exception
```java
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    // Common factories
    public static BusinessException emailExists() {
        return new BusinessException("EMAIL_EXISTS", "Email da ton tai", HttpStatus.CONFLICT);
    }

    public static BusinessException emailNotVerified() {
        return new BusinessException("EMAIL_NOT_VERIFIED", "Email chua xac thuc", HttpStatus.FORBIDDEN);
    }
}
```

## 4.7 Security Config Pattern

```java
package vn.movehome.backend.config;

import vn.movehome.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity   // enable @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // Constitution HR-02
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5500",
            "http://127.0.0.1:5500"   // Live Server VS Code
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())  // stateless JWT, không cần CSRF
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()   // public endpoints
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### Key patterns
- `@EnableMethodSecurity` để `@PreAuthorize` work
- `BCryptPasswordEncoder(12)` đúng Constitution
- CORS allow Live Server origins
- Stateless session (JWT)
- CSRF disabled (stateless API)
- JWT filter trước UsernamePasswordAuthenticationFilter

---

# PART 5 — AUTH FLOW DETAIL

## 5.1 Registration Flow Sequence
Customer                Frontend              Backend                  Database
│                       │                     │                         │
│ Fill register form    │                     │                         │
│──────────────────────▶│                     │                         │
│                       │                     │                         │
│                       │ POST /api/auth/      │                        │
│                       │ register/customer    │                        │
│                       │─────────────────────▶│                        │
│                       │                     │                         │
│                       │                     │ Validate fields         │
│                       │                     │ Check email unique      │
│                       │                     │─────────────────────────▶│
│                       │                     │◀────────────────────────│
│                       │                     │                         │
│                       │                     │ BCrypt hash password    │
│                       │                     │ Generate user UUID      │
│                       │                     │ INSERT app_user         │
│                       │                     │ (status=PENDING_VERIFY) │
│                       │                     │─────────────────────────▶│
│                       │                     │                         │
│                       │                     │ Generate verify token   │
│                       │                     │ SHA-256 hash token      │
│                       │                     │ INSERT email_verif_token│
│                       │                     │─────────────────────────▶│
│                       │                     │                         │
│                       │                     │ Log token plaintext     │
│                       │                     │ (mock email service)    │
│                       │                     │                         │
│                       │                     │ Generate JWT access     │
│                       │                     │ Generate refresh token  │
│                       │                     │ Hash refresh, save      │
│                       │                     │─────────────────────────▶│
│                       │                     │                         │
│                       │ 201 Created          │                        │
│                       │ + tokens + user     │                         │
│                       │◀─────────────────────│                        │
│                       │                     │                         │
│                       │ localStorage save   │                         │
│                       │ Show success msg    │                         │
│ Redirect login.html   │                     │                         │
│◀──────────────────────│                     │                         │

## 5.2 Login Flow Sequence
User                  Frontend              Backend                  Database
│                       │                     │                         │
│ Fill login form       │                     │                         │
│──────────────────────▶│                     │                         │
│                       │ POST /api/auth/login│                         │
│                       │─────────────────────▶│                        │
│                       │                     │                         │
│                       │                     │ Find user by email      │
│                       │                     │─────────────────────────▶│
│                       │                     │◀────────────────────────│
│                       │                     │                         │
│                       │                     │ Check failed_attempts   │
│                       │                     │ Check locked_until      │
│                       │                     │ BCrypt verify password  │
│                       │                     │                         │
│                       │                     │ IF password wrong:      │
│                       │                     │   increment failed      │
│                       │                     │   IF failed >= 5:       │
│                       │                     │     locked_until=NOW+15m│
│                       │                     │   return 401            │
│                       │                     │                         │
│                       │                     │ IF status=PENDING_VERIFY│
│                       │                     │   return 403 (HR-20)    │
│                       │                     │                         │
│                       │                     │ IF status=SUSPENDED     │
│                       │                     │   return 403            │
│                       │                     │                         │
│                       │                     │ Reset failed_attempts=0 │
│                       │                     │ Update last_login_at    │
│                       │                     │─────────────────────────▶│
│                       │                     │                         │
│                       │                     │ Generate JWT access     │
│                       │                     │ Generate refresh token  │
│                       │                     │ Save refresh hash       │
│                       │                     │─────────────────────────▶│
│                       │                     │                         │
│                       │ 200 OK              │                         │
│                       │ + tokens + user     │                         │
│                       │◀─────────────────────│                        │
│                       │                     │                         │
│                       │ localStorage save   │                         │
│                       │ Check user.role     │                         │
│                       │ Redirect by role:   │                         │
│                       │  - ADMIN → admin/   │                         │
│                       │  - CUSTOMER → cust/ │                         │
│ Land on role page     │                     │                         │
│◀──────────────────────│                     │                         │

## 5.3 Verify Email Flow
User           Frontend           Backend                Database
│                │                  │                       │
│ Click link with │                 │                       │
│ ?token=abc-123  │                 │                       │
│────────────────▶│                 │                       │
│                │                  │                       │
│                │ POST /api/auth/  │                       │
│                │ verify-email     │                       │
│                │ { token: abc123 }│                       │
│                │──────────────────▶│                      │
│                │                  │                       │
│                │                  │ Hash SHA-256(token)   │
│                │                  │ Find token_hash       │
│                │                  │──────────────────────▶│
│                │                  │◀──────────────────────│
│                │                  │                       │
│                │                  │ IF not found:         │
│                │                  │   return 400 INVALID  │
│                │                  │                       │
│                │                  │ IF used_at != NULL:   │
│                │                  │   return 410 USED     │
│                │                  │                       │
│                │                  │ IF expires_at < NOW:  │
│                │                  │   return 410 EXPIRED  │
│                │                  │                       │
│                │                  │ UPDATE token:         │
│                │                  │   used_at = NOW()     │
│                │                  │ UPDATE app_user:      │
│                │                  │   email_verified=TRUE │
│                │                  │   status=ACTIVE       │
│                │                  │──────────────────────▶│
│                │                  │                       │
│                │ 200 OK           │                       │
│                │◀──────────────────│                      │
│                │ Show success     │                       │
│ Redirect login │                  │                       │
│◀───────────────│                  │                       │

## 5.4 Refresh Token Rotation (HR-19)
Client                   Backend                       Database
│                         │                              │
│ Access token expired   │                              │
│ POST /api/auth/refresh │                              │
│ { refreshToken: x123 } │                              │
│────────────────────────▶│                             │
│                         │                              │
│                         │ Hash SHA-256(x123)           │
│                         │ Find refresh_token row       │
│                         │──────────────────────────────▶│
│                         │◀──────────────────────────────│
│                         │                              │
│                         │ IF revoked_at IS NOT NULL:   │
│                         │   PANIC! token replay attack │
│                         │   Revoke ALL refresh tokens  │
│                         │   of this user_id            │
│                         │   Return 401                 │
│                         │   (kick out all devices)     │
│                         │                              │
│                         │ IF expires_at < NOW:         │
│                         │   return 401 EXPIRED         │
│                         │                              │
│                         │ Mark x123 revoked            │
│                         │   revoked_at = NOW()         │
│                         │ Create new token y456        │
│                         │   rotated_from_id = x123.id  │
│                         │   token_hash = SHA256(y456)  │
│                         │   expires_at = NOW + 7d      │
│                         │ UPDATE x123:                 │
│                         │   rotated_to_id = y456.id    │
│                         │──────────────────────────────▶│
│                         │                              │
│                         │ Generate new access token    │
│                         │                              │
│ 200 OK                  │                              │
│ + newAccess + y456 plain│                              │
│◀────────────────────────│                              │
│ Replace y456 trong      │                              │
│ localStorage            │                              │

### Defense in depth
- **Short access token** (15 min) → leak có damage limit
- **Rotate refresh token** → token cũ chỉ valid 1 lần
- **Reuse detection** → nếu attacker grab token, victim sẽ trigger panic khi refresh
- **SHA-256 storage** → DB leak không lộ plaintext token

---# PART 6 — COMMON BUGS & SOLUTIONS

8 bugs đã gặp trong Sprint 1. AI assistant nên CHECK trước khi đề xuất code mới.

## Bug 1 — Hibernate Schema Validation: Double vs NUMERIC

### Symptom
Schema-validation: wrong column type encountered in column [average_rating]
in table [driver_profile]; found [numeric (Types#NUMERIC)],
but expecting [float(53) (Types#FLOAT)]

App fail to start. Entity manager không init được.

### Root cause
- DB column: `NUMERIC(3,2)` (decimal precision)
- Java field: `Double averageRating` (IEEE 754 float64)
- Hibernate `ddl-auto=validate` detect mismatch

### Why it matters
- AC-08: tiền và rating phải `BigDecimal`
- `Double` mất precision với số thập phân (vd: 0.1 + 0.2 != 0.3)
- DB NUMERIC giữ precision exact

### Fix
Đổi entity field từ `Double` sang `BigDecimal`:

```java
// SAI
@Column(name = "average_rating", precision = 3, scale = 2)
private Double averageRating;

// ĐÚNG
@Column(name = "average_rating", precision = 3, scale = 2)
@Builder.Default
private BigDecimal averageRating = new BigDecimal("0.00");
```

### Files affected (đã fix)
- `DriverProfile.java`: averageRating, depositAmount, totalRevenue
- `Order.java`: totalQuote, commissionRateSnapshot, distanceKm

### Prevention
Khi tạo entity mới với decimal field, **MẶC ĐỊNH dùng BigDecimal**. KHÔNG dùng Double/Float.

---

## Bug 2 — Wildcard Import Conflict

### Symptom
java: reference to Table is ambiguous
both class org.hibernate.annotations.Table in org.hibernate.annotations
and class jakarta.persistence.Table in jakarta.persistence match

Build fail.

### Root cause
```java
import jakarta.persistence.*;           // có @Table, @Index, @Entity
import org.hibernate.annotations.*;     // CŨNG có @Table, @Index (deprecated)
```

Compiler không biết dùng `@Table` của cái nào.

### Fix
Bỏ wildcard từ `org.hibernate.annotations.*`. Thay bằng explicit imports:

```java
// SAI
import org.hibernate.annotations.*;

// ĐÚNG
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
```

### Files affected (đã fix)
- `Order.java`
- `DriverProfile.java`
- `Transaction.java`

### Prevention
- **NEVER wildcard import `org.hibernate.annotations.*`**
- IntelliJ Settings → Editor → Code Style → Java → uncheck "Use single class import"
- Khi paste code có wildcard → Alt+Enter → "Expand wildcard import"

---

## Bug 3 — Missing CreationTimestamp Import (sau khi fix Bug 2)

### Symptom
java: cannot find symbol class CreationTimestamp

### Root cause
Khi sửa Bug 2, bỏ wildcard import nhưng **quên thêm explicit imports cho CreationTimestamp + UpdateTimestamp**.

### Fix
Thêm imports:
```java
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
```

Hoặc IntelliJ: click annotation đỏ → Alt+Enter → "Import class" → chọn `org.hibernate.annotations`.

### Prevention
Sau khi fix wildcard, **scan toàn bộ file** check còn annotation nào missing.

---

## Bug 4 — PostgreSQL Type Cast Error

### Symptom (khi V99 seed run)
ERROR: type "transaction_type" does not exist
Position: 7762
Line: 575

Flyway rollback. App fail.

### Root cause
Claude Code generated SQL có cast:
```sql
INSERT INTO transaction (..., type, ...) 
VALUES (..., 'ORDER_PAYMENT'::transaction_type, ...);
```

Nhưng bảng `transaction` của chúng ta dùng:
```sql
type VARCHAR(30) NOT NULL CHECK (type IN ('ORDER_PAYMENT', 'DRIVER_EARNING', ...))
```

**KHÔNG có PostgreSQL ENUM type `transaction_type`** → cast fail.

### Fix
Bỏ tất cả `::type_name` casts trong V99:

```sql
-- SAI
INSERT INTO transaction (type, ...) VALUES ('ORDER_PAYMENT'::transaction_type, ...);

-- ĐÚNG
INSERT INTO transaction (type, ...) VALUES ('ORDER_PAYMENT', ...);
```

VARCHAR tự match CHECK constraint, không cần cast.

### Cascading fix
Cũng check các cast khác có thể có:
- `::order_status`
- `::user_role`
- `::user_status`
- `CAST('VALUE' AS xxx_type)`

### Prevention
- **Constitution lock VARCHAR + CHECK**, KHÔNG dùng PostgreSQL ENUM
- Prompt cho Claude Code: "table uses VARCHAR + CHECK, no ENUM cast"

---

## Bug 5 — BCrypt Hash Hardcoded không khớp Password

### Symptom
Login `admin@movehome.vn / Admin@2026` → 401 INVALID_CREDENTIALS dù password type đúng.

### Root cause
V99 seed dùng hash placeholder:
```sql
password_hash = '$2a$12$LQv3c1yqBwEHxV5JG.JxgOewqJxOQF1eXrLh4WkS5xH4UcHFV1Bgi'
```

Hash này KHÔNG phải BCrypt thật của "Admin@2026" (chỉ là string giả).

→ `BCrypt.matches("Admin@2026", stored_hash)` = false → 401.

### Fix (1 lần)
1. Vào https://bcrypt-generator.com
2. Password: `Admin@2026`, Rounds: `12`
3. Generate → copy hash mới (vd: `$2a$12$abc...xyz`)
4. UPDATE Neon DB:
```sql
UPDATE app_user 
SET password_hash = '$2a$12$<HASH_THAT>'
WHERE role IN ('ADMIN', 'MANAGER', 'DRIVER') 
   OR email LIKE 'customer%@test.com';
```

### Prevention
- KHÔNG hardcode BCrypt hash trong V99 seed
- Thay vào đó: tạo migration script Java/Python generate hash khi seed
- Hoặc dùng password reset endpoint cho admin sau seed

### Long-term solution (Sprint 6)
Tạo `SeedDataService` runtime với password thực:
```java
@Component
public class SeedDataRunner implements ApplicationRunner {
    public void run(...) {
        if (userRepo.count() < 5) {
            String hash = passwordEncoder.encode("Admin@2026");
            // create users with hash
        }
    }
}
```

---

## Bug 6 — IntelliJ Working Directory Wrong (Teammate setup)

### Symptom
Teammate clone repo, tạo `.env` xong vẫn báo:
Could not resolve placeholder 'SMTP_HOST' in value "${SMTP_HOST}"

### Root cause
**spring-dotenv** library đọc `.env` từ **working directory** khi Spring start.

Trên máy teammate, IntelliJ chạy app từ:
C:\Users\ADMIN\Downloads\move-home-main\move-home-main
↑ working dir = root project

Nhưng `.env` ở:
C:\Users\ADMIN\Downloads\move-home-main\move-home-main\backend.env
↑ subfolder backend

→ spring-dotenv KHÔNG tìm thấy `.env`.

### Fix
IntelliJ → Edit Configurations → BackendApplication → field **Working directory**:
SAI: C:\Users\ADMIN\Downloads\move-home-main\move-home-main
ĐÚNG: C:\Users\ADMIN\Downloads\move-home-main\move-home-main\backend

Apply + Run lại.

### Prevention
- Document trong ENVIRONMENT_SETUP.md
- Cảnh báo trong onboarding checklist
- Alternative: dùng absolute path trong `application.properties`:
```properties
spring.config.import=optional:file:./backend/.env[.properties]
```

---

## Bug 7 — Neon Auto-Suspend Connection Timeout

### Symptom
Spring Boot fail to start với:
Caused by: java.net.SocketTimeoutException: Connect timed out
at PostgreSQL connection to ep-...-pooler...neon.tech

### Root cause
Neon free tier **auto-suspend sau 5 phút idle**. Lần connect đầu tiên sau idle mất 10-30s để wake up. Hikari pool default timeout 30s, đôi khi không đủ.

### Fix (one-time)
- **Đợi 30 giây** + restart app
- Hoặc vào https://console.neon.tech → SQL Editor → chạy `SELECT NOW();` để wake up Neon

### Fix (long-term, nếu muốn không bao giờ gặp lại)
Update `application.properties`:
```properties
spring.datasource.hikari.connection-timeout=60000  # 60s
spring.datasource.hikari.initialization-fail-timeout=120000  # 120s
```

### Prevention
- Document trong PROJECT_KNOWLEDGE.md
- Khi demo cho thầy: vào Neon console wake up trước khi start app
- Long-term: upgrade Neon Pro ($19/month, no auto-suspend)

---

## Bug 8 — JDK Version Mismatch (Teammate setup)

### Symptom
Teammate dùng JDK 26 (latest cutting edge), project expect JDK 17. Một số deprecated API có thể remove, runtime warning.

Log line:
Starting BackendApplication using Java 26.0.1

### Root cause
Teammate cài JDK 26 (mới nhất release), IntelliJ auto-pick.

### Fix
1. IntelliJ → Ctrl+Alt+Shift+S → Project Structure
2. SDKs → "+" → Download JDK → Version 17, Vendor Eclipse Temurin
3. Project → SDK = JDK 17, Language level = 17
4. Modules → backend → Sources level 17 + Dependencies SDK JDK 17
5. Run Configurations → JRE = JDK 17
6. Apply + Run lại

### Prevention
- ENVIRONMENT_SETUP.md document version JDK 17 mandatory
- pom.xml lock Java version:
```xml
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

---

# PART 7 — SPRINT 2-6 ROADMAP DETAIL

## Sprint 2 — Customer Booking (Tuần 2)

### Goal
Customer đặt đơn online với báo giá tự động dựa trên distance + surcharges.

### Spec
File: `specs/002-customer-booking/spec.md` (~800-1000 dòng)

### User Stories
| ID | Story | Acceptance |
|----|-------|-----------|
| US-201 | Customer chọn pickup + dropoff district | Form dropdown 12 quận Hà Nội |
| US-202 | Hệ thống tính báo giá tự động | API trả price < 2s |
| US-203 | Customer xem báo giá chi tiết breakdown | UI hiện: base, peak, alley, floor, porter |
| US-204 | Customer confirm + submit đơn | Status PENDING, có order_code |
| US-205 | Customer xem lịch sử đơn + status | Page my-orders.html |
| US-206 | Customer hủy đơn (chỉ PENDING) | Status CANCELLED, refund deposit |

### Backend Tasks
| Task | Estimate | Files |
|------|----------|-------|
| Spec #002 viết | 4h | specs/002-customer-booking/spec.md |
| Migration V7: `pricing_config` table | 2h | V7__create_pricing_config.sql |
| Entity: PricingConfig, OrderItem | 3h | entity/*.java |
| Service: OrderPricingService | 6h | service/OrderPricingService.java |
| Service: OrderBookingService | 4h | service/OrderBookingService.java |
| 5 endpoints | 3h | controller/CustomerOrderController.java |
| Unit tests | 4h | test/**/* |

**Total backend: ~26h** (1 dev × 4 ngày)

### Frontend Tasks
| Task | Estimate | Files |
|------|----------|-------|
| pages/customer/booking-form.html | 4h | |
| pages/customer/order-detail.html | 3h | |
| pages/customer/my-orders.html | 2h | |
| js/customer-booking.js | 4h | |
| js/customer-orders.js | 2h | |
| Migrate brand sang Grab | 8h | css/styles-v2.css |

**Total frontend: ~23h** (1 dev × 3 ngày)

### Pricing Formula (CONTEXT.md §2)
total_quote = base_price
+ peak_hour_surcharge
+ alley_surcharge
+ floor_surcharge
+ porter_fee
base_price = distance_km × price_per_km
peak_hour_surcharge:

IF scheduled_at hours in [7-9] OR [17-19] (Asia/Ho_Chi_Minh):
base_price × peak_rate (default 0.30)
ELSE: 0

alley_surcharge:

IF customer note có "ngo nho": +200,000 VND
ELSE: 0

floor_surcharge:

IF floor > 3 AND no elevator: floor × 50,000 VND
ELSE: 0

porter_fee:

IF customer chọn porter: 300,000 VND/porter


### External Integration
- **OSRM API**: `http://router.project-osrm.org/route/v1/driving/{lng1,lat1};{lng2,lat2}?overview=false`
  - Return: distance (meters), duration (seconds)
  - Fallback: bảng quận-quận hardcoded 12×12 matrix

### Risks & Mitigation
| Risk | Probability | Mitigation |
|------|-------------|-----------|
| OSRM API rate limit | Medium | Cache 24h + fallback table |
| Customer dùng pickup ngoài Hà Nội | Low | Validate district whitelist |
| Pricing logic phức tạp khó test | High | Unit test extensive với 20+ test cases |

---

## Sprint 3 — Driver Workflow (Tuần 3)

### Goal
Driver xem đơn PENDING phù hợp, nhận đơn, cập nhật trạng thái.

### User Stories
- US-301: Driver login → thấy danh sách đơn PENDING phù hợp vehicle_type
- US-302: Driver accept đơn → status ACCEPTED, customer được notify
- US-303: Driver "Bắt đầu chuyến" → IN_PROGRESS, set started_at
- US-304: Driver "Hoàn thành" → COMPLETED, set completed_at, trigger escrow 2h
- US-305: Customer rate driver 1-5 sao sau COMPLETED
- US-306: Auto job: sau 2h COMPLETED, release DRIVER_EARNING transaction

### Backend Tasks
- Service: OrderAssignmentService với concurrency lock (avoid 2 driver cùng accept)
- 5 endpoints PATCH order status transitions
- Scheduled job @Scheduled(cron) cho escrow release (mỗi 5 phút check)
- Rating calculation update driver_profile.average_rating

### Frontend Tasks
- pages/driver/available-orders.html
- pages/driver/in-progress.html
- pages/driver/history.html

### Critical implementation detail
**Concurrency lock cho accept order:**
```java
@Transactional
public void acceptOrder(UUID orderId, UUID driverId) {
    // Use pessimistic lock to avoid double-accept
    Order order = orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", ...));
    
    if (order.getStatus() != OrderStatus.PENDING) {
        throw new BusinessException("ORDER_ALREADY_TAKEN", ...);
    }
    
    order.setDriverId(driverId);
    order.setStatus(OrderStatus.ACCEPTED);
    order.setAcceptedAt(Instant.now());
}
```

```java
// OrderRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM ServiceOrder o WHERE o.id = :id")
Optional<Order> findByIdForUpdate(@Param("id") UUID id);
```

---

## Sprint 4 — Payment + Tracking (Tuần 4)

### Goal
Tích hợp thanh toán + tracking real-time.

### User Stories
- US-401: Customer top-up deposit qua VNPay sandbox
- US-402: Customer xem driver location real-time trên map
- US-403: Customer pay khi order COMPLETED
- US-404: Auto refund nếu order CANCELLED

### VNPay Sandbox Integration
- Endpoint config: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
- Flow: Customer click pay → redirect VNPay → return URL → IPN callback → update transaction

### Map Integration
- Leaflet.js + OpenStreetMap (free, no API key)
- Driver app gửi location mỗi 10s (POST endpoint)
- Customer poll location mỗi 5s (hoặc WebSocket nếu setup)

---

## Sprint 5 — Manager + Withdrawals (Tuần 5)

### Goal
Manager duyệt driver, xử lý rút tiền.

### User Stories
- US-501: Manager xem danh sách driver PENDING_APPROVAL
- US-502: Manager duyệt → user.status = ACTIVE + driver_profile.approved_at + approved_by_manager_id
- US-503: Manager từ chối với lý do → status = REJECTED + rejection_reason
- US-504: Driver request withdrawal (available balance = total_earnings - withdrawn)
- US-505: Manager duyệt/từ chối withdrawal

### Backend Tasks
- Migration V8: `withdrawal_request` table
- 6 endpoints manager
- Service: DriverApprovalService, WithdrawalService
- Email notification khi approved/rejected (vẫn mock)

### Frontend
- pages/manager/driver-approvals.html (đổi từ placeholder)
- pages/manager/withdrawals.html
- pages/admin/withdrawals.html → real data

---

## Sprint 6 — Polish + Public Landing (Tuần 6)

### Goal
Hoàn thiện UX, public homepage, fix bugs, final demo prep.

### Tasks
- Public homepage (`index.html` redesign theo Grab brand)
  - Hero section
  - Features (3 columns)
  - How it works
  - Testimonials (fake)
  - Footer
- Email service thật:
  - Option A: Gmail SMTP với app password
  - Option B: SendGrid free tier (100 emails/day)
- Bug fixes từ Sprint 1-5
- Mobile responsive admin pages
- Documentation:
  - USER_GUIDE.md
  - API_REFERENCE.md
  - DEPLOYMENT_GUIDE.md
- Final demo prep

---

# PART 8 — GLOSSARY

## Vietnamese terms

| Term | English | Definition |
|------|---------|-----------|
| Đơn hàng | Order | Service request từ Customer cho 1 chuyến chuyển nhà |
| Tài xế | Driver | Người vận chuyển + xe tải |
| Khách hàng | Customer | Người đặt dịch vụ chuyển nhà |
| Quản lý | Manager | Nhân sự duyệt driver + handle disputes |
| Cọc | Deposit | Tiền driver phải pay trước khi activate (3M VND) |
| Hoa hồng | Commission | Phí Move_home thu (30% mỗi order) |
| Phụ phí giờ cao điểm | Peak hour surcharge | 30% nếu 7-9h hoặc 17-19h |
| Phụ phí ngõ nhỏ | Alley surcharge | 200K VND nếu pickup/dropoff ngõ nhỏ |
| Phụ phí tầng | Floor surcharge | 50K/tầng nếu > 3 tầng không thang máy |
| Phí bốc xếp | Porter fee | 300K/porter nếu customer chọn |
| Quận | District | Đơn vị hành chính cấp quận Hà Nội (12 quận nội thành) |

## Technical terms

| Term | Definition |
|------|-----------|
| **JWT** | JSON Web Token — token format cho auth, gồm header.payload.signature |
| **HS256** | HMAC-SHA256 — symmetric algorithm cho JWT signing |
| **BCrypt** | Password hashing function với salt + iterations |
| **Cost factor** | BCrypt parameter (10-14) — cao = chậm = an toàn hơn |
| **JPA** | Java Persistence API — standard ORM Java |
| **Hibernate** | JPA implementation phổ biến nhất |
| **Flyway** | Migration tool — versioned SQL scripts |
| **Soft delete** | Mark `deleted_at` thay vì DELETE row |
| **Snapshot pattern** | Lưu giá trị tại thời điểm tạo, không thay đổi sau |
| **RBAC** | Role-Based Access Control |
| **OSRM** | Open Source Routing Machine — distance API |
| **VNPay** | Cổng thanh toán Vietnam |

---

# PART 9 — ENVIRONMENT & DEPLOY

## Local development
- Backend: IntelliJ Run → http://localhost:8080
- Frontend: VS Code Live Server → http://127.0.0.1:5500
- Database: Neon Cloud Singapore (shared)
- AI: Claude Code CLI (leader only)

## Production deploy (planned Sprint 6)
- Backend: AWS EC2 t3.small ($16/month) hoặc Hetzner ($5/month)
- Database: Neon Pro ($19/month) — no auto-suspend
- Frontend: Vercel (free) hoặc Netlify (free)
- Domain: $10/year (.com)
- SSL: Let's Encrypt free
- Email: Gmail SMTP free hoặc SendGrid free tier

**Estimated cost year 1: ~$300-500**

---

# PART 10 — END OF FILE

> **Tổng kết:** File này (~3000 dòng) chứa toàn bộ knowledge cần thiết cho AI assistant onboarding dự án Move_home.
>
> **Workflow recommend:**
> 1. Upload file này + 6 file gốc (CLAUDE.md, DESIGN.md, constitution.md, spec-001, spec-028, CONTEXT.md) vào **Claude Project Knowledge**
> 2. Paste **SYSTEM_PROMPT.md** vào **Custom Instructions**
> 3. Start new chat trong project → AI tự có context, không cần paste lại
>
> **Khi có decision mới quan trọng** (Sprint 2 spec, brand change, tech change): update file này.

---

**Generated by:** Claude (Anthropic) — chat session 30/05 → 02/06/2026
**Owner:** TriNM2505 (Nguyễn Mạnh Trí, FPT University SWP leader)
**Version:** 1.0 (Sprint 1 complete)