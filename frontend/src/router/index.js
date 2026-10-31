import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  { path: '/', name: 'apps', component: () => import('../components/AppsHub.vue') },
  { path: '/apps', redirect: '/' },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/PlatformDashboard.vue') },
  { path: '/tools', name: 'tools', component: () => import('../views/tools/Index.vue') },
  { path: '/suppliers', name: 'suppliers', component: () => import('../views/suppliers/Index.vue') },
  // 登录页使用独立全屏布局（meta.bare：隐藏导航与页脚）
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { bare: true } },
  { path: '/sso', name: 'sso', component: () => import('../components/SsoPlayground.vue') },
  { path: '/personas', name: 'personas', component: () => import('../components/PersonaManager.vue') },
  { path: '/security', name: 'security', component: () => import('../components/SecurityRadar.vue') },
  { path: '/guide', name: 'guide', component: () => import('../components/ArchitectureGuide.vue') },
  { path: '/profile', name: 'profile', component: () => import('../views/ProfileView.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 };
  },
});

export default router;
