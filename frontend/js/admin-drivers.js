// Trang quan ly tai xe — GET /api/admin/dashboard/drivers
// Response: List<DriverListItem>

import { getAuthenticated } from './api.js';
import { setupAdminPage, formatVnd, formatDate, driverStatusBadge, esc, showError, hideError } from './admin-common.js';

let currentStatus = '';

const FILTER_OPTIONS = [
  { value: '',                  label: 'Tat ca trang thai' },
  { value: 'ACTIVE',            label: 'Dang hoat dong (ACTIVE)' },
  { value: 'PENDING_APPROVAL',  label: 'Cho duyet (PENDING_APPROVAL)' },
  { value: 'PENDING_DOCUMENTS', label: 'Cho ho so (PENDING_DOCUMENTS)' },
  { value: 'PENDING_DEPOSIT',   label: 'Cho dong coc (PENDING_DEPOSIT)' },
  { value: 'SUSPENDED',         label: 'Tam khoa (SUSPENDED)' },
  { value: 'REJECTED',          label: 'Tu choi (REJECTED)' },
];

function showLoading(colSpan = 13) {
  const tbody = document.getElementById('drivers-tbody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="${colSpan}" class="table-empty-row">
        <span class="spinner spinner-sm" style="display:inline-block;vertical-align:middle;margin-right:8px;"></span>
        Dang tai du lieu...
      </td>
    </tr>`;
}

// Render sao rating
function renderStars(rating) {
  const n = parseFloat(rating) || 0;
  return '⭐ ' + n.toFixed(1);
}

function renderDrivers(drivers) {
  const tbody   = document.getElementById('drivers-tbody');
  const countEl = document.getElementById('drivers-count');
  if (!tbody) return;

  if (countEl) {
    countEl.textContent = `Hien thi ${drivers.length} tai xe`;
  }

  if (!drivers.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="13" class="table-empty-row">
          🚚 Khong co tai xe nao voi trang thai nay.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = drivers.map(d => {
    // Highlight row mau vang cho PENDING_APPROVAL (call to action)
    const rowClass = d.status === 'PENDING_APPROVAL' ? 'row-pending-approval' : '';
    return `
    <tr class="${rowClass}">
      <td><strong>${esc(d.fullName)}</strong></td>
      <td style="font-size:var(--font-size-xs);">${esc(d.email)}</td>
      <td>${esc(d.phone) || '—'}</td>
      <td>${esc(d.licenseNumber) || '—'}</td>
      <td>${esc(d.vehiclePlate) || '—'}</td>
      <td>${esc(d.vehicleType) || '—'}</td>
      <td class="num-money">${formatVnd(d.depositAmount)}</td>
      <td class="text-center">${d.totalOrdersCompleted ?? 0}</td>
      <td class="num-money">${formatVnd(d.totalRevenue)}</td>
      <td class="text-center">${renderStars(d.averageRating)}</td>
      <td>${driverStatusBadge(d.status)}</td>
      <td style="font-size:var(--font-size-xs);white-space:nowrap;">
        ${d.approvedAt ? formatDate(d.approvedAt) : '<em class="text-muted">Chua duyet</em>'}
      </td>
    </tr>`;
  }).join('');
}

async function loadDrivers() {
  hideError();
  showLoading();

  try {
    let url = '/api/admin/dashboard/drivers';
    if (currentStatus) url += `?status=${encodeURIComponent(currentStatus)}`;

    const drivers = await getAuthenticated(url);
    renderDrivers(Array.isArray(drivers) ? drivers : []);
  } catch (err) {
    console.error('Loi khi tai tai xe:', err);
    showError(err.message || 'Khong the tai danh sach tai xe. Thu lai sau.');
    const tbody = document.getElementById('drivers-tbody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="13" class="table-empty-row">⚠️ Loi tai du lieu.</td></tr>`;
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  const select = document.getElementById('status-filter');
  if (select) {
    FILTER_OPTIONS.forEach(opt => {
      const option = document.createElement('option');
      option.value = opt.value;
      option.textContent = opt.label;
      select.appendChild(option);
    });
    select.addEventListener('change', () => {
      currentStatus = select.value;
      loadDrivers();
    });
  }

  loadDrivers();
});
