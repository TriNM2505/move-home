// Module dùng chung cho tất cả trang admin:
// - Kiểm tra đăng nhập, redirect nếu chưa login
// - Hiển thị tên user trên header
// - Hook nút đăng xuất
// - Format helpers, status mappers, UI helpers

import { isLoggedIn, getCurrentUser, logout as authLogout } from './auth.js';

// ============================================================
// SETUP — khởi tạo trang admin (gọi trong DOMContentLoaded)
// ============================================================

/**
 * Khởi tạo trang admin: kiểm tra auth, set header, hook logout.
 * Gọi hàm này ngay đầu DOMContentLoaded của mỗi trang admin.
 * @param {string} loginRedirectUrl - Đường dẫn tương đối đến trang login
 * @returns {boolean} true nếu đã đăng nhập hợp lệ, false nếu redirect
 */
export function setupAdminPage(loginRedirectUrl = '../login.html') {
  if (!isLoggedIn()) {
    window.location.href = loginRedirectUrl;
    return false;
  }

  const user = getCurrentUser();

  // Hiển thị tên admin trên header
  const nameEl = document.getElementById('header-user-name');
  if (nameEl && user?.fullName) {
    nameEl.textContent = user.fullName;
  }

  // Hiển thị initials trong avatar
  const avatarEl = document.getElementById('header-user-avatar');
  if (avatarEl && user?.fullName) {
    avatarEl.textContent = user.fullName
      .split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
  }

  // Hook nút đăng xuất
  document.getElementById('logout-btn')
    ?.addEventListener('click', () => logout(loginRedirectUrl));

  return true;
}

// ============================================================
// LOGOUT
// ============================================================

/**
 * Đăng xuất: xóa token và redirect về login.
 * @param {string} loginUrl - Đường dẫn tương đối đến trang login
 */
export function logout(loginUrl = '../login.html') {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('userInfo');
  window.location.href = loginUrl;
}

// ============================================================
// FORMAT HELPERS
// ============================================================

/**
 * Format số tiền VND — "1.500.000 VND" (CLAUDE.md §5 AC-08)
 */
export function formatVND(amount) {
  if (amount === null || amount === undefined) return '—';
  return new Intl.NumberFormat('vi-VN').format(Number(amount)) + ' VND';
}

/** Alias giữ backward compat */
export const formatVnd = formatVND;

/**
 * Format ISO timestamp → "dd/MM/yyyy HH:mm" timezone Hồ Chí Minh (AC-07)
 */
export function formatDateTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

/**
 * Format ISO date → "dd/MM/yyyy" (không giờ)
 */
export function formatDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

// ============================================================
// STATUS MAPPERS — 6 trạng thái từ dashboard overview API
// ============================================================
const DASHBOARD_STATUS_VN = {
  PENDING:     'Đang chờ',
  ACCEPTED:    'Đã nhận đơn',
  IN_PROGRESS: 'Đang giao',
  COMPLETED:   'Hoàn thành',
  CANCELLED:   'Đã hủy',
  DISPUTED:    'Khiếu nại',
};

const DASHBOARD_STATUS_CLASS = {
  PENDING:     'badge-pending',
  ACCEPTED:    'badge-active',
  IN_PROGRESS: 'badge-in-progress',
  COMPLETED:   'badge-completed',
  CANCELLED:   'badge-cancelled',
  DISPUTED:    'badge-disputed',
};

/**
 * Chuyển status DB → chuỗi tiếng Việt có dấu.
 * Ví dụ: PENDING → "Đang chờ"
 */
export function statusToVietnamese(status) {
  return DASHBOARD_STATUS_VN[status]
    || ORDER_STATUS_LABEL[status]
    || DRIVER_STATUS_LABEL[status]
    || status;
}

/**
 * Trả về tên CSS class badge theo status.
 * Ví dụ: PENDING → "badge-pending"
 */
export function statusBadgeClass(status) {
  return DASHBOARD_STATUS_CLASS[status]
    || ORDER_STATUS_CLASS[status]
    || DRIVER_STATUS_CLASS[status]
    || 'badge-neutral';
}

// ============================================================
// ORDER STATUS MAPPING — đầy đủ 8 trạng thái cho list pages
// ============================================================
const ORDER_STATUS_LABEL = {
  PENDING_PAYMENT:        'Chờ thanh toán',
  CONFIRMED:              'Đã xác nhận',
  ASSIGNED:               'Đã phân công',
  IN_PROGRESS:            'Đang giao',
  AWAITING_FINAL_PAYMENT: 'Chờ trả 70%',
  COMPLETED:              'Hoàn thành',
  CANCELLED:              'Đã hủy',
  IN_DISPUTE:             'Tranh chấp',
};

const ORDER_STATUS_CLASS = {
  PENDING_PAYMENT:        'badge-warning',
  CONFIRMED:              'badge-info',
  ASSIGNED:               'badge-info',
  IN_PROGRESS:            'badge-in-progress',
  AWAITING_FINAL_PAYMENT: 'badge-warning',
  COMPLETED:              'badge-completed',
  CANCELLED:              'badge-cancelled',
  IN_DISPUTE:             'badge-disputed',
};

