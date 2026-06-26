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
    // Update Avatar
    const avatarEl = document.getElementById('userAvatar') || document.querySelector('.avatar');
    if (avatarEl && user.fullName) {
      if (!avatarEl.id) avatarEl.id = 'userAvatar';
      avatarEl.textContent = user.fullName
        .split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase();
      avatarEl.className = 'avatar avatar-sm avatar-initials';
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
