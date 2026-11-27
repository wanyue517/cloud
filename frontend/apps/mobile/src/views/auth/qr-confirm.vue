<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button as VanButton, showToast } from 'vant'
import { Camera, CheckCircle2, Monitor, QrCode } from '@lucide/vue'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { request } from '@repo/session/request'
import { MobileShell } from '@/components/common'

const route = useRoute()
const router = useRouter()

const qrId = ref('')
const manualInput = ref('')
const confirming = ref(false)
const confirmed = ref(false)
const errorText = ref('')

// Optional camera BarcodeDetector scanner
const scanning = ref(false)
const videoRef = ref<HTMLVideoElement | null>(null)
let mediaStream: MediaStream | null = null
let scanRaf = 0

function extractQrIdFromText(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed) return ''
  try {
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      const url = new URL(trimmed)
      const q = url.searchParams.get('qrId')
      if (q) return q.trim()
    }
  } catch {
    // not a URL, treat as raw code
  }
  const match = /qrId=([A-Za-z0-9_-]+)/.exec(trimmed)
  if (match?.[1]) return match[1]
  return trimmed
}

function stopCamera() {
  scanning.value = false
  if (scanRaf) {
    cancelAnimationFrame(scanRaf)
    scanRaf = 0
  }
  if (mediaStream) {
    for (const track of mediaStream.getTracks()) track.stop()
    mediaStream = null
  }
}

async function markScanned(id: string) {
  errorText.value = ''
  try {
    await request.post<void>(`/v1/auth/qr/${encodeURIComponent(id)}/scan`, undefined, { silent: true })
  } catch (err) {
    errorText.value = normalizeErrorMessage(err, '二维码已过期或无效，请在 PC 端刷新二维码')
  }
}

onMounted(() => {
  const fromQuery = typeof route.query.qrId === 'string' ? route.query.qrId.trim() : ''
  if (fromQuery) {
    qrId.value = fromQuery
    void markScanned(fromQuery)
  }
})

onBeforeUnmount(() => {
  stopCamera()
})

async function startCameraScan() {
  const BarcodeDetectorCtor = (
    window as unknown as {
      BarcodeDetector?: new (opts: { formats: string[] }) => {
        detect: (source: HTMLVideoElement) => Promise<{ rawValue?: string }[]>
      }
    }
  ).BarcodeDetector

  if (!BarcodeDetectorCtor || !navigator.mediaDevices?.getUserMedia) {
    showToast('当前浏览器不支持原生相机扫码，请直接输入 PC 端二维码下方的确认码')
    return
  }

  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
    })
    scanning.value = true
    const detector = new BarcodeDetectorCtor({ formats: ['qr_code'] })
    const tick = async () => {
      if (!scanning.value) return
      const video = videoRef.value
      if (video && video.srcObject !== mediaStream) {
        video.srcObject = mediaStream
        await video.play().catch(() => undefined)
      }
      if (video && video.readyState >= 2) {
        try {
          const results = await detector.detect(video)
          const raw = results[0]?.rawValue
          if (raw) {
            const parsed = extractQrIdFromText(raw)
            if (parsed) {
              stopCamera()
              qrId.value = parsed
              void markScanned(parsed)
              return
            }
          }
        } catch {
          // continue scanning
        }
      }
      scanRaf = requestAnimationFrame(() => void tick())
    }
    scanRaf = requestAnimationFrame(() => void tick())
  } catch {
    stopCamera()
    showToast('无法打开摄像头，请检查权限或直接输入确认码')
  }
}

function applyManualCode() {
  const parsed = extractQrIdFromText(manualInput.value)
  if (!parsed) {
    showToast('请输入 PC 端二维码下方的确认码')
    return
  }
  qrId.value = parsed
  void markScanned(parsed)
}

async function confirmPcLogin() {
  if (!qrId.value || confirming.value) return
  confirming.value = true
  errorText.value = ''
  try {
    await request.post<void>(`/v1/auth/qr/${encodeURIComponent(qrId.value)}/confirm`)
    confirmed.value = true
    showToast('已授权 PC 端登录')
  } catch (err) {
    errorText.value = normalizeErrorMessage(err, '授权失败，二维码可能已过期')
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <MobileShell title="扫码登录 PC 端" back-to="/me">
    <div class="flex-1 flex flex-col items-center justify-center px-5 py-8">
      <!-- 授权成功 -->
      <div
        v-if="confirmed"
        class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 text-center space-y-4 shadow-xs"
      >
        <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
          <CheckCircle2 :size="30" />
        </span>
        <h2 class="text-base font-semibold text-foreground">PC 端已确认登录</h2>
        <p class="text-xs text-muted-foreground">电脑浏览器将在 2 秒内自动进入 Cloudlane 网盘。</p>
        <VanButton block round type="primary" @click="router.replace('/home')">返回首页</VanButton>
      </div>

      <!-- 已识别 qrId：确认授权页 -->
      <div
        v-else-if="qrId"
        class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 text-center space-y-4 shadow-xs"
      >
        <span class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <Monitor :size="28" />
        </span>
        <div class="space-y-1">
          <h2 class="text-base font-semibold text-foreground">确认登录 PC 端 Cloudlane？</h2>
          <p class="text-xs text-muted-foreground">请确认正在登录的电脑为您本人操作</p>
        </div>

        <p v-if="errorText" class="rounded-xl bg-destructive/10 px-3 py-2 text-xs text-destructive">
          {{ errorText }}
        </p>

        <div class="space-y-2 pt-1">
          <VanButton
            block
            round
            type="primary"
            :loading="confirming"
            :disabled="Boolean(errorText)"
            @click="confirmPcLogin"
          >
            确认登录 PC 端
          </VanButton>
          <VanButton
            block
            round
            plain
            @click="qrId = ''; errorText = ''; manualInput = ''"
          >
            取消
          </VanButton>
        </div>
      </div>

      <!-- 扫一扫 / 输入确认码兜底 -->
      <div
        v-else
        class="w-full max-w-sm rounded-3xl border border-border bg-card p-6 space-y-5 shadow-xs"
      >
        <div class="text-center space-y-1.5">
          <span class="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10 text-primary">
            <QrCode :size="24" />
          </span>
          <h2 class="text-base font-semibold text-foreground">扫一扫登录电脑端</h2>
          <p class="text-xs text-muted-foreground">
            打开电脑端 Cloudlane 登录页，切换「扫码登录」后扫描二维码或输入确认码
          </p>
        </div>

        <div v-if="scanning" class="space-y-2">
          <video ref="videoRef" playsinline muted class="w-full rounded-2xl bg-black aspect-square object-cover" />
          <VanButton block size="small" round plain @click="stopCamera">关闭摄像头</VanButton>
        </div>
        <VanButton v-else block round type="primary" @click="startCameraScan">
          <span class="inline-flex items-center gap-1.5">
            <Camera :size="16" />
            <span>打开相机扫码</span>
          </span>
        </VanButton>

        <form class="space-y-2.5 pt-2 border-t border-border" @submit.prevent="applyManualCode">
          <label class="block text-xs font-medium text-muted-foreground">或手动输入 PC 端确认码</label>
          <input
            v-model="manualInput"
            type="text"
            placeholder="粘贴或输入 PC 端二维码下方确认码"
            class="w-full rounded-xl border border-input bg-background px-3.5 py-2.5 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
          <VanButton block round plain type="primary" native-type="submit">
            下一步确认授权
          </VanButton>
        </form>
      </div>
    </div>
  </MobileShell>
</template>
