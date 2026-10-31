<template>
  <PortalDialog title="采集状态与数据范围" eyebrow="公开来源 · 本地快照" @close="emit('close')">
    <p class="portal-muted">
      计划每 {{ interval }} 分钟采集一轮；页面刷新与“重读快照”只读本站已保存的数据，不触发对外采集。
      超过一个周期没有成功会标记为过期，采集失败时保留上次成功结果。
    </p>
    <div v-if="!sources.length" class="portal-muted">暂无同步记录。</div>
    <div v-else class="price-freshness-strip" aria-label="来源快照时间">
      <div v-for="source in sources" :key="source.source" class="price-freshness-item">
        <strong>{{ sourceName(source.source) }}</strong>
        <span>{{ dateTime(source.lastSuccessAt) }}</span>
        <span :class="source.status === 'failed' ? 'price-source-warning' : 'portal-muted'">
          {{
            source.status === "failed"
              ? "本轮失败"
              : source.status === "running"
                ? "本轮采集中"
                : source.status === "complete"
                  ? "上轮完成"
                  : "状态未公开"
          }}
        </span>
      </div>
    </div>
    <article v-for="source in sources" :key="source.source" class="source-detail">
      <div class="portal-section-heading">
        <h3>{{ sourceName(source.source) }}</h3>
        <span
          class="badge"
          :class="
            source.status === 'failed'
              ? 'badge-red'
              : isStale(source.lastSuccessAt, interval)
                ? 'badge-amber'
                : 'badge-green'
          "
        >
          {{
            source.status === "failed"
              ? isStale(source.lastSuccessAt, interval)
                ? "更新失败，显示上次数据"
                : "更新失败，保留上次快照"
              : isStale(source.lastSuccessAt, interval)
                ? "本地快照已超周期"
                : "本地快照已更新"
          }}
        </span>
      </div>
      <p>{{ source.message }}</p>
      <dl>
        <div>
          <dt>当前本地报价</dt>
          <dd>{{ source.offers.toLocaleString("zh-CN") }} 条</dd>
        </div>
        <div v-if="detailNumber(source, 'products') !== null">
          <dt>标准规格</dt>
          <dd>{{ detailNumber(source, "products") }}</dd>
        </div>
        <div v-if="detailNumber(source, 'rows') !== null">
          <dt>采集记录</dt>
          <dd>{{ detailNumber(source, "rows") }}</dd>
        </div>
        <div>
          <dt>上次成功同步</dt>
          <dd>{{ dateTime(source.lastSuccessAt) }}</dd>
        </div>
        <div>
          <dt>最近尝试</dt>
          <dd>{{ dateTime(source.attemptedAt) }}</dd>
        </div>
      </dl>
    </article>
    <p class="portal-muted">
      PriceAI 使用官方 Price Radar 免费只读接口，每规格公开 Top 5 与预设；PickAI 读取各标准规格全部分页的公开报价。
      来源覆盖范围不同，报价数量随上游变化，不代表全站或市场全部报价。
    </p>
    <p class="portal-muted sync-explanation">
      本地同步时间表示 Badge 读取来源的时间；每条报价同时展示来源采集时间。不同币种分别比较，不自动换算。
      来源未明确说明的库存、形态与周期一律标为未知。
    </p>
    <a
      href="https://priceai.cc/price-radar-api.md"
      target="_blank"
      rel="noopener noreferrer"
      class="portal-text-link"
    >
      PriceAI 公开接口说明 <ExternalLink :size="13" />
    </a>
    <details class="price-api-details">
      <summary>只读 API 接入</summary>
      <p class="portal-muted">在你的工具中读取当前已保存的公开报价快照与选品台。</p>
      <code>{{ snapshotEndpoint }}</code>
      <code>{{ catalogEndpoint }}</code>
      <div class="detail-actions">
        <button class="btn btn-secondary btn-sm" @click="copyEndpoints">
          <component :is="copied ? Check : Copy" :size="13" />
          <span>{{ copied ? "已复制" : "复制接口地址" }}</span>
        </button>
        <a
          :href="catalogEndpoint"
          target="_blank"
          rel="noopener noreferrer"
          class="btn btn-secondary btn-sm"
        >
          查看 JSON <ExternalLink :size="13" />
        </a>
      </div>
      <p v-if="copyMessage" class="portal-muted" role="status">{{ copyMessage }}</p>
    </details>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { ExternalLink, Copy, Check } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { sourceName, dateTime, isStale } from "../../../utils/portal";
import type { SourceStatus } from "../../../model/portal";

defineProps<{ sources: SourceStatus[]; interval: number; updatedAt: string | null }>();
const emit = defineEmits<{ close: [] }>();

const base = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "");
const snapshotEndpoint = new URL(`${base}/portal/prices`, window.location.origin).href;
const catalogEndpoint = new URL(`${base}/portal/prices/catalog`, window.location.origin).href;
const copyMessage = ref("");
const copied = ref(false);

const detailNumber = (source: SourceStatus, key: string) => {
  const value = source.details?.[key];
  return typeof value === "number" ? value : null;
};

async function copyEndpoints() {
  try {
    await navigator.clipboard.writeText(`${snapshotEndpoint}\n${catalogEndpoint}`);
    copied.value = true;
    setTimeout(() => (copied.value = false), 2000);
    copyMessage.value = "接口地址已复制。";
  } catch {
    copyMessage.value = "复制失败，请选中上方接口地址手动复制。";
  }
}
</script>
