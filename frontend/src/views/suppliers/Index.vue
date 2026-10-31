<template>
  <div class="price-page page-container">
    <header class="price-head">
      <div class="price-head-main">
        <h1>低价库</h1>
        <p>
          <template v-if="catalog === null">收录数量待更新</template>
          <template v-else>
            已收录 <strong>{{ catalog.totalLinks.toLocaleString("zh-CN") }}</strong> 条商品链接 ·
            同规格比较，保留原始币种
          </template>
        </p>
      </div>
      <div class="price-head-meta">
        <span>{{ catalog?.groups.length ?? 0 }} 个规格</span>
        <span>{{ catalog?.sources.length ?? 0 }} 个采集站</span>
      </div>
    </header>

    <!-- 工具条 -->
    <div class="price-toolbar">
      <div class="price-search-field">
        <label for="price-query-input" class="price-search-label">
          <Search :size="14" />找商品规格
        </label>
        <div class="price-search-box">
          <input
            id="price-query-input"
            ref="searchInput"
            v-model="query"
            class="price-search-input"
            aria-label="筛选标准商品名称"
            placeholder="输入标准商品名称，发现可比报价"
          />
          <button
            v-if="query"
            class="price-search-clear"
            aria-label="清除规格搜索"
            @click="query = ''"
          >
            <X :size="14" />
          </button>
          <kbd v-else class="price-search-kbd">{{ isMac ? "⌘ K" : "Ctrl K" }}</kbd>
        </div>
      </div>
      <div class="price-toolbar-actions">
        <button
          class="btn btn-secondary btn-sm"
          :aria-expanded="filtersOpen"
          @click="filtersOpen = !filtersOpen"
        >
          <SlidersHorizontal :size="13" />筛选
        </button>
        <label class="price-quality-toggle">
          <input v-model="quality" type="checkbox" />
          <span>过滤异常低价</span>
        </label>
        <button class="btn btn-ghost btn-sm" :disabled="!hasFilters" @click="clearFilters">
          <RotateCcw :size="13" />清空筛选
        </button>
        <button class="btn btn-secondary btn-sm" :disabled="loading" @click="loadCatalog(true)">
          <RefreshCw :size="13" :class="{ spin: loading }" />重读快照
        </button>
        <button class="btn btn-ghost btn-sm" @click="showSync = true">
          采集详情 <ChevronDown :size="13" />
        </button>
        <button class="btn btn-ghost btn-sm" @click="showBlacklist = true">
          <ShieldBan :size="13" />我的黑名单
          <em v-if="blockedCount" class="mono">{{ blockedCount }}</em>
        </button>
        <button class="btn btn-ghost btn-sm" :disabled="apiPlanBusy" @click="requestApiPlan">
          <Plug :size="13" />{{ apiPlanDone ? "已登记对接意向" : "申请 API 对接" }}
        </button>
      </div>
    </div>

    <div class="price-filters" :class="{ expanded: filtersOpen }">
      <label>
        报价来源
        <select v-model="catalogSource" class="input">
          <option value="all">全部来源</option>
          <option v-for="item in sourceOptions" :key="item" :value="item">
            {{ sourceName(item) }}
          </option>
        </select>
      </label>
      <label>
        商品形态
        <select v-model="productType" class="input">
          <option v-for="item in PRODUCT_TYPES" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label>
        订阅周期
        <select v-model="period" class="input">
          <option v-for="item in BILLING_PERIODS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
      <label>
        库存条件
        <select v-model="stockFilter" class="input">
          <option v-for="item in STOCK_FILTERS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </label>
    </div>

    <div class="price-source-strip">
      <span class="sync-indicator" :class="{ warning: sourceWarning }"></span>
      <span v-if="catalog?.updatedAt">
        上次同步 {{ dateTime(catalog.updatedAt) }}（北京时间）
      </span>
      <span v-else>尚未同步</span>
      <span v-if="failedSources" class="sync-warning">· {{ failedSources }} 站本轮同步失败，显示上次快照</span>
      <button class="price-source-more" @click="showSync = true">
        采集状态 <ChevronRight :size="12" />
      </button>
    </div>

    <p v-if="preferencesError" class="portal-error portal-banner" role="alert">
      {{ preferencesError }}<button @click="reloadPreferences">重试</button>
    </p>
    <p v-if="error" class="portal-error portal-banner" role="alert">
      {{ error
      }}<button @click="loadCatalog(true)">重试</button>
    </p>

    <!-- 分类 -->
    <nav class="price-categories" aria-label="商品分类">
      <button :class="{ active: category === 'all' }" @click="category = 'all'">
        全部分类 <span>{{ catalog?.groups.length ?? 0 }}</span>
      </button>
      <button
        v-for="item in categoryTabs"
        :key="item.name"
        :class="{ active: category === item.name }"
        @click="category = item.name"
      >
        {{ item.name }} <span>{{ item.count }}</span>
      </button>
      <button
        class="price-favorite-tab"
        :class="{ active: favoritesOnly }"
        :aria-pressed="favoritesOnly"
        @click="favoritesOnly = !favoritesOnly"
      >
        <Star :size="14" />我的关注 <span>{{ preferences.productFavorites.length }}</span>
      </button>
    </nav>

    <!-- 选品台 -->
    <section v-if="!selected" class="price-board" aria-labelledby="price-board-heading">
      <header class="price-board-heading">
        <div>
          <h2 id="price-board-heading">选品台</h2>
          <p>同规格合并两个来源的报价，最低价已排除售罄与规格不符的报价。</p>
        </div>
        <span class="portal-muted">{{ filteredGroups.length }} 个规格</span>
      </header>

      <div v-if="loading && !catalog" class="portal-state">
        <Loader2 :size="25" class="spin" />
        <h3>正在读取商品规格</h3>
        <p>核对标准规格与公开报价…</p>
      </div>
      <template v-else-if="catalog">
        <div v-if="!catalog.groups.length" class="portal-state">
          <PackageSearch :size="30" />
          <h3>暂无标准商品数据</h3>
          <p>采集快照尚未提供商品规格，稍后再试或查看采集状态。</p>
        </div>
        <div v-else-if="!visibleGroups.length" class="portal-state">
          <PackageSearch :size="30" />
          <h3>没有匹配的标准商品</h3>
          <p>调整名称或返回全部分类。</p>
          <button class="btn btn-secondary" @click="clearFilters">返回全部分类</button>
        </div>
        <div v-else class="price-product-grid" role="list" aria-label="标准商品规格">
          <button
            v-for="group in visibleGroups"
            :key="group.id"
            class="price-product-card"
            role="listitem"
            :aria-label="`查看规格：${group.product}`"
            @click="openGroup(group)"
          >
            <span class="price-card-category">{{ group.category }}</span>
            <span class="price-card-title">{{ group.product }}</span>
            <span class="price-card-prices" aria-label="当前快照最低价">
              <span class="price-card-price-label">当前最低价</span>
              <template v-if="group.lowestPrices.length">
                <span v-for="price in group.lowestPrices" :key="price.currency" class="price-card-minimum">
                  <strong>{{ priceNumber(price.price) }}</strong>
                  <span>{{ price.currency }}</span>
                </span>
              </template>
              <span v-else class="price-card-price-empty">
                {{ group.offers ? "价格待更新" : "暂无报价" }}
              </span>
            </span>
            <span class="price-card-meta">
              <span>{{ group.offers.toLocaleString("zh-CN") }} 条报价</span>
              <span>{{ group.sources.map(sourceName).join(" · ") }}</span>
              <span v-if="queryCount(group.id)" class="price-card-queries">
                已查询 {{ queryCount(group.id) }} 次
              </span>
            </span>
            <span class="price-card-action">
              查看商家报价 <ChevronRight :size="13" />
            </span>
          </button>
        </div>
        <div v-if="visibleGroups.length < filteredGroups.length" class="price-board-foot">
          <span class="portal-muted">
            已显示 {{ visibleGroups.length }} / {{ filteredGroups.length }} 个规格
          </span>
          <button class="btn btn-secondary btn-sm" @click="groupLimit += PAGE_SIZE * 3">
            加载更多
          </button>
        </div>
      </template>
    </section>

    <!-- 商家报价 -->
    <section v-else class="price-detail">
      <div class="price-breadcrumb">
        <button @click="closeGroup"><ArrowLeft :size="14" />返回选品台</button>
        <span>/</span>
        <span>{{ selected.product }}</span>
      </div>

      <header class="price-detail-heading">
        <div>
          <p class="portal-eyebrow">
            {{ selected.sources.map(sourceName).join(" · ") }} · {{ selected.category }}
          </p>
          <h2>
            {{ selected.product }}
            <button
              class="portal-icon-btn"
              :class="{ saved: isFavorite }"
              :aria-label="isFavorite ? '取消关注此规格' : '关注此规格'"
              :aria-pressed="isFavorite"
              :disabled="preferencesBusy || !!preferencesError"
              @click="toggle('productFavorites', selected.id)"
            >
              <Star :size="16" :fill="isFavorite ? 'currentColor' : 'none'" />
            </button>
          </h2>
          <p class="portal-muted">
            {{ pageData?.total ?? 0 }} 条报价 · 保留原始币种，下单前核对标题、交付与售后
          </p>
        </div>
        <div class="price-minima">
          <span class="portal-muted">本页同币种最低</span>
          <div v-if="pageMinima.length">
            <strong v-for="price in pageMinima" :key="price.currency">
              {{ money(price.price, price.currency) }}
            </strong>
          </div>
          <span v-else class="portal-muted">暂无可比报价</span>
        </div>
      </header>

      <div class="price-offer-controls">
        <div class="price-search-field">
          <label for="price-offer-input" class="price-search-label">
            <Search :size="13" />商家 / 商品关键词
          </label>
          <div class="price-search-box">
            <input
              id="price-offer-input"
              v-model="offerQuery"
              class="price-search-input"
              aria-label="在当前规格内查找商家或商品"
              placeholder="输入商铺名或商品关键词"
            />
            <button
              v-if="offerQuery"
              class="price-search-clear"
              aria-label="清除商家筛选"
              @click="offerQuery = ''"
            >
              <X :size="14" />
            </button>
          </div>
        </div>
        <select v-model="source" class="input price-select" aria-label="报价来源">
          <option value="all">全部来源</option>
          <option v-for="name in selected.sources" :key="name" :value="name">
            {{ sourceName(name) }}
          </option>
        </select>
        <select v-model="stockFilter" class="input price-select" aria-label="库存条件">
          <option v-for="item in STOCK_FILTERS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
        <select v-model="sort" class="input price-select" aria-label="报价排序">
          <option v-for="item in PRICE_SORTS" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div v-if="offersLoading && !pageData" class="price-quote-skeleton" role="status" aria-label="正在加载商家报价">
        <div v-for="n in 5" :key="n"><span></span><span></span></div>
      </div>
      <p v-else-if="offersError" class="portal-error" role="alert">
        {{ offersError }}<button @click="loadOffers()">重试</button>
      </p>
      <template v-else-if="pageData">
        <OfferTable v-if="sortedOffers.length" :offers="sortedOffers" :minima="pageMinima" />
        <div v-else class="portal-state">
          <PackageSearch :size="30" />
          <h3>{{ hasOfferFilters ? "没有符合筛选的报价" : "暂无商家报价" }}</h3>
          <p>
            {{
              hasOfferFilters
                ? "调整商家关键词、来源或库存条件后再试。"
                : "该规格目前没有公开报价。"
            }}
          </p>
          <button v-if="hasOfferFilters" class="btn btn-secondary" @click="resetOfferFilters">
            清空筛选
          </button>
        </div>
        <p class="price-time-note">
          <Clock :size="12" />第 {{ pageData.page }} / {{ offerPages }} 页 · 共
          {{ pageData.total.toLocaleString("zh-CN") }} 条 · 时间为北京时间
        </p>
        <nav v-if="pageData.total > pageData.pageSize" class="price-offer-pagination" aria-label="报价分页">
          <button
            class="btn btn-secondary btn-sm"
            :disabled="offersLoading || pageData.page <= 1"
            @click="goPage(pageData.page - 1)"
          >
            <ArrowLeft :size="13" />上一页
          </button>
          <span class="price-page-indicator">{{ pageData.page }} / {{ offerPages }}</span>
          <button
            class="btn btn-secondary btn-sm"
            :disabled="offersLoading || pageData.page >= offerPages"
            @click="goPage(pageData.page + 1)"
          >
            下一页<ArrowRight :size="13" />
          </button>
        </nav>
      </template>
    </section>

    <p class="price-note">
      价格与库存为采集快照，最终报价以原始商家页面为准。最低价不换算币种，不合并不同形态的规格；
      「过滤异常低价」会隐藏售罄、零价与标题明显不符本文规格的报价。
    </p>

    <BlacklistDialog v-if="showBlacklist" @close="showBlacklist = false" />
    <SyncDialog
      v-if="showSync"
      :sources="catalog?.sources ?? []"
      :interval="interval"
      :updated-at="catalog?.updatedAt ?? null"
      @close="showSync = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Search,
  X,
  RotateCcw,
  RefreshCw,
  ChevronDown,
  ChevronRight,
  ShieldBan,
  Star,
  Loader2,
  PackageSearch,
  ArrowLeft,
  ArrowRight,
  Clock,
  SlidersHorizontal,
  Plug,
} from "lucide-vue-next";
import BlacklistDialog from "./comp/BlacklistDialog.vue";
import SyncDialog from "./comp/SyncDialog.vue";
import OfferTable from "./comp/OfferTable.vue";
import { PAGE_SIZE, PRICE_SORTS, PRODUCT_TYPES, BILLING_PERIODS, STOCK_FILTERS } from "./enum";
import { usePortalPreferences } from "../../composables/usePortalPreferences";
import { portalApi } from "../../api/portal";
import { money, priceNumber, sourceName, dateTime, sortOffers } from "../../utils/portal";
import type { CurrencyPrice, OfferPage, PriceCatalog, PriceGroup } from "../../model/portal";

