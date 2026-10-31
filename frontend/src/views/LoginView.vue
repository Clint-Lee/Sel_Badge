<template>
  <div class="login-page">
    <!-- 左：品牌面 -->
    <aside class="login-side">
      <div class="side-grid" aria-hidden="true"></div>
      <router-link to="/" class="side-mark">
        <ShieldCheck :size="17" stroke-width="2.2" />
      </router-link>
      <div class="side-caption mono">BADGE · SSO</div>
    </aside>

    <!-- 右：表单 -->
    <main class="login-main">
      <div class="login-box">
        <router-link to="/" class="mobile-mark">
          <ShieldCheck :size="16" stroke-width="2.2" />
        </router-link>

        <!-- 子应用发起式 SSO 的跳转提示 -->
        <div v-if="ssoClientId" class="sso-banner">
          <template v-if="redirecting">
            <Loader2 :size="14" class="spin" />
            <span>登录成功，正在返回「{{ ssoAppName }}」…</span>
          </template>
          <template v-else>
            <span>登录后将返回「<strong>{{ ssoAppName }}</strong>」</span>
          </template>
        </div>

        <div class="mode-tabs" role="tablist">
          <button
            type="button"
            class="mode-tab"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >登录</button>
          <button
            type="button"
            class="mode-tab"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >注册</button>
        </div>

        <div v-if="redirecting" class="sso-redirect-panel">
          <Loader2 :size="20" class="spin" />
          <span>正在签发一次性票据并跳转，请稍候…</span>
        </div>

        <form v-else class="fields" @submit.prevent="handleSubmit">
          <div class="field">
            <label class="field-label">用户名</label>
            <input v-model="username" class="input" autocomplete="username" placeholder="3-32 个字符" />
          </div>

          <div v-if="mode === 'register'" class="field">
            <label class="field-label">邮箱</label>
            <input v-model="email" class="input" type="email" autocomplete="email" placeholder="you@example.com" />
          </div>

          <div v-if="mode === 'register'" class="field">
            <label class="field-label">手机号 <span class="field-hint">选填，加密存储</span></label>
            <input v-model="phone" class="input" autocomplete="tel" />
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <div class="pwd-wrap">
              <input
                v-model="password"
                class="input"
                :type="showPwd ? 'text' : 'password'"
                :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                :placeholder="mode === 'login' ? '输入密码' : '至少 8 位'"
              />
              <button type="button" class="pwd-eye" tabindex="-1" @click="showPwd = !showPwd">
                <component :is="showPwd ? EyeOff : Eye" :size="15" />
              </button>
            </div>
          </div>

          <div v-if="errorMsg" class="alert alert-error">{{ errorMsg }}</div>

          <button class="btn btn-primary btn-lg btn-block" :disabled="submitting">
            <Loader2 v-if="submitting" :size="15" class="spin" />
            <span>{{ mode === 'login' ? '登录' : '创建账号' }}</span>
          </button>
        </form>

        <button class="back-home" @click="$router.push('/')">
          <ArrowLeft :size="13" />
          <span>返回应用列表</span>
        </button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuth } from '../composables/useAuth';
import { api } from '../api/client';
import { ShieldCheck, Eye, EyeOff, Loader2, ArrowLeft } from 'lucide-vue-next';

const route = useRoute();
const router = useRouter();
const { login, register, isAuthenticated, loading } = useAuth();

// ── 子应用发起式 SSO ─────────────────────────────────────────
// 子应用把浏览器带到这里：/login?client_id=xxx[&redirect_uri=yyy][&state=zzz]
// 登录成功（或本就已登录）后立即签发票据并跳回子应用回调地址。
// redirect_uri 必须与该应用登记的回调同源，由 /api/sso/authorize 校验。
const ssoClientId = computed(() => (typeof route.query.client_id === 'string' ? route.query.client_id : ''));
const ssoRedirectUri = computed(() => (typeof route.query.redirect_uri === 'string' ? route.query.redirect_uri : ''));
const ssoState = computed(() => (typeof route.query.state === 'string' ? route.query.state : ''));
const ssoAppName = ref('');
const redirecting = ref(false);

async function resolveAppName() {
  if (!ssoClientId.value) return;
  try {
    const apps = await api.getPublicApps();
    ssoAppName.value = apps.find(a => a.clientId === ssoClientId.value)?.appName || ssoClientId.value;
  } catch {
    ssoAppName.value = ssoClientId.value;
  }
}

