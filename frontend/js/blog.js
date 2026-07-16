// ============================================================
// BLOG CONG DONG (Community Wall)
// - Pha A: Guest doc feed; Customer dang review + anh.
// - Pha B: binh luan; Manager tra loi (badge "Quan ly"); notification cho chu bai.
// - Pha C: Manager kiem duyet (An/Xoa bai + An/Xoa binh luan). Rate limit o backend.
// Realtime: REST + poll nhe 25s (khong WebSocket — SHELL, khong dung CORE).
// Tu dong mount vao #community-feed. Brand forest green + amber (HR-19), tieng Viet co dau (HR-20).
// ============================================================

import { getToken, getCurrentUser } from './auth.js';

const BASE_URL = 'http://localhost:8080';
const PAGE_SIZE = 5;
const MAX_PHOTOS = 3;
const POLL_MS = 25000;

const state = {
  root: null,
  listEl: null,
  me: null,          // user hien tai (null neu guest)
  page: 0,
  totalPages: 1,
  loading: false,
  seen: new Set(),   // id bai da hien — chong trung khi poll
  pollTimer: null,
  selectedFiles: [], // File[] cho form dang
  rating: 0,
};

// Customer + Manager duoc binh luan (Manager tra loi). Guest/Driver/Admin chi doc.
function canComment() {
  return state.me && (state.me.role === 'CUSTOMER' || state.me.role === 'MANAGER');
}

// Manager co quyen kiem duyet (An/Xoa).
function isManager() {
  return state.me && state.me.role === 'MANAGER';
}

