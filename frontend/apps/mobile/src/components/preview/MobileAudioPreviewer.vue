<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { Disc3, FastForward, Pause, Play, Rewind } from '@lucide/vue'
import { formatBytes } from '@repo/drive-core/format'
import { createProgressReporter, formatResumePrompt, shouldPromptResume } from '@repo/drive-core/progress'
import { mobileDriveApi, type DriveNode } from '@/api/drive'
import { formatMediaTime } from '@/lib/archive-tree'
import {
  AUDIO_RATES,
  audioState,
  loadAndPlayAudioNode,
  minimizeAudioPlayer,
  seekAudioBy,
  seekAudioTo,
  setAudioPlaybackRate,
  togglePlayAudio,
} from '@/stores/audio-player'

const props = defineProps<{
  node: DriveNode
  versionId?: string
}>()

const emit = defineEmits<{
  'toggle-chrome': []
}>()

const resumePromptSec = ref<number | null>(null)
const progressReporter = createProgressReporter(
  (sec) => mobileDriveApi.saveProgress(props.node.spaceId, props.node.id, 'AUDIO', sec),
  { intervalMs: 10_000 },
)

watch(
  () => [props.node.spaceId, props.node.id, props.versionId ?? props.node.currentVersionId] as const,
  ([spaceId, nodeId]) => {
    progressReporter.flush()
    progressReporter.reset()
    resumePromptSec.value = null
    void loadAndPlayAudioNode(props.node, props.versionId)
    void mobileDriveApi
      .getProgress(spaceId, nodeId)
      .then((saved) => {
        if (props.node.id !== nodeId || !saved || saved.progressType !== 'AUDIO') return
        if (shouldPromptResume('AUDIO', saved.positionValue, audioState.duration > 0 ? audioState.duration : undefined)) {
          resumePromptSec.value = saved.positionValue
        }
      })
      .catch(() => undefined)
  },
  { immediate: true },
)

watch(
  () => audioState.currentTime,
  (sec) => {
    if (audioState.track?.nodeId === props.node.id && Number.isFinite(sec) && sec >= 1) {
      progressReporter.report(sec)
    }
  },
)

onBeforeUnmount(() => {
  progressReporter.flush()
  // 退出预览页时不中断播放，自动缩为底部全局悬浮条
  minimizeAudioPlayer()
})

function resumeAudioFromSaved() {
  if (resumePromptSec.value === null) return
  seekAudioTo(resumePromptSec.value)
  resumePromptSec.value = null
}

function cycleRate() {
  const idx = AUDIO_RATES.indexOf(audioState.playbackRate as (typeof AUDIO_RATES)[number])
  const next = AUDIO_RATES[(idx + 1) % AUDIO_RATES.length] ?? 1
  setAudioPlaybackRate(next)
}
</script>

<template>
  <div
    class="flex-1 min-h-0 flex flex-col items-center justify-center px-6 pb-20 bg-background text-foreground"
    @click="emit('toggle-chrome')"
  >
    <div class="w-full max-w-xs flex flex-col items-center space-y-6" @click.stop>
      <!-- 封面唱片动效 -->
      <div
        class="flex h-44 w-44 items-center justify-center rounded-full bg-gradient-to-br from-primary/20 via-primary/10 to-muted border border-border shadow-lg"
      >
        <Disc3
          :size="84"
          class="text-primary"
          :class="audioState.playing ? 'animate-spin' : ''"
          style="animation-duration: 8s"
        />
      </div>

      <div class="text-center space-y-1 w-full">
        <p class="text-base font-semibold text-foreground break-all line-clamp-2">
          {{ props.node.name }}
        </p>
        <p class="text-xs text-muted-foreground">
          {{ formatBytes(props.node.size) }} · 退出后自动保持后台悬浮播放
        </p>
      </div>

      <p v-if="audioState.error" class="text-xs text-destructive text-center">
        {{ audioState.error }}
      </p>

      <!-- 跨端播放进度接力提示 -->
      <div
        v-if="resumePromptSec !== null"
        class="w-full flex items-center justify-between gap-2 rounded-xl border border-primary/25 bg-primary/10 px-3 py-2 text-xs text-foreground"
      >
        <span class="truncate">{{ formatResumePrompt('AUDIO', resumePromptSec) }}</span>
        <div class="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            class="rounded-lg bg-primary px-2.5 py-1 text-[11px] font-medium text-primary-foreground"
            @click="resumeAudioFromSaved"
          >
            跳转
          </button>
          <button
            type="button"
            class="text-muted-foreground px-1"
            aria-label="关闭提示"
            @click="resumePromptSec = null"
          >
            ×
          </button>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="w-full space-y-1.5">
        <input
          type="range"
          :min="0"
          :max="Math.max(1, Math.floor(audioState.duration))"
          :value="Math.floor(audioState.currentTime)"
          class="w-full accent-primary"
          @input="e => seekAudioTo(Number((e.target as HTMLInputElement).value))"
        />
        <div class="flex items-center justify-between text-[11px] text-muted-foreground tabular-nums">
          <span>{{ formatMediaTime(audioState.currentTime) }}</span>
          <span>{{ formatMediaTime(audioState.duration) }}</span>
        </div>
      </div>

      <!-- 播放控制区 -->
      <div class="flex items-center justify-center gap-5">
        <button
          type="button"
          class="rounded-full border border-border px-2.5 py-1 text-xs font-medium text-foreground active:bg-accent tabular-nums"
          @click="cycleRate"
        >
          {{ audioState.playbackRate }}x
        </button>

        <button
          type="button"
          class="inline-flex h-10 w-10 items-center justify-center rounded-full text-foreground active:bg-accent"
          aria-label="后退 15 秒"
          @click="seekAudioBy(-15)"
        >
          <Rewind :size="20" />
        </button>

        <button
          type="button"
          class="inline-flex h-14 w-14 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-md active:scale-95 transition-transform"
          :aria-label="audioState.playing ? '暂停' : '播放'"
          :disabled="audioState.loading"
          @click="togglePlayAudio()"
        >
          <Pause v-if="audioState.playing" :size="24" />
          <Play v-else :size="24" class="translate-x-0.5" />
        </button>

        <button
          type="button"
          class="inline-flex h-10 w-10 items-center justify-center rounded-full text-foreground active:bg-accent"
          aria-label="前进 15 秒"
          @click="seekAudioBy(15)"
        >
          <FastForward :size="20" />
        </button>
      </div>
    </div>
  </div>
</template>
