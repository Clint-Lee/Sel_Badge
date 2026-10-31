<template>
  <PortalDialog
    :title="isAdmin ? '收录申请与审核' : '申请收录一个网站'"
    eyebrow="BADGE · COMMUNITY CURATED"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="portal-tabs">
      <button :class="{ active: tab === 'submit' }" @click="tab = 'submit'">提交收录</button>
      <button
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
        分享你在用的网站、货源或交流群。审核通过后展示在目录中，联系方式登录后可见。
      </p>

      <div class="kind-switch" role="group" aria-label="收录类型">
        <button
          v-for="item in SUBMISSION_KINDS"
          :key="item.value"
          type="button"
          class="kind-chip"
          :class="{ active: form.kind === item.value }"
          @click="form.kind = item.value as typeof form.kind"
        >
          {{ item.label }}
        </button>
      </div>

      <label>
        {{ form.kind === "qq-group" ? "QQ群名称" : "网站名称" }}
        <input v-model="form.name" class="input" required maxlength="80" placeholder="它叫什么？" />
      </label>
      <label>
        {{ form.kind === "qq-group" ? "加群链接" : "网站地址" }}
        <input
          v-model="form.url"
          type="url"
          class="input"
          required
          maxlength="1000"
          placeholder="https://"
        />
      </label>
      <label>
        所属分类
        <select v-model="form.category" class="input" required>
          <option v-for="item in categories" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
        </select>
      </label>
      <label>
        {{ form.kind === "qq-group" ? "群主题与适合人群" : "站点介绍" }}
        <textarea
          v-model="form.description"
          class="input"
          required
          minlength="10"
          maxlength="2000"
          rows="4"
          placeholder="介绍它能帮大家做什么，至少 10 个字。"
        ></textarea>
      </label>
      <label>
        标签
        <span class="portal-muted">逗号分隔，最多 10 个，每个不超过 30 字</span>
        <input v-model="tags" class="input" maxlength="310" placeholder="货源，接码，虚拟卡" />
      </label>
      <label v-if="form.kind === 'qq-group'">
        群号
        <span class="portal-muted">5–12 位数字</span>
        <input v-model="form.qq" class="input" maxlength="12" inputmode="numeric" placeholder="636922735" />
      </label>

      <fieldset class="contact-fieldset">
        <legend>联系方式<span class="portal-muted">（登录会员可见）</span></legend>
        <p class="portal-muted">
          {{
            form.kind === "merchant"
              ? "商家自荐请至少填写 QQ、微信或 Telegram 中的一种。"
              : "选填，方便同行交流或核对信息。"
          }}
        </p>
        <div class="contact-grid">
          <label>
            QQ
            <input v-model="form.qq" class="input" maxlength="20" placeholder="选填" />
          </label>
          <label>
            微信
            <input v-model="form.wechat" class="input" maxlength="40" placeholder="选填" />
          </label>
          <label>
            Telegram
            <input v-model="form.telegram" class="input" maxlength="40" placeholder="选填" />
          </label>
          <label>
            邮箱
            <input v-model="form.email" class="input" type="email" maxlength="120" placeholder="选填" />
          </label>
        </div>
      </fieldset>

      <fieldset v-if="form.category === 'api-relays'" class="contact-fieldset">
        <legend>中转站赠金<span class="portal-muted">（选填，公开展示）</span></legend>
        <div class="contact-grid">
          <label>
            赠金金额（元）
            <input
              v-model="offerAmount"
              class="input"
              type="number"
              min="1.01"
              max="1000000"
              step="0.01"
              placeholder="5.00"
            />
          </label>
        </div>
        <label>
          赠金领取说明
          <textarea
            v-model="offerClaim"
            class="input"
            rows="3"
            maxlength="500"
            placeholder="例如：联系 QQ 并备注来自 Badge，注册后领取赠金"
          ></textarea>
        </label>
      </fieldset>

      <button class="btn btn-primary" :disabled="busy">
        {{ busy ? "正在提交…" : "提交收录申请" }}
      </button>
    </form>

    <div v-else class="submission-list">
      <div v-if="loading" class="portal-status">
        <Loader2 :size="16" class="spin" />正在读取申请
      </div>
      <p v-else-if="!submissions.length && !error" class="portal-empty-small">还没有收录申请。</p>
      <article v-for="item in submissions" :key="item.id" class="submission-item">
        <div class="portal-section-heading">
          <strong>{{ item.site.name }}</strong>
          <span
            class="badge"
            :class="
              item.status === 'APPROVED'
                ? 'badge-green'
                : item.status === 'PENDING'
                  ? 'badge-amber'
                  : 'badge-gray'
            "
          >
            {{ statusLabels[item.status] }}
          </span>
        </div>
        <p>{{ item.site.description }}</p>
        <a
          :href="safeLink(item.site.url)"
          target="_blank"
          rel="noopener noreferrer"
          class="portal-text-link"
        >
          {{ item.site.url }} <ExternalLink :size="12" />
        </a>
        <p class="portal-muted">
          {{ kindLabels[item.kind] || item.kind }} · {{ item.username }} · {{ dateTime(item.createdAt) }}
        </p>
        <div v-if="isAdmin && item.status === 'PENDING'" class="detail-actions">
          <button class="btn btn-primary btn-sm" :disabled="busy" @click="moderate(item.id, true)">
            通过收录
          </button>
          <button class="btn btn-secondary btn-sm" :disabled="busy" @click="moderate(item.id, false)">
            不予收录
          </button>
        </div>
      </article>
      <button v-if="error" class="btn btn-secondary" @click="load">重试</button>
    </div>
  </PortalDialog>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { Loader2, ExternalLink } from "lucide-vue-next";
