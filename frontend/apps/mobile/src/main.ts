import { createApp } from 'vue'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { initMobileTheme, isDarkTheme } from '@/lib/theme-sync'
import { initNetworkGuard } from '@/lib/network'
import { setupSession } from '@repo/session/session'
import { mobileLoginUrl } from '@repo/session/navigation'
import {
  buildApiBaseUrl,
  createNativeTokenStorage,
  getDefaultDeviceName,
  getSessionClientType,
  hydrateNativeTokenStorage,
  hydrateServerOriginFromNative,
  isNativeApp,
  syncNativeStatusBar,
} from '@/native'

async function bootstrap() {
  await hydrateNativeTokenStorage()
  const serverOrigin = await hydrateServerOriginFromNative()

  setupSession(
    () => {
      window.location.replace(mobileLoginUrl(window.location.pathname + window.location.search + window.location.hash))
    },
    {
      clientType: getSessionClientType(),
      deviceName: getDefaultDeviceName(),
      useHeaderRefresh: isNativeApp(),
      storage: createNativeTokenStorage(),
      baseURL: buildApiBaseUrl(serverOrigin),
    },
  )

  initMobileTheme()
  void syncNativeStatusBar(isDarkTheme.value)
  initNetworkGuard()

  createApp(App).use(router).mount('#app')
}

void bootstrap()
