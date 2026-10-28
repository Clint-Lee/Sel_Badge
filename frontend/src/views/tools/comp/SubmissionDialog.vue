<template>
  <PortalDialog
    :title="isAdmin ? '工具收录与审核' : '推荐一个好工具'"
    eyebrow="BADGE · COMMUNITY CURATED"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="portal-tabs">
      <button :class="{ active: tab === 'submit' }" @click="tab = 'submit'">
        提交收录</button
      ><button
        :class="{ active: tab === 'history' }"
        @click="
          tab = 'history';
          load();
        "
      >
        {{ isAdmin ? "收录审核" : "我的申请" }}
      </button>
    </div>
    <p v-if="error" class="portal-error" role="alert">{{ error }}</p>
    <p v-if="success" class="portal-success" role="status">{{ success }}</p>
    <form v-if="tab === 'submit'" class="portal-form" @submit.prevent="submit">
      <p class="portal-muted">
        分享你使用过的好工具，审核通过后会出现在工具导航。
      </p>
      <label
        >工具名称<input
          v-model="form.name"
          class="input"
          required
          maxlength="80"
          placeholder="这个工具叫什么？"
      /></label>
      <label
        >官方网站<input
          v-model="form.url"
          type="url"
          class="input"
          required
          maxlength="1000"
          placeholder="https://"
      /></label>
      <label
        >工具分类<select v-model="form.category" class="input" required>
          <option
            v-for="category in categories"
            :key="category.id"
            :value="category.id"
          >
            {{ category.label }}
          </option>
        </select></label
      >
      <label
        >推荐理由<textarea
          v-model="form.description"
          class="input"
          required
          minlength="10"
          maxlength="1000"
          rows="4"
          placeholder="介绍它的用途和你的使用体验，至少 10 个字。"
        ></textarea>
      </label>
      <label
        >标签<span class="portal-muted">逗号分隔，最多 10 个</span
        ><input
          v-model="tags"
          class="input"
          maxlength="310"
          placeholder="写作，设计，开源"
      /></label>
      <button class="btn btn-primary" :disabled="busy">
        {{ busy ? "正在提交…" : "提交收录申请" }}
      </button>
    </form>
    <div v-else class="submission-list">
      <div v-if="loading" class="portal-status">
        <Loader2 :size="16" class="spin" />正在读取申请
      </div>
      <p v-else-if="!submissions.length && !error" class="portal-empty-small">
        还没有收录申请。
      </p>
      <article
        v-for="item in submissions"
        :key="item.id"
        class="submission-item"
      >
        <div class="portal-section-heading">
          <strong>{{ item.tool.name }}</strong
          ><span
            class="badge"
            :class="
              item.status === 'APPROVED'
                ? 'badge-green'
                : item.status === 'PENDING'
                  ? 'badge-amber'
                  : 'badge-gray'
            "
            >{{ statusLabels[item.status] }}</span
          >
        </div>
        <p>{{ item.tool.description }}</p>
        <a
          :href="safeLink(item.tool.url)"
          target="_blank"
          rel="noopener noreferrer"
          class="portal-text-link"
          >{{ item.tool.url }} <ExternalLink :size="12"
        /></a>
        <p class="portal-muted">
          {{ item.username }} · {{ dateTime(item.createdAt) }}
        </p>
        <div v-if="isAdmin && item.status === 'PENDING'" class="detail-actions">
          <button
            class="btn btn-primary btn-sm"
            :disabled="busy"
            @click="moderate(item.id, true)"
          >
            通过收录</button
          ><button
            class="btn btn-secondary btn-sm"
            :disabled="busy"
            @click="moderate(item.id, false)"
          >
            不予收录
          </button>
        </div>
      </article>
      <button v-if="error" class="btn btn-secondary" @click="load">重试</button>
    </div>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref, reactive } from "vue";
import { Loader2, ExternalLink } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { TOOL_CATEGORIES } from "../enum";
import { useAuth } from "../../../composables/useAuth";
import { portalApi } from "../../../api/portal";
import { dateTime, safeLink } from "../../../utils/portal";
import type { Submission } from "../../../model/portal";
const emit = defineEmits<{ close: []; updated: [] }>();
const { isAdmin } = useAuth();
const categories = TOOL_CATEGORIES.filter(
  (x) => !["all", "favorites"].includes(x.id),
);
const statusLabels = {
  PENDING: "待审核",
  APPROVED: "已收录",
  REJECTED: "未通过",
};
const tab = ref("submit"),
  busy = ref(false),
  loading = ref(false),
  error = ref(""),
  success = ref(""),
  tags = ref("");
const form = reactive({
  name: "",
  url: "",
  category: "assistant",
  description: "",
});
const submissions = ref<Submission[]>([]);
async function submit() {
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    if (!safeLink(form.url)) throw new Error("请输入有效的 http 或 https 网址");
    const list = tags.value
      .split(/[,，]/)
      .map((x) => x.trim())
      .filter(Boolean);
    if (list.length > 10 || list.some((x) => x.length > 30))
      throw new Error("最多 10 个标签，每个不超过 30 字");
    await portalApi.submit({ ...form, tags: list });
    success.value = "收录申请已提交，可以在申请记录中查看审核结果。";
    tab.value = "history";
    await load();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "提交失败";
  } finally {
    busy.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    submissions.value = await portalApi.submissions();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "申请加载失败";
  } finally {
    loading.value = false;
  }
}
async function moderate(id: string, approve: boolean) {
  busy.value = true;
  error.value = "";
  try {
    const item = await portalApi.moderate(id, approve);
    submissions.value = submissions.value.map((x) => (x.id === id ? item : x));
    success.value = approve ? "工具已收录到导航。" : "已处理此申请。";
    emit("updated");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "处理失败";
  } finally {
    busy.value = false;
  }
}
</script>
