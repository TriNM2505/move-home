// Dashboard Admin — tai va render du lieu tu GET /api/admin/dashboard/overview
// Spec #028 FR-016: dung 1 API call thay vi 5 call rieng le

import { getAuthenticated } from './api.js';
import { isLoggedIn, getCurrentUser, logout } from './auth.js';

// Chuyen doi so tien VND sang dinh dang hien thi (CLAUDE.md §5)
function formatVnd(amount) {
  if (amount === null || amount === undefined) return '—';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Number(amount));
}

// Chuyen ISO timestamp sang dd/MM/yyyy HH:mm Asia/Ho_Chi_Minh (CLAUDE.md §5 AC-07)
function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

// Chuyen date string 'yyyy-MM-dd' sang 'DD/MM' cho truc x cua chart
function formatShortDate(dateStr) {
  if (!dateStr) return '';
  const [, mm, dd] = dateStr.split('-');
  return `${dd}/${mm}`;
}

// Render sao rating (0-5)
function renderStars(rating) {
  const n = parseFloat(rating) || 0;
  const full  = Math.floor(n);
  const empty = 5 - full;
  return '★'.repeat(full) + '☆'.repeat(empty);
}

// HTML badge trang thai don hang (design-internal-reference.md Status Mapping)
const STATUS_LABEL = {
  PENDING_PAYMENT:        'Cho thanh toan',
  CONFIRMED:              'Da xac nhan',
  ASSIGNED:               'Da phan cong',
  IN_PROGRESS:            'Dang giao',
  AWAITING_FINAL_PAYMENT: 'Cho tra 70%',
  COMPLETED:              'Hoan thanh',
  CANCELLED:              'Da huy',
  IN_DISPUTE:             'Tranh chap',
};
const STATUS_BADGE_CLASS = {
  PENDING_PAYMENT:        'badge-warning',
  CONFIRMED:              'badge-info',
  ASSIGNED:               'badge-info',
  IN_PROGRESS:            'badge-primary',
  AWAITING_FINAL_PAYMENT: 'badge-warning',
  COMPLETED:              'badge-success',
  CANCELLED:              'badge-neutral',
  IN_DISPUTE:             'badge-danger',
};

