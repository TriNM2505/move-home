// Dashboard Admin — tải và render dữ liệu từ GET /api/admin/dashboard/overview
// Spec #028 FR-016: dùng 1 API call thay vì 5 call riêng lẻ

import { getAuthenticated } from './api.js';
import { isLoggedIn, getCurrentUser, logout } from './auth.js';

// ============================================================
// FORMAT HELPERS
// ============================================================

// Format số tiền VND — "1.500.000 VND" (CLAUDE.md §5 AC-08)
function formatVND(amount) {
  if (amount === null || amount === undefined) return '—';
  return new Intl.NumberFormat('vi-VN').format(Number(amount)) + ' VND';
}

// Format ISO timestamp → "dd/MM/yyyy HH:mm" timezone Hồ Chí Minh (AC-07)
function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

// Format "yyyy-MM-dd" → "dd/MM" cho trục x biểu đồ
function formatShortDate(dateStr) {
  if (!dateStr) return '';
  const [, mm, dd] = dateStr.split('-');
  return `${dd}/${mm}`;
}

// Render sao đánh giá (0–5)
function renderStars(rating) {
  const n = parseFloat(rating) || 0;
  return '★'.repeat(Math.floor(n)) + '☆'.repeat(5 - Math.floor(n));
}

// Ngăn XSS — escape HTML
function esc(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ============================================================
// STATUS MAPPING (6 trạng thái từ API overview)
// ============================================================
const STATUS_VN = {
  PENDING:     'Đang chờ',
  ACCEPTED:    'Đã nhận đơn',
  IN_PROGRESS: 'Đang giao',
  COMPLETED:   'Hoàn thành',
  CANCELLED:   'Đã hủy',
  DISPUTED:    'Khiếu nại',
};

const STATUS_BADGE_CLASS = {
  PENDING:     'badge-pending',
  ACCEPTED:    'badge-active',
  IN_PROGRESS: 'badge-in-progress',
  COMPLETED:   'badge-completed',
  CANCELLED:   'badge-cancelled',
  DISPUTED:    'badge-disputed',
};

// Màu sắc cho donut chart — dùng màu đậm để dễ đọc trên chart
const STATUS_CHART_COLOR = {
  PENDING:     '#F59E0B',
  ACCEPTED:    '#0EA5E9',
  IN_PROGRESS: '#3B82F6',
  COMPLETED:   '#16A34A',
  CANCELLED:   '#DC2626',
  DISPUTED:    '#F97316',
};

function statusBadge(status) {
  const cls   = STATUS_BADGE_CLASS[status] || 'badge-neutral';
  const label = STATUS_VN[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// ============================================================
// RENDER KPI CARDS — 4 thẻ chỉ số (Spec #028 FR-017)
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
          <div class="kpi-label">Tổng khách hàng</div>
          <div class="kpi-sub">Đã đăng ký</div>
        </div>
      </div>
      <div class="kpi kpi-success">
        <div class="kpi-icon">🚗</div>
        <div class="kpi-body">
          <div class="kpi-value">${kpi.activeDrivers ?? 0}</div>
          <div class="kpi-label">Tài xế đang hoạt động</div>
          <div class="kpi-sub">${kpi.pendingDriverApprovals ?? 0} đang chờ duyệt</div>
        </div>
      </div>
      <div class="kpi kpi-warning">
        <div class="kpi-icon">📦</div>
        <div class="kpi-body">
          <div class="kpi-value">${kpi.totalOrdersThisMonth ?? 0}</div>
          <div class="kpi-label">Đơn hàng tháng này</div>
          <div class="kpi-sub">Hôm nay: ${kpi.totalOrdersToday ?? 0} đơn</div>
        </div>
      </div>
      <div class="kpi kpi-info">
        <div class="kpi-icon">💰</div>
        <div class="kpi-body">
          <div class="kpi-value num-money">${formatVND(kpi.totalRevenueThisMonth ?? 0)}</div>
          <div class="kpi-label">Doanh thu tháng này</div>
          <div class="kpi-sub">Hoa hồng: ${formatVND(kpi.totalCommissionThisMonth ?? 0)}</div>
        </div>
      </div>
    </div>
  `;
}

// ============================================================
// RENDER REVENUE CHART — line chart 2 đường 30 ngày (Spec #028 FR-018)
// ============================================================
function renderRevenueChart(revenueChart) {
  const canvas = document.getElementById('revenue-chart');
  if (!canvas || !revenueChart?.points?.length) return;

  const points = revenueChart.points;
  const labels  = points.map(p => formatShortDate(p.date));
  const revenue = points.map(p => Number(p.revenue)    || 0);
  const comm    = points.map(p => Number(p.commission) || 0);

  // Lấy màu từ CSS variables — tự động theo brand mới
  const style       = getComputedStyle(document.documentElement);
  const primaryColor = style.getPropertyValue('--color-primary').trim() || '#1B4D3E';
  const accentColor  = style.getPropertyValue('--color-accent').trim()  || '#F5A623';

  // Cập nhật meta label (ngày đầu — ngày cuối)
  if (points.length >= 2 && window.updateRevenueChartMeta) {
    const from = formatShortDate(points[0].date);
    const to   = formatShortDate(points[points.length - 1].date);
    window.updateRevenueChartMeta(from, to);
  }

  new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'Doanh thu',
          data: revenue,
          borderColor:      primaryColor,
          backgroundColor:  primaryColor + '22',
          borderWidth:      2,
          pointRadius:      3,
          pointHoverRadius: 5,
          fill:    true,
          tension: 0.4,
        },
        {
          label: 'Hoa hồng',
          data: comm,
          borderColor:      accentColor,
          backgroundColor:  accentColor + '22',
          borderWidth:      2,
          pointRadius:      3,
          pointHoverRadius: 5,
          fill:    false,
          tension: 0.4,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: { font: { size: 12, family: "'Be Vietnam Pro', sans-serif" }, padding: 16, boxWidth: 14 },
        },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${ctx.dataset.label}: ${formatVND(ctx.parsed.y)}`,
          },
        },
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
            font: { size: 11 },
            callback: (v) => v >= 1_000_000
              ? `${(v / 1_000_000).toFixed(1)}M`
              : new Intl.NumberFormat('vi-VN').format(v),
          },
          grid: { color: 'rgba(0, 0, 0, 0.06)' },
        },
      },
    },
  });
}

