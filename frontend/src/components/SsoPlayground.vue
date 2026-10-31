<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">开发者控制台</h1>
        <p class="page-desc">左边走通 SSO 流程，右边拿走接入代码。</p>
      </div>
    </div>

    <div class="console-grid">
      <!-- 左列：交互流程 -->
      <div class="console-flow">
        <section class="card step">
          <div class="step-head">
            <span class="step-no num">1</span>
            <h3 class="step-title">选择要接入的应用</h3>
          </div>
          <div class="app-pick">
            <button
              v-for="app in apps"
              :key="app.clientId"
              class="pick"
              :class="{ active: selectedAppId === app.clientId }"
              @click="selectApp(app.clientId)"
            >
              <span class="pick-name">{{ app.appName }}</span>
              <span class="pick-id mono">{{ app.clientId }}</span>
            </button>
            <div v-if="!apps.length" class="field-hint">暂无应用，请先在「应用」页注册。</div>
          </div>
        </section>

        <section class="card step">
          <div class="step-head">
            <span class="step-no num">2</span>
            <h3 class="step-title">签发一次性票据</h3>
            <span class="step-endpoint mono">POST /api/sso/authorize</span>
          </div>
          <div class="step-body">
            <button class="btn btn-primary btn-sm" :disabled="loadingTicket || !selectedAppId" @click="handleGenerateTicket">
              <Loader2 v-if="loadingTicket" :size="13" class="spin" />
              <span>签发票据</span>
            </button>

            <div v-if="ticketData" class="ticket-result">
              <div class="ticket-row">
                <div class="ticket-info">
                  <span class="mini-label">ticket</span>
                  <code class="mono ticket-val">{{ ticketData.ticket }}</code>
                </div>
                <span class="countdown mono" :class="{ warn: remainSeconds <= 60 }">{{ remainText }}</span>
              </div>
              <div class="ticket-row alt">
                <div class="ticket-info">
                  <span class="mini-label">redirect_url</span>
                  <code class="mono ticket-val small">{{ ticketData.redirectUrl }}</code>
                </div>
                <div class="ticket-ops">
                  <button class="btn btn-ghost btn-sm" @click="copyUrl">
                    <component :is="copiedUrl ? Check : Copy" :size="13" />
                  </button>
                  <a :href="ticketData.redirectUrl" target="_blank" rel="noreferrer" class="btn btn-secondary btn-sm">打开</a>
                </div>
              </div>
            </div>

            <details v-if="ticketRaw" class="raw-details">
              <summary class="mono">响应原文</summary>
              <pre class="json-block">{{ ticketRaw }}</pre>
            </details>
          </div>
        </section>

        <section class="card step">
          <div class="step-head">
            <span class="step-no num">3</span>
            <h3 class="step-title">服务端换票</h3>
            <span class="step-endpoint mono">POST /api/sso/token</span>
          </div>
          <div class="step-body">
            <button class="btn btn-secondary btn-sm" :disabled="loadingExchange || !ticketData" @click="handleExchangeToken">
              <Loader2 v-if="loadingExchange" :size="13" class="spin" />
              <span>模拟服务端换票</span>
            </button>

            <div v-if="exchangeResult" class="exchange-result">
              <div class="exchange-user">
                <span class="avatar">{{ (exchangeResult.user.username || '?').slice(0, 1).toUpperCase() }}</span>
                <div class="exchange-who">
                  <span class="exchange-name">{{ exchangeResult.user.username }}</span>
                  <span class="exchange-role mono">{{ exchangeResult.user.appRole }} · {{ exchangeResult.user.appNickname || '—' }}</span>
                </div>
                <span class="badge badge-green"><span class="dot"></span>已交付</span>
              </div>
              <pre class="json-block">{{ exchangeJson }}</pre>
            </div>
            <p v-else class="field-hint">票据核销一次即失效；换票只能由子应用后端携带 Client Secret 调用。</p>
          </div>
        </section>
      </div>

      <!-- 右列：代码与接口速查（粘性） -->
      <div class="console-code">
        <section class="card code-card">
          <div class="code-head">
            <h3 class="section-title">子应用接入代码</h3>
            <div class="lang-tabs">
              <button
                v-for="lang in ['kotlin', 'node', 'python']"
                :key="lang"
                class="lang-tab"
                :class="{ active: codeLang === lang }"
                @click="codeLang = lang"
              >
                {{ { kotlin: 'Kotlin', node: 'Node.js', python: 'Python' }[lang] }}
              </button>
            </div>
          </div>
          <pre class="code-block">{{ activeCode }}</pre>
          <div class="code-foot">
            <span class="field-hint">演示使用内置开发密钥；生产请改用注册应用时保存的 Secret，只放服务端。</span>
            <button class="btn btn-secondary btn-sm" @click="copyCode">
              <component :is="copied ? Check : Copy" :size="13" />
              <span>{{ copied ? '已复制' : '复制' }}</span>
            </button>
          </div>
        </section>

        <section class="card api-card">
          <h3 class="section-title">接口速查</h3>
          <div class="api-row">
            <code class="mono api-method post">POST</code>
            <div class="api-doc">
              <code class="mono api-path">/api/sso/authorize</code>
              <span class="api-desc">已登录用户签发票据；需 Bearer 令牌，回调地址与登记域名同源。</span>
            </div>
          </div>
          <div class="api-row">
            <code class="mono api-method post">POST</code>
            <div class="api-doc">
              <code class="mono api-path">/api/sso/token</code>
              <span class="api-desc">子应用后端换身份令牌；ticket + clientId + clientSecret，一次性核销。</span>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useAuth } from '../composables/useAuth';
