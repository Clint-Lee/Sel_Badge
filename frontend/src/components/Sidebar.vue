<template>
  <aside class="sidebar" :class="{ open }">
    <button v-if="isAdmin" class="btn btn-primary side-cta" @click="$emit('open-create-app')">
      <Plus :size="15" />
      <span>注册应用</span>
    </button>

    <nav class="side-nav">
      <div class="nav-group-label">工作台</div>
      <router-link to="/" class="side-link" :class="{ active: isActive('/') }" @click="navigate">
        <LayoutGrid :size="16" stroke-width="1.9" />
        <span>应用</span>
      </router-link>
      <router-link to="/dashboard" class="side-link" :class="{ active: isActive('/dashboard') }" @click="navigate">
        <BarChart3 :size="16" stroke-width="1.9" />
        <span>控制台大屏</span>
        <span class="side-live-badge">LIVE</span>
      </router-link>
      <router-link to="/personas" class="side-link" :class="{ active: isActive('/personas') }" @click="navigate">
        <IdCard :size="16" stroke-width="1.9" />
        <span>应用画像</span>
      </router-link>

      <div class="nav-group-label">发现</div>
      <router-link to="/tools" class="side-link" :class="{ active: isActive('/tools') }" @click="navigate">
        <Sparkles :size="16" stroke-width="1.9" /><span>AI工具</span>
      </router-link>
      <router-link to="/suppliers" class="side-link" :class="{ active: isActive('/suppliers') }" @click="navigate">
        <Tags :size="16" stroke-width="1.9" /><span>低价库</span>
      </router-link>

      <div class="nav-group-label">开发</div>
      <router-link to="/sso" class="side-link" :class="{ active: isActive('/sso') }" @click="navigate">
        <Terminal :size="16" stroke-width="1.9" />
        <span>开发者控制台</span>
      </router-link>
      <router-link to="/guide" class="side-link" :class="{ active: isActive('/guide') }" @click="navigate">
        <BookOpen :size="16" stroke-width="1.9" />
        <span>接入指南</span>
      </router-link>

      <div class="nav-group-label">运营</div>
      <router-link to="/security" class="side-link" :class="{ active: isActive('/security') }" @click="navigate">
        <Activity :size="16" stroke-width="1.9" />
        <span>安全动态</span>
      </router-link>
    </nav>

    <div class="side-bottom">
      <template v-if="isAuthenticated">
        <router-link to="/profile" class="user-card" title="个人资料" @click="navigate">
          <span class="avatar">{{ avatarLetter }}</span>
          <span class="user-meta">
            <span class="user-name">{{ user?.username }}</span>
            <span class="user-mail mono">{{ user?.email }}</span>
          </span>
        </router-link>
        <button class="side-icon-btn" title="退出登录" @click="handleLogout">
          <LogOut :size="15" />
        </button>
      </template>
      <router-link v-else to="/login" class="btn btn-primary btn-block" @click="navigate">登录</router-link>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { Plus, LogOut, LayoutGrid, BarChart3, IdCard, Terminal, BookOpen, Activity, Sparkles, Tags } from 'lucide-vue-next';

defineProps({
  open: { type: Boolean, default: false },
});

const emit = defineEmits(['open-create-app', 'navigate']);

const route = useRoute();
const router = useRouter();
const { user, isAuthenticated, isAdmin, logout } = useAuth();

const avatarLetter = computed(() => (user.value?.username || '?').slice(0, 1).toUpperCase());

const isActive = (path) => (path === '/' ? route.path === '/' || route.path.startsWith('/apps') : route.path.startsWith(path));

const navigate = () => emit('navigate');

const handleLogout = () => {
  logout();
  router.push('/');
  emit('navigate');
};
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  flex-shrink: 0;
  position: sticky;
  top: var(--header-h);
  height: calc(100vh - var(--header-h));
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-right: 1px solid var(--line);
  z-index: 40;
}

.side-cta {
  width: auto;
  margin: 14px 14px 4px;
}

.side-nav {
  flex: 1;
  padding: 6px 10px 14px;
  overflow-y: auto;
}

.nav-group-label {
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.1em;
  color: var(--ink-3);
  padding: 14px 8px 6px;
}

.side-link {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 11px;
  border-radius: var(--radius-s);
  font-size: 14px;
  font-weight: 550;
  color: var(--ink-2);
  transition: background 0.12s, color 0.12s;
}

.side-link:hover { background: var(--surface-2); color: var(--ink); }

.side-link.active {
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-weight: 700;
}

.side-live-badge {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.35);
  letter-spacing: 0.05em;
  line-height: 1.4;
}

.side-bottom {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 14px;
  border-top: 1px solid var(--line);
}

.user-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 8px;
  border-radius: var(--radius-s);
  min-width: 0;
  transition: background 0.12s;
}

.user-card:hover { background: var(--surface-2); }

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-size: 14px;
  font-weight: 750;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  min-width: 0;
}

.user-name {
  font-size: 14px;
  font-weight: 650;
  color: var(--ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-mail {
  font-size: 13.5px;
  color: var(--ink-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.side-icon-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  transition: all 0.12s;
  flex-shrink: 0;
}

.side-icon-btn:hover { background: var(--red-tint); color: var(--red); }

/* 移动端：侧栏转左侧抽屉 */
@media (max-width: 880px) {
  .sidebar {
    position: fixed;
    top: var(--header-h);
    left: 0;
    height: calc(100vh - var(--header-h));
    transform: translateX(-100%);
    transition: transform 0.22s var(--ease);
    box-shadow: var(--shadow-modal);
  }

  .sidebar.open { transform: translateX(0); }

  .side-cta { display: none; }
}
</style>
