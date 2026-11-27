<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Button as VanButton, Switch as VanSwitch, showConfirmDialog } from 'vant'
import { Eye, EyeOff, Fingerprint, Laptop, RefreshCw, Shield, Smartphone, Trash2 } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatDateTime } from '@repo/drive-core/format'
import { accountApi, type DeviceSessionItem } from '@/api/account'
import {
  checkBiometricAvailability,
  loadSecurityLockPrefs,
  mobileCapabilities,
  saveSecurityLockPrefs,
  type BiometricAvailability,
} from '@/native'
import { EmptyState, MobileShell } from '@/components/common'

const pwdForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const showPwd = ref(false)
const pwdSaving = ref(false)

const sessions = ref<DeviceSessionItem[]>([])
const sessionsLoading = ref(false)
const revokingId = ref('')
const biometricInfo = ref<BiometricAvailability>({
  available: false,
  biometryType: 'none',
  label: '不支持',
})
const lockPrefs = reactive(loadSecurityLockPrefs())

function updateLockPref(key: 'biometricVaultEnabled' | 'appStartupLockEnabled' | 'backgroundBlurEnabled', val: boolean) {
  lockPrefs[key] = val
  saveSecurityLockPrefs({ [key]: val })
  toast.success('安全锁设置已保存')
}

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await accountApi.listSessions()
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '加载活跃设备失败'))
  } finally {
    sessionsLoading.value = false
  }
}

onMounted(() => {
  void loadSessions()
  void checkBiometricAvailability().then((info) => {
    biometricInfo.value = info
  })
})

async function handleChangePassword() {
  if (pwdSaving.value) return
  if (!pwdForm.currentPassword) {
    toast.error('请输入当前密码')
    return
  }
  if (pwdForm.newPassword.length < 8 || pwdForm.newPassword.length > 32) {
    toast.error('新密码长度须为 8–32 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    toast.error('两次输入的新密码不一致')
    return
  }

  pwdSaving.value = true
  try {
    await accountApi.changePassword({
      currentPassword: pwdForm.currentPassword,
      newPassword: pwdForm.newPassword,
    })
    pwdForm.currentPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    toast.success('密码修改成功，旧设备会话已失效')
    await loadSessions()
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '修改密码失败'))
  } finally {
    pwdSaving.value = false
  }
}

