<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-head">
        <div>
          <div class="modal-title">前往「{{ app.appName }}」</div>
          <div class="modal-sub">已为此应用签发一次性登录票据，5 分钟内有效</div>
        </div>
        <button class="modal-close" @click="$emit('close')"><X :size="15" /></button>
      </div>

      <div class="modal-body">
        <div v-if="loading" class="loading-panel">
          <Loader2 :size="17" class="spin" />
          <span>正在签发票据…</span>
        </div>

        <template v-else-if="ticketData">
          <div class="ticket-row">
            <div class="ticket-info">
              <span class="ticket-label">一次性票据</span>
              <code class="mono ticket-val">{{ ticketData.ticket }}</code>
            </div>
            <div class="countdown" :class="{ warn: remainSeconds <= 60 }">
              <span class="num">{{ remainText }}</span>
            </div>
          </div>

          <div class="url-box">
            <code class="mono url-text">{{ ticketData.redirectUrl }}</code>
          </div>

          <div class="actions">
            <button class="btn btn-secondary" @click="copyUrl">
              <component :is="copied ? Check : Copy" :size="14" />
              <span>{{ copied ? '已复制回调地址' : '复制回调地址' }}</span>
            </button>
            <a :href="ticketData.redirectUrl" class="btn btn-primary">
              <ArrowRight :size="14" />
              <span>立即进入</span>
            </a>
          </div>

          <p class="explain">
            目标应用的后端会拿这张票据（连同它的 Client Secret）向 Badge 换取正式身份令牌；
            票据核销一次即失效，即使链接被转发也无法二次使用。
          </p>
        </template>

        <div v-else class="alert alert-error">{{ errorMsg || '签发票据失败' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { api } from '../api/client';
import { X, Loader2, Copy, Check, ArrowRight } from 'lucide-vue-next';

const props = defineProps({
  app: { type: Object, required: true },
});

const emit = defineEmits(['close']);

const loading = ref(true);
const ticketData = ref(null);
const errorMsg = ref('');
const copied = ref(false);
const remainSeconds = ref(300);
let timer = null;

const remainText = computed(() => {
  const s = Math.max(0, remainSeconds.value);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
});

const issueTicket = async () => {
  try {
    ticketData.value = await api.ssoAuthorize({ clientId: props.app.clientId });
    remainSeconds.value = ticketData.value.expiresInSeconds || 300;
    timer = setInterval(() => {
      remainSeconds.value -= 1;
      if (remainSeconds.value <= 0) clearInterval(timer);
    }, 1000);
  } catch (err) {
    errorMsg.value = err.message || '生成单点登录票据失败';
  } finally {
    loading.value = false;
  }
};

onMounted(issueTicket);
onUnmounted(() => timer && clearInterval(timer));

const copyUrl = () => {
  if (!ticketData.value?.redirectUrl) return;
  navigator.clipboard.writeText(ticketData.value.redirectUrl);
  copied.value = true;
  setTimeout(() => (copied.value = false), 2000);
};
</script>

<style scoped>
.loading-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px;
  color: var(--ink-3);
  font-size: 13px;
}

.ticket-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  background: var(--surface-2);
}

.ticket-info { min-width: 0; display: flex; flex-direction: column; gap: 3px; }

.ticket-label {
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
}

.ticket-val {
  font-size: 13px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.countdown {
  font-size: 15px;
  font-weight: 650;
  color: var(--accent-ink);
  flex-shrink: 0;
}

.countdown.warn { color: var(--red); }

.url-box {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  background: var(--surface-2);
  max-height: 72px;
  overflow: hidden;
}

.url-text { font-size: 13px; color: var(--ink-2); word-break: break-all; line-height: 1.5; }

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.explain {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
  font-size: 13px;
  color: var(--ink-3);
  line-height: 1.6;
}
</style>
