# Badge 门户：AI工具（资源导航）与低价库

门户包含两个模块：**AI工具**（`/tools`，资源导航目录）与**低价库**（`/suppliers`，规格选品 + 商家报价比较）。两者共用 Badge 登录体系、设计变量与 `/api/portal/**` 接口，数据独立存放于 `backend/data/portal/`，不进入身份 H2 库。

## 一、AI工具（资源导航）

`https://127.0.0.1:5173/tools`，对应接口前缀 `/api/portal/`。

### 页面结构

| 区域 | 内容 |
| --- | --- |
| 英雄区 | 分类数、站点数、评价数、收录日期，登录后可直接「申请收录」 |
| 左侧导航 | 全部网站 / 今日精选 / 我的收藏 / 最近 30 天收录，21 个分类（可展开全部），每项带实时计数 |
| 今日精选 | 编辑精选 + 赞助置顶，按 seed 稳定打散，可「换一批」与展开 |
| 分类资源目录 | 按分类分栏展示各分类访问量靠前的条目，点分类标题进入完整列表 |
| 列表结果 | 搜索、排序（推荐 / 访问最多 / 最新 / 名称）、分页，24 条/页 |
| 站点卡片 | 字母图标、域名、简介、标签（点击即搜索）、访问量、赠金标记、收藏、直达 |
| 站点详情 | 简介、标签、访问与今日点击、赠金金额与领取说明、联系方式（登录可见）、社区评价与打分 |
| 收录申请 | 三种类型：网站收录 / 商家自荐 / QQ群收录；标签、联系方式、中转站赠金；管理员审核后入目录 |

### 数据范围

- 初始目录 **208 个站点 / 21 个分类**，来自公开站点目录接口，连同来源方的访问量、精选标记、赠金信息一并整理；原有 34 个内置工具按场景映射进分类，去重后合并。
- 站点计数（访问、今日点击）单独存放于 `site-counters.json`，点击只累加计数，不改写目录内容。
- 联系方式存于 `contacts.json`，仅在登录后按站点单独读取；目录接口只下发 `hasContact` 标记。
- 收录申请与评价按账号隔离：申请只有本人与管理员可见，一个账号对同一站点只能评价一次。

## 二、低价库

`https://127.0.0.1:5173/suppliers`。

### 页面结构

| 区域 | 内容 |
| --- | --- |
| 标题区 | 已收录商品链接数、同规格保留原始币种的说明 |
| 工具条 | 规格搜索、筛选面板（来源 / 形态 / 周期 / 库存）、异常低价开关、清空筛选、重读快照、采集详情、我的黑名单、申请 API 对接 |
| 来源状态条 | 采集站数量、同步周期、上次同步时间、失败站数量、采集状态入口 |
| 分类页签 | 全部分类 + 各平台分类（带规格数）+ 我的关注 |
| 选品台 | 标准规格卡片：分类、规格名、当前快照同币种最低价、报价条数、来源、累计查询次数；分页式「加载更多规格」 |
| 商家报价 | 返回选品台面包屑、规格关注、本页同币种最低、关键词 / 来源 / 库存 / 排序筛选、报价表（商家、标题、报价、库存、来源采集时间、前往店铺、拉黑链接）、分页 |
| 采集详情 | 各来源状态、标准规格数、采集记录数、上次成功同步、最近尝试、只读 API 地址 |

### 报价来源

| 来源 | 查询方式 | 覆盖 |
| --- | --- | --- |
| PriceAI | 官方 Price Radar 快照 `https://data.priceai.cc/latest.json` 取规格清单，再逐规格走公开分页接口 `/api/products/{slug}/offers?offset=N` | 每个标准规格的全部分页报价（每页 30 条） |
| PickAI | 公开 `/api/backend/product-types` 与 `/api/backend/quotes` | 每个标准规格的全部分页报价 |

