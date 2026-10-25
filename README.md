# BADGE AUTH · 全局统一认证与身份中心

> 基于 **Kotlin + Java 17 + Spring Boot 3.3.4 + MySQL 8.0 + Spring Data JPA** 后端与 **React + Vite** 精致暗黑科技感前端的全栈单点登录 (SSO) 与多应用身份枢纽。

---

## 🌟 核心特性与安全标准

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

### 2. 启动前端 (React + Vite)
```powershell
cd d:\Extends\antigravityProj\Badge\frontend
npm run dev
```
> 前端访问地址: `http://localhost:5173`

### 3. 一键切换生产环境 MySQL 8.0
根目录已提供 `docker-compose.yml` 与 `schema.sql`：
```powershell
# 1. 启动本地 MySQL 8.0 与 Adminer 可视化管理面板
docker compose up -d

# 2. 后端切换为 MySQL 模式启动
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```
