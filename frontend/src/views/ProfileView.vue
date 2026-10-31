<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">个人资料</h1>
      <p class="page-desc">全局身份，所有应用共享；应用内昵称请到「应用画像」单独设置。</p>
    </div>

    <div v-if="!user" class="card">
      <div class="empty">
        <span class="empty-icon"><UserRound :size="20" /></span>
        <div class="empty-title">请先登录</div>
        <div class="empty-desc">登录后可以查看与编辑你的全局资料。</div>
        <router-link to="/login" class="btn btn-primary btn-sm">前往登录</router-link>
      </div>
    </div>

    <div v-else class="profile-grid">
      <!-- 身份卡片 -->
      <section class="card identity">
        <div class="identity-avatar">
          <img v-if="avatarSrc" :src="avatarSrc" alt="头像" @error="avatarSrc = ''" />
          <span v-else class="avatar-letter">{{ user.username.slice(0, 1).toUpperCase() }}</span>
        </div>
        <div class="identity-name">{{ user.username }}</div>
        <div class="identity-mail mono">{{ user.email }}</div>
        <div class="identity-badges">
          <span class="badge" :class="user.role === 'ROLE_ADMIN' ? 'badge-green' : 'badge-gray'">
            {{ user.role === 'ROLE_ADMIN' ? '管理员' : '成员' }}
          </span>
          <span class="badge badge-gray">{{ formatDate(user.createdAt) }} 加入</span>
        </div>
      </section>

      <!-- 基本资料 -->
      <section class="card pad">
        <h3 class="section-title">基本资料</h3>
        <form class="fields" @submit.prevent="saveProfile">
          <div class="field">
            <label class="field-label">邮箱</label>
            <input v-model="formEmail" class="input" type="email" autocomplete="email" />
          </div>
          <div class="field">
            <label class="field-label">手机号 <span class="field-hint">（加密存储，{{ user.phoneMasked || '未设置' }}）</span></label>
            <input v-model="formPhone" class="input" autocomplete="tel" placeholder="留空表示清除" />
          </div>
          <div class="field">
            <label class="field-label">头像地址 <span class="field-hint">（选填）</span></label>
            <input v-model="formAvatar" class="input input-mono" placeholder="https://…" />
          </div>

          <div v-if="profileMsg" class="alert alert-success">{{ profileMsg }}</div>
          <div v-if="profileError" class="alert alert-error">{{ profileError }}</div>

          <div class="form-actions">
            <button class="btn btn-primary" :disabled="savingProfile">
              <Loader2 v-if="savingProfile" :size="14" class="spin" />
              <span>保存资料</span>
            </button>
          </div>
        </form>
      </section>

      <!-- 修改密码 -->
      <section class="card pad span-2">
        <h3 class="section-title">修改密码</h3>
        <form class="fields pwd-form" @submit.prevent="savePassword">
          <div class="field">
            <label class="field-label">当前密码</label>
            <div class="pwd-wrap">
              <input v-model="oldPassword" class="input" type="password" autocomplete="current-password" />
              <button type="button" class="pwd-eye" @click="showOld = !showOld">
                <component :is="showOld ? EyeOff : Eye" :size="15" />
              </button>
            </div>
          </div>
          <div class="field">
            <label class="field-label">新密码</label>
            <div class="pwd-wrap">
              <input v-model="newPassword" class="input" :type="showNew ? 'text' : 'password'" autocomplete="new-password" placeholder="至少 8 位" />
              <button type="button" class="pwd-eye" @click="showNew = !showNew">
                <component :is="showNew ? EyeOff : Eye" :size="15" />
              </button>
            </div>
          </div>
          <div class="field">
            <label class="field-label">确认新密码</label>
            <div class="pwd-wrap">
              <input v-model="confirmPassword" class="input" :type="showConfirm ? 'text' : 'password'" autocomplete="new-password" />
              <button type="button" class="pwd-eye" @click="showConfirm = !showConfirm">
                <component :is="showConfirm ? EyeOff : Eye" :size="15" />
              </button>
            </div>
          </div>

          <div v-if="pwdMsg" class="alert alert-success">{{ pwdMsg }}</div>
          <div v-if="pwdError" class="alert alert-error">{{ pwdError }}</div>

          <div class="form-actions">
            <button class="btn btn-primary" :disabled="savingPwd">
              <Loader2 v-if="savingPwd" :size="14" class="spin" />
              <span>更新密码</span>
            </button>
          </div>
        </form>
      </section>

      <!-- 最近登录与安全活动 -->
      <section class="card pad span-2">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
          <div>
            <h3 class="section-title" style="margin-bottom: 2px;">最近登录与安全活动</h3>
            <span style="font-size: 12px; color: var(--ink-3);">记录你的账号登录（含接入的各子应用）及敏感操作日志</span>
          </div>
          <button type="button" class="btn btn-ghost btn-sm" @click="loadMyLogs" :disabled="loadingLogs">
            <RefreshCw :size="13" :class="{ spin: loadingLogs }" />
            <span>刷新</span>
          </button>
        </div>

        <div v-if="loadingLogs" class="loading-panel" style="padding: 24px; text-align: center; color: var(--ink-3);">
          <Loader2 :size="16" class="spin" style="margin-right: 6px; display: inline-block; vertical-align: middle;" />
          <span>正在加载活动日志…</span>
        </div>
        <div v-else-if="!myLogs.length" class="empty" style="padding: 20px; text-align: center; color: var(--ink-3);">
          <span>暂无最近活动记录</span>
        </div>
        <div v-else style="overflow-x: auto;">
          <table class="table" style="width: 100%; border-collapse: collapse; font-size: 13px;">
            <thead>
              <tr style="border-bottom: 1px solid var(--border); text-align: left; color: var(--ink-2);">
                <th style="padding: 8px 10px;">时间</th>
                <th style="padding: 8px 10px;">动作</th>
                <th style="padding: 8px 10px;">来源应用</th>
                <th style="padding: 8px 10px;">IP / 状态</th>
                <th style="padding: 8px 10px;">详情</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in myLogs" :key="log.id" style="border-bottom: 1px solid var(--surface-2);">
                <td style="padding: 8px 10px;" class="mono">{{ formatDate(log.createdAt) }}</td>
                <td style="padding: 8px 10px;">
                  <span class="badge" :class="log.status === 'SUCCESS' ? 'badge-green' : 'badge-warn'">
                    {{ formatAction(log.action) }}
                  </span>
                </td>
                <td style="padding: 8px 10px;"><span class="mono">{{ log.clientId || 'Badge 门户' }}</span></td>
                <td style="padding: 8px 10px;" class="mono">{{ log.ipAddress || '—' }}</td>
                <td style="padding: 8px 10px; color: var(--ink-2);">{{ log.details || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { useAuth } from '../composables/useAuth';
import { api } from '../api/client';
import { UserRound, Loader2, Eye, EyeOff, RefreshCw } from 'lucide-vue-next';

const { user, updateProfile } = useAuth();

const formEmail = ref('');
const formPhone = ref('');
const formAvatar = ref('');
const savingProfile = ref(false);
const profileMsg = ref('');
const profileError = ref('');

const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const showOld = ref(false);
const showNew = ref(false);
const showConfirm = ref(false);
const savingPwd = ref(false);
const pwdMsg = ref('');
const pwdError = ref('');

const hydrate = () => {
  if (user.value) {
    formEmail.value = user.value.email || '';
    formPhone.value = '';
    formAvatar.value = user.value.avatarUrl || '';
    avatarSrc.value = user.value.avatarUrl || '';
  }
};

const avatarSrc = ref(user.value?.avatarUrl || '');

onMounted(hydrate);
onMounted(() => window.addEventListener('badge:auth-changed', hydrate));
onUnmounted(() => window.removeEventListener('badge:auth-changed', hydrate));

const saveProfile = async () => {
  savingProfile.value = true;
  profileMsg.value = '';
  profileError.value = '';
  try {
    await updateProfile({
      email: formEmail.value.trim() || null,
      phone: formPhone.value.trim() || null,
      avatarUrl: formAvatar.value.trim() || null,
    });
    profileMsg.value = '资料已更新';
    formPhone.value = '';
    avatarSrc.value = formAvatar.value.trim() || '';
  } catch (err) {
    profileError.value = err.message || '更新失败，请稍后再试';
  } finally {
    savingProfile.value = false;
  }
};

const myLogs = ref([]);
const loadingLogs = ref(false);

const formatAction = (action) => {
  const map = {
    LOGIN_SUCCESS: '登录成功',
    LOGIN_FAILED: '登录失败',
    SSO_LOGIN_SUCCESS: '单点登录',
    SSO_AUTHORIZE: 'SSO授权',
    PASSWORD_CHANGE: '修改密码',
    PROFILE_UPDATE: '更新资料',
    REGISTER: '注册'
  };
  return map[action] || action;
};

const loadMyLogs = async () => {
  if (!user.value) return;
  loadingLogs.value = true;
  try {
    const res = await api.get('/api/audit/my-logs');
    myLogs.value = res.data || [];
  } catch (err) {
    console.warn('获取个人活动日志失败:', err);
  } finally {
    loadingLogs.value = false;
  }
};

onMounted(loadMyLogs);

const savePassword = async () => {
  pwdMsg.value = '';
  pwdError.value = '';
  if (newPassword.value !== confirmPassword.value) {
    pwdError.value = '两次输入的新密码不一致';
    return;
  }
  if (newPassword.value.length < 8) {
    pwdError.value = '新密码至少需要 8 个字符';
    return;
  }

  savingPwd.value = true;
  try {
    await api.changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value });
    pwdMsg.value = '密码已更新，下次登录请使用新密码。';
    oldPassword.value = newPassword.value = confirmPassword.value = '';
  } catch (err) {
    pwdError.value = err.message || '密码修改失败，请核对当前密码';
  } finally {
    savingPwd.value = false;
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '刚刚';
  try {
    return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
  } catch {
    return dateStr;
  }
};
</script>

<style scoped>
.profile-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 14px;
  align-items: start;
}

.span-2 { grid-column: 1 / -1; }

.identity {
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 4px;
}

.identity-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-tint);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.identity-avatar img { width: 100%; height: 100%; object-fit: cover; }

.avatar-letter { font-size: 28px; font-weight: 750; color: var(--accent-ink); }

.identity-name { font-size: 19px; font-weight: 750; color: var(--ink); }

.identity-mail { font-size: 14px; color: var(--ink-2); word-break: break-all; }

.identity-badges {
  display: flex;
  gap: 6px;
  margin-top: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

.pad { padding: 20px; }

.section-title { font-size: 16px; font-weight: 700; margin-bottom: 18px; color: var(--ink); }

.fields { display: flex; flex-direction: column; gap: 14px; }

.pwd-form {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 14px;
  align-items: start;
}

.pwd-form .form-actions { grid-column: 1 / -1; }

.form-actions { display: flex; justify-content: flex-end; margin-top: 2px; }

.pwd-wrap { position: relative; }

.pwd-wrap .input { padding-right: 38px; }

.pwd-eye {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pwd-eye:hover { color: var(--ink); background: var(--surface-2); }

@media (max-width: 860px) {
  .profile-grid { grid-template-columns: 1fr; }
}
</style>