const route = useRoute();
const router = useRouter();
const { preferences, preferencesBusy, preferencesError, toggle, reloadPreferences } =
  usePortalPreferences();

const isMac = typeof navigator !== "undefined" && /mac/i.test(navigator.userAgent);
const searchInput = ref<HTMLInputElement>();

const catalog = ref<PriceCatalog | null>(null);
const loading = ref(false);
const error = ref("");
const showBlacklist = ref(false);
const showSync = ref(false);
const filtersOpen = ref(typeof window !== "undefined" && window.innerWidth > 900);
const apiPlanBusy = ref(false);
const apiPlanDone = ref(false);
const groupLimit = ref(PAGE_SIZE * 3);

const pageData = ref<OfferPage | null>(null);
const offersLoading = ref(false);
const offersError = ref("");

const interval = computed(() => catalog.value?.intervalMinutes ?? 15);
const failedSources = computed(
  () => catalog.value?.sources.filter((x) => x.status === "failed").length ?? 0,
);
const sourceWarning = computed(() => failedSources.value > 0);

/* ---------- URL 状态 ---------- */
const setQuery = (values: Record<string, string | undefined>) =>
  router.replace({ query: { ...route.query, ...values } });

const query = computed({
  get: () => String(route.query.q || ""),
  set: (value) => setQuery({ q: value || undefined }),
});
const category = computed({
  get: () => String(route.query.category || "all"),
  set: (value) =>
    setQuery({
      category: value === "all" ? undefined : value,
      group: undefined,
      page: undefined,
      merchant: undefined,
    }),
});
const quality = computed({
  get: () => route.query.quality !== "off",
  set: (value) => setQuery({ quality: value ? undefined : "off" }),
});
const favoritesOnly = computed({
  get: () => route.query.favorites === "1",
  set: (value) => setQuery({ favorites: value ? "1" : undefined }),
});
const source = computed({
  get: () => String(route.query.source || "all"),
  set: (value) => setQuery({ source: value === "all" ? undefined : value }),
});
const catalogSource = computed({
  get: () => String(route.query.from || "all"),
  set: (value) => setQuery({ from: value === "all" ? undefined : value }),
});
const productType = computed({
  get: () => String(route.query.type || "all"),
  set: (value) => setQuery({ type: value === "all" ? undefined : value }),
});
const period = computed({
  get: () => String(route.query.period || "all"),
  set: (value) => setQuery({ period: value === "all" ? undefined : value }),
});
const stockFilter = computed({
  get: () => String(route.query.stock || "available"),
  set: (value) => setQuery({ stock: value === "available" ? undefined : value }),
});
const sort = computed({
  get: () => String(route.query.sort || "price"),
  set: (value) => setQuery({ sort: value === "price" ? undefined : value }),
});
const offerQuery = computed({
  get: () => String(route.query.merchant || ""),
  set: (value) => setQuery({ merchant: value || undefined, page: undefined }),
});
const page = computed({
  get: () => Math.max(1, Number(route.query.page) || 1),
  set: (value) => setQuery({ page: value <= 1 ? undefined : String(value) }),
});

