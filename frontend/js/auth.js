// Quản lý xác thực: đăng nhập, đăng ký, đăng xuất, kiểm tra trạng thái đăng nhập
// Token được lưu trong localStorage theo key 'accessToken' và 'userInfo'

import { post } from './api.js';

// Key lưu trữ trong localStorage
const TOKEN_KEY    = 'accessToken';
const USER_KEY     = 'userInfo';
const REFRESH_KEY  = 'refreshToken';

// Map role → URL tương đối (gọi từ pages/login.html, ngang cấp với pages/admin/, pages/customer/...)
const ROLE_HOME_URL = {
  ADMIN:    'admin/dashboard.html',
  MANAGER:  'manager/home.html',
  DRIVER:   'driver/home.html',
  CUSTOMER: 'customer/home.html',
};

/**
 * Đăng nhập bằng email + password.
 * Backend: POST /api/auth/login
 * @returns {Promise<object>} AuthResponse { accessToken, refreshToken, user, ... }
 */
export async function login(email, password) {
  const data = await post('/api/auth/login', { email, password });
  // Lưu token và thông tin user vào localStorage
  localStorage.setItem(TOKEN_KEY,   data.accessToken);
  localStorage.setItem(REFRESH_KEY, data.refreshToken || '');
  localStorage.setItem(USER_KEY,    JSON.stringify(data.user));
  return data;
}

/**
 * Đăng ký tài khoản Customer mới.
 * Backend: POST /api/auth/register/customer
 * @param {object} registerData - { email, password, fullName, phone?, termsAccepted }
 * @returns {Promise<object>} { userId, message }
 */
export async function register(registerData) {
  return await post('/api/auth/register/customer', registerData);
}

/**
 * Đăng ký tài khoản Tài xế mới (Onboarding Bước 1).
 * Backend: POST /api/auth/register/driver
 * @param {object} registerData - { email, password, fullName, phone?, termsAccepted }
 * @returns {Promise<object>} { userId, message }
 */
export async function registerDriver(registerData) {
  return await post('/api/auth/register/driver', registerData);
}

/**
 * Yêu cầu gửi email đặt lại mật khẩu. Backend luôn trả phản hồi trung lập.
 */
export async function forgotPassword(email) {
  return await post('/api/auth/forgot-password', { email });
}

/**
 * Đặt mật khẩu mới bằng token lấy từ query string của reset-password.html.
 */
export async function resetPassword(token, newPassword) {
  return await post('/api/auth/reset-password', { token, newPassword });
}

/**
 * Đăng xuất: xóa token trong localStorage và redirect đến trang đăng nhập.
 * @param {string} loginUrl - Đường dẫn tương đối đến trang đăng nhập
 */
export function logout(loginUrl = 'login.html') {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(REFRESH_KEY);
  window.location.href = loginUrl;
}

/**
 * Kiểm tra user đang đăng nhập (có token trong localStorage).
 * @returns {boolean}
 */
export function isLoggedIn() {
  return !!localStorage.getItem(TOKEN_KEY);
}

/**
 * Lấy thông tin user hiện tại từ localStorage.
 * @returns {object|null} UserInfo { id, email, fullName, role, status, mustChangePassword }
 */
export function getCurrentUser() {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/**
 * Lấy access token.
 * @returns {string|null}
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * Kiểm tra user có role cụ thể không.
 * @param {string} role - 'ADMIN', 'MANAGER', 'DRIVER', 'CUSTOMER'
 * @returns {boolean}
 */
export function hasRole(role) {
  const user = getCurrentUser();
  return user?.role === role;
}

/**
 * Redirect đến trang home của role sau khi đăng nhập thành công.
 * Gọi từ pages/login.html — các URL là tương đối trong thư mục pages/.
 * @param {object} user - UserInfo { role, ... }
 * @param {string} fallback - URL khi role không xác định (mặc định: login.html)
 */
// Tai xe chua hoan tat onboarding → dua ve dung buoc theo status (URL tuong doi tu pages/)
const DRIVER_ONBOARDING_URL = {
  PENDING_DOCUMENTS: 'driver/register-step2.html',
  PENDING_DEPOSIT:   'driver/register-step3-deposit.html',
  PENDING_APPROVAL:  'driver/pending-approval.html',
  REJECTED:          'driver/pending-approval.html',
};

export function redirectAfterLogin(user, fallback = 'login.html') {
  // Tai xe dang onboarding (chua ACTIVE) → dan toi dung buoc con dang do
  if (user?.role === 'DRIVER' && DRIVER_ONBOARDING_URL[user?.status]) {
    window.location.href = DRIVER_ONBOARDING_URL[user.status];
    return;
  }
  const url = ROLE_HOME_URL[user?.role] || fallback;
  window.location.href = url;
}

/**
 * Trả về URL trang home theo role (không có prefix).
 * Dùng để lấy URL mà không redirect ngay, ví dụ trong index.html.
 * @param {string} role
 * @param {string} prefix - Tiền tố path (vd: 'pages/')
 * @returns {string}
 */
export function getHomeUrl(role, prefix = '') {
  return prefix + (ROLE_HOME_URL[role] || 'login.html');
}