// ============================================================
// RENDER TOP DRIVERS TABLE (Spec #028 FR-019 left panel)
// ============================================================
function renderTopDrivers(topDrivers) {
  const tbody = document.getElementById('top-drivers-body');
  if (!tbody) return;

  const drivers = topDrivers?.topDrivers || [];
  if (!drivers.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="4" class="table-empty-row">Chưa có dữ liệu tài xế.</td>
      </tr>`;
    return;
  }

  tbody.innerHTML = drivers.map((d, i) => `
    <tr>
      <td>
        <span class="text-muted" style="margin-right:8px;">${i + 1}.</span>
        <strong>${esc(d.fullName)}</strong>
      </td>
      <td class="text-center">${d.totalOrders}</td>
      <td class="col-money">${formatVND(d.totalRevenue)}</td>
      <td>
        <span class="star-rating">${renderStars(d.averageRating)}</span>
        <span class="text-muted" style="font-size:12px; margin-left:4px;">${parseFloat(d.averageRating || 0).toFixed(1)}</span>
      </td>
    </tr>
  `).join('');
}

// ============================================================
// RENDER STATUS DISTRIBUTION — donut chart (Spec #028 FR-019 right)
// ============================================================
function renderStatusDistribution(statusDistribution) {
  const canvas = document.getElementById('status-chart');
  if (!canvas || !statusDistribution?.distribution) return;

  const dist    = statusDistribution.distribution;
  const entries = Object.entries(dist).filter(([, v]) => v > 0);
  if (!entries.length) return;

  const labels = entries.map(([k]) => STATUS_VN[k] || k);
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
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            font: { size: 11, family: "'Be Vietnam Pro', sans-serif" },
            padding: 12,
            boxWidth: 12,
          },
        },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${ctx.label}: ${ctx.raw} đơn`,
          },
        },
      },
      cutout: '60%',
    },
  });
}

