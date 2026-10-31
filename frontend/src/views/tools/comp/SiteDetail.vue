<template>
  <PortalDialog :title="site.name" :eyebrow="categoryName" :busy="saving" @close="emit('close')">
    <div class="detail-brand">
      <span class="site-logo" :class="site.category">{{ siteInitial(site.name, site.url) }}</span>
      <div>
        <p class="portal-muted">
          {{ hostName(site.url) }} · {{ site.visits.toLocaleString() }} 次访问 ·
          {{ site.todayClicks }} 次今日点击
        </p>
        <p class="portal-muted">收录于 {{ dateOnly(site.createdAt) }}</p>
      </div>
    </div>

    <section class="detail-block">
      <h3>站点简介</h3>
      <p class="detail-description">{{ site.description || "暂未提供简介" }}</p>
    </section>

    <section class="detail-block">
      <h3>标签 · 点击搜索</h3>
      <div v-if="site.tags.length" class="portal-tags">
        <button v-for="tag in site.tags" :key="tag" @click="emit('tag', tag)">{{ tag }}</button>
      </div>
      <p v-else class="portal-muted">暂未提供标签</p>
    </section>

    <div class="detail-actions">
      <button class="btn btn-primary" @click="emit('open', site)">
        访问网站 <ExternalLink :size="14" />
      </button>
      <button
        class="btn btn-secondary"
        :disabled="preferencesBusy || !!preferencesError"
        @click="toggle('toolFavorites', site.id)"
      >
        <Star :size="14" :fill="saved ? 'currentColor' : 'none'" />{{
          saved ? "已收藏" : "收藏网站"
        }}
      </button>
    </div>
    <p class="portal-muted detail-url">{{ site.url }}</p>

    <section v-if="site.relayOffer" class="detail-block detail-offer">
      <h3>赠金 {{ amountYuan(site.relayOffer.amountCents) }} 元</h3>
      <h4>赠金领取说明</h4>
      <p class="detail-description">{{ site.relayOffer.claimInstructions }}</p>
      <p class="portal-muted">赠金由站点提供，兑换与售后以原站为准。</p>
    </section>

    <section v-if="site.hasContact" class="detail-block">
      <h3>站点联系方式</h3>
      <p class="portal-muted">由站点提交，登录会员或管理员可查看；收录不代表身份认证。</p>
      <p v-if="!isAuthenticated" class="portal-muted">
        请先登录 Badge 账号，再返回此处查看联系方式。
      </p>
      <p v-else-if="contactError" class="portal-error" role="alert">
        {{ contactError }}<button @click="loadContact">重试</button>
      </p>
      <button v-else-if="!contact" class="btn btn-secondary btn-sm" :disabled="contactBusy" @click="loadContact">
        {{ contactBusy ? "正在读取…" : "查看联系方式" }}
      </button>
      <div v-else class="contact-rows">
        <div v-for="row in contactRows" :key="row.label" class="contact-row">
          <strong>{{ row.label }}</strong>
          <span>{{ row.value }}</span>
          <button
            class="btn btn-secondary btn-sm"
            :aria-label="`复制 ${row.label}`"
            @click="copy(row.value, row.label)"
          >
            <component :is="copiedLabel === row.label ? Check : Copy" :size="13" />
            <span>{{ copiedLabel === row.label ? "已复制" : "复制" }}</span>
          </button>
        </div>
        <p v-if="!contactRows.length" class="portal-muted">站点暂未提供联系方式。</p>
      </div>
    </section>

    <section class="detail-reviews">
      <div class="portal-section-heading">
        <h3>社区评价</h3>
        <span class="portal-muted">{{ reviews.length }} 条</span>
      </div>
      <div v-if="loadingReviews" class="portal-status">
        <Loader2 :size="16" class="spin" />正在读取评价
      </div>
      <p v-else-if="reviewLoadError" class="portal-error" role="alert">
        {{ reviewLoadError }}<button @click="loadReviews">重试</button>
      </p>
      <p v-else-if="!reviews.length" class="portal-muted">还没有评价，分享你的第一手使用体验。</p>
      <article v-for="review in reviews" :key="review.id" class="tool-review">
        <div>
          <strong>{{ review.username }}</strong>
          <span class="review-stars" :aria-label="`${review.rating} 星`">
            {{ "★".repeat(review.rating) }}{{ "☆".repeat(5 - review.rating) }}
          </span>
        </div>
        <p>{{ review.content }}</p>
        <time>{{ dateTime(review.createdAt) }}</time>
      </article>

      <form
        v-if="isAuthenticated && !hasReviewed && !loadingReviews && !reviewLoadError"
        class="portal-form review-form"
        @submit.prevent="submitReview"
      >
        <label>
          你的评分
          <select v-model.number="rating" class="input">
            <option v-for="n in 5" :key="n" :value="n">{{ n }} 星</option>
          </select>
        </label>
        <label>
          使用感受
          <textarea
            v-model="content"
            class="input"
            rows="3"
            minlength="5"
            maxlength="1000"
            required
            placeholder="它帮你解决了什么问题？至少 5 个字。"
          ></textarea>
        </label>
        <p v-if="reviewError" class="portal-error" role="alert">{{ reviewError }}</p>
        <button class="btn btn-primary" :disabled="saving">
          {{ saving ? "正在提交…" : "发表评价" }}
        </button>
      </form>
      <p v-else-if="hasReviewed" class="portal-muted review-form">你已评价过此站点。</p>
      <router-link
        v-else-if="!isAuthenticated"
        :to="{ path: '/login', query: { redirect: route.fullPath } }"
        class="btn btn-secondary review-form"
      >
        登录后发表评价
      </router-link>
      <p v-if="copyMessage" class="portal-muted" role="status">{{ copyMessage }}</p>
    </section>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { Star, ExternalLink, Loader2, Copy, Check } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { portalApi } from "../../../api/portal";