export function orderStatusBadge(status) {
  const cls   = ORDER_STATUS_CLASS[status] || 'badge-neutral';
  const label = ORDER_STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// ============================================================
// DRIVER STATUS MAPPING
// ============================================================
const DRIVER_STATUS_LABEL = {
  ACTIVE:            'Đang hoạt động',
  PENDING_APPROVAL:  'Chờ duyệt',
  PENDING_DOCUMENTS: 'Chờ hồ sơ',
  PENDING_VERIFY:    'Chờ xác thực',
  PENDING_DEPOSIT:   'Chờ đặt cọc',
  REJECTED:          'Bị từ chối',
  SUSPENDED:         'Tạm khóa',
};

const DRIVER_STATUS_CLASS = {
  ACTIVE:            'badge-success',
  PENDING_APPROVAL:  'badge-pending-approval',
  PENDING_DOCUMENTS: 'badge-pending-docs',
  PENDING_VERIFY:    'badge-pending-verify',
  PENDING_DEPOSIT:   'badge-warning',
  REJECTED:          'badge-rejected',
  SUSPENDED:         'badge-suspended',
};

export function driverStatusBadge(status) {
  const cls   = DRIVER_STATUS_CLASS[status] || 'badge-neutral';
  const label = DRIVER_STATUS_LABEL[status] || status;
  return `<span class="badge badge-sm ${cls}">${label}</span>`;
}

// ============================================================
// CUSTOMER STATUS MAPPING
// ============================================================
const CUSTOMER_STATUS_LABEL = {
  ACTIVE:         'Đang hoạt động',
  PENDING_VERIFY: 'Chờ xác thực email',
  SUSPENDED:      'Tạm khóa',
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

// ============================================================
// API HELPER — fetch có Authorization header + xử lý 401
// ============================================================

/**
 * Gọi API với Bearer token từ localStorage.
 * Tự redirect về login nếu 401/403.
 * @param {string} url - URL tuyệt đối hoặc path API
 * @param {RequestInit} options - Fetch options (method, body, ...)
 */
export async function fetchWithAuth(url, options = {}) {
  const token = localStorage.getItem('accessToken');
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch {
    const err = new Error('Lỗi kết nối mạng. Vui lòng kiểm tra kết nối.');
    err.isNetworkError = true;
    throw err;
  }

  if (response.status === 401 || response.status === 403) {
    logout('../login.html');
    return;
  }

  if (!response.ok) {
    let data = {};
    try { data = await response.json(); } catch {}
    const err = new Error(data.message || `Lỗi HTTP ${response.status}`);
    err.status = response.status;
    err.data   = data;
    throw err;
  }

  const text = await response.text();
  if (!text) return null;
  try { return JSON.parse(text); } catch { return text; }
}

// ============================================================
// UI HELPERS — render empty/error/loading states vào container
// ============================================================

/**
 * Hiển thị empty state trong container (xác định bởi ID hoặc element).
 */
export function renderEmpty(containerId, message = 'Không có dữ liệu để hiển thị') {
  const el = typeof containerId === 'string'
    ? document.getElementById(containerId)
    : containerId;
  if (!el) return;
  el.innerHTML = `
    <div class="empty-state">
      <div class="empty-state-icon" style="display: inline-flex; align-items: center; justify-content: center; color: var(--color-mute);">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
      </div>
      <p class="empty-state-title" style="margin-top: 12px;">${message}</p>
    </div>`;
}

/**
 * Hiển thị error state trong container.
 */
export function renderError(containerId, message = 'Không thể tải dữ liệu. Vui lòng thử lại.') {
  const el = typeof containerId === 'string'
    ? document.getElementById(containerId)
    : containerId;
  if (!el) return;
  el.innerHTML = `
    <div class="alert alert-danger" style="display:flex; align-items:center; gap:12px;">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
      <span>${message}</span>
      <button onclick="location.reload()" class="btn btn-sm btn-danger" style="margin-left:auto; flex-shrink:0;">
        Tải lại
      </button>
    </div>`;
}

/**
 * Hiển thị loading state trong container.
 */
export function renderLoading(containerId, message = 'Đang tải...') {
  const el = typeof containerId === 'string'
    ? document.getElementById(containerId)
    : containerId;
  if (!el) return;
  el.innerHTML = `
    <div class="page-loading">
      <span class="spinner spinner-md"></span>
      <span>${message}</span>
    </div>`;
}

// ============================================================
// ERROR BANNER — hiển thị #error-banner ở đầu trang
// ============================================================

export function showError(msg) {
  const el = document.getElementById('error-banner');
  if (!el) return;
  el.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0; margin-right:8px;"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg><span>${esc(msg)}</span>`;
  el.style.display = 'flex';
  el.style.alignItems = 'center';
}

export function hideError() {
  const el = document.getElementById('error-banner');
  if (el) el.style.display = 'none';
}

// ============================================================
// XSS PREVENTION
// ============================================================

export function esc(str) {
  if (!str) return '—';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ============================================================
// USER STATUS (driver + customer) — tiếng Việt đầy đủ dấu
// ============================================================

const USER_STATUS_VN = {
  ACTIVE:            'Đang hoạt động',
  PENDING_VERIFY:    'Chờ xác thực email',
  PENDING_DOCUMENTS: 'Chờ bổ sung giấy tờ',
  PENDING_DEPOSIT:   'Chờ đặt cọc',
  PENDING_APPROVAL:  'Chờ duyệt',
  SUSPENDED:         'Bị khóa',
  REJECTED:          'Bị từ chối',
};

export function userStatusToVietnamese(status) {
  return USER_STATUS_VN[status] || status;
}

// ============================================================
// PAGINATION — render UI phân trang
// ============================================================

/**
 * Render pagination bar vào container.
 * Dùng data attributes + event listener, KHÔNG dùng inline onclick.
 * @param {string} containerId - ID của DOM element
 * @param {object} pageInfo - { totalElements, totalPages, number, size }
 * @param {function} onPageChange - callback(newPage) — 0-indexed
 * @param {function} onSizeChange - callback(newSize)
 * @param {string} entityLabel - "đơn", "tài xế", "khách hàng"
 */
export function renderPagination(containerId, pageInfo, onPageChange, onSizeChange, entityLabel = 'mục') {
  const container = document.getElementById(containerId);
  if (!container) return;

  const { totalElements, totalPages, number: currentIndex, size } = pageInfo;

  if (!totalElements) { container.innerHTML = ''; return; }

  const currentPage = currentIndex + 1; // 0-indexed → 1-indexed hiển thị
  const startItem   = currentIndex * size + 1;
  const endItem     = Math.min((currentIndex + 1) * size, totalElements);

  let html = `<div class="pagination-bar">
    <div class="pagination-info">
      Hiển thị ${startItem}–${endItem} trong ${totalElements} ${entityLabel}
    </div>`;

  if (totalPages > 1) {
    html += `<div class="pagination-controls">
      <button class="pagination-btn-prev" data-page="${currentIndex - 1}"
              ${currentIndex === 0 ? 'disabled' : ''}>« Trước</button>`;

    computeVisiblePages(currentPage, totalPages).forEach(p => {
      if (p === '...') {
        html += `<span class="pagination-ellipsis">...</span>`;
      } else {
        html += `<button class="pagination-btn-page ${p === currentPage ? 'active' : ''}"
                         data-page="${p - 1}">${p}</button>`;
      }
    });

    html += `
      <button class="pagination-btn-next" data-page="${currentIndex + 1}"
              ${currentIndex === totalPages - 1 ? 'disabled' : ''}>Tiếp »</button>
    </div>`;
  }

  html += `
    <div class="pagination-size-wrapper">
      <span class="pagination-size-label">Số dòng/trang:</span>
      <select class="pagination-size-select" data-size-select>
        ${[10, 20, 50, 100].map(v =>
          `<option value="${v}"${size === v ? ' selected' : ''}>${v}</option>`
        ).join('')}
      </select>
    </div>
  </div>`;

  container.innerHTML = html;

  // Gắn event listeners sau khi render (tránh inline onclick)
  container.querySelectorAll('[data-page]:not([disabled])').forEach(btn => {
    btn.addEventListener('click', () => onPageChange(parseInt(btn.dataset.page, 10)));
  });
  const sizeSelect = container.querySelector('[data-size-select]');
  if (sizeSelect) {
    sizeSelect.addEventListener('change', () => onSizeChange(parseInt(sizeSelect.value, 10)));
  }
}

/**
 * Tính danh sách số trang hiển thị với dấu "..." (ellipsis).
 * Luôn hiển thị: trang đầu, trang cuối, trang hiện tại ± 2.
 */
function computeVisiblePages(current, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const pages = [1];
  if (current > 4) pages.push('...');
  const start = Math.max(2, current - 2);
  const end   = Math.min(total - 1, current + 2);
  for (let i = start; i <= end; i++) pages.push(i);
  if (current < total - 3) pages.push('...');
  pages.push(total);
  return pages;
}

/**
 * Client-side pagination cho List response (drivers, customers).
 * Mock thành Spring Page-like format.
 * @param {Array} items - Toàn bộ danh sách
 * @param {number} page - Trang hiện tại (0-indexed)
 * @param {number} size - Số dòng mỗi trang
 */
export function clientSidePaginate(items, page, size) {
  const totalElements = items.length;
  const totalPages    = Math.max(1, Math.ceil(totalElements / size));
  const safePage      = Math.min(page, totalPages - 1);
  const start         = safePage * size;
  const content       = items.slice(start, start + size);
  return {
    content,
    totalElements,
    totalPages,
    number: safePage,
    size,
    first: safePage === 0,
    last: safePage === totalPages - 1,
  };
}
