<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Cell as VanCell, CellGroup as VanCellGroup, showConfirmDialog } from 'vant'
import {
  Activity,
  Bell,
  ChevronRight,
  CloudUpload,
  DownloadCloud,
  FileScan,
  Image as ImageIcon,
  LockKeyhole,
  LogOut,
  Monitor,
  Palette,
  QrCode,
  Shield,
  SlidersHorizontal,
  Trash2,
  UploadCloud,
  User,
  Wifi,
} from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { logout as endSession } from '@repo/session/session'
import { clearPrivateSpaceToken } from '@repo/session/request'
import { formatBytes } from '@repo/drive-core/format'
import { mapMobilePathToWeb, saveClientModePreference } from '@repo/drive-core/client-mode'
import { MobileShell, PullRefresh } from '@/components/common'
import { mobileCapabilities } from '@/native'
import { mobileUi } from '@/stores/mobile-ui'
import { clearUserProfile, computeQuotaState, fetchUserProfile, userState } from '@/stores/user'

const router = useRouter()
const refreshing = ref(false)
const loggingOut = ref(false)

onMounted(() => {
  void fetchUserProfile()
})

async function onRefresh() {
  refreshing.value = true
  try {
    await fetchUserProfile(true)
  } finally {
    refreshing.value = false
  }
}

const quota = computed(() =>
  computeQuotaState(userState.profile?.usedBytes, userState.profile?.quotaBytes),
)

const quotaBarClass = computed(() => {
  if (quota.value.level === 'danger') return 'bg-destructive'
  if (quota.value.level === 'warning') return 'bg-amber-500'
  return 'bg-primary'
})

const initials = computed(() => {
  const name = userState.profile?.displayName?.trim() || 'U'
  return name.slice(0, 1).toUpperCase()
})

function switchToDesktop() {
  saveClientModePreference('desktop')
  window.location.assign(mapMobilePathToWeb('/m/home'))
}

async function handleLogout() {
  if (loggingOut.value) return
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定要退出当前账号吗？',
      confirmButtonText: '退出登录',
      confirmButtonColor: 'var(--destructive)',
    })
  } catch {
    return
  }

  loggingOut.value = true
  try {
    await endSession()
  } catch {
    // Even if server logout fails, local tokens are cleared
  } finally {
    clearPrivateSpaceToken()
    clearUserProfile()
    loggingOut.value = false
    toast.success('已退出登录')
    await router.replace('/auth/login')
  }
}
</script>

