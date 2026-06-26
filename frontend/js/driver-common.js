import { isLoggedIn, getCurrentUser, logout } from './auth.js';

// CSS styles to inject
const NOTIFICATION_STYLES = `
/* ============================================================
   CSS CHO CHUÔNG THÔNG BÁO VÀ DROPDOWN (notif-*) - DRIVER
   ============================================================ */
.notif-bell-container {
  position: relative;
  display: inline-block;
  margin-right: var(--spacing-sm, 8px);
}

.notif-bell-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: var(--spacing-xs, 6px);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-ink, #1A1A1A);
  border-radius: var(--rounded-pill, 999px);
  transition: background-color var(--transition-fast, 150ms);
  width: 38px;
  height: 38px;
}

.notif-bell-btn:hover {
  background-color: var(--color-canvas-soft, #F4F5F4);
}

.notif-bell-btn svg {
  width: 22px;
  height: 22px;
}

.notif-badge {
  position: absolute;
  top: 0px;
  right: 0px;
  background-color: var(--color-danger, #DC2626);
  color: white;
  font-size: 10px;
  font-weight: var(--font-weight-bold, 700);
  height: 16px;
  min-width: 16px;
  border-radius: var(--rounded-pill, 999px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
  box-shadow: 0 0 0 2px var(--color-canvas, #FFFFFF);
}

.notif-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: var(--spacing-sm, 8px);
  width: 340px;
  background-color: var(--color-canvas, #FFFFFF);
  border-radius: var(--rounded-xl, 16px);
  box-shadow: var(--shadow-level-2, rgba(0, 0, 0, 0.12) 0px 4px 16px 0px);
  display: none;
  flex-direction: column;
  z-index: var(--z-dropdown, 1000);
  border: 1px solid var(--color-surface-pressed, #E5E7EB);
  overflow: hidden;
  animation: notif-slide-up 200ms ease;
}

.notif-dropdown.show {
  display: flex;
}

@keyframes notif-slide-up {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.notif-dropdown-header {
  padding: var(--spacing-md, 12px) var(--spacing-lg, 16px);
  border-bottom: 1px solid var(--color-surface-pressed, #E5E7EB);
  background-color: var(--color-canvas-soft, #F4F5F4);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.notif-dropdown-header h3 {
  font-size: var(--font-size-body-sm, 14px);
  font-weight: var(--font-weight-bold, 700);
  margin: 0;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.notif-dropdown-list {
  max-height: 320px;
  overflow-y: auto;
}

.notif-dropdown-item {
  display: flex;
  gap: var(--spacing-md, 12px);
  padding: var(--spacing-md, 12px) var(--spacing-lg, 16px);
  border-bottom: 1px solid var(--color-surface-pressed, #E5E7EB);
  color: var(--color-ink, #1A1A1A);
  text-decoration: none;
  transition: background-color var(--transition-fast, 150ms);
}

.notif-dropdown-item:hover {
  background-color: var(--color-canvas-soft, #F4F5F4);
  text-decoration: none;
}

.notif-dropdown-item.unread {
  background-color: var(--color-canvas-softer, #FAFAF9);
}

.notif-dropdown-dot-col {
  width: 6px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.notif-dropdown-dot {
  width: 6px;
  height: 6px;
  border-radius: var(--rounded-full, 9999px);
  background-color: var(--color-accent, #F5A623);
}

.notif-dropdown-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.notif-dropdown-title {
  font-size: var(--font-size-body-sm, 14px);
  color: var(--color-ink, #1A1A1A);
  margin: 0;
  font-weight: 400;
  line-height: 1.3;
}

.notif-dropdown-item.unread .notif-dropdown-title {
  font-weight: var(--font-weight-bold, 700);
}

.notif-dropdown-time {
  font-size: var(--font-size-caption, 12px);
  color: var(--color-body, #5E5E5E);
}

.notif-dropdown-footer {
  padding: var(--spacing-md, 12px);
  text-align: center;
  background-color: var(--color-canvas-soft, #F4F5F4);
  border-top: 1px solid var(--color-surface-pressed, #E5E7EB);
}

.notif-see-all {
  font-size: var(--font-size-body-sm, 14px);
  color: var(--color-primary, #1B4D3E);
  font-weight: var(--font-weight-medium, 500);
  text-decoration: none;
}

.notif-see-all:hover {
  text-decoration: underline;
}

.hidden {
  display: none !important;
}
`;

const STORAGE_KEY = 'driver_notifications';

