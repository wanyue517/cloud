import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const config = {
    base: '/m/',
    plugins: [vue(), tailwindcss()],
    server: {
      host: true,
      port: 3002,
      strictPort: true,
      hmr: { path: '/hmr' },
      proxy: {
        '/api': {
          target: env.API_PROXY_TARGET || 'http://127.0.0.1:8000',
          changeOrigin: false,
        },
      },
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
  if (env.CAPACITOR_BUILD === '1') {
    config.base = './'
  }
  return config
})
