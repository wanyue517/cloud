import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
export default defineConfig(({ mode }) => ({
  base: '/admin/',
  plugins: [vue(), tailwindcss()],
  server: {
    host: '127.0.0.1', port: 3001, strictPort: true,
    hmr: { path: '/hmr' },
    proxy: { '/api': { target: loadEnv(mode, process.cwd(), '').API_PROXY_TARGET || 'http://127.0.0.1:8000', changeOrigin: false } },
  },
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
}))