async function authorizeAndReturn() {
  if (!ssoClientId.value || redirecting.value) return;
  redirecting.value = true;
  errorMsg.value = '';
  try {
    const res = await api.ssoAuthorize({
      clientId: ssoClientId.value,
      redirectUri: ssoRedirectUri.value || undefined,
    });
    let target = res.redirectUrl;
    if (ssoState.value) target += (target.includes('?') ? '&' : '?') + 'state=' + encodeURIComponent(ssoState.value);
    window.location.href = target;
  } catch (err) {
    redirecting.value = false;
    errorMsg.value = err.message || '单点登录跳转失败';
  }
}

// 已登录：等本地会话初始化完成后，直接回到发起方，不再展示表单
onMounted(async () => {
  await resolveAppName();
  const started = Date.now();
  while (loading.value && Date.now() - started < 3000) {
    await new Promise(resolve => setTimeout(resolve, 80));
  }
  if (ssoClientId.value && isAuthenticated.value) {
    await authorizeAndReturn();
  }
});

const mode = ref('login');
const username = ref('');
const email = ref('');
const phone = ref('');
const password = ref('');
const showPwd = ref(false);
const submitting = ref(false);
const errorMsg = ref('');

watch(mode, () => { errorMsg.value = ''; });

const handleSubmit = async () => {
  errorMsg.value = '';
  if (!username.value.trim() || !password.value) {
    errorMsg.value = '请输入用户名和密码';
    return;
  }
  if (mode.value === 'register' && password.value.length < 8) {
    errorMsg.value = '密码至少需要 8 个字符';
    return;
  }

  submitting.value = true;
  try {
    if (mode.value === 'login') {
      await login(username.value.trim(), password.value);
    } else {
      await register(username.value.trim(), email.value.trim(), password.value, phone.value.trim() || null);
    }
    window.dispatchEvent(new CustomEvent('badge:auth-changed'));
    // 子应用发起的登录：签发票据并跳回发起方
    if (ssoClientId.value) {
      await authorizeAndReturn();
      return;
    }
    const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    router.push(target.startsWith('/') ? target : '/');
  } catch (err) {
    errorMsg.value = err.message || '操作失败，请稍后再试';
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  animation: page-in 0.24s var(--ease);
}

/* 左侧品牌面：深绿 + 精细竖向刻线，只留一个标识，不说话 */
.login-side {
  width: 40%;
  min-width: 320px;
  flex-shrink: 0;
  position: relative;
  background: linear-gradient(170deg, #11382e 0%, #0c4437 55%, #0a362c 100%);
  overflow: hidden;
}

.side-grid {
  position: absolute;
  inset: 0;
  background:
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.045) 0 1px, transparent 1px 72px),
    radial-gradient(560px 420px at 78% 18%, rgba(255, 255, 255, 0.07), transparent 62%);
}

.side-mark {
  position: absolute;
  top: 26px;
  left: 30px;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  color: #d9efe6;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.side-mark:hover { background: rgba(255, 255, 255, 0.16); }

.side-caption {
  position: absolute;
  left: 32px;
  bottom: 28px;
  font-size: 13.5px;
  letter-spacing: 0.22em;
  color: rgba(217, 239, 230, 0.6);
}

/* 右侧表单 */
.login-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.login-box { width: 100%; max-width: 360px; }

.mobile-mark {
  display: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24px;
}

.mode-tabs {
  display: flex;
  gap: 22px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 24px;
}

.mode-tab {
  position: relative;
  border: none;
  background: transparent;
  padding: 0 2px 11px;
  font-size: 15px;
  font-weight: 650;
  color: var(--ink-2);
  cursor: pointer;
  transition: color 0.15s;
}

.mode-tab:hover { color: var(--ink); }

.mode-tab.active { color: var(--ink); font-weight: 700; }

.mode-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--accent);
}

.fields { display: flex; flex-direction: column; gap: 16px; }

.pwd-wrap { position: relative; }

.pwd-wrap .input { padding-right: 38px; }

.pwd-eye {
  position: absolute;
  right: 5px;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pwd-eye:hover { color: var(--ink); background: var(--surface-2); }

.fields .btn-lg { margin-top: 6px; }

.back-home {
  margin-top: 22px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: var(--ink-2);
  cursor: pointer;
  padding: 4px 2px;
  border-radius: 5px;
  transition: color 0.15s;
}

.back-home:hover { color: var(--ink); }

@media (max-width: 760px) {
  .login-side { display: none; }
  .mobile-mark { display: flex; }
  .login-main { align-items: flex-start; padding-top: 12vh; }
}
</style>

<style scoped>
/* 子应用发起式 SSO */
.sso-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  margin-bottom: 16px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-size: 14px;
}

.sso-banner strong { font-weight: 700; }

.sso-redirect-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 34px 10px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-s);
  color: var(--ink);
  font-size: 14px;
}
</style>
