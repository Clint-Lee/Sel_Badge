# 生产部署复核：Badge / MediaTools / XiYouOnline

检查日期：2026-10-31（北京时间）。

结论：三个公网入口可访问，SSO 发起地址已使用正确的新端口；同时发现需要立即处理的私有文件暴露、同源写入误拦截，以及生产密钥、备份和后端发布配置问题。现有交付手册不能作为这些问题已解决的证明。

## 检查范围与证据边界

- 阅读根目录与前端目录的 `PRODUCTION_DEPLOYMENT_HANDOFF.md`，两份内容当前相同。
- 对照 Badge 的部署脚本、认证代码，以及本机 MediaTools、XiYouOnline 源码。
- 对文档列明的自有服务器执行少量匿名 HTTP 检查：页面、公开应用列表、SSO 发起、响应头，以及无会话空请求。
- 敏感文件只发送 `HEAD`，未下载密钥或 Git 配置正文；未登录账号、读取玩家存档、提交有效业务写入或改动服务器配置。
- 未通过 SSH 核对服务器生效配置。因此，下文明确区分“线上已确认”与“仓库配置待核对”。本地已有未提交变更保持原样。

## P0：西游将私有文件暴露为静态资源（线上已确认响应头）

匿名 `HEAD` 检查结果：

| 路径（端口 8280） | 状态 | Content-Type |
| --- | --- | --- |
| `/saves/.session-secret` | 200 | `application/octet-stream` |
| `/.git/config` | 200 | `application/octet-stream` |
| `/server.js` | 200 | `application/javascript; charset=utf-8` |

本机 `XiYouOnline/server.js:268` 起直接从整个仓库根目录提供文件；登录门禁仅保护 `index.html`。同文件 58–69 行将 `saves/.session-secret` 用作游戏会话签名密钥。结合文件服务实现，以上结果说明私有路径未被访问控制隔离，存在会话伪造及玩家数据泄露、篡改风险。`scripts/nginx/xiyou.conf:16` 的后缀规则无法保护这些目录。

建议立即：

1. 在代理与应用两层禁止访问 `saves`、`.git` 等私有目录；静态服务改为明确的公开资源目录或允许列表，保留登录门禁。
2. 修复后轮换游戏会话签名密钥，使可能已经泄露的旧会话失效；保留玩家存档。
3. 检查历史访问日志中的私有路径请求，再判断是否有进一步泄露。关闭入口不能替代已泄露密钥的轮换。

验收：相应匿名 `HEAD/GET` 均为 403/404，公开游戏资源与登录流程保持可用。

## P1：正常来源的写请求被误判为跨站（线上已确认）

| 无会话、空 JSON 请求 | Origin | 实际响应 |
| --- | --- | --- |
| `POST :8280/api/save` | `http://39.97.50.218:8280` | 403，`拒绝跨站写入` |
| `POST :8180/api/auth/login` | `http://39.97.50.218:8180` | 403，`拒绝跨站修改请求` |

空请求不带账号或有效存档。游戏同源校验先于会话校验执行，因此能确认正常浏览器来源已经被错误拦截；这会阻断真实用户的云存档提交，媒体本地账号登录及其他修改接口也受影响。

原因：`scripts/nginx/xiyou.conf:27`、`scripts/nginx/mediatools.conf:27` 将 Host 设置为 `$host`，丢掉外部端口；应用分别在 `XiYouOnline/server.js:152`、`Media_Tools/lib/accounts.js:227` 比较 `Origin.host` 与 `req.headers.host`。

建议代理保留含端口的原始 Host（例如 `$http_host`），应用按明确的公网 Origin 和可信代理配置校验。不要以移除同源校验作为修复。Nginx 对原始 Host 的转发方式见[官方文档](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_set_header)。

验收：同源无会话存档返回未登录状态，真实登录后能够写入并回读；非允许来源仍被拒绝。

## P1：部署文件使用开发默认密钥，并已进入 Git（仓库已确认，线上有效值待核对）

| 文件 | 行 | 问题 |
| --- | --- | --- |
| `scripts/badge-backend.service` | 9–10 | JWT 签名密钥、AES 主密钥均等于代码中的开发默认值 |
| `scripts/media-tools.env` | 8 | SSO 客户端密钥等于开发默认值 |
| `scripts/xiyou.env` | 6 | SSO 客户端密钥等于开发默认值 |
| `scripts/webhook-config.json` | 4 | 配置中包含字面量 Webhook 密钥 |

