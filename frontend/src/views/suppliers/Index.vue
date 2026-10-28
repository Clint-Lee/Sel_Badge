<template>
  <div class="portal-page price-page page-container">
    <header class="portal-hero">
      <div>
        <p class="portal-eyebrow">
          <ChartNoAxesCombined :size="14" /> BADGE COMPARE / 02
        </p>
        <h1>选对规格，<span>看清每一份报价。</span></h1>
        <p class="portal-hero-description">
          把分散的公开报价放在一起，让选择多一份依据。
        </p>
        <div class="portal-hero-meta">
          <span
            ><strong>{{ snapshot.offers.length.toLocaleString() }}</strong>
            条快照报价</span
          ><span
            ><strong>{{ allGroups.length }}</strong> 个来源规格</span
          ><span>保留原币种 · 原店直达</span>
        </div>
      </div>
      <div class="price-hero-actions">
        <button class="btn btn-secondary" @click="showBlacklist = true">
          <ShieldBan :size="15" />我的黑名单<span
            v-if="blockedCount"
            class="mono"
            >{{ blockedCount }}</span
          ></button
        ><button
          class="btn btn-ghost"
          :disabled="loading"
          title="重读本地快照，不触发商家采集"
          @click="load"
        >
          <RefreshCw :size="14" :class="{ spin: loading }" />重读快照
        </button>
      </div>
    </header>
    <div class="price-sync-strip">
      <span class="sync-indicator" :class="{ warning: syncWarning }"></span
      ><span
        >{{ snapshot.sources.length }} 个数据源 · 每
        {{ snapshot.intervalMinutes }} 分钟后台同步</span
      ><span v-if="syncWarning" class="sync-warning">存在失败或超期快照</span
      ><button @click="showSync = true">
        采集详情 <ChevronRight :size="13" />
      </button>
    </div>
    <div
      v-if="preferencesError"
      class="portal-error portal-banner"
      role="alert"
    >
      {{ preferencesError }}<button @click="reloadPreferences">重试</button>
    </div>
    <div v-if="error" class="portal-error portal-banner" role="alert">
      {{ error }}{{ snapshot.offers.length ? "；保留上次成功读取的快照。" : ""
      }}<button @click="load">重试</button>
    </div>
    <div class="portal-controls">
      <div class="portal-search">
        <Search :size="17" /><input
          v-model="query"
          aria-label="搜索标准商品"
          placeholder="搜索标准商品名称，如 ChatGPT Plus、Claude…"
        /><button
          v-if="query"
          class="portal-icon-btn"
          aria-label="清除商品搜索"
          @click="query = ''"
        >
          <X :size="15" />
        </button>
      </div>
      <button
        class="btn btn-secondary price-filter-toggle"
        :aria-expanded="filtersOpen"
        @click="filtersOpen = !filtersOpen"
      >
        <SlidersHorizontal :size="15" />筛选</button
      ><button class="btn btn-ghost" @click="reset">
        <RotateCcw :size="14" />重置
      </button>
    </div>
    <div class="price-filter-bar" :class="{ expanded: filtersOpen }">
      <label
        >报价来源<select v-model="source" class="input">
          <option value="all">全部来源</option>
          <option value="priceai">PriceAI</option>
          <option value="pickai">PickAI</option>
        </select></label
      ><label
        >商品形态<select v-model="productType" class="input">
          <option
            v-for="item in PRODUCT_TYPES"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </option>
        </select></label
      ><label
        >订阅周期<select v-model="period" class="input">
          <option
            v-for="item in BILLING_PERIODS"
            :key="item.value"
            :value="item.value"
          >
            {{ item.label }}
          </option>
        </select></label
      ><label
        >库存条件<select v-model="stock" class="input">
          <option value="available">隐藏售罄（含未知）</option>
          <option value="in-stock">仅明确有货</option>
          <option value="all">全部库存</option>
        </select></label
      ><label class="portal-checkbox"
        ><input v-model="quality" type="checkbox" />过滤疑似异常报价</label
      >
    </div>
    <div class="portal-category-list price-categories" aria-label="商品分类">
      <button :class="{ active: category === 'all' }" @click="category = 'all'">
        全部分类<span>{{ allGroups.length }}</span></button
      ><button
        v-for="item in categories"
        :key="item.name"
        :class="{ active: category === item.name }"
        @click="category = item.name"
      >
        {{ item.name }}<span>{{ item.count }}</span></button
      ><button
        :class="{ active: favoritesOnly }"
        :aria-pressed="favoritesOnly"
        @click="favoritesOnly = !favoritesOnly"
      >
        <Star :size="14" />我的关注<span>{{
          preferences.productFavorites.length
        }}</span>
      </button>
    </div>
    <section class="portal-results">
      <div v-if="loading && !snapshot.offers.length" class="portal-state">
        <Loader2 :size="25" class="spin" />
        <h3>正在读取报价快照</h3>
        <p>核对商品规格和公开报价…</p>
      </div>
      <template v-else-if="selected">
        <div class="price-breadcrumb">
          <button @click="setQuery({ group: undefined, page: undefined })">
            <ArrowLeft :size="14" />返回选品台</button
          ><span>/</span><span>{{ selected.product }}</span>
        </div>
        <div class="price-selected-heading">
          <div>
            <span class="portal-eyebrow"
              >{{ sourceName(selected.source) }} · {{ selected.category }}</span
            >
            <h2>
              {{ selected.product
              }}<button
                class="portal-icon-btn"
                :class="{
                  saved: preferences.productFavorites.includes(selected.id),
                }"
                :aria-label="
                  preferences.productFavorites.includes(selected.id)
                    ? '取消关注此规格'
                    : '关注此规格'
                "
                :aria-pressed="
                  preferences.productFavorites.includes(selected.id)
                "
                :disabled="preferencesBusy || !!preferencesError"
                @click="toggle('productFavorites', selected.id)"
              >
                <Star
                  :size="18"
                  :fill="
                    preferences.productFavorites.includes(selected.id)
                      ? 'currentColor'
                      : 'none'
                  "
                />
              </button>
            </h2>
            <p class="portal-muted">
              {{ matchingOffers.length }} 条符合条件的报价 ·
              来源规格仅作选品分类，下单前核对标题、交付与售后。
            </p>
          </div>
          <div class="selected-minima">
            <span class="portal-muted">当前筛选 · 同币种快照最低</span>
            <div v-if="selectedPrices.length">
              <strong v-for="item in selectedPrices" :key="item.currency">{{
                money(item.price, item.currency)
              }}</strong>
            </div>
            <span v-else class="portal-muted">暂无可比报价</span>
          </div>
        </div>
        <div class="offer-controls">
          <div class="portal-search">
            <Search :size="15" /><input
              v-model="merchant"
              aria-label="搜索商家或商品标题"
              placeholder="在当前规格中搜索商家或商品标题"
            />
          </div>
          <select
            v-model="sort"
            class="input portal-select"
            aria-label="报价排序"
          >
            <option
              v-for="item in PRICE_SORTS"
              :key="item.value"
              :value="item.value"
            >
              {{ item.label }}
            </option>
          </select>
        </div>
        <OfferTable
          v-if="visibleOffers.length"
          :offers="visibleOffers"
          :minima="selectedPrices"
        />
        <div v-else class="portal-state">
          <PackageSearch :size="30" />
          <h3>没有符合筛选的报价</h3>
          <p>尝试调整库存、来源或关键词，也可以检查个人黑名单。</p>
          <button class="btn btn-secondary" @click="reset">清空筛选</button>
        </div>
        <div v-if="matchingOffers.length" class="portal-pagination">
          <span
            >第 {{ page }} / {{ offerPages }} 页 · 共
            {{ matchingOffers.length }} 条报价 · 北京时间</span
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
              :disabled="page >= offerPages"
              @click="page++"
            >
              下一页
            </button>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="portal-section-heading">
          <div class="portal-heading-inline">
            <h2>规格选品台</h2>
            <span class="portal-muted">{{ groups.length }} 个规格</span>
          </div>
          <span class="portal-muted">先选规格，再看商家报价</span>
        </div>
        <div v-if="groups.length" class="product-table-wrap">
          <table class="product-table">
            <thead>
              <tr>
                <th>标准商品规格</th>
                <th>当前快照最低</th>
                <th>报价 / 来源</th>
                <th class="table-right">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="group in visibleGroups" :key="group.id">
                <td>
                  <div class="product-name-cell">
                    <span class="product-symbol">{{
                      group.category.slice(0, 1)
                    }}</span>
                    <div>
                      <button @click="openGroup(group.id)">
                        {{ group.product }}</button
                      ><span>{{ group.category }}</span>
                    </div>
                  </div>
                </td>
                <td>
                  <div v-if="group.prices.length" class="product-price">
                    <strong
                      v-for="price in group.prices"
                      :key="price.currency"
                      >{{ money(price.price, price.currency) }}</strong
                    >
                  </div>
                  <span v-else class="portal-muted">暂无在售报价</span>
                </td>
                <td>
                  <span class="product-offer-count"
                    >{{ group.offers.length }} 条报价</span
                  ><span class="offer-source">{{
                    sourceName(group.source)
                  }}</span>
                </td>
                <td>
                  <div class="product-row-actions">
                    <button
                      class="portal-icon-btn"
                      :class="{
                        saved: preferences.productFavorites.includes(group.id),
                      }"
                      :aria-label="`${preferences.productFavorites.includes(group.id) ? '取消关注' : '关注'} ${group.product}`"
                      :aria-pressed="
                        preferences.productFavorites.includes(group.id)
                      "
                      :disabled="preferencesBusy || !!preferencesError"
                      @click="toggle('productFavorites', group.id)"
                    >
                      <Star
                        :size="16"
                        :fill="
                          preferences.productFavorites.includes(group.id)
                            ? 'currentColor'
                            : 'none'
                        "
                      /></button
                    ><button
                      class="btn btn-secondary btn-sm"
                      @click="openGroup(group.id)"
                    >
                      比较报价 <ArrowRight :size="13" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="portal-state">
          <PackageSearch :size="30" />
          <h3>
            {{ favoritesOnly ? "还没有匹配的关注规格" : "没有匹配的商品规格" }}
          </h3>
          <p>
            {{
              favoritesOnly
                ? "点击规格旁的星标，关注常用的商品。"
                : "调整关键词或筛选条件后再试。"
            }}
          </p>
          <button class="btn btn-secondary" @click="reset">查看全部规格</button>
        </div>
        <div v-if="groups.length > PAGE_SIZE" class="portal-pagination">
          <span
            >共 {{ groups.length }} 个规格 · 第 {{ groupPage }} /
            {{ groupPages }} 页</span
          >
          <div>
            <button
              class="btn btn-secondary btn-sm"
              :disabled="groupPage <= 1"
              @click="groupPage--"
            >
              上一页</button
            ><button
              class="btn btn-secondary btn-sm"
              :disabled="groupPage >= groupPages"
              @click="groupPage++"
            >
              下一页
            </button>
          </div>
        </div>
      </template>
    </section>
    <div class="portal-bottom-note">
      <Info :size="15" /><span
        >最低价仅针对当前已收录快照，未知库存不代表有货。规格、起购量、最终价格与售后以原始商家页面为准。</span
      >
    </div>
    <BlacklistDialog
      v-if="showBlacklist"
      @close="showBlacklist = false"
    /><SyncDialog
      v-if="showSync"
      :sources="snapshot.sources"
      :interval="snapshot.intervalMinutes"
      @close="showSync = false"
    />
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ChartNoAxesCombined,
  ShieldBan,
  RefreshCw,
  ChevronRight,
  Search,
  X,
  SlidersHorizontal,
  RotateCcw,
  Star,
  Loader2,
  ArrowLeft,
  ArrowRight,
  PackageSearch,
  Info,
} from "lucide-vue-next";
import BlacklistDialog from "./comp/BlacklistDialog.vue";
import SyncDialog from "./comp/SyncDialog.vue";
import OfferTable from "./comp/OfferTable.vue";
import { PRICE_SORTS, PRODUCT_TYPES, BILLING_PERIODS, PAGE_SIZE } from "./enum";
import { usePortalPreferences } from "../../composables/usePortalPreferences";
import { portalApi } from "../../api/portal";
import {
  filterOffers,
  groupOffers,
  sortOffers,
  money,
  sourceName,
  isStale,
} from "../../utils/portal";
import type { PriceSnapshot } from "../../model/portal";
const route = useRoute(),
  router = useRouter();
