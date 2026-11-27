<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Button as VanButton } from 'vant'
import { Fingerprint, Lock, ShieldCheck } from '@lucide/vue'
import { getAccessToken } from '@repo/session/auth'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { mobileDriveApi } from '@/api/drive'
import {
  authenticateBiometric,
  checkBiometricAvailability,
  loadSecurityLockPrefs,
  mobileCapabilities,
  onAppStateChange,
  type BiometricAvailability,
} from '@/native'

const privacyBlurActive = ref(false)
const startupLocked = ref(false)
const biometricInfo = ref<BiometricAvailability>({
  available: false,
  biometryType: 'none',
  label: '不支持',
})
const password = ref('')
const verifying = ref(false)
const errorText = ref('')

let stopStateWatch: (() => void) | null = null

async function tryBiometricUnlock() {
  errorText.value = ''
  const res = await authenticateBiometric('解锁 Cloudlane 应用')
  if (res.ok) {
    startupLocked.value = false
    password.value = ''
    return
  }
  errorText.value = res.message || '生物识别未通过，请使用登录密码解锁'
}

async function handlePasswordUnlock() {
  if (!password.value.trim() || verifying.value) return
  verifying.value = true
  errorText.value = ''
  try {
    await mobileDriveApi.unlockVault(password.value, 15)
    startupLocked.value = false
    password.value = ''
  } catch (err) {
    errorText.value = normalizeErrorMessage(err, '密码验证失败，请重试')
  } finally {
    verifying.value = false
  }
}

onMounted(async () => {
  if (!mobileCapabilities.appSecurityShield) return
  biometricInfo.value = await checkBiometricAvailability()
  const prefs = loadSecurityLockPrefs()

  if (prefs.appStartupLockEnabled && getAccessToken()) {
    startupLocked.value = true
  }

  stopStateWatch = onAppStateChange((isActive) => {
    const latest = loadSecurityLockPrefs()
    if (!isActive) {
      if (latest.backgroundBlurEnabled) {
        privacyBlurActive.value = true
      }
      if (latest.appStartupLockEnabled && getAccessToken()) {
        startupLocked.value = true
      }
    } else {
      privacyBlurActive.value = false
    }
  })
})

onBeforeUnmount(() => {
  stopStateWatch?.()
})
</script>

<template>
  <!-- 后台防偷窥高斯模糊遮罩 -->
  <div
    v-if="privacyBlurActive"
    class="fixed inset-0 z-[9998] flex flex-col items-center justify-center bg-background/85 backdrop-blur-2xl select-none"
    aria-label="后台隐私保护遮罩"
  >
    <span class="flex h-16 w-16 items-center justify-center rounded-3xl bg-primary/15 text-primary shadow-md">
      <ShieldCheck :size="32" />
    </span>
    <p class="mt-3 text-sm font-semibold text-foreground">Cloudlane 隐私保护中</p>
  </div>

  <!-- 应用启动锁界面（生物识别优先，失败/取消平滑降级密码输入） -->
  <div
    v-else-if="startupLocked"
    class="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-background px-6"
    role="dialog"
    aria-modal="true"
    aria-label="应用安全锁"
  >
    <div class="w-full max-w-xs space-y-5 text-center">
      <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-primary/10 text-primary">
        <Lock :size="28" />
      </span>
      <div class="space-y-1">
        <h2 class="text-lg font-semibold text-foreground">Cloudlane 已锁定</h2>
        <p class="text-xs text-muted-foreground">请验证身份以继续访问您的云端文件</p>
      </div>

      <p
        v-if="errorText"
        class="rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive"
      >
        {{ errorText }}
      </p>

      <VanButton
        v-if="biometricInfo.available"
        block
        round
        type="primary"
        class="!h-11"
        @click="tryBiometricUnlock"
      >
        <span class="inline-flex items-center gap-2">
          <Fingerprint :size="18" />
          <span>使用{{ biometricInfo.label }}解锁</span>
        </span>
      </VanButton>

      <form class="space-y-3 pt-1" @submit.prevent="handlePasswordUnlock">
        <input
          v-model="password"
          type="password"
          autocomplete="current-password"
          placeholder="输入登录密码解锁"
          class="w-full h-11 rounded-xl border border-input bg-card px-3.5 text-sm text-foreground outline-none focus:border-primary"
        />
        <VanButton
          block
          round
          :plain="biometricInfo.available"
          type="primary"
          native-type="submit"
          :loading="verifying"
          class="!h-10"
        >
          使用密码解锁
        </VanButton>
      </form>
    </div>
  </div>
</template>
