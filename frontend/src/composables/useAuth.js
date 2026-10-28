import { ref, computed } from 'vue';
import { api, AUTH_EXPIRED_EVENT } from '../api/client';

/** @type {import('vue').Ref<import('../model/auth').BadgeUser | null>} */
const user = ref(null);
const token = ref(localStorage.getItem('badge_auth_token'));
const loading = ref(true);

if (typeof window !== 'undefined') {
  // 任一请求续期失败都会广播登录态失效，这里统一回到未登录状态。
  window.addEventListener(AUTH_EXPIRED_EVENT, () => {
    token.value = null;
    user.value = null;
  });
}

export function useAuth() {
  const isAuthenticated = computed(() => !!user.value);
  const isAdmin = computed(() => user.value?.role === 'ROLE_ADMIN');

  const initAuth = async () => {
    if (token.value) {
      try {
        user.value = await api.getMe();
      } catch {
        logout();
      } finally {
        loading.value = false;
      }
    } else {
      loading.value = false;
    }
  };

  const login = async (username, password, clientId = null) => {
    const res = await api.login({ username, password, clientId });
    applySession(res);
    return res;
  };

  const register = async (username, email, password, phone = null) => {
    const res = await api.register({ username, email, password, phone });
    applySession(res);
    return res;
  };

  const applySession = (res) => {
    localStorage.setItem('badge_auth_token', res.accessToken);
    if (res.refreshToken) localStorage.setItem('badge_auth_refresh', res.refreshToken);
    token.value = res.accessToken;
    user.value = res.user;
  };

  const logout = () => {
    localStorage.removeItem('badge_auth_token');
    localStorage.removeItem('badge_auth_refresh');
    token.value = null;
    user.value = null;
  };

  const updateProfile = async (data) => {
    const updatedUser = await api.updateProfile(data);
    user.value = updatedUser;
    return updatedUser;
  };

  return {
    user,
    token,
    isAuthenticated,
    isAdmin,
    loading,
    initAuth,
    login,
    register,
    logout,
    updateProfile,
  };
}
