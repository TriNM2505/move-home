# Feature Specification: Admin Dashboard (Demo Spec)

**Feature Branch:** `028-admin-dashboard`
**Feature Number:** #28 of 30 — SHELL (priority cho demo Thu Ba 2026-06-02)
**Created:** 2026-05-30
**Version:** 1.0.0
**Status:** Draft (demo scope only)
**Sprint Target:** Thu Ba 2026-06-02

**Spec type:** MINI — chi cover phan can thiet cho demo. Full spec se viet sau Thu Ba.

**Reference:**
- CONTEXT.md v2.0 §7 Feature #28
- Constitution v1.2.0: HR-10 (RBAC), HR-13 (audit), AC-07 (timezone), AC-08 (BigDecimal)
- Spec #001 Auth (Admin role login flow — FR-001..FR-003)
- design.md v1.0: §5.3 KPI Box, §5.2 Table, §6 Header+Sidebar, §8 Charts

---

## 1. Goals & Scope

**Goal:** Admin co 1 trang duy nhat hien thi tinh hinh kinh doanh + operation tong quan, du de
Admin quyet dinh hanh dong buoi sang ma khong can boi trong nhieu trang.

**In scope (lam cho demo Thu Ba):**
1. Trang `/admin/dashboard` — chi Admin role truy cap (HR-10)
2. 6 KPI Cards (2 hang x 3): tong don, doanh thu commission, ty le hoan thanh; Driver ACTIVE,
   Driver cho duyet, Don tranh chap
3. 2 Charts (Chart.js): bar chart so don theo ngay (30 ngay), line chart doanh thu theo thang (12 thang)
4. 2 Tables: Driver PENDING_APPROVAL (top 10) + Order gan nhat (top 10)
5. SQL seed data de chart + table co data dep khi demo
6. Auth guard: redirect 403 / redirect login neu khong phai Admin

**Out of scope — defer phase 2 (xem muc 9):**
- Realtime WebSocket, date range filter, export, drill-down chart, pagination, cache, notification badge

---

## 2. User Stories

**US1:** As an Admin, I can log in and immediately see this month's business metrics (orders,
commission revenue, completion rate) so I can assess platform health at a glance.

**US2:** As an Admin, I can see a 30-day order trend bar chart so I can recognize growth or
decline patterns without running a custom report.

**US3:** As an Admin, I can see the top-10 Drivers waiting for approval directly on the dashboard
so I can act on them with one click without navigating elsewhere.

**US4:** As an Admin, I can see active disputes highlighted in a danger KPI card so I never miss
a dispute that needs my attention.

---

## 3. Functional Requirements

### Nhom 1 — RBAC + Page Access (FR-001..FR-003)

**FR-001**
WHEN a user navigates to `/admin/dashboard`, THE system SHALL verify the JWT token and check
`role = ADMIN`. WHERE role is not ADMIN → return HTTP 403 and redirect FE to the user's own home
page based on their role (Customer → `/`, Driver → `/driver/dashboard`, Manager → `/manager/dashboard`).

**FR-002**
WHERE the JWT token is missing, expired, or invalid when accessing `/admin/dashboard`,
THE system SHALL redirect the user to `/login?redirect_to=/admin/dashboard` so they return
automatically after a successful login.

**FR-003**
WHEN an Admin successfully accesses the dashboard page, THE system SHALL insert an audit log row
(HR-13): `{ event_type: "DASHBOARD_VIEW", actor_id: <admin_user_id>, actor_role: "ADMIN", ip_address, timestamp: NOW() UTC }`.

---

### Nhom 2 — KPI Aggregation (FR-004..FR-009)

**FR-004**
WHEN Admin calls `GET /api/admin/dashboard/kpi`, THE system SHALL return all 6 KPIs in a single
JSON response to minimize round trips (1 DB query per KPI or 1 combined query):

```json
{
  "month_total_orders": 152,
  "month_commission_revenue": 12500000,
  "completion_rate_percent": 87.5,
  "active_driver_count": 23,
  "pending_approval_count": 5,
  "in_dispute_count": 2,
  "calculated_at": "2026-05-30T07:00:00Z"
}
```