以上文件均被 Git 跟踪。本报告不重复任何密钥值。如果线上沿用该 systemd 文件，已知 JWT 密钥可被用于伪造门户身份；AES 及 SSO 密钥同样缺少生产隔离。

此外，service 未指定生产 profile，`application.yml:8` 默认为 `dev`；只有 `mysql` profile 去掉了密钥默认值。采用 H2 不应因此继续沿用开发安全配置，应提供独立生产 H2 配置，强制密钥必填并禁用开发控制台。

建议从 Git 迁出密钥，使用权限受限的运行配置，逐项核对并轮换。**AES 主密钥不能直接替换：必须先用旧钥解密、再用新钥重新加密手机号、应用密钥与画像等既有密文，并验证备份恢复。** JWT、SSO、Webhook 的轮换也需与对应服务同步。

## P1：公开登录与 SSO 仍走明文 HTTP（线上已确认）

三个公开 HTTP 页面未重定向到 HTTPS；两条 SSO 发起响应仍指向 HTTP 登录与回调。实际响应的状态 Cookie 缺少 `Secure`，媒体访客 Cookie 同样如此。

密码、门户 Bearer Token、SSO 票据及子应用会话在公网 HTTP 链路上缺少传输加密；HttpOnly、SameSite、一次性票据不能替代 HTTPS。参见 [MDN Cookie 安全说明](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies)。