// ============================================================
// RENDER RECENT ORDERS TABLE (Spec #028 FR-020)
// ============================================================
function renderRecentOrders(recentOrders) {
  const tbody = document.getElementById('recent-orders-body');
  if (!tbody) return;

  const orders = recentOrders?.orders || [];
  if (!orders.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="6" class="table-empty-row">📋 Chưa có đơn hàng nào.</td>
      </tr>`;
    return;
  }

  tbody.innerHTML = orders.slice(0, 10).map(o => {
    const shortCode = o.orderCode || `#${String(o.orderId).slice(-6).toUpperCase()}`;
    const driverCell = o.driverName
      ? esc(o.driverName)
      : `<em class="text-muted">Chưa phân công</em>`;
    return `
      <tr>
        <td>
          <a href="#" class="link-primary fw-medium">${esc(shortCode)}</a>
        </td>
        <td>${esc(o.customerName)}</td>
        <td>${driverCell}</td>
        <td>${statusBadge(o.status)}</td>
        <td class="text-right col-money">${formatVND(o.totalQuote)}</td>
        <td class="text-muted">${formatDateTime(o.createdAt)}</td>
      </tr>`;
  }).join('');
}

// ============================================================
// INIT — khởi chạy khi trang tải xong
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
  // Guard: chuyển đến login nếu chưa đăng nhập
  if (!isLoggedIn()) {
    window.location.href = '../login.html';
    return;
  }

  // Hiển thị tên và avatar admin trên header
  const user     = getCurrentUser();
  const nameEl   = document.getElementById('header-user-name');
  const avatarEl = document.getElementById('header-user-avatar');

  if (nameEl && user?.fullName) {
    nameEl.textContent = user.fullName;
  }
  if (avatarEl && user?.fullName) {
    avatarEl.textContent = user.fullName
      .split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  }

  // Gắn sự kiện đăng xuất
  document.getElementById('logout-btn')
    ?.addEventListener('click', () => logout('../login.html'));

  // Hiện skeleton trong khi tải
  const kpiSection = document.getElementById('kpi-section');
  if (kpiSection) {
    kpiSection.innerHTML = `
      <div class="grid-4">
        ${Array(4).fill(0).map(() => `
          <div class="kpi kpi-primary">
            <div class="skeleton" style="height:80px;width:100%;border-radius:var(--rounded-md);"></div>
          </div>`).join('')}
      </div>`;
  }

  try {
    // Gọi 1 API duy nhất lấy toàn bộ dữ liệu dashboard (Spec #028 FR-016)
    const overview = await getAuthenticated('/api/admin/dashboard/overview');

    renderKpiCards(overview.kpi);
    renderRevenueChart(overview.revenueChart);
    renderTopDrivers(overview.topDrivers);
    renderStatusDistribution(overview.statusDistribution);
    renderRecentOrders(overview.recentOrders);

  } catch (err) {
    console.error('Lỗi khi tải dashboard:', err);

    // Redirect về login nếu token hết hạn / không có quyền
    if (err.status === 401 || err.status === 403) {
      logout('../login.html');
      return;
    }

    // Hiển thị thông báo lỗi và nút tải lại
    if (kpiSection) {
      kpiSection.innerHTML = `
        <div class="alert alert-danger" style="display:flex; align-items:center; gap:12px;">
          ⚠️ Không thể tải dữ liệu dashboard. ${esc(err.message) || 'Vui lòng thử lại.'}
          <button onclick="location.reload()" class="btn btn-sm btn-danger" style="margin-left:auto; flex-shrink:0;">
            Tải lại
          </button>
        </div>`;
    }

    // Xóa skeleton bảng
    const driversBody = document.getElementById('top-drivers-body');
    if (driversBody) {
      driversBody.innerHTML = `<tr><td colspan="4" class="table-empty-row">Không thể tải dữ liệu.</td></tr>`;
    }
    const ordersBody = document.getElementById('recent-orders-body');
    if (ordersBody) {
      ordersBody.innerHTML = `<tr><td colspan="6" class="table-empty-row">Không thể tải dữ liệu.</td></tr>`;
    }
  }
});
