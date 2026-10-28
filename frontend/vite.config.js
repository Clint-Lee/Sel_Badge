import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: { '/api': { target: process.env.BADGE_API_TARGET || 'http://127.0.0.1:8080', changeOrigin: true } },
  },
});
