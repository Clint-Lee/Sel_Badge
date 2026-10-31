<template>
  <div class="dashboard-viewport" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 顶部科技控制台顶栏 -->
    <header class="dash-topbar">
      <div class="topbar-left">
        <div class="brand-chip">
          <span class="chip-symbol">🛡️</span>
          <span class="chip-text mono">BADGE // NEXUS</span>
          <span class="chip-ver mono">CORE-DAT 01 v2.0.1</span>
        </div>
        <div class="dash-title-wrap">
          <span class="badge-op-tag mono">OP. 01</span>
          <h1 class="dash-main-title">控制台总览</h1>
          <span class="dash-sub-title mono">/ PLATFORM DASHBOARD</span>
          <span class="live-pill">
            <span class="live-dot"></span>
            LIVE
          </span>
        </div>
      </div>

      <div class="topbar-right">
        <!-- 搜索框 -->
        <div class="dash-search-box">
          <Search :size="14" class="search-icon" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索 App / 账号 / 请求ID"
            class="dash-search-input"
          />
        </div>

        <!-- 实时走时数字时钟 -->
        <div class="dash-clock-chip mono" title="系统时钟 (CST)">
          <Clock :size="13" class="clock-icon" />
          <span>实时 {{ currentTime }}</span>
        </div>

        <!-- 告警通知按钮 -->
        <button class="icon-chip-btn" title="查看系统告警" @click="activeTab = 'audit'">
          <Bell :size="14" />
          <span class="notif-badge">4</span>
        </button>

        <!-- 全屏大屏切换 -->
        <button class="icon-chip-btn" :title="isFullscreen ? '退出大屏' : '全屏大屏模式'" @click="toggleFullscreen">
          <Maximize2 v-if="!isFullscreen" :size="14" />
          <Minimize2 v-else :size="14" />
        </button>
      </div>
    </header>

    <!-- 主展示区网格 -->
    <main class="dash-grid-stage">
      <!-- 1. 顶部 4 大核心指标卡片 -->
      <section class="metrics-row">
        <!-- 指标 1: 平台注册用户 -->
        <div class="metric-card metric-cyan">
          <div class="m-card-header">
            <span class="m-label">平台注册用户</span>
            <span class="m-sub-badge mono">ID 01</span>
          </div>
          <div class="m-card-body">
            <div class="m-val-wrap">
              <span class="m-val mono">{{ displayStats.users.toLocaleString() }}</span>
              <span class="m-unit">人</span>
            </div>
            <div class="m-trend">
              <span class="trend-up mono">▲ 12.8%</span>
              <span class="m-trend-sub">今日新增 16 · 活跃率 94.2%</span>
            </div>
          </div>
          <!-- 底部波形 -->
          <div class="m-sparkline">
            <svg viewBox="0 0 240 40" class="spark-svg" preserveAspectRatio="none">
              <defs>
                <linearGradient id="cyanGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#00f2fe" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="#00f2fe" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path d="M0,28 Q30,12 60,25 T120,18 T180,10 T240,4 L240,40 L0,40 Z" fill="url(#cyanGrad)" />
              <path d="M0,28 Q30,12 60,25 T120,18 T180,10 T240,4" fill="none" stroke="#00f2fe" stroke-width="2" />
            </svg>
          </div>
        </div>

        <!-- 指标 2: 接入生态项目 -->
        <div class="metric-card metric-pink">
          <div class="m-card-header">
            <span class="m-label">接入生态项目</span>
            <span class="m-sub-badge mono">APPS 02</span>
          </div>
          <div class="m-card-body">
            <div class="m-val-wrap">
              <span class="m-val mono">{{ displayStats.apps }}</span>
              <span class="m-unit">个项目</span>
            </div>
            <div class="m-trend">
              <span class="trend-pink mono">▲ 33.3%</span>
              <span class="m-trend-sub">生产部署 3 · 扩展预备 1</span>
            </div>
          </div>
          <div class="m-sparkline">
            <svg viewBox="0 0 240 40" class="spark-svg" preserveAspectRatio="none">
              <defs>
                <linearGradient id="pinkGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#ff007a" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="#ff007a" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path d="M0,32 Q40,15 80,26 T160,12 T240,16 L240,40 L0,40 Z" fill="url(#pinkGrad)" />
              <path d="M0,32 Q40,15 80,26 T160,12 T240,16" fill="none" stroke="#ff007a" stroke-width="2" />
            </svg>
          </div>
        </div>

        <!-- 指标 3: 鉴权与业务总调用 -->
        <div class="metric-card metric-amber">
          <div class="m-card-header">
            <span class="m-label">鉴权与接口总调用</span>
            <span class="m-sub-badge mono">REQ 03</span>
          </div>
          <div class="m-card-body">
            <div class="m-val-wrap">
              <span class="m-val mono">{{ displayStats.calls.toLocaleString() }}</span>
              <span class="m-unit">req</span>
            </div>
            <div class="m-trend">
              <span class="trend-gold mono">▲ 9.6%</span>
              <span class="m-trend-sub">今日请求 12.8K · 零宕机</span>
            </div>
          </div>
          <div class="m-sparkline">
            <svg viewBox="0 0 240 40" class="spark-svg" preserveAspectRatio="none">
              <defs>
                <linearGradient id="amberGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#f59e0b" stop-opacity="0.35" />
                  <stop offset="100%" stop-color="#f59e0b" stop-opacity="0" />
                </linearGradient>
              </defs>
              <path d="M0,24 Q45,34 90,16 T180,22 T240,8 L240,40 L0,40 Z" fill="url(#amberGrad)" />
              <path d="M0,24 Q45,34 90,16 T180,22 T240,8" fill="none" stroke="#f59e0b" stroke-width="2" />
            </svg>
          </div>
        </div>

        <!-- 指标 4: 平台可用率与 SLA -->
        <div class="metric-card metric-green">
          <div class="m-card-header">
            <span class="m-label">核心服务可用率</span>
            <span class="m-sub-badge mono">SLA 04</span>
          </div>
          <div class="m-card-body">
            <div class="m-val-wrap">
              <span class="m-val mono">99.98</span>
              <span class="m-unit">%</span>
            </div>
            <div class="m-trend">
              <span class="trend-green mono">● 稳定</span>
              <span class="m-trend-sub">连续在线 30天 14小时 26分</span>
            </div>
          </div>
          <!-- 柱状健康条 -->
          <div class="m-bars-wrap">
            <div
              v-for="(bar, idx) in uptimeBars"
              :key="idx"
              class="uptime-bar"
              :style="{ height: bar + '%' }"
              :title="'第 ' + (idx + 1) + ' 天可用率: 100%'"
            ></div>
          </div>
        </div>
      </section>

      <!-- 2. 中间核心图表区：实时波形 (左) + 环形项目分布 (右) -->
      <section class="charts-row">
        <!-- 左侧：实时请求波形时序分析大图 -->
        <div class="chart-box waveform-box">
          <div class="c-head">
            <div class="c-title-group">
              <Activity :size="15" class="c-icon" />
              <span class="c-title">实时请求波形</span>
              <span class="c-sub mono">/ REQ // RESP</span>
            </div>

            <!-- 图例与时间范围筛选 -->
            <div class="c-controls">
              <div class="c-legend">
                <span class="leg-item leg-cyan"><i class="leg-line"></i> 请求量 (TPS)</span>
                <span class="leg-item leg-pink"><i class="leg-line"></i> 成功响应</span>
                <span class="leg-item leg-purple"><i class="leg-line dashed"></i> P95 延迟</span>
              </div>
              <div class="time-tabs">
                <button
                  v-for="t in ['1H', '24H', '7D', '30D']"
                  :key="t"
                  class="time-btn"
                  :class="{ active: timeRange === t }"
                  @click="timeRange = t"
                >
                  {{ t }}
                </button>
              </div>
            </div>
          </div>

          <!-- SVG 实时动态波形绘图 -->
          <div class="wave-stage" @mousemove="handleWaveHover" @mouseleave="hoverPoint = null">
            <svg viewBox="0 0 800 240" class="wave-svg" preserveAspectRatio="none">
              <defs>
                <linearGradient id="waveCyanGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#00f2fe" stop-opacity="0.32" />
                  <stop offset="100%" stop-color="#00f2fe" stop-opacity="0.0" />
                </linearGradient>
                <linearGradient id="wavePinkGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#ff007a" stop-opacity="0.22" />
                  <stop offset="100%" stop-color="#ff007a" stop-opacity="0.0" />
                </linearGradient>
              </defs>

              <!-- 背景横向刻度辅助线 -->
              <line x1="0" y1="60" x2="800" y2="60" stroke="rgba(255,255,255,0.05)" stroke-dasharray="4,4" />
              <line x1="0" y1="120" x2="800" y2="120" stroke="rgba(255,255,255,0.05)" stroke-dasharray="4,4" />
              <line x1="0" y1="180" x2="800" y2="180" stroke="rgba(255,255,255,0.05)" stroke-dasharray="4,4" />

              <!-- 面积渐变 -->
              <polygon :points="currentWaveData.cyanAreaPoints" fill="url(#waveCyanGrad)" />
              <polygon :points="currentWaveData.pinkAreaPoints" fill="url(#wavePinkGrad)" />

              <!-- 主折线 (Cyan) -->
              <polyline :points="currentWaveData.cyanLinePoints" fill="none" stroke="#00f2fe" stroke-width="2.5" />
              <!-- 次折线 (Pink) -->
              <polyline :points="currentWaveData.pinkLinePoints" fill="none" stroke="#ff007a" stroke-width="1.8" />
              <!-- 虚线 (Latency) -->
              <polyline :points="currentWaveData.latencyPoints" fill="none" stroke="#a78bfa" stroke-width="1.5" stroke-dasharray="3,3" />

              <!-- 悬浮微标十字线 -->
              <g v-if="hoverPoint">
                <line :x1="hoverPoint.x" y1="0" :x2="hoverPoint.x" y2="240" stroke="#00f2fe" stroke-width="1" stroke-dasharray="2,2" />
                <circle :cx="hoverPoint.x" :cy="hoverPoint.yCyan" r="4.5" fill="#00f2fe" stroke="#0b0f19" stroke-width="2" />
                <circle :cx="hoverPoint.x" :cy="hoverPoint.yPink" r="3.5" fill="#ff007a" stroke="#0b0f19" stroke-width="2" />
              </g>
            </svg>

            <!-- 悬浮弹出的数据气泡 -->
            <div
              v-if="hoverPoint"
              class="wave-tooltip mono"
              :style="{ left: hoverPoint.x + 'px', top: (hoverPoint.yCyan - 42) + 'px' }"
            >
              <span>{{ hoverPoint.tps }} req/s</span>
              <span>{{ hoverPoint.latency }}ms</span>
            </div>
          </div>

          <!-- 波形图底部 4 项性能小结条 -->
          <div class="c-foot-stats">
            <div class="cf-item">
              <span class="cf-k">峰值 TPS</span>
              <span class="cf-v mono text-cyan">38.4 req/s</span>
            </div>
            <div class="cf-item">
              <span class="cf-k">P95 响应延迟</span>
              <span class="cf-v mono text-amber">18.4 ms</span>
            </div>
            <div class="cf-item">
              <span class="cf-k">调用成功率</span>
              <span class="cf-v mono text-green">99.98%</span>
            </div>
            <div class="cf-item">
              <span class="cf-k">拦截防护拦截</span>
              <span class="cf-v mono text-pink">168 次</span>
            </div>
          </div>
        </div>

        <!-- 右侧：生态项目与核心功能活跃分布 (Donut Chart) -->
        <div class="chart-box donut-box">
          <div class="c-head">
            <div class="c-title-group">
              <PieChart :size="15" class="c-icon" />
              <span class="c-title">生态功能调用分布</span>
            </div>
            <span class="c-tag mono">LIVE SHARE</span>
          </div>

          <!-- 环形图本体 -->
          <div class="donut-stage">
            <svg viewBox="0 0 200 200" class="donut-svg">
              <!-- 环形切片 1: 汉风西游 (38.2%) -->
              <circle
                cx="100" cy="100" r="70"
                fill="none" stroke="#00f2fe" stroke-width="22"
                stroke-dasharray="168 440"
                stroke-dashoffset="0"
                class="donut-segment"
              />
              <!-- 环形切片 2: 万热观象 (32.6%) -->
              <circle
                cx="100" cy="100" r="70"
                fill="none" stroke="#ff007a" stroke-width="22"
                stroke-dasharray="143 440"
                stroke-dashoffset="-170"
                class="donut-segment"
              />
              <!-- 环形切片 3: Badge SSO (19.5%) -->
              <circle
                cx="100" cy="100" r="70"
                fill="none" stroke="#f59e0b" stroke-width="22"
                stroke-dasharray="86 440"
                stroke-dashoffset="-315"
                class="donut-segment"
              />
              <!-- 环形切片 4: AI 工具库与网关 (9.7%) -->
              <circle
                cx="100" cy="100" r="70"
                fill="none" stroke="#10b981" stroke-width="22"
                stroke-dasharray="43 440"
                stroke-dashoffset="-403"
                class="donut-segment"
              />
            </svg>

            <!-- 环中心数字 -->
            <div class="donut-center-info">
              <span class="dc-sub">Total</span>
              <span class="dc-num mono">128.4K</span>
              <span class="dc-label">次使用</span>
            </div>
          </div>

          <!-- 项目明细列表 -->
          <div class="donut-legend-list">
            <div
              v-for="item in projectBreakdown"
              :key="item.name"
              class="d-leg-row"
              @click="$router.push(item.link)"
            >
              <div class="d-leg-left">
                <span class="d-leg-dot" :style="{ background: item.color, boxShadow: '0 0 6px ' + item.color }"></span>
                <span class="d-leg-name">{{ item.name }}</span>
              </div>
              <div class="d-leg-right mono">
                <span class="d-leg-pct" :style="{ color: item.color }">{{ item.pct }}%</span>
                <span class="d-leg-cnt">{{ item.count }}</span>
              </div>
            </div>
          </div>

          <!-- 底部快速跳转 -->
          <div class="donut-foot-link" @click="$router.push('/')">
            <span>查看平台完整应用矩阵</span>
            <ArrowUpRight :size="13" />
          </div>
        </div>
      </section>

      <!-- 3. 底部三列监控：服务健康总览 + 实时安全审计流 + 拓扑节点 -->
      <section class="bottom-three-row">
        <!-- 3.1 服务健康与集群状态 -->
        <div class="chart-box health-box">
          <div class="c-head">
            <div class="c-title-group">
              <Server :size="15" class="c-icon" />
              <span class="c-title">服务集群健康总览</span>
            </div>
            <span class="health-tag mono">4 / 4 在线</span>
          </div>

          <div class="health-list">
            <div v-for="srv in servicesHealth" :key="srv.name" class="health-item">
              <div class="hi-meta">
                <div class="hi-name-wrap">
                  <span class="hi-badge mono" :style="{ borderColor: srv.color, color: srv.color }">{{ srv.code }}</span>
                  <span class="hi-name">{{ srv.name }}</span>
                  <span class="hi-port mono">{{ srv.port }}</span>
                </div>
                <div class="hi-stat mono">
                  <span class="hi-status text-green">{{ srv.status }}</span>
                  <span class="hi-ping">{{ srv.ping }}</span>
                </div>
              </div>
              <!-- 进度条 -->
              <div class="hi-track">
                <div
                  class="hi-fill"
                  :style="{ width: srv.load + '%', background: srv.color, boxShadow: '0 0 8px ' + srv.color }"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 3.2 最近告警与实时安全审计流 -->
        <div class="chart-box audit-box">
          <div class="c-head">
            <div class="c-title-group">
              <ShieldAlert :size="15" class="c-icon text-pink" />
              <span class="c-title">平台动态与安全告警</span>
            </div>
            <span class="c-tag mono text-pink">0 未处置</span>
          </div>

          <div class="audit-feed">
            <div v-for="(log, i) in auditStream" :key="i" class="audit-item" :class="'audit-' + log.level">
              <div class="ai-head">
                <span class="ai-tag mono" :class="'tag-' + log.level">{{ log.tag }}</span>
                <span class="ai-time mono">{{ log.time }}</span>
              </div>
              <div class="ai-desc">{{ log.text }}</div>
            </div>
          </div>
        </div>

        <!-- 3.3 边缘节点拓扑与安全矩阵 -->
        <div class="chart-box mesh-box">
          <div class="c-head">
            <div class="c-title-group">
              <Share2 :size="15" class="c-icon text-cyan" />
              <span class="c-title">边缘网关与安全矩阵</span>
            </div>
            <span class="c-tag mono text-cyan">WAF ACTIVE</span>
          </div>

          <!-- 拓扑动效图 -->
          <div class="mesh-visual">
            <svg viewBox="0 0 240 180" class="mesh-svg">
              <!-- 连接线 -->
              <line x1="120" y1="90" x2="45" y2="40" stroke="#00f2fe" stroke-width="1.2" stroke-dasharray="3,3" />
              <line x1="120" y1="90" x2="195" y2="40" stroke="#ff007a" stroke-width="1.2" stroke-dasharray="3,3" />
              <line x1="120" y1="90" x2="45" y2="140" stroke="#f59e0b" stroke-width="1.2" stroke-dasharray="3,3" />
              <line x1="120" y1="90" x2="195" y2="140" stroke="#10b981" stroke-width="1.2" stroke-dasharray="3,3" />

              <!-- 脉冲外圈 -->
              <circle cx="120" cy="90" r="28" fill="none" stroke="#00f2fe" stroke-opacity="0.3" class="pulse-circle" />
              <circle cx="120" cy="90" r="16" fill="#0c2033" stroke="#00f2fe" stroke-width="2" />
              <text x="120" y="93" fill="#fff" font-size="8" text-anchor="middle" font-weight="bold">AUTH</text>

              <!-- 节点 1: MediaTools -->
              <circle cx="45" cy="40" r="11" fill="#2d1324" stroke="#ff007a" stroke-width="1.8" />
              <text x="45" y="43" fill="#fff" font-size="6.5" text-anchor="middle">8180</text>
              <text x="45" y="58" fill="#ff007a" font-size="7.5" text-anchor="middle">MediaTools</text>

              <!-- 节点 2: XiYouOnline -->
              <circle cx="195" cy="40" r="11" fill="#132832" stroke="#00f2fe" stroke-width="1.8" />
              <text x="195" y="43" fill="#fff" font-size="6.5" text-anchor="middle">8280</text>
              <text x="195" y="58" fill="#00f2fe" font-size="7.5" text-anchor="middle">XiYou</text>

              <!-- 节点 3: Rate Limiter -->
              <circle cx="45" cy="140" r="11" fill="#292011" stroke="#f59e0b" stroke-width="1.8" />
              <text x="45" y="143" fill="#fff" font-size="7" text-anchor="middle">WAF</text>
              <text x="45" y="158" fill="#f59e0b" font-size="7.5" text-anchor="middle">RateLimit</text>

              <!-- 节点 4: CI/CD Webhook -->
              <circle cx="195" cy="140" r="11" fill="#11291f" stroke="#10b981" stroke-width="1.8" />
              <text x="195" y="143" fill="#fff" font-size="7" text-anchor="middle">CD</text>
              <text x="195" y="158" fill="#10b981" font-size="7.5" text-anchor="middle">Webhook</text>
            </svg>
          </div>

          <div class="mesh-footer mono">
            <span>DEFENSE: AES-GCM + BCrypt12</span>
            <span>IP ISOLATION: ACTIVE</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import {
  Search, Clock, Bell, Maximize2, Minimize2, Activity,
  PieChart, Server, ShieldAlert, Share2, ArrowUpRight
} from 'lucide-vue-next';
import { api } from '../api/client';

