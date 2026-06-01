// Module dung chung cho tat ca trang admin:
// - Kiem tra dang nhap, redirect neu chua login
// - Hien ten user tren header
// - Hook nut dang xuat

import { isLoggedIn, getCurrentUser, logout } from './auth.js';

/**
 * Khoi tao trang admin: check auth, set header, hook logout.
 * Goi ham nay ngay dau DOMContentLoaded cua moi trang admin.
 * @param {string} loginRedirectUrl - Duong dan tuong doi den trang login
 * @returns {boolean} true neu da dang nhap hop le, false neu redirect
 */
export function setupAdminPage(loginRedirectUrl = '../login.html') {
  if (!isLoggedIn()) {
    window.location.href = loginRedirectUrl;
    return false;
  }

  const user = getCurrentUser();

  // Hien ten admin tren header
  const nameEl = document.getElementById('header-user-name');
  if (nameEl && user?.fullName) {
    nameEl.textContent = user.fullName;
  }

  // Hien initials trong avatar
  const avatarEl = document.getElementById('header-user-avatar');
  if (avatarEl && user?.fullName) {
    const initials = user.fullName
      .split(' ')
      .map(w => w[0])
      .slice(0, 2)
      .join('')
      .toUpperCase();
    avatarEl.textContent = initials;
  }

  // Hook nut dang xuat
  const logoutBtn = document.getElementById('logout-btn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => logout(loginRedirectUrl));
  }

  return true;
}

// Chuyen doi so tien VND sang dinh dang hien thi (CLAUDE.md §5, AC-08)
export function formatVnd(amount) {
  if (amount === null || amount === undefined) return '—';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(Number(amount));
}

// Chuyen ISO timestamp sang dd/MM/yyyy HH:mm (Asia/Ho_Chi_Minh)
export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

// Chuyen ISO timestamp sang dd/MM/yyyy (khong gio)
export function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

// HTML badge trang thai don hang
const ORDER_STATUS_LABEL = {
  PENDING_PAYMENT:        'Cho thanh toan',
  CONFIRMED:              'Da xac nhan',
  ASSIGNED:               'Da phan cong',
  IN_PROGRESS:            'Dang giao',
  AWAITING_FINAL_PAYMENT: 'Cho tra 70%',
  COMPLETED:              'Hoan thanh',
  CANCELLED:              'Da huy',
  IN_DISPUTE:             'Tranh chap',
};
const ORDER_STATUS_CLASS = {
  PENDING_PAYMENT:        'badge-warning',
  CONFIRMED:              'badge-info',
  ASSIGNED:               'badge-info',
  IN_PROGRESS:            'badge-primary',
  AWAITING_FINAL_PAYMENT: 'badge-warning',
  COMPLETED:              'badge-success',
  CANCELLED:              'badge-neutral',
  IN_DISPUTE:             'badge-danger',
};

export function orderStatusBadge(status) {
  const cls   = ORDER_STATUS_CLASS[status] || 'badge-neutral';
  const label = ORDER_STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// HTML badge trang thai tai xe
const DRIVER_STATUS_LABEL = {
  ACTIVE:               'Dang hoat dong',
  PENDING_APPROVAL:     'Cho duyet',
  PENDING_DOCUMENTS:    'Cho ho so',
  PENDING_VERIFY:       'Cho xac thuc',
  PENDING_DEPOSIT:      'Cho dong coc',
  REJECTED:             'Tu choi',
  SUSPENDED:            'Tam khoa',
};
const DRIVER_STATUS_CLASS = {
  ACTIVE:               'badge-success',
  PENDING_APPROVAL:     'badge-pending-approval',
  PENDING_DOCUMENTS:    'badge-pending-docs',
  PENDING_VERIFY:       'badge-pending-verify',
  PENDING_DEPOSIT:      'badge-warning',
  REJECTED:             'badge-rejected',
  SUSPENDED:            'badge-suspended',
};

export function driverStatusBadge(status) {
  const cls   = DRIVER_STATUS_CLASS[status] || 'badge-neutral';
  const label = DRIVER_STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// HTML badge trang thai khach hang
const CUSTOMER_STATUS_LABEL = {
  ACTIVE:         'Hoat dong',
  PENDING_VERIFY: 'Cho xac thuc',
  SUSPENDED:      'Tam khoa',
};
const CUSTOMER_STATUS_CLASS = {
  ACTIVE:         'badge-success',
  PENDING_VERIFY: 'badge-warning',
  SUSPENDED:      'badge-danger',
};

export function customerStatusBadge(status) {
  const cls   = CUSTOMER_STATUS_CLASS[status] || 'badge-neutral';
  const label = CUSTOMER_STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// Escape HTML de tranh XSS
export function esc(str) {
  if (!str) return '—';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// Hien error alert trong #error-banner
export function showError(msg) {
  const el = document.getElementById('error-banner');
  if (!el) return;
  el.textContent = '⚠️ ' + msg;
  el.style.display = 'flex';
}

export function hideError() {
  const el = document.getElementById('error-banner');
  if (el) el.style.display = 'none';
}
