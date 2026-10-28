<template>
  <PortalDialog
    title="我的黑名单"
    eyebrow="你的列表，你来决定"
    :busy="preferencesBusy"
    @close="emit('close')"
  >
    <p class="portal-muted">
      {{
        isAuthenticated
          ? "已绑定 Badge 账号，可跨设备使用。"
          : "访客设置仅保存在当前浏览器；登录后使用账号独立设置。"
      }}黑名单只影响你看到的报价。
    </p>
    <p v-if="preferencesError" class="portal-error" role="alert">
      {{ preferencesError }}<button @click="reloadPreferences">重试</button>
    </p>
    <form class="blacklist-form" @submit.prevent="addKeyword">
      <label for="blocked-keyword">隐藏商家或商品关键词</label>
      <div>
        <input
          id="blocked-keyword"
          v-model="keyword"
          class="input"
          maxlength="100"
          required
          placeholder="输入商家名或标题中的关键词"
        /><button
          class="btn btn-primary"
          :disabled="preferencesBusy || !!preferencesError"
        >
          添加
        </button>
      </div>
    </form>
    <h3 class="blacklist-section-title">
      关键词 <span>{{ preferences.blockedKeywords.length }} / 100</span>
    </h3>
    <div v-if="preferences.blockedKeywords.length" class="blacklist-keywords">
      <span v-for="word in preferences.blockedKeywords" :key="word"
        >{{ word
        }}<button
          :aria-label="`恢复关键词 ${word}`"
          :disabled="preferencesBusy || !!preferencesError"
          @click="toggle('blockedKeywords', word)"
        >
          <X :size="13" /></button
      ></span>
    </div>
    <p v-else class="portal-muted">暂无关键词。新增报价也会按关键词过滤。</p>
    <h3 class="blacklist-section-title">
      商品链接 <span>{{ preferences.blockedUrls.length }} / 500</span>
    </h3>
    <ul class="blocked-url-list">
      <li v-for="url in preferences.blockedUrls" :key="url">
        <span>{{ url }}</span
        ><button
          class="btn btn-secondary btn-sm"
          :disabled="preferencesBusy || !!preferencesError"
          @click="toggle('blockedUrls', url)"
        >
          恢复
        </button>
      </li>
    </ul>
    <p v-if="!preferences.blockedUrls.length" class="portal-muted">
      还没有隐藏的链接，可在商家报价列表中添加。
    </p>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref } from "vue";
import { X } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { useAuth } from "../../../composables/useAuth";
import { usePortalPreferences } from "../../../composables/usePortalPreferences";
const emit = defineEmits<{ close: [] }>();
const { isAuthenticated } = useAuth();
const {
  preferences,
  preferencesBusy,
  preferencesError,
  toggle,
  reloadPreferences,
} = usePortalPreferences();
const keyword = ref("");
async function addKeyword() {
  const word = keyword.value.trim();
  if (
    !word ||
    preferences.value.blockedKeywords.includes(word) ||
    preferences.value.blockedKeywords.length >= 100
  )
    return;
  await toggle("blockedKeywords", word);
  if (!preferencesError.value) keyword.value = "";
}
</script>
