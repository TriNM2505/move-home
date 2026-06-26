// Wrapper cho Fetch API — tu dong them Authorization header va xu ly loi
// Tat ca request di qua ham nay de dam bao consistency

const BASE_URL = 'http://localhost:8080';

/**
 * Ham request chinh — goi tat ca endpoint.
 * @param {string} method - HTTP method (GET, POST, PUT, DELETE)
 * @param {string} path - Duong dan API (vd: '/api/auth/login')
 * @param {object|null} body - Request body (null neu khong co)
 * @param {boolean} requiresAuth - Co can Bearer token khong
 * @returns {Promise<object>} Parsed JSON response
 * @throws {Error} Neu response khong OK, kem theo status va data
 */
async function apiRequest(method, path, body = null, requiresAuth = false) {
  const headers = { 'Content-Type': 'application/json' };

  if (requiresAuth) {
    const token = localStorage.getItem('accessToken');
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

