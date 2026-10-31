<template>
  <div class="portal-page directory-page page-container">
    <header class="directory-head">
      <div class="directory-head-main">
        <h1>AI工具</h1>
        <p>
          已收录 <strong>{{ stats.sites }}</strong> 个站点，覆盖 {{ stats.categories }} 个分类 ·
          按场景查找工具、货源与服务入口
        </p>
      </div>
      <button class="btn btn-primary" @click="openSubmission">
        <Plus :size="15" />{{ isAdmin ? "收录与审核" : "申请收录" }}
      </button>
    </header>

    <div class="directory-toolbar">
      <div class="portal-search">
        <Search :size="16" />
        <input
          ref="searchInput"
          v-model="query"
          aria-label="搜索站点、工具或标签"
          placeholder="搜索网站、工具、资源"
        />
        <button v-if="query" class="portal-icon-btn" aria-label="清除搜索" @click="query = ''">
          <X :size="15" />
        </button>
        <kbd v-else>{{ isMac ? "⌘ K" : "Ctrl K" }}</kbd>
      </div>
      <select v-model="sort" class="input portal-select" aria-label="站点排序">
        <option v-for="item in SITE_SORTS" :key="item.value" :value="item.value">
          {{ item.label }}
        </option>
      </select>
      <button class="btn btn-secondary directory-filter-toggle" @click="filtersOpen = !filtersOpen">
        <SlidersHorizontal :size="15" />分类
      </button>
    </div>

    <div class="directory-shell" :class="{ 'filters-open': filtersOpen }">
      <!-- 左侧分类导航 -->
      <aside class="directory-sidebar" aria-label="资源分类导航">
        <div class="side-block">
          <p class="side-label">浏览</p>
          <button
            v-for="view in SITE_VIEWS"
            :key="view.key"
            class="side-item"
            :class="{ active: viewMode === view.key }"
            @click="selectView(view.key)"
          >
            <component :is="view.icon" :size="15" />
            <span>{{ view.label }}</span>
            <em>{{ viewCount(view.key) }}</em>
          </button>
        </div>
        <div class="side-block">
          <p class="side-label">分类 · {{ categories.length }}</p>
          <button
            v-for="item in visibleCategories"
            :key="item.id"
            class="side-item"
            :class="{ active: viewMode === 'category' && category === item.id }"
            @click="selectCategory(item.id)"
          >
            <component :is="categoryIcon(item.id)" :size="15" />
            <span>{{ item.name }}</span>
            <em>{{ item.count }}</em>
          </button>
          <button
            v-if="categories.length > SIDEBAR_LIMIT"
            class="side-more"
            @click="showAllCategories = !showAllCategories"
          >
            {{ showAllCategories ? "收起分类" : `展开全部 ${categories.length} 个分类` }}
            <ChevronDown :size="13" :class="{ rotated: showAllCategories }" />
          </button>
        </div>
        <div class="side-block side-note">
          <Link2 :size="14" />
          <p>收录申请需登录，通过审核后进入公共目录；联系方式仅登录后可查看。</p>
        </div>
      </aside>

      <!-- 右侧主内容 -->
      <div class="directory-main">
        <div v-if="preferencesError" class="portal-error portal-banner" role="alert">
          {{ preferencesError }}<button @click="reloadPreferences">重试</button>
        </div>

        <div v-if="loading" class="portal-state">
          <Loader2 :size="25" class="spin" />
          <h3>正在读取资源目录</h3>
          <p>加载分类与站点…</p>
        </div>
        <div v-else-if="error" class="portal-state" role="alert">
          <CloudOff :size="28" />
          <h3>资源目录暂时无法加载</h3>
          <p>{{ error }}</p>
          <button class="btn btn-secondary" @click="load">重新加载</button>
        </div>

        <template v-else>
          <!-- 今日精选 -->
          <section v-if="showFeatured && featured.length" class="directory-section">
            <div class="directory-section-heading">
              <div class="portal-heading-inline">
                <h2>今日精选</h2>
                <span class="portal-result-count">{{ featured.length }} 个</span>
              </div>
              <button class="btn btn-ghost btn-sm" @click="reshuffle">
                <Shuffle :size="13" />换一批
              </button>
            </div>
            <div class="site-grid site-grid-featured">
              <SiteCard
                v-for="site in featured.slice(0, featuredLimit)"
                :key="site.id"
                :site="site"
                :saved="preferences.toolFavorites.includes(site.id)"
                :busy="preferencesBusy || !!preferencesError"
                @favorite="toggleFavorite"
                @detail="openDetail"
                @tag="searchTag"
                @open="openSite"
              />
            </div>
            <button
              v-if="featured.length > featuredLimit"
              class="btn btn-ghost btn-sm directory-expand"
              @click="featuredExpanded = !featuredExpanded"
            >
              {{ featuredExpanded ? "收起精选" : `展开全部 ${featured.length} 个精选` }}
            </button>
          </section>

          <!-- 分类导航 / 筛选结果 -->
          <section v-if="viewMode === 'all' && !query.trim()" class="directory-section">
            <div class="directory-section-heading">
              <div class="portal-heading-inline">
                <h2>按分类浏览</h2>
                <span class="portal-result-count">{{ activeCategoryCount }} 个分类</span>
              </div>
            </div>
            <div class="category-columns">
              <article
                v-for="group in categoryGroups"
                :id="`resources-${group.category.id}`"
                :key="group.category.id"
                class="category-column"
              >
                <header class="category-column-head">
                  <button class="category-column-title" @click="selectCategory(group.category.id)">
                    <component :is="categoryIcon(group.category.id)" :size="14" />
                    <span>{{ group.category.name }}</span>
                    <em>{{ group.category.count }}</em>
                  </button>
                  <button class="category-column-more" @click="selectCategory(group.category.id)">
                    全部 <ChevronRight :size="12" />
                  </button>
                </header>
                <div class="category-column-list">
                  <button
                    v-for="site in group.sites"
                    :key="site.id"
                    class="site-row"
                    @click="openDetail(site)"
                  >
                    <span class="site-row-name">{{ site.name }}</span>
                    <span class="site-row-host">{{ hostName(site.url) }}</span>
                  </button>
                </div>
              </article>
            </div>
          </section>

          <!-- 列表结果 -->
          <section class="directory-section">
            <div class="directory-section-heading">
              <div>
                <div class="portal-heading-inline">
                  <h2>{{ resultTitle }}</h2>
                  <span class="portal-result-count">{{ filteredSites.length }}</span>
                </div>
                <p class="portal-muted result-hint">{{ resultHint }}</p>
              </div>
              <button class="btn btn-ghost btn-sm" @click="resetFilters">
                <RotateCcw :size="13" />清空筛选
              </button>
            </div>
            <div v-if="filteredSites.length" class="site-grid">
              <SiteCard
                v-for="site in visibleSites"
                :key="site.id"
                :site="site"
                :saved="preferences.toolFavorites.includes(site.id)"
                :busy="preferencesBusy || !!preferencesError"
                @favorite="toggleFavorite"
                @detail="openDetail"
                @tag="searchTag"
                @open="openSite"
              />
            </div>
            <div v-else class="portal-state">
              <SearchX :size="28" />
              <h3>
                {{ viewMode === "favorites" ? "给喜欢的站点留一个位置" : "没有找到匹配的站点" }}
              </h3>
              <p>
                {{
                  viewMode === "favorites"
                    ? "点击站点卡片上的星标，就能在这里找到它。"
                    : "试试更短的关键词，或返回全部分类继续浏览。"
                }}
              </p>
              <div class="detail-actions">
                <button class="btn btn-secondary" @click="resetFilters">浏览全部站点</button>
                <button class="btn btn-primary" @click="openSubmission">推荐一个网站</button>
              </div>
            </div>
            <div v-if="filteredSites.length > PAGE_SIZE" class="portal-pagination">
              <span>第 {{ page }} / {{ pages }} 页 · 共 {{ filteredSites.length }} 个</span>
              <div>
                <button class="btn btn-secondary btn-sm" :disabled="page <= 1" @click="page--">
                  上一页
                </button>
                <button class="btn btn-secondary btn-sm" :disabled="page >= pages" @click="page++">
                  下一页
                </button>
              </div>
            </div>
          </section>
        </template>

        <div class="portal-bottom-note">
          <Info :size="14" />
          <span>收录只做信息整理与跳转，站点服务与售后以原站为准。</span>
          <button @click="openSubmission">推荐一个网站 <ArrowUpRight :size="13" /></button>
        </div>
      </div>
    </div>

    <SiteDetail
      v-if="selected"
      :key="selected.id"
      :site="selected"
      :category-name="categoryName(selected.category)"
      @close="setQuery({ site: undefined })"
      @tag="searchTag"
      @open="openSite"
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
  Search,
  X,
  Plus,
  Shuffle,
  ChevronRight,
  ChevronDown,
  SlidersHorizontal,
  RotateCcw,
  Loader2,
  CloudOff,
  SearchX,
  Info,
  ArrowUpRight,
  Link2,
} from "lucide-vue-next";
import { SITE_VIEWS, SITE_SORTS, categoryIcon } from "./enum";
import SiteCard from "./comp/SiteCard.vue";
import SiteDetail from "./comp/SiteDetail.vue";
import SubmissionDialog from "./comp/SubmissionDialog.vue";
import { portalApi } from "../../api/portal";
import { useAuth } from "../../composables/useAuth";
import { usePortalPreferences } from "../../composables/usePortalPreferences";
import { hostName, shuffleBy } from "../../utils/portal";
import type { Category, PortalStats, Site } from "../../model/portal";