随包附带 **8,400 余条真实报价 / 69 个标准规格**（`backend/src/main/resources/portal/prices.json`）。后端启动约 20 秒后首次同步，之后每 15 分钟一轮；同步失败保留该来源上次成功快照并标记过期。

### 分组与最低价规则（与参考站对齐）

1. **跨来源合并**：分组键只用「标准规格名」（忽略空格、斜杠、标点）。同一规格在 PickAI 与 PriceAI 下的报价归到同一张卡，卡片标注两个来源。名字本身已区分形态，所以「ChatGPT Plus」与「ChatGPT Plus 代充值」仍是两个规格。
2. **最低价候选**：排除售罄、零价，以及标题明显不是本标准规格的报价（`muse / token 成品号 / 资格号 / 半价资格 / 补差价 / 定金 / 勿拍 / 引流 …`）。
3. **不做中位数阈值**：参考站规则文案写着「同规格同币种至少 4 商户中位数 30% 以下隐藏」，但按其原文复算会把真实的一手低价货源一起误杀（ChatGPT Plus 会被抬到 ¥18.9，而参考站实际展示 ¥8），因此本实现只用「售罄 + 零价 + 规格不符」三条硬规则。
4. **不换算币种**：不同币种分别给最低价，不做汇率折算。
5. 分页结果只属于当前规格；页码越界自动回落到最后一页。

对齐校验（同一时间窗的公开数据）：

| 规格 | 参考站最低价 | 本项目最低价 |
| --- | --- | --- |
| ChatGPT Go（双来源合并） | ¥28 | ¥28 |
| ChatGPT Team / Business | ¥5.14 | ¥5.14 |
| ChatGPT Plus | ¥8 | ¥8.8 |

最低价随采集时点浮动，因此上表用于验证「合并方式与过滤规则一致」，不是固定数值承诺。

- 明确有货、售罄、未知库存分开显示；最低价排除明确售罄报价。
- 关闭「过滤异常低价」后显示全部报价（含疑似占位价），便于核对差异。
- 采集时间与 Badge 读取时间分别保留；「重读快照」只读本地，不触发外部采集。
- 本模块提供信息与跳转，不在 Badge 内处理支付、交付或商家账号凭据。

## 三、独立存储

默认写入 **`backend/data/portal/`**，与身份 H2 库分开；采用原子替换及进程内串行写入，适用于当前单实例部署。

| 文件 | 内容 |
| --- | --- |
| `catalog.json` | 站点目录、分类、收录申请、站点评价 |
| `contacts.json` | 站点联系方式（仅登录后按站点读取） |
| `site-counters.json` | 站点访问与今日点击计数 |
| `prices.json` | 最近公开报价快照与来源同步状态 |
| `price-queries.json` | 规格查询累计次数 |
| `preferences-<SHA256用户名>.json` | 账号收藏、关注与黑名单 |

无运行文件时读取 `backend/src/main/resources/portal/` 种子；损坏文件会明确报错并保留，不静默覆盖。备份时包含 `backend/data/portal/`。

## 四、配置与启动

| 变量 | 默认 | 用途 |
| --- | --- | --- |
| `BADGE_PORTAL_DATA_DIR` | `./data/portal`，相对后端工作目录 | 门户数据位置 |
| `BADGE_PORTAL_SYNC_ENABLED` | `true` | 自动外部同步开关；关闭后仍可浏览已有快照 |
| `VITE_API_BASE_URL` | `/api` | 前端 API 地址，生产推荐同源反向代理 |
| `BADGE_API_TARGET` | `http://127.0.0.1:8080` | Vite 开发代理目标 |

```powershell
# 终端一：从后端目录启动，沿用现有持久化身份库
cd backend
./mvnw.cmd spring-boot:run
```

```powershell
# 终端二：从前端目录启动
cd frontend
npm ci
npm run dev
```

本地入口：`http://127.0.0.1:5173/`、`/tools`、`/suppliers`；API：`http://127.0.0.1:8080/api`。

## 五、API

响应沿用 `ApiResponse`：`code/message/data/timestamp`。