import { api } from '../api/client';
import { Loader2, Copy, Check } from 'lucide-vue-next';

const { isAuthenticated } = useAuth();

const apps = ref([]);
const selectedAppId = ref('');
const ticketData = ref(null);
const ticketRaw = ref('');
const exchangeResult = ref(null);
const loadingTicket = ref(false);
const loadingExchange = ref(false);
const codeLang = ref('kotlin');
const copied = ref(false);
const copiedUrl = ref(false);
const remainSeconds = ref(0);
let timer = null;

const demoSecrets = {
  app_portal_01: 'sec_portal_super_secret_2026',
  app_game_arcade: 'sec_game_arcade_secret_2026',
  app_cloud_service: 'sec_cloud_service_secret_2026',
  app_wiki_knowledge: 'sec_wiki_secret_2026',
};

const remainText = computed(() => {
  const s = Math.max(0, remainSeconds.value);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
});

const exchangeJson = computed(() => {
  if (!exchangeResult.value) return '';
  try {
    return JSON.stringify(exchangeResult.value, null, 2);
  } catch {
    return String(exchangeResult.value);
  }
});

onMounted(async () => {
  try {
    apps.value = await api.getPublicApps();
    selectedAppId.value = apps.value.find((a) => a.appCategory === 'GAME')?.clientId
      || apps.value[0]?.clientId || '';
  } catch {
    apps.value = [];
  }
});

onUnmounted(() => timer && clearInterval(timer));

const selectApp = (clientId) => {
  selectedAppId.value = clientId;
  ticketData.value = null;
  ticketRaw.value = '';
  exchangeResult.value = null;
  if (timer) clearInterval(timer);
};

const handleGenerateTicket = async () => {
  if (!isAuthenticated.value) {
    alert('请先登录 Badge 账号');
    return;
  }
  loadingTicket.value = true;
  exchangeResult.value = null;
  try {
    ticketData.value = await api.ssoAuthorize({ clientId: selectedAppId.value });
    ticketRaw.value = JSON.stringify(ticketData.value, null, 2);
    remainSeconds.value = ticketData.value.expiresInSeconds || 300;
    if (timer) clearInterval(timer);
    timer = setInterval(() => {
      remainSeconds.value -= 1;
      if (remainSeconds.value <= 0) clearInterval(timer);
    }, 1000);
  } catch (err) {
    alert('签发票据失败：' + err.message);
  } finally {
    loadingTicket.value = false;
  }
};

const handleExchangeToken = async () => {
  if (!ticketData.value) return;
  loadingExchange.value = true;
  try {
    exchangeResult.value = await api.ssoTokenExchange({
      ticket: ticketData.value.ticket,
      clientId: selectedAppId.value,
      clientSecret: demoSecrets[selectedAppId.value] || 'sec_demo_secret',
    });
    ticketData.value = null;
    ticketRaw.value = '';
    if (timer) clearInterval(timer);
  } catch (err) {
    alert('换票失败：' + err.message);
  } finally {
    loadingExchange.value = false;
  }
};

const activeCode = computed(() => {
  const cid = selectedAppId.value;
  if (codeLang.value === 'kotlin') {
    return `// Spring Boot 子应用：票据换身份（放在自己的后端）
@PostMapping("/auth/sso-exchange")
fun exchange(@RequestBody body: SsoExchangeRequest): AuthResponse {
    val res = RestClient.create().post()
        .uri("http://localhost:8080/api/sso/token")
        .contentType(MediaType.APPLICATION_JSON)
        .body(mapOf(
            "ticket" to body.ticket,
            "clientId" to "${cid}",
            "clientSecret" to System.getenv("BADGE_CLIENT_SECRET")
        ))
        .retrieve()
        .body(ApiResponse::class.java)
    // 校验成功后，签发本应用自己的会话 Cookie
    return buildLocalSession(res)
}`;
  }
  if (codeLang.value === 'node') {
    return `// Node.js 子应用：票据换身份（放在自己的后端）
app.post('/auth/sso-exchange', async (req, res) => {
  const response = await fetch('http://localhost:8080/api/sso/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      ticket: req.body.ticket,
      clientId: '${cid}',
      clientSecret: process.env.BADGE_CLIENT_SECRET,
    }),
  });
  const { data } = await response.json();
  if (!data) return res.status(401).json({ error: 'invalid ticket' });
  // data.user 即身份，data.accessToken 为 APP_SCOPED 令牌
  issueLocalSession(res, data.user);
});`;
  }
  return `# Python / FastAPI 子应用：票据换身份（放在自己的后端）
@app.post("/auth/sso-exchange")
async def sso_exchange(body: SsoExchangeBody):
    async with httpx.AsyncClient(timeout=8) as client:
        r = await client.post("http://localhost:8080/api/sso/token", json={
            "ticket": body.ticket,
            "clientId": "${cid}",
            "clientSecret": os.environ["BADGE_CLIENT_SECRET"],
        })
    data = r.json().get("data")
    if not data:
        raise HTTPException(401, "invalid ticket")
    issue_local_session(data["user"])`;
});

