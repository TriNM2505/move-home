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
        <td colspan="8" class="table-empty-row">
          📋 Không có đơn hàng nào với trạng thái này.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = orders.map(o => {
    const code    = esc(o.orderCode || String(o.orderId || o.id || '').slice(-6).toUpperCase());
    const driver  = o.driverName
      ? esc(o.driverName)
      : `<em class="text-muted">Chưa phân công</em>`;
    return `
      <tr>
        <td><a href="#" class="link-primary fw-medium">${code}</a></td>
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
      tbody.innerHTML = `<tr><td colspan="8" class="table-empty-row">⚠️ Lỗi tải dữ liệu.</td></tr>`;
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
