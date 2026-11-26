import { reactive } from 'vue'
import { request } from '@repo/session/request'

export interface PublicSiteConfig {
  siteName: string
  siteLogoUrl: string
  siteAnnouncement: string
  allowRegister: boolean
  requireEmailVerify: boolean
  allowPasswordLogin: boolean
  uploadChunkSizeMb: number
  uploadMaxFileMb: number
  loaded: boolean
}

const state = reactive<PublicSiteConfig>({
  siteName: 'Cloudlane',
  siteLogoUrl: '',
  siteAnnouncement: '',
  allowRegister: true,
  requireEmailVerify: true,
  allowPasswordLogin: true,
  uploadChunkSizeMb: 4,
  uploadMaxFileMb: 10240,
  loaded: false,
})

let inflight: Promise<PublicSiteConfig> | null = null

export function useSiteConfig() {
  async function loadSiteConfig(force = false): Promise<PublicSiteConfig> {
    if (state.loaded && !force) return state
    if (!inflight) {
      inflight = request
        .get<Partial<PublicSiteConfig>>('/v1/public/site-config')
        .then(res => {
          state.siteName = res.siteName || 'Cloudlane'
          state.siteLogoUrl = res.siteLogoUrl || ''
          state.siteAnnouncement = res.siteAnnouncement || ''
          state.allowRegister = res.allowRegister ?? true
          state.requireEmailVerify = res.requireEmailVerify ?? true
          state.allowPasswordLogin = res.allowPasswordLogin ?? true
          state.uploadChunkSizeMb = Number(res.uploadChunkSizeMb) || 4
          state.uploadMaxFileMb = Number(res.uploadMaxFileMb) || 10240
          state.loaded = true
          if (typeof document !== 'undefined' && state.siteName) {
            document.title = state.siteName
          }
          return state
        })
        .catch(() => {
          state.loaded = true
          return state
        })
        .finally(() => {
          inflight = null
        })
    }
    return inflight
  }

  return { siteConfig: state, loadSiteConfig }
}
