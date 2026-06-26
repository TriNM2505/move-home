const initialMockNotifications = [
  { id: "1", type: "INFO", title: "Yêu cầu rút tiền mới", message: "Tài xế Nguyễn Văn Hùng đã gửi yêu cầu rút 4.500.000 đ.", isRead: false, createdAt: new Date(Date.now() - 1000 * 60 * 30).toISOString() },
  { id: "2", type: "WARNING", title: "Hồ sơ tài xế chờ duyệt", message: "Hồ sơ tài xế Trần Minh Đức đang chờ được phê duyệt.", isRead: false, createdAt: new Date(Date.now() - 1000 * 60 * 120).toISOString() },
  { id: "3", type: "DANGER", title: "Khiếu nại từ khách hàng", message: "Khách hàng Phạm Minh Tuấn gửi khiếu nại về đơn hàng MH12345.", isRead: false, createdAt: new Date(Date.now() - 1000 * 60 * 360).toISOString() },
  { id: "4", type: "SUCCESS", title: "Rút tiền thành công", message: "Yêu cầu rút tiền của tài xế Lê Hoàng Nam đã được xử lý xong.", isRead: true, createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString() },
  { id: "5", type: "INFO", title: "Đăng ký tài xế mới", message: "Tài xế Vũ Quốc Đạt đã hoàn thành bước gửi hồ sơ đăng ký.", isRead: true, createdAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 2).toISOString() }
];

function getNotifications() {
  const stored = localStorage.getItem('mockNotifications');
  if (!stored) {
    localStorage.setItem('mockNotifications', JSON.stringify(initialMockNotifications));
    return initialMockNotifications;
  }
  return JSON.parse(stored);
}

function saveNotifications(notifs) {
  localStorage.setItem('mockNotifications', JSON.stringify(notifs));
  window.dispatchEvent(new Event('storage'));
}

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

export function updateBellBadge() {
  const notifs = getNotifications();
  const unreadCount = notifs.filter(n => !n.isRead).length;
  const badge = document.getElementById('notifCountBadge');
  if (badge) {
    badge.textContent = unreadCount;
    badge.style.display = unreadCount === 0 ? 'none' : 'flex';
  }
}

export function renderDropdownList() {
  const dropdownList = document.getElementById('notifDropdownList');
  if (!dropdownList) return;

  const notifs = getNotifications();
  const recentNotifs = notifs.slice(0, 5);
  
  if (recentNotifs.length === 0) {
    dropdownList.innerHTML = `<div style="padding: 16px; text-align: center; color: var(--color-body); font-size: var(--font-size-body-sm);">Không có thông báo mới</div>`;
    return;
  }

  dropdownList.innerHTML = recentNotifs.map(item => {
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

  // Add click handlers inside dropdown
  dropdownList.querySelectorAll('.notif-dropdown-item').forEach(el => {
    el.addEventListener('click', () => {
      const id = el.getAttribute('data-id');
      const notifs = getNotifications();
      const matchedItem = notifs.find(item => item.id === id);
      if (matchedItem && !matchedItem.isRead) {
        matchedItem.isRead = true;
        saveNotifications(notifs);
        updateBellBadge();
      }
    });
  });
}

function injectBellDropdown() {
  const headerActions = document.querySelector('.site-header-actions');
  if (headerActions && !document.getElementById('notif-bell-container')) {
    const bellContainer = document.createElement('div');
    bellContainer.className = 'notif-bell-container';
    bellContainer.id = 'notif-bell-container';
    bellContainer.innerHTML = `
      <button class="notif-bell-btn" id="notifBellBtn" aria-label="Thông báo">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
          <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
        </svg>
        <span class="notif-badge" id="notifCountBadge"></span>
      </button>
      <div class="notif-dropdown" id="notifDropdown">
        <div class="notif-dropdown-header">
          <h3>Thông báo mới</h3>
        </div>
        <div class="notif-dropdown-list" id="notifDropdownList"></div>
        <div class="notif-dropdown-footer">
          <a href="notifications.html" class="notif-see-all">Xem tất cả thông báo</a>
        </div>
      </div>
    `;
    headerActions.insertBefore(bellContainer, headerActions.firstChild);
    
    const bellBtn = document.getElementById('notifBellBtn');
    const dropdown = document.getElementById('notifDropdown');
    
    bellBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      dropdown.classList.toggle('show');
      renderDropdownList();
    });
    
    document.addEventListener('click', (e) => {
      if (!dropdown.contains(e.target) && e.target !== bellBtn) {
        dropdown.classList.remove('show');
      }
    });

    updateBellBadge();
  }
}

window.updateBellNotificationState = () => {
  updateBellBadge();
  renderDropdownList();
};

window.addEventListener('storage', () => {
  updateBellBadge();
  if (document.getElementById('notifDropdown')?.classList.contains('show')) {
    renderDropdownList();
  }
});

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', injectBellDropdown);
} else {
  injectBellDropdown();
}
