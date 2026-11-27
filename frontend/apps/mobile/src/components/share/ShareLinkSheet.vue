<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button as VanButton, Switch as VanSwitch, showToast } from 'vant'
import { Copy, Dices, Link2, QrCode } from '@lucide/vue'
import QRCode from 'qrcode'
import { normalizeErrorMessage } from '@repo/drive-core/errors'
import { formatDateTime } from '@repo/drive-core/format'
import type { DriveNode, Space } from '@/api/drive'
import { mobileShareApi, type CreatedShareLink } from '@/api/share'
import {
  buildMobileShareUrl,
  buildWechatShareText,
  generateExtractCode,
} from '@/lib/share-helpers'
import { BottomSheet } from '@/components/common'

const props = defineProps<{
  open: boolean
  node: DriveNode | null
  space?: Space | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: [link: CreatedShareLink]
}>()

const expireDays = ref<number | undefined>(7)
const password = ref('')
const allowDownload = ref(true)
const maxDownloadsInput = ref('')
const submitting = ref(false)
const createdLink = ref<CreatedShareLink | null>(null)
const createdPassword = ref('')
const qrDataUrl = ref('')

const blockedReason = computed(() => {
  if (!props.node) return ''
  if (props.node.hidden) return '隐藏项严禁创建公开分享链接，请先取消隐藏。'
  if (props.space?.kind === 'PRIVATE') return '私密空间文件严禁创建公开分享链接。'
  return ''
})

const fullShareUrl = computed(() => {
  if (!createdLink.value) return ''
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  return buildMobileShareUrl(origin, createdLink.value.token)
})

const expireOptions: { label: string; value: number | undefined }[] = [
  { label: '1 天', value: 1 },
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
  { label: '永久', value: undefined },
]

watch(
  () => props.open,
  opened => {
    if (opened) {
      expireDays.value = 7
      password.value = ''
      allowDownload.value = true
      maxDownloadsInput.value = ''
      createdLink.value = null
      createdPassword.value = ''
      qrDataUrl.value = ''
    }
  },
)

function randomizeCode() {
  password.value = generateExtractCode(4)
}

async function copyText(text: string, successMsg: string) {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    showToast(successMsg)
  } catch {
    showToast('复制失败，请手动长按复制')
  }
}

async function handleCreate() {
  if (!props.node || blockedReason.value || submitting.value) return
  const trimmedPwd = password.value.trim()
  const maxDl = maxDownloadsInput.value.trim() ? Number(maxDownloadsInput.value.trim()) : undefined
  if (maxDl !== undefined && (!Number.isInteger(maxDl) || maxDl <= 0)) {
    showToast('下载次数上限须为正整数')
    return
  }

  submitting.value = true
  try {
    const res = await mobileShareApi.createShareLink(props.node.spaceId, props.node.id, {
      password: trimmedPwd || undefined,
      expireDays: expireDays.value,
      allowDownload: allowDownload.value,
      maxDownloads: maxDl,
    })
    createdLink.value = res
    createdPassword.value = trimmedPwd
    const origin = typeof window !== 'undefined' ? window.location.origin : ''
    const url = buildMobileShareUrl(origin, res.token)
    qrDataUrl.value = await QRCode.toDataURL(url, { width: 240, margin: 2 }).catch(() => '')
    emit('created', res)
  } catch (err) {
    showToast(normalizeErrorMessage(err, '创建分享链接失败'))
  } finally {
    submitting.value = false
  }
}

function copyWechatCode() {
  if (!props.node || !createdLink.value) return
  const text = buildWechatShareText({
    name: props.node.name,
    url: fullShareUrl.value,
    password: createdPassword.value || undefined,
    expiresAt: createdLink.value.expiresAt ? formatDateTime(createdLink.value.expiresAt) : undefined,
  })
  void copyText(text, '已复制分享口令，可直接粘贴发送给好友')
}
</script>

