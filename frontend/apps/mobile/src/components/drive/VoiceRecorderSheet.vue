<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Button as VanButton } from 'vant'
import { Mic, Pause, Play, RotateCcw, Square, Upload } from '@lucide/vue'
import { formatBytes } from '@repo/drive-core/format'
import {
  buildCaptureFilename,
  formatRecordingDuration,
  pickPreferredAudioMimeType,
} from '@/lib/media-capture'
import { BottomSheet } from '@/components/common'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  recorded: [file: File]
}>()

type RecorderPhase = 'idle' | 'recording' | 'paused' | 'recorded' | 'unsupported'

const phase = ref<RecorderPhase>('idle')
const elapsedSec = ref(0)
const errorText = ref('')
const filename = ref('')
const recordedBlob = ref<Blob | null>(null)
const audioUrl = ref<string | null>(null)

let mediaRecorder: MediaRecorder | null = null
let mediaStream: MediaStream | null = null
let chunks: Blob[] = []
let timer: ReturnType<typeof setInterval> | null = null

const isSupported = computed(
  () =>
    typeof window !== 'undefined' &&
    typeof MediaRecorder !== 'undefined' &&
    Boolean(navigator.mediaDevices?.getUserMedia),
)

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function stopStream() {
  if (mediaStream) {
    for (const track of mediaStream.getTracks()) {
      track.stop()
    }
    mediaStream = null
  }
}

function revokeAudioUrl() {
  if (audioUrl.value) {
    URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = null
  }
}

function resetRecorder() {
  stopTimer()
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    try {
      mediaRecorder.stop()
    } catch {
      // ignore
    }
  }
  mediaRecorder = null
  stopStream()
  revokeAudioUrl()
  chunks = []
  recordedBlob.value = null
  elapsedSec.value = 0
  errorText.value = ''
  filename.value = ''
  phase.value = isSupported.value ? 'idle' : 'unsupported'
}

watch(
  () => props.open,
  opened => {
    if (opened) {
      resetRecorder()
    } else {
      resetRecorder()
    }
  },
)

onBeforeUnmount(() => {
  resetRecorder()
})

async function startRecording() {
  if (!isSupported.value) {
    phase.value = 'unsupported'
    return
  }
  errorText.value = ''
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
  } catch (err) {
    const name = err instanceof Error ? err.name : ''
    if (name === 'NotAllowedError' || name === 'PermissionDeniedError') {
      errorText.value = '未获得麦克风权限，请在浏览器设置中允许访问麦克风后重试。'
    } else if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      errorText.value = '未检测到可用的麦克风设备。'
    } else {
      errorText.value = '无法启动录音，请确认当前处于 HTTPS 安全环境并已授权麦克风。'
    }
    return
  }

  const mimeType = pickPreferredAudioMimeType(
    typeof MediaRecorder.isTypeSupported === 'function'
      ? m => MediaRecorder.isTypeSupported(m)
      : undefined,
  )
  chunks = []
  mediaRecorder = mimeType
    ? new MediaRecorder(mediaStream, { mimeType })
    : new MediaRecorder(mediaStream)

  mediaRecorder.ondataavailable = evt => {
    if (evt.data && evt.data.size > 0) chunks.push(evt.data)
  }

  mediaRecorder.onstop = () => {
    stopTimer()
    stopStream()
    if (chunks.length === 0) return
    const type = mediaRecorder?.mimeType || mimeType || 'audio/webm'
    const blob = new Blob(chunks, { type })
    recordedBlob.value = blob
    revokeAudioUrl()
    audioUrl.value = URL.createObjectURL(blob)
    filename.value = buildCaptureFilename('audio', type)
    phase.value = 'recorded'
  }

  mediaRecorder.start(250)
  elapsedSec.value = 0
  phase.value = 'recording'
  timer = setInterval(() => {
    elapsedSec.value += 1
  }, 1000)
}

