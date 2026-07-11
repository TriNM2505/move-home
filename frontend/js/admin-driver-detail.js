// Chi tiết tài xế (Admin) — GET /api/admin/drivers/{id}
// JSON snake_case (backend @JsonProperty). Nut "Khoa tai khoan" do admin-user-account.js dam nhan.

import { setupAdminPage, formatVND, formatDateTime, esc, showError, hideError } from './admin-common.js';
import { getAuthenticated } from './api.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const USER_STATUS = {
  ACTIVE: { label: 'Đang hoạt động', cls: 'badge-active' },
  SUSPENDED: { label: 'Đã tạm ngưng', cls: 'badge-cancelled' },
  LOCKED: { label: 'Đã khóa', cls: 'badge-cancelled' },
  PENDING_VERIFY: { label: 'Chờ xác thực email', cls: 'badge-pending' },
  PENDING_DOCUMENTS: { label: 'Chờ nộp giấy tờ', cls: 'badge-pending' },
  PENDING_DEPOSIT: { label: 'Chờ đặt cọc', cls: 'badge-pending' },
  PENDING_APPROVAL: { label: 'Chờ duyệt', cls: 'badge-pending' },
  REJECTED: { label: 'Bị từ chối', cls: 'badge-cancelled' },
};

const ORDER_STATUS = {
  PENDING_PAYMENT: 'Chờ thanh toán', CONFIRMED: 'Đã xác nhận', ASSIGNED: 'Đã phân công',
  IN_PROGRESS: 'Đang thực hiện', AWAITING_FINAL_PAYMENT: 'Chờ thanh toán cuối',
  COMPLETED: 'Hoàn thành', IN_DISPUTE: 'Đang khiếu nại', CANCELLED: 'Đã hủy', PENDING: 'Chờ xử lý',
};

function setText(id, value) { const el = document.getElementById(id); if (el) el.textContent = value; }

function initials(name) {
  if (!name) return '—';
  return name.trim().split(/\s+/).filter(Boolean).map(w => w[0]).slice(0, 2).join('').toUpperCase();
}

document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  const params = new URLSearchParams(window.location.search);
  const id = params.get('id') || params.get('userId');
  if (!id || !UUID_PATTERN.test(id)) {
    showError('Thiếu mã tài xế hợp lệ trên đường dẫn (?id=...).');
    return;
  }
  loadDetail(id);
});

async function loadDetail(id) {
  hideError();
  try {
    const d = await getAuthenticated(`/api/admin/drivers/${encodeURIComponent(id)}`);
    renderHead(d.user, d.vehicles);
    renderKpi(d.stats, d.wallet);
    renderProfile(d.profile, d.vehicles, d.user, d.documents_summary);
    renderWallet(d.wallet, d.deposit);
    renderOrders(d.recent_orders || []);
  } catch (err) {
    console.error('Lỗi tải chi tiết tài xế:', err);
    showError(err.message || 'Không thể tải chi tiết tài xế. Vui lòng thử lại.');
    setText('dd-name', 'Không tải được dữ liệu');
    const tb = document.getElementById('dd-orders-body');
    if (tb) tb.innerHTML = '<tr><td colspan="4" class="table-empty-row" style="color:var(--color-danger);">Không thể tải lịch sử đơn.</td></tr>';
  }
}

function renderHead(user, vehicles) {
  if (!user) return;
  setText('dd-avatar', initials(user.full_name));
  setText('dd-name', user.full_name || '—');
  const v = (vehicles && vehicles[0]) || null;
  const parts = [];
  if (v?.vehicle_type) parts.push(v.vehicle_type);
  if (v?.plate) parts.push('Biển số ' + v.plate);
  if (user.email) parts.push(user.email);
  setText('dd-subtitle', parts.join(' • ') || '—');

  const badge = document.getElementById('dd-status-badge');
  if (badge) {
    const s = USER_STATUS[user.status] || { label: user.status || '—', cls: 'badge-pending' };
    badge.className = s.cls;
    badge.textContent = s.label;
  }
}

function renderKpi(stats, wallet) {
  const completed = stats?.total_completed_orders ?? 0;
  const cancelled = stats?.total_cancelled_orders ?? 0;
  setText('dd-kpi-completed', Number(completed).toLocaleString('vi-VN'));
  setText('dd-kpi-earned', formatVND(wallet?.total_earned ?? 0));
  setText('dd-kpi-rating', stats?.average_rating != null
    ? Number(stats.average_rating).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + '★'
    : '—');
  const denom = completed + cancelled;
  setText('dd-kpi-completion', denom > 0 ? Math.round((completed / denom) * 100) + '%' : '—');
}

function renderProfile(profile, vehicles, user, docs) {
  setText('dd-license-number', profile?.license_number || '—');
  setText('dd-license-class', profile?.license_class || '—');
  const v = (vehicles && vehicles[0]) || null;
  setText('dd-vehicle-type', v?.vehicle_type || '—');
  setText('dd-vehicle-plate', v?.plate || '—');
  const verified = (docs?.total_count ?? 0) > 0 || profile?.approved_at;
  setText('dd-profile-status', verified ? 'Đã có giấy tờ' : 'Chưa đầy đủ');
}

function renderWallet(wallet, deposit) {
  setText('dd-wallet-balance', formatVND(wallet?.balance ?? 0));
  setText('dd-wallet-earned', formatVND(wallet?.total_earned ?? 0));
  setText('dd-wallet-withdrawn', formatVND(wallet?.total_withdrawn ?? 0));
  setText('dd-deposit-amount', formatVND(deposit?.amount ?? 0));
  setText('dd-deposit-status', deposit?.status || '—');
}

function renderOrders(orders) {
  const tbody = document.getElementById('dd-orders-body');
  if (!tbody) return;
  if (!orders.length) {
    tbody.innerHTML = '<tr><td colspan="4" class="table-empty-row" style="color:var(--color-mute);">Chưa có đơn nào.</td></tr>';
    return;
  }
  tbody.innerHTML = orders.map(o => {
    const route = `${esc(o.pickup_district) || '—'} → ${esc(o.dropoff_district) || '—'}`;
    const orderCell = o.id
      ? `<a href="order-detail.html?id=${encodeURIComponent(o.id)}" class="link-primary">${esc(o.order_code) || 'Xem'}</a>`
      : (esc(o.order_code) || '—');
    return `
      <tr>
        <td>${orderCell}</td>
        <td class="text-muted">${route}</td>
        <td>${statusBadge(o.status)}</td>
        <td class="text-right num-money" style="font-weight:500;">${formatVND(o.total_quote)}</td>
      </tr>`;
  }).join('');
}

function statusBadge(status) {
  const label = ORDER_STATUS[status] || status || '—';
  let cls = 'badge-pending';
  if (status === 'COMPLETED') cls = 'badge-completed';
  else if (status === 'CANCELLED') cls = 'badge-cancelled';
  else if (status === 'IN_PROGRESS' || status === 'ASSIGNED') cls = 'badge-in-progress';
  return `<span class="${cls}">${esc(label)}</span>`;
}
