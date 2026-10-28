<template>
  <PortalDialog
    title="采集状态与数据范围"
    eyebrow="公开来源 · 本地快照"
    @close="emit('close')"
  >
    <p class="portal-muted">
      计划每 {{ interval }} 分钟同步。页面刷新只重读 Badge
      已保存的快照，同步失败保留上次成功的数据。
    </p>
    <article
      v-for="source in sources"
      :key="source.source"
      class="source-detail"
    >
      <div class="portal-section-heading">
        <h3>{{ sourceName(source.source) }}</h3>
        <span
          class="badge"
          :class="
            source.status === 'error'
              ? 'badge-red'
              : isStale(source.lastSuccessAt, interval)
                ? 'badge-amber'
                : 'badge-green'
          "
          >{{
            source.status === "error"
              ? "更新失败，保留旧快照"
              : isStale(source.lastSuccessAt, interval)
                ? "本地同步已超周期"
                : "本地快照已更新"
          }}</span
        >
      </div>
      <p>{{ source.message }}</p>
      <dl>
        <div>
          <dt>当前本地报价</dt>
          <dd>{{ source.offers.toLocaleString() }} 条</dd>
        </div>
        <div>
          <dt>上次成功读取</dt>
          <dd>{{ dateTime(source.lastSuccessAt) }}</dd>
        </div>
        <div>
          <dt>最近尝试</dt>
          <dd>{{ dateTime(source.attemptedAt) }}</dd>
        </div>
      </dl>
    </article>
    <p class="portal-muted">
      PriceAI 使用官方 Price Radar 免费只读接口，每规格公开 Top 5 及预设；PickAI
      保存每个标准规格价格升序前 50 条。来源覆盖不同，不代表全站或市场全部报价。
    </p>
    <p class="portal-muted sync-explanation">
      本地同步时间表示 Badge
      读取来源的时间；每条报价同时展示来源采集时间。不同币种分别比较，不自动换算。来源未明确说明的库存、形态与周期均标为未知。
    </p>
    <a
      href="https://priceai.cc/price-radar-api.md"
      target="_blank"
      rel="noopener noreferrer"
      class="portal-text-link"
      >PriceAI 公开接口说明 <ExternalLink :size="13"
    /></a>
    <details class="price-api-details">
      <summary>只读 API 接入</summary>
      <p class="portal-muted">
        在你的工具中读取 Badge 当前已保存的公开报价快照。
      </p>
      <code>{{ endpoint }}</code>
      <div class="detail-actions">
        <button class="btn btn-secondary btn-sm" @click="copyEndpoint">
          复制接口地址
        </button>
        <a
          :href="endpoint"
          target="_blank"
          rel="noopener noreferrer"
          class="btn btn-secondary btn-sm"
          >查看 JSON <ExternalLink :size="12"
        /></a>
      </div>
      <p v-if="copyMessage" class="portal-muted" role="status">
        {{ copyMessage }}
      </p>
    </details>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { ExternalLink } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { sourceName, dateTime, isStale } from "../../../utils/portal";
import type { SourceStatus } from "../../../model/portal";
defineProps<{ sources: SourceStatus[]; interval: number }>();
const emit = defineEmits<{ close: [] }>();
const endpoint = new URL(
  `${(import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "")}/portal/prices`,
  window.location.origin,
).href;
const copyMessage = ref("");
async function copyEndpoint() {
  try {
    await navigator.clipboard.writeText(endpoint);
    copyMessage.value = "接口地址已复制。";
  } catch {
    copyMessage.value = "复制失败，请选中上方接口地址手动复制。";
  }
}
</script>