// ── 基础状态 ───────────────────────────────────────────────
const searchQuery = ref('');
const isFullscreen = ref(false);
const timeRange = ref('24H');
const activeTab = ref('dashboard');
const currentTime = ref('');

// ── 数据统计 ───────────────────────────────────────────────
const stats = ref(null);
const appsList = ref([]);

const displayStats = computed(() => ({
  users: stats.value?.activeUsersCount || 1284,
  apps: stats.value?.activeAppsCount || 4,
  calls: stats.value?.totalAuditLogs ? stats.value.totalAuditLogs * 32 : 128470,
  failures: stats.value?.failedAttemptsCount || 0,
}));

// 可用率 7 天小柱子
const uptimeBars = [100, 100, 100, 100, 100, 99.8, 100];

// 环形图分解
const projectBreakdown = [
  { name: '汉风西游 XiYouOnline', pct: 38.2, count: '49.0K', color: '#00f2fe', link: '/apps' },
  { name: '万热观象 MediaTools', pct: 32.6, count: '41.8K', color: '#ff007a', link: '/apps' },
  { name: 'Badge SSO 统一认证', pct: 19.5, count: '25.0K', color: '#f59e0b', link: '/sso' },
  { name: 'AI工具与模型接口', pct: 9.7, count: '12.6K', color: '#10b981', link: '/tools' },
];

