// Wrapper cho Fetch API — tu dong them Authorization header va xu ly loi
// Tat ca request di qua ham nay de dam bao consistency.
// AC-03: access token het han (401) → tu dong goi /api/auth/refresh (rotation) va retry 1 lan;
// refresh that bai → xoa phien + dua ve trang dang nhap.

const BASE_URL = 'http://localhost:8080';

const TOKEN_KEY   = 'accessToken';
const REFRESH_KEY = 'refreshToken';
const USER_KEY    = 'userInfo';

// Single-flight: nhieu request 401 cung luc chi refresh 1 lan (chong revoke chuoi rotation)
let refreshInFlight = null;

/**
 * Goi POST /api/auth/refresh bang refresh token trong localStorage.
 * Thanh cong → luu access + refresh token moi (rotation), tra ve true.
 * That bai (token het han 7 ngay / da bi revoke) → tra ve false.
 */
async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${BASE_URL}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;

    const data = await response.json();
    if (!data || !data.accessToken) return false;

    localStorage.setItem(TOKEN_KEY, data.accessToken);
    if (data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken);
    if (data.user) localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    return true;
  } catch {
    // Loi mang khi refresh → coi nhu that bai, KHONG xoa phien (thu lai o request sau)
    return false;
  }
}

/** Xoa phien va dua ve trang dang nhap (tinh duong dan tu vi tri file trong /pages/). */
function redirectToLogin() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
  const path = window.location.pathname;
  const idx = path.indexOf('/pages/');
  const loginUrl = idx >= 0 ? path.substring(0, idx + 7) + 'login.html' : 'login.html';
  window.location.href = loginUrl;
}

/**
 * Ham request chinh — goi tat ca endpoint.
 * @param {string} method - HTTP method (GET, POST, PUT, DELETE)
 * @param {string} path - Duong dan API (vd: '/api/auth/login')
 * @param {object|null} body - Request body (null neu khong co)
 * @param {boolean} requiresAuth - Co can Bearer token khong
 * @param {boolean} isRetry - Noi bo: request nay la lan retry sau refresh (khong retry tiep)
 * @returns {Promise<object>} Parsed JSON response
 * @throws {Error} Neu response khong OK, kem theo status va data
 */
async function apiRequest(method, path, body = null, requiresAuth = false, isRetry = false) {
  const headers = { 'Content-Type': 'application/json' };

  if (requiresAuth) {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  const options = { method, headers };
  if (body !== null) {
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, options);
  } catch (networkError) {
    // Loi mang (offline, CORS, server down)
    const err = new Error('Khong the ket noi den may chu. Vui long kiem tra mang.');
    err.isNetworkError = true;
    throw err;
  }

  // Access token het han → refresh (1 lan duy nhat) roi goi lai request goc
  if (response.status === 401 && requiresAuth && !isRetry) {
    if (!refreshInFlight) {
      refreshInFlight = refreshAccessToken().finally(() => { refreshInFlight = null; });
    }
    const refreshed = await refreshInFlight;
    if (refreshed) {
      return apiRequest(method, path, body, requiresAuth, true);
    }
    // Refresh that bai va dang co refresh token (phien thuc su het han) → ve trang dang nhap
    if (localStorage.getItem(REFRESH_KEY)) {
      redirectToLogin();
    }
  }

  // Xu ly response khong OK (4xx, 5xx)
  if (!response.ok) {
    let errorData = {};
    try {
      errorData = await response.json();
    } catch {
      errorData = { message: `Loi HTTP ${response.status}` };
    }
    const err = new Error(errorData.message || `Loi ${response.status}`);
    err.status = response.status;
    err.data = errorData;
    throw err;
  }

  // Tra ve null neu response khong co body (204 No Content)
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

// Cac ham tien ich de goi API
export const get              = (path)         => apiRequest('GET',  path, null,  false);
export const post             = (path, body)   => apiRequest('POST', path, body,  false);
export const getAuthenticated = (path)         => apiRequest('GET',  path, null,  true);
export const postAuthenticated = (path, body)  => apiRequest('POST', path, body,  true);
export const putAuthenticated  = (path, body)  => apiRequest('PUT',  path, body,  true);
