const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const TOKEN_KEY = 'badge_auth_token';
const REFRESH_KEY = 'badge_auth_refresh';

class ApiError extends Error {
  constructor(message, code, status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

/** 登录态过期时广播给 UI（打开登录窗）；由 useAuth 监听。 */
export const AUTH_EXPIRED_EVENT = 'badge:auth-expired';

let refreshInFlight = null;

async function doRefresh() {
  const refreshToken = localStorage.getItem(REFRESH_KEY);
  if (!refreshToken) return false;
  try {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;
    const json = await response.json();
    if (json.code !== 200 || !json.data?.accessToken) return false;
    localStorage.setItem(TOKEN_KEY, json.data.accessToken);
    localStorage.setItem(REFRESH_KEY, json.data.refreshToken);
    return true;
  } catch {
    return false;
  }
}

/** 401 时用 refresh token 单飞续期一次，成功后重放原请求。 */
export async function request(endpoint, options = {}, allowRetry = true) {
  const token = localStorage.getItem(TOKEN_KEY);
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  };

  const response = await fetch(`${API_BASE_URL}${endpoint}`, { ...options, headers });
  const json = await response.json().catch(() => ({}));

  if (response.status === 401 && allowRetry && localStorage.getItem(REFRESH_KEY)) {
    refreshInFlight ||= doRefresh().finally(() => { refreshInFlight = null; });
    const renewed = await refreshInFlight;
    if (renewed) return request(endpoint, options, false);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    window.dispatchEvent(new CustomEvent(AUTH_EXPIRED_EVENT));
  }

  if (!response.ok || (json.code && json.code !== 200)) {
    const errorMsg = json.message || `请求失败（${response.status}）`;
    throw new ApiError(errorMsg, json.code || response.status, response.status);
  }

  return json.data;
}

export const api = {
  // Auth
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  getMe: () => request('/auth/me'),
  updateProfile: (data) => request('/auth/profile', { method: 'PUT', body: JSON.stringify(data) }),
  changePassword: (data) => request('/auth/change-password', { method: 'POST', body: JSON.stringify(data) }),

  // Apps Registry
  getApps: () => request('/apps'),
  getPublicApps: () => request('/apps/public'),
  createApp: (data) => request('/apps', { method: 'POST', body: JSON.stringify(data) }),

  // Multi-App Profile
  updateAppProfile: (clientId, data) =>
    request(`/profiles/apps/${clientId}`, { method: 'PUT', body: JSON.stringify(data) }),

  // SSO Flow
  ssoAuthorize: (data) => request('/sso/authorize', { method: 'POST', body: JSON.stringify(data) }),
  ssoTokenExchange: (data) => request('/sso/token', { method: 'POST', body: JSON.stringify(data) }),

  // Security & Audit
  getAuditLogs: () => request('/audit/logs'),
  getSecurityStatus: () => request('/security/status'),
};