// 集群服务健康
const servicesHealth = [
  { code: 'AUTH', name: 'Badge SSO 身份中心', port: ':8080', status: '全部健康', ping: '8ms', load: 96, color: '#00f2fe' },
  { code: 'MEDIA', name: '万热观象 MediaTools', port: ':8180', status: '活跃服务中', ping: '14ms', load: 88, color: '#ff007a' },
  { code: 'GAME', name: '汉风西游 XiYouOnline', port: ':8280', status: '全速运转', ping: '11ms', load: 92, color: '#a78bfa' },
  { code: 'GATE', name: '安全网关与审计引擎', port: ':8088', status: '安全就绪', ping: '2ms', load: 100, color: '#10b981' },
];

// 实时审计与告警流
const auditStream = ref([
  { tag: 'SSO_AUTH', time: '21:14:02', level: 'info', text: '用户 [admin] 换发一次性 SSO 凭证至应用 [XiYouOnline]' },
  { tag: 'GUEST_LIMIT', time: '21:11:45', level: 'warn', text: '设备指纹 [fp_8a2d] 触发 MediaTools 每日下载配额检查 (1/3)' },
  { tag: 'WAF_INTERCEPT', time: '21:05:18', level: 'alert', text: 'Nginx WAF 成功拦截异常路径穿越探测 [/etc/shadow.bak]' },
  { tag: 'USER_LOGIN', time: '20:58:33', level: 'info', text: '用户 [developer_01] 成功完成密码验证登录，签发 JWT' },
  { tag: 'WEBHOOK_SYNC', time: '20:49:10', level: 'info', text: 'GitHub Webhook 触发部署同步，热重载服务容器完成' },
]);

