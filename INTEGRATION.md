# Badge 生态接入与部署说明

三个项目已联通：Badge 是唯一身份源（IdP），万热观象与汉风西游各自通过"使用 Badge 登录"建立**本站会话**。本文是接入架构与上服务器的环境变量清单。

## 一、架构（谁负责什么）

```
浏览器 ──登录──► Badge 门户（前端，/login）      唯一登录界面
                    │ 登录成功，签发 5 分钟一次性票据
                    ▼
        302 回各子应用回调地址（携带 ticket）
                    │
子应用后端 ──ticket + client_secret──► Badge API（/api/sso/token）   服务端对服务端换票
                    │ 返回用户身份
                    ▼
        子应用建立【自己的】会话 Cookie，业务数据存自己的库
```

- 密钥只存在子应用服务端，永不进前端。
- 票据一次性核销、绑定应用与回调同源；换票带状态校验（防登录 CSRF）。
- 各应用会话彼此独立：Badge 只证明"你是你"，不参与日常请求鉴权。

**部署时浏览器要访问的是"门户地址"，后端换票走"API 地址"——生产是两个域名**（如 `www.xxx.com` 与 `auth.xxx.com`），开发对应 5173 与 8080。

## 二、环境变量清单（上线前必改）

### Badge（后端，mysql profile 缺任一项会拒绝启动）
| 变量 | 说明 |
|---|---|
| `BADGE_JWT_SECRET` | 门户会话 JWT 签名密钥（随机长串） |
| `BADGE_CRYPTO_MASTER_KEY` | 手机号/画像 AES-256-GCM 主密钥 |
| `BADGE_CORS_ORIGINS` | 允许的门户前端来源，逗号分隔 |

### 万热观象（HotPot，`.env`）
| 变量 | 开发默认 | 生产应配 |
|---|---|---|
| `BADGE_URL` | `http://localhost:8080` | Badge API 地址 |
| `BADGE_PORTAL_URL` | `http://localhost:5173` | Badge 门户地址 |
| `BADGE_CLIENT_ID` | `app_hotpot_video` | 同 Badge 库注册值 |
| `BADGE_CLIENT_SECRET` | `sec_hotpot_video_secret_2026` | **改真实密钥** |
| `HOTPOT_PUBLIC_URL` | `http://localhost:4000` | 本站对外地址（决定回调） |

### 汉风西游（XiYou，启动环境变量）
| 变量 | 开发默认 | 生产应配 |
|---|---|---|
| `BADGE_URL` / `BADGE_PORTAL_URL` | 同上 | 同上 |
| `BADGE_CLIENT_ID` | `app_xiyou_game` | 同 Badge 库注册值 |
| `BADGE_CLIENT_SECRET` | `sec_xiyou_game_secret_2026` | **改真实密钥** |
| `PORT` | `5173`（生产建议换端口/由反代转发） | — |
| `XIYOU_PUBLIC_URL` | `http://localhost:$PORT` | 本站对外地址 |
| `XIYOU_SESSION_SECRET` | 缺省自动生成于 `saves/.session-secret` | 建议显式配置 |

**Badge 侧同步动作**：应用注册表里的回调地址必须改成生产地址（管理端"应用画像"或直接改库），并更新各子应用 client_secret。

## 三、各项目接入了什么

### 万热观象（Media_Tools）
- `lib/badge-sso.js`：换票客户端 + 本地账号映射（`badge_<Badge用户ID>_<用户名>`，与本地注册账号命名空间隔离，不会顶号；首次登录自动建号，历史与每日额度连续）。
- 路由：`GET /auth/badge`（发起，带 state Cookie）→ `GET /auth/badge/callback`（校验状态 → 换票 → 建 scrypt 会话 → 回首页）。
- 前端：登录弹窗底部"使用 Badge 统一账号登录"入口。访客模式不受影响；本地账号登录 `/api/auth/login` 保留。

### 汉风西游（XiYouOnline）
- `server.js`：静态服务之上新增 `/auth/badge`、`/auth/badge/callback`、`/auth/logout`、`GET/POST /api/save`、`GET /api/me`；会话为 HMAC 签名 Cookie；云存档按玩家存 `saves/<玩家>/auto_progress.json`（原子写、256KB 上限、结构校验）。
- `js/core/cloud.js`（在 `save.js` 之后加载）：运行时包装 `SaveManager.saveGameFullState`——本地自动存档成功后 3 秒防抖推送云端；启动时云端较新则覆盖本地，游戏读档自然生效。**未改动任何游戏本体逻辑**，网络异常一律静默降级为纯本地。
- 右下角角标：未登录显示"使用 Badge 登录"，登录后显示"已同步：昵称 · 时间"，点击可退出。

## 四、已实测的完整链路（2026-10-25）

1. 万热观象：登录弹窗 → Badge → 自动签发票据 → 回调换票 → `/api/auth/me` 返回映射账号（昵称/额度正常）。✔
2. 汉风西游：角标 → Badge → 回调 → `/api/me` 返回玩家；本地自动存档 → 云端 `saves/1_admin/auto_progress.json` 落盘 → 回读一致。✔
3. 回调 state 校验：伪造 state 被正确拒绝。✔

## 五、上服务器前的检查单

1. Badge `mysql` profile + 三个环境变量；两个子应用的 `BADGE_CLIENT_SECRET` 换新并在 Badge 库同步。
2. Badge CORS 白名单加入门户生产域名；两个子应用注册的 redirect_uri/homepage 改为生产地址。
3. 西游 `saves/` 目录纳入持久化/备份；`.session-secret` 不要提交 Git。
4. HTTPS：三家都用 `secure` Cookie 时必须全程 TLS；反代把 `X-Forwarded-For` 传给 Badge（审计与限流按真实 IP）。
5. 首次登录 Badge 后立即改掉种子管理员密码（`admin / Admin@123456`）。

## 六、AI工具与低价库门户（2026-10-28）

- 页面：`/tools`、`/suppliers`；导航复用现有 Badge 登录，子应用 SSO 仍使用本文原有接口。
- 本地运行：后端从 `backend/` 启动（8080），前端从 `frontend/` 执行 `npm ci`、`npm run dev`（5173）。本地首页、两个模块及公开数据接口已实测返回 HTTP 200。
- 前端默认请求同源 `/api`，开发由 Vite 代理至 `http://127.0.0.1:8080`。生产 Web 服务须代理 `/api/` 至后端，并为前端路由配置 `index.html` 回退；跨域时配置 `VITE_API_BASE_URL` 及 `BADGE_CORS_ORIGINS`。
- 门户目录、评价、申请、个人收藏及黑名单独立存于 `backend/data/portal/`；连同身份库一并备份。自定义目录使用 `BADGE_PORTAL_DATA_DIR`。
- `BADGE_PORTAL_SYNC_ENABLED` 默认 `true`，后端约 20 秒后首次读取公开来源，每轮结束间隔 15 分钟同步。来源失败保留成功快照；范围见 [PORTAL.md](PORTAL.md)。
- 代码以 `main` 完整项目为基准，`frontend`、`backend` 各自保持仅包含对应部署代码。
