<template>
  <div class="portal-page page-container">
    <header class="portal-hero">
      <div>
        <p class="portal-eyebrow">
          <Sparkles :size="13" /> BADGE DISCOVER / 01
        </p>
        <h1>让好工具，<span>为你所用。</span></h1>
        <p class="portal-hero-description">
          从灵感到实现，找到你的下一件 AI 工具。
        </p>
        <div class="portal-hero-meta">
          <span
            ><strong>{{ tools.length }}</strong> 个精选工具</span
          ><span>8 个使用场景</span><span>持续收录</span>
        </div>
      </div>
      <div class="hero-aside">
        <span class="hero-symbol"
          ><Workflow :size="32" stroke-width="1.2"
        /></span>
        <p>发现 · 收藏 · 创造</p>
        <button class="btn btn-primary" @click="openSubmission">
          <Plus :size="15" />{{ isAdmin ? "收录与审核" : "推荐工具" }}
        </button>
      </div>
    </header>
    <div class="portal-controls">
      <div class="portal-search">
        <Search :size="17" /><input
          ref="searchInput"
          v-model="query"
          aria-label="搜索 AI 工具"
          placeholder="搜索工具名称、用途或标签…"
        /><button
          v-if="query"
          class="portal-icon-btn"
          aria-label="清除搜索"
          @click="query = ''"
        >
          <X :size="15" /></button
        ><kbd v-else>Ctrl K</kbd>
      </div>
      <select v-model="sort" class="input portal-select" aria-label="工具排序">
        <option value="featured">精选优先</option>
        <option value="newest">最新收录</option>
        <option value="name">名称排序</option>
      </select>
    </div>
    <div class="portal-category-list" aria-label="工具分类">
      <button
        v-for="item in TOOL_CATEGORIES"
        :key="item.id"
        :class="{ active: category === item.id }"
        :aria-pressed="category === item.id"
        @click="category = item.id"
      >
        <component :is="item.icon" :size="15" />{{ item.label
        }}<span>{{ categoryCount(item.id) }}</span>
      </button>
    </div>
    <div
      v-if="preferencesError"
      class="portal-error portal-banner"
      role="alert"
    >
      {{ preferencesError }}<button @click="reloadPreferences">重试</button>
    </div>
    <section class="portal-results">
      <div class="portal-section-heading">
        <div class="portal-heading-inline">
          <h2>{{ query ? "搜索结果" : categoryFor(category).label }}</h2>
          <span class="portal-muted">{{ filtered.length }} 个工具</span>
        </div>
        <p class="portal-muted">
          {{
            isAuthenticated
              ? "收藏随 Badge 账号同步"
              : "访客收藏保存在当前浏览器"
          }}
        </p>
      </div>
      <div v-if="loading" class="portal-state">
        <Loader2 :size="25" class="spin" />
        <h3>正在发现好工具</h3>
        <p>读取工具目录…</p>
      </div>
      <div v-else-if="error" class="portal-state" role="alert">
        <CloudOff :size="28" />
        <h3>工具目录暂时无法加载</h3>
        <p>{{ error }}</p>
        <button class="btn btn-secondary" @click="load">重新加载</button>
      </div>
      <div v-else-if="!filtered.length" class="portal-state">
        <SearchX :size="30" />
        <h3>
          {{
            category === "favorites"
              ? "给喜欢的工具留个位置"
              : "没有找到合适的工具"
          }}
        </h3>
        <p>
          {{
            category === "favorites"
              ? "点击工具卡片上的星标，下次就能快速找到。"
              : "试试其他关键词，或切换一个使用场景。"
          }}
        </p>
        <button class="btn btn-secondary" @click="reset">浏览全部工具</button>
      </div>
      <div v-else class="tool-grid">
        <ToolCard
          v-for="tool in visible"
          :key="tool.id"
          :tool="tool"
          :saved="preferences.toolFavorites.includes(tool.id)"
          :busy="preferencesBusy || !!preferencesError"
          @favorite="toggle('toolFavorites', $event)"
          @detail="openDetail"
          @tag="searchTag"
        />
      </div>
      <div v-if="filtered.length > pageSize" class="portal-pagination">
        <span
          >共 {{ filtered.length }} 个工具 · 第 {{ page }} /
          {{ pages }} 页</span
        >
        <div>
          <button
            class="btn btn-secondary btn-sm"
            :disabled="page <= 1"
            @click="page--"
          >
            上一页</button
          ><button
            class="btn btn-secondary btn-sm"
            :disabled="page >= pages"
            @click="page++"
          >
            下一页
          </button>
        </div>
      </div>
    </section>
    <div class="portal-bottom-note">
      <BookOpen :size="15" /><span
        >好工具，值得被发现。工具具体服务与使用条件以官方网站为准。</span
      ><button @click="openSubmission">
        推荐一个工具 <ArrowUpRight :size="13" />
      </button>
    </div>
    <ToolDetail
      v-if="selected"
      :key="selected.id"
      :tool="selected"
      @close="setQuery({ tool: undefined })"
    />
    <SubmissionDialog
      v-if="route.query.submit === '1' && isAuthenticated"
      @close="setQuery({ submit: undefined })"
      @updated="load"
    />
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Sparkles,
  Workflow,
  Plus,
  Search,
  X,
  Loader2,
  CloudOff,
  SearchX,
  BookOpen,
  ArrowUpRight,
} from "lucide-vue-next";
import { TOOL_CATEGORIES, categoryFor } from "./enum";
import ToolCard from "./comp/ToolCard.vue";
import ToolDetail from "./comp/ToolDetail.vue";
import SubmissionDialog from "./comp/SubmissionDialog.vue";
import { portalApi } from "../../api/portal";
import { useAuth } from "../../composables/useAuth";
import { usePortalPreferences } from "../../composables/usePortalPreferences";
import type { Tool } from "../../model/portal";
const route = useRoute(),
  router = useRouter();
