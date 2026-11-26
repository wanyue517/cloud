<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Laptop, LogOut, Palette, RefreshCw, Shield, SlidersHorizontal, Trash2, Upload, User } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { Input } from '@repo/ui/input'
import { Separator } from '@repo/ui/separator'
import { accentColors, theme, type ColorMode } from '@repo/ui/theme'
import { request } from '@/utils/request'
import { opsApi } from '@/api/ops'

export type SettingsSection = 'profile' | 'security' | 'appearance' | 'files'

export interface UserProfile {
  id: string
  displayName: string
  avatarUrl: string | null
  admin: boolean
  usedBytes?: number
  quotaBytes?: number
}

interface DeviceSessionItem {
  id: string
  deviceName: string
  clientType: string
  ipAddress: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  current: boolean
}

const props = defineProps<{
  section: SettingsSection
  profile: UserProfile
  view: string
  sort: string
  showHidden: boolean
}>()

const emit = defineEmits<{
  'profile-updated': [profile: UserProfile]
  'update:view': [value: string]
  'update:sort': [value: string]
  'update:showHidden': [value: boolean]
  logout: []
}>()

const accentOptions: { key: keyof typeof accentColors; name: string; color: string }[] = [
  { key: 'blue', name: '海蓝', color: accentColors.blue },
  { key: 'violet', name: '紫罗兰', color: accentColors.violet },
  { key: 'green', name: '森林', color: accentColors.green },
  { key: 'rose', name: '玫瑰', color: accentColors.rose },
  { key: 'orange', name: '暖橙', color: accentColors.orange },
]
const colorModes: { id: ColorMode; name: string }[] = [
  { id: 'system', name: '跟随系统' },
  { id: 'light', name: '浅色' },
  { id: 'dark', name: '深色' },
]
const radiusOptions = [0, 0.5, 0.75, 1] as const

const currentMode = computed(() => theme.mode)
const currentAccent = computed(() => theme.accent)
const currentRadius = computed(() => theme.radius)
const isCompact = computed(() => theme.compact)

function setColorMode(mode: ColorMode) { theme.mode = mode }
function setAccent(accent: keyof typeof accentColors) { theme.accent = accent }
function setRadius(radius: number) { theme.radius = radius }
function setCompact(checked: boolean) { theme.compact = checked }

const displayName = ref(props.profile.displayName)
const avatarUrl = ref(props.profile.avatarUrl || '')
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const profileSaving = ref(false)
const feedback = ref('')
const error = ref('')

async function onAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  avatarUploading.value = true
  error.value = ''
  feedback.value = ''
  try {
    const uploaded = await request.upload<{ id: string; url: string }>('/v1/assets/images', file, {
      extraData: { category: 'avatar' },
    })
    avatarUrl.value = uploaded.url
    await request.put('/v1/me', { displayName: displayName.value || props.profile.displayName, avatarUrl: uploaded.url })
    const me = await request.get<UserProfile>('/v1/me')
    emit('profile-updated', me)
    feedback.value = '头像已上传至当前存储方案并保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '上传头像失败'
  } finally {
    avatarUploading.value = false
  }
}

async function clearAvatar() {
  if (avatarUploading.value || profileSaving.value) return
  avatarUrl.value = ''
  await saveProfile()
}

const sessions = ref<DeviceSessionItem[]>([])
const sessionsLoading = ref(false)
const revokingId = ref('')

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordSaving = ref(false)

const userMaxVersions = ref(10)
const systemMaxVersions = ref(20)
const versionPolicySaving = ref(false)

async function loadVersionPolicy() {
  try {
    const res = await opsApi.getVersionPolicy()
    userMaxVersions.value = res.maxVersions
    systemMaxVersions.value = res.systemMaxVersions
  } catch { /* ignore */ }
}

async function saveVersionPolicy() {
  if (versionPolicySaving.value) return
  versionPolicySaving.value = true
  error.value = ''
  feedback.value = ''
  try {
    const res = await opsApi.updateVersionPolicy(Number(userMaxVersions.value) || 10)
    userMaxVersions.value = res.maxVersions
    systemMaxVersions.value = res.systemMaxVersions
    feedback.value = `个人历史版本保留上限已更新为 ${res.maxVersions} 个`
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存版本策略失败'
  } finally {
    versionPolicySaving.value = false
  }
}

