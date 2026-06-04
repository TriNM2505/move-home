// Trang quản lý tài xế — GET /api/admin/dashboard/drivers
// Response: List<DriverListItem> — client-side pagination (Sprint 2 sẽ server-side)

import {
  setupAdminPage, formatVND, formatDate,
  driverStatusBadge, esc, showError, hideError,
  renderPagination, clientSidePaginate,
} from './admin-common.js';
import { getAuthenticated } from './api.js';

let allDrivers    = [];   // toàn bộ list từ API
let currentPage   = 0;
let currentSize   = 10;
let currentStatus = '';   // rỗng = tất cả

// Hiển thị spinner
function showLoading() {
  const tbody = document.getElementById('driversTableBody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="10" class="table-empty-row">
        <span class="spinner spinner-sm"></span>
        Đang tải dữ liệu...
      </td>
    </tr>`;
}

// Render bảng — nhận mảng drivers đã được slice bởi clientSidePaginate
function renderDriversTable(drivers) {
  const tbody = document.getElementById('driversTableBody');
  if (!tbody) return;

  if (!drivers.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="10" class="table-empty-row">
          🚛 Không có tài xế nào với trạng thái này.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = drivers.map(d => {
    const rowClass = d.status === 'PENDING_APPROVAL' ? 'row-warning' : '';
    const stars    = parseFloat(d.averageRating || 0).toFixed(1);
    return `
      <tr class="${rowClass}">
        <td><strong>${esc(d.fullName)}</strong></td>
        <td class="text-muted">${esc(d.email)}</td>
        <td>${esc(d.phone) || '—'}</td>
        <td>${esc(d.licenseNumber) || '—'}</td>
        <td>${esc(d.vehiclePlate) || '—'}</td>
        <td>${esc(d.vehicleType) || '—'}</td>
        <td class="text-right">${d.totalOrdersCompleted ?? 0}</td>
        <td class="text-right num-money">${formatVND(d.totalRevenue)}</td>
        <td>★ ${stars}/5</td>
        <td>${driverStatusBadge(d.status)}</td>
      </tr>`;
  }).join('');
}

// Lọc và re-render với pagination
function applyFilterAndRender() {
  const filtered = currentStatus
    ? allDrivers.filter(d => d.status === currentStatus)
    : allDrivers;

  const countEl = document.getElementById('drivers-count');
  if (countEl) countEl.textContent = `${filtered.length} tài xế`;

  const pageData = clientSidePaginate(filtered, currentPage, currentSize);
  renderDriversTable(pageData.content);
  renderPagination('paginationContainer', pageData, changePage, changeSize, 'tài xế');
}

// Tải toàn bộ từ API (List response)
async function loadDrivers() {
  hideError();
  showLoading();
  allDrivers = [];

  try {
    const data = await getAuthenticated('/api/admin/dashboard/drivers');
    allDrivers = Array.isArray(data) ? data : [];
    applyFilterAndRender();
  } catch (err) {
    console.error('Lỗi khi tải tài xế:', err);
    showError(err.message || 'Không thể tải danh sách tài xế. Vui lòng thử lại.');
    const tbody = document.getElementById('driversTableBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="10" class="table-empty-row">⚠️ Lỗi tải dữ liệu.</td></tr>`;
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

  loadDrivers();
});
