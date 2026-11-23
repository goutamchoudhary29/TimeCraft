export const STORAGE_KEYS = {
  ACCESS: 'tc_access_token',
  REFRESH: 'tc_refresh_token',
  USER: 'tc_user',
  GUEST: 'tc_guest_token',
  COMPARE: 'tc_compare_watches',
};

export const FREE_SHIPPING_THRESHOLD = 10000;

const inrFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export function formatInr(amount) {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) return '₹0';
  return inrFormatter.format(Number(amount));
}

export function getStoredUser() {
  try {
    const raw = localStorage.getItem(STORAGE_KEYS.USER);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveAuthSession(authResponse) {
  if (!authResponse) return;
  if (authResponse.accessToken) localStorage.setItem(STORAGE_KEYS.ACCESS, authResponse.accessToken);
  if (authResponse.refreshToken) localStorage.setItem(STORAGE_KEYS.REFRESH, authResponse.refreshToken);
  if (authResponse.user) localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(authResponse.user));
}

export function clearAuthSession() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS);
  localStorage.removeItem(STORAGE_KEYS.REFRESH);
  localStorage.removeItem(STORAGE_KEYS.USER);
}

async function tryRefreshToken() {
  const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH);
  if (!refreshToken) return false;
  try {
    const res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      clearAuthSession();
      return false;
    }
    const data = await res.json();
    saveAuthSession(data);
    return true;
  } catch {
    return false;
  }
}

export async function api(path, options = {}, retried = false) {
  const headers = { Accept: 'application/json', ...(options.headers || {}) };
  if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS);
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const guestToken = localStorage.getItem(STORAGE_KEYS.GUEST);
  if (guestToken) headers['X-Guest-Token'] = guestToken;

  const res = await fetch(`/api/v1${path}`, {
    method: options.method || 'GET',
    headers,
    credentials: 'same-origin',
    body:
      options.body && !(options.body instanceof FormData) && typeof options.body !== 'string'
        ? JSON.stringify(options.body)
        : options.body,
  });

  const returnedGuest = res.headers.get('X-Guest-Token');
  if (returnedGuest) localStorage.setItem(STORAGE_KEYS.GUEST, returnedGuest);

  if (res.status === 401 && !retried && !path.startsWith('/auth/')) {
    const refreshed = await tryRefreshToken();
    if (refreshed) return api(path, options, true);
  }

  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { message: text };
  }
  if (!res.ok) {
    const err = new Error((data && data.message) || `Request failed (${res.status})`);
    err.status = res.status;
    err.payload = data;
    throw err;
  }
  return data;
}