const {
  preferences,
  preferencesBusy,
  preferencesError,
  toggle,
  reloadPreferences,
} = usePortalPreferences();
const snapshot = ref<PriceSnapshot>({
  offers: [],
  sources: [],
  intervalMinutes: 15,
});
const loading = ref(false),
  error = ref(""),
  showBlacklist = ref(false),
  showSync = ref(false),
  filtersOpen = ref(false);
const setQuery = (values: Record<string, string | undefined>) =>
  router.replace({ query: { ...route.query, ...values } });
function filter(key: string, fallback: string) {
  return computed({
    get: () => String(route.query[key] || fallback),
    set: (value: string) =>
      setQuery({
        [key]: value === fallback ? undefined : value,
        page: undefined,
      }),
  });
}
const query = filter("q", ""),
  category = filter("category", "all"),
  source = filter("source", "all"),
  productType = filter("type", "all"),
  period = filter("period", "all"),
  stock = filter("stock", "available"),
  sort = filter("sort", "price"),
  merchant = filter("merchant", "");
const quality = computed({
  get: () => route.query.quality !== "off",
  set: (value) =>
    setQuery({ quality: value ? undefined : "off", page: undefined }),
});
const favoritesOnly = computed({
  get: () => route.query.favorites === "1",
  set: (value) => setQuery({ favorites: value ? "1" : undefined }),
});
const page = computed({
  get: () =>
    Math.max(1, Math.min(offerPages.value, Number(route.query.page) || 1)),
  set: (value) => setQuery({ page: String(value) }),
});
const groupPage = ref(1);
const allGroups = computed(() => groupOffers(snapshot.value.offers));
const selected = computed(() =>
  allGroups.value.find((x) => x.id === route.query.group),
);
const categories = computed(() =>
  [...new Set(allGroups.value.map((x) => x.category))].map((name) => ({
    name,
    count: allGroups.value.filter((x) => x.category === name).length,
  })),
);
const blockedCount = computed(
  () =>
    preferences.value.blockedUrls.length +
    preferences.value.blockedKeywords.length,
);
const clock = ref(Date.now());
const syncWarning = computed(() => {
  void clock.value;
  return snapshot.value.sources.some(
    (x) =>
      x.status === "error" ||
      isStale(x.lastSuccessAt, snapshot.value.intervalMinutes),
  );
});
const eligible = computed(() =>
  filterOffers(
    snapshot.value.offers,
    preferences.value,
    quality.value,
    stock.value === "in-stock",
  ).filter(
    (x) =>
      (stock.value !== "available" || x.available !== false) &&
      (source.value === "all" || x.source === source.value) &&
      (productType.value === "all" || x.productType === productType.value) &&
      (period.value === "all" || x.billingPeriod === period.value),
  ),
);
const groups = computed(() =>
  groupOffers(eligible.value).filter(
    (x) =>
      (category.value === "all" || x.category === category.value) &&
      (!favoritesOnly.value ||
        preferences.value.productFavorites.includes(x.id)) &&
      `${x.product} ${x.category}`
        .toLowerCase()
        .includes(query.value.trim().toLowerCase()),
  ),
);
const groupPages = computed(() =>
  Math.max(1, Math.ceil(groups.value.length / PAGE_SIZE)),
);
const visibleGroups = computed(() =>
  groups.value.slice(
    (groupPage.value - 1) * PAGE_SIZE,
    groupPage.value * PAGE_SIZE,
  ),
);
const matchingOffers = computed(() =>
  sortOffers(
    eligible.value.filter(
      (x) =>
        x.groupId === selected.value?.id &&
        `${x.shopName} ${x.title}`
          .toLocaleLowerCase()
          .includes(merchant.value.trim().toLocaleLowerCase()),
    ),
    sort.value,
  ),
);
const selectedPrices = computed(
  () => groupOffers(matchingOffers.value)[0]?.prices || [],
);
const offerPages = computed(() =>
  Math.max(1, Math.ceil(matchingOffers.value.length / PAGE_SIZE)),
);
const visibleOffers = computed(() =>
  matchingOffers.value.slice(
    (page.value - 1) * PAGE_SIZE,
    page.value * PAGE_SIZE,
  ),
);
watch(
  [
    query,
    category,
    source,
    productType,
    period,
    stock,
    quality,
    favoritesOnly,
    () => preferences.value,
  ],
  () => {
    groupPage.value = 1;
  },
);
watch(groupPages, (count) => {
  groupPage.value = Math.min(groupPage.value, count);
});
const openGroup = (id: string) =>
  setQuery({ group: id, merchant: undefined, page: undefined });
const reset = () =>
  router.replace({
    query: route.query.group ? { group: String(route.query.group) } : {},
  });
let alive = true;
async function load() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const data = await portalApi.prices();
    if (alive) snapshot.value = data;
  } catch (e) {
    if (alive)
      error.value = e instanceof Error ? e.message : "报价快照读取失败";
  } finally {
    loading.value = false;
  }
}
let timer: ReturnType<typeof setInterval>;
onMounted(() => {
  load();
  timer = setInterval(() => {
    clock.value = Date.now();
    if (
      document.visibilityState === "visible" &&
      !document.querySelector("dialog[open]")
    )
      load();
  }, 60000);
});
onUnmounted(() => {
  alive = false;
  clearInterval(timer);
});
</script>
