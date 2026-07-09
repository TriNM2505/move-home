// Logic dung chung cho trang "Don dang cho" va "Lich su" cua Customer.
// Goi GET /api/customer/orders?scope=... — backend da filter theo JWT (HR-10),
// nen khach chi thay don cua chinh minh.

import { isLoggedIn, getCurrentUser, logout } from './auth.js';
import { getAuthenticated } from './api.js';
import { formatVnd } from './admin-common.js';

// Map status (English enum) → nhan tieng Viet + class badge (HR-20)
const STATUS_META = {
  PENDING:                { label: 'Chờ thanh toán',      badge: 'badge-pending' },
  PENDING_PAYMENT:        { label: 'Chờ thanh toán',      badge: 'badge-pending' },
  CONFIRMED:              { label: 'Đã xác nhận',         badge: 'badge-info' },
  ASSIGNED:               { label: 'Đã phân tài xế',      badge: 'badge-info' },
  IN_PROGRESS:            { label: 'Đang giao',           badge: 'badge-in-progress' },
  AWAITING_FINAL_PAYMENT: { label: 'Chờ thanh toán cuối', badge: 'badge-warning' },
  COMPLETED:              { label: 'Hoàn thành',          badge: 'badge-completed' },
  CANCELLED:              { label: 'Đã hủy',              badge: 'badge-cancelled' },
  IN_DISPUTE:             { label: 'Đang khiếu nại',      badge: 'badge-disputed' },
};

function statusMeta(status) {
  return STATUS_META[status] || { label: status || '—', badge: 'badge-neutral' };
}

function escapeHTML(str) {
  if (str === null || str === undefined) return '';
  return String(str).replace(/[&<>'"]/g, tag => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  }[tag] || tag));
}

// scheduled_at (ISO) → "HH:mm, dd/MM/yyyy" theo gio Viet Nam (AC-07)
function formatDateTimeVN(iso) {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit',
      day: '2-digit', month: '2-digit', year: 'numeric',
      timeZone: 'Asia/Ho_Chi_Minh',
    });
  } catch {
    return '';
  }
}

function initials(name) {
  if (!name) return 'KH';
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (!words.length) return 'KH';
  return words.map(w => w[0]).slice(0, 2).join('').toUpperCase();
}

function renderHeaderIdentity(user) {
  const nameEl = document.getElementById('header-user-name');
  if (nameEl) nameEl.textContent = user.fullName || 'Khách hàng';
  const avatarEl = document.getElementById('header-user-avatar');
  if (avatarEl) avatarEl.textContent = initials(user.fullName);
}

function renderCard(o) {
  const meta = statusMeta(o.status);
  const route = `${escapeHTML(o.pickup_district || '—')} → ${escapeHTML(o.dropoff_district || '—')}`;
  const when = formatDateTimeVN(o.scheduled_at);
  const amount = (o.total_quote === null || o.total_quote === undefined)
    ? '—' : formatVnd(o.total_quote);
  return `
    <a class="order-card" href="order-detail.html?id=${encodeURIComponent(o.id)}">
      <div>
        <span class="badge ${meta.badge}">${meta.label}</span>
        <h2>${escapeHTML(o.order_code)}</h2>
        <p class="route">${route}</p>
        <p class="text-muted">${when}</p>
      </div>
      <strong>${amount}</strong>
    </a>`;
}

async function loadOrders(scope) {
  const loadingEl = document.getElementById('orders-loading');
  const errorEl = document.getElementById('orders-error');
  const emptyEl = document.getElementById('orders-empty');
  const listEl = document.getElementById('orders-list');

  loadingEl.style.display = 'flex';
  errorEl.style.display = 'none';
  emptyEl.style.display = 'none';
  listEl.style.display = 'none';

  try {
    const pageData = await getAuthenticated(
      `/api/customer/orders?scope=${encodeURIComponent(scope)}&page=0&size=50`);
    const items = (pageData && pageData.content) ? pageData.content : [];

    loadingEl.style.display = 'none';

    if (items.length === 0) {
      emptyEl.style.display = 'block';
      return;
    }

    listEl.innerHTML = items.map(renderCard).join('');
    listEl.style.display = 'grid';
  } catch (err) {
    console.error(err);
    loadingEl.style.display = 'none';
    errorEl.style.display = 'block';
    const msgEl = document.getElementById('orders-error-message');
    if (msgEl) {
      msgEl.textContent = err.message || 'Không thể tải danh sách đơn hàng. Vui lòng thử lại.';
    }
  }
}

/**
 * Khoi tao trang danh sach don.
 * @param {string} scope - 'pending' hoac 'history'
 */
export function initCustomerOrdersPage(scope) {
  document.addEventListener('DOMContentLoaded', () => {
    // Guard: chua dang nhap → ve trang login
    if (!isLoggedIn()) {
      window.location.href = '../login.html';
      return;
    }
    const user = getCurrentUser();
    if (user?.role !== 'CUSTOMER') {
      logout('../login.html');
      return;
    }

    renderHeaderIdentity(user);
    document.getElementById('logout-btn')?.addEventListener('click', () => logout('../login.html'));
    document.getElementById('orders-retry-btn')?.addEventListener('click', () => loadOrders(scope));

    loadOrders(scope);
  });
}