async function handleRevoke(item: DeviceSessionItem) {
  try {
    await showConfirmDialog({
      title: '下线设备',
      message: `确定要将「${item.deviceName}」强制下线吗？`,
      confirmButtonText: '确认下线',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  revokingId.value = item.id
  try {
    await accountApi.revokeSession(item.id)
    toast.success('已下线该设备')
    await loadSessions()
  } catch (err) {
    toast.error(normalizeErrorMessage(err, '下线设备失败'))
  } finally {
    revokingId.value = ''
  }
}

function isMobileClient(clientType: string): boolean {
  return clientType === 'MOBILE_H5' || clientType === 'IOS' || clientType === 'ANDROID'
}

function clientBadge(clientType: string): string {
  switch (clientType) {
    case 'IOS': return 'iOS App'
    case 'ANDROID': return 'Android App'
    case 'MOBILE_H5': return '移动 Web'
    default: return '桌面 Web'
  }
}
</script>

<template>
  <MobileShell title="账户与安全" fallback-path="/me" padded>
    <div class="space-y-5 pb-6">
      <!-- 应用安全锁与防偷窥保护（M038：仅原生 App 显示，H5 浏览器自动隐藏） -->
      <section v-if="mobileCapabilities.appSecurityShield" class="rounded-2xl border border-border bg-card p-4 space-y-3.5">
        <div class="flex items-center gap-2">
          <Shield :size="16" class="text-primary" />
          <h2 class="text-sm font-semibold text-foreground">应用安全锁与防偷窥</h2>
        </div>

        <div class="flex items-center justify-between gap-3 py-1">
          <div class="min-w-0">
            <p class="text-sm font-medium text-foreground">后台防偷窥高斯模糊遮罩</p>
            <p class="text-xs text-muted-foreground">切换后台或截屏缩略图时自动模糊页面内容</p>
          </div>
          <VanSwitch
            :model-value="lockPrefs.backgroundBlurEnabled"
            size="20px"
            @update:model-value="v => updateLockPref('backgroundBlurEnabled', Boolean(v))"
          />
        </div>

        <div class="flex items-center justify-between gap-3 py-1 border-t border-border pt-3">
          <div class="min-w-0">
            <p class="text-sm font-medium text-foreground">应用启动锁</p>
            <p class="text-xs text-muted-foreground">冷启动或从后台切回时要求身份验证</p>
          </div>
          <VanSwitch
            :model-value="lockPrefs.appStartupLockEnabled"
            size="20px"
            @update:model-value="v => updateLockPref('appStartupLockEnabled', Boolean(v))"
          />
        </div>

        <!-- 仅在支持生物识别的设备上展示此开关 -->
        <div
          v-if="biometricInfo.available"
          class="flex items-center justify-between gap-3 py-1 border-t border-border pt-3"
        >
          <div class="min-w-0 flex items-start gap-2">
            <Fingerprint :size="16" class="text-primary shrink-0 mt-0.5" />
            <div>
              <p class="text-sm font-medium text-foreground">{{ biometricInfo.label }}解锁</p>
              <p class="text-xs text-muted-foreground">用于快速解锁私密空间、隐藏项与应用启动锁</p>
            </div>
          </div>
          <VanSwitch
            :model-value="lockPrefs.biometricVaultEnabled"
            size="20px"
            @update:model-value="v => updateLockPref('biometricVaultEnabled', Boolean(v))"
          />
        </div>
      </section>

      <!-- 修改密码 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3.5">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold text-foreground">修改登录密码</h2>
          <button
            type="button"
            class="inline-flex items-center gap-1 text-xs text-muted-foreground"
            @click="showPwd = !showPwd"
          >
            <EyeOff v-if="showPwd" :size="14" />
            <Eye v-else :size="14" />
            <span>{{ showPwd ? '隐藏' : '显示' }}</span>
          </button>
        </div>

        <input
          v-model="pwdForm.currentPassword"
          :type="showPwd ? 'text' : 'password'"
          autocomplete="current-password"
          placeholder="当前密码"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />
        <input
          v-model="pwdForm.newPassword"
          :type="showPwd ? 'text' : 'password'"
          autocomplete="new-password"
          placeholder="新密码（8–32 位）"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />
        <input
          v-model="pwdForm.confirmPassword"
          :type="showPwd ? 'text' : 'password'"
          autocomplete="new-password"
          placeholder="确认新密码"
          class="w-full h-11 rounded-xl border border-input bg-background px-3 text-sm text-foreground outline-none focus:border-primary"
        />

        <VanButton
          type="primary"
          block
          round
          :loading="pwdSaving"
          loading-text="正在更新…"
          class="!h-10 !text-sm"
          @click="handleChangePassword"
        >
          更新密码
        </VanButton>
      </section>

      <!-- 活跃登录设备 -->
      <section class="space-y-2.5">
        <div class="flex items-center justify-between px-1">
          <h2 class="text-sm font-semibold text-foreground">活跃登录设备 ({{ sessions.length }})</h2>
          <button
            type="button"
            class="inline-flex items-center gap-1 text-xs text-primary"
            :disabled="sessionsLoading"
            @click="loadSessions"
          >
            <RefreshCw :size="13" :class="{ 'animate-spin': sessionsLoading }" />
            <span>刷新</span>
          </button>
        </div>

        <EmptyState v-if="!sessionsLoading && sessions.length === 0" title="暂无活跃会话" />

        <div v-else class="space-y-2.5">
          <div
            v-for="item in sessions"
            :key="item.id"
            class="rounded-2xl border border-border bg-card p-3.5 flex items-start gap-3"
          >
            <div class="h-10 w-10 rounded-xl bg-muted text-foreground flex items-center justify-center shrink-0">
              <Smartphone v-if="isMobileClient(item.clientType)" :size="18" />
              <Laptop v-else :size="18" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-1.5 flex-wrap">
                <p class="text-sm font-medium text-foreground truncate">{{ item.deviceName }}</p>
                <span class="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                  {{ clientBadge(item.clientType) }}
                </span>
                <span
                  v-if="item.current"
                  class="rounded bg-primary/15 px-1.5 py-0.5 text-[10px] font-medium text-primary"
                >
                  当前设备
                </span>
              </div>
              <p class="mt-1 text-xs text-muted-foreground">
                IP：{{ item.ipAddress || '未知' }}
              </p>
              <p class="text-[11px] text-muted-foreground">
                最近活跃：{{ formatDateTime(item.lastUsedAt || item.createdAt) }}
              </p>
            </div>
            <button
              v-if="!item.current"
              type="button"
              :disabled="revokingId === item.id"
              class="inline-flex items-center gap-1 rounded-lg border border-destructive/30 px-2.5 py-1.5 text-xs text-destructive shrink-0 active:bg-destructive/10 disabled:opacity-50"
              @click="handleRevoke(item)"
            >
              <Trash2 :size="13" />
              <span>{{ revokingId === item.id ? '下线中' : '下线' }}</span>
            </button>
          </div>
        </div>
      </section>
    </div>
  </MobileShell>
</template>
