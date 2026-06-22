import { fetchWithAuth, setupAdminPage } from './admin-common.js';

const API_BASE_URL = 'http://localhost:8080';
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function createStatusButton() {
  const placeholder = document.querySelector('a.btn-danger');
  if (!placeholder) return null;

  const button = document.createElement('button');
  button.type = 'button';
  button.id = 'account-status-toggle';
  button.className = 'btn-danger';
  button.textContent = 'Khóa tài khoản';
  placeholder.replaceWith(button);
  return button;
}

function renderStatus(button, status) {
  button.dataset.status = status;
  button.disabled = false;
  button.classList.remove('btn-danger', 'btn-primary');

  if (status === 'LOCKED') {
    button.classList.add('btn-primary');
    button.textContent = 'Mở khóa tài khoản';
    return;
  }

  if (status === 'ACTIVE') {
    button.classList.add('btn-danger');
    button.textContent = 'Khóa tài khoản';
    return;
  }

  button.classList.add('btn-danger');
  button.textContent = 'Không thể khóa ở trạng thái hiện tại';
  button.disabled = true;
}

async function loadStatus(button, userId) {
  button.disabled = true;
  button.textContent = 'Đang tải trạng thái...';
  const result = await fetchWithAuth(`${API_BASE_URL}/api/admin/users/${userId}/status`);
  if (result) renderStatus(button, result.status);
}

async function toggleStatus(button, userId) {
  const nextStatus = button.dataset.status === 'LOCKED' ? 'ACTIVE' : 'LOCKED';
  const actionLabel = nextStatus === 'LOCKED' ? 'khóa' : 'mở khóa';
  if (!window.confirm(`Xác nhận ${actionLabel} tài khoản này?`)) return;

  button.disabled = true;
  button.textContent = 'Đang cập nhật...';

  try {
    const result = await fetchWithAuth(
      `${API_BASE_URL}/api/admin/users/${userId}/status`,
      { method: 'PATCH', body: JSON.stringify({ status: nextStatus }) },
    );
    if (result) renderStatus(button, result.status);
  } catch (error) {
    renderStatus(button, button.dataset.status);
    window.alert(error.message || 'Không thể cập nhật trạng thái tài khoản.');
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  if (!setupAdminPage('../login.html')) return;

  const button = createStatusButton();
  if (!button) return;

  const params = new URLSearchParams(window.location.search);
  const userId = params.get('id') || params.get('userId');

  if (!userId || !UUID_PATTERN.test(userId)) {
    button.disabled = true;
    button.title = 'Thiếu mã người dùng trên URL.';
    return;
  }

  button.addEventListener('click', () => toggleStatus(button, userId));

  try {
    await loadStatus(button, userId);
  } catch (error) {
    button.disabled = true;
    button.textContent = 'Không tải được trạng thái';
    window.alert(error.message || 'Không thể tải trạng thái tài khoản.');
  }
});