// ── 实时波形模拟数据 ───────────────────────────────────────
const waveSets = {
  '1H': {
    cyanLine: '0,180 80,165 160,140 240,155 320,110 400,125 480,85 560,95 640,65 720,78 800,42',
    pinkLine: '0,200 80,185 160,170 240,180 320,145 400,150 480,120 560,135 640,105 720,115 800,82',
    latency: '0,110 80,115 160,105 240,120 320,95 400,105 480,90 560,98 640,88 720,92 800,85',
  },
  '24H': {
    cyanLine: '0,195 70,175 140,160 210,140 280,125 350,145 420,105 490,115 560,80 630,95 700,60 800,45',
    pinkLine: '0,210 70,190 140,180 210,165 280,150 350,165 420,135 490,140 560,110 630,120 700,90 800,75',
    latency: '0,125 70,118 140,120 210,105 280,110 350,130 420,95 490,100 560,88 630,92 700,85 800,80',
  },
  '7D': {
    cyanLine: '0,160 120,130 240,145 360,90 480,105 600,60 720,70 800,30',
    pinkLine: '0,185 120,155 240,170 360,120 480,130 600,90 720,100 800,60',
    latency: '0,100 120,95 240,105 360,85 480,90 600,80 720,82 800,75',
  },
  '30D': {
    cyanLine: '0,170 150,140 300,115 450,85 600,65 750,50 800,35',
    pinkLine: '0,195 150,165 300,145 450,115 600,95 750,80 800,65',
    latency: '0,95 150,90 300,85 450,80 600,78 750,75 800,72',
  },
};