// ---------- Tien ich ----------
function escapeHTML(str) {
  if (str === null || str === undefined) return '';
  return String(str).replace(/[&<>'"]/g, t => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  }[t] || t));
}

function initials(name) {
  if (!name) return '?';
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (!w.length) return '?';
  return w.map(x => x[0]).slice(0, 2).join('').toUpperCase();
}

// Hien thi thoi gian theo Asia/Ho_Chi_Minh (AC-07: server tra UTC)
function formatTimeVN(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const now = new Date();
  const mins = Math.floor((now - d) / 60000);
  const hours = Math.floor((now - d) / 3600000);
  if (mins < 1) return 'Vừa xong';
  if (mins < 60) return `${mins} phút trước`;
  if (hours < 24) return `${hours} giờ trước`;
  return d.toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function starsHTML(rating) {
  if (!rating) return '';
  let out = '<span class="community-stars" aria-label="Đánh giá ' + rating + ' trên 5 sao">';
  for (let i = 1; i <= 5; i++) {
    out += `<span class="community-star${i <= rating ? ' is-on' : ''}">★</span>`;
  }
  return out + '</span>';
}

// ---------- Style (inject 1 lan) ----------
function injectStyles() {
  if (document.getElementById('community-feed-styles')) return;
  const css = `
  .community-wrap { max-width: 760px; margin: 0 auto; }
  .community-compose {
    background: var(--color-canvas, #fff);
    border: 1px solid var(--color-surface-pressed, #e5e7eb);
    border-radius: var(--rounded-xl, 16px);
    padding: var(--spacing-xl, 20px);
    margin-bottom: var(--spacing-2xl, 24px);
  }
  .community-compose textarea {
    width: 100%; min-height: 84px; resize: vertical;
    border: 1px solid var(--color-surface-pressed, #d1d5db);
    border-radius: 12px; padding: 12px 14px;
    font-family: inherit; font-size: var(--font-size-body-sm, 0.95rem); color: var(--color-ink, #061b31);
    box-sizing: border-box;
  }
  .community-compose textarea:focus { outline: none; border-color: var(--color-primary, #1B4D3E); }
  .community-compose-row { display: flex; align-items: center; gap: 16px; margin-top: 12px; flex-wrap: wrap; }
  .community-rate { display: inline-flex; gap: 2px; cursor: pointer; }
  .community-rate .community-star { font-size: 1.4rem; color: #d1d5db; transition: color .12s; }
  .community-rate .community-star.is-on { color: var(--color-accent, #F5A623); }
  .community-upload-btn, .community-post-btn, .community-more-btn {
    font-family: inherit; cursor: pointer; border-radius: var(--rounded-pill, 999px);
    font-weight: 600; border: 1px solid transparent; padding: 9px 18px; font-size: 0.9rem;
  }
  .community-upload-btn { background: var(--color-canvas-soft, #f3f4f6); color: var(--color-ink, #061b31); border-color: var(--color-surface-pressed, #e5e7eb); }
  .community-post-btn { background: var(--color-primary, #1B4D3E); color: #fff; margin-left: auto; }
  .community-post-btn:disabled { opacity: .55; cursor: not-allowed; }
  .community-previews { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 12px; }
  .community-preview { position: relative; width: 84px; height: 84px; border-radius: 12px; overflow: hidden; border: 1px solid var(--color-surface-pressed, #e5e7eb); }
  .community-preview img { width: 100%; height: 100%; object-fit: cover; }
  .community-preview button {
    position: absolute; top: 3px; right: 3px; width: 22px; height: 22px; border: none; border-radius: 50%;
    background: rgba(6,27,49,.7); color: #fff; cursor: pointer; font-size: 14px; line-height: 22px; padding: 0;
  }
  .community-hint { font-size: 0.8rem; color: var(--color-body, #6b7280); margin-top: 8px; }
  .community-card {
    background: var(--color-canvas, #fff);
    border: 1px solid var(--color-surface-pressed, #e5e7eb);
    border-radius: var(--rounded-xl, 16px);
    padding: var(--spacing-xl, 20px); margin-bottom: var(--spacing-lg, 16px);
  }
  .community-card-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
  .community-avatar {
    width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0;
    background: var(--color-primary-soft, #2f6f59); color: #fff;
    display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 0.95rem;
    overflow: hidden;
  }
  .community-avatar img { width: 100%; height: 100%; object-fit: cover; }
  .community-name { font-weight: 700; color: var(--color-ink, #061b31); font-size: 0.95rem; }
  .community-meta { font-size: 0.8rem; color: var(--color-body, #6b7280); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .community-stars { letter-spacing: 1px; }
  .community-star { color: #d1d5db; }
  .community-star.is-on { color: var(--color-accent, #F5A623); }
  .community-content { color: var(--color-ink, #1f2937); line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
  .community-photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-top: 12px; }
  .community-photos.count-1 { grid-template-columns: 1fr; max-width: 360px; }
  .community-photos.count-2 { grid-template-columns: repeat(2, 1fr); }
  .community-photos a { display: block; border-radius: 12px; overflow: hidden; aspect-ratio: 1/1; }
  .community-photos img { width: 100%; height: 100%; object-fit: cover; }
  .community-state { text-align: center; padding: 40px 16px; color: var(--color-body, #6b7280); }
  .community-more { text-align: center; margin-top: 8px; }
  .community-more-btn { background: var(--color-canvas-soft, #f3f4f6); color: var(--color-ink, #061b31); border-color: var(--color-surface-pressed, #e5e7eb); }
  .community-comments { margin-top: 14px; border-top: 1px solid var(--color-surface-pressed, #eef0f2); padding-top: 10px; }
  .community-cc-toggle { background: none; border: none; cursor: pointer; font-family: inherit; font-size: 0.85rem; font-weight: 600; color: var(--color-primary, #1B4D3E); padding: 4px 0; }
  .community-cc-body { margin-top: 10px; }
  .community-cc-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 12px; }
  .community-comment { display: flex; gap: 10px; }
  .community-avatar-sm { width: 32px; height: 32px; font-size: 0.8rem; }
  .community-comment.is-mgr .community-avatar-sm { background: var(--color-accent, #F5A623); color: var(--color-ink, #061b31); }
  .community-comment-body { flex: 1; background: var(--color-canvas-soft, #f6f7f8); border-radius: 12px; padding: 8px 12px; }
  .community-comment.is-mgr .community-comment-body { background: #FFF7E8; border: 1px solid #F5D9A6; }
  .community-comment-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 2px; }
  .community-name-sm { font-weight: 700; font-size: 0.85rem; color: var(--color-ink, #061b31); }
  .community-time-sm { font-size: 0.75rem; color: var(--color-body, #6b7280); }
  .community-badge-mgr { font-size: 0.68rem; font-weight: 700; color: #fff; background: var(--color-primary, #1B4D3E); padding: 1px 8px; border-radius: 999px; text-transform: uppercase; letter-spacing: .03em; }
  .community-comment-text { font-size: 0.9rem; color: var(--color-ink, #1f2937); line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
  .community-cc-form { display: flex; gap: 8px; }
  .community-cc-input { flex: 1; border: 1px solid var(--color-surface-pressed, #d1d5db); border-radius: 999px; padding: 8px 14px; font-family: inherit; font-size: 0.88rem; color: var(--color-ink, #061b31); }
  .community-cc-input:focus { outline: none; border-color: var(--color-primary, #1B4D3E); }
  .community-cc-send { background: var(--color-primary, #1B4D3E); color: #fff; border: none; border-radius: 999px; padding: 8px 18px; font-family: inherit; font-weight: 600; font-size: 0.88rem; cursor: pointer; }
  .community-cc-send:disabled { opacity: .55; cursor: not-allowed; }
  .community-state-sm { font-size: 0.85rem; color: var(--color-body, #6b7280); padding: 4px 0; }
  .community-mod { margin-left: auto; display: flex; gap: 6px; }
  .community-mod-btn { font-family: inherit; font-size: 0.75rem; font-weight: 600; border: 1px solid var(--color-surface-pressed, #e5e7eb); background: var(--color-canvas, #fff); color: var(--color-body, #6b7280); border-radius: 999px; padding: 3px 12px; cursor: pointer; }
  .community-mod-btn.danger { color: #b42318; border-color: #f3c0ba; }
  .community-mod-btn:hover { background: var(--color-canvas-soft, #f3f4f6); }
  .community-cmod { display: inline-flex; gap: 6px; margin-left: auto; }
  .community-cmod-btn { font-family: inherit; font-size: 0.7rem; font-weight: 600; border: none; background: none; color: var(--color-body, #6b7280); cursor: pointer; padding: 0 2px; }
  .community-cmod-btn.danger { color: #b42318; }
  .community-cmod-btn:hover { text-decoration: underline; }
  `;
  const style = document.createElement('style');
  style.id = 'community-feed-styles';
  style.textContent = css;
  document.head.appendChild(style);
}

// ---------- API ----------
async function fetchFeed(page) {
  const res = await fetch(`${BASE_URL}/api/public/blog/feed?page=${page}&size=${PAGE_SIZE}`);
  if (!res.ok) throw new Error(`Lỗi ${res.status}`);
  return res.json();
}

// Nen anh ve JPEG (max 1280px, chat luong 0.8) truoc khi gui — giong chat.js
function compressImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      const maxDim = 1280;
      let w = img.width, h = img.height;
      if (w > maxDim || h > maxDim) {
        if (w >= h) { h = Math.round(h * maxDim / w); w = maxDim; }
        else { w = Math.round(w * maxDim / h); h = maxDim; }
      }
      const canvas = document.createElement('canvas');
      canvas.width = w; canvas.height = h;
      canvas.getContext('2d').drawImage(img, 0, 0, w, h);
      canvas.toBlob(b => b ? resolve(b) : reject(new Error('Không xử lý được ảnh.')), 'image/jpeg', 0.8);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Ảnh không hợp lệ.')); };
    img.src = url;
  });
}