/* MOCK - thay bằng API sau */
const DEFAULT_NOTIFICATIONS = [
  {
    id: 1,
    type: 'ORDER_ASSIGNED',
    title: 'Đơn hàng mới được phân công',
    message: 'Bạn được phân công thực hiện đơn hàng MH2026060005 (Ba Đình → Cầu Giấy). Vui lòng xác nhận nhận đơn.',
    isRead: false,
    createdAt: '2026-06-23T02:45:00Z',
    timeText: '30 phút trước'
  },
  {
    id: 2,
    type: 'ORDER_CANCELLED',
    title: 'Đơn hàng bị hủy',
    message: 'Rất tiếc, đơn hàng MH2026060003 (Thanh Xuân → Hà Đông) đã bị hủy bởi khách hàng.',
    isRead: false,
    createdAt: '2026-06-23T01:30:00Z',
    timeText: '2 giờ trước'
  },
  {
    id: 3,
    type: 'WITHDRAWAL_APPROVED',
    title: 'Yêu cầu rút tiền được duyệt',
    message: 'Yêu cầu rút 500.000đ của bạn đã được duyệt thành công. Tiền đã được chuyển vào tài khoản ngân hàng liên kết.',
    isRead: false,
    createdAt: '2026-06-22T21:00:00Z',
    timeText: '6 giờ trước'
  },
  {
    id: 4,
    type: 'ORDER_COMPLETED',
    title: 'Đơn hàng hoàn thành',
    message: 'Bạn đã hoàn thành đơn hàng MH2026050028 (Đống Đa → Hoàn Kiếm). Doanh thu 686.000đ đã được ghi nhận.',
    isRead: true,
    createdAt: '2026-06-22T15:30:00Z',
    timeText: '1 ngày trước'
  },
  {
    id: 5,
    type: 'DEPOSIT_PAID',
    title: 'Nộp tiền cọc thành công',
    message: 'Yêu cầu đặt cọc 3.000.000đ đã được xác nhận. Tài khoản tài xế của bạn hiện đã đủ điều kiện nhận đơn.',
    isRead: true,
    createdAt: '2026-06-21T09:00:00Z',
    timeText: '2 ngày trước'
  }
];

export function getNotifications() {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (!stored) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(DEFAULT_NOTIFICATIONS));
    return DEFAULT_NOTIFICATIONS;
  }
  try {
    return JSON.parse(stored);
  } catch {
    return DEFAULT_NOTIFICATIONS;
  }
}

export function saveNotifications(data) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
}

document.addEventListener('DOMContentLoaded', () => {
  // 1. Inject Styles
  const styleEl = document.createElement('style');
  styleEl.textContent = NOTIFICATION_STYLES;
  document.head.appendChild(styleEl);

  // 2. Auth Check & Header Init
  const loggedIn = isLoggedIn();
  const user = getCurrentUser();

  // Update name, avatar and logout logic on the page dynamically if user is logged in
  if (loggedIn && user && user.role === 'DRIVER') {
    // Try to find user name elements
    const nameSpan = document.getElementById('userName');
    if (nameSpan) {
      nameSpan.textContent = user.fullName || '';
    } else {
      const welcomeSpan = document.querySelector('.user-menu-name');
      if (welcomeSpan) {
        welcomeSpan.innerHTML = `Xin chào, <span id="userName">${user.fullName || ''}</span>`;
      }
    }

    // Try to find avatar elements
    const avatarDiv = document.getElementById('userAvatar') || document.querySelector('.avatar');
    if (avatarDiv && user.fullName) {
      if (!avatarDiv.id) avatarDiv.id = 'userAvatar';
      avatarDiv.textContent = user.fullName
        .split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
      avatarDiv.className = 'avatar avatar-sm avatar-initials';
    }

    // Try to find logout button and bind click event if it's not home.html (since home.html handles its own)
    const logoutBtn = document.getElementById('logoutBtn') || document.querySelector('.site-header-actions a[href="../login.html"]');
    if (logoutBtn) {
      if (!logoutBtn.id) logoutBtn.id = 'logoutBtn';
      if (window.location.pathname.indexOf('home.html') === -1) {
        logoutBtn.addEventListener('click', (e) => {
          e.preventDefault();
          logout('../login.html');
        });
      }
    }
  }

  // 3. Inject Bell Icon & Dropdown HTML inside .site-header-actions
  const headerActions = document.querySelector('.site-header-actions');
  if (headerActions) {
    // Create the container element for bell
    const bellContainer = document.createElement('div');
    bellContainer.className = 'notif-bell-container';
    bellContainer.innerHTML = `
      <button class="notif-bell-btn" id="notifBellBtn" aria-label="Thông báo">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
        </svg>
        <span class="notif-badge" id="notifCountBadge">3</span>
      </button>
      <div class="notif-dropdown" id="notifDropdown">
        <div class="notif-dropdown-header">
          <h3>Thông báo mới</h3>
        </div>
        <div class="notif-dropdown-list" id="notifDropdownList">
          <!-- Render dropdown items dynamically -->
        </div>
        <div class="notif-dropdown-footer">
          <a href="notifications.html" class="notif-see-all">Xem tất cả thông báo</a>
        </div>
      </div>
    `;
    
    // Insert before the user-menu-name span or as the first child of site-header-actions
    headerActions.insertBefore(bellContainer, headerActions.firstChild);

    // Initial load and render
    initBellDropdown();
  }
});