`money` fields (AC-08): serialized as integer (VND nguyen dong, scale=0). `calculated_at` = server
time UTC ISO-8601 (AC-07).

**FR-005**
WHEN computing `month_total_orders`, THE system SHALL count:
```sql
SELECT COUNT(*) FROM "order"
WHERE DATE_TRUNC('month', created_at AT TIME ZONE 'UTC') =
      DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
  AND deleted_at IS NULL;
```

**FR-006**
WHEN computing `month_commission_revenue`, THE system SHALL sum:
```sql
SELECT COALESCE(SUM(total_quote * commission_rate_snapshot), 0)
FROM "order"
WHERE status = 'COMPLETED'
  AND DATE_TRUNC('month', completed_at AT TIME ZONE 'UTC') =
      DATE_TRUNC('month', CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
  AND deleted_at IS NULL;
```
Use `BigDecimal` scale=0 (AC-08). Return `0` when no completed orders in month.

**FR-007**
WHEN computing `completion_rate_percent`, THE system SHALL calculate:
`ROUND((completed_count * 100.0 / NULLIF(total_count, 0)), 1)` for current month.
WHERE `total_count = 0` → return `0.0` (khong chia cho 0).

**FR-008**
WHEN computing `active_driver_count`, THE system SHALL count:
```sql
SELECT COUNT(*) FROM "user"
WHERE role = 'DRIVER' AND status = 'ACTIVE' AND deleted_at IS NULL;
```

**FR-009**
WHEN computing `pending_approval_count` and `in_dispute_count`, THE system SHALL count:
- `pending_approval_count`: `COUNT(*) FROM "user" WHERE role='DRIVER' AND status='PENDING_APPROVAL' AND deleted_at IS NULL`
- `in_dispute_count`: `COUNT(*) FROM "order" WHERE status='IN_DISPUTE' AND deleted_at IS NULL`

---

### Nhom 3 — Chart Data (FR-010..FR-012)

**FR-010**
WHEN Admin calls `GET /api/admin/dashboard/chart/orders-by-day?days=30`, THE system SHALL return
an array of exactly `days` entries, one per calendar day (Asia/Ho_Chi_Minh date), ordered ascending:

```json
[
  { "date": "2026-05-01", "count": 5 },
  { "date": "2026-05-02", "count": 8 },
  ...
]
```

Query strategy: generate date series (TODAY - 29 days → TODAY), LEFT JOIN with order counts.

**FR-011**
WHEN Admin calls `GET /api/admin/dashboard/chart/revenue-by-month?months=12`, THE system SHALL
return an array of exactly `months` entries, one per calendar month (yyyy-MM), ordered ascending:

```json
[
  { "month": "2025-06", "revenue": 15000000 },
  { "month": "2025-07", "revenue": 22000000 },
  ...
]
```

Revenue = `SUM(total_quote * commission_rate_snapshot)` for COMPLETED orders in that month.

**FR-012**
WHERE a date or month in FR-010/FR-011 has zero matching orders, THE system SHALL still include
that entry with `count: 0` or `revenue: 0` — KHONG duoc bo qua entry (neu bo qua: Chart.js se
ve sai truc x, chart co khoang trong).

---

### Nhom 4 — Table Data (FR-013..FR-015)

**FR-013**
WHEN Admin calls `GET /api/admin/dashboard/pending-drivers?limit=10`, THE system SHALL return the
10 oldest PENDING_APPROVAL Drivers (FIFO — order by `driver_deposit_payment.paid_at ASC`):

```json
[
  {
    "user_id": "uuid",
    "full_name": "Nguyen Van A",
    "email": "vana@example.com",
    "phone": "+84901234567",
    "submitted_at": "2026-05-29T08:30:00Z",
    "operating_districts": ["Ba Dinh", "Hoan Kiem"]
  }
]
```

**FR-014**
WHEN Admin calls `GET /api/admin/dashboard/recent-orders?limit=10`, THE system SHALL return the
10 most recent orders by `created_at DESC` (bat ky status nao):