// ---------- Render ----------
function cardHTML(post) {
  const avatar = post.authorAvatarUrl
    ? `<img src="${escapeHTML(post.authorAvatarUrl)}" alt="Ảnh đại diện của ${escapeHTML(post.authorName)}">`
    : escapeHTML(initials(post.authorName));
  const photos = (post.photos || []);
  const photosHTML = photos.length
    ? `<div class="community-photos count-${Math.min(photos.length, 3)}">` +
      photos.map(u =>
        `<a href="${escapeHTML(u)}" target="_blank" rel="noopener"><img src="${escapeHTML(u)}" alt="Ảnh đính kèm bài đăng" loading="lazy"></a>`
      ).join('') + `</div>`
    : '';
  const count = post.commentCount || 0;
  const formHTML = canComment()
    ? `<div class="community-cc-form">
         <input type="text" class="community-cc-input" maxlength="1000" placeholder="Viết bình luận…">
         <button class="community-cc-send" type="button">Gửi</button>
       </div>`
    : '';
  const modHTML = isManager()
    ? `<div class="community-mod">
         <button class="community-mod-btn" data-act="hide" type="button">Ẩn</button>
         <button class="community-mod-btn danger" data-act="delete" type="button">Xoá</button>
       </div>`
    : '';
  return `
    <article class="community-card" data-post-id="${escapeHTML(post.id)}">
      <div class="community-card-head">
        <div class="community-avatar">${avatar}</div>
        <div>
          <div class="community-name">${escapeHTML(post.authorName)}</div>
          <div class="community-meta">
            <span>${escapeHTML(formatTimeVN(post.createdAt))}</span>
            ${starsHTML(post.rating)}
          </div>
        </div>
        ${modHTML}
      </div>
      <div class="community-content">${escapeHTML(post.content)}</div>
      ${photosHTML}
      <div class="community-comments" data-post-id="${escapeHTML(post.id)}">
        <button class="community-cc-toggle" type="button">💬 <span class="cc-count">${count}</span> bình luận</button>
        <div class="community-cc-body" hidden>
          <div class="community-cc-list"></div>
          ${formHTML}
        </div>
      </div>
    </article>`;
}

