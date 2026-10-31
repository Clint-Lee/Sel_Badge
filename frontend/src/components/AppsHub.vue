<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">应用</h1>
        <p class="page-desc">已接入 Badge 的全部自研应用。</p>
      </div>
      <span v-if="apps.length" class="head-stat mono">{{ apps.length }} 个应用 · {{ categorySummary }}</span>
    </div>

    <div v-if="isAuthenticated" class="login-strip">
      <span>已登录 <strong>{{ user?.username }}</strong>，可一键直达各应用</span>
    </div>
    <div v-else class="login-strip">
      <span>访客视角 · 登录后可一键直达并管理各应用画像</span>
      <button class="btn btn-secondary btn-sm" @click="$emit('request-login')">登录</button>
    </div>

    <div class="discover-entries">
      <router-link to="/tools"><span class="discover-entry-icon"><Sparkles :size="19" /></span><span><strong>AI工具</strong><small>发现、收藏适合你的创作与开发工具</small></span><ArrowUpRight :size="17" /></router-link>
      <router-link to="/suppliers"><span class="discover-entry-icon"><ChartNoAxesCombined :size="19" /></span><span><strong>低价库</strong><small>选商品规格，比较公开报价与库存</small></span><ArrowUpRight :size="17" /></router-link>
    </div>

    <div class="toolbar">
      <div class="chips">
        <button
          v-for="cat in categories"
          :key="cat.id"
          class="chip"
          :class="{ active: activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >
          {{ cat.label }}
          <span class="chip-count num">{{ cat.count }}</span>
        </button>
      </div>
      <div class="search">
        <Search :size="14" class="search-icon" />
        <input v-model="query" type="text" class="input search-input" placeholder="搜索名称、ID 或说明" />
      </div>
    </div>

    <div v-if="loading" class="card loading-panel">
      <Loader2 :size="16" class="spin" />
      <span>正在载入应用列表</span>
    </div>

    <div v-else-if="filteredApps.length === 0" class="card">
      <div class="empty">
        <span class="empty-icon"><SearchX :size="18" /></span>
        <div class="empty-title">没有匹配的应用</div>
        <div class="empty-desc">换个关键词，或清除分类筛选再试一次。</div>
        <button class="btn btn-secondary btn-sm" @click="query = ''; activeCategory = 'ALL'">清除筛选</button>
      </div>
    </div>

    <div v-else class="grid">
      <article v-for="app in filteredApps" :key="app.id" class="card app-card">
        <div class="card-top">
          <span class="app-icon" :class="app.appCategory.toLowerCase()">
            <component :is="categoryIcon(app.appCategory)" :size="17" stroke-width="1.9" />
          </span>
          <div class="app-head">
            <div class="app-name-row">
              <span class="app-name">{{ app.appName }}</span>
              <span class="badge" :class="categoryBadge(app.appCategory)">
                {{ categoryLabel(app.appCategory) }}
              </span>
              <span v-if="app.status !== 'ACTIVE'" class="badge badge-red">维护中</span>
            </div>
            <span class="app-client mono">{{ app.clientId }}</span>
          </div>
        </div>

        <p class="app-desc">{{ app.description || '暂无应用说明。' }}</p>

        <div class="binding">
          <template v-if="app.isBound">
            <span class="binding-item">
              <span class="binding-key">昵称</span>
              <span class="binding-val">{{ app.boundNickname || '—' }}</span>
            </span>
            <span class="binding-item">
              <span class="binding-key">角色</span>
              <span class="binding-val mono">{{ app.boundRole || '—' }}</span>
            </span>
          </template>
          <template v-else>
            <span class="binding-unbound">尚未绑定画像</span>
          </template>
        </div>

        <div class="card-actions">
          <button
            class="btn btn-primary btn-sm btn-launch"
            :disabled="app.status !== 'ACTIVE'"
            @click="$emit('launch-sso', app)"
          >
            <ArrowRight :size="13" />
            <span>一键直达</span>
          </button>
          <button class="btn btn-secondary btn-sm" @click="$emit('edit-profile', app)">画像</button>
          <a :href="app.homepageUrl" target="_blank" rel="noreferrer" class="btn btn-ghost btn-sm icon-btn" title="打开应用主页">
            <ExternalLink :size="13" />
          </a>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useAuth } from '../composables/useAuth';
import { api } from '../api/client';
import {
  Search, SearchX, Loader2, ArrowRight, ExternalLink,
  Globe, Gamepad2, Server, BookOpen, LayoutGrid, Sparkles, ChartNoAxesCombined, ArrowUpRight,
} from 'lucide-vue-next';

defineEmits(['launch-sso', 'edit-profile', 'request-login']);

const { user, isAuthenticated } = useAuth();

const apps = ref([]);
const loading = ref(true);
const activeCategory = ref('ALL');
const query = ref('');

const loadApps = async () => {
  loading.value = true;
  try {
    apps.value = isAuthenticated.value ? await api.getApps() : await api.getPublicApps();
  } catch {
    try {
      apps.value = await api.getPublicApps();
    } catch {
      apps.value = [];
    }
  } finally {
    loading.value = false;
  }
};

onMounted(loadApps);
onMounted(() => {
  window.addEventListener('badge:auth-changed', loadApps);
  window.addEventListener('badge:apps-changed', loadApps);
});
onUnmounted(() => {
  window.removeEventListener('badge:auth-changed', loadApps);
  window.removeEventListener('badge:apps-changed', loadApps);
});