```json
[
  {
    "order_id": "uuid",
    "customer_name": "Tran Thi B",
    "driver_name": "Le Van C",
    "total_quote": 850000,
    "status": "COMPLETED",
    "created_at": "2026-05-30T06:15:00Z"
  }
]
```

**FR-015**
WHERE an order has no Driver assigned (`status IN (PENDING_PAYMENT, CONFIRMED, CANCELLED)` before
assignment), THE system SHALL set `driver_name: null` in the response. FE renders null as em-dash
`—`.

---

### Nhom 5 — Frontend Rendering (FR-016..FR-018)

**FR-016**
WHEN the dashboard page loads, THE frontend SHALL use `Promise.all` to fetch all 5 API endpoints
in parallel (not sequentially), then render all sections. Skeleton loaders (design.md §7.4) MUST
display while data is loading.

**FR-017**
THE frontend SHALL render 6 KPI cards using `.kpi` + variant classes from design.md §5.3:

| KPI | Variant | Icon |
|-----|---------|------|
| Tong don thang nay | `.kpi-primary` | 📦 |
| Doanh thu commission | `.kpi-success` | 💰 |
| Ty le hoan thanh | `.kpi-info` | ✅ |
| Driver ACTIVE | `.kpi-success` | 🚗 |
| Driver cho duyet | `.kpi-warning` | ⏳ |
| Don tranh chap | `.kpi-danger` | ⚠️ |

**FR-018**
THE frontend SHALL render 2 Chart.js charts per design.md §8 (using CSS token colors):
- `orders-by-day-chart`: `type: 'bar'`, x-axis labels format `DD/MM`, y-axis integers only
- `revenue-by-month-chart`: `type: 'line'`, y-axis format abbreviate millions (vd `12.5M`), fill
  area enabled

---

## 4. Data Aggregation Strategy

**Nguon du lieu:** READ-ONLY queries tren cac bang hien co (`"order"`, `"user"`,
`wallet_transaction`). KHONG can them bang moi cho MVP nay.

**Performance targets:**

| Endpoint | Target p95 |
|----------|-----------|
| `/kpi` | < 200ms |
| `/chart/orders-by-day` | < 300ms |
| `/chart/revenue-by-month` | < 300ms |
| `/pending-drivers` | < 100ms |
| `/recent-orders` | < 100ms |
| Tong page load (DOMContentLoaded → rendered) | < 1.5s |

**Index can co** (tao trong Flyway migration neu chua co — AC-12):
```sql
CREATE INDEX IF NOT EXISTS idx_order_created_at  ON "order" (created_at);
CREATE INDEX IF NOT EXISTS idx_order_status_completed ON "order" (status, completed_at)
  WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_user_role_status   ON "user" (role, status)
  WHERE deleted_at IS NULL;
```

---

## 5. API Endpoints Summary

| Endpoint | Method | Auth | FR |
|----------|--------|------|----|
| `GET /api/admin/dashboard/kpi` | GET | Admin JWT | FR-004..FR-009 |
| `GET /api/admin/dashboard/chart/orders-by-day` | GET | Admin JWT | FR-010, FR-012 |
| `GET /api/admin/dashboard/chart/revenue-by-month` | GET | Admin JWT | FR-011, FR-012 |
| `GET /api/admin/dashboard/pending-drivers` | GET | Admin JWT | FR-013 |
| `GET /api/admin/dashboard/recent-orders` | GET | Admin JWT | FR-014, FR-015 |

Tat ca endpoint PHAI duoc bao ve bang `@PreAuthorize("hasRole('ADMIN')")` (Spring Security).
Non-Admin → 403 (HR-10).

---

## 6. Frontend Implementation

### HTML Structure

