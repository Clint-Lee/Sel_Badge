<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal">
      <div class="modal-head">
        <div>
          <div class="modal-title">应用画像 · {{ app.appName }}</div>
          <div class="modal-sub">每个应用可以拥有独立的昵称与私有配置</div>
        </div>
        <button class="modal-close" @click="$emit('close')"><X :size="15" /></button>
      </div>

      <div class="modal-body">
        <form class="fields" @submit.prevent="handleSubmit">
          <div class="field">
            <label class="field-label">在这个应用里的昵称</label>
            <input v-model="appNickname" class="input" placeholder="留空则使用用户名" />
          </div>

          <div class="field">
            <label class="field-label">私有配置 JSON</label>
            <textarea v-model="appMetadata" class="textarea input-mono" rows="8" spellcheck="false"></textarea>
            <span class="field-hint">
              会以 AES-256-GCM 加密存储，仅授权给「{{ app.appName }}」解密读取。留空表示清除。
            </span>
          </div>

          <div v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</div>

          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="$emit('close')">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              <Loader2 v-if="submitting" :size="14" class="spin" />
              <span>保存</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { api } from '../api/client';
import { X, Loader2 } from 'lucide-vue-next';

const props = defineProps({
  app: { type: Object, required: true },
});

const emit = defineEmits(['close', 'updated']);

const appNickname = ref(props.app.boundNickname || '');
const appMetadata = ref(props.app.appMetadata || '');
const submitting = ref(false);
const errorMsg = ref('');

const handleSubmit = async () => {
  submitting.value = true;
  errorMsg.value = '';
  try {
    if (appMetadata.value.trim()) {
      JSON.parse(appMetadata.value); // 语法错误直接拦截，不给后端发脏数据
    }
    await api.updateAppProfile(props.app.clientId, {
      appNickname: appNickname.value,
      appMetadata: appMetadata.value,
    });
    window.dispatchEvent(new CustomEvent('badge:apps-changed'));
    emit('updated');
  } catch (err) {
    if (err instanceof SyntaxError) {
      errorMsg.value = '私有配置不是合法的 JSON，请检查格式';
    } else {
      errorMsg.value = err.message || '更新失败，请稍后再试';
    }
  } finally {
    submitting.value = false;
  }
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
</style>