function initBellDropdown() {
  const bellBtn = document.getElementById('notifBellBtn');
  const dropdown = document.getElementById('notifDropdown');
  
  if (!bellBtn || !dropdown) return;

  // Toggle Dropdown
  bellBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    dropdown.classList.toggle('show');
    renderDropdown();
  });

  // Close when click outside
  document.addEventListener('click', (e) => {
    if (!dropdown.contains(e.target) && e.target !== bellBtn) {
      dropdown.classList.remove('show');
    }
  });

  // Render initial badge & dropdown items
  renderDropdown();
  updateBadge();
}

const BASE_URL = 'http://localhost:8080';

function formatTimeVN(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now - date;
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  
  if (diffMins < 1) {
    return 'Vừa xong';
  } else if (diffMins < 60) {
    return `${diffMins} phút trước`;
  } else if (diffHours < 24) {
    return `${diffHours} giờ trước`;
  } else {
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const mins = String(date.getMinutes()).padStart(2, '0');
    return `${day}/${month} ${hours}:${mins}`;
  }
}

function escapeHTML(str) {
  if (!str) return '';
  return str.replace(/[&<>'"]/g, 
    tag => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      "'": '&#39;',
      '"': '&quot;'
    }[tag] || tag)
  );
}

export async function updateBadge() {
  const token = localStorage.getItem('accessToken');
  if (!token) return;
  try {
    const res = await fetch(`${BASE_URL}/api/notifications?page=0&size=5`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    if (!res.ok) return;
    const pageData = await res.json();
    const content = pageData.content || [];
    const unreadCount = content.filter(item => !item.isRead).length;
    const badge = document.getElementById('notifCountBadge');
    if (badge) {
      badge.textContent = unreadCount;
      badge.classList.toggle('hidden', unreadCount === 0);
    }
  } catch (err) {
    console.error(err);
  }
}

export async function renderDropdown() {
  const dropdownList = document.getElementById('notifDropdownList');
  if (!dropdownList) return;

  const token = localStorage.getItem('accessToken');
  if (!token) return;
  try {
    const res = await fetch(`${BASE_URL}/api/notifications?page=0&size=5`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    if (!res.ok) return;
    const pageData = await res.json();
    const content = pageData.content || [];
    
    if (content.length === 0) {
      dropdownList.innerHTML = `<div style="padding: 16px; text-align: center; color: var(--color-body, #5E5E5E); font-size: var(--font-size-body-sm, 14px);">Không có thông báo mới</div>`;
      return;
    }

    dropdownList.innerHTML = content.map(item => {
      const unreadClass = item.isRead ? '' : 'unread';
      const dotMarkup = item.isRead ? '<span class="notif-dropdown-dot" style="visibility:hidden"></span>' : '<span class="notif-dropdown-dot"></span>';
      const timeFormatted = formatTimeVN(item.createdAt);
      
      return `
        <a href="notifications.html" class="notif-dropdown-item ${unreadClass}" data-id="${item.id}">
          <div class="notif-dropdown-dot-col">
            ${dotMarkup}
          </div>
          <div class="notif-dropdown-body">
            <h4 class="notif-dropdown-title">${escapeHTML(item.title)}</h4>
            <span class="notif-dropdown-time">${timeFormatted}</span>
          </div>
        </a>
      `;
    }).join('');

    // Add click listener inside dropdown to mark single item as read when clicked
    dropdownList.querySelectorAll('.notif-dropdown-item').forEach(el => {
      el.addEventListener('click', async (e) => {
        const id = el.getAttribute('data-id');
        const wasUnread = el.classList.contains('unread');
        if (wasUnread) {
          // Optimistic UI update
          el.classList.remove('unread');
          const dot = el.querySelector('.notif-dropdown-dot');
          if (dot) dot.style.visibility = 'hidden';
          
          try {
            const res = await fetch(`${BASE_URL}/api/notifications/${id}/read`, {
              method: 'PATCH',
              headers: {
                'Authorization': `Bearer ${token}`
              }
            });
            if (res.ok) {
              updateBadge();
            }
          } catch (err) {
            console.error(err);
          }
        }
      });
    });
  } catch (err) {
    console.error(err);
  }
}
