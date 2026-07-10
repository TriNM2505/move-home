import { isLoggedIn, getCurrentUser, logout } from './auth.js';

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

function getInitialsFromName(name) {
  if (!name) return 'KH';
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (!words.length) return 'KH';
  return words.map(w => w[0]).slice(0, 2).join('').toUpperCase();
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

const NOTIFICATION_STYLES = `
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
  box-shadow: var(--shadow-level-2, 0 4px 12px rgba(0,0,0,0.08));
  display: none;
  flex-direction: column;
  z-index: var(--z-dropdown, 100);
  border: 1px solid var(--color-surface-pressed, #E4E5E4);
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
  border-bottom: 1px solid var(--color-surface-pressed, #E4E5E4);
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
  border-bottom: 1px solid var(--color-surface-pressed, #E4E5E4);
  color: var(--color-ink, #1A1A1A);
  text-decoration: none;
  transition: background-color var(--transition-fast, 150ms);
}

.notif-dropdown-item:hover {
  background-color: var(--color-canvas-soft, #F4F5F4);
  text-decoration: none;
}

.notif-dropdown-item.unread {
  background-color: var(--color-canvas-softer, #FAFAFA);
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
  border-radius: var(--rounded-full, 999px);
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
  border-top: 1px solid var(--color-surface-pressed, #E4E5E4);
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
`;

document.addEventListener('DOMContentLoaded', () => {
  // 1. Guard check and redirect if not logged in
  if (!isLoggedIn()) {
    if (window.location.pathname.indexOf('login.html') === -1 && window.location.pathname.indexOf('register.html') === -1) {
      window.location.href = '../login.html';
      return;
    }
  }

  const user = getCurrentUser();
  if (user && user.role === 'CUSTOMER') {
    // Update Name
    const userNameEl = document.getElementById('userName') || document.querySelector('.user-menu-name');
    if (userNameEl) {
      if (userNameEl.id === 'userName') {
        userNameEl.textContent = user.fullName || 'Khách hàng';
      } else {
        userNameEl.innerHTML = `Xin chào, <span id="userName">${user.fullName || 'Khách hàng'}</span>`;
      }
    }
    // Update Avatar — anh that tu Cloudinary neu co avatarUrl, fallback chu cai dau
    const avatarEl = document.getElementById('userAvatar') || document.querySelector('.avatar');
    if (avatarEl) {
      if (!avatarEl.id) avatarEl.id = 'userAvatar';
      avatarEl.className = 'avatar avatar-sm avatar-initials';
      if (user.avatarUrl) {
        avatarEl.style.overflow = 'hidden';
        avatarEl.innerHTML = '';
        const img = document.createElement('img');
        img.src = user.avatarUrl;
        img.alt = 'Ảnh đại diện';
        img.style.cssText = 'width:100%;height:100%;object-fit:cover;display:block;border-radius:inherit;';
        // Anh loi (URL hong) -> quay ve chu cai dau
        img.onerror = () => { avatarEl.textContent = getInitialsFromName(user.fullName); };
        avatarEl.appendChild(img);
      } else if (user.fullName) {
        avatarEl.textContent = getInitialsFromName(user.fullName);
      }
    }
  }

  // Bind Logout
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', (e) => {
      e.preventDefault();
      logout('../login.html');
    });
  }

  // 1.5 Inject styles and HTML for bell
  const headerActions = document.querySelector('.site-header-actions');
  if (headerActions && !document.getElementById('notif-bell-container')) {
    // Inject styles dynamically if not present
    if (!document.getElementById('notif-bell-styles')) {
      const styleEl = document.createElement('style');
      styleEl.id = 'notif-bell-styles';
      styleEl.textContent = NOTIFICATION_STYLES;
      document.head.appendChild(styleEl);
    }

    const bellContainer = document.createElement('div');
    bellContainer.className = 'notif-bell-container';
    bellContainer.id = 'notif-bell-container';
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
    headerActions.insertBefore(bellContainer, headerActions.firstChild);
  }

  // 2. Notifications Bell and Dropdown logic
  initBellDropdown();
});

function initBellDropdown() {
  const bellBtn = document.getElementById('notifBellBtn');
  const dropdown = document.getElementById('notifDropdown');
  const badge = document.getElementById('notifCountBadge');
  const dropdownList = document.getElementById('notifDropdownList');
  
  if (!bellBtn || !dropdown) return;
  
  const BASE_URL = 'http://localhost:8080';
  const token = localStorage.getItem('accessToken');
  
  async function loadNotifications() {
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
      
      // Update badge
      const unreadCount = content.filter(item => !item.isRead).length;
      if (badge) {
        badge.textContent = unreadCount;
        badge.classList.toggle('hidden', unreadCount === 0);
      }
      
      // Render dropdown
      if (dropdownList) {
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
        
        // Add click event for each item to mark as read
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
                  loadNotifications();
                }
              } catch (err) {
                console.error(err);
              }
            }
          });
        });
      }
    } catch (err) {
      console.error(err);
    }
  }
  
  // Toggle dropdown
  bellBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    dropdown.classList.toggle('show');
    if (dropdown.classList.contains('show')) {
      loadNotifications();
    }
  });
  
  document.addEventListener('click', (e) => {
    if (!dropdown.contains(e.target) && e.target !== bellBtn) {
      dropdown.classList.remove('show');
    }
  });
  
  // Load notifications initially
  loadNotifications();
}
