<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Music2, Pause, Play, X } from '@lucide/vue'
import { formatMediaTime } from '@/lib/archive-tree'
import { audioState, stopAndCloseAudioPlayer, togglePlayAudio } from '@/stores/audio-player'
import { mobileUi } from '@/stores/mobile-ui'

const route = useRoute()

const visible = computed(() => {
  if (!audioState.track || !audioState.minimized) return false
  if (route.path.startsWith('/auth')) return false
  return true
})

const progressPercent = computed(() => {
  if (!audioState.duration || audioState.duration <= 0) return 0
  return Math.min(100, Math.round((audioState.currentTime / audioState.duration) * 100))
})

// 位于底部 TabBar 上方，且右侧留出 FAB 空间防止遮挡
const barStyle = computed(() => ({
  bottom: mobileUi.tabBarHidden
    ? 'calc(4.75rem + var(--safe-bottom))'
    : 'calc(4.1rem + var(--safe-bottom))',
}))
</script>

<template>
  <Transition name="van-slide-up">
    <div
      v-if="visible && audioState.track"
      class="fixed left-3.5 right-20 z-30 rounded-2xl border border-border bg-card/95 shadow-lg backdrop-blur overflow-hidden"
      :style="barStyle"
    >
      <div class="flex items-center gap-2.5 px-3 py-2">
        <span class="flex h-8 w-8 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
          <Music2 :size="16" :class="audioState.playing ? 'animate-pulse' : ''" />
        </span>

        <div class="flex-1 min-w-0">
          <p class="text-xs font-medium text-foreground truncate">{{ audioState.track.name }}</p>
          <p class="text-[10px] text-muted-foreground tabular-nums">
            {{ formatMediaTime(audioState.currentTime) }} / {{ formatMediaTime(audioState.duration) }}
          </p>
        </div>

        <button
          type="button"
          class="inline-flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary shrink-0 active:bg-primary/20"
          :aria-label="audioState.playing ? '暂停' : '播放'"
          @click="togglePlayAudio()"
        >
          <Pause v-if="audioState.playing" :size="15" />
          <Play v-else :size="15" />
        </button>

        <button
          type="button"
          class="inline-flex h-7 w-7 items-center justify-center rounded-full text-muted-foreground shrink-0 active:bg-accent"
          aria-label="关闭音频"
          @click="stopAndCloseAudioPlayer"
        >
          <X :size="14" />
        </button>
      </div>

      <div class="h-0.5 w-full bg-muted">
        <div
          class="h-full bg-primary transition-all duration-150"
          :style="{ width: `${progressPercent}%` }"
        />
      </div>
    </div>
  </Transition>
</template>
