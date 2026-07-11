// Chi tiết đơn hàng (Admin) — GET /api/admin/orders/{id}
// JSON snake_case (backend @JsonProperty).

import { setupAdminPage, formatVND, formatDateTime, esc, showError, hideError } from './admin-common.js';
import { getAuthenticated } from './api.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const ORDER_STATUS = {
  PENDING_PAYMENT: { label: 'Chờ thanh toán', cls: 'badge-pending' },
  CONFIRMED: { label: 'Đã xác nhận', cls: 'badge-in-progress' },
  ASSIGNED: { label: 'Đã phân công', cls: 'badge-in-progress' },
  IN_PROGRESS: { label: 'Đang thực hiện', cls: 'badge-in-progress' },
  AWAITING_FINAL_PAYMENT: { label: 'Chờ thanh toán cuối', cls: 'badge-pending' },
  COMPLETED: { label: 'Hoàn thành', cls: 'badge-completed' },
  IN_DISPUTE: { label: 'Đang khiếu nại', cls: 'badge-pending' },
  CANCELLED: { label: 'Đã hủy', cls: 'badge-cancelled' },
  PENDING: { label: 'Chờ xử lý', cls: 'badge-pending' },
};

const VEHICLE_LABELS = {
  TRUCK_500KG: 'Xe tải 500kg',
  TRUCK_1TON: 'Xe tải 1 tấn',
  TRUCK_1500KG: 'Xe tải 1.5 tấn',
  TRUCK_2TON: 'Xe tải 2 tấn',
};

function setText(id, value) { const el = document.getElementById(id); if (el) el.textContent = value; }

function fmtDateTime(iso) {
  if (!iso) return '—';
  return formatDateTime(iso);
}

document.addEventListener('DOMContentLoaded', () => {
  if (!setupAdminPage('../login.html')) return;

  const params = new URLSearchParams(window.location.search);
  const id = params.get('id') || params.get('orderId');
  if (!id || !UUID_PATTERN.test(id)) {
    showError('Thiếu mã đơn hàng hợp lệ trên đường dẫn (?id=...).');
    return;
  }
  loadDetail(id);
});

async function loadDetail(id) {
  hideError();
  try {
    const d = await getAuthenticated(`/api/admin/orders/${encodeURIComponent(id)}`);
    renderOrder(d.order, d.customer, d.driver, d.pickup, d.dropoff);
    renderPricing(d.pricing);
    renderTimeline(d.timeline || []);
    renderTransactions(d.transactions || []);
    renderLinks(d.customer, d.driver);
  } catch (err) {
    console.error('Lỗi tải chi tiết đơn:', err);
    showError(err.message || 'Không thể tải chi tiết đơn hàng. Vui lòng thử lại.');
    const tb = document.getElementById('od-tx-body');
    if (tb) tb.innerHTML = '<tr><td colspan="3" class="table-empty-row" style="color:var(--color-danger);">Không thể tải dữ liệu.</td></tr>';
  }
}

function renderOrder(order, customer, driver, pickup, dropoff) {
  if (order) {
    setText('order-title', 'Chi tiết đơn hàng ' + (order.order_code || ''));
    const badge = document.getElementById('od-status');
    if (badge) {
      const s = ORDER_STATUS[order.status] || { label: order.status || '—', cls: 'badge-pending' };
      badge.className = s.cls;
      badge.textContent = s.label;
    }
    setText('od-vehicle', VEHICLE_LABELS[order.vehicle_type] || order.vehicle_type || '—');
    setText('od-scheduled', fmtDateTime(order.scheduled_at));
  }
  setText('od-customer', customer?.full_name || '—');
  setText('od-driver', driver?.full_name || 'Chưa phân công');
  setText('od-pickup', joinLoc(pickup));
  setText('od-dropoff', joinLoc(dropoff));
}

function joinLoc(loc) {
  if (!loc) return '—';
  return [loc.address, loc.district].filter(Boolean).join(', ') || '—';
}

