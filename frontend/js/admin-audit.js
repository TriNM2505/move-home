// Trang Nhật ký hệ thống — GET /api/admin/audit-logs (Spring Page, phân trang server-side)
import {
  setupAdminPage,
  esc,
  formatDateTime,
  showError,
  hideError,
  renderPagination,
} from './admin-common.js';
import { getAuthenticated } from './api.js';

let page = 0;
let size = 10;
let action = '';   // rỗng = tất cả
let entityType = '';
let from = '';
let to = '';

function showLoading() {
  const tbody = document.getElementById('auditTableBody');
  if (tbody) tbody.innerHTML =
    `<tr><td colspan="5" class="table-empty-row"><span class="spinner spinner-sm"></span> Đang tải dữ liệu...</td></tr>`;
}

// Nhãn hành động cho dễ đọc
function actionLabel(a) {
  const map = {
    LOGIN_SUCCESS: 'Đăng nhập', LOGIN_FAILED: 'Đăng nhập thất bại',
    ACCOUNT_LOCKED: 'Khóa tài khoản', ACCOUNT_UNLOCKED: 'Mở khóa tài khoản',
    PASSWORD_RESET: 'Đặt lại mật khẩu', ORDER_ACCEPTED: 'Nhận đơn',
    WITHDRAWAL_REQUESTED: 'Yêu cầu rút tiền',
  };
  return map[a] || a || '—';
}

function render(data) {
  const rows = (data && data.content) || [];
  const tbody = document.getElementById('auditTableBody');
  const countEl = document.getElementById('audit-count');

  if (countEl) {
    const total = (data && (data.totalElements ?? rows.length)) || 0;
    countEl.textContent = `${total} bản ghi`;
  }

  if (!rows.length) {
    tbody.innerHTML = `<tr><td colspan="5" class="table-empty-row">📝 Chưa có bản ghi nào.</td></tr>`;
  } else {
    tbody.innerHTML = rows.map(r => {
      const who = esc(r.actorEmail || 'Hệ thống');
      const obj = r.entityType ? `${esc(r.entityType)} ${esc(r.entityId || '')}` : '—';
      return `
        <tr>
          <td class="text-muted">${esc(formatDateTime(r.createdAt))}</td>
          <td>${who}</td>
          <td><strong>${esc(actionLabel(r.action))}</strong></td>
          <td>${obj}</td>
          <td class="text-muted">${esc(r.detail || '—')}</td>
        </tr>`;
    }).join('');
  }

  renderPagination(
    'audit-pagination',
    data,
    newPage => { page = newPage; load(); },
    newSize => { size = newSize; page = 0; load(); },
    'bản ghi',
  );
}

async function load() {
  hideError();
  showLoading();
  try {
    const q = new URLSearchParams({ page, size });
    if (action) q.set('action', action);
    if (entityType) q.set('entityType', entityType);
    if (from) q.set('from', new Date(from).toISOString());
    if (to) q.set('to', new Date(to).toISOString());
    const data = await getAuthenticated('/api/admin/audit-logs?' + q.toString());
    render(data);
  } catch (err) {
    console.error('Lỗi tải audit log:', err);
    showError(err.message || 'Không thể tải nhật ký. Vui lòng thử lại.');
    const tbody = document.getElementById('auditTableBody');
    if (tbody) tbody.innerHTML = `<tr><td colspan="5" class="table-empty-row">⚠️ Lỗi tải dữ liệu.</td></tr>`;
  }
}

document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  document.getElementById('filterContainer')?.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-action]');
    if (!pill) return;
    action = pill.dataset.action || '';
    page = 0;
    document.querySelectorAll('#filterContainer [data-action]').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    load();
  });

  document.getElementById('applyAuditFilters')?.addEventListener('click', () => {
    const nextEntityType = document.getElementById('entityTypeFilter')?.value.trim() || '';
    const nextFrom = document.getElementById('fromFilter')?.value || '';
    const nextTo = document.getElementById('toFilter')?.value || '';

    if (nextFrom && nextTo && new Date(nextFrom) > new Date(nextTo)) {
      showError('Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.');
      return;
    }

    entityType = nextEntityType;
    from = nextFrom;
    to = nextTo;
    page = 0;
    load();
  });

  document.getElementById('clearAuditFilters')?.addEventListener('click', () => {
    ['entityTypeFilter', 'fromFilter', 'toFilter'].forEach(id => {
      const input = document.getElementById(id);
      if (input) input.value = '';
    });
    entityType = '';
    from = '';
    to = '';
    page = 0;
    load();
  });

  load();
});
