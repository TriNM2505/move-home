// Trang quản lý khách hàng — GET /api/admin/dashboard/customers
// Response: List<CustomerListItem> — client-side pagination (Sprint 2 sẽ server-side)

import {
  setupAdminPage, formatDate,
  customerStatusBadge, esc, showError, hideError,
  renderPagination, clientSidePaginate,
} from './admin-common.js';
import { getAuthenticated } from './api.js';

let allCustomers  = [];   // toàn bộ list từ API
let currentPage   = 0;
let currentSize   = 10;
let currentStatus = '';   // rỗng = tất cả

// Hiển thị spinner
function showLoading() {
  const tbody = document.getElementById('customersTableBody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="7" class="table-empty-row">
        <span class="spinner spinner-sm"></span>
        Đang tải dữ liệu...
      </td>
    </tr>`;
}

// Render bảng khách hàng
function renderCustomersTable(customers) {
  const tbody = document.getElementById('customersTableBody');
  if (!tbody) return;

  if (!customers.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="table-empty-row">
          👥 Không có khách hàng nào với trạng thái này.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = customers.map(c => {
    const emailVerified = c.emailVerified
      ? `<span class="verified-yes">✅ Đã xác thực</span>`
      : `<span class="verified-no">❌ Chưa xác thực</span>`;
    return `
      <tr>
        <td><strong>${esc(c.fullName)}</strong></td>
        <td class="text-muted">${esc(c.email)}</td>
        <td>${esc(c.phone) || '—'}</td>
        <td>${emailVerified}</td>
        <td class="text-right">${c.totalOrdersPlaced ?? 0}</td>
        <td class="text-muted">${formatDate(c.createdAt)}</td>
        <td>${customerStatusBadge(c.status)}</td>
      </tr>`;
  }).join('');
}

// Lọc và re-render với pagination
function applyFilterAndRender() {
  const filtered = currentStatus
    ? allCustomers.filter(c => c.status === currentStatus)
    : allCustomers;

  const countEl = document.getElementById('customers-count');
  if (countEl) countEl.textContent = `${filtered.length} khách hàng`;

  const pageData = clientSidePaginate(filtered, currentPage, currentSize);
  renderCustomersTable(pageData.content);
  renderPagination('paginationContainer', pageData, changePage, changeSize, 'khách hàng');
}

// Tải toàn bộ từ API (List response)
async function loadCustomers() {
  hideError();
  showLoading();
  allCustomers = [];

  try {
    const data = await getAuthenticated('/api/admin/dashboard/customers');
    allCustomers = Array.isArray(data) ? data : [];
    applyFilterAndRender();
  } catch (err) {
    console.error('Lỗi khi tải khách hàng:', err);
    showError(err.message || 'Không thể tải danh sách khách hàng. Vui lòng thử lại.');
    const tbody = document.getElementById('customersTableBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7" class="table-empty-row">⚠️ Lỗi tải dữ liệu.</td></tr>`;
    }
  }
}

function changePage(newPage) {
  currentPage = newPage;
  applyFilterAndRender();
}

function changeSize(newSize) {
  currentSize = newSize;
  currentPage = 0;
  applyFilterAndRender();
}

// ============================================================
// INIT
// ============================================================
document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  // Filter pills — event delegation
  document.getElementById('filterContainer')?.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-status]');
    if (!pill) return;
    currentStatus = pill.dataset.status || '';
    currentPage   = 0;
    document.querySelectorAll('#filterContainer [data-status]')
      .forEach(b => b.classList.remove('active'));
    pill.classList.add('active');
    applyFilterAndRender();
  });

  loadCustomers();
});