const selected = computed(() =>
  catalog.value?.groups.find((x) => x.id === route.query.group),
);
const isFavorite = computed(() =>
  selected.value ? preferences.value.productFavorites.includes(selected.value.id) : false,
);
const blockedCount = computed(
  () => preferences.value.blockedUrls.length + preferences.value.blockedKeywords.length,
);

/* ---------- 选品台筛选 ---------- */
const categoryTabs = computed(() => {
  const counts = new Map<string, number>();
  for (const group of catalog.value?.groups ?? [])
    counts.set(group.category, (counts.get(group.category) ?? 0) + 1);
  return [...counts].map(([name, count]) => ({ name, count }));
});
const sourceOptions = computed(() => (catalog.value?.sources ?? []).map((x) => x.source));
const filteredGroups = computed(() => {
  const needle = query.value.trim().toLocaleLowerCase();
  return (catalog.value?.groups ?? []).filter(
    (group) =>
      (category.value === "all" || group.category === category.value) &&
      (catalogSource.value === "all" || group.sources.includes(catalogSource.value)) &&
      (productType.value === "all" || group.productType === productType.value) &&
      (period.value === "all" || group.billingPeriod === period.value) &&
      (!favoritesOnly.value || preferences.value.productFavorites.includes(group.id)) &&
      (!needle || `${group.product} ${group.category}`.toLocaleLowerCase().includes(needle)),
  );
});
const visibleGroups = computed(() => filteredGroups.value.slice(0, groupLimit.value));
const hasFilters = computed(
  () =>
    Boolean(query.value) ||
    category.value !== "all" ||
    catalogSource.value !== "all" ||
    productType.value !== "all" ||
    period.value !== "all" ||
    stockFilter.value !== "available" ||
    favoritesOnly.value ||
    !quality.value ||
    Boolean(route.query.group),
);
const queryCount = (groupId: string) => catalog.value?.queries?.[groupId] ?? 0;