import PortalDialog from "../../../components/portal/PortalDialog.vue";
import { SUBMISSION_KINDS } from "../enum";
import { useAuth } from "../../../composables/useAuth";
import { portalApi } from "../../../api/portal";
import { dateTime, safeLink } from "../../../utils/portal";
import type { Category, Submission } from "../../../model/portal";

const emit = defineEmits<{ close: []; updated: [] }>();
const { isAdmin } = useAuth();

const categories = ref<Category[]>([]);
const statusLabels = { PENDING: "待审核", APPROVED: "已收录", REJECTED: "未通过" } as const;
const kindLabels: Record<string, string> = {
  website: "网站收录",
  merchant: "商家自荐",
  "qq-group": "QQ群收录",
};

const tab = ref<"submit" | "history">("submit");
const busy = ref(false);
const loading = ref(false);
const error = ref("");
const success = ref("");
const tags = ref("");
const offerAmount = ref("");
const offerClaim = ref("");
const submissions = ref<Submission[]>([]);

const form = reactive({
  name: "",
  url: "",
  category: "",
  description: "",
  kind: "website" as "website" | "merchant" | "qq-group",
  qq: "",
  wechat: "",
  telegram: "",
  email: "",
});

async function submit() {
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    if (!safeLink(form.url)) throw new Error("请输入有效的 http 或 https 网址");
    const list = tags.value
      .split(/[,，、\n]/)
      .map((x) => x.trim())
      .filter(Boolean);
    if (list.length > 10 || list.some((x) => x.length > 30))
      throw new Error("最多 10 个标签，每个不超过 30 字");
    const cents = offerAmount.value ? Math.round(Number(offerAmount.value) * 100) : 0;
    if (cents > 0 && (!Number.isFinite(cents) || cents < 101))
      throw new Error("赠金须大于 1 元且不超过 1000000 元");
    if (cents > 0 && !offerClaim.value.trim()) throw new Error("请填写赠金领取说明");
    await portalApi.submit({
      ...form,
      tags: list,
      relayOffer:
        form.category === "api-relays" && cents > 0
          ? { amountCents: cents, claimInstructions: offerClaim.value.trim() }
          : null,
    });
    success.value = "收录申请已提交，可在申请记录中查看审核结果。";
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
    success.value = approve ? "已收录到导航目录。" : "已处理此申请。";
    emit("updated");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "处理失败";
  } finally {
    busy.value = false;
  }
}

onMounted(async () => {
  try {
    categories.value = await portalApi.categories();
    form.category = categories.value[0]?.id || "ai";
  } catch (e) {
    error.value = e instanceof Error ? e.message : "分类加载失败";
  }
});
</script>
