import { reactive } from 'vue'
import { accountApi, type UserProfile } from '@/api/account'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
export { computeQuotaState, type QuotaState } from '@/lib/quota'

export const userState = reactive<{
  profile: UserProfile | null
  loading: boolean
  error: string
}>({
  profile: null,
  loading: false,
  error: '',
})

let inflight: Promise<UserProfile | null> | null = null

export async function fetchUserProfile(force = false): Promise<UserProfile | null> {
  if (userState.profile && !force) return userState.profile
  if (inflight && !force) return inflight

  userState.loading = true
  userState.error = ''
  inflight = accountApi
    .me()
    .then(profile => {
      userState.profile = profile
      return profile
    })
    .catch(err => {
      userState.error = normalizeErrorMessage(err, '获取用户信息失败')
      return userState.profile
    })
    .finally(() => {
      userState.loading = false
      inflight = null
    })

  return inflight
}

export function updateLocalProfile(patch: Partial<UserProfile>) {
  if (!userState.profile) return
  userState.profile = { ...userState.profile, ...patch }
}

export function clearUserProfile() {
  userState.profile = null
  userState.error = ''
}