const PAGE_SIZE = 24;
const SIDEBAR_LIMIT = 10;
const isMac = typeof navigator !== "undefined" && /mac/i.test(navigator.userAgent);

const route = useRoute();
const router = useRouter();
const { isAuthenticated, isAdmin } = useAuth();
const { preferences, preferencesBusy, preferencesError, toggle, reloadPreferences } =
  usePortalPreferences();

const categories = ref<Category[]>([]);
const sites = ref<Site[]>([]);
const stats = ref<PortalStats>({ sites: 0, categories: 0, reviews: 0, submissions: 0, updatedAt: "" });
const loading = ref(true);
const error = ref("");
const searchInput = ref<HTMLInputElement>();
const filtersOpen = ref(false);
const showAllCategories = ref(false);
const featuredExpanded = ref(false);
const featuredSeed = ref(String(Date.now()));
const page = ref(1);

const setQuery = (values: Record<string, string | undefined>) =>
  router.replace({ query: { ...route.query, ...values } });

const query = computed({
  get: () => String(route.query.q || ""),
  set: (value) => setQuery({ q: value || undefined }),
});
const viewMode = computed(() => String(route.query.view || "all"));
const category = computed(() => String(route.query.category || ""));
const sort = computed({
  get: () => String(route.query.sort || "recommended"),
  set: (value) => setQuery({ sort: value === "recommended" ? undefined : value }),
});
const selected = computed(() => sites.value.find((x) => x.id === route.query.site));