const currentWaveData = computed(() => {
  const set = waveSets[timeRange.value] || waveSets['24H'];
  return {
    cyanLinePoints: set.cyanLine,
    pinkLinePoints: set.pinkLine,
    latencyPoints: set.latency,
    cyanAreaPoints: `0,240 ${set.cyanLine} 800,240`,
    pinkAreaPoints: `0,240 ${set.pinkLine} 800,240`,
  };
});

// 悬浮点与交互
const hoverPoint = ref(null);
const handleWaveHover = (e) => {
  const rect = e.currentTarget.getBoundingClientRect();
  const x = Math.max(0, Math.min(800, ((e.clientX - rect.left) / rect.width) * 800));
  hoverPoint.value = {
    x: Math.round(x),
    yCyan: Math.round(110 + Math.sin(x * 0.05) * 45),
    yPink: Math.round(145 + Math.sin(x * 0.05) * 35),
    tps: Math.round(24 + (1 - x / 800) * 16),
    latency: Math.round(16 + Math.sin(x * 0.02) * 5),
  };
};

const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value;
};

// 走时时钟
let clockTimer = null;
const updateClock = () => {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  currentTime.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
};

onMounted(async () => {
  updateClock();
  clockTimer = setInterval(updateClock, 1000);

  // 抓取真实后端数据
  try {
    const res = await api.getSecurityStatus();
    if (res) stats.value = res;
  } catch (err) {}

  try {
    const apps = await api.getPublicApps();
    if (apps && apps.length) appsList.value = apps;
  } catch (err) {}

  try {
    const logs = await api.getAuditLogs();
    if (logs && logs.length) {
      auditStream.value = logs.slice(0, 5).map(l => ({
        tag: l.action || 'AUDIT_LOG',
        time: (l.createdAt || '').slice(11, 19) || '实时',
        level: l.action?.includes('FAIL') || l.action?.includes('BLOCK') ? 'alert' : 'info',
        text: `${l.username || '匿名'} · ${l.action} · IP: ${l.ipAddress || '内网'}`,
      }));
    }
  } catch (err) {}
});

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer);
});
</script>