<template>
  <BottomSheet
    :open="props.open"
    :title="createdLink ? '分享链接已生成' : '创建公开分享链接'"
    :subtitle="props.node?.name"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="blockedReason" class="py-6 text-center space-y-2">
      <p class="text-sm font-medium text-destructive">{{ blockedReason }}</p>
    </div>

    <!-- 创建成功卡片：口令 + 二维码海报 -->
    <div v-else-if="createdLink" class="space-y-4 pb-2">
      <div class="rounded-2xl border border-border bg-muted/40 p-4 flex flex-col items-center gap-3 text-center">
        <div
          v-if="qrDataUrl"
          class="rounded-2xl bg-white p-2.5 shadow-xs border border-border"
        >
          <img
            :src="qrDataUrl"
            alt="分享二维码"
            class="h-44 w-44 block"
          />
        </div>
        <div v-else class="flex h-36 w-36 items-center justify-center rounded-2xl bg-muted text-muted-foreground">
          <QrCode :size="40" />
        </div>
        <p class="text-[11px] text-muted-foreground">长按二维码可保存图片或直接转发好友</p>

        <div class="w-full rounded-xl bg-background border border-border px-3 py-2 text-left text-xs space-y-1">
          <p class="font-medium text-foreground break-all">{{ fullShareUrl }}</p>
          <p v-if="createdPassword" class="text-primary font-semibold">
            提取码：{{ createdPassword }}
          </p>
          <p class="text-muted-foreground">
            {{ createdLink.expiresAt ? `有效期至 ${formatDateTime(createdLink.expiresAt)}` : '永久有效' }}
            · {{ createdLink.allowDownload ? '允许下载' : '仅限在线预览' }}
          </p>
        </div>
      </div>

      <div class="flex gap-2">
        <VanButton block round plain type="primary" @click="copyText(fullShareUrl, '已复制链接')">
          <span class="inline-flex items-center gap-1">
            <Link2 :size="14" />
            <span>复制链接</span>
          </span>
        </VanButton>
        <VanButton block round type="primary" @click="copyWechatCode">
          <span class="inline-flex items-center gap-1">
            <Copy :size="14" />
            <span>复制口令</span>
          </span>
        </VanButton>
      </div>
    </div>

    <!-- 创建配置表单 -->
    <form v-else class="space-y-4 pb-2" @submit.prevent="handleCreate">
      <div class="space-y-1.5">
        <label class="text-xs font-medium text-muted-foreground">有效期</label>
        <div class="grid grid-cols-4 gap-2">
          <button
            v-for="opt in expireOptions"
            :key="opt.label"
            type="button"
            class="rounded-xl border py-2 text-xs font-medium"
            :class="
              expireDays === opt.value
                ? 'border-primary bg-primary/10 text-primary'
                : 'border-border bg-background text-foreground'
            "
            @click="expireDays = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>

      <div class="space-y-1.5">
        <label class="text-xs font-medium text-muted-foreground">提取码（可选）</label>
        <div class="flex gap-2">
          <input
            v-model="password"
            type="text"
            maxlength="16"
            placeholder="留空则无需提取码"
            class="flex-1 min-w-0 rounded-xl border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
          />
          <button
            type="button"
            class="inline-flex items-center gap-1 rounded-xl border border-border px-3 py-2 text-xs text-foreground active:bg-accent shrink-0"
            @click="randomizeCode"
          >
            <Dices :size="14" />
            <span>随机 4 位</span>
          </button>
        </div>
      </div>

      <div class="flex items-center justify-between rounded-xl border border-border bg-background px-3.5 py-2.5">
        <div>
          <p class="text-sm font-medium text-foreground">允许访客下载</p>
          <p class="text-[11px] text-muted-foreground">关闭后仅允许在线预览</p>
        </div>
        <VanSwitch v-model="allowDownload" size="22" />
      </div>

      <div class="space-y-1.5">
        <label class="text-xs font-medium text-muted-foreground">下载次数上限（可选）</label>
        <input
          v-model="maxDownloadsInput"
          type="number"
          min="1"
          placeholder="留空表示不限次数"
          class="w-full rounded-xl border border-input bg-background px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
      </div>

      <VanButton block round type="primary" native-type="submit" :loading="submitting">
        生成分享链接与二维码
      </VanButton>
    </form>
  </BottomSheet>
</template>
