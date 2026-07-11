// Trang quản lý đơn hàng — GET /api/admin/dashboard/orders
// Response: Page<OrderListItem> (Spring Data Page — server-side pagination)

import {
  setupAdminPage, formatVND, formatDateTime,
  orderStatusBadge, esc, showError, hideError,
  renderPagination,
} from './admin-common.js';
import { getAuthenticated } from './api.js';

let currentPage   = 0;
let currentSize   = 10;
let currentStatus = '';   // rỗng = tất cả

// Hiển thị spinner trong tbody khi đang tải
function showLoading() {
  const tbody = document.getElementById('ordersTableBody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="8" class="table-empty-row">
        <span class="spinner spinner-sm"></span>
        Đang tải dữ liệu...
      </td>
    </tr>`;
}

// Render bảng đơn hàng
function renderOrdersTable(orders) {
  const tbody   = document.getElementById('ordersTableBody');
  const countEl = document.getElementById('orders-count');
  if (!tbody) return;

  if (!orders.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" class="table-empty-row" style="padding: var(--spacing-4xl) var(--spacing-2xl);">
          <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-md); color: var(--color-mute);">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity: 0.6;"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
            <span style="font-size: var(--font-size-body-sm); font-weight: 500;">Không có đơn hàng nào với trạng thái này.</span>
          </div>
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = orders.map(o => {
    const code    = esc(o.orderCode || String(o.orderId || o.id || '').slice(-6).toUpperCase());
    const orderId = o.orderId || o.id;
    const codeCell = orderId
      ? `<a href="order-detail.html?id=${encodeURIComponent(orderId)}" class="link-primary fw-medium">${code}</a>`
      : `<span class="fw-medium">${code}</span>`;
    const driver  = o.driverName
      ? esc(o.driverName)
      : `<em class="text-muted">Chưa phân công</em>`;
    return `
      <tr>
        <td>${codeCell}</td>
        <td>${esc(o.customerName)}</td>
        <td>${driver}</td>
        <td class="text-muted">${esc(o.pickupDistrict) || '—'}</td>
        <td class="text-muted">${esc(o.dropoffDistrict) || '—'}</td>
        <td>${orderStatusBadge(o.status)}</td>
        <td class="text-right num-money">${formatVND(o.totalQuote)}</td>
        <td class="text-muted">${formatDateTime(o.createdAt)}</td>
      </tr>`;
  }).join('');
}

// Gọi API và render
async function loadOrders() {
  hideError();
  showLoading();

  try {
    let url = `/api/admin/dashboard/orders?page=${currentPage}&size=${currentSize}`;
    if (currentStatus) url += `&status=${encodeURIComponent(currentStatus)}`;

    const page = await getAuthenticated(url);

    renderOrdersTable(page.content || []);

    // Cập nhật nhãn đếm
    const countEl = document.getElementById('orders-count');
    if (countEl) {
      countEl.textContent = `${page.totalElements ?? 0} đơn hàng`;
    }

    // Render pagination bar
    renderPagination('paginationContainer', page, changePage, changeSize, 'đơn');

  } catch (err) {
    console.error('Lỗi khi tải đơn hàng:', err);
    showError(err.message || 'Không thể tải danh sách đơn hàng. Vui lòng thử lại.');
    const tbody = document.getElementById('ordersTableBody');
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="8" class="table-empty-row" style="padding: var(--spacing-4xl) var(--spacing-2xl); color: var(--color-danger);">
            <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-md);">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" style="opacity: 0.8;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
              <span style="font-size: var(--font-size-body-sm); font-weight: 500;">Không thể tải danh sách đơn hàng. Vui lòng kiểm tra kết nối.</span>
            </div>
          </td>
        </tr>`;
    }
  }
}

function changePage(newPage) {
  currentPage = newPage;
  loadOrders();
}

function changeSize(newSize) {
  currentSize = newSize;
  currentPage = 0;
  loadOrders();
}

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  // Filter pills — event delegation, không inline onclick
  document.getElementById('filterContainer')?.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-status]');
    if (!pill) return;
    currentStatus = pill.dataset.status || '';
    currentPage   = 0;
    document.querySelectorAll('#filterContainer [data-status]')
      .forEach(b => b.classList.remove('active'));
    pill.classList.add('active');
    loadOrders();
  });

  loadOrders();
});
