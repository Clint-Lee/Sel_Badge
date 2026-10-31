<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">安全动态</h1>
        <p class="page-desc">身份中心状态与最近的关键动作，明细仅管理员可见。</p>
      </div>
    </div>

    <div v-if="stats" class="stats-strip card">
      <div class="stat">
        <span class="stat-num num">{{ stats.activeUsersCount }}</span>
        <span class="stat-label">注册用户</span>
      </div>
      <div class="stat">
        <span class="stat-num num">{{ stats.activeAppsCount }}</span>
        <span class="stat-label">已接入应用</span>
      </div>
      <div class="stat">
        <span class="stat-num num">{{ stats.totalAuditLogs }}</span>
        <span class="stat-label">审计记录</span>
      </div>
      <div class="stat">
        <span class="stat-num num" :class="{ 'stat-warn': stats.failedAttemptsCount > 0 }">
          {{ stats.failedAttemptsCount }}
        </span>
        <span class="stat-label">失败尝试</span>
      </div>
      <div class="stat algo">
        <span class="stat-algo mono">BCrypt-12 · AES-256-GCM · 一次性票据</span>
        <span class="stat-label">加密与协议</span>
      </div>
    </div>

    <div class="card table-card">
      <div class="table-head">
        <h3 class="section-title">最近动态</h3>
        <span v-if="isAdminDenied" class="badge badge-gray">明细需管理员权限</span>
        <button v-else class="btn btn-ghost btn-sm" @click="loadLogs">
          <RefreshCw :size="13" :class="{ spin: loading }" />
          <span>刷新</span>
        </button>
      </div>

      <div v-if="loading" class="loading-panel">
        <Loader2 :size="15" class="spin" />
        <span>载入审计记录…</span>
      </div>

      <div v-else-if="isAdminDenied" class="empty">
        <span class="empty-icon"><Lock :size="17" /></span>
        <div class="empty-title">审计明细仅对管理员开放</div>
        <div class="empty-desc">审计记录包含所有用户的 IP 与设备信息，属于运营者视角的数据。普通用户可以看到上方统计。</div>
      </div>

      <div v-else-if="!logs.length" class="empty">
        <span class="empty-icon"><Activity :size="17" /></span>
        <div class="empty-title">还没有记录</div>
        <div class="empty-desc">登录、注册与单点登录动作会出现在这里。</div>
      </div>

      <div v-else class="table-scroll">
      <table class="table">
        <thead>
          <tr>
            <th>时间</th>
            <th>动作</th>
            <th>用户</th>
            <th>应用</th>
            <th>来源 IP</th>
            <th>结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td class="mono time-cell">{{ formatTime(log.createdAt) }}</td>
            <td>
              <span class="badge" :class="actionBadge(log.action)">{{ actionLabel(log.action) }}</span>
            </td>
            <td>{{ log.username || '—' }}</td>
            <td class="mono client-cell">{{ log.clientId || '—' }}</td>
            <td class="mono client-cell">{{ log.ipAddress || '—' }}</td>
            <td>
              <span class="badge" :class="log.status === 'SUCCESS' ? 'badge-green' : 'badge-red'">
                {{ log.status === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
  </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue';
import { api } from '../api/client';
import { RefreshCw, Loader2, Lock, Activity } from 'lucide-vue-next';

const logs = ref([]);
const stats = ref(null);
const loading = ref(true);
const isAdminDenied = ref(false);

const loadLogs = async () => {
  loading.value = true;
  isAdminDenied.value = false;
  try {
    logs.value = (await api.getAuditLogs()) || [];
  } catch (err) {
    if (err.status === 403) isAdminDenied.value = true;
    logs.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(async () => {
  loadLogs();
  try {
    stats.value = await api.getSecurityStatus();
  } catch {
    stats.value = null;
  }
});

const formatTime = (dateStr) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleString('zh-CN', {
      month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  } catch {
    return dateStr;
  }
};

const actionLabel = (action) => ({
  LOGIN_SUCCESS: '登录', LOGIN_FAILED: '登录失败', LOGIN_BLOCKED: '登录拦截',
  REGISTER: '注册', PASSWORD_CHANGE: '改密', PROFILE_UPDATE: '资料更新',
  SSO_AUTHORIZE: '签发票据', SSO_EXCHANGE_FAILED: '换票失败', TOKEN_REFRESH: '续期',
}[action] || action);

const actionBadge = (action) => {
  if (action.includes('FAIL') || action.includes('BLOCKED')) return 'badge-red';
  if (action.includes('SSO') || action.includes('REFRESH')) return 'badge-blue';
  return 'badge-gray';
};
</script>

<style scoped>
.stats-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr) auto;
  gap: 0;
  margin-bottom: 16px;
  overflow: hidden;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 16px 20px;
}

.stat + .stat { border-left: 1px solid var(--line); }

.stat-num {
  font-size: 22px;
  font-weight: 800;
  color: var(--ink);
  line-height: 1.25;
}

.stat-warn { color: var(--red); }

.stat-label { font-size: 14px; color: var(--ink-2); font-weight: 550; }

.stat.algo { justify-content: center; }

.stat-algo { font-size: 14px; color: var(--ink-2); }

.table-card { overflow: hidden; }

.table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px 11px;
}

.loading-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 40px;
  color: var(--ink-2);
  font-size: 14px;
}

.time-cell { font-size: 14px; white-space: nowrap; }
.client-cell { font-size: 14px; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 880px) {
  .stats-strip { grid-template-columns: 1fr 1fr; }
  .stat.algo { display: none; }
}
</style>