```html
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <title>Bang dieu khien — Move_home Admin</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="/css/tokens.css">
  <link rel="stylesheet" href="/css/layout.css">
  <link rel="stylesheet" href="/css/nav.css">
  <link rel="stylesheet" href="/css/data-display.css">
  <link rel="stylesheet" href="/css/feedback.css">
  <style>
    body { margin: 0; font-family: var(--font-family-base); background: var(--color-bg-page); }
    .dashboard-body { display: flex; }
    .dashboard-main { flex: 1; padding: var(--space-8); overflow-y: auto; }
    .chart-canvas-wrapper { height: 260px; position: relative; }
  </style>
</head>
<body>

<div class="toast-container" id="toast-container" aria-live="polite"></div>

<!-- Header (design.md §6.1) -->
<header class="site-header">
  <div class="container site-header-inner">
    <a href="/" class="site-logo"><span class="site-logo-icon">🏠</span><span class="site-logo-text">Move_home</span></a>
    <div class="site-header-actions">
      <button class="user-menu-trigger" id="user-menu-trigger" aria-haspopup="true" aria-expanded="false">
        <div class="avatar avatar-sm avatar-initials" id="user-avatar">AD</div>
        <span class="user-menu-name" id="user-name">Admin</span>
        <span class="user-menu-chevron">▾</span>
      </button>
    </div>
  </div>
</header>

<div class="dashboard-body">

  <!-- Sidebar (design.md §6.5) -->
  <aside class="sidebar">
    <nav class="sidebar-nav">
      <a href="/admin/dashboard" class="sidebar-item sidebar-item--active">
        <span class="sidebar-icon">📊</span><span class="sidebar-label">Tong quan</span>
      </a>
      <a href="/admin/orders" class="sidebar-item">
        <span class="sidebar-icon">📦</span><span class="sidebar-label">Don hang</span>
      </a>
      <a href="/admin/drivers" class="sidebar-item">
        <span class="sidebar-icon">🚗</span><span class="sidebar-label">Tai xe</span>
      </a>
      <a href="/admin/withdrawals" class="sidebar-item">
        <span class="sidebar-icon">💸</span><span class="sidebar-label">Rut tien</span>
      </a>
      <div class="sidebar-divider"></div>
      <a href="/admin/settings" class="sidebar-item">
        <span class="sidebar-icon">⚙️</span><span class="sidebar-label">Cau hinh</span>
      </a>
    </nav>
  </aside>

  <!-- Main content -->
  <main class="dashboard-main" id="main-content">
    <h1 class="page-title">Bang dieu khien</h1>

    <!-- Row 1: Business KPIs -->
    <div class="grid-3" id="kpi-row-1">
      <!-- Skeleton while loading -->
      <div class="kpi kpi-primary"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
      <div class="kpi kpi-success"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
      <div class="kpi kpi-info"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
    </div>

    <!-- Row 2: Operational KPIs -->
    <div class="grid-3" style="margin-top:var(--space-5);" id="kpi-row-2">
      <div class="kpi kpi-success"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
      <div class="kpi kpi-warning"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
      <div class="kpi kpi-danger"><div class="skeleton skeleton-line skeleton-line--full" style="height:80px;border-radius:var(--radius-md)"></div></div>
    </div>

    <!-- Charts -->
    <div class="grid-2" style="margin-top:var(--space-8);">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">So don 30 ngay gan nhat</h3>
          <span class="card-meta" id="orders-chart-label">Dang tai...</span>
        </div>
        <div class="card-body">
          <div class="chart-canvas-wrapper">
            <canvas id="orders-by-day-chart"></canvas>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-header">
          <h3 class="card-title">Doanh thu commission 12 thang</h3>
          <span class="card-meta" id="revenue-chart-label">Dang tai...</span>
        </div>
        <div class="card-body">
          <div class="chart-canvas-wrapper">
            <canvas id="revenue-by-month-chart"></canvas>
          </div>
        </div>
      </div>
    </div>

    <!-- Pending Drivers Table -->
    <div class="card" style="margin-top:var(--space-8);">
      <div class="card-header">
        <h3 class="card-title">Driver cho duyet</h3>
        <a href="/admin/drivers?status=PENDING_APPROVAL" class="btn btn-secondary btn-sm">Xem tat ca</a>
      </div>
      <div class="card-body" style="padding:0;">
        <div class="table-wrapper" style="border:none;border-radius:0;">
          <table class="table table-hover" id="pending-drivers-table">
            <thead>
              <tr>
                <th>Ho ten</th>
                <th>Email</th>
                <th>Quan hoat dong</th>
                <th>Ngay nop</th>
                <th class="table-col-action">Hanh dong</th>
              </tr>
            </thead>
            <tbody id="pending-drivers-body">
              <tr><td colspan="5" class="text-center" style="padding:var(--space-8);color:var(--color-text-tertiary);">Dang tai...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Recent Orders Table -->
    <div class="card" style="margin-top:var(--space-6);">
      <div class="card-header">
        <h3 class="card-title">Don hang gan nhat</h3>
        <a href="/admin/orders" class="btn btn-secondary btn-sm">Xem tat ca</a>
      </div>
      <div class="card-body" style="padding:0;">
        <div class="table-wrapper" style="border:none;border-radius:0;">
          <table class="table table-hover" id="recent-orders-table">
            <thead>
              <tr>
                <th>Ma don</th>
                <th>Khach hang</th>
                <th>Tai xe</th>
                <th>Bao gia</th>
                <th>Trang thai</th>
                <th>Ngay tao</th>
              </tr>
            </thead>
            <tbody id="recent-orders-body">
              <tr><td colspan="6" class="text-center" style="padding:var(--space-8);color:var(--color-text-tertiary);">Dang tai...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

  </main>
</div><!-- end dashboard-body -->

<script defer src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
<script defer src="/js/dashboard.js" type="module"></script>
</body>
</html>
```

