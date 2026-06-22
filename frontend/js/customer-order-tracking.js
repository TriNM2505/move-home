import { getAuthenticated } from './api.js';
import { isLoggedIn, getCurrentUser, logout } from './auth.js';

const POLL_INTERVAL_MS = 5000;
const HANOI_CENTER = [21.0278, 105.8342];

const ORDER_STATUSES = {
  PENDING: ['Đang chờ', 'badge-pending'],
  ACCEPTED: ['Đã nhận đơn', 'badge-active'],
  CONFIRMED: ['Đã xác nhận', 'badge-active'],
  ASSIGNED: ['Đã phân công', 'badge-active'],
  IN_PROGRESS: ['Đang giao', 'badge-in-progress'],
  AWAITING_FINAL_PAYMENT: ['Chờ thanh toán còn lại', 'badge-warning'],
  COMPLETED: ['Hoàn thành', 'badge-completed'],
  CANCELLED: ['Đã hủy', 'badge-cancelled'],
  DISPUTED: ['Khiếu nại', 'badge-disputed'],
  IN_DISPUTE: ['Tranh chấp', 'badge-disputed'],
};

const PAYMENT_STATUSES = {
  UNPAID: ['Chưa thanh toán', 'badge-warning'],
  PENDING: ['Chờ thanh toán', 'badge-warning'],
  PENDING_PAYMENT: ['Chờ thanh toán', 'badge-warning'],
  DEPOSIT_PAID: ['Đã thanh toán cọc', 'badge-in-progress'],
  PARTIALLY_PAID: ['Đã thanh toán một phần', 'badge-in-progress'],
  AWAITING_FINAL_PAYMENT: ['Chờ thanh toán còn lại', 'badge-warning'],
  CASH_ON_DELIVERY: ['Thanh toán khi hoàn thành', 'badge-neutral'],
  PAID: ['Đã thanh toán', 'badge-completed'],
  REFUNDED: ['Đã hoàn tiền', 'badge-neutral'],
};

const TIMELINE = [
  ['PENDING', 'Đã tạo đơn'],
  ['ACCEPTED', 'Tài xế nhận đơn'],
  ['IN_PROGRESS', 'Đang vận chuyển'],
  ['AWAITING_FINAL_PAYMENT', 'Chờ thanh toán còn lại'],
  ['COMPLETED', 'Hoàn thành'],
];

let orderId = '';
let map;
let marker;
let pollTimer;
let hasLocation = false;
let orderMeta = {};

const $ = (id) => document.getElementById(id);

document.addEventListener('DOMContentLoaded', () => {
  if (!isLoggedIn()) {
    window.location.href = '../login.html';
    return;
  }

  bindHeader();
  bindManualOrderForm();
  $('tracking-retry-btn')?.addEventListener('click', startTracking);
  startTracking();
});

function bindHeader() {
  const user = getCurrentUser();
  if (!user) return;

  setText('header-user-name', user.fullName || 'Khách hàng');
  setText('header-user-avatar', initials(user.fullName || 'Khách hàng'));
}

function bindManualOrderForm() {
  $('tracking-order-form')?.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = $('tracking-order-input')?.value.trim();
    if (!value) return;

    const params = new URLSearchParams(window.location.search);
    params.set('order_id', value);
    params.set('order_status', params.get('order_status') || 'IN_PROGRESS');
    params.set('payment_status', params.get('payment_status') || 'DEPOSIT_PAID');
    window.location.href = `${window.location.pathname}?${params.toString()}`;
  });
}

function startTracking() {
  clearPoll();
  orderId = getOrderId();

  if (!orderId) {
    showOnly('tracking-empty');
    return;
  }

  orderMeta = readOrderMeta(orderId);
  renderOrderMeta();
  showOnly('tracking-content');
  showMapState('Đang tải vị trí tài xế', 'Hệ thống đang lấy vị trí mới nhất từ API.');
  initMap();

  pollLocation(true);
  pollTimer = window.setInterval(() => pollLocation(false), POLL_INTERVAL_MS);
}

async function pollLocation(firstLoad) {
  try {
    const location = await getAuthenticated(`/api/customer/orders/${encodeURIComponent(orderId)}/location`);
    hasLocation = true;
    renderLocation(location);
    hideAlert();
  } catch (err) {
    handleLocationError(err, firstLoad);
  }
}