/* ---------- 商家报价 ---------- */
const hasOfferFilters = computed(
  () => Boolean(offerQuery.value) || source.value !== "all" || stockFilter.value !== "all",
);
const offerPages = computed(() =>
  pageData.value ? Math.max(1, Math.ceil(pageData.value.total / pageData.value.pageSize)) : 1,
);
const sortedOffers = computed(() =>
  pageData.value ? sortOffers(pageData.value.items, sort.value) : [],
);
const pageMinima = computed<CurrencyPrice[]>(() => {
  const minima = new Map<string, number>();
  for (const offer of pageData.value?.items ?? []) {
    if (offer.available === false || !offer.currency.trim()) continue;
    minima.set(offer.currency, Math.min(minima.get(offer.currency) ?? Infinity, offer.price));
  }
  return [...minima].map(([currency, price]) => ({ currency, price }));
});

/* ---------- 加载 ---------- */
async function loadCatalog(force = false) {
  if (loading.value && !force) return;
  loading.value = true;
  error.value = "";
  try {
    catalog.value = await portalApi.catalog(quality.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "分类和规格数据加载失败";
  } finally {
    loading.value = false;
  }
}

async function loadOffers() {
  const group = selected.value;
  if (!group) return;
  offersLoading.value = true;
  offersError.value = "";
  try {
    pageData.value = await portalApi.offers(group.id, {
      page: page.value,
      quality: quality.value,
      source: source.value,
      stock: stockFilter.value,
      keyword: offerQuery.value || undefined,
    });
  } catch (e) {
    offersError.value = e instanceof Error ? e.message : "商家报价加载失败";
  } finally {
    offersLoading.value = false;
  }
}

function openGroup(group: PriceGroup) {
  setQuery({ group: group.id, page: undefined, merchant: undefined, source: undefined, stock: undefined });
  portalApi.recordQuery(group.id).catch(() => {});
}

function closeGroup() {
  setQuery({ group: undefined, page: undefined, merchant: undefined });
}

function clearFilters() {
  setQuery({
    q: undefined,
    category: undefined,
    favorites: undefined,
    quality: undefined,
    group: undefined,
    page: undefined,
    merchant: undefined,
    source: undefined,
    from: undefined,
    type: undefined,
    period: undefined,
    stock: undefined,
  });
}

function resetOfferFilters() {
  setQuery({ merchant: undefined, source: undefined, stock: undefined, page: undefined });
}

function goPage(next: number) {
  page.value = next;
}

/** API 对接意向只登记到本地计数，不发起任何出站请求。 */
async function requestApiPlan() {
  if (apiPlanBusy.value || apiPlanDone.value) return;
  apiPlanBusy.value = true;
  try {
    await portalApi.requestApiPlan();
    apiPlanDone.value = true;
  } catch {
    apiPlanDone.value = false;
  } finally {
    apiPlanBusy.value = false;
  }
}

watch([quality, () => route.query.group], () => {
  loadCatalog(true);
});
/** 首次进入规格详情时，目录可能比 URL 后到；两边都到齐后只取一次报价。 */
function ensureOffers() {
  if (route.query.group && !pageData.value && !offersLoading.value) loadOffers();
}
watch(
  () => route.query.group,
  (groupId) => {
    pageData.value = null;
    if (groupId) loadOffers();
  },
);
watch(catalog, () => ensureOffers());
watch([page, source, stockFilter, offerQuery], () => {
  if (selected.value) loadOffers();
});
// 选品台换筛选时回到前 60 个规格，避免一次渲染全部分组。
watch([query, category, favoritesOnly, catalogSource, productType, period], () => {
  groupLimit.value = PAGE_SIZE * 3;
});
// 规格目录被重新读取后，若当前规格已不存在则回到选品台。
watch(catalog, (value) => {
  if (route.query.group && value && !value.groups.some((x) => x.id === route.query.group))
    closeGroup();
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

let timer: ReturnType<typeof setInterval>;
onMounted(async () => {
  await loadCatalog(true);
  ensureOffers();
  window.addEventListener("keydown", shortcut);
  // 与后端 15 分钟同步周期一致：前台每分钟重读本地快照，不触发外部采集。
  timer = setInterval(() => {
    if (document.visibilityState === "visible" && !document.querySelector("dialog[open]"))
      loadCatalog(true);
  }, 60000);
});
onUnmounted(() => {
  clearInterval(timer);
  window.removeEventListener("keydown", shortcut);
});
</script>
