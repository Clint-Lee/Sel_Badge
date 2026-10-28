# Badge 门户：AI工具与低价库

新增 **AI工具**（`/tools`）和 **低价库**（`/suppliers`），沿用现有 Vue 3 门户、设计变量和 Badge 登录。主页快捷卡片、顶部菜单与手机侧栏均提供入口。

## 功能

- AI工具：8 类场景、名称/描述/标签搜索、精选/最新/名称排序、分页、详情、网站直达、收藏、账号评价、收录申请、管理员审核。初始化 34 个真实工具，介绍为独立整理。
- 低价库：规格选品、商家/标题搜索、来源/形态/周期/库存过滤、异常报价开关、同币种排序、规格关注、链接/关键词黑名单、分页、采集与同步状态、原店跳转。
- 访客收藏与黑名单保存在浏览器；登录后使用账号独立偏好，跨设备同步。访客偏好不会自动混入账号。
- 收录申请只有申请人与管理员可见；通过审核后进入公共目录。每个账号对同一工具只能评价一次。

## 报价来源与边界

直接读取公开来源，运行时不依赖参考站的代码、接口或服务。

| 来源 | 查询方式 | 覆盖 |
| --- | --- | --- |
| PriceAI | 官方 [Price Radar](https://priceai.cc/price-radar-api.md)：读取 `https://data.priceai.cc/latest.json`，再跟随 `snapshot_url` | 每规格公开 Top 5 及公开预设，免费源不提供全量报价或任意查询 |
| PickAI | 公开 `/api/backend/product-types` 和 `/api/backend/quotes` | 每个来源规格价格升序前 50 条，非全量报价 |

随包附带 2026-10-28 读取的 **1,904 条真实初始报价**，保留原链接、来源、币种与时间。后端启动约 20 秒后首次同步，之后每轮结束间隔 15 分钟同步；报价数量随来源变化。

- 数量以本地实际读取的快照为准，不使用上游全库数量冒充本地收录数。
- 明确有货、售罄和未知库存分开显示。最低价排除明确售罄报价，不换算币种；已公开的起购数量一并展示。
- 默认隐藏售罄、零价与标题明确提示“勿拍/定金/补差价/测试商品”等疑似异常报价。这是可关闭的启发式筛选，不能识别所有问题。
- 仅同来源标准商品 ID 归为一组，不模糊合并不同套餐或来源规格。来源标准规格本身也可能宽泛，商品标题、交付与售后仍需到原店核实。
- 采集时间与 Badge 读取时间分别保留。超期或同步失败时显示提示并保留上次成功快照；“重读快照”不触发外部采集。
- PriceAI 在进程内按 `snapshot_id` 缓存不可变快照，版本未变时不重复下载。上游格式改变时视为同步失败。
- 本模块提供信息和跳转，不在 Badge 内处理支付、交付或商家账号凭据。

## 独立存储

新增数据默认存放于 **`backend/data/portal/`**，与身份 H2 数据库分开。采用原子替换及进程内串行写入，适用于当前单实例部署。

| 文件 | 内容 |
| --- | --- |
| `catalog.json` | 已收录工具、收录申请、用户评价 |
| `prices.json` | 最近的公开报价与来源同步状态 |
| `preferences-<SHA256用户名>.json` | 账号收藏、关注与黑名单 |

无运行文件时读取 `backend/src/main/resources/portal/` 种子；损坏文件会明确报错并保留，不会静默覆盖。备份时包含 `backend/data/portal/`。这些文件是门户自身的导航内容，西游与视频工具业务数据仍存各自项目。

## 配置与启动

| 变量 | 默认 | 用途 |
| --- | --- | --- |
| `BADGE_PORTAL_DATA_DIR` | `./data/portal`，相对后端工作目录 | 门户数据位置 |
| `BADGE_PORTAL_SYNC_ENABLED` | `true` | 自动外部同步开关；关闭后仍可浏览已有快照 |
| `VITE_API_BASE_URL` | `/api` | 前端 API 地址，生产推荐同源反向代理 |
| `BADGE_API_TARGET` | `http://127.0.0.1:8080` | Vite 开发代理目标 |

在两个终端中分别启动：

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

本地入口为 `http://127.0.0.1:5173/`、`/tools`、`/suppliers`；API 为 `http://127.0.0.1:8080/api`。2026-10-28 已使用原有数据库启动，确认主页、两个模块与公开 API 均返回 HTTP 200，自动同步实际运行成功。当前后台进程在电脑重启后需重新启动。

生产静态站点将 `/api/` 代理到后端，其余前端路径回退 `index.html`。跨域部署时设置现有 `BADGE_CORS_ORIGINS`。

## API

响应沿用 `ApiResponse`：`code/message/data/timestamp`。

| 方法与路径 | 权限 | 用途 |
| --- | --- | --- |
| `GET /api/portal/tools` | 公开 | 工具目录 |
| `GET /api/portal/tools/{id}/reviews` | 公开 | 工具评价 |
| `GET /api/portal/prices` | 公开 | 只读报价快照与来源状态 |
| `POST /api/portal/tools/{id}/reviews` | 登录 | 评价：`rating/content` |
| `POST /api/portal/submissions` | 登录 | 收录申请，按 IP 每小时最多 10 次 |
| `GET /api/portal/submissions` | 登录 | 本人申请；管理员读取全部 |
| `PUT /api/portal/submissions/{id}/approval` | 管理员 | 通过收录 |
| `PUT /api/portal/submissions/{id}/rejection` | 管理员 | 拒绝收录 |
| `GET/PUT /api/portal/preferences` | 登录 | 当前账号偏好，不接受其他账号标识 |

偏好字段：`toolFavorites/productFavorites/blockedUrls/blockedKeywords`。原有身份、SSO 票据与应用画像接口继续使用。

## 验证

- 前端 `npm run typecheck` 检查类型；`npm test` 验证币种隔离、售罄排除、未知库存、来源规格隔离、黑名单、异常报价与网址安全、排序。构建：`npm run build`。
- 后端 `./mvnw.cmd test`：独立临时门户目录与 H2 内存库，禁用外部采集；覆盖公开/私有权限、账号隔离、磁盘重读、审核发布、重复申请、URL/评分校验及重复评价，不读取真实本地账号。
- 浏览器：桌面及 390px 手机布局，访客/账号收藏、登录返回、评价、收录审核、选品/报价、黑名单持久化、来源状态、手机菜单与首页入口。

## 交接与分支

接手时在 `backend` 分支，缺失的前端源码保留于本地 `main` 分支。本次先恢复前端再实现门户模块，完整成果集成到 `main`；`frontend` 与 `backend` 部署分支分别同步对应源码与公共文档，保留拆分部署约定。

本地预览使用真实持久化库；自动化测试使用独立内存库与临时门户目录，测试申请、评价与偏好不会混入真实运行数据。运行日志、身份库、门户运行文件、依赖及构建输出不提交到 Git。
