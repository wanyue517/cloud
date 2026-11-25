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
    proxy: {
      '/api': {
        target: env.API_PROXY_TARGET || 'http://127.0.0.1:8000',
        changeOrigin: true,
      },
    },
  },
  resolve:{
    alias:{
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  }
  }
})
