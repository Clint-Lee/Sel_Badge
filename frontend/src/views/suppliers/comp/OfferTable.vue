<template>
  <div class="offer-table-wrap">
    <table class="offer-table">
      <thead>
        <tr>
          <th>商家 / 商品信息</th>
          <th>报价</th>
          <th>库存</th>
          <th>来源采集时间</th>
          <th class="table-right">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="offer in offers" :key="`${offer.groupId}-${offer.id}`">
          <td class="offer-product-cell">
            <strong>{{ offer.shopName || "商家未公开" }}</strong>
            <p>{{ offer.title }}</p>
            <span class="offer-source">{{ sourceName(offer.source) }}</span
            ><span v-if="offer.suspicious" class="badge badge-amber"
              >疑似异常报价</span
            ><span
              v-if="offer.minOrderQuantity && offer.minOrderQuantity > 1"
              class="badge badge-amber"
              >{{ offer.minOrderQuantity }} 件起购</span
            >
          </td>
          <td class="offer-price-cell">
            <strong>{{ money(offer.price, offer.currency) }}</strong
            ><span
              v-if="
                offer.available !== false &&
                minima.some(
                  (x) =>
                    x.currency === offer.currency && x.price === offer.price,
                )
              "
              class="lowest-label"
              >当前规格快照最低</span
            ><span class="portal-muted">{{ offer.currency }}</span>
          </td>
          <td>
            <span
              class="stock-badge"
              :class="
                offer.available === true
                  ? 'available'
                  : offer.available === false
                    ? 'soldout'
                    : 'unknown'
              "
              >{{
                offer.stock !== null
                  ? offer.stock.toLocaleString() + " 件"
                  : offer.available === true
                    ? "有货 · 数量未知"
                    : offer.available === false
                      ? "暂无库存"
                      : "库存未公开"
              }}</span
            >
          </td>
          <td class="offer-time-cell">
            <time>{{ dateTime(offer.capturedAt) }}</time
            ><span v-if="isStale(offer.capturedAt)" class="stale-label"
              >采集已超 15 分钟</span
            ><span v-else class="portal-muted">最近采集</span>
          </td>
          <td>
            <div class="offer-actions">
              <a
                :href="safeLink(offer.url)"
                target="_blank"
                rel="noopener noreferrer"
                class="btn btn-secondary btn-sm"
                >前往店铺 <ArrowUpRight :size="13" /></a
              ><button
                class="offer-block-btn"
                :disabled="
                  preferencesBusy ||
                  !!preferencesError ||
                  preferences.blockedUrls.length >= 500
                "
                :aria-label="`隐藏商品链接：${offer.title}`"
                @click="toggle('blockedUrls', offer.url)"
              >
                <EyeOff :size="12" />隐藏链接
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<script setup lang="ts">
import { ArrowUpRight, EyeOff } from "lucide-vue-next";
import { usePortalPreferences } from "../../../composables/usePortalPreferences";
import {
  money,
  dateTime,
  sourceName,
  safeLink,
  isStale,
} from "../../../utils/portal";
import type { Offer } from "../../../model/portal";
defineProps<{
  offers: Offer[];
  minima: { currency: string; price: number }[];
}>();
const { preferences, preferencesBusy, preferencesError, toggle } =
  usePortalPreferences();
</script>
