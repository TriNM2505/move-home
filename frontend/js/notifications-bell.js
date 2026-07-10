// ============================================================
// CHUÔNG THÔNG BÁO DÙNG CHUNG (mọi role: manager, admin, ...)
// Nối API THẬT /api/notifications theo user đang đăng nhập (Bearer token).
// KHÔNG dùng dữ liệu giả. Backend đã có: GET /api/notifications, PATCH /{id}/read.
// Tự inject chuông vào .site-header-actions của trang.
// ============================================================

const BASE_URL = 'http://localhost:8080';

function escapeHTML(str) {
  if (str === null || str === undefined) return '';
  return String(str).replace(/[&<>'"]/g, tag => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[tag] || tag));
}

function formatTimeVN(dateString) {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffMins = Math.floor((now - date) / 60000);
  const diffHours = Math.floor((now - date) / 3600000);
  if (diffMins < 1) return 'Vừa xong';
  if (diffMins < 60) return `${diffMins} phút trước`;
  if (diffHours < 24) return `${diffHours} giờ trước`;
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const mins = String(date.getMinutes()).padStart(2, '0');
  return `${day}/${month} ${hours}:${mins}`;
}

async function fetchNotifications() {
  const token = localStorage.getItem('accessToken');
  if (!token) return [];
  try {
    const res = await fetch(`${BASE_URL}/api/notifications?page=0&size=5`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    if (!res.ok) return [];
    const data = await res.json();
    return data.content || [];
  } catch (err) {
    console.error('Không tải được thông báo:', err);
    return [];
  }
}

async function markNotificationRead(id) {
  const token = localStorage.getItem('accessToken');
  if (!token) return false;
  try {
    const res = await fetch(`${BASE_URL}/api/notifications/${encodeURIComponent(id)}/read`, {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${token}` }
    });
    return res.ok;
  } catch (err) {
    console.error('Không đánh dấu đã đọc được:', err);
    return false;
  }
}

function renderList(listEl, notifs) {
  if (!notifs.length) {
    listEl.innerHTML = `<div style="padding:16px;text-align:center;color:var(--color-body,#5E5E5E);font-size:var(--font-size-body-sm,14px);">Không có thông báo mới</div>`;
    return;
  }
  listEl.innerHTML = notifs.map(n => {
    const unreadClass = n.isRead ? '' : 'unread';
    const dot = n.isRead
      ? '<span class="notif-dropdown-dot" style="visibility:hidden"></span>'
      : '<span class="notif-dropdown-dot"></span>';
    return `
      <a href="notifications.html" class="notif-dropdown-item ${unreadClass}" data-id="${escapeHTML(n.id)}">
        <div class="notif-dropdown-dot-col">${dot}</div>
        <div class="notif-dropdown-body">
          <h4 class="notif-dropdown-title">${escapeHTML(n.title)}</h4>
          <span class="notif-dropdown-time">${formatTimeVN(n.createdAt)}</span>
        </div>
      </a>`;
  }).join('');

  listEl.querySelectorAll('.notif-dropdown-item').forEach(el => {
    el.addEventListener('click', async () => {
      if (!el.classList.contains('unread')) return;
      // Cập nhật giao diện lạc quan rồi gọi API mark-read (link vẫn điều hướng bình thường)
      el.classList.remove('unread');
      const d = el.querySelector('.notif-dropdown-dot');
      if (d) d.style.visibility = 'hidden';
      if (await markNotificationRead(el.getAttribute('data-id'))) {
        refreshBell();
      }
    });
  });
}

async function refreshBell() {
  const notifs = await fetchNotifications();
  const unread = notifs.filter(n => !n.isRead).length;
  const badge = document.getElementById('notifCountBadge');
  if (badge) {
    badge.textContent = unread;
    badge.style.display = unread === 0 ? 'none' : 'flex';
  }
  const listEl = document.getElementById('notifDropdownList');
  if (listEl) renderList(listEl, notifs);
}

const NOTIFICATION_STYLES = `
.notif-bell-container { position: relative; display: inline-block; margin-right: var(--spacing-sm, 8px); }
.notif-bell-btn { background: none; border: none; cursor: pointer; padding: var(--spacing-xs, 6px); position: relative; display: flex; align-items: center; justify-content: center; color: var(--color-ink, #1A1A1A); border-radius: var(--rounded-pill, 999px); transition: background-color var(--transition-fast, 150ms); width: 38px; height: 38px; }
.notif-bell-btn:hover { background-color: var(--color-canvas-soft, #F4F5F4); }
.notif-bell-btn svg { width: 22px; height: 22px; }
.notif-badge { position: absolute; top: 0; right: 0; background-color: var(--color-danger, #DC2626); color: white; font-size: 10px; font-weight: var(--font-weight-bold, 700); height: 16px; min-width: 16px; border-radius: var(--rounded-pill, 999px); display: flex; align-items: center; justify-content: center; padding: 0 4px; box-shadow: 0 0 0 2px var(--color-canvas, #FFFFFF); }
.notif-dropdown { position: absolute; top: 100%; right: 0; margin-top: var(--spacing-sm, 8px); width: 340px; background-color: var(--color-canvas, #FFFFFF); border-radius: var(--rounded-xl, 16px); box-shadow: var(--shadow-level-2, 0 4px 12px rgba(0,0,0,0.08)); display: none; flex-direction: column; z-index: var(--z-dropdown, 1000); border: 1px solid var(--color-surface-pressed, #E4E5E4); overflow: hidden; animation: notif-slide-up 200ms ease; }
.notif-dropdown.show { display: flex; }
@keyframes notif-slide-up { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.notif-dropdown-header { padding: var(--spacing-md, 12px) var(--spacing-lg, 16px); border-bottom: 1px solid var(--color-surface-pressed, #E4E5E4); background-color: var(--color-canvas-soft, #F4F5F4); display: flex; align-items: center; justify-content: space-between; }
.notif-dropdown-header h3 { font-size: var(--font-size-body-sm, 14px); font-weight: var(--font-weight-bold, 700); margin: 0; text-transform: uppercase; letter-spacing: 0.05em; }
.notif-dropdown-list { max-height: 320px; overflow-y: auto; }
.notif-dropdown-item { display: flex; gap: var(--spacing-md, 12px); padding: var(--spacing-md, 12px) var(--spacing-lg, 16px); border-bottom: 1px solid var(--color-surface-pressed, #E4E5E4); color: var(--color-ink, #1A1A1A); text-decoration: none; transition: background-color var(--transition-fast, 150ms); }
.notif-dropdown-item:hover { background-color: var(--color-canvas-soft, #F4F5F4); text-decoration: none; }
.notif-dropdown-item.unread { background-color: var(--color-canvas-softer, #FAFAFA); }
.notif-dropdown-dot-col { width: 6px; display: flex; align-items: center; flex-shrink: 0; }
.notif-dropdown-dot { width: 6px; height: 6px; border-radius: var(--rounded-full, 999px); background-color: var(--color-accent, #F5A623); }
.notif-dropdown-body { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.notif-dropdown-title { font-size: var(--font-size-body-sm, 14px); color: var(--color-ink, #1A1A1A); margin: 0; font-weight: 400; line-height: 1.3; }
.notif-dropdown-item.unread .notif-dropdown-title { font-weight: var(--font-weight-bold, 700); }
.notif-dropdown-time { font-size: var(--font-size-caption, 12px); color: var(--color-body, #5E5E5E); }
.notif-dropdown-footer { padding: var(--spacing-md, 12px); text-align: center; background-color: var(--color-canvas-soft, #F4F5F4); border-top: 1px solid var(--color-surface-pressed, #E4E5E4); }
.notif-see-all { font-size: var(--font-size-body-sm, 14px); color: var(--color-primary, #1B4D3E); font-weight: var(--font-weight-medium, 500); text-decoration: none; }
.notif-see-all:hover { text-decoration: underline; }
`;

function injectBellDropdown() {
  const headerActions = document.querySelector('.site-header-actions');
  if (!headerActions || document.getElementById('notif-bell-container')) return;
  // Chỉ hiện chuông khi đã đăng nhập (có token)
  if (!localStorage.getItem('accessToken')) return;

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
      <span class="notif-badge" id="notifCountBadge" style="display:none"></span>
    </button>
    <div class="notif-dropdown" id="notifDropdown">
      <div class="notif-dropdown-header"><h3>Thông báo mới</h3></div>
      <div class="notif-dropdown-list" id="notifDropdownList"></div>
      <div class="notif-dropdown-footer">
        <a href="notifications.html" class="notif-see-all">Xem tất cả thông báo</a>
      </div>
    </div>`;
  headerActions.insertBefore(bellContainer, headerActions.firstChild);

  const bellBtn = document.getElementById('notifBellBtn');
  const dropdown = document.getElementById('notifDropdown');
  bellBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    dropdown.classList.toggle('show');
    if (dropdown.classList.contains('show')) refreshBell();
  });
  document.addEventListener('click', (e) => {
    if (!dropdown.contains(e.target) && e.target !== bellBtn) dropdown.classList.remove('show');
  });

  refreshBell();
}

// Cho phép các trang khác (vd trang "Xem tất cả thông báo") refresh lại badge sau khi mark-read.
// Giữ tên cũ updateBellNotificationState để tương thích code sẵn có.
window.updateBellNotificationState = refreshBell;
window.refreshNotifBell = refreshBell;

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', injectBellDropdown);
} else {
  injectBellDropdown();
}
