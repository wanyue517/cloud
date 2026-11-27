import { reactive } from 'vue'
import { request } from '@repo/session/request'

export interface PublicSiteConfig {
  siteName: string
  siteLogoUrl: string
  allowRegister: boolean
}

export const siteConfig = reactive<PublicSiteConfig>({
  siteName: 'Cloudlane',
  siteLogoUrl: '',
  allowRegister: true,
})

let loaded = false
let inflight: Promise<PublicSiteConfig> | null = null

export async function loadSiteConfig(force = false): Promise<PublicSiteConfig> {
  if (loaded && !force) return siteConfig
  if (inflight && !force) return inflight

  inflight = request
    .get<Partial<PublicSiteConfig>>('/v1/public/site-config', undefined, {
      skipAuth: true,
      skipRefresh: true,
      silent: true,
    })
    .then(data => {
      if (data?.siteName && data.siteName.trim()) {
        siteConfig.siteName = data.siteName.trim()
      }
      if (typeof data?.siteLogoUrl === 'string') {
        siteConfig.siteLogoUrl = data.siteLogoUrl
      }
      if (typeof data?.allowRegister === 'boolean') {
        siteConfig.allowRegister = data.allowRegister
      }
      loaded = true
      return siteConfig
    })
    .catch(() => siteConfig)
    .finally(() => {
      inflight = null
    })

  return inflight
}
