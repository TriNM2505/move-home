// Quan ly xac thuc: login, register, logout, kiem tra trang thai dang nhap
// Token duoc luu trong localStorage theo key 'accessToken' va 'userInfo'

import { post } from './api.js';

// Key luu tru trong localStorage
const TOKEN_KEY    = 'accessToken';
const USER_KEY     = 'userInfo';
const REFRESH_KEY  = 'refreshToken';

// Map role → URL tuong doi (goi tu pages/login.html, ngang cap voi pages/admin/, pages/customer/...)
const ROLE_HOME_URL = {
  ADMIN:    'admin/dashboard.html',
  MANAGER:  'manager/home.html',
  DRIVER:   'driver/home.html',
  CUSTOMER: 'customer/home.html',
};

/**
 * Dang nhap bang email + password.
 * Backend: POST /api/auth/login (chi ho tro email o Round 2)
 * @returns {Promise<object>} AuthResponse { accessToken, refreshToken, user, ... }
 */
export async function login(email, password) {
  const data = await post('/api/auth/login', { email, password });
  // Luu token va thong tin user
  localStorage.setItem(TOKEN_KEY,   data.accessToken);
  localStorage.setItem(REFRESH_KEY, data.refreshToken || '');
  localStorage.setItem(USER_KEY,    JSON.stringify(data.user));
  return data;
}

/**
 * Dang ky tai khoan Customer moi.
 * Backend: POST /api/auth/register/customer
 * @param {object} registerData - { email, password, fullName, phone?, termsAccepted }
 * @returns {Promise<object>} { userId, message }
 */
export async function register(registerData) {
  return await post('/api/auth/register/customer', registerData);
}

/**
 * Dang xuat: xoa token trong localStorage va redirect den trang login.
 * @param {string} loginUrl - Duong dan tuong doi den trang login
 */
export function logout(loginUrl = 'login.html') {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  localStorage.removeItem(REFRESH_KEY);
  window.location.href = loginUrl;
}

/**
 * Kiem tra user dang dang nhap (co token trong localStorage).
 * @returns {boolean}
 */
export function isLoggedIn() {
  return !!localStorage.getItem(TOKEN_KEY);
}

/**
 * Lay thong tin user hien tai tu localStorage.
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
 * Lay access token.
 * @returns {string|null}
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * Kiem tra user co role cu the khong.
 * @param {string} role - 'ADMIN', 'MANAGER', 'DRIVER', 'CUSTOMER'
 * @returns {boolean}
 */
export function hasRole(role) {
  const user = getCurrentUser();
  return user?.role === role;
}

/**
 * Redirect den trang home cua role sau khi login thanh cong.
 * Goi tu pages/login.html — cac URL la tuong doi trong thu muc pages/.
 * @param {object} user - UserInfo { role, ... }
 * @param {string} fallback - URL khi role khong xac dinh (mac dinh: login.html)
 */
export function redirectAfterLogin(user, fallback = 'login.html') {
  const url = ROLE_HOME_URL[user?.role] || fallback;
  window.location.href = url;
}

/**
 * Tra ve URL trang home theo role (khong co prefix).
 * Dung de lay URL ma khong redirect ngay, vi du trong index.html.
 * @param {string} role
 * @param {string} prefix - Tien to path (vd: 'pages/')
 * @returns {string}
 */
export function getHomeUrl(role, prefix = '') {
  return prefix + (ROLE_HOME_URL[role] || 'login.html');
}