function statusBadge(status) {
  const cls   = STATUS_BADGE_CLASS[status] || 'badge-neutral';
  const label = STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// Mau sac cho donut chart Status Distribution
const STATUS_CHART_COLOR = {
  PENDING_PAYMENT:        '#F59E0B',
  CONFIRMED:              '#0284C7',
  ASSIGNED:               '#6366F1',
  IN_PROGRESS:            '#533afd',
  AWAITING_FINAL_PAYMENT: '#F97316',
  COMPLETED:              '#16A34A',
  CANCELLED:              '#9CA3AF',
  IN_DISPUTE:             '#DC2626',
};

// ============================================================
// RENDER KPI CARDS (4 the — Spec prompt §A)
// ============================================================
function renderKpiCards(kpi) {
  const container = document.getElementById('kpi-section');
  if (!kpi || !container) return;

  container.innerHTML = `
    <div class="grid-4">
      <div class="kpi kpi-primary">
        <div class="kpi-icon">👥</div>
        <div class="kpi-body">
          <div class="kpi-value">${kpi.totalCustomers ?? 0}</div>
          <div class="kpi-label">Tong khach hang</div>
          <div class="kpi-sub">Da dang ky</div>
        </div>
      </div>
      <div class="kpi kpi-success">
        <div class="kpi-icon">🚗</div>
        <div class="kpi-body">
          <div class="kpi-value">${kpi.activeDrivers ?? 0}</div>
          <div class="kpi-label">Tai xe ACTIVE</div>
          <div class="kpi-sub">${kpi.pendingDriverApprovals ?? 0} cho duyet</div>
        </div>
      </div>
      <div class="kpi kpi-warning">
        <div class="kpi-icon">📦</div>
        <div class="kpi-body">
          <div class="kpi-value">${kpi.totalOrdersThisMonth ?? 0}</div>
          <div class="kpi-label">Don thang nay</div>
          <div class="kpi-sub">Hom nay: ${kpi.totalOrdersToday ?? 0} don</div>
        </div>
      </div>
      <div class="kpi kpi-info">
        <div class="kpi-icon">💰</div>
        <div class="kpi-body">
          <div class="kpi-value num-money">${formatVnd(kpi.totalCommissionThisMonth ?? 0)}</div>
          <div class="kpi-label">Commission thang nay</div>
          <div class="kpi-sub">Doanh thu: ${formatVnd(kpi.totalRevenueThisMonth ?? 0)}</div>
        </div>
      </div>
    </div>
  `;
}

// ============================================================
// RENDER REVENUE CHART — line chart 30 ngay (Spec prompt §B)
// ============================================================
function renderRevenueChart(revenueChart) {
  const canvas = document.getElementById('revenue-chart');
  if (!canvas || !revenueChart?.points?.length) return;

  const points = revenueChart.points;
  const labels  = points.map(p => formatShortDate(p.date));
  const data    = points.map(p => Number(p.revenue) || 0);

  // Lay CSS variable de dung trong Chart.js
  const primaryColor = getComputedStyle(document.documentElement)
    .getPropertyValue('--color-primary').trim() || '#533afd';

  new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Doanh thu (VND)',
        data,
        borderColor:     primaryColor,
        backgroundColor: primaryColor + '28',  /* ~15% opacity */
        borderWidth:     2,
        pointRadius:     3,
        pointHoverRadius: 5,
        fill:   true,
        tension: 0.4,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${formatVnd(ctx.parsed.y)}`,
          }
        }
      },
      scales: {
        x: {
          grid:   { display: false },
          border: { display: false },
          ticks:  { font: { size: 11 }, maxTicksLimit: 10 },
        },
        y: {
          beginAtZero: true,
          border:      { display: false },
          ticks: {
            callback: (v) => v >= 1_000_000
              ? `${(v / 1_000_000).toFixed(1)}M`
              : v.toLocaleString('vi-VN'),
            font: { size: 11 },
          },
          grid: { color: 'rgba(0, 55, 112, 0.06)' }
        }
      }
    }
  });
}

// ============================================================
// RENDER TOP DRIVERS TABLE (Spec prompt §C left)
// ============================================================
function renderTopDrivers(topDrivers) {
  const tbody = document.getElementById('top-drivers-body');
  if (!tbody) return;

  const drivers = topDrivers?.topDrivers || [];
  if (!drivers.length) {
    tbody.innerHTML = `<tr><td colspan="4" class="table-empty-row">Chua co du lieu tai xe.</td></tr>`;
    return;
  }

  tbody.innerHTML = drivers.map((d, i) => `
    <tr>
      <td>
        <span style="color:var(--color-text-tertiary);margin-right:8px;">${i + 1}.</span>
        <strong>${escapeHtml(d.fullName)}</strong>
      </td>
      <td class="text-center">${d.totalOrders}</td>
      <td class="col-money">${formatVnd(d.totalRevenue)}</td>
      <td>
        <span class="star-rating">${renderStars(d.averageRating)}</span>
        <span class="text-muted" style="font-size:12px;margin-left:4px;">${parseFloat(d.averageRating || 0).toFixed(1)}</span>
      </td>
    </tr>
  `).join('');
}

// ============================================================
// RENDER STATUS DISTRIBUTION DONUT CHART (Spec prompt §C right)
// ============================================================
function renderStatusDistribution(statusDistribution) {
  const canvas = document.getElementById('status-chart');
  if (!canvas || !statusDistribution?.distribution) return;

  const dist = statusDistribution.distribution;
  const entries = Object.entries(dist).filter(([, v]) => v > 0);
  if (!entries.length) return;

  const labels = entries.map(([k]) => STATUS_LABEL[k] || k);
  const data   = entries.map(([, v]) => v);
  const colors = entries.map(([k]) => STATUS_CHART_COLOR[k] || '#9CA3AF');

  new Chart(canvas, {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderWidth: 2,
        borderColor: '#ffffff',
        hoverOffset: 6,
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            font: { size: 11 },
            padding: 12,
            boxWidth: 12,
          }
        },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${ctx.label}: ${ctx.raw} don`,
          }
        }
      },
      cutout: '60%',
    }
  });
}

