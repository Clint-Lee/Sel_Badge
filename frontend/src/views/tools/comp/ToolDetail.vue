<template>
  <PortalDialog
    :title="tool.name"
    :eyebrow="categoryFor(tool.category).label"
    :busy="saving"
    @close="emit('close')"
  >
    <p class="detail-description">{{ tool.description }}</p>
    <div class="portal-tags">
      <span v-for="tag in tool.tags" :key="tag">{{ tag }}</span>
    </div>
    <div class="detail-actions">
      <a
        :href="safeLink(tool.url)"
        class="btn btn-primary"
        target="_blank"
        rel="noopener noreferrer"
        >访问工具 <ExternalLink :size="14" /></a
      ><button
        class="btn btn-secondary"
        :disabled="preferencesBusy || !!preferencesError"
        @click="toggle('toolFavorites', tool.id)"
      >
        <Star :size="14" :fill="saved ? 'currentColor' : 'none'" />{{
          saved ? "已收藏" : "收藏工具"
        }}
      </button>
    </div>
    <p class="portal-muted detail-url">{{ tool.url }}</p>
    <p v-if="preferencesError" class="portal-error" role="alert">
      {{ preferencesError }}
    </p>
    <section class="detail-reviews">
      <div class="portal-section-heading">
        <h3>使用评价</h3>
        <span class="portal-muted">{{ reviews.length }} 条</span>
      </div>
      <div v-if="loading" class="portal-status">
        <Loader2 :size="16" class="spin" />正在读取评价
      </div>
      <div v-else-if="loadError" class="portal-error" role="alert">
        {{ loadError }}<button @click="loadReviews">重试</button>
      </div>
      <p v-else-if="!reviews.length" class="portal-muted">
        还没有评价，分享你的第一手使用体验。
      </p>
      <article v-for="review in reviews" :key="review.id" class="tool-review">
        <div>
          <strong>{{ review.username }}</strong
          ><span class="review-stars" :aria-label="`${review.rating} 星`"
            >{{ "★".repeat(review.rating)
            }}{{ "☆".repeat(5 - review.rating) }}</span
          >
        </div>
        <p>{{ review.content }}</p>
        <time>{{ dateTime(review.createdAt) }}</time>
      </article>
    </section>
    <form
      v-if="isAuthenticated && !hasReviewed && !loading && !loadError"
      class="portal-form review-form"
      @submit.prevent="submitReview"
    >
      <label
        >你的评分<select v-model.number="rating" class="input">
          <option v-for="n in 5" :key="n" :value="n">{{ n }} 星</option>
        </select></label
      >
      <label
        >使用感受<textarea
          v-model="content"
          class="input"
          rows="3"
          minlength="5"
          maxlength="1000"
          required
          placeholder="它帮你解决了什么问题？至少 5 个字。"
        ></textarea>
      </label>
      <p v-if="error" class="portal-error" role="alert">{{ error }}</p>
      <button class="btn btn-primary" :disabled="saving">
        {{ saving ? "正在提交…" : "发表评价" }}
      </button>
    </form>
    <p v-else-if="hasReviewed" class="portal-muted review-form">
      你已评价过此工具。
    </p>
    <router-link
      v-else-if="!isAuthenticated"
      :to="{ path: '/login', query: { redirect: route.fullPath } }"
      class="btn btn-secondary review-form"
      >登录后发表评价</router-link
    >
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { Star, ExternalLink, Loader2 } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { categoryFor } from "../enum";
import { portalApi } from "../../../api/portal";
import { useAuth } from "../../../composables/useAuth";
import { usePortalPreferences } from "../../../composables/usePortalPreferences";
import { dateTime, safeLink } from "../../../utils/portal";
import type { Tool, Review } from "../../../model/portal";
const props = defineProps<{ tool: Tool }>();
const emit = defineEmits<{ close: [] }>();
const route = useRoute();
const { user, isAuthenticated } = useAuth();
const { preferences, preferencesBusy, preferencesError, toggle } =
  usePortalPreferences();
const saved = computed(() =>
  preferences.value.toolFavorites.includes(props.tool.id),
);
const reviews = ref<Review[]>([]),
  loading = ref(true),
  loadError = ref(""),
  saving = ref(false),
  error = ref("");
const rating = ref(5),
  content = ref("");
const hasReviewed = computed(() =>
  reviews.value.some((x) => x.username === user.value?.username),
);
async function loadReviews() {
  loading.value = true;
  loadError.value = "";
  try {
    reviews.value = await portalApi.reviews(props.tool.id);
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : "评价加载失败";
  } finally {
    loading.value = false;
  }
}
async function submitReview() {
  saving.value = true;
  error.value = "";
  try {
    const review = await portalApi.review(props.tool.id, {
      rating: rating.value,
      content: content.value,
    });
    reviews.value.unshift(review);
    content.value = "";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "提交失败";
  } finally {
    saving.value = false;
  }
}
onMounted(loadReviews);
</script>
