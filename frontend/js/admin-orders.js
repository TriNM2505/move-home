// Trang quan ly don hang — GET /api/admin/dashboard/orders
// Response: Page<OrderListItem> (Spring Page)

import { getAuthenticated } from './api.js';
import { setupAdminPage, formatVnd, formatDateTime, orderStatusBadge, esc, showError, hideError } from './admin-common.js';

// Trang thai dang chon (mac dinh: tat ca)
let currentStatus = '';
let currentPage   = 0;
const PAGE_SIZE   = 30;

// Mapping filter value → label tieng Viet
const FILTER_OPTIONS = [
  { value: '',                      label: 'Tat ca trang thai' },
  { value: 'PENDING_PAYMENT',       label: 'Cho thanh toan' },
  { value: 'CONFIRMED',             label: 'Da xac nhan' },
  { value: 'ASSIGNED',              label: 'Da phan cong' },
  { value: 'IN_PROGRESS',           label: 'Dang giao' },
  { value: 'AWAITING_FINAL_PAYMENT', label: 'Cho tra 70%' },
  { value: 'COMPLETED',             label: 'Hoan thanh' },
  { value: 'CANCELLED',             label: 'Da huy' },
  { value: 'IN_DISPUTE',            label: 'Tranh chap' },
];

// Hien loading trong tbody
function showLoading(colSpan = 9) {
  const tbody = document.getElementById('orders-tbody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="${colSpan}" class="table-empty-row">
        <span class="spinner spinner-sm" style="display:inline-block;vertical-align:middle;margin-right:8px;"></span>
        Dang tai du lieu...
      </td>
    </tr>`;
}

// Format commissionRateSnapshot (0.30 → "30%")
function formatRate(rate) {
  if (rate === null || rate === undefined) return '—';
  return Math.round(Number(rate) * 100) + '%';
}

// Render bang don hang
function renderOrders(page) {
  const tbody = document.getElementById('orders-tbody');
  const countEl = document.getElementById('orders-count');
  if (!tbody) return;

  const orders = page.content || [];
  const total  = page.totalElements || 0;

  // Cap nhat count label
  if (countEl) {
    countEl.textContent = `Hien thi ${orders.length} / ${total} don hang`;
  }

  if (!orders.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="9" class="table-empty-row">
          📋 Khong co don hang nao voi trang thai nay.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = orders.map(o => `
    <tr>
      <td>
        <span class="fw-medium" style="color:var(--color-primary);">
          ${esc(o.orderCode || String(o.id).slice(-6).toUpperCase())}
        </span>
      </td>
      <td>${esc(o.customerName)}</td>
      <td>${o.driverName ? esc(o.driverName) : '<em class="text-muted">Chua phan cong</em>'}</td>
      <td class="text-muted">${esc(o.pickupDistrict) || '—'}</td>
      <td class="text-muted">${esc(o.dropoffDistrict) || '—'}</td>
      <td class="num-money">${formatVnd(o.totalQuote)}</td>
      <td class="text-center">${formatRate(o.commissionRateSnapshot)}</td>
      <td>${orderStatusBadge(o.status)}</td>
      <td class="text-muted" style="font-size:var(--font-size-xs);white-space:nowrap;">
        ${formatDateTime(o.createdAt)}
      </td>
    </tr>`).join('');
}

// Goi API va re-render
async function loadOrders() {
  hideError();
  showLoading();

  try {
    let url = `/api/admin/dashboard/orders?page=${currentPage}&size=${PAGE_SIZE}`;
    if (currentStatus) url += `&status=${encodeURIComponent(currentStatus)}`;

    const page = await getAuthenticated(url);
    renderOrders(page);
  } catch (err) {
    console.error('Loi khi tai don hang:', err);
    showError(err.message || 'Khong the tai danh sach don hang. Thu lai sau.');
    const tbody = document.getElementById('orders-tbody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="9" class="table-empty-row">⚠️ Loi tai du lieu.</td></tr>`;
    }
  }
}

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  // Render filter options
  const select = document.getElementById('status-filter');
  if (select) {
    FILTER_OPTIONS.forEach(opt => {
      const option = document.createElement('option');
      option.value = opt.value;
      option.textContent = opt.label;
      select.appendChild(option);
    });

    // Lang nghe thay doi filter
    select.addEventListener('change', () => {
      currentStatus = select.value;
      currentPage   = 0;
      loadOrders();
    });
  }

  // Tai du lieu lan dau
  loadOrders();
});