// ============================================================
// RENDER RECENT ORDERS TABLE (Spec prompt §D)
// ============================================================
function renderRecentOrders(recentOrders) {
  const tbody = document.getElementById('recent-orders-body');
  if (!tbody) return;

  const orders = recentOrders?.orders || [];
  if (!orders.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="table-empty-row">📋 Chua co don hang nao.</td></tr>`;
    return;
  }

  tbody.innerHTML = orders.slice(0, 10).map(o => {
    const shortCode = o.orderCode || `#${String(o.orderId).slice(-6).toUpperCase()}`;
    const driverDisplay = o.driverName
      ? escapeHtml(o.driverName)
      : `<em class="text-muted">Chua phan cong</em>`;
    return `
      <tr>
        <td><span class="fw-medium" style="color:var(--color-primary);">${shortCode}</span></td>
        <td>${escapeHtml(o.customerName)}</td>
        <td>${driverDisplay}</td>
        <td>${statusBadge(o.status)}</td>
        <td class="col-money">${formatVnd(o.totalQuote)}</td>
        <td class="text-muted">${formatDate(o.createdAt)}</td>
      </tr>`;
  }).join('');
}

// ============================================================
// TIEN ICH AN TOAN — ngan XSS
// ============================================================
function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ============================================================
// INIT — chay khi trang tai xong
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
  // Guard: chuyen den login neu chua dang nhap
  if (!isLoggedIn()) {
    window.location.href = '../../pages/login.html';
    return;
  }

  // Hien ten user tren header
  const user = getCurrentUser();
  const nameEl   = document.getElementById('header-user-name');
  const avatarEl = document.getElementById('header-user-avatar');
  if (nameEl && user?.fullName) {
    nameEl.textContent = user.fullName;
  }
  if (avatarEl && user?.fullName) {
    const initials = user.fullName
      .split(' ')
      .map(w => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
    avatarEl.textContent = initials;
  }

  // Nut logout
  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      logout('../login.html');
    });
  }

  // Hien skeleton trong luc load
  const kpiSection = document.getElementById('kpi-section');
  if (kpiSection) {
    kpiSection.innerHTML = `
      <div class="grid-4">
        ${Array(4).fill(`<div class="kpi kpi-primary"><div class="skeleton" style="height:80px;width:100%;border-radius:var(--radius-md);"></div></div>`).join('')}
      </div>`;
  }

  try {
    // Goi 1 API call lay toan bo du lieu dashboard (Spec #028 FR-016)
    const overview = await getAuthenticated('/api/admin/dashboard/overview');

    renderKpiCards(overview.kpi);
    renderRevenueChart(overview.revenueChart);
    renderTopDrivers(overview.topDrivers);
    renderStatusDistribution(overview.statusDistribution);
    renderRecentOrders(overview.recentOrders);

  } catch (err) {
    console.error('Loi khi tai dashboard:', err);

    // Redirect login neu 401/403
    if (err.status === 401 || err.status === 403) {
      logout('../login.html');
      return;
    }

    // Hien thong bao loi
    const kpiSection = document.getElementById('kpi-section');
    if (kpiSection) {
      kpiSection.innerHTML = `
        <div class="alert alert-danger" style="display:flex;">
          ⚠️ Khong the tai du lieu dashboard.
          ${err.message || 'Vui long thu lai.'}
          <button onclick="location.reload()" class="btn btn-sm btn-danger" style="margin-left:auto;">
            Thu lai
          </button>
        </div>`;
    }
  }
});
