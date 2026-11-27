<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button as VanButton, Switch as VanSwitch } from 'vant'
import { Trash2, Wifi, WifiOff } from '@lucide/vue'
import { toast } from '@repo/ui/toast'
import { formatBytes } from '@repo/drive-core/format'
import { networkPrefs, networkStatus, saveNetworkPreferences } from '@/lib/network'
import { mobileThumbCache } from '@/lib/thumb-cache'
import { MobileShell } from '@/components/common'

const cacheStats = ref(mobileThumbCache.stats())

const networkLabel = computed(() => {
  if (!networkStatus.online) return '当前已离线'
  if (networkStatus.kind === 'wifi') return 'Wi-Fi / 宽带网络'
  if (networkStatus.kind === 'cellular') return `移动蜂窝网络 (${networkStatus.effectiveType.toUpperCase()})`
  return '在线（网络类型未知）'
})

function togglePref(key: 'wifiOnlyUpload' | 'warnCellularVideo' | 'lowResThumbOnCellular', value: boolean) {
  saveNetworkPreferences({ [key]: value })
}

function clearCache() {
  mobileThumbCache.clear()
  cacheStats.value = mobileThumbCache.stats()
  toast.success('本地缩略图缓存已清理')
}
</script>

<template>
  <MobileShell title="网络与流量保护" fallback-path="/me" padded>
    <div class="space-y-5">
      <!-- 当前网络状态 -->
      <section class="rounded-2xl border border-border bg-card p-4 flex items-center gap-3">
        <div
          class="h-11 w-11 rounded-xl flex items-center justify-center shrink-0"
          :class="networkStatus.online ? 'bg-primary/15 text-primary' : 'bg-destructive/15 text-destructive'"
        >
          <Wifi v-if="networkStatus.online" :size="20" />
          <WifiOff v-else :size="20" />
        </div>
        <div class="min-w-0">
          <p class="text-sm font-semibold text-foreground">{{ networkLabel }}</p>
          <p class="text-xs text-muted-foreground mt-0.5">
            {{ networkStatus.saveData ? '浏览器已开启省流模式' : '网络状态实时监测中' }}
          </p>
        </div>
      </section>

      <!-- 流量保护开关 -->
      <section class="rounded-2xl border border-border bg-card divide-y divide-border">
        <div class="px-4 py-3.5 flex items-center justify-between gap-3">
          <div>
            <p class="text-sm font-medium text-foreground">仅 Wi-Fi 自动上传</p>
            <p class="text-xs text-muted-foreground mt-0.5">蜂窝网络下发起上传前弹出流量确认</p>
          </div>
          <VanSwitch
            :model-value="networkPrefs.wifiOnlyUpload"
            size="22px"
            @update:model-value="togglePref('wifiOnlyUpload', $event)"
          />
        </div>

        <div class="px-4 py-3.5 flex items-center justify-between gap-3">
          <div>
            <p class="text-sm font-medium text-foreground">蜂窝网络播放视频提醒</p>
            <p class="text-xs text-muted-foreground mt-0.5">使用移动流量播放视频前先提示确认</p>
          </div>
          <VanSwitch
            :model-value="networkPrefs.warnCellularVideo"
            size="22px"
            @update:model-value="togglePref('warnCellularVideo', $event)"
          />
        </div>

        <div class="px-4 py-3.5 flex items-center justify-between gap-3">
          <div>
            <p class="text-sm font-medium text-foreground">蜂窝网络省流缩略图</p>
            <p class="text-xs text-muted-foreground mt-0.5">移动网络下请求 240px 轻量缩略图</p>
          </div>
          <VanSwitch
            :model-value="networkPrefs.lowResThumbOnCellular"
            size="22px"
            @update:model-value="togglePref('lowResThumbOnCellular', $event)"
          />
        </div>
      </section>

      <!-- 缩略图缓存管理 -->
      <section class="rounded-2xl border border-border bg-card p-4 space-y-3">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-semibold text-foreground">本地缩略图缓存</p>
            <p class="text-xs text-muted-foreground mt-0.5">
              已缓存 {{ cacheStats.count }} / {{ cacheStats.maxEntries }} 项 · 约 {{ formatBytes(cacheStats.bytes) }}
            </p>
          </div>
        </div>
        <VanButton
          plain
          block
          round
          size="small"
          :disabled="cacheStats.count === 0"
          class="!h-10"
          @click="clearCache"
        >
          <span class="inline-flex items-center gap-1.5">
            <Trash2 :size="14" />
            <span>清理缩略图缓存</span>
          </span>
        </VanButton>
      </section>
    </div>
  </MobileShell>
</template>