const { isAuthenticated, isAdmin } = useAuth();
const {
  preferences,
  preferencesBusy,
  preferencesError,
  toggle,
  reloadPreferences,
} = usePortalPreferences();
const tools = ref<Tool[]>([]),
  loading = ref(true),
  error = ref("");
const searchInput = ref<HTMLInputElement>();
const setQuery = (values: Record<string, string | undefined>) =>
  router.replace({ query: { ...route.query, ...values } });
const query = computed({
  get: () => String(route.query.q || ""),
  set: (value) => setQuery({ q: value || undefined }),
});
const category = computed({
  get: () =>
    TOOL_CATEGORIES.some((x) => x.id === route.query.category)
      ? String(route.query.category)
      : "all",
  set: (value) => setQuery({ category: value === "all" ? undefined : value }),
});
const sort = computed({
  get: () => String(route.query.sort || "featured"),
  set: (value) => setQuery({ sort: value }),
});
const page = ref(1),
  pageSize = 18;
const selected = computed(() =>
  tools.value.find((x) => x.id === route.query.tool),
);
const categoryCount = (id: string) =>
  tools.value.filter(
    (x) =>
      id === "all" ||
      (id === "favorites"
        ? preferences.value.toolFavorites.includes(x.id)
        : x.category === id),
  ).length;
const filtered = computed(() => {
  const q = query.value.toLocaleLowerCase().trim();
  return tools.value
    .filter(
      (x) =>
        (category.value === "all" ||
          (category.value === "favorites"
            ? preferences.value.toolFavorites.includes(x.id)
            : x.category === category.value)) &&
        (!q ||
          `${x.name} ${x.description} ${x.tags.join(" ")}`
            .toLocaleLowerCase()
            .includes(q)),
    )
    .sort((a, b) =>
      sort.value === "name"
        ? a.name.localeCompare(b.name, "zh-CN")
        : sort.value === "newest"
          ? b.createdAt.localeCompare(a.createdAt)
          : Number(b.featured) - Number(a.featured),
    );
});
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / pageSize)),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize),
);
watch([query, category, sort, () => preferences.value.toolFavorites], () => {
  page.value = 1;
});
const reset = () => setQuery({ q: undefined, category: undefined });
const searchTag = (tag: string) => setQuery({ q: tag, category: undefined });
const openDetail = (tool: Tool) => setQuery({ tool: tool.id });
const openSubmission = () => {
  if (isAuthenticated.value) setQuery({ submit: "1" });
  else
    router.push({
      path: "/login",
      query: {
        redirect: router.resolve({
          path: "/tools",
          query: { ...route.query, submit: "1" },
        }).fullPath,
      },
    });
};
async function load() {
  loading.value = true;
  error.value = "";
  try {
    tools.value = await portalApi.tools();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "请检查连接后重试";
  } finally {
    loading.value = false;
  }
}
const shortcut = (event: KeyboardEvent) => {
  if (
    (event.ctrlKey || event.metaKey) &&
    event.key.toLowerCase() === "k" &&
    !document.querySelector("dialog[open]")
  ) {
    event.preventDefault();
    searchInput.value?.focus();
  }
};
onMounted(() => {
  load();
  window.addEventListener("keydown", shortcut);
});
onUnmounted(() => window.removeEventListener("keydown", shortcut));
</script>