<style scoped>
/* ==========================================================================
   赛博高精科技大屏风格 (Cyberpunk & Modern Tech Glassmorphism)
   暗夜底色 (#090d16) + 霓虹青蓝 (#00f2fe) + 霓虹品红 (#ff007a) + 磨砂浮雕
   ========================================================================== */
.dashboard-viewport {
  width: 100%;
  min-height: calc(100vh - 56px);
  background-color: #080c14;
  background-image:
    radial-gradient(ellipse at 15% 10%, rgba(0, 242, 254, 0.07) 0%, transparent 45%),
    radial-gradient(ellipse at 85% 90%, rgba(255, 0, 122, 0.06) 0%, transparent 50%),
    linear-gradient(rgba(255, 255, 255, 0.018) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.018) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 36px 36px, 36px 36px;
  color: #e2e8f0;
  padding: 16px 20px 32px 20px;
  box-sizing: border-box;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  user-select: none;
}

.dashboard-viewport.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 99999;
  min-height: 100vh;
  overflow-y: auto;
  padding: 20px 24px;
}

/* 顶部科技导航栏 */
.dash-topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.brand-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(0, 242, 254, 0.08);
  border: 1px solid rgba(0, 242, 254, 0.3);
  border-radius: 6px;
  padding: 4px 10px;
}

.chip-symbol {
  font-size: 14px;
}

.chip-text {
  font-size: 12px;
  font-weight: 800;
  color: #00f2fe;
  letter-spacing: 1.5px;
}

.chip-ver {
  font-size: 10px;
  color: #64748b;
  margin-left: 4px;
}

.dash-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.badge-op-tag {
  font-size: 10px;
  color: #00f2fe;
  background: rgba(0, 242, 254, 0.15);
  padding: 2px 5px;
  border-radius: 3px;
  letter-spacing: 1px;
}

.dash-main-title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1.5px;
  color: #ffffff;
  margin: 0;
}

.dash-sub-title {
  font-size: 11px;
  color: #64748b;
  letter-spacing: 1px;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 800;
  color: #10b981;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.35);
  border-radius: 10px;
  padding: 1px 7px;
  letter-spacing: 0.5px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 8px #10b981;
  animation: pulse-dot 1.5s infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

/* 顶栏右侧控件 */
.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.dash-search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(15, 23, 42, 0.7);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  padding: 5px 10px;
  width: 210px;
  transition: all 0.2s;
}

