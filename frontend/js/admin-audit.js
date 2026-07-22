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
let entityType = '';
let from = '';
let to = '';

function showLoading() {
  const tbody = document.getElementById('auditTableBody');
  if (tbody) tbody.innerHTML =
    `<tr><td colspan="5" class="table-empty-row"><span class="spinner spinner-sm"></span> Đang tải dữ liệu...</td></tr>`;
}

// Nhãn hành động cho dễ đọc — khớp 18 mã thật backend đang ghi (AuditService.log)
const ACTION_LABELS = {
  // Khiếu nại (entity_type = DISPUTE)
  DISPUTE_OPENED:            'Mở khiếu nại',
  DISPUTE_RESOLVED:          'Xử lý khiếu nại',
  DISPUTE_REJECTED:          'Từ chối khiếu nại',
  DISPUTE_RESOLVED_DEDUCT:   'Trừ tiền bồi thường tài xế',
  DISPUTE_DEDUCT_PENDING:    'Chờ tài xế nộp bổ sung',
  DISPUTE_PENALTY_PAID:      'Tài xế đã nộp bổ sung',
  DISPUTE_PENALTY_ENFORCED:  'Cưỡng chế phạt (hệ thống)',
  MISMATCH_DISPUTE_OPENED:   'Mở khiếu nại đối chiếu',
  MISMATCH_DISPUTE_REJECTED: 'Từ chối khiếu nại đối chiếu',
  MISMATCH_DISPUTE_RESOLVED: 'Xử lý khiếu nại đối chiếu',
  // Tài khoản (entity_type = USER)
  DRIVER_APPROVED:           'Duyệt tài xế',
  DRIVER_REJECTED:           'Từ chối tài xế',
  USER_SUSPENDED:            'Đình chỉ tài khoản',
  USER_REACTIVATED:          'Kích hoạt lại tài khoản',
  USER_ACCOUNT_LOCKED:       'Khóa tài khoản',
  USER_ACCOUNT_UNLOCKED:     'Mở khóa tài khoản',
  // Hoàn cọc (entity_type = ORDER_CANCELLATION_REFUND)
  CANCELLATION_REFUNDED:         'Hoàn cọc đơn hủy',
  CANCELLATION_REFUND_REJECTED:  'Từ chối hoàn cọc',
};
function actionLabel(a) {
  return ACTION_LABELS[a] || a || '—';
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

  // Pill lọc theo NHÓM ĐỐI TƯỢNG thật (entityType), không phải action cũ đã lỗi thời
  document.getElementById('filterContainer')?.addEventListener('click', (e) => {
    const pill = e.target.closest('[data-entity]');
    if (!pill) return;
    entityType = pill.dataset.entity || '';
    page = 0;
    document.querySelectorAll('#filterContainer [data-entity]').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    load();
  });

  // Bộ lọc thời gian (kết hợp được với pill nhóm đối tượng)
  document.getElementById('applyAuditFilters')?.addEventListener('click', () => {
    const nextFrom = document.getElementById('fromFilter')?.value || '';
    const nextTo = document.getElementById('toFilter')?.value || '';

    if (nextFrom && nextTo && new Date(nextFrom) > new Date(nextTo)) {
      showError('Thời gian bắt đầu phải trước hoặc bằng thời gian kết thúc.');
      return;
    }

    from = nextFrom;
    to = nextTo;
    page = 0;
    load();
  });

  document.getElementById('clearAuditFilters')?.addEventListener('click', () => {
    ['fromFilter', 'toFilter'].forEach(id => {
      const input = document.getElementById(id);
      if (input) input.value = '';
    });
    // Reset cả pill về "Tất cả"
    entityType = '';
    from = '';
    to = '';
    page = 0;
    document.querySelectorAll('#filterContainer [data-entity]').forEach(p => p.classList.remove('active'));
    document.querySelector('#filterContainer [data-entity=""]')?.classList.add('active');
    load();
  });

  load();
});