function renderPricing(p) {
  const container = document.getElementById('od-pricing');
  if (!container) return;
  if (!p) {
    container.innerHTML = '<p class="text-muted">Không có dữ liệu giá.</p>';
    return;
  }
  const rows = [];
  const line = (label, val) => `<div class="price-row"><span>${label}</span><strong>${formatVND(val)}</strong></div>`;

  if (p.base_fare != null) rows.push(line('Giá cơ bản', p.base_fare));
  if (Number(p.peak_surcharge) > 0) rows.push(line('Phụ phí giờ cao điểm', p.peak_surcharge));
  if (Number(p.alley_surcharge) > 0) rows.push(line('Phụ phí ngõ nhỏ', p.alley_surcharge));
  if (Number(p.floor_surcharge) > 0) rows.push(line('Phụ phí tầng cao', p.floor_surcharge));
  if (Number(p.porter_fee) > 0) rows.push(line('Phí bốc xếp', p.porter_fee));

  rows.push(`
    <div class="price-row price-total" style="display: flex; justify-content: space-between; padding: 12px 0 0 0; font-size: var(--font-size-body-lg); font-weight: 700; color: var(--color-primary); border-bottom: none;">
      <span>Tổng cộng</span><span>${formatVND(p.total_quote)}</span>
    </div>`);

  const rate = p.commission_rate_snapshot != null ? Math.round(Number(p.commission_rate_snapshot) * 100) : null;
  if (rate != null) {
    rows.push(`<p class="text-muted" style="font-size: var(--font-size-caption); margin: 10px 0 0 0;">Hoa hồng nền tảng: ${rate}%</p>`);
  }

  container.innerHTML = rows.join('');
}

function renderTimeline(items) {
  const container = document.getElementById('od-timeline');
  if (!container) return;
  if (!items.length) {
    container.innerHTML = '<p class="text-muted" style="margin:0; font-size: var(--font-size-body-sm);">Chưa có mốc thời gian nào.</p>';
    return;
  }
  container.innerHTML = items.map(it => `
    <div class="timeline-item" style="border-left: 4px solid var(--color-primary); padding-left: 14px;">
      <strong style="font-size: var(--font-size-body-sm-strong); color: var(--color-ink);">${fmtDateTime(it.at)}</strong>
      <p class="text-muted" style="margin: 4px 0 0 0; font-size: var(--font-size-body-sm);">${esc(it.label)}</p>
    </div>
  `).join('');
}

function renderTransactions(txs) {
  const tbody = document.getElementById('od-tx-body');
  if (!tbody) return;
  if (!txs.length) {
    tbody.innerHTML = '<tr><td colspan="3" class="table-empty-row" style="color:var(--color-mute);">Chưa có giao dịch nào.</td></tr>';
    return;
  }
  tbody.innerHTML = txs.map(tx => {
    const amount = Number(tx.amount);
    const color = amount < 0 ? 'var(--color-danger)' : 'inherit';
    const amountTxt = (amount >= 0 ? '' : '') + formatVND(amount);
    return `
      <tr>
        <td>${esc(tx.type_label) || esc(tx.type)}</td>
        <td>${esc(tx.user_name)}</td>
        <td class="text-right num-money" style="font-weight:500; color:${color};">${amountTxt}</td>
      </tr>`;
  }).join('');
}

function renderLinks(customer, driver) {
  const cl = document.getElementById('od-customer-link');
  if (cl) {
    if (customer?.id) cl.href = `customer-detail.html?id=${encodeURIComponent(customer.id)}`;
    else { cl.classList.add('disabled'); cl.style.opacity = '0.5'; cl.style.pointerEvents = 'none'; }
  }
  const dl = document.getElementById('od-driver-link');
  if (dl) {
    if (driver?.id) dl.href = `driver-detail.html?id=${encodeURIComponent(driver.id)}`;
    else { dl.textContent = 'Chưa có tài xế'; dl.style.opacity = '0.5'; dl.style.pointerEvents = 'none'; }
  }
}