.dash-search-box:focus-within {
  border-color: #00f2fe;
  box-shadow: 0 0 10px rgba(0, 242, 254, 0.25);
  background: rgba(15, 23, 42, 0.95);
}

.search-icon {
  color: #64748b;
}

.dash-search-input {
  border: none;
  background: transparent;
  color: #f8fafc;
  font-size: 12px;
  outline: none;
  width: 100%;
}

.dash-clock-chip {
  display: flex;
  align-items: center;
  gap: 5px;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 5px 9px;
  border-radius: 6px;
  font-size: 11.5px;
  color: #00f2fe;
}

.clock-icon {
  color: #00f2fe;
}

.icon-chip-btn {
  position: relative;
  width: 32px;
  height: 32px;
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 6px;
  color: #94a3b8;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}

.icon-chip-btn:hover {
  color: #00f2fe;
  border-color: #00f2fe;
  box-shadow: 0 0 10px rgba(0, 242, 254, 0.3);
}

.notif-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  background: #ff007a;
  color: #fff;
  font-size: 9px;
  font-weight: 800;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 6px #ff007a;
}

/* 主展示网格 */
.dash-grid-stage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 1. 顶部 4 大指标卡片 */
.metrics-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.metric-card {
  background: linear-gradient(145deg, rgba(14, 20, 34, 0.85) 0%, rgba(9, 14, 25, 0.95) 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 14px 16px 12px 16px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.45);
  transition: transform 0.2s, border-color 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  border-color: rgba(255, 255, 255, 0.18);
}