function handleLocationError(err, firstLoad) {
  if (err.status === 401 || err.status === 403) {
    logout('../login.html');
    return;
  }

  const message = errorMessage(err);

  if (err.status === 404 && !hasLocation) {
    showMapState('Chưa có vị trí tài xế', message || 'Tài xế chưa cập nhật vị trí cho đơn này.');
    showAlert('warning', message || 'Tài xế chưa cập nhật vị trí. Màn hình sẽ tự kiểm tra lại.');
    return;
  }

  if (err.status === 409 || firstLoad) {
    clearPoll();
    showOnly('tracking-error');
    setText('tracking-error-message', message);
    return;
  }

  showAlert('error', `${message} Dữ liệu vị trí gần nhất vẫn được giữ trên bản đồ.`);
}

function initMap() {
  if (map) {
    window.setTimeout(() => map.invalidateSize(), 80);
    return;
  }

  if (!window.L) {
    showMapState('Không tải được bản đồ', 'Thư viện Leaflet chưa sẵn sàng. Vui lòng tải lại trang.');
    return;
  }

  map = window.L.map('tracking-map').setView(HANOI_CENTER, 13);
  window.L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap',
  }).addTo(map);
  window.setTimeout(() => map.invalidateSize(), 120);
}

function renderLocation(location) {
  const lat = Number(location?.lat);
  const lng = Number(location?.lng);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    showMapState('Dữ liệu vị trí chưa hợp lệ', 'API chưa trả về tọa độ tài xế có thể hiển thị.');
    return;
  }

  initMap();
  if (!map) return;

  const point = [lat, lng];
  const icon = window.L.divIcon({
    className: '',
    html: '<div class="driver-marker" aria-hidden="true"></div>',
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  });

  if (!marker) {
    marker = window.L.marker(point, { icon }).addTo(map);
    map.setView(point, 15);
  } else {
    marker.setLatLng(point);
    map.panTo(point, { animate: true });
  }

  marker.bindPopup(`
    <strong>Vị trí tài xế</strong><br>
    Cập nhật: ${escapeHtml(formatDateTime(location.recorded_at || location.recordedAt))}<br>
    Tốc độ: ${escapeHtml(formatSpeed(location.speed_kmh || location.speedKmh))}
  `);

  hideMapState();
  renderLocationMeta(location);
}

function renderLocationMeta(location) {
  const recordedAt = location.recorded_at || location.recordedAt;
  const lastUpdated = formatDateTime(recordedAt);

  setText('last-updated', location.stale ? `${lastUpdated} (cũ)` : lastUpdated);
  setText('driver-speed', formatSpeed(location.speed_kmh || location.speedKmh));

  if (location.stale) {
    showAlert('warning', 'Vị trí tài xế đã quá 30 giây chưa cập nhật.');
  }
}

function renderOrderMeta() {
  const status = normalizeOrderStatus(orderMeta.orderStatus || 'IN_PROGRESS');
  const paymentStatus = normalizePaymentStatus(orderMeta.paymentStatus || inferPaymentStatus(status));
  const orderCode = orderMeta.orderCode || shortOrderId(orderId);

  setText('tracking-title', `Theo dõi đơn ${orderCode}`);
  setText('tracking-subtitle', 'Bản đồ hiển thị vị trí tài xế theo dữ liệu cập nhật từ hệ thống.');
  renderBadge('order-status-badge', ORDER_STATUSES[status], status);
  renderBadge('payment-status-badge', PAYMENT_STATUSES[paymentStatus], paymentStatus);
  setText('order-code', orderCode);
  setText('driver-name', orderMeta.driverName || 'Tài xế đang thực hiện đơn');
  setText('driver-vehicle', orderMeta.vehiclePlate || 'Thông tin tài xế sẽ hiển thị khi có dữ liệu.');
  setText('driver-avatar', initials(orderMeta.driverName || 'Tài xế'));

  const callLink = $('driver-call-link');
  if (callLink && orderMeta.driverPhone) {
    callLink.href = `tel:${orderMeta.driverPhone}`;
    callLink.classList.remove('hidden');
  }

  const detailLink = $('order-detail-link');
  if (detailLink) {
    detailLink.href = `order-detail.html?id=${encodeURIComponent(orderId)}`;
  }

  renderTimeline(status);
}

function renderTimeline(status) {
  const container = $('order-timeline');
  if (!container) return;

  const normalized = normalizeOrderStatus(status);
  const activeIndex = Math.max(0, TIMELINE.findIndex(([key]) => key === normalized));
  container.innerHTML = TIMELINE.map(([, label], index) =>
    `<div class="timeline-item ${index <= activeIndex ? 'active' : ''}">${escapeHtml(label)}</div>`
  ).join('');
}