---

### JS Data Loading + Rendering (`/js/dashboard.js`)

```javascript
// dashboard.js
const token = localStorage.getItem('move_home_access_token');
const AUTH  = { headers: { 'Authorization': `Bearer ${token}` } };

// Format helpers
const fmtMoney = (n) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(n);

const fmtDate = (iso) =>
  new Date(iso).toLocaleDateString('vi-VN',
    { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      timeZone: 'Asia/Ho_Chi_Minh' });

const fmtShortDate = (iso) =>
  new Date(iso + 'T00:00:00+07:00')
    .toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' });

// Status pill HTML
const STATUS_PILL = {
  PENDING_PAYMENT:        '<span class="badge badge-sm badge-warning">Cho thanh toan</span>',
  CONFIRMED:              '<span class="badge badge-sm badge-info">Da xac nhan</span>',
  ASSIGNED:               '<span class="badge badge-sm badge-info">Da phan cong</span>',
  IN_PROGRESS:            '<span class="badge badge-sm badge-primary">Dang giao</span>',
  AWAITING_FINAL_PAYMENT: '<span class="badge badge-sm badge-warning">Cho tra 70%</span>',
  COMPLETED:              '<span class="badge badge-sm badge-success">Hoan thanh</span>',
  CANCELLED:              '<span class="badge badge-sm badge-danger">Da huy</span>',
  IN_DISPUTE:             '<span class="badge badge-sm badge-danger">Tranh chap</span>',
};

// ============================================================
// DATA LOADING — parallel fetch
// ============================================================
async function loadDashboard() {
  try {
    const [kpi, ordersByDay, revenueByMonth, pendingDrivers, recentOrders] =
      await Promise.all([
        fetch('/api/admin/dashboard/kpi', AUTH).then(r => r.json()),
        fetch('/api/admin/dashboard/chart/orders-by-day?days=30', AUTH).then(r => r.json()),
        fetch('/api/admin/dashboard/chart/revenue-by-month?months=12', AUTH).then(r => r.json()),
        fetch('/api/admin/dashboard/pending-drivers?limit=10', AUTH).then(r => r.json()),
        fetch('/api/admin/dashboard/recent-orders?limit=10', AUTH).then(r => r.json()),
      ]);

    renderKpi(kpi);
    renderOrdersByDayChart(ordersByDay);
    renderRevenueByMonthChart(revenueByMonth);
    renderPendingDriversTable(pendingDrivers);
    renderRecentOrdersTable(recentOrders);
  } catch (err) {
    console.error('Dashboard load failed', err);
    // showToast('danger', 'Loi!', 'Khong the tai du lieu dashboard. Thu lai sau.');
  }
}

// ============================================================
// KPI RENDERING
// ============================================================
function renderKpi(kpi) {
  document.getElementById('kpi-row-1').innerHTML = `
    <div class="kpi kpi-primary">
      <div class="kpi-icon">📦</div>
      <div class="kpi-body">
        <div class="kpi-value">${kpi.month_total_orders}</div>
        <div class="kpi-label">Tong don thang nay</div>
      </div>
    </div>
    <div class="kpi kpi-success">
      <div class="kpi-icon">💰</div>
      <div class="kpi-body">
        <div class="kpi-value">${fmtMoney(kpi.month_commission_revenue)}</div>
        <div class="kpi-label">Commission thang nay</div>
      </div>
    </div>
    <div class="kpi kpi-info">
      <div class="kpi-icon">✅</div>
      <div class="kpi-body">
        <div class="kpi-value">${kpi.completion_rate_percent}%</div>
        <div class="kpi-label">Ty le hoan thanh</div>
      </div>
    </div>
  `;

  document.getElementById('kpi-row-2').innerHTML = `
    <div class="kpi kpi-success">
      <div class="kpi-icon">🚗</div>
      <div class="kpi-body">
        <div class="kpi-value">${kpi.active_driver_count}</div>
        <div class="kpi-label">Driver dang ACTIVE</div>
      </div>
    </div>
    <div class="kpi kpi-warning">
      <div class="kpi-icon">⏳</div>
      <div class="kpi-body">
        <div class="kpi-value">${kpi.pending_approval_count}</div>
        <div class="kpi-label">Driver cho duyet</div>
      </div>
    </div>
    <div class="kpi kpi-danger">
      <div class="kpi-icon">⚠️</div>
      <div class="kpi-body">
        <div class="kpi-value">${kpi.in_dispute_count}</div>
        <div class="kpi-label">Don dang tranh chap</div>
      </div>
    </div>
  `;
}

// ============================================================
// CHART RENDERING
// ============================================================
function renderOrdersByDayChart(data) {
  const ctx = document.getElementById('orders-by-day-chart').getContext('2d');
  new Chart(ctx, {
    type: 'bar',
    data: {
      labels: data.map(d => fmtShortDate(d.date)),
      datasets: [{
        label: 'So don',
        data: data.map(d => d.count),
        backgroundColor: getComputedStyle(document.documentElement)
          .getPropertyValue('--color-primary-500').trim(),
        hoverBackgroundColor: getComputedStyle(document.documentElement)
          .getPropertyValue('--color-primary-700').trim(),
        borderRadius: 4,
        borderSkipped: false,
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, border: { display: false },
             ticks: { maxTicksLimit: 10, font: { size: 11 } } },
        y: { beginAtZero: true, border: { display: false },
             ticks: { precision: 0 } }
      }
    }
  });
  document.getElementById('orders-chart-label').textContent =
    `${data[0]?.date} → ${data[data.length - 1]?.date}`;
}

function renderRevenueByMonthChart(data) {
  const ctx = document.getElementById('revenue-by-month-chart').getContext('2d');
  const primaryColor = getComputedStyle(document.documentElement)
    .getPropertyValue('--color-primary-500').trim();
  new Chart(ctx, {
    type: 'line',
    data: {
      labels: data.map(d => d.month),
      datasets: [{
        label: 'Doanh thu (VND)',
        data: data.map(d => d.revenue),
        borderColor:     primaryColor,
        backgroundColor: primaryColor + '33',  /* opacity ~20% */
        borderWidth: 2,
        pointRadius: 4,
        fill: true,
        tension: 0.35,
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { display: false }, border: { display: false } },
        y: { beginAtZero: true, border: { display: false },
             ticks: {
               callback: (v) => v >= 1_000_000
                 ? `${(v / 1_000_000).toFixed(1)}M`
                 : v.toLocaleString('vi-VN')
             }
           }
      }
    }
  });
}

// ============================================================
// TABLE RENDERING
// ============================================================
function renderPendingDriversTable(drivers) {
  const tbody = document.getElementById('pending-drivers-body');
  if (!drivers.length) {
    tbody.innerHTML = `
      <tr><td colspan="5" style="text-align:center;padding:var(--space-10);color:var(--color-text-tertiary);">
        ✅ Khong co Driver nao dang cho duyet.
      </td></tr>`;
    return;
  }
  tbody.innerHTML = drivers.map(d => `
    <tr>
      <td><strong>${d.full_name}</strong></td>
      <td>${d.email}</td>
      <td>${(d.operating_districts || []).join(', ') || '—'}</td>
      <td>${fmtDate(d.submitted_at)}</td>
      <td class="table-col-action">
        <a href="/admin/drivers/${d.user_id}" class="btn btn-primary btn-sm">Xem ho so</a>
      </td>
    </tr>`).join('');
}

function renderRecentOrdersTable(orders) {
  const tbody = document.getElementById('recent-orders-body');
  if (!orders.length) {
    tbody.innerHTML = `
      <tr><td colspan="6" style="text-align:center;padding:var(--space-10);color:var(--color-text-tertiary);">
        📋 Chua co don hang nao.
      </td></tr>`;
    return;
  }
  tbody.innerHTML = orders.map(o => `
    <tr>
      <td><a href="/admin/orders/${o.order_id}" style="color:var(--color-primary-600);text-decoration:none;">
        #${o.order_id.toString().slice(-6).toUpperCase()}
      </a></td>
      <td>${o.customer_name}</td>
      <td>${o.driver_name ?? '<em style="color:var(--color-text-tertiary)">Chua phan cong</em>'}</td>
      <td>${fmtMoney(o.total_quote)}</td>
      <td>${STATUS_PILL[o.status] ?? o.status}</td>
      <td>${fmtDate(o.created_at)}</td>
    </tr>`).join('');
}

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', loadDashboard);
```