function commentHTML(c) {
  const isMgr = c.authorRole === 'MANAGER';
  const badge = isMgr ? '<span class="community-badge-mgr">Quản lý</span>' : '';
  const avatar = c.authorAvatarUrl
    ? `<img src="${escapeHTML(c.authorAvatarUrl)}" alt="Ảnh đại diện của ${escapeHTML(c.authorName)}">`
    : escapeHTML(initials(c.authorName));
  const modHTML = isManager()
    ? `<span class="community-cmod">
         <button class="community-cmod-btn" data-act="hide" type="button">Ẩn</button>
         <button class="community-cmod-btn danger" data-act="delete" type="button">Xoá</button>
       </span>`
    : '';
  return `
    <div class="community-comment${isMgr ? ' is-mgr' : ''}" data-comment-id="${escapeHTML(c.id)}">
      <div class="community-avatar community-avatar-sm">${avatar}</div>
      <div class="community-comment-body">
        <div class="community-comment-head">
          <span class="community-name-sm">${escapeHTML(c.authorName)}</span>
          ${badge}
          <span class="community-time-sm">${escapeHTML(formatTimeVN(c.createdAt))}</span>
          ${modHTML}
        </div>
        <div class="community-comment-text">${escapeHTML(c.content)}</div>
      </div>
    </div>`;
}

async function loadComments(postId, listEl, countEl) {
  listEl.innerHTML = '<div class="community-state-sm">Đang tải bình luận…</div>';
  try {
    const res = await fetch(`${BASE_URL}/api/public/blog/posts/${postId}/comments`);
    if (!res.ok) throw new Error(`Lỗi ${res.status}`);
    const comments = await res.json();
    listEl.innerHTML = comments.length
      ? comments.map(commentHTML).join('')
      : '<div class="community-state-sm">Chưa có bình luận. Hãy là người đầu tiên!</div>';
    wireCommentActions(listEl, countEl);
  } catch (err) {
    listEl.innerHTML = '<div class="community-state-sm">Không tải được bình luận.</div>';
  }
}

