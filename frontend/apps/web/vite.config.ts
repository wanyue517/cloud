import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
  plugins: [vue(),tailwindcss()],
  server: {
    port: 3000,
    strictPort: true,
    proxy: {
      '^/admin(?:/|$)': { target: 'http://127.0.0.1:3001', ws: true },
      '^/m(?:/|$)': { target: 'http://127.0.0.1:3002', ws: true },
      '/api': {
        target: env.API_PROXY_TARGET || 'http://127.0.0.1:8000',
        changeOrigin: false,
      },
    },
  },
  optimizeDeps: {
    include: ['hls.js'],
    exclude: ['@repo/drive-core', '@repo/ui', '@repo/session'],
  },
  resolve:{
    alias:{
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
  }
})
