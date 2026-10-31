<template>
  <div class="app-shell" :class="{ bare: isBare }">
    <template v-if="!isBare">
      <!-- 顶部 header：常用菜单 tab -->
      <header class="topbar">
        <div class="topbar-inner">
          <button class="hamburger" title="菜单" @click="drawerOpen = !drawerOpen">
            <Menu :size="18" />
          </button>

          <router-link to="/" class="brand">
            <span class="brand-mark">
              <ShieldCheck :size="15" stroke-width="2.2" />
            </span>
            <span class="brand-text">
              <span class="brand-name">Badge</span>
              <span class="brand-sub">统一身份中心</span>
            </span>
          </router-link>

          <nav class="tabs">
            <router-link
              v-for="item in tabs"
              :key="item.path"
              :to="item.path"
              class="tab"
              :class="{ active: isActive(item.path) }"
            >
              {{ item.label }}
            </router-link>
          </nav>

          <div class="topbar-actions">
            <router-link v-if="isAdmin" to="/personas" class="head-user" title="应用画像">
              <span class="avatar">{{ avatarLetter }}</span>
            </router-link>
            <router-link v-else-if="isAuthenticated" to="/profile" class="head-user" title="个人资料">
              <span class="avatar">{{ avatarLetter }}</span>
            </router-link>
            <router-link v-else to="/login" class="btn btn-primary btn-sm">登录</router-link>
          </div>
        </div>
      </header>

      <!-- 移动端抽屉遮罩 -->
      <div v-if="drawerOpen" class="drawer-mask" @click="drawerOpen = false"></div>

      <div class="app-columns">
        <Sidebar
          :open="drawerOpen"
          @open-create-app="handleOpenCreateApp"
          @navigate="drawerOpen = false"
        />

        <div class="app-body">
          <main class="app-main">
            <router-view
              @launch-sso="handleLaunchSso"
              @edit-profile="handleEditProfile"
              @request-login="goLogin"
            />
          </main>

          <footer class="app-footer">Badge · 统一身份中心</footer>
        </div>
      </div>
    </template>

    <!-- 登录等裸布局页面：无导航与页脚 -->
    <router-view v-if="isBare" />

    <!-- 模态层 -->
    <CreateAppModal v-if="showCreateAppModal" @close="showCreateAppModal = false" />
    <EditProfileModal
      v-if="editingApp"
      :app="editingApp"
      @close="editingApp = null"
      @updated="editingApp = null"
    />
    <SsoLaunchModal v-if="launchingApp" :app="launchingApp" @close="launchingApp = null" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from './composables/useAuth';
import { Menu, ShieldCheck } from 'lucide-vue-next';
import Sidebar from './components/Sidebar.vue';
import CreateAppModal from './components/CreateAppModal.vue';
import EditProfileModal from './components/EditProfileModal.vue';
import SsoLaunchModal from './components/SsoLaunchModal.vue';

const route = useRoute();
const router = useRouter();
const { user, isAuthenticated, isAdmin, initAuth } = useAuth();

const isBare = computed(() => Boolean(route.meta.bare));
const drawerOpen = ref(false);

watch(() => route.path, () => { drawerOpen.value = false; });

const tabs = [
  { path: '/', label: '应用' },
  { path: '/dashboard', label: '控制台大屏' },
  { path: '/tools', label: 'AI工具' },
  { path: '/suppliers', label: '低价库' },
  { path: '/sso', label: '开发者控制台' },
  { path: '/personas', label: '应用画像' },
  { path: '/security', label: '安全动态' },
];

const avatarLetter = computed(() => (user.value?.username || '?').slice(0, 1).toUpperCase());

const isActive = (path) => (path === '/' ? route.path === '/' || route.path.startsWith('/apps') : route.path.startsWith(path));

const goLogin = () => router.push({ path: '/login', query: { redirect: route.fullPath } });

const requireLogin = (action) => {
  if (isAuthenticated.value) {
    action();
  } else {
    goLogin();
  }
};

const handleOpenCreateApp = () => requireLogin(() => { showCreateAppModal.value = true; });
const handleLaunchSso = (app) => requireLogin(() => { launchingApp.value = app; });
const handleEditProfile = (app) => requireLogin(() => { editingApp.value = app; });

const showCreateAppModal = ref(false);
const editingApp = ref(null);
const launchingApp = ref(null);

// 登录态变化后通知各视图刷新（应用列表、画像等）
onMounted(async () => {
  await initAuth();
  window.dispatchEvent(new CustomEvent('badge:auth-changed'));
});
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ---- 顶部 header ---- */
.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--header-h);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--line);
}

.topbar-inner {
  height: 100%;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 22px;
}

.hamburger {
  display: none;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius-s);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
}

.hamburger:hover { background: var(--surface-2); color: var(--ink); }

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
}

.brand-mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(17, 112, 88, 0.25);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-name {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.015em;
  color: var(--ink);
}

.brand-sub {
  font-size: 13px;
  color: var(--ink-3);
  letter-spacing: 0.04em;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 100%;
  flex: 1;
  overflow-x: auto;
  scrollbar-width: none;
}
.tabs::-webkit-scrollbar { display: none; }

.tab {
  position: relative;
  height: 100%;
  display: inline-flex;
  align-items: center;
  padding: 0 14px;
  font-size: 14.5px;
  font-weight: 500;
  color: var(--ink-2);
  white-space: nowrap;
  transition: color 0.14s;
}

.tab:hover { color: var(--ink); }

.tab.active { color: var(--accent-ink); font-weight: 700; }

.tab.active::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 2.5px;
  border-radius: 2px 2px 0 0;
  background: var(--accent);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-left: auto;
}

.head-user .avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-size: 14px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: box-shadow 0.14s;
}

.head-user .avatar:hover { box-shadow: 0 0 0 3px var(--accent-tint); }

/* ---- 主体两列 ---- */
.app-columns {
  flex: 1;
  display: flex;
  align-items: stretch;
  min-height: calc(100vh - var(--header-h));
}

.app-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.app-footer {
  padding: 14px 28px;
  border-top: 1px solid var(--line);
  background: var(--surface);
  font-size: 13.5px;
  color: var(--ink-3);
  letter-spacing: 0.03em;
}

.drawer-mask {
  display: none;
}

/* ---- 移动端 ---- */
@media (max-width: 880px) {
  .topbar-inner { gap: 10px; padding: 0 14px; }
  .hamburger { display: flex; }
  .tabs { display: none; }
  .brand-sub { display: none; }

  .drawer-mask {
    display: block;
    position: fixed;
    inset: var(--header-h) 0 0 0;
    background: rgba(23, 29, 25, 0.4);
    z-index: 39;
    animation: overlay-in 0.16s var(--ease);
  }

  .page { padding: 14px 14px 28px; }
  .app-footer { padding: 11px 14px; }
}
</style>