function readOrderMeta(id) {
  const params = new URLSearchParams(window.location.search);
  const stored = readStoredMeta(id);
  return {
    orderCode: firstParam(params, ['order_code', 'orderCode', 'code']) || stored.orderCode || '',
    orderStatus: firstParam(params, ['order_status', 'orderStatus', 'status']) || stored.orderStatus || 'IN_PROGRESS',
    paymentStatus: firstParam(params, ['payment_status', 'paymentStatus']) || stored.paymentStatus || 'DEPOSIT_PAID',
    driverName: firstParam(params, ['driver_name', 'driverName']) || stored.driverName || '',
    driverPhone: firstParam(params, ['driver_phone', 'driverPhone']) || stored.driverPhone || '',
    vehiclePlate: firstParam(params, ['vehicle_plate', 'vehiclePlate']) || stored.vehiclePlate || '',
  };
}

function readStoredMeta(id) {
  const raw = localStorage.getItem(`moveHomeTrackingOrder:${id}`);
  if (!raw) return {};
  try {
    return JSON.parse(raw) || {};
  } catch {
    return {};
  }
}

function getOrderId() {
  const params = new URLSearchParams(window.location.search);
  return firstParam(params, ['order_id', 'orderId', 'id']);
}

function firstParam(params, names) {
  for (const name of names) {
    const value = params.get(name);
    if (value && value.trim()) return value.trim();
  }
  return '';
}

function renderBadge(id, config, fallback) {
  const el = $(id);
  if (!el) return;

  const [label, className] = config || [fallback || '—', 'badge-neutral'];
  el.className = `badge ${className}`;
  el.textContent = label;
}

function normalizeOrderStatus(status) {
  const value = String(status || '').trim().toUpperCase();
  if (value === 'ASSIGNED' || value === 'CONFIRMED') return 'ACCEPTED';
  return value;
}

function normalizePaymentStatus(status) {
  return String(status || '').trim().toUpperCase();
}

function inferPaymentStatus(status) {
  if (status === 'AWAITING_FINAL_PAYMENT') return 'AWAITING_FINAL_PAYMENT';
  if (status === 'COMPLETED') return 'PAID';
  if (status === 'PENDING') return 'PENDING';
  return 'DEPOSIT_PAID';
}

function showOnly(id) {
  ['tracking-empty', 'tracking-loading', 'tracking-error', 'tracking-content'].forEach((sectionId) => {
    $(sectionId)?.classList.toggle('hidden', sectionId !== id);
  });
}

function showMapState(title, body) {
  const el = $('map-state');
  if (!el) return;
  el.innerHTML = `
    <div>
      <h2>${escapeHtml(title)}</h2>
      <p class="text-muted mt-sm">${escapeHtml(body)}</p>
    </div>
  `;
  el.classList.remove('hidden');
}

function hideMapState() {
  $('map-state')?.classList.add('hidden');
}

function showAlert(type, message) {
  const el = $('tracking-alert');
  if (!el) return;
  el.className = `inline-alert ${type}`;
  el.textContent = message;
}

function hideAlert() {
  const el = $('tracking-alert');
  if (!el) return;
  el.className = 'inline-alert hidden';
  el.textContent = '';
}

function errorMessage(err) {
  const raw = err?.data?.message || err?.message || '';
  const message = String(raw).split('|').pop().trim();

  if (err?.isNetworkError) return 'Không thể kết nối máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  if (err?.status === 404) return message || 'Tài xế chưa cập nhật vị trí cho đơn này.';
  if (err?.status === 409) return message || 'Đơn hiện không ở trạng thái có thể theo dõi.';
  if (err?.status === 429) return message || 'Có quá nhiều yêu cầu. Vui lòng thử lại sau.';
  return message || 'Không thể tải dữ liệu theo dõi. Vui lòng thử lại.';
}

function formatDateTime(value) {
  if (!value) return '—';
  return new Date(value).toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
  });
}

function formatSpeed(value) {
  const speed = Number(value);
  return Number.isFinite(speed) ? `${speed.toFixed(1)} km/h` : '—';
}

function shortOrderId(id) {
  return `#${String(id).slice(0, 8).toUpperCase()}`;
}

function initials(name) {
  const words = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (!words.length) return 'KH';
  return words.map((word) => word[0]).slice(0, 2).join('').toUpperCase();
}

function setText(id, value) {
  const el = $(id);
  if (el) el.textContent = value || '—';
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function clearPoll() {
  if (!pollTimer) return;
  window.clearInterval(pollTimer);
  pollTimer = null;
}