---

## 7. Seed Data Strategy

> File: `backend/src/main/resources/db/migration/V99__seed_demo_data.sql`
> Chay SAU het migration thuc te. Chi chay o DEV profile.

**Muc tieu:** Chart + table co data dep khi demo Thu Ba.

**User seed (~60 rows):**
```sql
-- Admin
INSERT INTO "user" (id, role, status, email, password_hash, full_name, must_change_password, created_at)
VALUES (gen_random_uuid(), 'ADMIN', 'ACTIVE', 'admin@movehome.vn',
        '$2a$12$...', 'Admin He Thong', false, NOW())
ON CONFLICT (email) DO NOTHING;

-- Manager
INSERT INTO "user" (id, role, status, email, password_hash, full_name, must_change_password, created_at)
VALUES (gen_random_uuid(), 'MANAGER', 'ACTIVE', 'manager@movehome.vn',
        '$2a$12$...', 'Nguyen Manager', false, NOW())
ON CONFLICT (email) DO NOTHING;

-- 8 Drivers ACTIVE + 5 Drivers PENDING_APPROVAL + others
-- (thuc te: generate bang script Python hoac dung Faker SQL)
```

**Order seed (~150 rows, trai 30 ngay):**
- ~120 COMPLETED (de revenue chart co data dep — tai sao: commission = 30% x total_quote)
- 3–7 orders moi ngay, volume tang nhe gan ngay hom nay (de bar chart co slope di len)
- ~5 IN_DISPUTE (de KPI danger hien so)
- ~5 CANCELLED

