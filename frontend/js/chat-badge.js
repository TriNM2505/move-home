// ============================================================
// BADGE "TIN NHAN" tren thanh dieu huong (moi role).
// Tu inject vao .site-header-actions, poll /api/chat/unread-count.
// Bam vao → mo trang chat dung chung (../messages.html).
// KHONG dung du lieu gia. Chi hien khi da dang nhap (co accessToken).
// ============================================================

(function () {
  const BASE_URL = 'http://localhost:8080';

  const STYLES = `
  .chat-badge-container { position: relative; display: inline-block; margin-right: var(--spacing-sm, 8px); }
  .chat-badge-btn {
    background: none; border: none; cursor: pointer; padding: var(--spacing-xs, 6px);
    position: relative; display: flex; align-items: center; justify-content: center;
    color: var(--color-ink, #1A1A1A); border-radius: var(--rounded-pill, 999px);
    transition: background-color 150ms; width: 38px; height: 38px; text-decoration: none;
  }
  .chat-badge-btn:hover { background-color: var(--color-canvas-soft, #F4F5F4); }
  .chat-badge-btn svg { width: 22px; height: 22px; }
  .chat-badge-count {
    position: absolute; top: 0; right: 0; background-color: var(--color-danger, #DC2626);
    color: #fff; font-size: 10px; font-weight: 700; height: 16px; min-width: 16px;
    border-radius: 999px; display: none; align-items: center; justify-content: center;
    padding: 0 4px; box-shadow: 0 0 0 2px var(--color-canvas, #FFFFFF);
  }`;

  async function fetchUnread() {
    const token = localStorage.getItem('accessToken');
    if (!token) return 0;
    try {
      const res = await fetch(`${BASE_URL}/api/chat/unread-count`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (!res.ok) return 0;
      const data = await res.json();
      return data.unreadCount || 0;
    } catch {
      return 0;
    }
  }

  async function refresh() {
    const badge = document.getElementById('chatBadgeCount');
    if (!badge) return;
    const n = await fetchUnread();
    badge.textContent = n > 99 ? '99+' : n;
    badge.style.display = n === 0 ? 'none' : 'flex';
  }

  function inject() {
    const actions = document.querySelector('.site-header-actions');
    if (!actions || document.getElementById('chat-badge-container')) return;
    if (!localStorage.getItem('accessToken')) return;

    if (!document.getElementById('chat-badge-styles')) {
      const s = document.createElement('style');
      s.id = 'chat-badge-styles';
      s.textContent = STYLES;
      document.head.appendChild(s);
    }

    const container = document.createElement('div');
    container.className = 'chat-badge-container';
    container.id = 'chat-badge-container';
    container.innerHTML = `
      <a href="../messages.html" class="chat-badge-btn" aria-label="Tin nhắn" title="Tin nhắn">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
        </svg>
        <span class="chat-badge-count" id="chatBadgeCount" style="display:none">0</span>
      </a>`;
    actions.insertBefore(container, actions.firstChild);

    refresh();
    setInterval(() => { if (!document.hidden) refresh(); }, 15000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  }

  window.refreshChatBadge = refresh;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', inject);
  } else {
    inject();
  }
})();