另一个边界：同一 IP 的不同端口不隔离 Cookie，不能把当前三个端口视为独立的 Cookie 信任边界，见 [RFC 6265 第 8.5 节](https://www.rfc-editor.org/rfc/rfc6265#section-8.5)。

建议启用 HTTPS并统一更新门户、子应用公网地址及数据库回调；具备域名时可按子域名拆分应用。代理后的应用需正确识别 HTTPS，生产会话设置 `Secure`。没有证据认定服务器不存在其他 HTTPS 入口，问题在于当前公布的登录路径仍允许明文使用。

## P1：备份命令不满足可靠恢复要求（仓库配置已确认）

- `scripts/badge.conf:3` 在服务运行时直接 `cp` H2 主文件，且失败被 `|| true` 忽略，每次还覆盖相同目标文件。H2 明确不保证普通文件复制的在线备份一致性；应使用 `BACKUP TO`、逻辑导出或停库备份，见 [H2 官方备份说明](https://h2database.com/html/tutorial.html#backup)。
- `scripts/xiyou.conf:3` 使用 `cp -rn`，已有同名存档备份不会随新进度更新，不是可靠的增量备份方案；失败也被忽略。
- 手册备份清单漏掉 Badge `backend/data/portal/`、MediaTools `.runtime/data/accounts.json` 等账号与历史数据，以及完成恢复必需的运行密钥/配置；仅列媒体 `.env` 不足以恢复账号和会话数据。
- `/var/backups` 与应用仍在同一台服务器，不能覆盖系统盘丢失的场景。

建议建立有时间戳、保留策略、校验和、异机副本及恢复演练的备份；备份失败需可见，不能继续声称“零数据丢失”。需要在服务器上核对是否已有其他有效备份任务。

## P1：Badge 自动发布只构建前端，却重启固定后端 Jar（仓库配置已确认）

`scripts/badge.conf`：

- 第 4 行 `BUILD_CMD` 仅运行前端 `npm ci` 和 `npm run build`。
- 第 6 行重启 `badge-backend`。
- `scripts/badge-backend.service:12` 运行固定的 `backend/target/badge-auth-platform-1.0.0.jar`。

按这份配置，后续提交 Kotlin 源码不会重新生成运行 Jar，会出现“Git/前端已更新，后端仍是旧代码”。这不是断言当前 Jar 一定过期；需要核对服务器实际 deploy.sh 是否另有后端构建逻辑。

建议明确加入后端测试、打包、版本化发布、健康检查与回滚流程；轻量服务器也可接收在 CI 构建好的产物。验收时对照运行版本与目标提交，而非只检查 HTTP 200。

## P2：媒体访客指纹与 IP 限额的可信边界有问题（本机源码已确认）

`Media_Tools/lib/guest-access.js:61` 直接采用客户端提交的 `x-fingerprint`，有值时不再混入 IP/UA。更换头值会形成新的访客身份；这不能保证“同一物理设备每日严格 3 次”。访客计数当前位于内存 Map，重启后也会消失。

源码未设置 Express `trust proxy`，但计数与认证限流使用 `req.ip`。在 Nginx 代理后通常得到回环地址，会把多名真实用户聚合在相同 IP 配额/限流桶中。另一方面，直接信任未经约束的客户端转发头也不安全。

建议仅信任明确的反向代理，确定真实 IP 取值；采用服务端签发的访客标识、服务器侧风控和持久化计数。客户端指纹可用作辅助信号，不能当作可靠认证。以上为源码结论，未对生产下载接口做消耗配额或绕过试验。

## P2：服务权限、监听范围与资源约束需要核对

- `scripts/badge-backend.service:7` 以 root 身份运行公开业务。建议独立低权限用户，并限制可写目录。
- PM2 虽给西游配置 `HOST=127.0.0.1`，`XiYouOnline/server.js:318` 实际只调用 `server.listen(port)`，没有使用 HOST。文档声称的回环监听未在应用代码中落实；需要在服务器用 `ss` 核对并修复监听，同时核对云防火墙。
- `-Xmx256m` 只限制 Java 堆，不等于整个 Java 进程最多 256/300 MB，更不能保证整机无 OOM。当前 1.6 GB 内存还承载 Chromium、下载和构建，需要限制并发、设定服务资源边界，并监测峰值。未执行生产压力测试，不据此宣称已经发生 OOM。

## P2：应用导航包含未完成生产配置的入口（线上已确认）

`/api/apps/public` 返回六个应用，其中以下三个仍是 ACTIVE 状态的 localhost 入口：

- `app_game_arcade` → `http://localhost:8081`
- `app_cloud_service` → `http://localhost:8082`
- `app_wiki_knowledge` → `http://localhost:8083`

公网用户点击将访问其自己的电脑，不是服务器。应配置真实生产地址，或停用未部署的应用。线上三个主要应用（门户、视频工具、汉风西游）的首页及回调已是 8080/8180/8280。

## P2：交接文档包含事实错误和过度验收结论

- 手册第 26、122、170 行提到 TailwindCSS、`AuthController.java`、MyBatis-Plus；实际是无该 UI 框架的 Vue 门户、Kotlin Controller、Spring Data JPA。
- 第 172 行写票据有效期 60 秒；当前代码 `Services.kt:461` 与返回字段是 5 分钟/300 秒。
- 第 202、205 行的“通过”证据仍含旧 101/102 回调，本次线上发起地址已是 8180/8280，不能把旧记录当成当前失败。
- 第 238 行运维端口命令仍只匹配 80/100/101/102，漏掉主端口。
- 个人审计接口目前返回最近记录列表，没有请求分页参数；“支持分页”的表述不符合该接口实现。
- “绝对安全”“JVM 限堆杜绝崩溃”“零数据丢失”等表述超出了当前配置和测试能够证明的范围。

建议文档记录实际生效配置、提交/产物版本、测试时间与结果；区分计划、已配置与已实际验收。

## 已确认正常的部分

- 三个主要公网入口能够响应；西游匿名访问首页按预期返回 302 门禁。
- 视频和游戏的 SSO 发起链接使用正确的 8180/8280 回调，并带 state Cookie。
- Badge 公共应用接口未返回应用 client secret。
- H2 嵌入式库与各应用独立文件存储适合当前单实例、小数据量阶段；当前优先级是访问控制、生产配置与备份可靠性。

## 建议处理顺序

1. 封住西游私有文件访问并轮换游戏会话密钥。
2. 修复代理 Host/Origin，验证真实登录、云存档写入与读回。
3. 核对生产密钥，按迁移计划轮换；启用 HTTPS 与生产 Cookie 属性。
4. 修复备份和后端发布链路，做一次可回滚发布与隔离恢复演练。
5. 收紧服务权限、修复配额取 IP/身份设计，清理导航与文档。
