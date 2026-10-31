<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <template v-if="!createdApp">
        <div class="modal-head">
          <div>
            <div class="modal-title">注册新应用</div>
            <div class="modal-sub">把你的下一个站点接入 Badge 统一登录</div>
          </div>
          <button class="modal-close" @click="$emit('close')"><X :size="15" /></button>
        </div>

        <div class="modal-body">
          <form class="fields" @submit.prevent="handleSubmit">
            <div class="field">
              <label class="field-label">应用名称</label>
              <input v-model="appName" class="input" placeholder="例如：汉风西游" />
            </div>

            <div class="field">
              <label class="field-label">Client ID</label>
              <input
                v-model="clientId"
                class="input input-mono"
                placeholder="小写字母、数字与下划线"
                @input="formatClientId"
              />
            </div>

            <div class="field">
              <label class="field-label">分类</label>
              <select v-model="appCategory" class="select">
                <option value="SERVICE">服务 / 工具</option>
                <option value="GAME">游戏</option>
                <option value="PORTAL">门户</option>
                <option value="WIKI">知识文档</option>
              </select>
            </div>

            <div class="field">
              <label class="field-label">应用主页</label>
              <input v-model="homepageUrl" class="input input-mono" placeholder="https://app.example.com" />
            </div>

            <div class="field">
              <label class="field-label">SSO 回调地址</label>
              <input v-model="redirectUri" class="input input-mono" placeholder="https://app.example.com/auth/sso-callback" />
              <span class="field-hint">签发票据后用户将被重定向到这里；换票时校验回调必须与登记的地址同源。</span>
            </div>

            <div class="field">
              <label class="field-label">一句话说明 <span class="field-hint">（选填）</span></label>
              <input v-model="description" class="input" placeholder="这个应用是做什么的" />
            </div>

            <div v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</div>

            <div class="form-actions">
              <button type="button" class="btn btn-secondary" @click="$emit('close')">取消</button>
              <button type="submit" class="btn btn-primary" :disabled="submitting">
                <Loader2 v-if="submitting" :size="14" class="spin" />
                <span>注册应用</span>
              </button>
            </div>
          </form>
        </div>
      </template>

      <!-- 密钥只出现这一次 -->
      <template v-else>
        <div class="modal-head">
          <div>
            <div class="modal-title">应用已注册</div>
            <div class="modal-sub">请立即保存 Client Secret，它只会显示这一次</div>
          </div>
          <button class="modal-close" @click="finish"><X :size="15" /></button>
        </div>

        <div class="modal-body">
          <div class="secret-card">
            <div class="secret-row">
              <span class="secret-key">Client ID</span>
              <code class="mono">{{ createdApp.clientId }}</code>
            </div>
            <div class="secret-row">
              <span class="secret-key">Client Secret</span>
              <code class="mono secret-val">{{ createdApp.clientSecret }}</code>
            </div>
          </div>

          <div class="alert alert-info" style="margin-top: 14px;">
            密钥落库即为密文，本平台无法再次查看。请把它配置到子应用的服务端环境变量中，
            用于换取票据时的服务端对服务端校验。
          </div>

          <div class="form-actions" style="margin-top: 18px;">
            <button class="btn btn-secondary" @click="copySecret">
              <component :is="copied ? Check : Copy" :size="14" />
              <span>{{ copied ? '已复制' : '复制密钥' }}</span>
            </button>
            <button class="btn btn-primary" @click="finish">我已保存，完成</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { api } from '../api/client';
import { X, Loader2, Copy, Check } from 'lucide-vue-next';

const emit = defineEmits(['close', 'created']);

const appName = ref('');
const clientId = ref('');
const appCategory = ref('SERVICE');
const homepageUrl = ref('http://localhost:3000');
const redirectUri = ref('http://localhost:3000/auth/sso-callback');
const description = ref('');
const submitting = ref(false);
const errorMsg = ref('');
const createdApp = ref(null);
const copied = ref(false);

const formatClientId = () => {
  clientId.value = clientId.value.toLowerCase().replace(/[^a-z0-9_]/g, '');
};

const handleSubmit = async () => {
  submitting.value = true;
  errorMsg.value = '';
  try {
    createdApp.value = await api.createApp({
      appName: appName.value,
      clientId: clientId.value,
      appCategory: appCategory.value,
      homepageUrl: homepageUrl.value,
      redirectUri: redirectUri.value,
      description: description.value,
      iconType: appCategory.value === 'GAME' ? 'gamepad' : appCategory.value === 'SERVICE' ? 'server' : 'globe',
    });
    window.dispatchEvent(new CustomEvent('badge:apps-changed'));
  } catch (err) {
    errorMsg.value = err.message || '注册新应用失败';
  } finally {
    submitting.value = false;
  }
};

const copySecret = () => {
  if (!createdApp.value?.clientSecret) return;
  navigator.clipboard.writeText(createdApp.value.clientSecret);
  copied.value = true;
  setTimeout(() => (copied.value = false), 2000);
};

const finish = () => {
  emit('created');
  emit('close');
};
</script>

<style scoped>
.fields { display: flex; flex-direction: column; gap: 14px; }

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

.secret-card {
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  overflow: hidden;
}

.secret-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
}

.secret-row + .secret-row { border-top: 1px solid var(--line); background: var(--surface-2); }

.secret-key {
  width: 96px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--ink-2);
}

.secret-row code {
  flex: 1;
  font-size: 14px;
  color: var(--ink);
  word-break: break-all;
  line-height: 1.55;
}
</style>
