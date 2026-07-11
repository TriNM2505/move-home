// Admin khóa/mở khóa tài khoản Tài xế & Khách hàng (đình chỉ có lý do).
//   Khóa:   POST /api/admin/users/{id}/suspend  { reason }   (lý do >= 30 ký tự — backend validate)
//   Mở khóa: POST /api/admin/users/{id}/activate
// Nút "Khóa tài khoản" trên driver-detail.html / customer-detail.html duoc thay bang button nay.

import { setupAdminPage } from './admin-common.js';
import { getAuthenticated, postAuthenticated } from './api.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const REASON_MIN_LENGTH = 30;

let currentStatus = null;
let currentUserId = null;

function cleanMessage(raw) {
  if (!raw) return '';
  return String(raw).split('|').pop().trim();
}

// Thay the link "Khóa tài khoản" (a.btn-danger) bang 1 button dieu khien duoc.
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

function renderButton(button, status) {
  currentStatus = status;
  button.disabled = false;
  button.classList.remove('btn-danger', 'btn-primary');

  if (status === 'SUSPENDED') {
    button.classList.add('btn-primary');
    button.textContent = 'Mở khóa tài khoản';
  } else {
    button.classList.add('btn-danger');
    button.textContent = 'Khóa tài khoản';
  }
}

async function loadStatus(button, userId) {
  button.disabled = true;
  button.textContent = 'Đang tải trạng thái...';
  const result = await getAuthenticated(`/api/admin/users/${userId}/status`);
  if (result) renderButton(button, result.status);
}

// ============================================================
// MODAL NHẬP LÝ DO KHÓA (dựng động, không cần sửa HTML từng trang)
// ============================================================
function buildSuspendModal() {
  if (document.getElementById('suspend-modal-overlay')) return;

  const overlay = document.createElement('div');
  overlay.id = 'suspend-modal-overlay';
  overlay.style.cssText = 'position:fixed; inset:0; background:rgba(0,0,0,0.5); z-index:2000; display:none; align-items:center; justify-content:center; padding:16px;';
  overlay.innerHTML = `
    <div style="width:100%; max-width:480px; background:var(--color-canvas); border-radius:16px; padding:var(--spacing-2xl); box-shadow:var(--shadow-level-3); max-height:90vh; overflow-y:auto;">
      <h3 style="font-size:var(--font-size-display-sm); font-weight:700; margin:0 0 var(--spacing-md);">Khóa tài khoản</h3>
      <p style="color:var(--color-body); font-size:var(--font-size-body-sm); margin:0 0 var(--spacing-lg);">Nhập lý do khóa. Người dùng sẽ thấy lý do này khi đăng nhập lại.</p>
      <div class="form-group">
        <label class="form-label" for="suspend-reason">Lý do khóa <span style="color:var(--color-danger);">*</span></label>
        <textarea id="suspend-reason" class="text-input" rows="4" maxlength="1000" placeholder="Nhập lý do khóa (tối thiểu 30 ký tự)..."></textarea>
        <div style="display:flex; justify-content:space-between; margin-top:4px;">
          <span class="form-error" id="suspend-reason-error" style="color:var(--color-danger); font-size:var(--font-size-body-sm); display:none;"></span>
          <span id="suspend-reason-count" style="color:var(--color-mute); font-size:var(--font-size-caption); margin-left:auto;">0/30</span>
        </div>
      </div>
      <div style="display:flex; gap:var(--spacing-md); justify-content:flex-end; margin-top:var(--spacing-lg);">
        <button type="button" class="btn-secondary" id="suspend-cancel-btn">Hủy</button>
        <button type="button" class="btn-danger" id="suspend-confirm-btn">Xác nhận khóa</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);

  const reasonEl = overlay.querySelector('#suspend-reason');
  const countEl = overlay.querySelector('#suspend-reason-count');
  reasonEl.addEventListener('input', () => {
    const len = reasonEl.value.trim().length;
    countEl.textContent = `${len}/30`;
    countEl.style.color = len >= REASON_MIN_LENGTH ? 'var(--color-success)' : 'var(--color-mute)';
    hideModalError();
  });
  overlay.querySelector('#suspend-cancel-btn').addEventListener('click', closeSuspendModal);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeSuspendModal(); });
}

function openSuspendModal() {
  buildSuspendModal();
  const overlay = document.getElementById('suspend-modal-overlay');
  const reasonEl = document.getElementById('suspend-reason');
  reasonEl.value = '';
  document.getElementById('suspend-reason-count').textContent = '0/30';
  hideModalError();
  overlay.style.display = 'flex';

  const confirmBtn = document.getElementById('suspend-confirm-btn');
  confirmBtn.onclick = submitSuspend;
  reasonEl.focus();
}

function closeSuspendModal() {
  const overlay = document.getElementById('suspend-modal-overlay');
  if (overlay) overlay.style.display = 'none';
}

function showModalError(msg) {
  const el = document.getElementById('suspend-reason-error');
  if (el) { el.textContent = msg; el.style.display = 'block'; }
}
function hideModalError() {
  const el = document.getElementById('suspend-reason-error');
  if (el) { el.textContent = ''; el.style.display = 'none'; }
}

async function submitSuspend() {
  const reason = document.getElementById('suspend-reason').value.trim();
  hideModalError();
  if (reason.length < REASON_MIN_LENGTH) {
    showModalError(`Lý do phải có ít nhất ${REASON_MIN_LENGTH} ký tự (hiện ${reason.length}).`);
    return;
  }

  const confirmBtn = document.getElementById('suspend-confirm-btn');
  confirmBtn.disabled = true;
  confirmBtn.textContent = 'Đang khóa...';
  try {
    await postAuthenticated(`/api/admin/users/${currentUserId}/suspend`, { reason });
    // Tai lai trang de cap nhat badge trang thai + nut
    window.location.reload();
  } catch (err) {
    console.error(err);
    showModalError(cleanMessage(err.message) || 'Không thể khóa tài khoản. Vui lòng thử lại.');
    confirmBtn.disabled = false;
    confirmBtn.textContent = 'Xác nhận khóa';
  }
}

async function activateAccount(button) {
  if (!window.confirm('Xác nhận mở khóa tài khoản này?')) return;
  button.disabled = true;
  button.textContent = 'Đang mở khóa...';
  try {
    await postAuthenticated(`/api/admin/users/${currentUserId}/activate`, {});
    window.location.reload();
  } catch (err) {
    console.error(err);
    window.alert(cleanMessage(err.message) || 'Không thể mở khóa tài khoản.');
    renderButton(button, currentStatus);
  }
}

document.addEventListener('DOMContentLoaded', async () => {
  if (!setupAdminPage('../login.html')) return;

  const button = createStatusButton();
  if (!button) return;

  const params = new URLSearchParams(window.location.search);
  currentUserId = params.get('id') || params.get('userId');

  if (!currentUserId || !UUID_PATTERN.test(currentUserId)) {
    button.disabled = true;
    button.title = 'Thiếu mã người dùng trên URL.';
    return;
  }

  button.addEventListener('click', () => {
    if (currentStatus === 'SUSPENDED') {
      activateAccount(button);
    } else {
      openSuspendModal();
    }
  });

  try {
    await loadStatus(button, currentUserId);
  } catch (error) {
    button.disabled = true;
    button.textContent = 'Không tải được trạng thái';
    window.alert(cleanMessage(error.message) || 'Không thể tải trạng thái tài khoản.');
  }
});