async function submitComment(postId, inputEl, sendBtn, listEl, countEl) {
  const content = (inputEl.value || '').trim();
  if (!content) { inputEl.focus(); return; }
  const path = state.me.role === 'MANAGER'
    ? `/api/manager/blog/posts/${postId}/comments`
    : `/api/customer/blog/posts/${postId}/comments`;
  sendBtn.disabled = true;
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    });
    if (!res.ok) {
      let data = {};
      try { data = await res.json(); } catch { /* ignore */ }
      throw new Error(data.message || `Lỗi ${res.status}`);
    }
    const saved = await res.json();
    const emptyState = listEl.querySelector('.community-state-sm');
    if (emptyState) listEl.innerHTML = '';
    listEl.insertAdjacentHTML('beforeend', commentHTML(saved));
    wireCommentActions(listEl, countEl);
    inputEl.value = '';
    if (countEl) countEl.textContent = String((parseInt(countEl.textContent, 10) || 0) + 1);
  } catch (err) {
    alert(err.message || 'Không gửi được bình luận.');
  } finally {
    sendBtn.disabled = false;
  }
}

// ---------- Kiem duyet (Manager) ----------
async function moderatePost(postId, act, cardEl) {
  if (act === 'delete' && !confirm('Xoá bài viết này? Hành động không thể hoàn tác.')) return;
  const path = act === 'hide' ? `/api/manager/blog/posts/${postId}/hide` : `/api/manager/blog/posts/${postId}`;
  const method = act === 'hide' ? 'POST' : 'DELETE';
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method, headers: { 'Authorization': `Bearer ${getToken()}` },
    });
    if (!res.ok) {
      let data = {}; try { data = await res.json(); } catch { /* ignore */ }
      throw new Error(data.message || `Lỗi ${res.status}`);
    }
    cardEl.remove();
    state.seen.delete(postId);
  } catch (err) {
    alert(err.message || 'Không thực hiện được thao tác.');
  }
}

async function moderateComment(commentId, act, commentEl, countEl) {
  if (act === 'delete' && !confirm('Xoá bình luận này?')) return;
  const path = act === 'hide'
    ? `/api/manager/blog/comments/${commentId}/hide`
    : `/api/manager/blog/comments/${commentId}`;
  const method = act === 'hide' ? 'POST' : 'DELETE';
  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method, headers: { 'Authorization': `Bearer ${getToken()}` },
    });
    if (!res.ok) {
      let data = {}; try { data = await res.json(); } catch { /* ignore */ }
      throw new Error(data.message || `Lỗi ${res.status}`);
    }
    commentEl.remove();
    if (countEl) countEl.textContent = String(Math.max(0, (parseInt(countEl.textContent, 10) || 1) - 1));
  } catch (err) {
    alert(err.message || 'Không thực hiện được thao tác.');
  }
}

// Gan nut kiem duyet cho cac binh luan moi render (Manager).
function wireCommentActions(listEl, countEl) {
  if (!isManager()) return;
  listEl.querySelectorAll('.community-comment:not([data-cmod-init])').forEach(el => {
    el.setAttribute('data-cmod-init', '1');
    const commentId = el.getAttribute('data-comment-id');
    el.querySelectorAll('.community-cmod-btn').forEach(btn => {
      btn.addEventListener('click', () => moderateComment(commentId, btn.dataset.act, el, countEl));
    });
  });
}

// Gan su kien cho cac card moi chen (toggle binh luan + form + kiem duyet). Goi sau moi lan insert.
function wireCards() {
  state.listEl.querySelectorAll('.community-card:not([data-card-init])').forEach(card => {
    card.setAttribute('data-card-init', '1');
    const postId = card.getAttribute('data-post-id');

    const box = card.querySelector('.community-comments');
    const toggle = box.querySelector('.community-cc-toggle');
    const body = box.querySelector('.community-cc-body');
    const listEl = box.querySelector('.community-cc-list');
    const countEl = box.querySelector('.cc-count');
    let loaded = false;
    toggle.addEventListener('click', () => {
      const willOpen = body.hidden;
      body.hidden = !willOpen;
      if (willOpen && !loaded) { loaded = true; loadComments(postId, listEl, countEl); }
    });
    const form = box.querySelector('.community-cc-form');
    if (form) {
      const input = form.querySelector('.community-cc-input');
      const send = form.querySelector('.community-cc-send');
      const doSend = () => submitComment(postId, input, send, listEl, countEl);
      send.addEventListener('click', doSend);
      input.addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); doSend(); }
      });
    }

    // Kiem duyet bai (Manager)
    const mod = card.querySelector('.community-mod');
    if (mod) {
      mod.querySelectorAll('.community-mod-btn').forEach(btn => {
        btn.addEventListener('click', () => moderatePost(postId, btn.dataset.act, card));
      });
    }
  });
}