**wallet_transaction seed:**
- Moi COMPLETED order → 1 EARNING transaction (type=EARNING, amount = 70% x total_quote)
- 8 DEPOSIT_PAID cho 8 Driver ACTIVE

**Idempotency:** Dung `ON CONFLICT DO NOTHING` tren cac UNIQUE column (email, vnp_txn_ref).
Seed chi chay 1 lan do Flyway version control.

**Mat khau mac dinh cho demo (BCrypt cost 12, hash truoc khi nhung):**
- Admin: `Admin@123456`
- Manager: `Manager@123456`
- Drivers: `Driver@123456`

---

## 8. Non-Functional Requirements

| # | Yeu cau | Nguong | Ly do |
|---|---------|--------|-------|
| NFR-001 | Page load (DOMContentLoaded → all data rendered) | < 1.5s | Demo impression quan trong |
| NFR-002 | KPI endpoint p95 | < 200ms | 6 aggregations can index tot |
| NFR-003 | Chart endpoints p95 | < 300ms | GROUP BY theo ngay/thang |
| NFR-004 | Table endpoints p95 | < 100ms | LIMIT 10, co index |
| NFR-005 | Money display format | `Intl.NumberFormat('vi-VN')` → `1.234.567 ₫` | Thuan Viet Nam |
| NFR-006 | Date display format | `dd/MM/yyyy HH:mm` Asia/Ho_Chi_Minh | Nguoi dung VN |
| NFR-007 | Charts responsive | Hoat dong tren tablet 768px+ | Thay co the demo tablet |