const visibleCategories = computed(() =>
  showAllCategories.value || categories.value.length <= SIDEBAR_LIMIT
    ? categories.value
    : categories.value.slice(0, SIDEBAR_LIMIT),
);
const activeCategoryCount = computed(() => categories.value.filter((x) => x.count > 0).length);
const featuredLimit = computed(() => (featuredExpanded.value ? 24 : 8));
const categoryName = (id: string) => categories.value.find((x) => x.id === id)?.name || "未分类";

const matches = (site: Site, needle: string) =>
  `${site.name} ${site.description} ${site.tags.join(" ")}`
    .toLocaleLowerCase()
    .includes(needle);

const sortedSites = computed(() => {
  const list = [...sites.value];
  const pin = (site: Site) =>
    Number(Boolean(site.sponsorPinUntil && Date.parse(site.sponsorPinUntil) > Date.now()));
  if (sort.value === "name") return list.sort((a, b) => a.name.localeCompare(b.name, "zh-CN"));
  if (sort.value === "newest")
    return list.sort((a, b) => (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0));
  if (sort.value === "popular") return list.sort((a, b) => b.visits - a.visits);
  return list.sort(
    (a, b) =>
      pin(b) - pin(a) ||
      Number(b.featured) - Number(a.featured) ||
      b.visits - a.visits ||
      (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0),
  );
});

const filteredSites = computed(() => {
  const needle = query.value.trim().toLocaleLowerCase();
  const now = Date.now();
  return sortedSites.value.filter((site) => {
    if (needle && !matches(site, needle)) return false;
    if (viewMode.value === "favorites" && !preferences.value.toolFavorites.includes(site.id)) return false;
    if (viewMode.value === "recent" && !(Date.parse(site.createdAt) > now - 30 * 86400000)) return false;
    if (viewMode.value === "category" && site.category !== category.value) return false;
    return true;
  });
});

