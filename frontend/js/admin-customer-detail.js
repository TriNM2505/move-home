// Chi tiết khách hàng (Admin) — GET /api/admin/customers/{id}
// JSON snake_case (backend @JsonProperty). Nut "Khoa tai khoan" do admin-user-account.js dam nhan.

import { setupAdminPage, formatVND, formatDateTime, esc, showError, hideError } from './admin-common.js';
import { getAuthenticated } from './api.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const USER_STATUS = {
  ACTIVE: { label: 'Đang hoạt động', cls: 'badge-active' },
  SUSPENDED: { label: 'Đã tạm ngưng', cls: 'badge-cancelled' },
  LOCKED: { label: 'Đã khóa', cls: 'badge-cancelled' },
  PENDING_VERIFY: { label: 'Chờ xác thực email', cls: 'badge-pending' },
  REJECTED: { label: 'Bị từ chối', cls: 'badge-cancelled' },
};

const ORDER_STATUS = {
  PENDING_PAYMENT: 'Chờ thanh toán', CONFIRMED: 'Đã xác nhận', ASSIGNED: 'Đã phân công',
  IN_PROGRESS: 'Đang thực hiện', AWAITING_FINAL_PAYMENT: 'Chờ thanh toán cuối',
  COMPLETED: 'Hoàn thành', IN_DISPUTE: 'Đang khiếu nại', CANCELLED: 'Đã hủy', PENDING: 'Chờ xử lý',
};

const TX_LABELS = {
  WALLET_TOP_UP: 'Nạp tiền', ORDER_PAYMENT: 'Thanh toán đơn', REFUND: 'Hoàn tiền',
  WITHDRAWAL: 'Rút tiền', DEPOSIT_TOP_UP: 'Nạp đặt cọc', DEPOSIT_REFUND: 'Hoàn đặt cọc',
  DRIVER_EARNING: 'Thu nhập tài xế', PLATFORM_FEE: 'Phí nền tảng', DAMAGE_DEDUCTION: 'Khấu trừ đền bù',
};

function setText(id, value) { const el = document.getElementById(id); if (el) el.textContent = value; }

function initials(name) {
  if (!name) return '—';
  return name.trim().split(/\s+/).filter(Boolean).map(w => w[0]).slice(0, 2).join('').toUpperCase();
}

function dateOnly(iso) {
  if (!iso) return '—';
  try {
    const d = new Date(iso);
    if (isNaN(d)) return '—';
    return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' });
  } catch { return '—'; }
}

document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  const params = new URLSearchParams(window.location.search);
  const id = params.get('id') || params.get('userId');
  if (!id || !UUID_PATTERN.test(id)) {
    showError('Thiếu mã khách hàng hợp lệ trên đường dẫn (?id=...).');
    return;
  }
  loadDetail(id);
});

async function loadDetail(id) {
  hideError();
  try {
    const d = await getAuthenticated(`/api/admin/customers/${encodeURIComponent(id)}`);
    renderHead(d.user);
    renderKpi(d.stats, d.wallet_summary);
    renderOrders(d.recent_orders || []);
    renderTransactions(d.recent_wallet_transactions || []);
  } catch (err) {
    console.error('Lỗi tải chi tiết khách hàng:', err);
    showError(err.message || 'Không thể tải chi tiết khách hàng. Vui lòng thử lại.');
    setText('cd-name', 'Không tải được dữ liệu');
    ['cd-orders-body', 'cd-tx-body'].forEach(bid => {
      const tb = document.getElementById(bid);
      if (tb) tb.innerHTML = '<tr><td colspan="3" class="table-empty-row" style="color:var(--color-danger);">Không thể tải dữ liệu.</td></tr>';
    });
  }
}

function renderHead(user) {
  if (!user) return;
  setText('cd-avatar', initials(user.full_name));
  setText('cd-name', user.full_name || '—');
  const parts = [];
  if (user.email) parts.push(user.email);
  if (user.phone_masked) parts.push(user.phone_masked);
  if (user.created_at) parts.push('Tham gia ' + dateOnly(user.created_at));
  setText('cd-subtitle', parts.join(' • ') || '—');

  const badge = document.getElementById('cd-status-badge');
  if (badge) {
    const s = USER_STATUS[user.status] || { label: user.status || '—', cls: 'badge-pending' };
    badge.className = s.cls;
    badge.textContent = s.label;
  }
}

function renderKpi(stats, wallet) {
  setText('cd-kpi-orders', Number(stats?.total_orders ?? 0).toLocaleString('vi-VN'));
  setText('cd-kpi-spent', formatVND(stats?.total_spent ?? 0));
  setText('cd-kpi-completed', Number(stats?.total_completed ?? 0).toLocaleString('vi-VN'));
  setText('cd-kpi-balance', formatVND(wallet?.balance ?? 0));
}

function renderOrders(orders) {
  const tbody = document.getElementById('cd-orders-body');
  if (!tbody) return;
  if (!orders.length) {
    tbody.innerHTML = '<tr><td colspan="3" class="table-empty-row" style="color:var(--color-mute);">Chưa có đơn nào.</td></tr>';
    return;
  }
  tbody.innerHTML = orders.map(o => {
    const orderCell = o.id
      ? `<a href="order-detail.html?id=${encodeURIComponent(o.id)}" class="link-primary" style="font-weight:500;">${esc(o.order_code) || 'Xem'}</a>`
      : (esc(o.order_code) || '—');
    return `
      <tr>
        <td>${orderCell}</td>
        <td>${statusBadge(o.status)}</td>
        <td class="text-right num-money" style="font-weight:500;">${formatVND(o.total_quote)}</td>
      </tr>`;
  }).join('');
}

function renderTransactions(txs) {
  const tbody = document.getElementById('cd-tx-body');
  if (!tbody) return;
  if (!txs.length) {
    tbody.innerHTML = '<tr><td colspan="3" class="table-empty-row" style="color:var(--color-mute);">Chưa có giao dịch nào.</td></tr>';
    return;
  }
  tbody.innerHTML = txs.map(tx => {
    const amount = Number(tx.amount);
    const color = amount < 0 ? 'var(--color-danger)' : 'var(--color-success)';
    const amountTxt = (amount >= 0 ? '+' : '') + formatVND(amount);
    return `
      <tr>
        <td>${esc(TX_LABELS[tx.type] || tx.type)}</td>
        <td class="text-muted">${dateOnly(tx.created_at)}</td>
        <td class="text-right num-money" style="font-weight:500; color:${color};">${amountTxt}</td>
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