---

## 9. Out of Scope — DEFER Phase 2

> Ghi ro de team khong implement them va lam cham sprint.

1. **Realtime updates** — WebSocket push khi co don moi / Driver moi duyet (defer post-demo)
2. **Filter date range tuy chinh** — KPI va chart hien tai fixed 30 ngay / 12 thang
3. **Export Excel / PDF** — bao cao cuoi ky (phase 2)
4. **Drill-down click vao chart** — click bar de xem danh sach don cua ngay do
5. **Multi-tenant** — chuoi cong ty, phan quyen theo branch (ngoai scope)
6. **Server-side caching (Redis)** — KPI refresh real-time; cache se them sau khi co load that
7. **Pagination cho tables** — dashboard fixed 10 rows; full list tren trang rieng
8. **Advanced search trong tables** — thanh tim kiem + filter theo ngay/status
9. **Notification badge** — "5 pending approvals" hien tren header bell icon
10. **Compare period** — "Thang nay vs thang truoc" percentage delta
11. **KPI custom config** — Admin chon hien widget gi, sap xep lai
12. **Mobile-optimized dashboard** — hien tai chi focus desktop/tablet
13. **Manager dashboard** — Manager co giao dien rieng (khac Admin), viet spec rieng sau demo

---

## 10. Constitution Compliance Mapping

| Rule | Enforced where | Implementation note |
|------|----------------|---------------------|
| HR-10 (RBAC → 403) | FR-001, tat ca 5 endpoint | `@PreAuthorize("hasRole('ADMIN')")` |
| HR-13 (Audit log) | FR-003 | INSERT auth_audit_log on DASHBOARD_VIEW |
| AC-07 (Timezone UTC) | FR-005..FR-011 | TIMESTAMP WITH TIME ZONE; display convert to Asia/Ho_Chi_Minh |
| AC-08 (BigDecimal money) | FR-006, FR-011 | NUMERIC(15,0); JSON serialize as integer; FE dung Intl.NumberFormat |
| AC-11 (CORS whitelist) | Tat ca endpoint | Spring Security CORS config cho FE origin |
| AC-12 (Flyway migration) | Seed V99 + index migration | V99__seed_demo_data.sql + V{n}__add_dashboard_indexes.sql |

---

## 11. Open Questions

| # | Cau hoi | Quyet dinh tam | Block? |
|---|---------|---------------|--------|
| OQ-1 | Cache KPI 5 phut de giam DB load? | DEFER — khong can cho demo (traffic thap) | No |
| OQ-2 | Seed data reset moi lan chay app (DEV) hay chi chay 1 lan (Flyway)? | Chay 1 lan qua Flyway V99 — reset bang cach drop + re-run migration | No |
| OQ-3 | Admin Dashboard va Manager Dashboard cung 1 URL hay khac? | Khac — Admin: `/admin/dashboard`; Manager: `/manager/dashboard` (spec sau demo) | No |