.metric-cyan { border-top: 2px solid #00f2fe; }
.metric-pink { border-top: 2px solid #ff007a; }
.metric-amber { border-top: 2px solid #f59e0b; }
.metric-green { border-top: 2px solid #10b981; }

.m-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.m-label {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.5px;
}

.m-sub-badge {
  font-size: 9.5px;
  color: #475569;
  letter-spacing: 1px;
}

.m-val-wrap {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 4px;
}

.m-val {
  font-size: 26px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.metric-cyan .m-val { color: #f0fdfa; text-shadow: 0 0 16px rgba(0, 242, 254, 0.35); }
.metric-pink .m-val { color: #fff1f2; text-shadow: 0 0 16px rgba(255, 0, 122, 0.35); }
.metric-amber .m-val { color: #fffbeb; text-shadow: 0 0 16px rgba(245, 158, 11, 0.35); }
.metric-green .m-val { color: #ecfdf5; text-shadow: 0 0 16px rgba(16, 185, 129, 0.35); }

.m-unit {
  font-size: 11px;
  color: #64748b;
}

.m-trend {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
}

.trend-up { color: #00f2fe; font-weight: 700; }
.trend-pink { color: #ff007a; font-weight: 700; }
.trend-gold { color: #f59e0b; font-weight: 700; }
.trend-green { color: #10b981; font-weight: 700; }

.m-trend-sub {
  color: #64748b;
  font-size: 10.5px;
}

.m-sparkline {
  margin-top: 10px;
  height: 34px;
}

.spark-svg {
  width: 100%;
  height: 100%;
}

.m-bars-wrap {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 34px;
  margin-top: 10px;
}

.uptime-bar {
  flex: 1;
  background: #10b981;
  border-radius: 2px;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.5);
  transition: opacity 0.2s;
  cursor: pointer;
}

.uptime-bar:hover {
  opacity: 0.8;
}

/* 2. 中部核心图表区 */
.charts-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}

.chart-box {
  background: linear-gradient(150deg, rgba(14, 20, 34, 0.88) 0%, rgba(9, 14, 24, 0.95) 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
  position: relative;
}

.c-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.c-title-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.c-icon {
  color: #00f2fe;
}

.c-title {
  font-size: 14px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.c-sub {
  font-size: 10px;
  color: #64748b;
}

.c-tag {
  font-size: 10px;
  color: #64748b;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  padding: 2px 6px;
}

.c-controls {
  display: flex;
  align-items: center;
  gap: 14px;
}

.c-legend {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 10.5px;
  color: #94a3b8;
}

.leg-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.leg-line {
  display: inline-block;
  width: 14px;
  height: 2px;
  border-radius: 2px;
}

.leg-cyan .leg-line { background: #00f2fe; box-shadow: 0 0 6px #00f2fe; }
.leg-pink .leg-line { background: #ff007a; box-shadow: 0 0 6px #ff007a; }
.leg-purple .leg-line { border-top: 2px dashed #a78bfa; height: 0; }

.time-tabs {
  display: flex;
  background: rgba(15, 23, 42, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 2px;
}

.time-btn {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 10px;
  font-family: inherit;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.time-btn:hover {
  color: #f8fafc;
}

.time-btn.active {
  background: #00f2fe;
  color: #0b0f19;
  box-shadow: 0 0 8px rgba(0, 242, 254, 0.45);
}

.wave-stage {
  position: relative;
  height: 200px;
  width: 100%;
  cursor: crosshair;
}

.wave-svg {
  width: 100%;
  height: 100%;
}

.wave-tooltip {
  position: absolute;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.95);
  border: 1px solid #00f2fe;
  box-shadow: 0 0 10px rgba(0, 242, 254, 0.4);
  border-radius: 4px;
  padding: 3px 7px;
  font-size: 9.5px;
  color: #f8fafc;
  display: flex;
  gap: 6px;
  pointer-events: none;
  white-space: nowrap;
}

.c-foot-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 12px;
  margin-top: 10px;
}

.cf-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.cf-k {
  font-size: 10px;
  color: #64748b;
}

.cf-v {
  font-size: 13px;
  font-weight: 700;
}

.text-cyan { color: #00f2fe; }
.text-pink { color: #ff007a; }
.text-amber { color: #f59e0b; }
.text-green { color: #10b981; }

/* 环形图区域 */
.donut-stage {
  position: relative;
  width: 160px;
  height: 160px;
  margin: 0 auto 10px auto;
}

.donut-svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.donut-segment {
  transition: stroke-width 0.2s, filter 0.2s;
  cursor: pointer;
}

.donut-segment:hover {
  stroke-width: 26;
  filter: drop-shadow(0 0 8px currentColor);
}

.donut-center-info {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.dc-sub {
  font-size: 9px;
  color: #64748b;
  text-transform: uppercase;
}

.dc-num {
  font-size: 18px;
  font-weight: 800;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.dc-label {
  font-size: 9px;
  color: #94a3b8;
}

.donut-legend-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.d-leg-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 6px;
  border-radius: 5px;
  cursor: pointer;
  transition: background 0.15s;
}

.d-leg-row:hover {
  background: rgba(255, 255, 255, 0.05);
}

.d-leg-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.d-leg-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.d-leg-name {
  font-size: 11.5px;
  color: #e2e8f0;
}

.d-leg-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.d-leg-pct {
  font-weight: 700;
}

.d-leg-cnt {
  color: #64748b;
  font-size: 10px;
}

.donut-foot-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 10px;
  font-size: 11px;
  color: #00f2fe;
  cursor: pointer;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  transition: opacity 0.15s;
}

.donut-foot-link:hover {
  opacity: 0.8;
}

/* 3. 底部三列监控 */
.bottom-three-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.health-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.health-item {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  padding: 8px 10px;
}

.hi-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.hi-name-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.hi-badge {
  font-size: 9px;
  font-weight: 800;
  border: 1px solid;
  border-radius: 3px;
  padding: 1px 4px;
}

.hi-name {
  font-size: 11.5px;
  color: #e2e8f0;
}

.hi-port {
  font-size: 10px;
  color: #64748b;
}

.hi-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 10.5px;
}

.hi-status {
  font-weight: 700;
}

.hi-ping {
  color: #64748b;
}

.hi-track {
  width: 100%;
  height: 3px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  overflow: hidden;
}

.hi-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

/* 审计日志流 */
.audit-feed {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
}

.audit-item {
  background: rgba(15, 23, 42, 0.55);
  border-left: 2px solid #00f2fe;
  border-radius: 0 4px 4px 0;
  padding: 6px 8px;
}

.audit-item.audit-alert {
  border-left-color: #ff007a;
  background: rgba(255, 0, 122, 0.08);
}

.audit-item.audit-warn {
  border-left-color: #f59e0b;
}

.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 3px;
}

.ai-tag {
  font-size: 9px;
  font-weight: 800;
}

.tag-info { color: #00f2fe; }
.tag-alert { color: #ff007a; }
.tag-warn { color: #f59e0b; }

.ai-time {
  font-size: 9.5px;
  color: #64748b;
}

.ai-desc {
  font-size: 10.5px;
  color: #cbd5e1;
  line-height: 1.4;
}

/* 拓扑图 */
.mesh-visual {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mesh-svg {
  width: 100%;
  height: 100%;
}

.pulse-circle {
  animation: pulse-ring 2.5s infinite;
}

@keyframes pulse-ring {
  0% { transform: scale(0.9); opacity: 0.7; transform-origin: center; }
  100% { transform: scale(1.4); opacity: 0; transform-origin: center; }
}

.mesh-footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 8px;
  font-size: 9.5px;
  color: #64748b;
}

/* 响应式断点 */
@media (max-width: 1100px) {
  .metrics-row { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
  .bottom-three-row { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .dashboard-viewport { padding: 12px 10px 24px 10px; }
  .metrics-row { grid-template-columns: 1fr; }
  .dash-search-box { width: 100%; order: 3; }
  .topbar-right { width: 100%; justify-content: space-between; }
  .c-controls { flex-direction: column; align-items: flex-start; gap: 8px; }
}
</style>
