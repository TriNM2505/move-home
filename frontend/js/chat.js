// ============================================================
// CHAT 3 CAP (Customer <-> Manager <-> Driver) — trang messages.html
// Realtime: WebSocket STOMP + SockJS (AC-05). Lich su + gui: REST /api/chat/**.
// Co polling nhe lam luoi an toan khi WebSocket rot ket noi.
// ============================================================

import { getCurrentUser, getToken, logout } from './auth.js';

const BASE_URL = 'http://localhost:8080';

const ROLE_HOME = {
  ADMIN: 'admin/dashboard.html',
  MANAGER: 'manager/home.html',
  DRIVER: 'driver/home.html',
  CUSTOMER: 'customer/home.html',
};

const state = {
  me: null,
  conversations: [],
  currentId: null,
  stompClient: null,
  wsConnected: false,
  threadPollTimer: null,
  listPollTimer: null,
};

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

function formatTime(dateString) {
  if (!dateString) return '';
  const d = new Date(dateString);
  const now = new Date();
  const mins = Math.floor((now - d) / 60000);
  const hours = Math.floor((now - d) / 3600000);
  if (mins < 1) return 'Vừa xong';
  if (mins < 60) return `${mins} phút`;
  if (hours < 24) return `${hours} giờ`;
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${day}/${month} ${hh}:${mm}`;
}

async function api(method, path, body) {
  const token = getToken();
  const opts = { method, headers: { 'Authorization': `Bearer ${token}` } };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(`${BASE_URL}${path}`, opts);
  if (!res.ok) {
    let data = {};
    try { data = await res.json(); } catch { /* ignore */ }
    const err = new Error(data.message || `Lỗi ${res.status}`);
    err.status = res.status;
    throw err;
  }
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---------- Danh sach hoi thoai ----------
async function loadConversations() {
  try {
    const list = await api('GET', '/api/chat/conversations');
    state.conversations = Array.isArray(list) ? list : [];
    renderConversations();
  } catch (err) {
    const el = document.getElementById('convList');
    el.innerHTML = `<div class="chat-state"><div class="chat-state-icon">⚠️</div><p>Không tải được danh sách.</p></div>`;
  }
}

function typeTag(type, role) {
  if (type === 'CUSTOMER_MANAGER') return role === 'CUSTOMER' ? 'Hỗ trợ' : 'Khách hàng';
  if (type === 'MANAGER_DRIVER') return role === 'DRIVER' ? 'Quản lý' : 'Tài xế';
  if (type === 'CUSTOMER_DRIVER') return 'Theo đơn';
  return '';
}

function renderConversations() {
  const el = document.getElementById('convList');
  if (!state.conversations.length) {
    el.innerHTML = `<div class="chat-state"><div class="chat-state-icon">💬</div><p>Chưa có hội thoại nào.</p></div>`;
    return;
  }
  const role = state.me.role;
  el.innerHTML = state.conversations.map(c => {
    const active = c.id === state.currentId ? 'active' : '';
    const unread = c.unreadCount > 0
      ? `<span class="chat-unread-pill">${c.unreadCount > 99 ? '99+' : c.unreadCount}</span>` : '';
    const tag = typeTag(c.type, role);
    const orderTag = c.orderCode ? ` · ${escapeHTML(c.orderCode)}` : '';
    return `
      <div class="chat-conv-item ${active}" data-id="${escapeHTML(c.id)}">
        <div class="chat-avatar">${escapeHTML(initials(c.counterpartName))}</div>
        <div class="chat-conv-main">
          <div class="chat-conv-row">
            <span class="chat-conv-name">${escapeHTML(c.counterpartName || 'Hội thoại')}</span>
            <span class="chat-conv-time">${formatTime(c.lastMessageAt)}</span>
          </div>
          <div class="chat-conv-row">
            <span class="chat-conv-preview">${escapeHTML(c.lastMessageText || 'Chưa có tin nhắn')}</span>
            ${unread}
          </div>
          <span class="chat-conv-tag">${escapeHTML(tag)}${orderTag}</span>
        </div>
      </div>`;
  }).join('');

  el.querySelectorAll('.chat-conv-item').forEach(item => {
    item.addEventListener('click', () => openConversation(item.getAttribute('data-id')));
  });
}

// ---------- Khung tin nhan ----------
async function openConversation(conversationId) {
  state.currentId = conversationId;
  document.getElementById('chatApp').classList.add('thread-open');
  document.getElementById('btnBackList').classList.remove('hidden');
  renderConversations(); // cap nhat highlight active

  const conv = state.conversations.find(c => c.id === conversationId);
  document.getElementById('threadTitle').textContent = conv ? (conv.counterpartName || 'Hội thoại') : 'Hội thoại';
  document.getElementById('threadSub').textContent = conv && conv.orderCode ? `Đơn ${conv.orderCode}` : '';

  document.getElementById('threadPlaceholder').classList.add('hidden');
  const box = document.getElementById('threadMessages');
  box.classList.remove('hidden');
  document.getElementById('composer').classList.remove('hidden');
  box.innerHTML = `<div class="chat-state"><p>Đang tải tin nhắn…</p></div>`;

  await loadMessages(conversationId, true);
  startThreadPolling();
  // Mo hoi thoai = da doc → cap nhat lai danh sach (badge/unread)
  loadConversations();
}

async function loadMessages(conversationId, scroll) {
  try {
    const page = await api('GET', `/api/chat/conversations/${conversationId}/messages?page=0&size=50`);
    const msgs = (page.content || []).slice().reverse(); // API tra moi-nhat-truoc → dao lai cu-truoc
    renderMessages(msgs);
    if (scroll) scrollToBottom();
  } catch (err) {
    document.getElementById('threadMessages').innerHTML =
      `<div class="chat-state"><div class="chat-state-icon">⚠️</div><p>Không tải được tin nhắn.</p></div>`;
  }
}

function bubbleHTML(m) {
  const mine = m.senderId === state.me.id;
  const showSender = !mine && state.me.role !== 'CUSTOMER'; // quan ly xem ten nguoi gui
  const imagePart = m.imageUrl
    ? `<a href="${escapeHTML(m.imageUrl)}" target="_blank" rel="noopener"><img src="${escapeHTML(m.imageUrl)}" alt="Ảnh" style="max-width:220px;max-height:240px;border-radius:12px;display:block;cursor:pointer;border:1px solid var(--color-surface-pressed);"></a>`
    : '';
  const textPart = (m.content && m.content.length)
    ? `<div class="chat-bubble">${escapeHTML(m.content)}</div>` : '';
  return `
    <div class="chat-bubble-row ${mine ? 'mine' : ''}">
      <div>
        ${showSender ? `<div class="chat-bubble-sender">${escapeHTML(m.senderName || '')}</div>` : ''}
        ${imagePart}
        ${textPart}
        <div class="chat-bubble-meta">${formatTime(m.createdAt)}</div>
      </div>
    </div>`;
}

function renderMessages(msgs) {
  const box = document.getElementById('threadMessages');
  if (!msgs.length) {
    box.innerHTML = `<div class="chat-state"><p>Hãy gửi tin nhắn đầu tiên.</p></div>`;
    return;
  }
  box.innerHTML = msgs.map(bubbleHTML).join('');
}

function appendMessage(m) {
  const box = document.getElementById('threadMessages');
  const state0 = box.querySelector('.chat-state');
  if (state0) box.innerHTML = '';
  box.insertAdjacentHTML('beforeend', bubbleHTML(m));
  scrollToBottom();
}

function scrollToBottom() {
  const box = document.getElementById('threadMessages');
  box.scrollTop = box.scrollHeight;
}

async function sendMessage(content) {
  const text = content.trim();
  if (!text || !state.currentId) return;
  try {
    const saved = await api('POST', `/api/chat/conversations/${state.currentId}/messages`, { content: text });
    appendMessage(saved);
    loadConversations();
  } catch (err) {
    alert(err.message || 'Không gửi được tin nhắn.');
  }
}

// Gui 1 anh: nen client → upload multipart. KHONG set Content-Type (de browser tu dat boundary).
async function sendImageFile(file) {
  if (!file || !state.currentId) return;
  try {
    const blob = await compressImage(file);
    const form = new FormData();
    form.append('file', blob, 'chat.jpg');
    const res = await fetch(`${BASE_URL}/api/chat/conversations/${state.currentId}/images`, {
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
    appendMessage(saved);
    loadConversations();
  } catch (err) {
    alert(err.message || 'Không gửi được ảnh.');
  }
}

// Nen anh ve JPEG (max 1280px canh dai, chat luong 0.8) de duoi 1.5MB truoc khi gui
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
      canvas.width = w;
      canvas.height = h;
      canvas.getContext('2d').drawImage(img, 0, 0, w, h);
      canvas.toBlob(b => b ? resolve(b) : reject(new Error('Không xử lý được ảnh.')), 'image/jpeg', 0.8);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Ảnh không hợp lệ.')); };
    img.src = url;
  });
}

// ---------- WebSocket realtime ----------
function connectWebSocket() {
  if (!window.StompJs || !window.SockJS) return; // CDN chua tai → dua vao polling
  const token = getToken();
  const client = new window.StompJs.Client({
    webSocketFactory: () => new window.SockJS(`${BASE_URL}/ws`),
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      state.wsConnected = true;
      client.subscribe('/user/queue/messages', frame => {
        let payload;
        try { payload = JSON.parse(frame.body); } catch { return; }
        onRealtimeMessage(payload);
      });
    },
    onDisconnect: () => { state.wsConnected = false; },
    onWebSocketClose: () => { state.wsConnected = false; },
    onStompError: () => { state.wsConnected = false; },
  });
  client.activate();
  state.stompClient = client;
}

function onRealtimeMessage(payload) {
  if (payload.conversationId === state.currentId) {
    appendMessage(payload);
    // Danh dau da doc ngay vi dang mo hoi thoai
    api('POST', `/api/chat/conversations/${state.currentId}/read`).catch(() => {});
  }
  loadConversations(); // cap nhat preview + unread
}

// ---------- Polling (luoi an toan) ----------
function startThreadPolling() {
  stopThreadPolling();
  state.threadPollTimer = setInterval(() => {
    if (document.hidden || !state.currentId) return;
    if (state.wsConnected) return; // WS dang chay thi khong can poll thread
    loadMessages(state.currentId, false);
  }, 6000);
}
function stopThreadPolling() {
  if (state.threadPollTimer) clearInterval(state.threadPollTimer);
  state.threadPollTimer = null;
}
function startListPolling() {
  state.listPollTimer = setInterval(() => {
    if (document.hidden) return;
    loadConversations();
  }, 15000);
}

// ---------- Mo hoi thoai theo yeu cau (nut Ho tro / Nhan quan ly / chon tai xe) ----------
async function openViaRequest(body, errMsg) {
  try {
    const conv = await api('POST', '/api/chat/conversations/open', body);
    await loadConversations();
    if (conv && conv.id) openConversation(conv.id);
  } catch (err) {
    alert(err.message || errMsg);
  }
}

// ---------- Modal chon tai xe (Manager/Admin) ----------
let driverDirectory = [];

async function openDriverPicker() {
  const overlay = document.getElementById('driverPicker');
  overlay.classList.remove('hidden');
  document.getElementById('pickerSearch').value = '';
  const listEl = document.getElementById('pickerList');
  listEl.innerHTML = `<div class="chat-state"><p>Đang tải danh bạ…</p></div>`;
  try {
    driverDirectory = await api('GET', '/api/chat/directory/drivers') || [];
    renderPicker('');
  } catch (err) {
    listEl.innerHTML = `<div class="chat-state"><div class="chat-state-icon">⚠️</div><p>Không tải được danh bạ tài xế.</p></div>`;
  }
}

function renderPicker(query) {
  const listEl = document.getElementById('pickerList');
  const kw = (query || '').trim().toLowerCase();
  const items = driverDirectory.filter(d => !kw || (d.fullName || '').toLowerCase().includes(kw));
  if (!items.length) {
    listEl.innerHTML = `<div class="chat-state"><p>Không tìm thấy tài xế.</p></div>`;
    return;
  }
  listEl.innerHTML = items.map(d => `
    <div class="chat-picker-item" data-id="${escapeHTML(d.id)}">
      <div class="chat-avatar">${escapeHTML(initials(d.fullName))}</div>
      <div>
        <div class="chat-picker-name">${escapeHTML(d.fullName || 'Tài xế')}</div>
        <div class="chat-picker-phone">${escapeHTML(d.phone || '')}</div>
      </div>
    </div>`).join('');
  listEl.querySelectorAll('.chat-picker-item').forEach(el => {
    el.addEventListener('click', () => {
      closeDriverPicker();
      openViaRequest({ type: 'MANAGER_DRIVER', driverId: el.getAttribute('data-id') }, 'Không mở được hội thoại.');
    });
  });
}

function closeDriverPicker() {
  document.getElementById('driverPicker').classList.add('hidden');
}

// ---------- Mo hoi thoai tu query param (tu order-detail) ----------
async function handleOpenParam() {
  const params = new URLSearchParams(window.location.search);
  if (params.get('open') !== '1') return;
  const type = params.get('type');
  const orderId = params.get('orderId');
  if (!type) return;
  try {
    const body = orderId ? { type, orderId } : { type };
    const conv = await api('POST', '/api/chat/conversations/open', body);
    await loadConversations();
    if (conv && conv.id) openConversation(conv.id);
  } catch (err) {
    alert(err.message || 'Không mở được hội thoại.');
  }
}

// ---------- Khoi tao ----------
document.addEventListener('DOMContentLoaded', async () => {
  const user = getCurrentUser();
  if (!getToken() || !user) {
    window.location.href = 'login.html';
    return;
  }
  state.me = user;

  document.getElementById('backHomeLink').href = ROLE_HOME[user.role] || 'login.html';
  document.getElementById('logoutBtn').addEventListener('click', () => logout('login.html'));

  // Nut mo hoi thoai theo vai tro:
  // - Customer: "Hỗ trợ" (CUSTOMER_MANAGER)
  // - Driver:   "Nhắn quản lý" (MANAGER_DRIVER khong theo don)
  // - Manager/Admin: "＋ Tài xế" → modal chon tai xe (MANAGER_DRIVER khong theo don)
  if (user.role === 'CUSTOMER') {
    const btn = document.getElementById('btnSupport');
    btn.classList.remove('hidden');
    btn.addEventListener('click', () =>
      openViaRequest({ type: 'CUSTOMER_MANAGER' }, 'Không mở được kênh hỗ trợ.'));
  } else if (user.role === 'DRIVER') {
    const btn = document.getElementById('btnDriverSupport');
    btn.classList.remove('hidden');
    btn.addEventListener('click', () =>
      openViaRequest({ type: 'MANAGER_DRIVER' }, 'Không mở được kênh hỗ trợ.'));
  } else if (user.role === 'MANAGER' || user.role === 'ADMIN') {
    const btn = document.getElementById('btnCompose');
    btn.classList.remove('hidden');
    btn.addEventListener('click', openDriverPicker);
    document.getElementById('pickerClose').addEventListener('click', closeDriverPicker);
    document.getElementById('pickerSearch').addEventListener('input', e => renderPicker(e.target.value));
    document.getElementById('driverPicker').addEventListener('click', e => {
      if (e.target.id === 'driverPicker') closeDriverPicker();
    });
  }

  document.getElementById('btnBackList').addEventListener('click', () => {
    document.getElementById('chatApp').classList.remove('thread-open');
  });

  document.getElementById('composer').addEventListener('submit', e => {
    e.preventDefault();
    const input = document.getElementById('composerInput');
    const val = input.value;
    input.value = '';
    sendMessage(val);
  });

  // Dinh kem 1 anh
  document.getElementById('composerImageBtn').addEventListener('click', () =>
    document.getElementById('composerImage').click());
  document.getElementById('composerImage').addEventListener('change', e => {
    const file = e.target.files && e.target.files[0];
    e.target.value = ''; // cho phep chon lai cung file
    if (file) sendImageFile(file);
  });

  await loadConversations();
  await handleOpenParam();
  connectWebSocket();
  startListPolling();
});
