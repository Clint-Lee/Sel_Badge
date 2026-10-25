<template>
  <div class="page">
    <div class="page-head">
      <h1 class="page-title">接入指南</h1>
      <p class="page-desc">Badge 只负责证明"你是你"，业务数据留在各应用自己的库里。</p>
    </div>

    <section class="card flow-card">
      <h3 class="section-title">单点登录是怎么发生的</h3>
      <div class="flow">
        <div class="flow-step">
          <span class="flow-no num">1</span>
          <div>
            <div class="flow-title">用户访问子应用，未登录</div>
            <div class="flow-desc">子应用把浏览器重定向到 Badge，并带上自己的 clientId。</div>
          </div>
        </div>
        <div class="flow-connector"></div>
        <div class="flow-step">
          <span class="flow-no num">2</span>
          <div>
            <div class="flow-title">在 Badge 登录（只需这一次）</div>
            <div class="flow-desc">Badge 校验账号密码后签发 5 分钟有效的一次性票据，附带回调地址跳回子应用。</div>
          </div>
        </div>
        <div class="flow-connector"></div>
        <div class="flow-step">
          <span class="flow-no num">3</span>
          <div>
            <div class="flow-title">子应用后端拿票据换身份</div>
            <div class="flow-desc">用票据 + Client Secret 调 /api/sso/token（服务端对服务端）。票据核销一次即失效，不可重放。</div>
          </div>
        </div>
        <div class="flow-connector"></div>
        <div class="flow-step">
          <span class="flow-no num">4</span>
          <div>
            <div class="flow-title">子应用建立自己的会话</div>
            <div class="flow-desc">之后的所有请求都由子应用自己的会话承接，不再经过 Badge。</div>
          </div>
        </div>
      </div>
    </section>

    <div class="two-col">
      <section class="card pad">
        <h3 class="section-title">安全边界</h3>
        <ul class="point-list">
          <li>密码使用 BCrypt（工作因子 12）慢哈希，任何人（包括管理员）都无法还原。</li>
          <li>手机号与应用私有配置以 AES-256-GCM 加密落库，密钥来自环境变量。</li>
          <li>换票要求 Client Secret，且只应发生在子应用的服务端；密钥永远不进前端。</li>
          <li>票据绑定应用与回调同源，防开放重定向；一次性核销防重放。</li>
          <li>登录、注册、换票接口均有频率限制；审计记录所有关键动作。</li>
        </ul>
      </section>

      <section class="card pad">
        <h3 class="section-title">应用画像的作用</h3>
        <ul class="point-list">
          <li>每个应用可以给同一个用户不同的昵称、角色与私有配置。</li>
          <li>私有配置以 JSON 形式存放在 Badge，加密存储、按应用授权解密。</li>
          <li>适合存放外观偏好、游戏内称号、工具配置等轻量数据。</li>
          <li>业务核心数据（订单、进度、内容）请始终存在子应用自己的数据库里。</li>
        </ul>
      </section>
    </div>

    <section class="card pad">
      <h3 class="section-title">三步接入一个新站点</h3>
      <ol class="todo-list">
        <li>管理员在「应用」页点<span class="em">注册应用</span>，保存好只显示一次的 Client Secret。</li>
        <li>子应用后端实现票据换身份接口（示例代码见<span class="em">开发者控制台</span>）。</li>
        <li>前端把未登录用户重定向到 Badge 登录页，换票成功后建立本地会话。</li>
      </ol>
    </section>
  </div>
</template>

<script setup>
</script>

<style scoped>
.flow-card { padding: 16px; margin-bottom: 12px; }

.section-title { font-size: 14px; font-weight: 650; margin-bottom: 14px; }

.flow { display: flex; flex-direction: column; }

.flow-step { display: flex; gap: 14px; align-items: flex-start; }

.flow-no {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--accent-tint);
  color: var(--accent-ink);
  font-size: 13px;
  font-weight: 650;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.flow-title { font-size: 13.5px; font-weight: 600; color: var(--ink); }
.flow-desc { font-size: 13px; color: var(--ink-2); margin-top: 2px; }

.flow-connector {
  width: 1px;
  height: 18px;
  background: var(--line);
  margin-left: 13px;
}

.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.pad { padding: 16px; }

.point-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  color: var(--ink-2);
  line-height: 1.6;
}

.point-list li {
  padding-left: 16px;
  position: relative;
}

.point-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.6;
}

.todo-list {
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  color: var(--ink-2);
  line-height: 1.6;
}

.em { color: var(--accent-ink); font-weight: 600; }

@media (max-width: 720px) {
  .two-col { grid-template-columns: 1fr; }
}
</style>