const quotaPercent = computed(() => {
  const quota = Number(props.profile.quotaBytes || 0)
  const used = Number(props.profile.usedBytes || 0)
  if (!quota) return 0
  return Math.min(100, Math.round((used / quota) * 1000) / 10)
})

function formatBytes(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}`
}

async function changePassword() {
  if (passwordSaving.value) return
  error.value = ''
  feedback.value = ''
  if (!currentPassword.value || newPassword.value.length < 8) {
    error.value = '请输入当前密码与 8–32 位新密码'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  try {
    await request.put('/v1/me/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    feedback.value = '登录密码已修改，其他设备会话已自动失效。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '修改密码失败'
  } finally {
    passwordSaving.value = false
  }
}

watch(
  () => props.profile,
  next => {
    displayName.value = next.displayName
    avatarUrl.value = next.avatarUrl || ''
  },
  { deep: true },
)

async function saveProfile() {
  if (profileSaving.value) return
  profileSaving.value = true
  error.value = ''
  feedback.value = ''
  try {
    await request.put('/v1/me', { displayName: displayName.value, avatarUrl: avatarUrl.value })
    const me = await request.get<UserProfile>('/v1/me')
    emit('profile-updated', me)
    feedback.value = '个人资料已保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存资料失败'
  } finally {
    profileSaving.value = false
  }
}

async function loadSessions() {
  sessionsLoading.value = true
  error.value = ''
  try {
    sessions.value = await request.get<DeviceSessionItem[]>('/v1/auth/sessions')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载登录设备失败'
  } finally {
    sessionsLoading.value = false
  }
}

async function revokeSession(id: string) {
  revokingId.value = id
  error.value = ''
  feedback.value = ''
  try {
    await request.delete(`/v1/auth/sessions/${id}`)
    sessions.value = sessions.value.filter(item => item.id !== id)
    feedback.value = '已下线指定设备会话'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '下线设备失败'
  } finally {
    revokingId.value = ''
  }
}

watch(
  () => props.section,
  next => {
    error.value = ''
    feedback.value = ''
    if (next === 'security') void loadSessions()
    if (next === 'files') void loadVersionPolicy()
  },
)

onMounted(() => {
  if (props.section === 'security') void loadSessions()
  if (props.section === 'files') void loadVersionPolicy()
})
</script>

<template>
  <div class="user-center">
    <p v-if="error" class="drive-error" role="alert">{{ error }}</p>
    <p v-if="feedback" class="drive-notice" role="status">{{ feedback }}</p>

    <!-- 个人资料 -->
    <section v-if="section === 'profile'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><User :size="18" /></div>
        <div>
          <h2>个人资料</h2>
          <p>管理你的公开显示昵称、头像以及账户基础信息。</p>
        </div>
      </header>

      <Separator />

      <form class="settings-form" @submit.prevent="saveProfile">
        <div class="profile-avatar-row">
          <img
            v-if="avatarUrl"
            :src="avatarUrl"
            alt="头像预览"
            class="profile-avatar-img"
          />
          <div v-else class="profile-avatar-fallback">
            {{ (displayName || profile.displayName || 'U').slice(0, 1).toUpperCase() }}
          </div>
          <div class="profile-meta flex-1">
            <strong>{{ profile.displayName || '未设置昵称' }}</strong>
            <span>账号 ID：{{ profile.id || '—' }} · 角色：{{ profile.admin ? '超级管理员' : '普通成员' }}</span>
          </div>
          <div class="flex items-center gap-2 shrink-0">
            <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="onAvatarSelected" />
            <Button type="button" variant="outline" size="sm" :disabled="avatarUploading || profileSaving" @click="avatarInput?.click()">
              <Upload :size="14" /> {{ avatarUploading ? '上传中…' : '上传头像' }}
            </Button>
            <Button v-if="avatarUrl" type="button" variant="ghost" size="sm" :disabled="avatarUploading || profileSaving" @click="clearAvatar">
              <Trash2 :size="14" /> 恢复默认
            </Button>
          </div>
        </div>

        <label class="settings-field">
          <span>显示昵称</span>
          <Input v-model="displayName" aria-label="显示昵称" :disabled="profileSaving" placeholder="输入显示昵称" />
        </label>

        <div class="rounded-lg border border-border bg-muted/30 p-3.5 space-y-1.5 text-xs">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">存储空间用量</span>
            <span class="font-medium tabular-nums">
              {{ formatBytes(Number(profile.usedBytes || 0)) }} /
              {{ Number(profile.quotaBytes || 0) > 0 ? formatBytes(Number(profile.quotaBytes)) : '不限' }}
            </span>
          </div>
          <div v-if="Number(profile.quotaBytes || 0) > 0" class="h-1.5 w-full rounded-full bg-muted overflow-hidden">
            <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${quotaPercent}%` }" />
          </div>
        </div>

        <div class="settings-actions">
          <Button type="submit" :disabled="profileSaving">
            {{ profileSaving ? '正在保存…' : '保存资料' }}
          </Button>
        </div>
      </form>
    </section>

    <!-- 账户与安全 -->
    <section v-else-if="section === 'security'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><Shield :size="18" /></div>
        <div class="flex-1">
          <h2>账户与安全</h2>
          <p>查看当前账号的所有活跃登录设备与刷新会话，发现异常可立即下线。</p>
        </div>
        <Button variant="outline" size="sm" :disabled="sessionsLoading" @click="loadSessions">
          <RefreshCw :size="14" :class="{ 'animate-spin': sessionsLoading }" /> 刷新
        </Button>
      </header>

      <Separator />

      <div v-if="sessionsLoading" class="settings-empty">正在加载活跃设备列表…</div>
      <div v-else-if="!sessions.length" class="settings-empty">当前没有记录到其他活跃会话。</div>
      <div v-else class="session-list">
        <div v-for="item in sessions" :key="item.id" class="session-row">
          <div class="session-icon"><Laptop :size="18" /></div>
          <div class="session-info">
            <div class="session-title">
              <strong>{{ item.deviceName }}</strong>
              <span v-if="item.current" class="session-badge">当前设备</span>
            </div>
            <p>
              {{ item.clientType }}{{ item.ipAddress ? ` · ${item.ipAddress}` : '' }} · 最近活跃 {{ item.lastUsedAt || item.createdAt }}
            </p>
          </div>
          <Button
            variant="outline"
            size="sm"
            :disabled="item.current || revokingId === item.id"
            @click="revokeSession(item.id)"
          >
            {{ item.current ? '当前' : revokingId === item.id ? '处理中…' : '下线' }}
          </Button>
        </div>
      </div>

      <Separator />

      <form class="settings-form" @submit.prevent="changePassword">
        <div class="text-sm font-medium">修改登录密码</div>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Input v-model="currentPassword" type="password" autocomplete="current-password" placeholder="当前密码" :disabled="passwordSaving" />
          <Input v-model="newPassword" type="password" autocomplete="new-password" maxlength="32" placeholder="新密码（8–32位）" :disabled="passwordSaving" />
          <Input v-model="confirmPassword" type="password" autocomplete="new-password" maxlength="32" placeholder="确认新密码" :disabled="passwordSaving" />
        </div>
        <div class="settings-actions">
          <Button type="submit" size="sm" variant="outline" :disabled="passwordSaving">
            {{ passwordSaving ? '正在修改…' : '更新登录密码' }}
          </Button>
        </div>
      </form>

      <Separator />

      <div class="settings-danger-row">
        <div>
          <strong>退出当前账号</strong>
          <p>将立即撤销当前浏览器的登录凭证并返回登录页。</p>
        </div>
        <Button variant="outline" class="text-destructive" @click="emit('logout')">
          <LogOut :size="15" /> 退出登录
        </Button>
      </div>
    </section>

    <!-- 显示与外观 -->
    <section v-else-if="section === 'appearance'" class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><Palette :size="18" /></div>
        <div>
          <h2>显示与外观</h2>
          <p>自定义颜色模式、主题强调色、圆角与列表密度，设置自动保存在当前浏览器。</p>
        </div>
      </header>

      <Separator />

      <div class="settings-form">
        <fieldset class="settings-fieldset">
          <legend>颜色模式</legend>
          <div class="settings-option-row">
            <Button
              v-for="option in colorModes"
              :key="option.id"
              :variant="currentMode === option.id ? 'default' : 'outline'"
              :aria-pressed="currentMode === option.id"
              @click="setColorMode(option.id)"
            >
              {{ option.name }}
            </Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>主题强调色</legend>
          <div class="settings-option-row">
            <Button
              v-for="item in accentOptions"
              :key="item.key"
              variant="outline"
              :aria-pressed="currentAccent === item.key"
              :class="currentAccent === item.key ? 'ring-2 ring-ring' : ''"
              @click="setAccent(item.key)"
            >
              <span class="size-3 rounded-full" :style="{ background: item.color }" />
              {{ item.name }}
            </Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>界面圆角</legend>
          <div class="settings-option-row">
            <Button
              v-for="radius in radiusOptions"
              :key="radius"
              :variant="currentRadius === radius ? 'default' : 'outline'"
              :aria-pressed="currentRadius === radius"
              @click="setRadius(radius)"
            >
              {{ radius === 0 ? '直角' : `${radius * 16}px` }}
            </Button>
          </div>
        </fieldset>

        <label class="settings-checkbox">
          <input :checked="isCompact" type="checkbox" class="accent-primary" @change="setCompact(($event.target as HTMLInputElement).checked)" />
          <div>
            <strong>紧凑文件列表</strong>
            <span>减小文件行高（36px），在单屏内浏览更多文件。</span>
          </div>
        </label>
      </div>
    </section>

    <!-- 文件偏好 -->
    <section v-else class="settings-card">
      <header class="settings-card-header">
        <div class="settings-card-icon"><SlidersHorizontal :size="18" /></div>
        <div>
          <h2>文件工作台偏好</h2>
          <p>调整文件列表的默认展示方式、排序规则与隐藏项可见性。</p>
        </div>
      </header>

      <Separator />

      <div class="settings-form">
        <fieldset class="settings-fieldset">
          <legend>默认视图模式</legend>
          <div class="settings-option-row">
            <Button :variant="view === 'list' ? 'default' : 'outline'" @click="emit('update:view', 'list')">列表视图</Button>
            <Button :variant="view === 'grid' ? 'default' : 'outline'" @click="emit('update:view', 'grid')">网格视图</Button>
          </div>
        </fieldset>

        <fieldset class="settings-fieldset">
          <legend>默认排序方式</legend>
          <div class="settings-option-row">
            <Button :variant="sort === 'name' ? 'default' : 'outline'" @click="emit('update:sort', 'name')">名称排序</Button>
            <Button :variant="sort === 'updated' ? 'default' : 'outline'" @click="emit('update:sort', 'updated')">最近修改</Button>
            <Button :variant="sort === 'size' ? 'default' : 'outline'" @click="emit('update:sort', 'size')">文件大小</Button>
          </div>
        </fieldset>

        <label class="settings-checkbox">
          <input :checked="showHidden" type="checkbox" class="accent-primary" @change="emit('update:showHidden', ($event.target as HTMLInputElement).checked)" />
          <div>
            <strong>显示隐藏文件与文件夹</strong>
            <span>开启后将在文件列表中展示标记为隐藏的项目。</span>
          </div>
        </label>

        <Separator />

        <form class="space-y-2" @submit.prevent="saveVersionPolicy">
          <label class="settings-field">
            <span>单文件历史版本保留上限（系统最大允许 {{ systemMaxVersions }} 个）</span>
            <div class="flex items-center gap-2">
              <Input v-model.number="userMaxVersions" type="number" min="1" :max="systemMaxVersions" class="w-32" :disabled="versionPolicySaving" />
              <Button type="submit" size="sm" variant="outline" :disabled="versionPolicySaving">
                {{ versionPolicySaving ? '保存中…' : '保存版本上限' }}
              </Button>
            </div>
            <small>替换文件产生新版本时，超出此上限的最旧版本将自动清理。</small>
          </label>
        </form>
      </div>
    </section>
  </div>
</template>