const pages = computed(() => Math.max(1, Math.ceil(filteredSites.value.length / PAGE_SIZE)));
const visibleSites = computed(() =>
  filteredSites.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

/** 精选：按 seed 稳定打散，换一批只改 seed。 */
const featured = computed(() => {
  const pool = sites.value.filter(
    (site) =>
      site.featured ||
      (site.sponsorPinUntil && Date.parse(site.sponsorPinUntil) > Date.now()),
  );
  const source = pool.length ? pool : sites.value.filter((x) => x.visits > 0);
  return shuffleBy(source, featuredSeed.value, (site) => site.id);
});
const showFeatured = computed(
  () => !query.value.trim() && (viewMode.value === "all" || viewMode.value === "featured"),
);

/** 分类看板：每个分类取访问量前若干条，避免一次渲染上千卡片。 */
const categoryGroups = computed(() =>
  categories.value
    .filter((item) => item.count > 0)
    .map((item) => ({
      category: item,
      sites: sites.value
        .filter((site) => site.category === item.id)
        .sort((a, b) => b.visits - a.visits || a.name.localeCompare(b.name, "zh-CN"))
        .slice(0, item.id === "upi-zone" ? 10 : 5),
    })),
);

const resultTitle = computed(() => {
  if (query.value.trim()) return "搜索结果";
  if (viewMode.value === "favorites") return "我的收藏";
  if (viewMode.value === "recent") return "最近 30 天收录";
  if (viewMode.value === "category")
    return categories.value.find((x) => x.id === category.value)?.name || "分类";
  if (viewMode.value === "featured") return "今日精选";
  return "全部站点";
});
const resultHint = computed(() => {
  if (query.value.trim()) return `在全部站点中搜索「${query.value.trim()}」`;
  if (viewMode.value === "favorites")
    return isAuthenticated.value ? "收藏随 Badge 账号同步" : "访客收藏保存在当前浏览器";
  if (viewMode.value === "category")
    return categories.value.find((x) => x.id === category.value)?.description || "按分类浏览";
  return "按需发现，找到适合你的下一件工具。";
});

const viewCount = (key: string) => {
  if (key === "all") return sites.value.length;
  if (key === "featured")
    return sites.value.filter(
      (x) => x.featured || (x.sponsorPinUntil && Date.parse(x.sponsorPinUntil) > Date.now()),
    ).length;
  if (key === "favorites") return preferences.value.toolFavorites.length;
  return sites.value.filter((x) => Date.parse(x.createdAt) > Date.now() - 30 * 86400000).length;
};

function selectView(key: string) {
  page.value = 1;
  setQuery({
    view: key === "all" ? undefined : key,
    category: undefined,
    q: undefined,
  });
}
function selectCategory(id: string) {
  page.value = 1;
  setQuery({ view: "category", category: id, q: undefined });
  filtersOpen.value = false;
}
function searchTag(tag: string) {
  page.value = 1;
  setQuery({ q: tag, view: undefined, category: undefined });
}
function resetFilters() {
  page.value = 1;
  setQuery({ q: undefined, view: undefined, category: undefined });
}
function reshuffle() {
  featuredSeed.value = String(Date.now());
  page.value = 1;
}
function toggleFavorite(id: string) {
  toggle("toolFavorites", id);
}
function openDetail(site: Site) {
  setQuery({ site: site.id });
}
/** 访问跳转：先记一次点击，再新窗口打开，避免目标页拿不到 opener。 */
async function openSite(site: Site) {
  const target = site.url;
  window.open(target, "_blank", "noopener,noreferrer");
  try {
    const updated = await portalApi.visit(site.id);
    sites.value = sites.value.map((x) => (x.id === updated.id ? updated : x));
  } catch {
    /* 计数失败不影响访问 */
  }
}
function openSubmission() {
  if (isAuthenticated.value) setQuery({ submit: "1" });
  else
    router.push({
      path: "/login",
      query: {
        redirect: router.resolve({ path: "/tools", query: { ...route.query, submit: "1" } }).fullPath,
      },
    });
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [categoryList, siteList, portalStats] = await Promise.all([
      portalApi.categories(),
      portalApi.sites(),
      portalApi.stats(),
    ]);
    categories.value = categoryList;
    sites.value = siteList;
    stats.value = portalStats;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "请检查连接后重试";
  } finally {
    loading.value = false;
  }
}

watch([query, viewMode, category, sort], () => {
  page.value = 1;
});
watch(pages, (count) => {
  if (page.value > count) page.value = count;
});

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