function renderState(html) {
  state.listEl.innerHTML = `<div class="community-state">${html}</div>`;
}

function appendPosts(posts) {
  const html = posts
    .filter(p => !state.seen.has(p.id))
    .map(p => { state.seen.add(p.id); return cardHTML(p); })
    .join('');
  state.listEl.insertAdjacentHTML('beforeend', html);
  wireCards();
}

function prependNewPosts(posts) {
  const fresh = posts.filter(p => !state.seen.has(p.id));
  if (!fresh.length) return;
  const html = fresh.map(p => { state.seen.add(p.id); return cardHTML(p); }).join('');
  state.listEl.insertAdjacentHTML('afterbegin', html);
  wireCards();
}

function renderMoreButton() {
  const old = state.root.querySelector('.community-more');
  if (old) old.remove();
  if (state.page + 1 < state.totalPages) {
    const wrap = document.createElement('div');
    wrap.className = 'community-more';
    wrap.innerHTML = `<button class="community-more-btn" type="button">Tải thêm bài viết</button>`;
    wrap.querySelector('button').addEventListener('click', () => loadMore());
    state.listEl.after(wrap);
  }
}

async function loadFirstPage() {
  if (state.loading) return;
  state.loading = true;
  state.page = 0;
  state.seen.clear();
  renderState('Đang tải bài viết…');
  try {
    const data = await fetchFeed(0);
    state.totalPages = data.totalPages || 1;
    const posts = data.content || [];
    state.listEl.innerHTML = '';
    if (!posts.length) {
      renderState('Chưa có bài viết nào. Hãy là người đầu tiên chia sẻ trải nghiệm của bạn!');
    } else {
      appendPosts(posts);
    }
    renderMoreButton();
  } catch (err) {
    renderState('Không thể tải bài viết. <button class="community-more-btn" type="button" onclick="window.__communityRetry&&window.__communityRetry()">Thử lại</button>');
  } finally {
    state.loading = false;
  }
}

async function loadMore() {
  if (state.loading || state.page + 1 >= state.totalPages) return;
  state.loading = true;
  try {
    const next = state.page + 1;
    const data = await fetchFeed(next);
    state.page = next;
    state.totalPages = data.totalPages || state.totalPages;
    appendPosts(data.content || []);
    renderMoreButton();
  } catch (err) {
    // giu nguyen danh sach hien tai; lan poll/thao tac sau se thu lai
  } finally {
    state.loading = false;
  }
}

async function pollNewPosts() {
  if (state.loading) return;
  try {
    const data = await fetchFeed(0);
    prependNewPosts(data.content || []);
  } catch { /* im lang khi poll loi */ }
}

// ---------- Form dang (chi Customer) ----------
function renderComposer() {
  const box = document.createElement('div');
  box.className = 'community-compose';
  box.innerHTML = `
    <textarea id="community-input" maxlength="1000" placeholder="Chia sẻ trải nghiệm của bạn về dịch vụ Move_home…"></textarea>
    <div class="community-previews" id="community-previews"></div>
    <div class="community-compose-row">
      <div class="community-rate" id="community-rate" role="radiogroup" aria-label="Chấm điểm dịch vụ">
        ${[1,2,3,4,5].map(i => `<span class="community-star" data-v="${i}" role="radio" aria-label="${i} sao">★</span>`).join('')}
      </div>
      <label class="community-upload-btn">
        📷 Thêm ảnh
        <input type="file" id="community-files" accept="image/jpeg,image/png,image/webp" multiple hidden>
      </label>
      <button class="community-post-btn" id="community-submit" type="button">Đăng bài</button>
    </div>
    <div class="community-hint" id="community-hint">Tối đa 3 ảnh, mỗi ảnh dưới 1,5 MB.</div>
  `;
  state.root.insertBefore(box, state.listEl);

  const rateEl = box.querySelector('#community-rate');
  rateEl.addEventListener('click', (e) => {
    const s = e.target.closest('.community-star');
    if (!s) return;
    state.rating = Number(s.dataset.v);
    rateEl.querySelectorAll('.community-star').forEach(st => {
      st.classList.toggle('is-on', Number(st.dataset.v) <= state.rating);
    });
  });

  box.querySelector('#community-files').addEventListener('change', (e) => {
    for (const f of e.target.files) {
      if (state.selectedFiles.length >= MAX_PHOTOS) break;
      state.selectedFiles.push(f);
    }
    e.target.value = '';
    renderPreviews();
  });

  box.querySelector('#community-submit').addEventListener('click', submitPost);
}