const copyCode = () => {
  navigator.clipboard.writeText(activeCode.value);
  copied.value = true;
  setTimeout(() => (copied.value = false), 2000);
};

const copyUrl = () => {
  if (!ticketData.value?.redirectUrl) return;
  navigator.clipboard.writeText(ticketData.value.redirectUrl);
  copiedUrl.value = true;
  setTimeout(() => (copiedUrl.value = false), 2000);
};
</script>

<style scoped>
.console-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
  gap: 12px;
  align-items: start;
}

.console-flow {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.console-code {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  position: sticky;
  top: 16px;
}

.step { padding: 18px 20px; }

.step-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 13px;
  flex-wrap: wrap;
}

.step-no {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-size: 14px;
  font-weight: 750;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-title { font-size: 16px; font-weight: 750; color: var(--ink); }

.step-endpoint {
  margin-left: auto;
  font-size: 13.5px;
  color: var(--ink-2);
  background: var(--surface-2);
  padding: 3px 9px;
  border-radius: 6px;
}

.step-body { margin-left: 36px; display: flex; flex-direction: column; gap: 12px; }

.step-body > .btn { align-self: flex-start; }

.app-pick {
  margin-left: 36px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.pick {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  background: var(--surface);
  cursor: pointer;
  transition: all 0.14s var(--ease);
  text-align: left;
}

.pick:hover { border-color: var(--line-strong); background: var(--surface-2); }

.pick.active { border-color: var(--accent); background: var(--accent-tint); }

.pick-name { font-size: 14.5px; font-weight: 700; color: var(--ink); }
.pick-id { font-size: 13.5px; color: var(--ink-3); }

.ticket-result {
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  overflow: hidden;
}

.ticket-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
}

.ticket-row.alt {
  border-top: 1px solid var(--line);
  background: var(--surface-2);
}

.ticket-info { min-width: 0; display: flex; flex-direction: column; gap: 3px; }

.mini-label {
  font-family: var(--font-mono);
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--ink-3);
  text-transform: uppercase;
}

.ticket-val {
  font-size: 14px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ticket-val.small { font-size: 13.5px; }

.ticket-ops { display: flex; gap: 6px; flex-shrink: 0; }

.countdown { font-size: 14px; font-weight: 750; color: var(--accent-ink); flex-shrink: 0; }
.countdown.warn { color: var(--red); }

.raw-details summary {
  cursor: pointer;
  font-size: 13.5px;
  color: var(--ink-2);
  user-select: none;
}

.exchange-result {
  border: 1px solid var(--line);
  border-radius: var(--radius-s);
  overflow: hidden;
}

.exchange-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 13px;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-weight: 750;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.exchange-who {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  min-width: 0;
  flex: 1;
}

.exchange-name { font-size: 14.5px; font-weight: 700; color: var(--ink); }

.exchange-role {
  font-size: 13.5px;
  color: var(--ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.exchange-result .json-block {
  border-top: 1px solid var(--line);
  border-radius: 0;
  max-height: 180px;
}

.code-card { overflow: hidden; }

.code-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 18px;
  flex-wrap: wrap;
}

.lang-tabs { display: flex; gap: 5px; }

.lang-tab {
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface);
  font-size: 13.5px;
  font-weight: 550;
  color: var(--ink-2);
  cursor: pointer;
  transition: all 0.14s;
}

.lang-tab.active { background: var(--accent-tint); border-color: var(--accent); color: var(--accent-ink); font-weight: 700; }

.code-card .code-block { margin: 0 18px; }

.code-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 18px 15px;
  flex-wrap: wrap;
}

.api-card { padding: 16px 18px; }

.api-card .section-title { margin-bottom: 10px; }

.api-row {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

.api-row + .api-row { border-top: 1px solid var(--line); }

.api-method {
  font-size: 13px;
  font-weight: 750;
  letter-spacing: 0.06em;
  padding: 3px 8px;
  border-radius: 5px;
  background: var(--accent-tint);
  color: var(--accent-ink);
  height: fit-content;
  flex-shrink: 0;
}

.api-doc { display: flex; flex-direction: column; gap: 3px; min-width: 0; }

.api-path { font-size: 14px; font-weight: 650; color: var(--ink); }

.api-desc { font-size: 14px; color: var(--ink-2); line-height: 1.55; }

@media (max-width: 1080px) {
  .console-grid { grid-template-columns: 1fr; }
  .console-code { position: static; }
}
</style>