import { useAuth } from "../../../composables/useAuth";
import { usePortalPreferences } from "../../../composables/usePortalPreferences";
import {
  dateOnly,
  dateTime,
  hostName,
  siteInitial,
  amountYuan,
} from "../../../utils/portal";
import type { Review, Site, SiteContact } from "../../../model/portal";

const props = defineProps<{ site: Site; categoryName?: string }>();
const emit = defineEmits<{ close: []; tag: [tag: string]; open: [site: Site] }>();
const route = useRoute();
const { user, isAuthenticated } = useAuth();
const { preferences, preferencesBusy, preferencesError, toggle } = usePortalPreferences();

const saved = computed(() => preferences.value.toolFavorites.includes(props.site.id));

const reviews = ref<Review[]>([]);
const loadingReviews = ref(true);
const reviewLoadError = ref("");
const saving = ref(false);
const reviewError = ref("");
const rating = ref(5);
const content = ref("");

const contact = ref<SiteContact | null>(null);
const contactBusy = ref(false);
const contactError = ref("");
const copyMessage = ref("");

const contactRows = computed(() =>
  contact.value
    ? (
        [
          ["QQ", contact.value.qq],
          ["微信", contact.value.wechat],
          ["Telegram", contact.value.telegram],
          ["邮箱", contact.value.email],
        ] as const
      )
        .filter(([, value]) => value)
        .map(([label, value]) => ({ label, value }))
    : [],
);

const hasReviewed = computed(() =>
  reviews.value.some((x) => x.username === user.value?.username),
);

async function loadReviews() {
  loadingReviews.value = true;
  reviewLoadError.value = "";
  try {
    reviews.value = await portalApi.reviews(props.site.id);
  } catch (e) {
    reviewLoadError.value = e instanceof Error ? e.message : "评价加载失败";
  } finally {
    loadingReviews.value = false;
  }
}

async function loadContact() {
  contactBusy.value = true;
  contactError.value = "";
  try {
    contact.value = await portalApi.contact(props.site.id);
  } catch (e) {
    contactError.value = e instanceof Error ? e.message : "联系方式加载失败";
  } finally {
    contactBusy.value = false;
  }
}

async function submitReview() {
  saving.value = true;
  reviewError.value = "";
  try {
    const review = await portalApi.review(props.site.id, {
      rating: rating.value,
      content: content.value,
    });
    reviews.value.unshift(review);
    content.value = "";
  } catch (e) {
    reviewError.value = e instanceof Error ? e.message : "提交失败";
  } finally {
    saving.value = false;
  }
}

const copiedLabel = ref("");

async function copy(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value);
    copiedLabel.value = label;
    setTimeout(() => {
      if (copiedLabel.value === label) copiedLabel.value = "";
    }, 1800);
    copyMessage.value = `${label} 已复制`;
  } catch {
    copyMessage.value = "复制失败，请选中文字手动复制";
  }
}

onMounted(loadReviews);
</script>
