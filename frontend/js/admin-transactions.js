// Trang Giao dịch hệ thống — GET /api/admin/transactions (Spring Page, server-side pagination)
// KPI strip lay tu GET /api/admin/dashboard/kpi (du lieu that, khong hardcode).

import {
  setupAdminPage, formatVND, formatDateTime,
  esc, showError, hideError, renderPagination,
} from './admin-common.js';
import { getAuthenticated } from './api.js';

let currentPage = 0;
let currentSize = 10;
let currentType = 'ALL'; // ALL = tat ca loai

const ROLE_LABELS = {
  CUSTOMER: 'Khách hàng',
  DRIVER: 'Tài xế',
  MANAGER: 'Quản lý',
  ADMIN: 'Quản trị',
};

function roleLabel(role) {
  return ROLE_LABELS[role] || (role ? esc(role) : '—');
}

// ============================================================
// KPI STRIP (du lieu that tu dashboard KPI)
// ============================================================
async function loadKpi() {
  try {
    const kpi = await getAuthenticated('/api/admin/dashboard/kpi');
    setText('kpi-revenue', formatVND(kpi.totalRevenueThisMonth));
    setText('kpi-commission', formatVND(kpi.totalCommissionThisMonth));
    setText('kpi-completed', kpi.completedOrders ?? 0);
    setText('kpi-pending', kpi.pendingOrders ?? 0);
  } catch (err) {
    console.warn('Không tải được KPI giao dịch:', err);
    ['kpi-revenue', 'kpi-commission', 'kpi-completed', 'kpi-pending'].forEach(id => setText(id, '—'));
  }
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

// ============================================================
// BANG GIAO DICH
// ============================================================
function showLoading() {
  const tbody = document.getElementById('txTableBody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="7" class="table-empty-row">
        <span class="spinner spinner-sm"></span> Đang tải dữ liệu...
      </td>
    </tr>`;
}

function renderTable(items) {
  const tbody = document.getElementById('txTableBody');
  if (!tbody) return;

  if (!items.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="table-empty-row" style="padding: var(--spacing-4xl) var(--spacing-2xl);">
          <div style="display:flex; flex-direction:column; align-items:center; gap:var(--spacing-md); color:var(--color-mute);">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity:0.6;"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect><line x1="1" y1="10" x2="23" y2="10"></line></svg>
            <span style="font-size:var(--font-size-body-sm); font-weight:500;">Không có giao dịch nào phù hợp bộ lọc.</span>
          </div>
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = items.map(tx => {
    const code = tx.id ? `GD-${String(tx.id).substring(0, 8).toUpperCase()}` : '—';
    const orderCell = tx.relatedOrderId
      ? `<a href="order-detail.html?id=${encodeURIComponent(tx.relatedOrderId)}" class="link-primary">${esc(tx.orderCode) || 'Xem đơn'}</a>`
      : '<span class="text-muted">—</span>';
    const amount = Number(tx.amount);
    const amountCls = amount < 0 ? 'amount-minus' : 'amount-plus';
    const amountTxt = (amount >= 0 ? '+' : '') + formatVND(amount);
    return `
      <tr>
        <td style="font-family:monospace;">${code}</td>
        <td>${esc(tx.userName)}</td>
        <td class="text-muted">${roleLabel(tx.userRole)}</td>
        <td>${orderCell}</td>
        <td>${esc(tx.typeLabel) || esc(tx.type)}</td>
        <td class="text-muted">${formatDateTime(tx.createdAt)}</td>
        <td class="text-right num-money ${amountCls}">${amountTxt}</td>
      </tr>`;
  }).join('');
}

async function loadTransactions() {
  hideError();
  showLoading();
  try {
    const url = `/api/admin/transactions?page=${currentPage}&size=${currentSize}&type=${encodeURIComponent(currentType)}`;
    const page = await getAuthenticated(url);

    renderTable(page.content || []);

    const countEl = document.getElementById('tx-count');
    if (countEl) countEl.textContent = `${page.totalElements ?? 0} giao dịch`;

    renderPagination('paginationContainer', page, changePage, changeSize, 'giao dịch');
  } catch (err) {
    console.error('Lỗi khi tải giao dịch:', err);
    showError(err.message || 'Không thể tải danh sách giao dịch. Vui lòng thử lại.');
    const tbody = document.getElementById('txTableBody');
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="7" class="table-empty-row" style="padding:var(--spacing-4xl) var(--spacing-2xl); color:var(--color-danger);">
            <div style="display:flex; flex-direction:column; align-items:center; gap:var(--spacing-md);">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
              <span style="font-size:var(--font-size-body-sm); font-weight:500;">Không thể tải danh sách giao dịch. Vui lòng kiểm tra kết nối.</span>
            </div>
          </td>
        </tr>`;
    }
  }
}

function changePage(newPage) { currentPage = newPage; loadTransactions(); }
function changeSize(newSize) { currentSize = newSize; currentPage = 0; loadTransactions(); }

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  document.getElementById('filterContainer')?.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-type]');
    if (!pill) return;
    currentType = pill.dataset.type || 'ALL';
    currentPage = 0;
    document.querySelectorAll('#filterContainer [data-type]').forEach(b => b.classList.remove('active'));
    pill.classList.add('active');
    loadTransactions();
  });

  loadKpi();
  loadTransactions();
});
