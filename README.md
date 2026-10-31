# BADGE AUTH · 全局统一认证与身份中心

> 基于 **Kotlin + Java 17 + Spring Boot 3**（H2 嵌入式库）后端与 **Vue 3 + Vite** 前端的全栈单点登录 (SSO) 与多应用身份枢纽。界面为暖纸底色的控制台式设计，支持桌面与 H5。

> 📖 **接手任何设备前先读 [ECOSYSTEM.md](ECOSYSTEM.md)**——个人应用生态总览：各项目是什么、统一登录如何工作、每个项目用什么存储、数据库选型决策与升级触发器、上手清单。接入与部署环境变量见 [INTEGRATION.md](INTEGRATION.md)。

---

## 🌟 核心特性与安全标准

新增门户模块：**AI工具**（资源导航目录）与**低价库**（规格选品 + 商家报价比较），支持站点分类浏览/搜索/收藏/收录审核、赠金与联系方式、规格级报价比较、来源状态与个人黑名单。数据范围、接口与配置见 [PORTAL.md](PORTAL.md)。

1. **工业级身份三层存储模型**：
   - 核心全局主表 (`users`)：存储全局基础凭据（UID、用户名、邮箱、加密手机号、BCrypt 密码哈希）。
   - 注册应用表 (`app_clients`)：登记全部自研项目（平台主页、微服务中枢、在线小游戏、知识库等）。
   - 多应用画像绑定表 (`user_app_profiles`)：支持用户在不同应用拥有不同昵称、不同权限角色，以及通过 **AES-256-GCM** 硬件加密的平台私有配置 JSON。

2. **军事级加密与安全防线**：
   - **密码加密**：Spring Security 6 内置 `BCryptPasswordEncoder(cost=12)`，抗 GPU 暴力破解与彩虹表攻击。
   - **敏感字段落库加密**：手机号、客户端密钥及平台私有 JSON 均通过 JPA 拦截器自动经过 **AES-256-GCM (带 128 位认证标签 AEAD)** 密文持久化。
   - **防重放单点登录**：颁发 **5 分钟短效一次性 SSO Ticket**，仅供后端安全兑换，防窃听、防重放攻击。
   - **双令牌鉴权**：短效 Access Token (2小时) + 刷新令牌 Refresh Token (7天)。

3. **精致科技感全景工作台**：
   - **应用集控大厅**：集中卡片化展示自研应用、在线状态与平台专属用户画像。
   - **一键单点直达 (SSO Launch)**：点击一键免密跳转到目标子应用。
   - **SSO 开发者接入联调中心**：支持交互式模拟子系统换票流程，并提供 **Kotlin/Spring Boot、Node.js、Python FastAPI** 即拷即用接入代码。
   - **全链路安全审计控制台**：实时追踪登录、授权与拦截日志。

---

## 🚀 极速启动与运行

### 1. 启动后端 (Kotlin + Spring Boot 3)
```powershell
cd d:\Extends\antigravityProj\Badge\backend
# 使用自带 Maven Wrapper 启动 (默认已配置零依赖持久化，开箱即用)
.\mvnw.cmd spring-boot:run
```
> 后端服务运行于: `http://localhost:8080`  
> 默认内置管理员账号：`admin` / `Admin@123456`

### 2. 启动前端 (Vue 3 + Vite)
```powershell
cd d:\Extends\antigravityProj\Badge\frontend
npm ci
npm run dev
```
> 前端访问地址: `http://localhost:5173`

### 门户功能与本地验收（2026-10-29）

- [主页](http://127.0.0.1:5173/) · [AI工具](http://127.0.0.1:5173/tools) · [低价库](http://127.0.0.1:5173/suppliers)。后端使用已有 `backend/data/badge_auth_db.mv.db`，门户数据独立存于 `backend/data/portal/`。
- AI工具收录 **208 个站点 / 21 个分类**，含精选、分类看板、站点详情、赠金与联系方式（登录可见）、评价、收录申请与管理员审核。
- 低价库收录 **102 个来源标准规格 / 8,400 余条真实公开报价**（PriceAI + PickAI），支持规格搜索、来源/形态/周期/库存筛选、规格级商家报价分页比较、异常低价过滤、个人黑名单与关注。
- 前端通过 `/api` 开发代理连接 8080；报价在后端每 15 分钟同步，刷新页面读取已保存快照。完整数据范围见 [PORTAL.md](PORTAL.md)。
- 已验证类型检查、前端构建、10 项前端测试、6 项后端集成测试，以及桌面 1440px / 手机 390px 的收藏、收录弹窗、筛选与报价分页流程。
- 独立运行后端：在 `backend/` 执行 `./mvnw.cmd -DskipTests package`，再执行 `java -jar target/badge-auth-platform-1.0.0.jar`。从后端目录启动，以保持原数据库相对路径。

### 分支约定

| 分支 | 内容 |
| --- | --- |
| `main` | 完整项目：前端、后端、部署及交接文档 |
| `frontend` | 前端部署源码与公共文档 |
| `backend` | 后端部署源码与公共文档 |

本地完整开发使用 `main`；拆分部署分支的改动应与完整项目同步。

### 3. 一键切换生产环境 MySQL 8.0
根目录已提供 `docker-compose.yml` 与 `schema.sql`：
```powershell
# 1. 启动本地 MySQL 8.0 与 Adminer 可视化管理面板
docker compose up -d

# 2. 后端切换为 MySQL 模式启动
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```