const categories = computed(() => {
  const base = [
    { id: 'ALL', label: '全部' },
    { id: 'PORTAL', label: '门户' },
    { id: 'GAME', label: '游戏' },
    { id: 'SERVICE', label: '服务' },
    { id: 'WIKI', label: '文档' },
  ];
  return base.map((cat) => ({
    ...cat,
    count: cat.id === 'ALL' ? apps.value.length : apps.value.filter((a) => a.appCategory === cat.id).length,
  }));
});

const categorySummary = computed(() => {
  const parts = apps.value.reduce((acc, app) => {
    const key = categoryLabel(app.appCategory);
    acc[key] = (acc[key] || 0) + 1;
    return acc;
  }, {});
  return Object.entries(parts).map(([k, v]) => `${k} ${v}`).join(' · ');
});

const filteredApps = computed(() => {
  const q = query.value.toLowerCase().trim();
  return apps.value.filter((app) => {
    const matchCat = activeCategory.value === 'ALL' || app.appCategory === activeCategory.value;
    const matchQ = !q
      || app.appName.toLowerCase().includes(q)
      || app.clientId.toLowerCase().includes(q)
      || (app.description || '').toLowerCase().includes(q);
    return matchCat && matchQ;
  });
});

const categoryIcon = (cat) =>
  ({ GAME: Gamepad2, SERVICE: Server, WIKI: BookOpen }[cat] || (cat === 'PORTAL' ? Globe : LayoutGrid));

const categoryLabel = (cat) =>
  ({ PORTAL: '门户', GAME: '游戏', SERVICE: '服务', WIKI: '文档' }[cat] || cat);

const categoryBadge = (cat) =>
  ({ PORTAL: 'badge-green', GAME: 'badge-amber', SERVICE: 'badge-blue', WIKI: 'badge-violet' }[cat] || 'badge-gray');
</script>

<style scoped>
.discover-entries { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 16px 0 24px; }
.discover-entries > a { display: flex; align-items: center; gap: 14px; padding: 18px 20px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--radius-m); box-shadow: var(--shadow-card); transition: border-color .16s, box-shadow .16s, transform .16s; }
.discover-entries > a:hover { border-color: var(--accent); box-shadow: var(--shadow-hover); transform: translateY(-2px); }
.discover-entries > a:hover .discover-entry-icon { transform: scale(1.05); }
.discover-entries > a:hover > svg { transform: translateX(3px); color: var(--accent); }
.discover-entry-icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 11px; color: var(--accent); background: var(--accent-tint); flex-shrink: 0; transition: transform .18s var(--ease); }
.discover-entries > a > span:nth-child(2) { flex: 1; }
.discover-entries strong { display: block; font-size: 16px; font-weight: 750; color: var(--ink); }
.discover-entries small { display: block; font-size: 14px; color: var(--ink-2); margin-top: 4px; line-height: 1.5; }
.discover-entries > a > svg { color: var(--ink-3); flex-shrink: 0; transition: transform .16s var(--ease), color .16s; }
@media (max-width: 600px) { .discover-entries { grid-template-columns: 1fr; } }
.head-stat { font-size: 14px; color: var(--ink-2); font-weight: 550; }

.login-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  box-shadow: var(--shadow-subtle);
  font-size: 14px;
  color: var(--ink-2);
  margin-bottom: 16px;
}

.login-strip strong { color: var(--accent-ink); font-weight: 650; }

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.chips { display: flex; gap: 6px; flex-wrap: wrap; }

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  font-size: 14px;
  font-weight: 550;
  color: var(--ink-2);
  cursor: pointer;
  box-shadow: var(--shadow-subtle);
  transition: all 0.14s var(--ease);
}

.chip:hover { border-color: var(--line-strong); color: var(--ink); background: var(--surface-2); }

.chip.active {
  background: var(--accent-tint);
  border-color: var(--accent);
  color: var(--accent-ink);
  font-weight: 700;
}

.chip-count { font-size: 13px; opacity: 0.75; }

.search { position: relative; width: 260px; }

.search-icon {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--ink-3);
  pointer-events: none;
}

.search-input { padding-left: 32px; height: 36px; font-size: 14px; }

.loading-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 48px;
  color: var(--ink-2);
  font-size: 14px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(290px, 1fr));
  gap: 14px;
}

.app-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 11px;
  transition: transform 0.18s var(--ease), box-shadow 0.18s var(--ease), border-color 0.18s;
}

.app-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-hover);
  border-color: var(--line-strong);
}

.card-top { display: flex; gap: 12px; align-items: center; }

.app-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.app-icon.portal  { background: var(--accent-tint); color: var(--accent); }
.app-icon.game    { background: var(--amber-tint);  color: var(--amber); }
.app-icon.service { background: var(--blue-tint);   color: var(--blue); }
.app-icon.wiki    { background: var(--violet-tint); color: var(--violet); }

.app-head { min-width: 0; }

.app-name-row {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.app-name {
  font-size: 16px;
  font-weight: 750;
  color: var(--ink);
}

.app-client { font-size: 13.5px; color: var(--ink-3); }

.app-desc {
  font-size: 14px;
  color: var(--ink-2);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 44px;
}

.binding {
  display: flex;
  gap: 16px;
  padding: 10px 2px 0;
  border-top: 1px solid var(--line);
  font-size: 14px;
}

.binding-item { display: flex; align-items: center; gap: 6px; min-width: 0; }
.binding-key { color: var(--ink-3); flex-shrink: 0; }
.binding-val { color: var(--ink); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.binding-unbound { color: var(--ink-3); }

.card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: auto;
}

.btn-launch { flex: 1; }

.icon-btn { width: 32px; padding: 0; }
</style>