function togglePause() {
  if (!mediaRecorder) return
  if (phase.value === 'recording' && typeof mediaRecorder.pause === 'function') {
    mediaRecorder.pause()
    stopTimer()
    phase.value = 'paused'
  } else if (phase.value === 'paused' && typeof mediaRecorder.resume === 'function') {
    mediaRecorder.resume()
    phase.value = 'recording'
    timer = setInterval(() => {
      elapsedSec.value += 1
    }, 1000)
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
}

function uploadRecording() {
  if (!recordedBlob.value) return
  const trimmed = filename.value.trim()
  if (!trimmed || /[/\\]/.test(trimmed)) {
    errorText.value = '请输入合法的录音文件名'
    return
  }
  const file = new File([recordedBlob.value], trimmed, {
    type: recordedBlob.value.type || 'audio/webm',
    lastModified: Date.now(),
  })
  emit('update:open', false)
  emit('recorded', file)
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    title="语音备忘录"
    subtitle="录制语音并直接上传至当前目录"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4 pb-2">
      <div
        v-if="phase === 'unsupported'"
        class="rounded-2xl bg-muted/60 p-4 text-center text-xs text-muted-foreground space-y-1.5"
      >
        <p class="text-sm font-medium text-foreground">当前浏览器不支持网页录音</p>
        <p>请使用系统录音机录制后通过「上传本地文件」上传，或在 HTTPS 环境下使用新版浏览器。</p>
      </div>

      <template v-else>
        <!-- 计时与波形指示 -->
        <div class="rounded-2xl bg-muted/40 py-6 px-4 flex flex-col items-center gap-3">
          <div class="flex items-center gap-1 h-8" aria-hidden="true">
            <span
              v-for="bar in 9"
              :key="bar"
              class="w-1 rounded-full bg-primary transition-all duration-200"
              :class="phase === 'recording' ? 'animate-pulse' : 'opacity-40'"
              :style="{
                height: phase === 'recording' ? `${10 + ((bar * 7) % 20)}px` : '8px',
              }"
            />
          </div>
          <p class="text-2xl font-mono font-semibold tabular-nums text-foreground">
            {{ formatRecordingDuration(elapsedSec) }}
          </p>
          <p class="text-xs text-muted-foreground">
            <template v-if="phase === 'idle'">点击下方按钮开始录音</template>
            <template v-else-if="phase === 'recording'">正在录音…</template>
            <template v-else-if="phase === 'paused'">录音已暂停</template>
            <template v-else-if="phase === 'recorded' && recordedBlob">
              已录制完成 · {{ formatBytes(recordedBlob.size) }}
            </template>
          </p>
        </div>

        <p v-if="errorText" class="rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">
          {{ errorText }}
        </p>

        <!-- 录音控制区 -->
        <div v-if="phase === 'idle'" class="flex justify-center">
          <VanButton round type="primary" class="px-8" @click="startRecording">
            <span class="inline-flex items-center gap-1.5">
              <Mic :size="16" />
              <span>开始录音</span>
            </span>
          </VanButton>
        </div>

        <div v-else-if="phase === 'recording' || phase === 'paused'" class="flex items-center justify-center gap-4">
          <VanButton round plain type="primary" @click="togglePause">
            <span class="inline-flex items-center gap-1.5">
              <Pause v-if="phase === 'recording'" :size="15" />
              <Play v-else :size="15" />
              <span>{{ phase === 'recording' ? '暂停' : '继续' }}</span>
            </span>
          </VanButton>
          <VanButton round type="danger" @click="stopRecording">
            <span class="inline-flex items-center gap-1.5">
              <Square :size="14" />
              <span>停止</span>
            </span>
          </VanButton>
        </div>

        <!-- 试听与上传区 -->
        <div v-else-if="phase === 'recorded'" class="space-y-3">
          <audio v-if="audioUrl" :src="audioUrl" controls class="w-full" />

          <div>
            <label class="block text-xs font-medium text-muted-foreground mb-1">录音文件名</label>
            <input
              v-model="filename"
              type="text"
              maxlength="255"
              class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
            />
          </div>

          <div class="flex gap-2">
            <VanButton block round plain @click="resetRecorder">
              <span class="inline-flex items-center gap-1">
                <RotateCcw :size="14" />
                <span>重录</span>
              </span>
            </VanButton>
            <VanButton block round type="primary" @click="uploadRecording">
              <span class="inline-flex items-center gap-1">
                <Upload :size="14" />
                <span>上传录音</span>
              </span>
            </VanButton>
          </div>
        </div>
      </template>
    </div>
  </BottomSheet>
</template>