<template>
  <MobileShell title="我的" :show-back="false">
    <template #right>
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary active:bg-primary/20"
        aria-label="扫一扫登录 PC"
        @click="router.push('/qr-confirm')"
      >
        <QrCode :size="14" />
        <span>扫一扫</span>
      </button>
    </template>
    <PullRefresh v-model="refreshing" @refresh="onRefresh">
      <div class="space-y-4 px-4 py-4 pb-8">
        <!-- 用户资料卡片 -->
        <button
          type="button"
          class="w-full flex items-center gap-3.5 rounded-2xl border border-border bg-card p-4 text-left active:bg-accent/50 transition-colors"
          @click="router.push('/settings/profile')"
        >
          <div class="h-14 w-14 rounded-full bg-primary/15 text-primary flex items-center justify-center text-lg font-semibold overflow-hidden shrink-0">
            <img
              v-if="userState.profile?.avatarUrl"
              :src="userState.profile.avatarUrl"
              :alt="userState.profile.displayName"
              class="h-full w-full object-cover"
            />
            <span v-else>{{ initials }}</span>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <p class="text-base font-semibold truncate text-foreground">
                {{ userState.profile?.displayName || (userState.loading ? '加载中…' : '个人用户') }}
              </p>
              <span
                v-if="userState.profile?.admin"
                class="rounded-full bg-primary/15 px-2 py-0.5 text-[10px] font-medium text-primary shrink-0"
              >
                管理员
              </span>
            </div>
            <p class="mt-0.5 text-xs text-muted-foreground">点击编辑昵称与头像</p>
          </div>
          <ChevronRight :size="18" class="text-muted-foreground shrink-0" />
        </button>

        <!-- 存储空间概览 -->
        <section class="rounded-2xl border border-border bg-card p-4">
          <div class="flex items-center justify-between text-xs">
            <span class="font-medium text-foreground">存储空间</span>
            <span class="text-muted-foreground">
              {{ formatBytes(userState.profile?.usedBytes) }} / {{ formatBytes(userState.profile?.quotaBytes) }}
            </span>
          </div>
          <div class="mt-2.5 h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              class="h-full rounded-full transition-[width] duration-300"
              :class="quotaBarClass"
              :style="{ width: `${quota.percent}%` }"
            />
          </div>
          <div class="mt-1.5 flex items-center justify-between text-[11px] text-muted-foreground">
            <span>已使用 {{ quota.percent }}%</span>
            <span v-if="quota.level === 'danger'" class="text-destructive font-medium">空间即将耗尽，请及时清理</span>
            <span v-else-if="quota.level === 'warning'" class="text-amber-600 dark:text-amber-400 font-medium">空间使用率较高</span>
          </div>
        </section>

        <!-- 常用功能 -->
        <VanCellGroup inset class="!mx-0 border border-border overflow-hidden !rounded-2xl">
          <VanCell title="云端相册" label="按拍摄时间浏览照片与视频" is-link to="/photos">
            <template #icon>
              <ImageIcon :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell
            v-if="mobileCapabilities.photoAutoBackup"
            title="相册自动增量备份"
            label="Wi-Fi / 充电自动备份并秒传去重"
            is-link
            to="/settings/photo-backup"
          >
            <template #icon>
              <CloudUpload :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell
            v-if="mobileCapabilities.offlineCache"
            title="离线文件"
            label="无网飞行模式下直接阅读已缓存文件"
            is-link
            to="/offline"
          >
            <template #icon>
              <DownloadCloud :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell
            v-if="mobileCapabilities.documentScanner"
            title="文档扫描转 PDF"
            label="多页拍照增强并合成 PDF 上传"
            is-link
            to="/tools/doc-scan"
          >
            <template #icon>
              <FileScan :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell
            title="传输任务中心"
            :value="mobileUi.activeUploadCount > 0 ? `${mobileUi.activeUploadCount} 项进行中` : ''"
            is-link
            to="/tasks"
          >
            <template #icon>
              <UploadCloud :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell
            title="消息中心"
            :value="mobileUi.unreadMessageCount > 0 ? `${mobileUi.unreadMessageCount} 条未读` : ''"
            is-link
            to="/messages"
          >
            <template #icon>
              <Bell :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
          <VanCell title="私密空间" is-link to="/private">
            <template #icon>
              <LockKeyhole :size="18" class="mr-2.5 text-amber-500 self-center" />
            </template>
          </VanCell>
          <VanCell title="回收站" is-link to="/trash">
            <template #icon>
              <Trash2 :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
        </VanCellGroup>

        <!-- 设置分组 -->
        <VanCellGroup inset class="!mx-0 border border-border overflow-hidden !rounded-2xl">
          <VanCell title="个人资料" is-link to="/settings/profile">
            <template #icon>
              <User :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell title="账户与安全" label="修改密码与活跃设备管理" is-link to="/settings/security">
            <template #icon>
              <Shield :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell title="主题与外观" is-link to="/settings/appearance">
            <template #icon>
              <Palette :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell title="文件与版本偏好" is-link to="/settings/files">
            <template #icon>
              <SlidersHorizontal :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell title="网络与流量保护" is-link to="/settings/network">
            <template #icon>
              <Wifi :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell v-if="userState.profile?.admin" title="系统管理概览" is-link to="/admin-overview">
            <template #icon>
              <Activity :size="18" class="mr-2.5 text-primary self-center" />
            </template>
          </VanCell>
        </VanCellGroup>

        <!-- 切换与退出 -->
        <VanCellGroup inset class="!mx-0 border border-border overflow-hidden !rounded-2xl">
          <VanCell title="切换到桌面版" is-link @click="switchToDesktop">
            <template #icon>
              <Monitor :size="18" class="mr-2.5 text-muted-foreground self-center" />
            </template>
          </VanCell>
          <VanCell title="退出登录" clickable @click="handleLogout">
            <template #icon>
              <LogOut :size="18" class="mr-2.5 text-destructive self-center" />
            </template>
            <template #title>
              <span class="text-destructive font-medium">{{ loggingOut ? '正在退出…' : '退出登录' }}</span>
            </template>
          </VanCell>
        </VanCellGroup>
      </div>
    </PullRefresh>
  </MobileShell>
</template>