### 资源导航

| 方法与路径 | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/portal/categories` | 公开 | 分类及各分类站点数 |
| `GET /api/portal/sites` | 公开 | 站点目录 |
| `GET /api/portal/sites/{id}` | 公开 | 站点详情 |
| `GET /api/portal/stats` | 公开 | 站点 / 分类 / 评价 / 申请计数 |
| `GET /api/portal/sites/{id}/reviews` | 公开 | 站点评价 |
| `POST /api/portal/sites/{id}/visit` | 公开 | 访问计数（只累加） |
| `GET /api/portal/sites/{id}/contact` | 登录 | 站点联系方式 |
| `POST /api/portal/sites/{id}/reviews` | 登录 | 评价：`rating/content` |
| `POST /api/portal/submissions` | 登录 | 收录申请，按 IP 每小时最多 10 次 |
| `GET /api/portal/submissions` | 登录 | 本人申请；管理员读取全部 |
| `PUT /api/portal/submissions/{id}/approval` | 管理员 | 通过收录 |
| `PUT /api/portal/submissions/{id}/rejection` | 管理员 | 拒绝收录 |
| `GET/PUT /api/portal/preferences` | 登录 | 当前账号偏好，不接受其他账号标识 |

### 低价库

| 方法与路径 | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/portal/prices` | 公开 | 完整报价快照与来源状态 |
| `GET /api/portal/prices/catalog?quality=on\|off` | 公开 | 选品台：规格分组 + 可比最低价 + 来源状态 + 查询计数 |
| `GET /api/portal/prices/groups/{groupId}/offers` | 公开 | 商家报价分页：`page/quality/source/stock/keyword` |
| `POST /api/portal/prices/catalog/query` | 公开 | 记录一次规格查询（只累加计数） |
| `POST /api/portal/prices/catalog/api-plan` | 公开 | 登记 API 对接意向（不出站） |

偏好字段：`toolFavorites/productFavorites/blockedUrls/blockedKeywords`。原有身份、SSO 票据与应用画像接口继续使用。

## 六、种子数据重建

```powershell
# 目录：从公开站点目录接口读取并合并内置工具清单
node scripts/build-directory-seed.mjs

# 报价：PickAI 全部分页 + PriceAI 逐规格全部分页（PriceAI 有限流，脚本会等待）
node scripts/build-price-seed.mjs --max-pages=80
```

脚本只写 `backend/src/main/resources/portal/`，不触碰运行数据；重建后可按需复制到 `backend/data/portal/`。

- PriceAI 公开分页接口限流约 30 请求 / 35 秒，全量约 700 次请求，需要十几分钟。网络中断或持续 429 时该规格会回落到上一轮数据，不会清空。
- `scripts/fetch-priceai.mjs` 单独做 PriceAI 全量抓取，带断点续传缓存（`.cache-priceai/`），中断后重跑会跳过已完成规格。
- `scripts/restore-price-seed.mjs` 可把运行库 `backend/data/portal/prices.json` 回填为种子，用于离线保留已抓到数据。

## 七、验证

- 前端：`npm run typecheck`（类型）、`npm test`（10 项：币种隔离、售罄排除、未知库存、来源规格隔离、黑名单、异常报价与网址安全、排序、赠金换算、站点图标兜底、换一批稳定性、置顶过期）、`npm run build`。
- 后端：`./mvnw.cmd test`（6 项：公开/私有权限、访问计数、偏好隔离与落盘、申请审核与联系方式隔离、链接/标签/商家联系方式/赠金校验、**跨来源合并 + 卡片最低价等于可报价最小值 + 仅明确有库存过滤 + 页码越界回落**），使用独立临时门户目录与 H2 内存库，禁用外部采集。
- 浏览器：`scripts/verify-portal.mjs` 与 `scripts/verify-interactions.mjs` 覆盖桌面 1440px 与手机 390px 的截图、收藏、收录弹窗、筛选、报价分页与控制台报错检查。
