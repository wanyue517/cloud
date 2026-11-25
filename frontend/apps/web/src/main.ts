import { createApp } from 'vue'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { setupRequest } from '@/utils/request'
import { clearAccessToken, getAccessToken } from '@/lib/auth'

setupRequest({
  getAccessToken,
  onUnauthorized: () => {
    clearAccessToken()
    void router.replace('/auth/login')
  },
})

const app = createApp(App)

app.use(router)

app.mount('#app')