function renderPreviews() {
  const wrap = document.getElementById('community-previews');
  if (!wrap) return;
  wrap.innerHTML = state.selectedFiles.map((f, i) => {
    const url = URL.createObjectURL(f);
    return `<div class="community-preview"><img src="${url}" alt="Ảnh xem trước"><button type="button" data-i="${i}" aria-label="Xóa ảnh">×</button></div>`;
  }).join('');
  wrap.querySelectorAll('button').forEach(b => b.addEventListener('click', () => {
    state.selectedFiles.splice(Number(b.dataset.i), 1);
    renderPreviews();
  }));
  const hint = document.getElementById('community-hint');
  if (hint) hint.textContent = `Đã chọn ${state.selectedFiles.length}/3 ảnh. Mỗi ảnh dưới 1,5 MB.`;
}

async function submitPost() {
  const input = document.getElementById('community-input');
  const submitBtn = document.getElementById('community-submit');
  const content = (input.value || '').trim();
  if (!content) { input.focus(); return; }

  submitBtn.disabled = true;
  submitBtn.textContent = 'Đang đăng…';
  try {
    const form = new FormData();
    form.append('content', content);
    if (state.rating) form.append('rating', String(state.rating));
    for (let i = 0; i < state.selectedFiles.length; i++) {
      const blob = await compressImage(state.selectedFiles[i]);
      form.append('files', blob, `blog_${i}.jpg`);
    }
    const res = await fetch(`${BASE_URL}/api/customer/blog/posts`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${getToken()}` },
      body: form,
    });
    if (!res.ok) {
      let data = {};
      try { data = await res.json(); } catch { /* ignore */ }
      throw new Error(data.message || `Lỗi ${res.status}`);
    }
    const saved = await res.json();
    if (!state.seen.has(saved.id)) {
      state.seen.add(saved.id);
      const emptyState = state.listEl.querySelector('.community-state');
      if (emptyState) state.listEl.innerHTML = '';
      state.listEl.insertAdjacentHTML('afterbegin', cardHTML(saved));
      wireCards();
    }
    input.value = '';
    state.rating = 0;
    state.selectedFiles = [];
    renderPreviews();
    document.querySelectorAll('#community-rate .community-star').forEach(st => st.classList.remove('is-on'));
  } catch (err) {
    alert(err.message || 'Không đăng được bài. Vui lòng thử lại.');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Đăng bài';
  }
}

// ---------- Init ----------
function init() {
  const root = document.getElementById('community-feed');
  if (!root) return;
  injectStyles();

  const wrap = document.createElement('div');
  wrap.className = 'community-wrap';
  const list = document.createElement('div');
  list.className = 'community-list';
  wrap.appendChild(list);
  root.appendChild(wrap);
  state.root = wrap;
  state.listEl = list;

  // Form dang chi cho Customer da dang nhap
  state.me = getCurrentUser();
  if (state.me && state.me.role === 'CUSTOMER') {
    renderComposer();
  }

  window.__communityRetry = loadFirstPage;
  loadFirstPage();

  // Poll nhe lay bai moi (REST, khong WebSocket)
  if (state.pollTimer) clearInterval(state.pollTimer);
  state.pollTimer = setInterval(pollNewPosts, POLL_MS);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
