import { createApp } from 'vue'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { initTheme } from '@repo/ui/theme'
import { setupSession } from '@repo/session/session'
import { loginUrl } from '@repo/session/navigation'
setupSession(() => { window.location.replace(loginUrl(window.location.pathname + window.location.search + window.location.hash)) })
initTheme()
createApp(App).use(router).mount('#app')
