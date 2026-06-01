// Trang quan ly khach hang — GET /api/admin/dashboard/customers
// Response: List<CustomerListItem>

import { getAuthenticated } from './api.js';
import { setupAdminPage, formatDate, customerStatusBadge, esc, showError, hideError } from './admin-common.js';

let currentStatus = '';

const FILTER_OPTIONS = [
  { value: '',               label: 'Tat ca trang thai' },
  { value: 'ACTIVE',         label: 'Hoat dong (ACTIVE)' },
  { value: 'PENDING_VERIFY', label: 'Cho xac thuc (PENDING_VERIFY)' },
  { value: 'SUSPENDED',      label: 'Tam khoa (SUSPENDED)' },
];

function showLoading(colSpan = 7) {
  const tbody = document.getElementById('customers-tbody');
  if (!tbody) return;
  tbody.innerHTML = `
    <tr>
      <td colspan="${colSpan}" class="table-empty-row">
        <span class="spinner spinner-sm" style="display:inline-block;vertical-align:middle;margin-right:8px;"></span>
        Dang tai du lieu...
      </td>
    </tr>`;
}

function renderCustomers(customers) {
  const tbody   = document.getElementById('customers-tbody');
  const countEl = document.getElementById('customers-count');
  if (!tbody) return;

  if (countEl) {
    countEl.textContent = `Hien thi ${customers.length} khach hang`;
  }

  if (!customers.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" class="table-empty-row">
          👥 Khong co khach hang nao voi trang thai nay.
        </td>
      </tr>`;
    return;
  }

  tbody.innerHTML = customers.map(c => {
    // Hien thi email da xac thuc: ✓ xanh hoac ✗ do
    const emailVerifiedHtml = c.emailVerified
      ? '<span class="verified-yes">✓ Da xac thuc</span>'
      : '<span class="verified-no">✗ Chua xac thuc</span>';

    return `
    <tr>
      <td><strong>${esc(c.fullName)}</strong></td>
      <td style="font-size:var(--font-size-xs);">${esc(c.email)}</td>
      <td>${esc(c.phone) || '—'}</td>
      <td>${emailVerifiedHtml}</td>
      <td class="text-center">${c.totalOrdersPlaced ?? 0}</td>
      <td>${customerStatusBadge(c.status)}</td>
      <td style="font-size:var(--font-size-xs);white-space:nowrap;">
        ${formatDate(c.createdAt)}
      </td>
    </tr>`;
  }).join('');
}

async function loadCustomers() {
  hideError();
  showLoading();

  try {
    let url = '/api/admin/dashboard/customers';
    if (currentStatus) url += `?status=${encodeURIComponent(currentStatus)}`;

    const customers = await getAuthenticated(url);
    renderCustomers(Array.isArray(customers) ? customers : []);
  } catch (err) {
    console.error('Loi khi tai khach hang:', err);
    showError(err.message || 'Khong the tai danh sach khach hang. Thu lai sau.');
    const tbody = document.getElementById('customers-tbody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7" class="table-empty-row">⚠️ Loi tai du lieu.</td></tr>`;
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
      loadCustomers();
    });
  }

  loadCustomers();
});
