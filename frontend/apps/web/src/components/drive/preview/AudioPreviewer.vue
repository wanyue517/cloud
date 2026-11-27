<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Music, Pause, Play, Repeat, Repeat1, Volume2, VolumeX } from '@lucide/vue'
import { Button } from '@repo/ui/button'
import { createProgressReporter, formatResumePrompt, shouldPromptResume } from '@repo/drive-core/progress'
import { drive, type DriveNode, type Version } from '@/api/drive'

const props = defineProps<{ node: DriveNode; version?: Version }>()

const loading = ref(true)
const error = ref('')
const streamUrl = ref('')
const audioRef = ref<HTMLAudioElement | null>(null)
const playing = ref(false)
const muted = ref(false)
const loopMode = ref<'none' | 'one'>('none')
const currentTime = ref(0)
const duration = ref(0)
const playbackRate = ref(1)
const rates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const resumePromptSec = ref<number | null>(null)
const progressReporter = createProgressReporter(
  (sec) => drive.saveProgress(props.node, 'AUDIO', sec),
  { intervalMs: 10_000 },
)
let loadSeq = 0

function formatTime(sec: number): string {
  if (!Number.isFinite(sec) || sec < 0) return '00:00'
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function loadAudio() {
  progressReporter.flush()
  progressReporter.reset()
  resumePromptSec.value = null
  const seq = ++loadSeq
  loading.value = true
  error.value = ''
  playing.value = false
  currentTime.value = 0
  duration.value = 0
  streamUrl.value = ''
  try {
    const ticket = await drive.streamTicket(props.node, { versionId: props.version?.id, assetType: 'ORIGINAL' })
    if (seq !== loadSeq) return
    streamUrl.value = ticket.streamUrl
    void drive.getProgress(props.node).then((saved) => {
      if (seq !== loadSeq || !saved || saved.progressType !== 'AUDIO') return
      if (shouldPromptResume('AUDIO', saved.positionValue, duration.value > 0 ? duration.value : undefined)) {
        resumePromptSec.value = saved.positionValue
      }
    }).catch(() => undefined)
  } catch (e) {
    if (seq !== loadSeq) return
    error.value = e instanceof Error ? e.message : '音频加载失败'
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function togglePlay() {
  const el = audioRef.value
  if (!el) return
  if (el.paused) void el.play()
  else el.pause()
}

function toggleMute() {
  const el = audioRef.value
  if (!el) return
  el.muted = !el.muted
  muted.value = el.muted
}

function seek(event: Event) {
  const el = audioRef.value
  const input = event.target as HTMLInputElement
  if (!el) return
  const next = Number(input.value)
  if (Number.isFinite(next)) {
    el.currentTime = next
    currentTime.value = next
  }
}

function setRate(rate: number) {
  playbackRate.value = rate
  if (audioRef.value) audioRef.value.playbackRate = rate
}

function resumeAudioFromSaved() {
  const el = audioRef.value
  if (!el || resumePromptSec.value === null) return
  el.currentTime = resumePromptSec.value
  currentTime.value = resumePromptSec.value
  resumePromptSec.value = null
  void el.play()
}

function handleAudioTimeUpdate() {
  const sec = audioRef.value?.currentTime || 0
  currentTime.value = sec
  if (sec >= 1) progressReporter.report(sec)
}

watch(() => [props.node.id, props.version?.id], () => { void loadAudio() })
onMounted(() => { void loadAudio() })
onBeforeUnmount(() => {
  progressReporter.flush()
  loadSeq++
  streamUrl.value = ''
})
</script>

<template>
  <div class="flex h-full min-h-0 flex-1 items-center justify-center bg-muted/20 p-6">
    <p v-if="loading" role="status" class="text-sm text-muted-foreground">正在加载音频…</p>
    <p v-else-if="error" role="alert" class="text-sm text-destructive">{{ error }}</p>
    <div v-else class="flex w-full max-w-lg flex-col gap-5 rounded-xl border border-border bg-card p-6 shadow-sm">
      <div class="flex items-center gap-4">
        <div class="flex size-14 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <Music :size="28" />
        </div>
        <div class="min-w-0 flex-1">
          <h3 class="truncate text-base font-semibold">{{ node.name }}</h3>
          <p class="text-xs text-muted-foreground">{{ version?.mimeType || node.mimeType || '音频文件' }}</p>
        </div>
      </div>

      <audio
        ref="audioRef"
        :src="streamUrl"
        :loop="loopMode === 'one'"
        preload="metadata"
        @play="playing = true"
        @pause="playing = false; progressReporter.flush()"
        @ended="playing = false; progressReporter.flush()"
        @loadedmetadata="duration = audioRef?.duration || 0; if (audioRef) audioRef.playbackRate = playbackRate"
        @timeupdate="handleAudioTimeUpdate"
      />

      <div v-if="resumePromptSec !== null" class="flex items-center justify-between gap-2 rounded-lg border border-primary/25 bg-primary/10 px-3 py-2 text-xs">
        <span>{{ formatResumePrompt('AUDIO', resumePromptSec) }}</span>
        <div class="flex items-center gap-2 shrink-0">
          <Button size="sm" class="h-6 px-2.5 text-xs" @click="resumeAudioFromSaved">跳转播放</Button>
          <button type="button" class="text-muted-foreground hover:text-foreground" @click="resumePromptSec = null">忽略</button>
        </div>
      </div>

      <div class="space-y-2">
        <input
          type="range"
          min="0"
          :max="duration || 0"
          step="0.1"
          :value="currentTime"
          aria-label="音频播放进度"
          class="w-full accent-primary"
          @input="seek"
        />
        <div class="flex items-center justify-between text-xs tabular-nums text-muted-foreground">
          <span>{{ formatTime(currentTime) }}</span>
          <span>{{ formatTime(duration) }}</span>
        </div>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <Button size="sm" @click="togglePlay">
            <Pause v-if="playing" :size="16" />
            <Play v-else :size="16" />
            {{ playing ? '暂停' : '播放' }}
          </Button>
          <Button variant="outline" size="icon-sm" :aria-label="muted ? '取消静音' : '静音'" @click="toggleMute">
            <VolumeX v-if="muted" :size="15" />
            <Volume2 v-else :size="15" />
          </Button>
          <Button
            variant="outline"
            size="icon-sm"
            :class="loopMode === 'one' ? 'border-primary text-primary' : ''"
            :aria-label="loopMode === 'one' ? '关闭单曲循环' : '开启单曲循环'"
            :title="loopMode === 'one' ? '单曲循环已开启' : '顺序播放'"
            @click="loopMode = loopMode === 'one' ? 'none' : 'one'"
          >
            <Repeat1 v-if="loopMode === 'one'" :size="15" />
            <Repeat v-else :size="15" />
          </Button>
        </div>

        <div class="flex items-center gap-1" role="group" aria-label="播放倍速">
          <Button
            v-for="r in rates"
            :key="r"
            variant="outline"
            size="sm"
            class="h-7 px-2 text-xs"
            :class="playbackRate === r ? 'border-primary bg-primary/10 text-primary' : ''"
            @click="setRate(r)"
          >
            {{ r }}x
          </Button>
        </div>
      </div>
    </div>
  </div>
</template>
