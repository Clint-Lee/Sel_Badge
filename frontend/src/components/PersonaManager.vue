<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">应用画像</h1>
        <p class="page-desc">你在每个应用里的昵称、角色与私有配置，彼此隔离。</p>
      </div>
    </div>

    <div v-if="loading" class="card loading-panel">
      <Loader2 :size="16" class="spin" />
      <span>正在载入画像…</span>
    </div>

    <div v-else-if="!isAuthenticated" class="card">
      <div class="empty">
        <span class="empty-icon"><UserRound :size="18" /></span>
        <div class="empty-title">请先登录</div>
        <div class="empty-desc">画像属于已登录用户，登录后即可查看与编辑。</div>
        <router-link to="/login" class="btn btn-primary btn-sm">前往登录</router-link>
      </div>
    </div>

    <div v-else-if="apps.length === 0" class="card">
      <div class="empty">
        <span class="empty-icon"><Users :size="18" /></span>
        <div class="empty-title">还没有应用</div>
        <div class="empty-desc">管理员注册应用并完成首次进入后，画像会自动创建。</div>
      </div>
    </div>

    <div v-else class="card table-card">
      <div class="table-scroll">
      <table class="table">
        <thead>
          <tr>
            <th>应用</th>
            <th>应用内昵称</th>
            <th>角色</th>
            <th>最近进入</th>
            <th style="width: 150px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="app in apps" :key="app.clientId">
            <tr>
              <td>
                <div class="app-cell">
                  <span class="app-dot" :class="app.appCategory.toLowerCase()"></span>
                  <div class="app-cell-text">
                    <span class="app-cell-name">{{ app.appName }}</span>
                    <span class="app-cell-id mono">{{ app.clientId }}</span>
                  </div>
                </div>
              </td>
              <td>{{ app.boundNickname || '—' }}</td>
              <td><span class="mono role-text">{{ app.boundRole || '—' }}</span></td>
              <td class="time-cell">{{ formatDate(app.lastLoginAt) }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="app.appMetadata"
                    class="btn btn-ghost btn-sm"
                    @click="toggle(app.clientId)"
                  >
                    <ChevronDown :size="13" class="chev" :class="{ open: expanded.has(app.clientId) }" />
                    <span>配置</span>
                  </button>
                  <button class="btn btn-secondary btn-sm" @click="$emit('edit-profile', app)">
                    <Pencil :size="13" />
                    <span>编辑</span>
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="expanded.has(app.clientId)" class="expand-row">
              <td colspan="5">
                <div class="expand-inner">
                  <span class="mini-label">私有配置（加密存储，此处为你本人解密预览）</span>
                  <pre class="json-block">{{ pretty(app.appMetadata) }}</pre>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      </div>
  </div>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue';
import { useAuth } from '../composables/useAuth';
import { api } from '../api/client';
import { Loader2, UserRound, Users, Pencil, ChevronDown } from 'lucide-vue-next';

defineEmits(['edit-profile']);

const { isAuthenticated } = useAuth();

const apps = ref([]);
const loading = ref(true);
const expanded = reactive(new Set());

const load = async () => {
  loading.value = true;
  try {
    apps.value = isAuthenticated.value ? await api.getApps() : [];
  } catch {
    apps.value = [];
  } finally {
    loading.value = false;
  }
};

onMounted(load);
onMounted(() => window.addEventListener('badge:auth-changed', load));
onUnmounted(() => window.removeEventListener('badge:auth-changed', load));

const toggle = (clientId) => {
  if (expanded.has(clientId)) expanded.delete(clientId);
  else expanded.add(clientId);
};

const pretty = (raw) => {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    return raw;
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '尚未进入';
  try {
    return new Date(dateStr).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return dateStr;
  }
};
</script>

<style scoped>
.loading-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 48px;
  color: var(--ink-3);
  font-size: 13px;
}

.table-card { overflow: hidden; }

.app-cell {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.app-dot {
  width: 8px;
  height: 8px;
  border-radius: 3px;
  flex-shrink: 0;
}

.app-dot.portal  { background: var(--accent); }
.app-dot.game    { background: var(--amber); }
.app-dot.service { background: var(--blue); }
.app-dot.wiki    { background: var(--violet); }

.app-cell-text { display: flex; flex-direction: column; line-height: 1.3; min-width: 0; }

.app-cell-name { font-size: 13px; font-weight: 650; color: var(--ink); }

.app-cell-id { font-size: 13px; color: var(--ink-3); }

.role-text { font-size: 13px; color: var(--ink-2); }

.time-cell { font-size: 13px; white-space: nowrap; }

.row-actions { display: flex; gap: 5px; }

.chev { transition: transform 0.15s; }
.chev.open { transform: rotate(180deg); }

.expand-row td {
  background: var(--surface-2);
  padding: 10px 12px 12px 37px;
}

.expand-inner { display: flex; flex-direction: column; gap: 6px; }

.mini-label {
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.1em;
  color: var(--ink-3);
  text-transform: uppercase;
}
</style>
