# Badge 门户前端

基于 Vue 3、Vite 和 Lucide，包含统一登录、应用入口、画像、安全动态、开发者控制台，以及 AI工具与低价库。新增模块使用 TypeScript，沿用暖灰、松绿设计系统。

## 本地运行

需要 Node.js（本机验证版本 20.20.0）与 npm。先启动 Badge 后端，再在本目录运行：

```powershell
npm ci
npm run dev
```

入口：[主页](http://127.0.0.1:5173/) · [AI工具](http://127.0.0.1:5173/tools) · [低价库](http://127.0.0.1:5173/suppliers)。默认仅监听本机；开发请求 /api 代理到 http://127.0.0.1:8080。

## 验证与构建

```powershell
npm run typecheck
npm test
npm run build
```

构建结果位于 dist/。2026-10-28 已通过类型检查、构建、6 项前端测试及桌面/手机浏览器验证。

## 配置与部署

| 变量 | 默认 | 用途 |
| --- | --- | --- |
| VITE_API_BASE_URL | /api | 构建时 API 地址，生产推荐同源代理 |
| BADGE_API_TARGET | http://127.0.0.1:8080 | Vite 开发代理目标 |

生产 Web 服务将 /api/ 代理至后端，其余页面回退 index.html。跨域时在后端配置 BADGE_CORS_ORIGINS。main 包含完整项目，frontend 分支只保留前端部署源码与公共文档。

功能、真实报价覆盖、存储及接口说明见根目录 [PORTAL.md](../PORTAL.md)，SSO 与生产配置见 [INTEGRATION.md](../INTEGRATION.md)。
